FROM openjdk:18-jdk-slim

LABEL maintainer "mpdr"

ENV DEBIAN_FRONTEND noninteractive

WORKDIR /
#=============================
# Install Dependenices
#=============================
SHELL ["/bin/bash", "-c"]

RUN apt update && apt install -y curl sudo wget unzip bzip2 libdrm-dev libxkbcommon-dev libgbm-dev libasound-dev libnss3 libxcursor1 libpulse-dev libxshmfence-dev xauth xvfb x11vnc fluxbox wmctrl libdbus-glib-1-2

#==============================
# Android SDK ARGS
#==============================
ARG ARCH="x86_64"
ARG TARGET="aosp_atd"
ARG API_LEVEL="35"
ARG BUILD_TOOLS="35.0.0"
ARG ANDROID_ARCH=${ANDROID_ARCH_DEFAULT}
ARG ANDROID_API_LEVEL="android-${API_LEVEL}"
ARG ANDROID_APIS="${TARGET};${ARCH}"
ARG EMULATOR_PACKAGE="system-images;${ANDROID_API_LEVEL};${ANDROID_APIS}"
ARG PLATFORM_VERSION="platforms;${ANDROID_API_LEVEL}"
ARG BUILD_TOOL="build-tools;${BUILD_TOOLS}"
ARG ANDROID_CMD="commandlinetools-linux-11076708_latest.zip"
ARG ANDROID_SDK_PACKAGES="${EMULATOR_PACKAGE} ${PLATFORM_VERSION} ${BUILD_TOOL} platform-tools emulator"

#==============================
# Set JAVA_HOME - SDK
#==============================
ENV ANDROID_SDK_ROOT=/opt/android
ENV PATH "$PATH:$ANDROID_SDK_ROOT/cmdline-tools/tools:$ANDROID_SDK_ROOT/cmdline-tools/tools/bin:$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/tools/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/build-tools/${BUILD_TOOLS}"
ENV DOCKER="true"

#============================================
# Install required Android CMD-line tools
#============================================
RUN wget https://dl.google.com/android/repository/${ANDROID_CMD} -P /tmp && \
              unzip -d $ANDROID_SDK_ROOT /tmp/$ANDROID_CMD && \
              mkdir -p $ANDROID_SDK_ROOT/cmdline-tools/tools && cd $ANDROID_SDK_ROOT/cmdline-tools &&  mv NOTICE.txt source.properties bin lib tools/  && \
              cd $ANDROID_SDK_ROOT/cmdline-tools/tools && ls

#============================================
# Install required package using SDK manager
#============================================
RUN yes Y | sdkmanager --licenses
RUN yes Y | sdkmanager --verbose --no_https ${ANDROID_SDK_PACKAGES}

#============================================
# Create required emulator
#============================================
ARG EMULATOR_NAME="service-runner"
ENV EMULATOR_NAME=$EMULATOR_NAME
RUN echo "no" | avdmanager --verbose create avd --force --name "${EMULATOR_NAME}" --package "${EMULATOR_PACKAGE}"

#===================
# Ports
#===================
EXPOSE 8081 8081/tcp

#=========================
# Copying Scripts to root
#=========================
ARG RUNTIME_ROOT="/run"
COPY ./launch_emulator_headless.sh $RUNTIME_ROOT/launch_emulator_headless.sh

# Copy APK to location
ARG APK_LOCATION="${RUNTIME_ROOT}/artifacts/source-service.apk"
COPY ./source-service/build/outputs/apk/release/source-service-release-unsigned.apk $APK_LOCATION
ENV APK_LOCATION=$APK_LOCATION

RUN ls -la /run/
RUN chmod a+x /run/launch_emulator_headless.sh

#=======================
# framework entry point
#=======================
CMD [ "/run/launch_emulator_headless.sh" ]
