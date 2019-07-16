#!/usr/bin/env bash

set -e

STACK_NAME=$1
GROUP_NAME=$1

if [ -z "$GROUP_NAME" ];
then
  echo "Group name must be specified"
  exit 1
fi

echo "Extracting values from group $GROUP_NAME"
OUTFILE=$GROUP_NAME.outfile.txt

jq -r '.["certs/core.crt"]' $OUTFILE > $GROUP_NAME.core.crt
jq -r '.["certs/core.key"]' $OUTFILE > $GROUP_NAME.core.key
jq -r '.["certs/root.ca.pem"]' $OUTFILE > $GROUP_NAME.root.ca.pem
jq -r '.["config/config.json"]' $OUTFILE > $GROUP_NAME.config.json
