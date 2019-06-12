package com.awslabs.aws.greengrass.provisioner.data;

import software.amazon.awssdk.services.lambda.model.Runtime;

public enum Language {
    Python(null), // Legacy
    Java(null),   // Legacy
    Node(null),   // Legacy
    NODEJS6_10(Runtime.NODEJS6_10),
    NODEJS8_10(Runtime.NODEJS8_10),
    JAVA8(Runtime.JAVA8),
    PYTHON2_7(Runtime.PYTHON2_7),
    PYTHON3_7(Runtime.PYTHON3_7);

    private final Runtime runtime;

    public Runtime getRuntime() {
        return runtime;
    }

    Language(Runtime runtime) {
        this.runtime = runtime;
    }
}
