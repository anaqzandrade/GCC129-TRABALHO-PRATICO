"""O provedor de LLM como dependência remota falível (doc 07, seção 5).

Três mecanismos, na ordem em que agem sobre cada chamada ao Gemini:

1. Timeout de 10 s por chamada, limitado pelo orçamento da requisição (12 s no total).
2. Retry com backoff exponencial (tenacity): até 3 tentativas, só para timeout, 429 e 5xx.
3. Circuit breaker (pybreaker) em volta de tudo: 3 falhas seguidas abrem o circuito por 30 s, e
   nesse tempo as chamadas falham na hora, sem esperar o provedor.

Quem chama trata a exceção com o fallback (app/assistant.py).
"""

from __future__ import annotations

import logging
import time
from concurrent.futures import ThreadPoolExecutor
from typing import Callable, TypeVar

import pybreaker
from tenacity import Retrying, retry_if_exception, stop_after_attempt, wait_exponential
from tenacity.stop import stop_base

from app.errors import describe, is_retryable

log = logging.getLogger("app.resilience")
T = TypeVar("T")

# uma tentativa só começa se sobrar pelo menos este tempo no orçamento
MIN_ATTEMPT_SECONDS = 1.0
_EXECUTOR = ThreadPoolExecutor(max_workers=16, thread_name_prefix="provider")


class BudgetExhausted(Exception):
    """O orçamento de tempo da requisição acabou antes de chamar o provedor."""


class Deadline:
    """Orçamento de tempo de uma requisição. Mantém o assistente abaixo do timeout do Portal BFF (14 s)."""

    def __init__(self, seconds: float, clock: Callable[[], float] = time.monotonic) -> None:
        self._clock = clock
        self._end = clock() + seconds

    def remaining(self) -> float:
        return max(0.0, self._end - self._clock())

    def timeout_for(self, per_call: float) -> float:
        return min(per_call, self.remaining())


def run_with_timeout(fn: Callable[[], T], seconds: float) -> T:
    """Executa fn numa thread e desiste depois de `seconds`, mesmo que a biblioteca não respeite o próprio timeout."""
    if seconds <= 0:
        raise TimeoutError("sem tempo restante para chamar o provedor")
    future = _EXECUTOR.submit(fn)
    try:
        return future.result(timeout=seconds)
    except TimeoutError:
        if future.done():
            raise  # a própria chamada levantou TimeoutError
        future.cancel()
        raise TimeoutError(f"o provedor não respondeu em {seconds:.1f} s") from None


class _DeadlineStop(stop_base):
    def __init__(self, deadline: Deadline) -> None:
        self.deadline = deadline

    def __call__(self, retry_state) -> bool:
        return self.deadline.remaining() < MIN_ATTEMPT_SECONDS


class _LogListener(pybreaker.CircuitBreakerListener):
    def state_change(self, cb, old_state, new_state) -> None:
        old = getattr(old_state, "name", old_state)
        new = getattr(new_state, "name", new_state)
        level = logging.WARNING if new == pybreaker.STATE_OPEN else logging.INFO
        log.log(level, "Circuit breaker %s: %s -> %s", cb.name, old, new)

    def failure(self, cb, exc) -> None:
        log.warning("Falha na chamada ao provedor (%s/%s seguidas): %s", cb.fail_counter, cb.fail_max, describe(exc))


class ProviderGuard:
    """Timeout + retry + circuit breaker para as chamadas ao provedor (chat e embeddings)."""

    def __init__(self, timeout: float, max_attempts: int, fail_max: int, reset_seconds: int,
                 name: str = "gemini", backoff_min: float = 0.5, backoff_max: float = 4.0) -> None:
        self.timeout = timeout
        self.max_attempts = max_attempts
        self.backoff_min, self.backoff_max = backoff_min, backoff_max
        self.breaker = pybreaker.CircuitBreaker(fail_max=fail_max, reset_timeout=reset_seconds, name=name,
                                                listeners=[_LogListener()])

    @property
    def state(self) -> str:
        return str(self.breaker.current_state)

    def call(self, fn: Callable[[], T], deadline: Deadline, what: str) -> T:
        if deadline.remaining() < MIN_ATTEMPT_SECONDS:
            raise BudgetExhausted(f"orçamento da requisição esgotado antes de {what}")
        return self.breaker.call(self._with_retry, fn, deadline, what)

    def _with_retry(self, fn: Callable[[], T], deadline: Deadline, what: str) -> T:
        retrying = Retrying(
            stop=stop_after_attempt(self.max_attempts) | _DeadlineStop(deadline),
            wait=wait_exponential(multiplier=0.5, min=self.backoff_min, max=self.backoff_max),
            retry=retry_if_exception(is_retryable),
            before_sleep=lambda state: log.warning(
                "%s: tentativa %s falhou (%s); nova tentativa em %.1f s", what, state.attempt_number,
                describe(state.outcome.exception()), state.next_action.sleep if state.next_action else 0),
            reraise=True,
        )
        for attempt in retrying:
            with attempt:
                return run_with_timeout(fn, deadline.timeout_for(self.timeout))
        raise RuntimeError("retry terminou sem resultado")  # não acontece: reraise=True
