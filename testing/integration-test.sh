#!/usr/bin/env bash

CWD=`basename $PWD`

if [ "$CWD" == "testing" ]; then
  cd ..
fi

set -e

rm -rf build out credentials
./gradlew clean build integrationTest $@
rm -rf out credentials
