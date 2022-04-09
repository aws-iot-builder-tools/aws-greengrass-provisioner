package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.conf.DeploymentConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.typesafe.config.Config;
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

    List<FunctionConf> getFunctionConfObjects(DeploymentArguments deploymentArguments, Config defaultConfig, DeploymentConf deploymentConf, FunctionIsolationMode defaultFunctionIsolationMode);

    void verifyFunctionsAreSupported(List<FunctionConf> functionConfs);

    Predicate<FunctionConf> getPython2Predicate();

    Predicate<FunctionConf> getPython3Predicate();

    Predicate<FunctionConf> getNodePredicate();

    Predicate<FunctionConf> getExecutablePredicate();

    Predicate<FunctionConf> getJavaPredicate();

    Map<Function, FunctionConf> buildFunctionsAndGenerateMap(String s3Bucket, String s3Directory, List<FunctionConf> buildableFunctions, Role lambdaRole);
}
