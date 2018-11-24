#!/usr/bin/env bash

set -e
set -x

rm -f AwsGreengrassProvisioner.jar

# Get the dependencies and build the code
docker build -t build-aws-greengrass-provisioner -f Dockerfile.build .

# Create a container from our image so we can copy the JAR out
id=$(docker create build-aws-greengrass-provisioner)

# Copy out the JAR file
docker cp $id:/build/build/libs/AwsGreengrassProvisioner.jar AwsGreengrassProvisioner.jar

# Clean up the container
docker rm -v $id

# Build the container that runs the application
docker build -t aws-greengrass-provisioner -f Dockerfile.run .
