package com.awslabs.aws.greengrass.provisioner.data;

import com.amazonaws.services.identitymanagement.model.Role;

public class BuildablePythonFunction extends BuildableFunction {
    public BuildablePythonFunction(FunctionConf functionConf, Role lambdaRole) {
        super(functionConf, lambdaRole);
    }
}
