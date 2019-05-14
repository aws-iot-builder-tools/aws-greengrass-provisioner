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

If no group name is specified with this option one will be randomly generated.

If an architecture is specified it will attempt to launch an Ubuntu image on the specified platform (ARM64 and X86-64 are supported).

This option will only work on a new group and will refuse to run if the group already exists.  This is to prevent
Greengrass Cores from running on multiple instances at the same time.  To update an EC2 instance that has been built
with this option simply omit the `--ec2-launch` option on subsequent runs and make sure the group name is specified.

Example (initial launch):

```bash
GGP -d deployments/python-hello-world.conf -g newgroup --ec2-launch
```

Example (subsequent runs):

```bash
GGP -d deployments/python-hello-world.conf -g newgroup
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
GGP -d deployments/python-hello-world.conf -g newgroup --docker-launch
```

Example (subsequent runs):

```bash
GGP -d deployments/python-hello-world.conf -g newgroup
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

## Generate Greengrass Device scripts

Long form: `--ggd`

Generates the Greengrass Device scripts that can be used to test the core's connectivity info and discovery information.
This is stored in `build/ggd.GROUP_NAME.sh` which will extract the GGD scripts, configuration, and certificates when a
user runs it.

## Use SoftHSM2 for Greengrass Hardware Security Integration (HSI)

Long form: `--hsi-softhsm2`

Generates a configuration and bootstrap scripts that use Greengrass Hardware Security Integration (HSI) with SoftHSM2. Only
works on Ubuntu. This can be used to test out HSI but is not for production use as it only simulates hardware security.
Works with `--ec2-launch`.

## Do not use systemd

Long form: `--no-systemd`

Prevents the bootstrap script from adding Greengrass to systemd.