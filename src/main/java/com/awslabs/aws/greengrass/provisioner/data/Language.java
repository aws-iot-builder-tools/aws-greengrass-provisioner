package com.awslabs.aws.greengrass.provisioner.data;

import software.amazon.awssdk.services.lambda.model.Runtime;

public enum Language {
    Python(null), // Legacy
    Java(null),   // Legacy
    Node(null),   // Legacy
    NODEJS12_X(Runtime.NODEJS12_X),
    JAVA8(Runtime.JAVA8),
    PYTHON2_7(Runtime.PYTHON2_7),
    PYTHON3_7(Runtime.PYTHON3_7),
    EXECUTABLE(null); // Raw executables like C functions

    private final Runtime runtime;

    Language(Runtime runtime) {
        this.runtime = runtime;
    }

    public Runtime getRuntime() {
        return runtime;
    }
}
