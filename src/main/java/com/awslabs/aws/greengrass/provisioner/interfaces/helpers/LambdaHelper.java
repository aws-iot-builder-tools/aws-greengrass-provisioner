package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.LambdaFunctionArnInfo;
import com.awslabs.aws.greengrass.provisioner.data.ZipFilePathAndFunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import io.vavr.control.Either;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.PublishVersionResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationResponse;

import java.util.Optional;

public interface LambdaHelper {
    ZipFilePathAndFunctionConf buildExecutableFunction(FunctionConf functionConf);

    ZipFilePathAndFunctionConf buildJavaFunction(FunctionConf functionConf);

    ZipFilePathAndFunctionConf buildPython2Function(FunctionConf functionConf);

    ZipFilePathAndFunctionConf buildPython3Function(FunctionConf functionConf);

    ZipFilePathAndFunctionConf buildNodeFunction(FunctionConf functionConf);

    Either<CreateFunctionResponse, UpdateFunctionConfigurationResponse> createOrUpdateFunction(FunctionConf functionConf, Role role, String zipFilePath);

    LambdaFunctionArnInfo publishLambdaFunctionVersion(String groupFunctionName);

    PublishVersionResponse publishFunctionVersion(String groupFunctionName);

    boolean aliasExists(String functionName, String aliasName);

    String createAlias(String functionName, String functionVersion, String aliasName);

    String createAlias(FunctionConf functionConf, String functionVersion);

    Optional<GetFunctionResponse> getFunction(String functionName);

    void deleteAlias(String functionArn);
}
