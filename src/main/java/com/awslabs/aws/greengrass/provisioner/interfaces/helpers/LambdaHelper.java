package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.PublishVersionResult;
import com.amazonaws.services.lambda.model.Runtime;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.LambdaFunctionArnInfo;

import java.util.Optional;

public interface LambdaHelper {
    LambdaFunctionArnInfo buildAndCreateJavaFunctionIfNecessary(FunctionConf functionConf, Role role);

    LambdaFunctionArnInfo buildAndCreatePythonFunctionIfNecessary(FunctionConf functionConf, Role role);

    LambdaFunctionArnInfo buildAndCreateNodeFunctionIfNecessary(FunctionConf functionConf, Role role);

    LambdaFunctionArnInfo createFunctionIfNecessary(FunctionConf functionConf, Runtime runtime, Role role, String zipFilePath);

    PublishVersionResult publishFunctionVersion(String groupFunctionName);

    boolean aliasExists(String functionName, String aliasName);

    String createAlias(Optional<String> groupName, String baseFunctionName, String functionVersion, String aliasName);

    String createAlias(FunctionConf functionConf, String functionVersion);

    Optional<GetFunctionResult> getFunction(String functionName);

    void deleteAlias(String functionArn);
}
