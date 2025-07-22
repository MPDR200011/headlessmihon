#!/usr/bin/env bash

./gradlew :source-service:assembleDebug

# TODO: sign the APK

sudo docker build -t service-runner .
