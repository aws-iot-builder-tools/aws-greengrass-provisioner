#!/usr/bin/env bash

set -x

CERTIFICATE_ARN_FILE=certificate.arn
CERTIFICATE_ID_FILE=certificate.id
P11_PROVIDER=""
PKCS11_ENGINE_FOR_CURL=""
OPENSSL_ENGINE_PATH=$(find /usr/lib /usr/lib64 -name "libpkcs11.so" | head -n 1)

function error() {
  ARGS=$@
  echo "ERROR: $ARGS"
  exit 1
}

function success() {
  ARGS=$@
  echo "SUCCESS: $ARGS"
  exit 0
}

function finish() {
  CERTIFICATE_ARN=$(cat $CERTIFICATE_ARN_FILE)
  echo "If you are using GGP your HSI options will be:"
  echo " "
  echo "  --hsi P11Provider=$P11_PROVIDER,slotLabel=greengrass,slotUserPin=1234,OpenSSLEngine=$OPENSSL_ENGINE_PATH$PKCS11_ENGINE_FOR_CURL --certificate-arn $CERTIFICATE_ARN"
  echo " "
  success "$CERTIFICATE_ARN"
}

hash aws &>/dev/null

if [ $? -ne 0 ]; then
  error "AWS CLI not found. It can be installed with 'pip3 install awscli' if you have pip3 installed. On Debian based distros install python3-pip if you need pip3. You may have to log back in for the tool to be added to your path if it is not installed globally."
fi

hash jq &>/dev/null

if [ $? -ne 0 ]; then
  error "jq not found, on Debian based distros install jq"
fi

aws sts get-caller-identity >/dev/null

if [ $? -ne 0 ]; then
  error "An error occurred with the AWS CLI. Configure your CLI or supply temporary configuration values in environment variables and try again."
fi

if [ -z "$1" ]; then
  error "You must specify the name of the CSR file"
fi

CSR_FILE=$1

if [ -z "$2" ]; then
  error "You must specify the name of the P11Provider library after the name of the CSR"
fi

P11_PROVIDER=$2

if [ ! -z "$3" ]; then
  PKCS11_ENGINE_FOR_CURL=",pkcs11EngineForCurl=$3"
fi

if [ ! -f "$CSR_FILE" ]; then
  error "The CSR file [$CSR_FILE] does not exist"
fi

if [ -f "$CERTIFICATE_ARN_FILE" ]; then
  echo "$CERTIFICATE_ARN_FILE already exists, checking if cert still exists"
  aws iot describe-certificate --certificate-id $(cat $CERTIFICATE_ID_FILE) >/dev/null

  if [ $? -eq 0 ]; then
    # Certificate still exists
    echo "Certificate still exists in AWS IoT, nothing to do"
    finish
  fi

  # Certificate does not exist in AWS IoT, try to sign the CSR again
  echo "Certificate does not exist in AWS IoT, recreating"
fi

OPENSSL_ENGINE_NAME=libpkcs11.so
OPENSSL_ENGINE_PATH=$(find /usr/lib /usr/lib64 -name "$OPENSSL_ENGINE_NAME" | head -n 1)

if [ -z "$OPENSSL_ENGINE_PATH" ]; then
  error "$OPENSSL_ENGINE_NAME not found, can not continue"
fi

CERTIFICATE_INFO=$(aws iot create-certificate-from-csr --set-as-active --certificate-signing-request file://$CSR_FILE)

CERTIFICATE_ARN=$(jq --raw-output .certificateArn <(echo $CERTIFICATE_INFO))
CERTIFICATE_PEM=$(jq .certificatePem <(echo $CERTIFICATE_INFO) | sed 's/\"//g')
CERTIFICATE_ID=$(jq --raw-output .certificateId <(echo $CERTIFICATE_INFO))

echo $CERTIFICATE_ARN >$CERTIFICATE_ARN_FILE
echo -e $CERTIFICATE_PEM >certificate.pem
echo $CERTIFICATE_ID >certificate.id

if [[ $CERTIFICATE_ARN == arn:* ]]; then
  finish
else
  error "Failed to get CSR signed by AWS IoT Core"
fi
