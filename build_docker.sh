#!/usr/bin/env bash

set -e

./gradlew :source-service:assembleDebug

# TODO: sign the APK

sudo docker build -t service-runner .
