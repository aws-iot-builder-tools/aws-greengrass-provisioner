#!/usr/bin/env bash

set -e

AWS_SDK2_VERSION=$1

mkdir -p ./layers/temp
curl -SL https://github.com/aws/aws-sdk-java-v2/archive/refs/tags/$AWS_SDK2_VERSION.tar.gz | tar -zxC ./layers/temp

cd ./layers/temp/aws-sdk-java-v2-$AWS_SDK2_VERSION
mvn clean install -pl :ecr -P quick --am
mvn clean install -pl :ec2 -P quick --am
mvn clean install -pl :sts -P quick --am
mvn clean install -pl :cloudformation -P quick --am
mvn clean install -pl :iot -P quick --am
mvn clean install -pl :greengrass -P quick --am
mvn clean install -pl :lambda -P quick --am
mvn clean install -pl :iam -P quick --am
mvn clean install -pl :cloudwatchlogs -P quick --am
mvn clean install -pl :secretsmanager -P quick --am

cd ../../../
rm -rf ./layers/aws-sdk-layer/
mkdir -p ./layers/aws-sdk-layer/
mkdir -p ./layers/aws-sdk-layer/java/
mkdir -p ./layers/aws-sdk-layer/java/lib/
cp ./layers/temp/aws-sdk-java-v2-$AWS_SDK2_VERSION/services/ecr/target/aws-sdk-java-ecr-$AWS_SDK2_VERSION.jar ./layers/aws-sdk-layer/java/lib
cp ./layers/temp/aws-sdk-java-v2-$AWS_SDK2_VERSION/services/ec2/target/aws-sdk-java-ec2-$AWS_SDK2_VERSION.jar ./layers/aws-sdk-layer/java/lib
cp ./layers/temp/aws-sdk-java-v2-$AWS_SDK2_VERSION/services/sts/target/aws-sdk-java-sts-$AWS_SDK2_VERSION.jar ./layers/aws-sdk-layer/java/lib
cp ./layers/temp/aws-sdk-java-v2-$AWS_SDK2_VERSION/services/cloudformation/target/aws-sdk-java-cloudformation-$AWS_SDK2_VERSION.jar ./layers/aws-sdk-layer/java/lib
cp ./layers/temp/aws-sdk-java-v2-$AWS_SDK2_VERSION/services/iot/target/aws-sdk-java-iot-$AWS_SDK2_VERSION.jar ./layers/aws-sdk-layer/java/lib
cp ./layers/temp/aws-sdk-java-v2-$AWS_SDK2_VERSION/services/greengrass/target/aws-sdk-java-greengrass-$AWS_SDK2_VERSION.jar ./layers/aws-sdk-layer/java/lib
cp ./layers/temp/aws-sdk-java-v2-$AWS_SDK2_VERSION/services/lambda/target/aws-sdk-java-lambda-$AWS_SDK2_VERSION.jar ./layers/aws-sdk-layer/java/lib
cp ./layers/temp/aws-sdk-java-v2-$AWS_SDK2_VERSION/services/iam/target/aws-sdk-java-iam-$AWS_SDK2_VERSION.jar ./layers/aws-sdk-layer/java/lib
cp ./layers/temp/aws-sdk-java-v2-$AWS_SDK2_VERSION/services/cloudwatchlogs/target/aws-sdk-java-cloudwatchlogs-$AWS_SDK2_VERSION.jar ./layers/aws-sdk-layer/java/lib
cp ./layers/temp/aws-sdk-java-v2-$AWS_SDK2_VERSION/services/secretsmanager/target/aws-sdk-java-secretsmanager-$AWS_SDK2_VERSION.jar ./layers/aws-sdk-layer/java/lib

rm -rf ./layers/temp/