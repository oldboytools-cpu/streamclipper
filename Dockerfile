FROM python:3.12-slim

ENV PYTHONUTF8=1 PIP_DISABLE_PIP_VERSION_CHECK=1 PIP_ROOT_USER_ACTION=ignore \
    STREAMCLIPPER_CONFIG_DIR=/data/config \
    XDG_CACHE_HOME=/data/cache \
    STREAMCLIPPER_WORKSPACE=/data/StreamClipper

RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg fonts-dejavu-core libglib2.0-0 libgl1 libegl1 libgles2 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY . .
RUN python -m pip install --no-cache-dir ".[asr,vision]"
COPY deploy/render-start.sh /usr/local/bin/render-start
RUN chmod +x /usr/local/bin/render-start
EXPOSE 10000
CMD ["/usr/local/bin/render-start"]
