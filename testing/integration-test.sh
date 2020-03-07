#!/usr/bin/env bash

CWD=`basename $PWD`

if [ "$CWD" == "testing" ]; then
  cd ..
fi

set -e

find ../aws-greengrass-lambda-functions -name "build.gradle" -exec sh -c 'cd $(dirname {}); ./gradlew clean' \;
find ../aws-greengrass-lambda-functions/functions -name "venv" -exec rm -rf {} \;

./testing/push-to-dockerhub.sh

rm -rf build out credentials
./gradlew clean build integrationTest $@ --info
rm -rf out credentials
