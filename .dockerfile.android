# NimHUB Vpn deterministic Android release builder.
FROM golang:1.25.12-bookworm AS go

FROM node:24-bookworm AS build

COPY --from=go /usr/local/go /usr/local/go
ENV PATH="/usr/local/go/bin:${PATH}"
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        openjdk-17-jdk-headless \
        unzip \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL -o /tmp/android-commandlinetools.zip \
        https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q /tmp/android-commandlinetools.zip -d /tmp/android-commandlinetools \
    && mv /tmp/android-commandlinetools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -rf /tmp/android-commandlinetools /tmp/android-commandlinetools.zip \
    && yes | sdkmanager --licenses >/dev/null \
    && sdkmanager \
        "platform-tools" \
        "platforms;android-36" \
        "build-tools;36.0.0" \
        "ndk;28.0.13004108"

WORKDIR /src
COPY . .

RUN chmod +x ./gradlew ./scripts/build-sing-box-android.sh \
    && ./scripts/build-sing-box-android.sh

ARG ANDROID_RELEASE_KEYSTORE_B64
ARG ANDROID_RELEASE_KEY_ALIAS
ARG ANDROID_RELEASE_KEY_PASSWORD
ARG ANDROID_RELEASE_STORE_PASSWORD

RUN test -n "${ANDROID_RELEASE_KEYSTORE_B64}" \
    && test -n "${ANDROID_RELEASE_KEY_ALIAS}" \
    && test -n "${ANDROID_RELEASE_KEY_PASSWORD}" \
    && test -n "${ANDROID_RELEASE_STORE_PASSWORD}" \
    && printf '%s' "${ANDROID_RELEASE_KEYSTORE_B64}" | base64 -d > /tmp/nimhub-release.jks \
    && ANDROID_RELEASE_STORE_FILE=/tmp/nimhub-release.jks \
       ANDROID_RELEASE_KEY_ALIAS="${ANDROID_RELEASE_KEY_ALIAS}" \
       ANDROID_RELEASE_KEY_PASSWORD="${ANDROID_RELEASE_KEY_PASSWORD}" \
       ANDROID_RELEASE_STORE_PASSWORD="${ANDROID_RELEASE_STORE_PASSWORD}" \
       ./gradlew :apps:android:assembleRelease --stacktrace \
    && APK="$(find apps/android/build/outputs/apk/release -name '*.apk' -type f | head -n 1)" \
    && test -n "${APK}" \
    && "${ANDROID_HOME}/build-tools/36.0.0/apksigner" verify --verbose --print-certs "${APK}" > /tmp/apksigner.txt \
    && SHA1="$(sed -n 's/^Signer #1 certificate SHA-1 digest: //p' /tmp/apksigner.txt | head -n 1)" \
    && SHA256="$(sha256sum "${APK}" | awk '{print $1}')" \
    && test -n "${SHA1}" \
    && mkdir -p /out \
    && cp "${APK}" /out/NimHUB-Vpn-2.6.11-release.apk \
    && printf '{"version":"2.6.11","variant":"release","applicationId":"org.quickping","sha1":"%s","sha256":"%s"}\n' "${SHA1}" "${SHA256}" > /out/metadata.json \
    && rm -f /tmp/nimhub-release.jks

FROM node:24-bookworm-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /out/NimHUB-Vpn-2.6.11-release.apk /app/NimHUB-Vpn.apk
COPY --from=build /out/metadata.json /app/metadata.json
COPY scripts/android-artifact-server.js /app/server.js
CMD ["node", "/app/server.js"]
