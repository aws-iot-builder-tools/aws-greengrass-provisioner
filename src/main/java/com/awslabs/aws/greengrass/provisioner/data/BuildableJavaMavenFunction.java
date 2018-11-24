package com.awslabs.aws.greengrass.provisioner.data;

import com.amazonaws.services.identitymanagement.model.Role;

public class BuildableJavaMavenFunction extends BuildableFunction {
    public BuildableJavaMavenFunction(FunctionConf functionConf, Role lambdaRole) {
        super(functionConf, lambdaRole);
    }
}
