package com.awslabs.aws.greengrass.provisioner.data.functions;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import software.amazon.awssdk.services.iam.model.Role;

public class BuildableJavaGradleFunction extends BuildableFunction {
    public BuildableJavaGradleFunction(FunctionConf functionConf, Role lambdaRole) {
        super(functionConf, lambdaRole);
    }
}
