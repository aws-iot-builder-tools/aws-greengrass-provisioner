package com.awslabs.aws.greengrass.provisioner.data;

import com.amazonaws.services.identitymanagement.model.Role;

public class BuildableJavaGradleFunction extends BuildableFunction {
    public BuildableJavaGradleFunction(FunctionConf functionConf, Role lambdaRole) {
        super(functionConf, lambdaRole);
    }
}
