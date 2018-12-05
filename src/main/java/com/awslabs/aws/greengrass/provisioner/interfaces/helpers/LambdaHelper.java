package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.LambdaFunctionArnInfo;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.PublishVersionResponse;
import software.amazon.awssdk.services.lambda.model.Runtime;

import java.util.Optional;

public interface LambdaHelper {
    LambdaFunctionArnInfo buildAndCreateJavaFunctionIfNecessary(FunctionConf functionConf, Role role);

    LambdaFunctionArnInfo buildAndCreatePythonFunctionIfNecessary(FunctionConf functionConf, Role role);

    LambdaFunctionArnInfo buildAndCreateNodeFunctionIfNecessary(FunctionConf functionConf, Role role);

    LambdaFunctionArnInfo createFunctionIfNecessary(FunctionConf functionConf, Runtime runtime, Role role, String zipFilePath);

    PublishVersionResponse publishFunctionVersion(String groupFunctionName);

    boolean aliasExists(String functionName, String aliasName);

    String createAlias(Optional<String> groupName, String baseFunctionName, String functionVersion, String aliasName);

    String createAlias(FunctionConf functionConf, String functionVersion);

    Optional<GetFunctionResponse> getFunction(String functionName);

    void deleteAlias(String functionArn);
}
