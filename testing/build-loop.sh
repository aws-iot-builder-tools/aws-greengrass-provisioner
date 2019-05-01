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

    if [[ $signature1 != $signature2 ]] ; then
        until time ./testing/push-to-dockerhub.sh
    	do
            echo "Build failed, retrying"
            sleep 10
        done
        
        signature1=$signature2
    fi

    sleep 1
done
