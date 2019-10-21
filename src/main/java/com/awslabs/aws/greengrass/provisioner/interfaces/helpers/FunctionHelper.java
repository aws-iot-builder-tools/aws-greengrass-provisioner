package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.DeploymentConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.FunctionIsolationMode;
import software.amazon.awssdk.services.iam.model.Role;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public interface FunctionHelper {
    String FUNCTIONS = "functions";
    String URI = "uri";
    String ARN = "arn";
    String PATH = "path";
    String READ_WRITE = "readWrite";
    String TRAINING_JOB = "training-job";
    String LOCAL_LAMBDA = "LOCAL_LAMBDA_";
    String HTTPS = "https://";
    String FUNCTION_CONF = "function.conf";

    List<FunctionConf> getFunctionConfObjects(Map<String, String> defaultEnvironment, DeploymentConf deploymentConf, FunctionIsolationMode defaultFunctionIsolationMode);

    void verifyFunctionsAreBuildable(List<FunctionConf> functionConfs);

    Predicate<FunctionConf> getPython2Predicate();

    Predicate<FunctionConf> getPython3Predicate();

    Predicate<FunctionConf> getNodePredicate();

    Predicate<FunctionConf> getExecutablePredicate();

    Predicate<FunctionConf> getJavaPredicate();

    Map<Function, FunctionConf> buildFunctionsAndGenerateMap(List<FunctionConf> buildableFunctions, Role lambdaRole);
}
