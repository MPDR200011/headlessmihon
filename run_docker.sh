#!/usr/bin/env bash

sudo docker run -it -p 8081:8081 -p 8090:8090 --env LARAVEL_HOST=http://172.17.0.1:8000 --device /dev/kvm mpdr/headlessmihon
