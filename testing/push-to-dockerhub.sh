#!/usr/bin/env bash

set -e
set -x

CWD=`basename $PWD`

if [ "$CWD" == "testing" ]; then
  cd ..
fi

TAG=`git symbolic-ref --short HEAD`

./build.sh

docker tag aws-greengrass-provisioner timmattison/aws-greengrass-provisioner:$TAG
docker push timmattison/aws-greengrass-provisioner:$TAG
