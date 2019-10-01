#!/bin/bash

# GROOT

docker-compose up -d --build; sleep 5
while ! curl --connect-timeout 1 http://127.0.0.1:8080 > /dev/null 2> /dev/null; do 
  echo "Waiting 8080/tcp... (press CTRL+C to cancel)"; 
  sleep 5;
done
