package com.awslabs.aws.greengrass.provisioner.data;

import software.amazon.awssdk.services.lambda.model.Runtime;

public enum Language {
    Python(null), // Legacy
    Java(null),   // Legacy
    Node(null),   // Legacy
    NODEJS12_X(Runtime.NODEJS12_X),
    NODEJS14_X(Runtime.NODEJS14_X),
    NODEJS16_X(Runtime.NODEJS16_X),
    NODEJS18_X(Runtime.NODEJS18_X),
    NODEJS20_X(Runtime.NODEJS20_X),
    JAVA8(Runtime.JAVA8),
    JAVA11(Runtime.JAVA11),
    JAVA17(Runtime.JAVA17),
    JAVA21(Runtime.JAVA21),
    PYTHON2_7(Runtime.PYTHON2_7),
    PYTHON3_7(Runtime.PYTHON3_7),
    PYTHON3_8(Runtime.PYTHON3_8),
    PYTHON3_9(Runtime.PYTHON3_9),
    PYTHON3_10(Runtime.PYTHON3_10),
    PYTHON3_11(Runtime.PYTHON3_11),
    PYTHON3_12(Runtime.PYTHON3_12),
    GO1_X(Runtime.GO1_X),
    DOTNET6(Runtime.DOTNET6),
    DOTNETCORE1_0(Runtime.DOTNETCORE1_0),
    DOTNETCORE2_0(Runtime.DOTNETCORE2_0),
    DOTNETCORE2_1(Runtime.DOTNETCORE2_1),
    DOTNETCORE3_1(Runtime.DOTNETCORE3_1),
    EXECUTABLE(null); // Raw executables like C functions

    private final Runtime runtime;

    Language(Runtime runtime) {
        this.runtime = runtime;
    }

    public Runtime getRuntime() {
        return runtime;
    }
}
