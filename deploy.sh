#!/usr/bin/env bash

set -e

AWS_ACCESS_KEY_ID=$(aws configure get aws_access_key_id)
AWS_SECRET_ACCESS_KEY=$(aws configure get aws_secret_access_key)
REGION=$(aws configure get region)

if [ -z "$REGION" ]; then
  echo The REGION variable is not set, cannot continue
  exit 1
fi

PWD=$(pwd)

if git symbolic-ref --short HEAD > /dev/null 2>&1 ; then
  TAG=`git symbolic-ref --short HEAD`
else
  TAG=latest
fi

docker pull timmattison/aws-greengrass-provisioner:$TAG

docker run \
   -v $PWD/foundation:/foundation \
   -v $PWD/deployments:/deployments \
   -v $PWD/functions:/functions \
   -v $PWD/credentials:/credentials \
   -v $PWD/ggds:/ggds \
   -v $PWD/build:/build \
   -v $HOME/.ssh:/root/.ssh \
   -it --rm \
   -e AWS_REGION=$REGION \
   -e AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID \
   -e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY \
   timmattison/aws-greengrass-provisioner:$TAG $@
