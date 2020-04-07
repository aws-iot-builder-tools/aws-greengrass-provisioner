# Deployment command-line options

## Architecture

Long form: `--arch`

Short form: `-a`

Possible values:
- `ARM32`
- `X86_64`
- `ARM64`

This is only useful when using the `--script` option.  This tells GGP which Greengrass Core bits to include in the
bootstrap script.

## Group name

Required: Always (except for EC2 launches)

Long form: `--group-name`

Short form: `-g`

Specifies the name of the group that you want to work with.  The thing name of the core will be the value you specify
for the group name followed by `_Core`.

## Deployment config

Required: Always

Long form: `--deployment-config`

Short form: `-d`

Specifies the file to read the deployment configuration from.

## Launch EC2 instance

Long form: `--ec2-launch`

Launches an EC2 instance, sends the bootstrap script to it, and starts Greengrass Core on that instance.

Valid options: `Ubuntu1804` and `AmazonLinux2` depending on which operating system should be launch on the EC2 instance.

If no group name is specified with this option one will be randomly generated.

If an architecture is specified it will attempt to launch an Ubuntu image on the specified platform (ARM64 and X86-64 are supported).

This option will only work on a new group and will refuse to run if the group already exists.  This is to prevent
Greengrass Cores from running on multiple instances at the same time.  To update an EC2 instance that has been built
with this option simply omit the `--ec2-launch` option on subsequent runs and make sure the group name is specified.

Example (initial launch):

```bash
GGP -d deployments/python3-hello-world.conf -g newgroup --ec2-launch
```

Example (subsequent runs):

```bash
GGP -d deployments/python3-hello-world.conf -g newgroup
```

## Launch Docker container

Long form: `--docker-launch`

Launches a Docker container locally using the official Docker images and the specified deployment.

If no group name is specified with this option one will be randomly generated.
  
This option will work on new and existing groups.  The name of the Docker container launched with this command will be
the name of the group.  This is to prevent Greengrass Cores from running in multiple containers at the same time.

To update a Docker container that has been built with this option simply omit the `--docker-launch` option on subsequent
runs and make sure the group name is specified.  If you specify this option on a group that is already running in a
Docker container you will receive a warning like this:

```
[WARN] GreengrassDockerHelper: The Docker container for this core is already running locally, the core should be redeploying now
```

Example (initial launch):

```bash
GGP -d deployments/python3-hello-world.conf -g newgroup --docker-launch
```

Example (subsequent runs):

```bash
GGP -d deployments/python3-hello-world.conf -g newgroup
```

**Note: This option forces the architecture to `X86_64` and enables script output `--script`.**

## Build container

Long form: `--build-container`

Short form: `-c`

When specified this tells GGP to create a Docker container for the core.  The image name is the name of the group and
the tag is `latest`.

## Push container

Long form: `--push-container`

Short form: `-p`

When specified this tells GGP to push the Docker container for the core to Amazon Elastic Container Registry (ECR).

## ECR repository name

Long form: `--ecr-repository-name`

Short form: `-r`

This changes the default ECR repository name from `greengrass` to whatever is specified.

## ECR image name

Long form: `--ecr-image-name`

Short form: `-i`

This changes the default Docker image name from the group name to whatever is specified.

## Generate bootstrap script

Long form: `--script`

Generates a bootstrap script for Amazon Linux, Ubuntu, and Raspbian and stores it in `build/gg.GROUP_NAME.sh` where
`GROUP_NAME` is the name of the group.

## Generate OEM package

Long form: `--oem`

Generates an archive containing the core's public key, private key, public signed certificate, config.json, and root CA.
This is useful for OEM devices where Greengrass is pre-installed.

## Use Greengrass [Hardware Security Integration](https://docs.aws.amazon.com/greengrass/latest/developerguide/hardware-security.html) (HSI)

Long form: `--hsi`

Generates a configuration and bootstrap scripts that use Greengrass Hardware Security Integration (HSI). This option
requires that the certificate already be registered in AWS IoT.

This option has multiple sub-options and is specified as key-value pairs in a comma-separated list.

The key-value pairs expected are:
- `P11Provider` - the path to the shared object (so) to be used as the `P11Provider` value in the `PKCS11` configuration block
- `slotLabel` - the name of the slot to be used as the `slotLabel` value in the `PKCS11` configuration block
- `slotUserPin` - the PIN of the slot to be used as the `slotUserPin` value in the `PKCS11` configuration block
- (Optional) `OpenSSLEngine` - the path to the shared object (so) to be used as the `OpenSSLEngine` value in the `PKCS11` configuration block for OTA support
- `pkcs11EngineForCurl` - the engine value to use with curl (`--engine VALUE`) to make HTTPS requests with the HSI hardware. This is used in the credentials.sh script when using the AWS CLI on the Greengrass Core with the Greengrass Core's certificate.

A simple example configuration for SoftHSM2 on a Raspberry Pi might look like this:

```
--hsi P11Provider=/usr/lib/arm-linux-gnueabihf/softhsm/libsofthsm2.so,slotLabel=greengrass,slotUserPin=1234,pkcs11EngineForCurl=pkcs11
```

## Do not use systemd

Long form: `--no-systemd`

Prevents the bootstrap script from adding Greengrass to systemd.

## Put artifacts into S3

Long form: `--s3-bucket`

Long form: `--s3-directory`

Stores build artifacts (OEM file or bootstrap script) into S3. Bucket and directory must both be specified.

## Bootstrap a non-EC2 host

Long form: `--launch`

Copies the bootstrap script to a host via scp then attempts to SSH to the host and run the bootstrap script with the `--now` option in a screen session named `greengrass`.

The format for the parameter is the same as SSH (e.g. `pi@192.168.1.5`).

## Force create new keys

Long form: `--force-create-new-keys`

Forces the creation of new keys if the keys for the core can not be found. This is only necessary when creating a new OEM
or bootstrap file for a group. These files require the private keys and therefore if they are not found they must be
recreated. Requiring this option prevents GGP from generating new keys for a Greengrass core that is already deployed so that it
does not lose connectivity to AWS IoT Core.

## Service role exists

Long form: `--service-role-exists`

Prevents GGP from creating a new Greengrass service role. This can be used when the user running GGP does not have sufficient
privileges to create a service role and one has already been created.

## Create a new certificate for a keypair using a CSR

Long form: `--csr`

GGP will use the CSR in this argument and have it signed by AWS IoT. This allows a user to keep their private keys secret.
Any output files that are generated will not contain the private key.

This option can either contain the entire CSR as an inline string or can be the filename of a file that contains the CSR.

## Use an existing certificate

Long form: `--certificate-arn`

GGP will use the certificate referenced by the exact certificate ARN in this argument. Any output files that are generated
will not contain the private key.
