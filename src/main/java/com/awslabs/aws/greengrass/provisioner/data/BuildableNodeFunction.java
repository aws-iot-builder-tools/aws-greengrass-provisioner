package com.awslabs.aws.greengrass.provisioner.data;

import com.amazonaws.services.identitymanagement.model.Role;

public class BuildableNodeFunction extends BuildableFunction {
    public BuildableNodeFunction(FunctionConf functionConf, Role lambdaRole) {
        super(functionConf, lambdaRole);
    }
}
