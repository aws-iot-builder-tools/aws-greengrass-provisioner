#!/usr/bin/env bash

CWD=`basename "$PWD"`

if [ "$CWD" == "testing" ]; then
  cd ..
fi

./testing/gradle-loop.sh build
