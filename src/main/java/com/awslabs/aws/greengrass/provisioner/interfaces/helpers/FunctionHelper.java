package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.DeploymentConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.ModifiableFunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.functions.BuildableFunction;
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

    List<ModifiableFunctionConf> getFunctionConfObjects(Map<String, String> defaultEnvironment, DeploymentConf deploymentConf, FunctionIsolationMode defaultFunctionIsolationMode);

    List<BuildableFunction> getBuildableFunctions(List<ModifiableFunctionConf> functionConfs, Role lambdaRole);

    Predicate<ModifiableFunctionConf> getPython2Predicate();

    Predicate<ModifiableFunctionConf> getPython3Predicate();

    Predicate<ModifiableFunctionConf> getNodePredicate();

    Predicate<ModifiableFunctionConf> getExecutablePredicate();

    Predicate<ModifiableFunctionConf> getJavaPredicate();

    Map<Function, ModifiableFunctionConf> buildFunctionsAndGenerateMap(List<BuildableFunction> buildableFunctions);

    void installJavaDependencies();
}
