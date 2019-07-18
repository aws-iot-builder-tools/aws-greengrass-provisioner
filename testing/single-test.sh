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

./testing/integration-test.sh --tests '*'$TEST_NAME'*'
