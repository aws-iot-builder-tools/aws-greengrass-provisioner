# Greengrass Hardware Security Integration (HSI) bootstrapping command-line options

**Note: To use HSI bootstrapping you must use the `--hsi-bootstrap` option.  It is a flag and does not take any arguments.**

**Note: This feature is tested on Ubuntu 18.04, Amazon Linux 2, and Raspbian. If you are using a different Linux distribution it may not be compatible.**

## How it works

HSI bootstrapping copies two bootstrap scripts to a specified host via scp. One script is a vendor specific bootstrapping script,
the other script is a generic bootstrapping script that will get the output CSR from the vendor specific bootstapping process
signed by AWS IoT.

After the scripts are copied to the target GGP attempts to SSH to the it and run the vendor specific bootstrap script.
When it does this it passes along STS (temporary) AWS credentials of the user running GGP to the target. This done so that
the generic bootstrapping script can get the CSR signed by AWS IoT.

If the user running this command does not have access to call [CreateCertificateFromCsr](https://docs.aws.amazon.com/iot/latest/apireference/API_CreateCertificateFromCsr.html)
then this command will fail.

## Expected output

This feature will stream the status of the bootstrapping process to the console. If this process fails an attempt is made
to provide a recommendation on how to fix it. Some vendors require a device to be rebooted. In this case this command
will need to be executed twice.

Once bootstrapping is complete there should be an output message that looks like this:

```
--hsi P11Provider=/usr/lib/p11provider.so,slotLabel=greengrass,slotUserPin=1234,pkcs11EngineForCurl=pkcs11 --certificate-arn arn:aws:iot:REGION:ACCOUNT_ID:cert/FINGERPRINT
```

The `--hsi` and `--certificate-arn` values can be fed back to GGP to generate Greengrass bootstrapping scripts that utilize HSI.

## Vendor

Required: Always

Long form: `--vendor`

Specifies the name of the vendor supplying the HSI support.

Valid values: `SoftHSM2`

## Target

Required: Always

Long form: `--target`

Copies the bootstrap script to a host via scp then attempts to SSH to the host and run the bootstrap script. This passes
along STS (temporary) AWS credentials of the user running GGP to the target so that it can get the CSR signed by AWS IoT.
If the user running this command does not have access to call [CreateCertificateFromCsr](https://docs.aws.amazon.com/iot/latest/apireference/API_CreateCertificateFromCsr.html)
then this command will fail.

The format for the parameter is the same as SSH (e.g. `pi@192.168.1.5`).
