package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.DeploymentConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.functions.BuildableFunction;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.FunctionIsolationMode;
import software.amazon.awssdk.services.iam.model.Role;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface FunctionHelper {
    String FUNCTIONS = "functions";
    String FUNCTION_DEFAULTS_CONF = "deployments/function.defaults.conf";
    String URI = "uri";
    String ARN = "arn";
    String PATH = "path";
    String READ_WRITE = "readWrite";
    String TRAINING_JOB = "training-job";
    String LOCAL_LAMBDA = "LOCAL_LAMBDA_";
    String HTTPS = "https://";
    String FUNCTION_CONF = "function.conf";
    String CONF_GREENGRASS_CONTAINER = "conf.greengrassContainer";

    static File getFunctionDefaultsFile() {
        return new File(FUNCTION_DEFAULTS_CONF);
    }

    static Config getFunctionDefaults() {
        return ConfigFactory.parseFile(getFunctionDefaultsFile());
    }

    static FunctionIsolationMode getDefaultFunctionIsolationMode() {
        boolean greengrassContainer = getFunctionDefaults().getBoolean(CONF_GREENGRASS_CONTAINER);

        return (greengrassContainer ? FunctionIsolationMode.GREENGRASS_CONTAINER : FunctionIsolationMode.NO_CONTAINER);
    }

    List<FunctionConf> getFunctionConfObjects(Map<String, String> defaultEnvironment, DeploymentConf deploymentConf);

    List<BuildableFunction> getBuildableFunctions(List<FunctionConf> functionConfs, Role lambdaRole);

    Map<Function, FunctionConf> buildFunctionsAndGenerateMap(List<BuildableFunction> buildableFunctions);

    void installJavaDependencies();
}
