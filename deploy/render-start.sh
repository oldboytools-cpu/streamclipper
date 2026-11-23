#!/bin/sh
set -eu
: "${PORT:=10000}"
: "${STREAMCLIPPER_WORKSPACE:=/data/StreamClipper}"
: "${STREAMCLIPPER_CONFIG_DIR:=/data/config}"
: "${XDG_CACHE_HOME:=/data/cache}"
export PORT STREAMCLIPPER_WORKSPACE STREAMCLIPPER_CONFIG_DIR XDG_CACHE_HOME

if [ -z "${STREAMCLIPPER_ACCESS_TOKEN:-}" ]; then
  echo "STREAMCLIPPER_ACCESS_TOKEN is required; configure a strong secret in the host dashboard." >&2
  exit 1
fi
if [ -z "${STREAMCLIPPER_PUBLIC_URL:-}" ] && [ -n "${RENDER_EXTERNAL_HOSTNAME:-}" ]; then
  STREAMCLIPPER_PUBLIC_URL="https://${RENDER_EXTERNAL_HOSTNAME}"
  export STREAMCLIPPER_PUBLIC_URL
fi
export STREAMCLIPPER_TRUST_PROXY=1
mkdir -p "$STREAMCLIPPER_WORKSPACE" "$STREAMCLIPPER_CONFIG_DIR" "$XDG_CACHE_HOME"
exec streamclipper dashboard --lan --host 0.0.0.0 --port "$PORT" --workspace "$STREAMCLIPPER_WORKSPACE" --no-browser
