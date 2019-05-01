#!/usr/bin/env bash

CWD=`basename "$PWD"`

if [ "$CWD" == "testing" ]; then
  cd ..
fi

signature1=""

while [[ true ]]
do
    # Simple mechanism to see if anything in the src directory changed since the last run
    # grep -v command makes sure that we ignore parent directory entries otherwise any
    #   files changed in the parent of the src directory would trigger a rebuild
    signature2=`ls -laFR src | grep -v "\.\.\/" | sort`

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
