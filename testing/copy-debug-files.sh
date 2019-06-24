#!/usr/bin/env bash

testing/clean-debug-files.sh
./gradlew build

cp -R ../aws-greengrass-lambda-functions/functions .
cp -R ../aws-greengrass-lambda-functions/foundation .
cp -R ../aws-greengrass-lambda-functions/deployments .
cp -R ../aws-greengrass-lambda-functions/ggds .

cp build/foundation/* foundation
