# AWS Greengrass Provisioner (AWS GGP or GGP)

Note: Greengrass v2 support is underway now. [Greengrass v1 support is now in its own branch](../../tree/greengrass-v1-support) and the main branch will be used for Greengrass v2 development.

[STOP! If this is your first time here you can get started quickly with the basic tutorials!](docs/BasicTutorials.md)

[![Build Status](https://travis-ci.org/awslabs/aws-greengrass-provisioner.svg?branch=master)](https://travis-ci.org/awslabs/aws-greengrass-provisioner)
[![Open Issues](https://img.shields.io/github/issues-raw/awslabs/aws-greengrass-provisioner.svg)](https://github.com/awslabs/aws-greengrass-provisioner/issues)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/awslabs/aws-greengrass-provisioner/blob/master/LICENSE)

**Note: GGP expects to be used with Greengrass Core 1.10.2 only!**

#### Just here for a quick way to get the Greengrass dependencies installed on Raspbian, Amazon Linux 2, CentOS, Debian, or Ubuntu?

```bash
curl https://raw.githubusercontent.com/awslabs/aws-greengrass-provisioner/master/src/main/resources/shell/update-dependencies.sh.in | sudo bash
```
Simplifies provisioning Greengrass Cores and building Greengrass Lambda functions.  GGP configures Greengrass so you don't have to.

#### What is GGP?

GGP creates the device for the core, Greengrass devices, subscriptions, functions, logger configurations, local resource
definitions, and ML Inference definitions.

GGP builds your Java, Python, and NodeJS functions and puts them into AWS Lambda.

GGP wires your Lambda functions together for local communcation on the Greengrass Core.

GGP can create simulated Greengrass Device scripts that use Greengrass discovery to connect to your core.  You can use
them as a baseline to build more advanced scripts.

GGP creates a bootstrap script that you copy to your host that installs Greengrass.

GGP launches any CloudFormation templates necessary to support your functions.

If there's something it doesn't do or you find a bug please create a Github issue.

For brevity this application will be referred to as GGP in the docs.

# Are you looking to use GGP or are you looking to modify it?

If you only want to use this tool you can simply read the "Want to get started quickly?" section.  You only need to
clone this repo if you want to build the JAR file or modify the code.

# Looking for the ggp.sh script so you can run GGP with Docker?

[ggp.sh](https://github.com/aws-samples/aws-greengrass-lambda-functions/blob/master/ggp.sh) was moved so that there was only one version of it floating around across the repos.

# Want to know about the concepts behind GGP?

Check out the [Concepts](/docs/Concepts.md) docs to understand GGP's deployment and function concepts.

# Want to know all of the command-line options supported?

Check out the [Command Line](/docs/CommandLine.md) docs.

# How does it work?

Check out the [How It Works](/docs/HowItWorks.md) docs to understand how GGP does what it does.

# Want to get started quickly?

Choose either option 1 or option 2 below and then jump to the [Getting Started](/docs/GettingStarted.md) docs.

**Remember: Any references to `GGP` in commands in this documentation must be replaced with the appropriate command for
your environment (e.g. `./ggp.sh`, `java -jar ...`, etc.)**

## Option 1: Use Docker

Copy the [ggp.sh](https://github.com/aws-samples/aws-greengrass-lambda-functions/blob/master/ggp.sh) script from this repo to your local system and make it executable (`chmod +x ggp.sh`
on Unix-y systems).  Then run `./ggp.sh` to pull the latest version to your system and run it in a Docker container.

If you plan on running GGP in Docker please remember that it needs to write files to the host operating system.  With
Docker it uses volume mounts to do this.  Therefore, we recommend you use the `ggp.sh` script to run GGP so that the
mounts are set up properly.  If you don't use the script you'll need to use the `-v` option in Docker to mount the paths
on your own.  If you don't do this the output files will be lost.

## Option 2: Use the JAR file

If you have Java installed and want to use the JAR file without Docker run this command:

```bash
./gradlew build
```

The output JAR file will be placed in `build/libs` and is called `AwsGreengrassProvisioner.jar`.  You can run the JAR
file directly like this from the cloned directory:

```bash
java -jar build/libs/AwsGreengrassProvisioner.jar
```

If you have built GGP correctly and have specified no options you should get this message:

```
[ERROR] AwsGreengrassProvisioner: No operation specified
```

# Contributors

Want to contribute some code?  Check out the issues listed as feature requests, branch and implement the code, and then
submit a pull request!

# Acknowledgements

In alphabetical order by last name people who contributed to GGP are:

- Richard Elberger
- Tim Mattison
- Erin McGill
- Anton Shmagin
- Craig Williams

# License Summary

This code is made available under the terms of the Apache 2.0 license. See the LICENSE file.
Included Lambda functions are made available under a modified MIT license. See LICENSE.Lambda.
