package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.DeploymentConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.functions.BuildableFunction;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.iam.model.Role;

import java.util.List;
import java.util.Map;

public interface FunctionHelper {
    void setPathPrefix(String pathPrefix);

    List<FunctionConf> getFunctionConfObjects(Map<String, String> defaultEnvironment, DeploymentConf deploymentConf);

    List<BuildableFunction> getBuildableFunctions(List<FunctionConf> functionConfs, Role lambdaRole);

    Map<Function, FunctionConf> buildFunctionsAndGenerateMap(List<BuildableFunction> buildableFunctions);

    void installJavaDependencies();
}
