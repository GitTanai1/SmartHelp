# ── Stage 1: dependency install ──────────────────────────────────────────────
FROM python:3.12-slim AS build
WORKDIR /app

# Install deps into a prefix dir to keep the runtime layer lean
COPY requirements.txt ./
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

# ── Stage 2: runtime ─────────────────────────────────────────────────────────
FROM python:3.12-slim AS runtime
WORKDIR /app

# Copy installed packages from the build stage
COPY --from=build /install /usr/local

# Create a non-root user
RUN useradd -m -u 1000 appuser
USER appuser

# Copy application source
COPY --chown=appuser:appuser . .

EXPOSE 8000

# Bind to 0.0.0.0 so Render can reach the container
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
