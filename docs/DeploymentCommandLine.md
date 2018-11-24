# Deployment command-line options

## Architecture

Long form: `--arch`

Short form: `-a`

Possible values:
- `ARM32`
- `X86_64`
- `ARM64`
- `UBUNTU_X86`

This is only useful when using the `--script` option.  This tells GGP which Greengrass Core bits to include in the
bootstrap script.

## Group name

Required: Always

Long form: `--group-name`

Short form: `-g`

Specifies the name of the group that you want to work with.  The thing name of the core will be the value you specify
for the group name followed by `_Core`.

## Deployment config

Required: Always

Long form: `--deployment-config`

Short form: `-d`

Specifies the file to read the deployment configuration from.

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