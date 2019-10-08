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

jq -r '.["certs/core.crt"]' $OUTFILE > certs/core.crt
jq -r '.["certs/core.key"]' $OUTFILE > certs/core.key
jq -r '.["certs/root.ca.pem"]' $OUTFILE > certs/root.ca.pem
jq -r '.["config/config.json"]' $OUTFILE > config/config.json

tar cjvf $GROUP_NAME.tar.bz2 certs config
rm -rf certs config
