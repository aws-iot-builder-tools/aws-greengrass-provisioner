#!/usr/bin/env bash

CWD=`basename $PWD`

if [ "$CWD" == "testing" ]; then
  cd ..
fi

TEST_NAME=$1

if [ -z "$TEST_NAME" ]; then
  echo You must specify a test name or test name pattern
  exit 1
fi

set -e

rm -rf build out credentials
./gradlew clean build integrationTest --tests '*'$TEST_NAME'*'
rm -rf out credentials
