#!/bin/bash

docker run -d --rm -p 9100:9100 \
  -v "/proc:/host/proc:ro" \
  -v "/sys:/host/sys:ro" \
  -v "/:/rootfs:ro" \
  --network host \
  quay.io/prometheus/node-exporter \
  --collector.filesystem.ignored-mount-points "^/(sys|proc|dev|host|etc)($|/)" \
  --collector.tcpstat \
  --collector.meminfo \
  --collector.loadavg \
  --collector.stat \
  --collector.diskstats
