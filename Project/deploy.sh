#!/bin/bash

set -euxo pipefail

pushd frontend/ShareaPicture/
./gradlew assembleDebug
cp app/build/outputs/apk/app-debug.apk ../../
popd

pushd backend/
gcloud config set project mcc-fall-2017-g14
echo y | gcloud app deploy app.yaml
echo y | gcloud app deploy cron.yaml
popd
