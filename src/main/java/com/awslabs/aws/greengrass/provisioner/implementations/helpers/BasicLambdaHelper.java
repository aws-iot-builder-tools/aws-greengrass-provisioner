package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.ImmutableLambdaFunctionArnInfo;
import com.awslabs.aws.greengrass.provisioner.data.LambdaFunctionArnInfo;
import com.awslabs.aws.greengrass.provisioner.data.Language;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.*;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.LambdaHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.LoggingHelper;
import io.vavr.control.Try;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;

import javax.inject.Inject;
import java.io.File;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;

public class BasicLambdaHelper implements LambdaHelper {
    private static final String ARN_AWS_GREENGRASS_RUNTIME_FUNCTION_EXECUTABLE = "arn:aws:greengrass:::runtime/function/executable";
    private static final String ZIP_ARCHIVE_FOR_EXECUTABLE_NATIVE_FUNCTION_NOT_PRESENT = "ZIP archive for executable/native function not present ";
    private final Logger log = LoggerFactory.getLogger(BasicLambdaHelper.class);
    @Inject
    LambdaClient lambdaClient;
    @Inject
    IoHelper ioHelper;
    @Inject
    MavenBuilder mavenBuilder;
    @Inject
    GradleBuilder gradleBuilder;
    @Inject
    Python2Builder python2Builder;
    @Inject
    Python3Builder python3Builder;
    @Inject
    NodeBuilder nodeBuilder;
    @Inject
    ExecutableBuilder executableBuilder;
    @Inject
    LoggingHelper loggingHelper;

    @Inject
    public BasicLambdaHelper() {
    }

    private boolean functionExists(String functionName) {
        GetFunctionRequest getFunctionRequest = GetFunctionRequest.builder()
                .functionName(functionName)
                .build();

        return Try.of(() -> lambdaClient.getFunction(getFunctionRequest) != null)
                .recover(ResourceNotFoundException.class, throwable -> false)
                .get();
    }

    @Override
    public LambdaFunctionArnInfo buildAndCreateExecutableFunctionIfNecessary(FunctionConf functionConf, Role role) {
        String zipFilePath = String.join("/", functionConf.getBuildDirectory().toString(), functionConf.getFunctionName() + ".zip");

        File zipFile = new File(zipFilePath);

        if (!zipFile.exists()) {
            log.warn(ZIP_ARCHIVE_FOR_EXECUTABLE_NATIVE_FUNCTION_NOT_PRESENT + "[" + zipFile.getName() + "], attempting build");
            executableBuilder.buildExecutableFunctionIfNecessary(functionConf);
        }

        if (!zipFile.exists()) {
            throw new RuntimeException(ZIP_ARCHIVE_FOR_EXECUTABLE_NATIVE_FUNCTION_NOT_PRESENT + "[" + zipFile.getName() + "]");
        }

        return createFunctionIfNecessary(functionConf, role, zipFilePath);
    }

    @Override
    public LambdaFunctionArnInfo buildAndCreateJavaFunctionIfNecessary(FunctionConf functionConf, Role role) {
        String zipFilePath;

        if (mavenBuilder.isMavenFunction(functionConf)) {
            /*
            mavenBuilder.buildJavaFunctionIfNecessary(functionConf);

            zipFilePath = mavenBuilder.getArchivePath(functionConf);
            */
            throw new RuntimeException("This function [" + functionConf.getFunctionName() + "] is a Maven project but Maven support is currently disabled.  If you need this feature please file a Github issue.");
        } else if (gradleBuilder.isGradleFunction(functionConf)) {
            gradleBuilder.buildJavaFunctionIfNecessary(functionConf);

            zipFilePath = gradleBuilder.getArchivePath(functionConf);
        } else {
            throw new RuntimeException("This function [" + functionConf.getFunctionName() + "] is neither a Maven project nor a Gradle project.  It cannot be built automatically.");
        }

        return createFunctionIfNecessary(functionConf, role, zipFilePath);
    }

    @Override
    public LambdaFunctionArnInfo buildAndCreatePython2FunctionIfNecessary(FunctionConf functionConf, Role role) {
        Optional<String> error = python2Builder.verifyHandlerExists(functionConf);

        if (error.isPresent()) {
            return ImmutableLambdaFunctionArnInfo.builder()
                    .error(error).build();
        }

        python2Builder.buildFunctionIfNecessary(functionConf);

        String zipFilePath = python2Builder.getArchivePath(functionConf);

        return createFunctionIfNecessary(functionConf, role, zipFilePath);
    }

