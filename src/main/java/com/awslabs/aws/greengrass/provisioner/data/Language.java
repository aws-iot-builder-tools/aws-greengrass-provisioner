package com.awslabs.aws.greengrass.provisioner.data;

import lombok.Getter;
import software.amazon.awssdk.services.lambda.model.Runtime;

public enum Language {
    Python(Runtime.PYTHON2_7), // Legacy, equivalent to PYTHON2_7
    Java(Runtime.JAVA8),       // Legacy, equivalent to JAVA8
    Node(Runtime.NODEJS6_10),  // Legacy, equivalent to NODEJS6_10
    NODEJS6_10(Runtime.NODEJS6_10),
    NODEJS8_10(Runtime.NODEJS8_10),
    JAVA8(Runtime.JAVA8),
    PYTHON2_7(Runtime.PYTHON2_7),
    PYTHON3_7(Runtime.PYTHON3_7);

    @Getter
    private final Runtime runtime;

    Language(Runtime runtime) {
        this.runtime = runtime;
    }
}
