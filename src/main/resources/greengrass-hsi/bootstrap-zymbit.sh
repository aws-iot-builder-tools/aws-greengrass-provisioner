#!/usr/bin/env bash

set -x

CSR=zymbit.csr
LIBZKPKCS11_SO_NAME="libzk_pkcs11.so"
PKCS11_ENGINE_FOR_CURL="zymkey_ssl"

function find_library() {
  LIBZKPKCS11_SO=$(find /usr/lib /usr/lib64 -name "$LIBZKPKCS11_SO_NAME" | head -n 1)
}

function finish() {
  if [ -z "$LIBZKPKCS11_SO" ]; then
    find_library
  fi

  ./bootstrap-common.sh $CSR $LIBZKPKCS11_SO $PKCS11_ENGINE_FOR_CURL
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

APT_GET=$(which apt-get)
INSTALLER_UPDATE=$APT_GET
INSTALLER=$APT_GET

# Avoid preinst and postinst tasks from asking questions
export DEBIAN_FRONTEND=noninteractive
INSTALLER_SPECIFIC_PACKAGES="jq python3-pip opensc"

sudo $INSTALLER update -y
sudo $INSTALLER upgrade -y
sudo $INSTALLER install -y $INSTALLER_SPECIFIC_PACKAGES

sudo pip3 install awscli

# NOTE: -c operator checks if a file is a character device, -f checks for regular files and will not work here
if [ ! -c /dev/i2c-1 ]; then
  echo "I2C not enabled, enabling..."

  # Add i2c-dev to the list of modules to load at boot time if it isn't there already
  if ! sudo grep -q "^i2c[-_]dev" /etc/modules; then
    sudo bash -c "printf \"i2c-dev\n\" >> /etc/modules"
  fi

  # Force i2c on in /boot/config.txt
  sudo sed -i 's/#dtparam=i2c_arm=on/dtparam=i2c_arm=on/' /boot/config.txt

  # Force i2c on with dtparam
  sudo dtparam i2c_arm=on

  # Load i2c module
  sudo modprobe i2c_dev

  if [ ! -c /dev/i2c-1 ]; then
    error "Failed to enable I2C, enable it manually with raspi-config and try again"
  fi
fi

hash zk_pkcs11-util &>/dev/null

if [ $? -ne 0 ]; then
  echo "ERROR: zk_pkcs11-util not found, running Zymbit installer. Re-run the HSI bootstrap after the device reboots."
  curl -G https://s3.amazonaws.com/zk-sw-repo/install_zk_sw.sh | sudo bash
fi

# Make sure the user is added to the necessary group so they can use the tools later without being root
sudo usermod -a -G zk_pkcs11 pi

hash pkcs11-tool &>/dev/null

if [ $? -ne 0 ]; then
  error "pkcs11-tool not found, on Debian based distros install opensc"
fi

find_library

if [ -z "$LIBZKPKCS11_SO" ]; then
  error "$LIBZKPKCS11_SO_NAME not found, can not continue"
fi

sudo pkcs11-tool --login --module $LIBZKPKCS11_SO --list-objects --pin 1234 2>&1 | grep -q iotkey >/dev/null

if [ $? -ne 0 ]; then
  SLOT_NUMBER=$(sudo zk_pkcs11-util --init-token --slot 0 --label "greengrass" --pin 1234 --so-pin 1234 | sed 's/[^0-9]//g')

  if [ $? -ne 0 ]; then
    error "Failed to initialize token with zk_pkcs11-util"
  fi

  sudo zk_pkcs11-util --use-zkslot 0 --slot $SLOT_NUMBER --label iotkey --id 0000 --pin 1234
else
  echo "Keys already exist, skipping token initialization and private key creation"
fi

sudo openssl req -nodes -key "pkcs11:token=greengrass;object=iotkey;type=private" -new -out $CSR -engine $PKCS11_ENGINE_FOR_CURL -keyform e -subj "/CN=Zymbit-device"

if [ ! -f "$CSR" ]; then
  error "The CSR file [$CSR] was not found. CSR generation failed."
fi

sudo chown $USER $CSR

if [ ! -f "bootstrap-common.sh" ]; then
  error "bootstrap-common.sh was not found, you will have to manually sign the CSR, activate the certificate, and copy the certificate file back to this device."
fi

finish