    @Override
    public LambdaFunctionArnInfo buildAndCreatePython3FunctionIfNecessary(FunctionConf functionConf, Role role) {
        Optional<String> error = python3Builder.verifyHandlerExists(functionConf);

        if (error.isPresent()) {
            return ImmutableLambdaFunctionArnInfo.builder()
                    .error(error).build();
        }

        python3Builder.buildFunctionIfNecessary(functionConf);

        String zipFilePath = python3Builder.getArchivePath(functionConf);

        return createFunctionIfNecessary(functionConf, role, zipFilePath);
    }

    @Override
    public LambdaFunctionArnInfo buildAndCreateNodeFunctionIfNecessary(FunctionConf functionConf, Role role) {
        Optional<String> error = nodeBuilder.verifyHandlerExists(functionConf);

        if (error.isPresent()) {
            return ImmutableLambdaFunctionArnInfo.builder()
                    .error(error).build();
        }

        nodeBuilder.buildFunctionIfNecessary(functionConf);

        String zipFilePath = nodeBuilder.getArchivePath(functionConf);
        return createFunctionIfNecessary(functionConf, role, zipFilePath);
    }

    @Override
    public LambdaFunctionArnInfo createFunctionIfNecessary(FunctionConf functionConf, Role role, String zipFilePath) {
        String baseFunctionName = functionConf.getFunctionName();
        String groupFunctionName = getFunctionName(functionConf);

        /*
            loggingHelper.logInfoWithName(log, baseFunctionName, "Deleting existing Lambda function");
            DeleteFunctionRequest deleteFunctionRequest = DeleteFunctionRequest.builder()
                    .functionName(groupFunctionName)
                    .build();

            lambdaClient.deleteFunction(deleteFunctionRequest);
        }
         */

        FunctionCode functionCode = FunctionCode.builder()
                .zipFile(SdkBytes.fromByteBuffer(ByteBuffer.wrap(ioHelper.readFile(zipFilePath))))
                .build();

        String runtime;

        if (functionConf.getLanguage().equals(Language.EXECUTABLE)) {
            runtime = ARN_AWS_GREENGRASS_RUNTIME_FUNCTION_EXECUTABLE;
        } else {
            runtime = functionConf.getLanguage().getRuntime().toString();
        }

        // Sometimes the Lambda IAM role isn't immediately visible so we need retries
        RetryPolicy<LambdaResponse> lambdaIamRoleRetryPolicy = new RetryPolicy<LambdaResponse>()
                .handleIf(throwable -> throwable.getMessage().startsWith("The role defined for the function cannot be assumed by Lambda."))
                .withDelay(Duration.ofSeconds(5))
                .withMaxRetries(10)
                .onRetry(failure -> log.warn("Waiting for IAM role to be visible to AWS Lambda..."))
                .onRetriesExceeded(failure -> log.error("IAM role never became visible to AWS Lambda. Cannot continue."));

        if (functionExists(groupFunctionName)) {
            loggingHelper.logInfoWithName(log, baseFunctionName, "Updating Lambda function code");

            UpdateFunctionCodeRequest updateFunctionCodeRequest = UpdateFunctionCodeRequest.builder()
                    .functionName(groupFunctionName)
                    .zipFile(functionCode.zipFile())
                    .build();

            // Make sure multiple threads don't do this at the same time
            synchronized (this) {
                Failsafe.with(lambdaIamRoleRetryPolicy).get(() ->
                        lambdaClient.updateFunctionCode(updateFunctionCodeRequest));
            }

            loggingHelper.logInfoWithName(log, baseFunctionName, "Updating Lambda function configuration");

            UpdateFunctionConfigurationRequest updateFunctionConfigurationRequest = UpdateFunctionConfigurationRequest.builder()
                    .functionName(groupFunctionName)
                    .role(role.arn())
                    .handler(functionConf.getHandlerName())
                    .runtime(runtime)
                    .build();

            // Make sure multiple threads don't do this at the same time
            synchronized (this) {
                Failsafe.with(lambdaIamRoleRetryPolicy).get(() ->
                        lambdaClient.updateFunctionConfiguration(updateFunctionConfigurationRequest));
            }
        } else {
            loggingHelper.logInfoWithName(log, baseFunctionName, "Creating new Lambda function");

            CreateFunctionRequest createFunctionRequest = CreateFunctionRequest.builder()
                    .functionName(groupFunctionName)
                    .role(role.arn())
                    .handler(functionConf.getHandlerName())
                    .code(functionCode)
                    .runtime(runtime)
                    .build();

            // Make sure multiple threads don't do this at the same time
            synchronized (this) {
                Failsafe.with(lambdaIamRoleRetryPolicy).get(() ->
                        lambdaClient.createFunction(createFunctionRequest));
            }
        }

        loggingHelper.logInfoWithName(log, baseFunctionName, "Publishing Lambda function version");
        PublishVersionResponse publishVersionResponse = publishFunctionVersion(groupFunctionName);

        String qualifier = publishVersionResponse.version();
        String qualifiedArn = publishVersionResponse.functionArn();
        String baseArn = qualifiedArn.replaceAll(":" + qualifier + "$", "");

        LambdaFunctionArnInfo lambdaFunctionArnInfo = ImmutableLambdaFunctionArnInfo.builder()
                .qualifier(qualifier)
                .qualifiedArn(qualifiedArn)
                .baseArn(baseArn)
                .build();

        return lambdaFunctionArnInfo;
    }

