#!/usr/bin/env bash

./gradlew :source-service:assembleRelease

# TODO: sign the APK

sudo docker build -t source-service .
