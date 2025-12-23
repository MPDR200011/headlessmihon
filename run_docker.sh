#!/usr/bin/env bash

sudo docker run -it -p 8081:8081 -p 8090:8090 --device /dev/kvm mpdr/headlessmihon
