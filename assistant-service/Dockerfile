# syntax=docker/dockerfile:1

# ---------- Estágio 1: dependências (o pip e o cache de download não vão para a imagem final) ----------
FROM python:3.12-slim AS deps
ENV PIP_DISABLE_PIP_VERSION_CHECK=1 PIP_NO_CACHE_DIR=1
WORKDIR /build
COPY requirements.txt .
RUN pip install --prefix=/install -r requirements.txt

# ---------- Estágio de testes (opcional): docker build --target test ./assistant-service ----------
FROM python:3.12-slim AS test
ENV PIP_DISABLE_PIP_VERSION_CHECK=1 PIP_NO_CACHE_DIR=1 PYTHONDONTWRITEBYTECODE=1
WORKDIR /app
COPY --from=deps /install /usr/local
COPY requirements-dev.txt pytest.ini ./
RUN pip install -r requirements-dev.txt
COPY app ./app
COPY knowledge ./knowledge
COPY eval ./eval
COPY tests ./tests
RUN python -m pytest -q -rs

# ---------- Estágio 2: execução (só o interpretador, as bibliotecas e o código) ----------
FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 SERVER_PORT=8095
RUN groupadd --system ravita && useradd --system --gid ravita --no-create-home ravita
WORKDIR /app
COPY --from=deps /install /usr/local
COPY app ./app
COPY knowledge ./knowledge
COPY eval ./eval
USER ravita
EXPOSE 8095
# exec: o uvicorn vira o processo 1 e recebe o SIGTERM do "docker compose stop"
CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${SERVER_PORT} --no-access-log"]
