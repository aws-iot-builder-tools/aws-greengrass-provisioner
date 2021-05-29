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

rm -rf certs config
mkdir certs
mkdir config

jq -r '.["coreCrt"]' $OUTFILE > certs/core.crt
jq -r '.["coreKey"]' $OUTFILE > certs/core.key
jq -r '.["rootCaPem"]' $OUTFILE > certs/root.ca.pem
jq -r '.["configJson"]' $OUTFILE > config/config.json

tar cjvf $GROUP_NAME.tar.bz2 certs config
rm -rf certs config
