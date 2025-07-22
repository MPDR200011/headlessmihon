#!/usr/bin/env bash

sudo docker run -dt -p 8081:8081 --device /dev/kvm service-runner
