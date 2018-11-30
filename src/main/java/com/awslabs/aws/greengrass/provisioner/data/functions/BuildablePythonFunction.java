package com.awslabs.aws.greengrass.provisioner.data.functions;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import software.amazon.awssdk.services.iam.model.Role;

public class BuildablePythonFunction extends BuildableFunction {
    public BuildablePythonFunction(FunctionConf functionConf, Role lambdaRole) {
        super(functionConf, lambdaRole);
    }
}
