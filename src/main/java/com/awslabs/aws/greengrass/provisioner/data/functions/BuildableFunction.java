package com.awslabs.aws.greengrass.provisioner.data.functions;

import com.awslabs.aws.greengrass.provisioner.data.conf.ModifiableFunctionConf;
import software.amazon.awssdk.services.iam.model.Role;

public class BuildableFunction {
    private final ModifiableFunctionConf functionConf;
    private final Role lambdaRole;

    public BuildableFunction(ModifiableFunctionConf functionConf, Role lambdaRole) {
        this.functionConf = functionConf;
        this.lambdaRole = lambdaRole;
    }

    public ModifiableFunctionConf getFunctionConf() {
        return functionConf;
    }

    public Role getLambdaRole() {
        return lambdaRole;
    }
}
