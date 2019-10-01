#!/bin/bash

## Requirements (on Ubuntu)

### docker
### docker-compose
### curl

sudo apt install docker.io
sudo apt install curl
sudo curl -L https://github.com/docker/compose/releases/download/1.18.0/docker-compose-`uname -s`-`uname -m` -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
