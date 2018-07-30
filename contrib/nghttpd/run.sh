#!/bin/bash

if [ "x$(uname)" == "xDarwin" ]; then
  numcores="$(sysctl -n machdep.cpu.core_count)"
else
  numcores="$(nproc)"
fi

nghttpd $1 -d . --no-content-length -n $[($numcores/4)+1] 8445 ./server.key ./server.crt
