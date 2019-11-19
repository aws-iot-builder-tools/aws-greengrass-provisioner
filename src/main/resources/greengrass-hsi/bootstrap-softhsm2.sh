#!/usr/bin/env bash

set -x

CSR=softhsm2.csr
LIBSOFTHSM2_SO_NAME="libsofthsm2.so"
PKCS11_ENGINE_FOR_CURL="pkcs11"

function find_library() {
  LIBSOFTHSM2_SO=$(find /usr/lib /usr/lib64 -name "$LIBSOFTHSM2_SO_NAME" | head -n 1)
}

function finish() {
  if [ -z "$LIBSOFTHSM2_SO" ]; then
    find_library
  fi

  ./bootstrap-common.sh $CSR $LIBSOFTHSM2_SO $PKCS11_ENGINE_FOR_CURL
  exit 0
}

if [ -f "$CSR" ]; then
  echo "$CSR already exists, skipping CSR generation"
  finish
fi

function error() {
  echo "ERROR: $1"
  exit 1
}

# Necessary OpenSSL config
OPENSSL_CONFIG_FILE=openssl.config

read -r -d '' OPENSSL_CONFIG <<EOF
[ req ]
distinguished_name      = req_distinguished_name
attributes              = req_attributes

[ req_distinguished_name ]

[ req_attributes ]
EOF

APT_GET=$(which apt-get)
APT_GET_MISSING=$?

YUM=$(which yum)
YUM_MISSING=$?

if [ $YUM_MISSING -eq 0 ]; then
  INSTALLER_UPDATE=$YUM
  INSTALLER=$YUM

  # Add EPEL to get openssl-pkcs11
  sudo $INSTALLER install -y https://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm

  INSTALLER_SPECIFIC_PACKAGES="softhsm opensc openssl-pkcs11 jq"

  sudo $INSTALLER update -y
  sudo $INSTALLER install -y $INSTALLER_SPECIFIC_PACKAGES
fi

if [ $APT_GET_MISSING -eq 0 ]; then
  INSTALLER_UPDATE=$APT_GET
  INSTALLER=$APT_GET

  # Avoid preinst and postinst tasks from asking questions
  export DEBIAN_FRONTEND=noninteractive
  INSTALLER_SPECIFIC_PACKAGES="softhsm2 opensc libengine-pkcs11-openssl jq python3-pip"

  sudo $INSTALLER update -y
  sudo $INSTALLER upgrade -y
  sudo $INSTALLER install -y $INSTALLER_SPECIFIC_PACKAGES

  sudo pip3 install awscli

  # Fix "ERROR: Could not initialize the library." error due to missing directory
  sudo mkdir -p /var/lib/softhsm/tokens

  # Necessary on Ubuntu
  sudo echo "$OPENSSL_CONFIG" >$OPENSSL_CONFIG_FILE
  OPENSSL_CONFIG_OPTION="--config $OPENSSL_CONFIG_FILE"
fi

if [ -z "$INSTALLER" ]; then
  echo "Couldn't determine package manager, any missing packages will need to be installed manually"
fi

hash softhsm2-util &>/dev/null

if [ $? -ne 0 ]; then
  error "softhsm2-util not found, on Debian based distros install softhsm2"
fi

hash pkcs11-tool &>/dev/null

if [ $? -ne 0 ]; then
  error "pkcs11-tool not found, on Debian based distros install opensc"
fi

MATCHES=$(sudo find / -name "pkcs11.so" -print -quit | wc -l)

if [ "$MATCHES" -eq 0 ]; then
  error "pkcs11.so for OpenSSL not found, on Debian based distros install libengine-pkcs11-openssl"
fi

find_library

if [ -z "$LIBSOFTHSM2_SO" ]; then
  error "$LIBSOFTHSM2_SO_NAME not found, can not continue"
fi

sudo pkcs11-tool --login --module $LIBSOFTHSM2_SO --list-objects --pin 1234 2>&1 | grep -q iotkey >/dev/null

if [ $? -ne 0 ]; then
  sudo softhsm2-util --init-token --free --label "greengrass" --pin 1234 --so-pin 1234
  sudo pkcs11-tool --module $LIBSOFTHSM2_SO -l -k --key-type rsa:2048 --id 0000 --label iotkey --pin 1234
else
  echo "Keys already exist, skipping token initialization and private key creation"
fi

# OPENSSL_CONFIG_OPTION is necessary on Ubuntu, on Amazon Linux it is ignored
sudo openssl req -new $OPENSSL_CONFIG_OPTION -key "PKCS11_URL" -out $CSR -engine $PKCS11_ENGINE_FOR_CURL -keyform engine -subj "/CN=SoftHSM2-device" &
sleep 1
SUDO_PID=$!
OPENSSL_PID=$(ps --ppid $SUDO_PID -o pid=)
sleep 2
sudo kill -9 $OPENSSL_PID 2>/dev/null && echo "OpenSSL was killed while generating the CSR. This needs to be done on some operating systems due to issues with SoftHSM2 and/or PKCS11."

if [ ! -f "$CSR" ]; then
  error "The CSR file [$CSR] was not found. CSR generation failed."
fi

sudo chown $USER $CSR

if [ ! -f "bootstrap-common.sh" ]; then
  error "bootstrap-common.sh was not found, you will have to manually sign the CSR, activate the certificate, and copy the certificate file back to this device."
fi

finish
