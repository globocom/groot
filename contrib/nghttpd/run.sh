#!/bin/bash

nghttpd $1 -d . --no-content-length -n $[($(nproc)/4)+1] 8445 ./server.key ./server.crt
