#!/usr/bin/env bash

# Check if we're root and re-execute if we're not.
rootcheck() {
  if [ $(id -u) != "0" ]; then
    sudo "$0" "$@"
    exit $?
  fi
}

rootcheck "${@}"

PREFIX=/greengrass

GROUP_ID_FILE=$PREFIX/config/group-id.txt
REGION_FILE=$PREFIX/config/region.txt
THING_NAME_FILE=$PREFIX/config/thing-name.txt
ROLE_ALIAS_NAME_FILE=$PREFIX/config/role-alias-name.txt
CREDENTIAL_PROVIDER_URL_FILE=$PREFIX/config/credential-provider-url.txt
PKCS11_ENGINE_FOR_CURL_FILE=$PREFIX/config/pkcs11-engine-for-curl.txt
PKCS11_PATH_FOR_CURL_FILE=$PREFIX/config/pkcs11-path-for-curl.txt
KEY=$PREFIX/certs/core.key
CERT=$PREFIX/certs/core.crt
CACERT=$PREFIX/certs/root.ca.pem

GROUP_ID=$1

if [ -f "$GROUP_ID_FILE" ]; then
  GROUP_ID=$(cat $GROUP_ID_FILE)
fi

if [ -z "$GROUP_ID" ]; then
  echo "Group ID required"
  exit 1
fi

REGION=$(cat $REGION_FILE)
THING_NAME=$(cat $THING_NAME_FILE)
ROLE_ALIAS_NAME=$(cat $ROLE_ALIAS_NAME_FILE)
CREDENTIAL_PROVIDER_URL="https://"$(cat $CREDENTIAL_PROVIDER_URL_FILE)"/"
FULL_URL=$CREDENTIAL_PROVIDER_URL"role-aliases/"$ROLE_ALIAS_NAME"/credentials"

if [ -f "$PKCS11_ENGINE_FOR_CURL_FILE" ]; then
  PKCS11_ENGINE_FOR_CURL="--engine $(cat $PKCS11_ENGINE_FOR_CURL_FILE) --key-type ENG"
else
  PKCS11_ENGINE_FOR_CURL=""
fi

if [ -f "$PKCS11_PATH_FOR_CURL_FILE" ]; then
  KEY="$(cat $PKCS11_PATH_FOR_CURL_FILE)"
fi

# No longer using the AWS IoT Verisign root CA.  If the distro doesn't have certificate authorities installed this command will probably fail
CREDENTIALS=$(curl -s --cacert $CACERT --cert $CERT $PKCS11_ENGINE_FOR_CURL --key $KEY -H "x-amzn-iot-thingname: $THING_NAME" $FULL_URL)

export AWS_DEFAULT_REGION=$REGION
export AWS_ACCESS_KEY_ID=$(jq --raw-output .credentials.accessKeyId <(echo $CREDENTIALS))
export AWS_SECRET_ACCESS_KEY=$(jq --raw-output .credentials.secretAccessKey <(echo $CREDENTIALS))
export AWS_SESSION_TOKEN=$(jq --raw-output .credentials.sessionToken <(echo $CREDENTIALS))

export EXPIRATION=$(date --date="+120 seconds" -Iseconds)

echo "{ \"Version\": 1, \"AccessKeyId\": \"$AWS_ACCESS_KEY_ID\", \"SecretAccessKey\": \"$AWS_SECRET_ACCESS_KEY\", \"SessionToken\": \"$AWS_SESSION_TOKEN\", \"Expiration\": \"$EXPIRATION\" }"
