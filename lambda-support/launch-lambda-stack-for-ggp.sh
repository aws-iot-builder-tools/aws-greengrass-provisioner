#!/usr/bin/env bash

set -e

BUCKET_NAME=$1
STACK_NAME=$2

if [ -z "$BUCKET_NAME" ];
then
  echo "A bucket name must be specified"
  exit 1
fi

if [ -z "$STACK_NAME" ];
then
  STACK_NAME=$(uuidgen | sed s/^/A/ )
  echo "No stack name specified, generating a random stack name [$STACK_NAME]" 
fi

echo "Launching (or updating) stack [$STACK_NAME]"

# Use this value if you have multiple profiles
# PROFILE="--profile default"
PROFILE=""

TEMP_FILE=$(mktemp)
pushd .
cd ..
./gradlew build
popd
aws cloudformation $PROFILE package --template-file lambda-stack-for-ggp.yaml --s3-bucket $BUCKET_NAME --output-template-file $TEMP_FILE
aws cloudformation $PROFILE deploy --stack-name $STACK_NAME --template-file $TEMP_FILE --capabilities CAPABILITY_NAMED_IAM || aws cloudformation $PROFILE describe-stack-events --stack-name $STACK_NAME
rm $TEMP_FILE
