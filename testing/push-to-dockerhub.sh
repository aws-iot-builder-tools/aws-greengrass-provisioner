#!/usr/bin/env bash

set -e
set -x

CWD=$(basename $PWD)

if [ "$CWD" == "testing" ]; then
  cd ..
fi

TAG=$1

if [ -z "$TAG" ]; then
  TAG=$(git symbolic-ref --short HEAD | tr -cd '[:alnum:]._-')

  if [ $? -ne 0 ]; then
    TAG=$(git rev-parse HEAD)
  fi
else
  echo "Manually specified branch $TAG"
fi

./build-ggp-docker-container.sh

docker tag aws-greengrass-provisioner timmattison/aws-greengrass-provisioner:$TAG
docker push timmattison/aws-greengrass-provisioner:$TAG