    @Override
    public PublishVersionResponse publishFunctionVersion(String groupFunctionName) {
        PublishVersionRequest publishVersionRequest = PublishVersionRequest.builder()
                .functionName(groupFunctionName)
                .build();

        return lambdaClient.publishVersion(publishVersionRequest);
    }

    private boolean aliasExists(FunctionConf functionConf) {
        return aliasExists(getFunctionName(functionConf), functionConf.getAliasName());
    }

    @Override
    public boolean aliasExists(String functionName, String aliasName) {
        GetAliasRequest getAliasRequest = GetAliasRequest.builder()
                .functionName(functionName)
                .name(aliasName)
                .build();

        return Try.of(() -> lambdaClient.getAlias(getAliasRequest) != null)
                .recover(ResourceNotFoundException.class, throwable -> false)
                .get();
    }

    private String getFunctionName(FunctionConf functionConf) {
        return getFunctionName(functionConf.getGroupName(), functionConf.getFunctionName());
    }

    private String getFunctionName(String groupName, String baseFunctionName) {
        return String.join("-", groupName, baseFunctionName);
    }

    @Override
    public String createAlias(Optional<String> groupName, String baseFunctionName, String functionVersion, String aliasName) {
        String groupFunctionName = baseFunctionName;

        if (groupName.isPresent()) {
            groupFunctionName = getFunctionName(groupName.get(), baseFunctionName);
        }

        if (aliasExists(groupFunctionName, aliasName)) {
            loggingHelper.logInfoWithName(log, baseFunctionName, "Deleting existing alias");

            DeleteAliasRequest deleteAliasRequest = DeleteAliasRequest.builder()
                    .functionName(groupFunctionName)
                    .name(aliasName)
                    .build();

            lambdaClient.deleteAlias(deleteAliasRequest);
        }

        loggingHelper.logInfoWithName(log, baseFunctionName, "Creating new alias");

        CreateAliasRequest createAliasRequest = CreateAliasRequest.builder()
                .functionName(groupFunctionName)
                .name(aliasName)
                .functionVersion(functionVersion)
                .build();

        CreateAliasResponse createAliasResponse = lambdaClient.createAlias(createAliasRequest);

        return createAliasResponse.aliasArn();
    }

    @Override
    public String createAlias(FunctionConf functionConf, String functionVersion) {
        return createAlias(Optional.of(functionConf.getGroupName()), functionConf.getFunctionName(), functionVersion, functionConf.getAliasName());
    }

    @Override
    public Optional<GetFunctionResponse> getFunction(String functionName) {
        GetFunctionRequest getFunctionRequest = GetFunctionRequest.builder()
                .functionName(functionName)
                .build();

        return Try.of(() -> Optional.of(lambdaClient.getFunction(getFunctionRequest)))
                .recover(ResourceNotFoundException.class, throwable -> Optional.empty())
                .get();
    }

    @Override
    public void deleteAlias(String functionArn) {
        String temp = functionArn.substring(0, functionArn.lastIndexOf(":"));
        String aliasName = functionArn.substring(functionArn.lastIndexOf(":") + 1);
        String functionName = temp.substring(temp.lastIndexOf(":") + 1);

        DeleteAliasRequest deleteAliasRequest = DeleteAliasRequest.builder()
                .functionName(functionName)
                .name(aliasName)
                .build();

        lambdaClient.deleteAlias(deleteAliasRequest);
    }
}
