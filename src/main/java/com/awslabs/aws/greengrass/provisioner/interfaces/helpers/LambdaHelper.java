package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.FunctionConfAndFunctionCode;
import com.awslabs.aws.greengrass.provisioner.data.LambdaFunctionArnInfo;
import com.awslabs.aws.greengrass.provisioner.data.ZipFilePathAndFunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.DeploymentConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.lambda.data.FunctionAliasArn;
import com.awslabs.lambda.data.FunctionName;
import io.vavr.control.Either;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationResponse;

public interface LambdaHelper {
    String GGP_FUNCTION_CONF = "GGP_FUNCTION_CONF";

    ZipFilePathAndFunctionConf buildExecutableFunction(FunctionConf functionConf);

    ZipFilePathAndFunctionConf buildJavaFunction(FunctionConf functionConf);

    ZipFilePathAndFunctionConf buildPython2Function(FunctionConf functionConf);

    ZipFilePathAndFunctionConf buildPython3Function(FunctionConf functionConf);

    ZipFilePathAndFunctionConf buildNodeFunction(FunctionConf functionConf);

    Either<CreateFunctionResponse, UpdateFunctionConfigurationResponse> createOrUpdateFunction(FunctionConfAndFunctionCode functionConfAndFunctionCode, Role role);

    LambdaFunctionArnInfo publishLambdaFunctionVersion(FunctionName functionName);

    void deleteAlias(String functionArn);

    FunctionAliasArn findFullFunctionArnByPartialName(String substring);
}
