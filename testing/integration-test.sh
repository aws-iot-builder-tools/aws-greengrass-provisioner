#!/usr/bin/env bash

CWD=`basename $PWD`

if [ "$CWD" == "testing" ]; then
  cd ..
fi

./testing/push-to-dockerhub.sh
./gradlew build

cd ../aws-greengrass-lambda-functions

function CLEAN_BUILD_DIRECTORIES {
    # Docker builds leave files around owned by root that need to be cleaned up
    sudo find functions -name "build" -type d -exec rm -rf {} \; 2> /dev/null
    sudo find foundation -name "build" -type d -exec rm -rf {} \; 2> /dev/null
}

GROUP="-g JUNK"
RUN_NATIVELY="java -jar ../aws-greengrass-provisioner/build/libs/AwsGreengrassProvisioner.jar"
RUN_IN_DOCKER="./deploy.sh"

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

CLEAN_BUILD_DIRECTORIES

# Make sure Java builds work
RUN_TEST "Native Java build" "$RUN_NATIVELY $CHECK_JAVA_BUILD"
RUN_TEST "Docker Java build" "$RUN_IN_DOCKER $CHECK_JAVA_BUILD"

# Make sure Python builds work
RUN_TEST "Native Python build" "$RUN_NATIVELY $CHECK_PYTHON_BUILD"
RUN_TEST "Docker Python build" "$RUN_IN_DOCKER $CHECK_PYTHON_BUILD"

# Make sure Python builds with dependencies work
RUN_TEST "Native Python build" "$RUN_NATIVELY $CHECK_PYTHON_BUILD_WITH_DEPENDENCIES"
RUN_TEST "Docker Python build" "$RUN_IN_DOCKER $CHECK_PYTHON_BUILD_WITH_DEPENDENCIES"

# Make sure Node builds work
RUN_TEST "Native Node build" "$RUN_NATIVELY $CHECK_NODE_BUILD"
RUN_TEST "Docker Node build" "$RUN_IN_DOCKER $CHECK_NODE_BUILD"

# Make sure combined builds work
RUN_TEST "Native combined build" "$RUN_NATIVELY $CHECK_COMBINED_BUILD"
RUN_TEST "Docker combined build" "$RUN_IN_DOCKER $CHECK_COMBINED_BUILD"

echo ALL INTEGRATION TESTS PASSED
