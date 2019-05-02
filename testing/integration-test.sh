#!/usr/bin/env bash

CWD=`basename $PWD`

if [ "$CWD" == "testing" ]; then
  cd ..
fi

set -e

./testing/push-to-dockerhub.sh
./gradlew clean build integrationTest

cd ../aws-greengrass-lambda-functions

function CLEAN_BUILD_DIRECTORIES {
    # Docker builds leave files around owned by root that need to be cleaned up
    sudo find functions -name "build" -type d -exec rm -rf {} \; 2> /dev/null
    sudo find foundation -name "build" -type d -exec rm -rf {} \; 2> /dev/null
}

GROUP="-g `uuidgen`"
RUN_NATIVELY="java -jar ../aws-greengrass-provisioner/build/libs/AwsGreengrassProvisioner.jar"

CHECK_JAVA_BUILD="-d deployments/cdd-skeleton.conf $GROUP"
CHECK_PYTHON_BUILD="-d deployments/python-hello-world.conf $GROUP"
CHECK_PYTHON_BUILD_WITH_DEPENDENCIES="-d deployments/lifx.conf $GROUP"
CHECK_NODE_BUILD="-d deployments/node-hello-world.conf $GROUP"
CHECK_COMBINED_BUILD="-d deployments/all-hello-world.conf $GROUP"

function RUN_TEST {
    $2
    if [ $? -ne 0 ]; then echo "$1 test failed"; exit 1; fi
    echo "$1 SUCCEEDED"
    CLEAN_BUILD_DIRECTORIES
}

set +e

CLEAN_BUILD_DIRECTORIES

# Make sure Java builds work
RUN_TEST "Native Java build" "$RUN_NATIVELY $CHECK_JAVA_BUILD"

# Make sure Python builds work
RUN_TEST "Native Python build" "$RUN_NATIVELY $CHECK_PYTHON_BUILD"

# Make sure Python builds with dependencies work
RUN_TEST "Native Python build" "$RUN_NATIVELY $CHECK_PYTHON_BUILD_WITH_DEPENDENCIES"

# Make sure Node builds work
RUN_TEST "Native Node build" "$RUN_NATIVELY $CHECK_NODE_BUILD"

# Make sure combined builds work
RUN_TEST "Native combined build" "$RUN_NATIVELY $CHECK_COMBINED_BUILD"

echo ALL INTEGRATION TESTS PASSED
