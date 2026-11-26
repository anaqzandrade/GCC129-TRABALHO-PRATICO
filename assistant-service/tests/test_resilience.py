"""Timeout, retry e circuit breaker com as bibliotecas reais (roda no estágio de teste do Dockerfile)."""

import time

import pytest

pybreaker = pytest.importorskip("pybreaker")
pytest.importorskip("tenacity")

from app.resilience import BudgetExhausted, Deadline, ProviderGuard  # noqa: E402


class ProviderError(Exception):
    def __init__(self, code):
        super().__init__(f"{code} do provedor")
        self.code = code


def guard(**overrides):
    options = dict(timeout=1.0, max_attempts=3, fail_max=3, reset_seconds=30, backoff_min=0, backoff_max=0)
    options.update(overrides)
    return ProviderGuard(**options)


class Flaky:
    def __init__(self, failures, code=503):
        self.calls, self.failures, self.code = 0, failures, code

    def __call__(self):
        self.calls += 1
        if self.calls <= self.failures:
            raise ProviderError(self.code)
        return "ok"


def test_transient_failures_are_retried_until_success():
    fn = Flaky(failures=2)
    assert guard().call(fn, Deadline(10), "teste") == "ok"
    assert fn.calls == 3


def test_invalid_key_is_not_retried():
    fn = Flaky(failures=10, code=400)
    with pytest.raises(ProviderError):
        guard().call(fn, Deadline(10), "teste")
    assert fn.calls == 1


def test_slow_call_times_out_and_is_retried():
    calls = []

    def slow():
        calls.append(1)
        time.sleep(1)

    started = time.monotonic()
    with pytest.raises(TimeoutError):
        guard(timeout=0.1).call(slow, Deadline(10), "teste")
    assert len(calls) == 3
    assert time.monotonic() - started < 1.5


def test_circuit_opens_after_three_failed_calls_and_skips_the_provider():
    protected, fn = guard(), Flaky(failures=100, code=400)
    for _ in range(3):
        with pytest.raises((ProviderError, pybreaker.CircuitBreakerError)):
            protected.call(fn, Deadline(10), "teste")
    assert protected.state == "open"
    calls = fn.calls
    with pytest.raises(pybreaker.CircuitBreakerError):
        protected.call(fn, Deadline(10), "teste")
    assert fn.calls == calls  # circuito aberto: o provedor nem é chamado


def test_no_call_without_budget():
    with pytest.raises(BudgetExhausted):
        guard().call(lambda: "ok", Deadline(0.2), "teste")
