#!/usr/bin/env bash
docker build -t npmbin -f Dockerfile.npm-layer .
docker run npmbin cat /tmp/npm-layer.zip > ./layers/npm-layer.zip