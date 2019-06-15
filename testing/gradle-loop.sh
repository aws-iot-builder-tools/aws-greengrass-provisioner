#!/usr/bin/env bash

CWD=`basename "$PWD"`

if [ "$CWD" == "testing" ]; then
  cd ..
fi

signature1=""

while [[ true ]]
do
    # Simple mechanism to see if anything in the directory changed since the last run
    signature2=`find . -not -type d -ls | sort | shasum`

    if [[ $signature1 != $signature2 ]]; then
        ./gradlew compileJava

        signature1=$signature2
    fi

    sleep 1
done
