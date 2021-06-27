#!/usr/bin/env bash

set -e

GROUP_NAME=$1

if [ -z "$GROUP_NAME" ];
then
  echo "Group name must be specified"
  exit 1
fi

echo "Extracting values from group $GROUP_NAME"
OUTFILE=$GROUP_NAME.outfile.txt

# rm -rf certs config
mkdir -p certs/$GROUP_NAME
mkdir -p config/$GROUP_NAME

jq -r '.["coreCrt"]' $OUTFILE > certs/$GROUP_NAME/core.crt
jq -r '.["coreKey"]' $OUTFILE > certs/$GROUP_NAME/core.key
jq -r '.["rootCaPem"]' $OUTFILE > certs/$GROUP_NAME/root.ca.pem
jq -r '.["configJson"]' $OUTFILE > config/$GROUP_NAME/config.json

# tar cjvf $GROUP_NAME.tar.bz2 certs config
# rm -rf certs config
