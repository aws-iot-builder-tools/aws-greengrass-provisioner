package com.awslabs.aws.greengrass.provisioner.data.functions;

import com.amazonaws.services.identitymanagement.model.Role;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;

public class BuildableNodeFunction extends BuildableFunction {
    public BuildableNodeFunction(FunctionConf functionConf, Role lambdaRole) {
        super(functionConf, lambdaRole);
    }
}
