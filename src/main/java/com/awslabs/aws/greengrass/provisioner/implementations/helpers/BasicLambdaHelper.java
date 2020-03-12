package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.ImmutableLambdaFunctionArnInfo;
import com.awslabs.aws.greengrass.provisioner.data.ImmutableZipFilePathAndFunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.LambdaFunctionArnInfo;
import com.awslabs.aws.greengrass.provisioner.data.Language;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.*;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.LambdaHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.LoggingHelper;
import com.awslabs.lambda.data.FunctionName;
import com.awslabs.lambda.data.ImmutableFunctionName;
import com.awslabs.lambda.helpers.interfaces.V2LambdaHelper;
import io.vavr.control.Either;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.text.StringEscapeUtils;
import org.gradle.tooling.BuildException;
import org.jetbrains.annotations.NotNull;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BasicLambdaHelper implements LambdaHelper {
    private static final String ARN_AWS_GREENGRASS_RUNTIME_FUNCTION_EXECUTABLE = "arn:aws:greengrass:::runtime/function/executable";
    private static final String ZIP_ARCHIVE_FOR_EXECUTABLE_NATIVE_FUNCTION_NOT_PRESENT = "ZIP archive for executable/native function not present ";
    private final Logger log = LoggerFactory.getLogger(BasicLambdaHelper.class);
    @Inject
    LambdaClient lambdaClient;
    @Inject
    V2LambdaHelper v2LambdaHelper;
    @Inject
    IoHelper ioHelper;
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

    @Override
    public ImmutableZipFilePathAndFunctionConf buildExecutableFunction(FunctionConf functionConf) {
        log.info("Creating executable/native function [" + functionConf.getFunctionName() + "]");

        String zipFilePath = String.join("/", functionConf.getBuildDirectory().get().toString(), functionConf.getFunctionName() + ".zip");

        File zipFile = new File(zipFilePath);

        if (!zipFile.exists()) {
            log.warn(ZIP_ARCHIVE_FOR_EXECUTABLE_NATIVE_FUNCTION_NOT_PRESENT + "[" + zipFile.getName() + "], attempting build");
            executableBuilder.buildExecutableFunctionIfNecessary(functionConf);
        }

        if (!zipFile.exists()) {
            throw new RuntimeException(ZIP_ARCHIVE_FOR_EXECUTABLE_NATIVE_FUNCTION_NOT_PRESENT + "[" + zipFile.getName() + "]");
        }

        return ImmutableZipFilePathAndFunctionConf.builder()
                .zipFilePath(zipFilePath)
                .functionConf(functionConf)
                .build();
    }

    @Override
    public ImmutableZipFilePathAndFunctionConf buildJavaFunction(FunctionConf functionConf) {
        log.info("Creating Java function [" + functionConf.getFunctionName() + "]");

        if (gradleBuilder.isGradleFunction(functionConf)) {
            return buildGradleFunction(functionConf);

        } else {
            throw new RuntimeException("This function [" + functionConf.getFunctionName() + "] is not a Gradle project.  It cannot be built automatically.");
        }
    }

    private ImmutableZipFilePathAndFunctionConf buildGradleFunction(FunctionConf functionConf) {
        try {
            gradleBuilder.buildJavaFunctionIfNecessary(functionConf);

            String zipFilePath = gradleBuilder.getArchivePath(functionConf);

            return ImmutableZipFilePathAndFunctionConf.builder()
                    .zipFilePath(zipFilePath)
                    .functionConf(functionConf)
                    .build();
        } catch (BuildException e) {
            return ImmutableZipFilePathAndFunctionConf.builder()
                    .error(e.getLocalizedMessage())
                    .functionConf(functionConf)
                    .build();
        }
    }

    @Override
    public ImmutableZipFilePathAndFunctionConf buildPython2Function(FunctionConf functionConf) {
        log.info("Creating Python 2 function [" + functionConf.getFunctionName() + "]");

        Optional<String> error = python2Builder.verifyHandlerExists(functionConf);

        if (error.isPresent()) {
            return ImmutableZipFilePathAndFunctionConf.builder()
                    .functionConf(functionConf)
                    .error(error)
                    .build();
        }

        python2Builder.buildFunctionIfNecessary(functionConf);

        String zipFilePath = python2Builder.getArchivePath(functionConf);

        return ImmutableZipFilePathAndFunctionConf.builder()
                .zipFilePath(zipFilePath)
                .functionConf(functionConf)
                .build();
    }

    @Override
    public ImmutableZipFilePathAndFunctionConf buildPython3Function(FunctionConf functionConf) {
        log.info("Creating Python 3 function [" + functionConf.getFunctionName() + "]");

        Optional<String> error = python3Builder.verifyHandlerExists(functionConf);

        if (error.isPresent()) {
            return ImmutableZipFilePathAndFunctionConf.builder()
                    .functionConf(functionConf)
                    .error(error)
                    .build();
        }

        python3Builder.buildFunctionIfNecessary(functionConf);

        String zipFilePath = python3Builder.getArchivePath(functionConf);

        return ImmutableZipFilePathAndFunctionConf.builder()
                .zipFilePath(zipFilePath)
                .functionConf(functionConf)
                .build();
    }

    @Override
    public ImmutableZipFilePathAndFunctionConf buildNodeFunction(FunctionConf functionConf) {
        log.info("Creating Node function [" + functionConf.getFunctionName() + "]");

        Optional<String> error = nodeBuilder.verifyHandlerExists(functionConf);

        if (error.isPresent()) {
            return ImmutableZipFilePathAndFunctionConf.builder()
                    .functionConf(functionConf)
                    .error(error)
                    .build();
        }

        nodeBuilder.buildFunctionIfNecessary(functionConf);

        String zipFilePath = nodeBuilder.getArchivePath(functionConf);

        return ImmutableZipFilePathAndFunctionConf.builder()
                .zipFilePath(zipFilePath)
                .functionConf(functionConf)
                .build();
    }

    @Override
    public Either<CreateFunctionResponse, UpdateFunctionConfigurationResponse> createOrUpdateFunction(FunctionConf functionConf, Role role, String zipFilePath) {
        String baseFunctionName = functionConf.getFunctionName();
        String groupFunctionName = functionConf.getGroupFunctionName();

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

        if (v2LambdaHelper.functionExists(ImmutableFunctionName.builder().name(groupFunctionName).build())) {
            // Update the function
            return Either.right(updateExistingLambdaFunction(functionConf, role, baseFunctionName, groupFunctionName, functionCode, runtime, lambdaIamRoleRetryPolicy));
        }

        // Create a new function
        return Either.left(createNewLambdaFunction(functionConf, role, baseFunctionName, groupFunctionName, functionCode, runtime, lambdaIamRoleRetryPolicy));
    }

    @Override
    public LambdaFunctionArnInfo publishLambdaFunctionVersion(FunctionName functionName) {
        PublishVersionResponse publishVersionResponse = v2LambdaHelper.publishFunctionVersion(functionName);

        String qualifier = publishVersionResponse.version();
        String qualifiedArn = publishVersionResponse.functionArn();
        String baseArn = qualifiedArn.replaceAll(":" + qualifier + "$", "");

        return ImmutableLambdaFunctionArnInfo.builder()
                .qualifier(qualifier)
                .qualifiedArn(qualifiedArn)
                .baseArn(baseArn)
                .build();
    }

    private UpdateFunctionConfigurationResponse updateExistingLambdaFunction(FunctionConf functionConf, Role role, String baseFunctionName, String functionName, FunctionCode functionCode, String runtime, RetryPolicy<LambdaResponse> lambdaIamRoleRetryPolicy) {
        loggingHelper.logInfoWithName(log, baseFunctionName, "Updating Lambda function code");

        UpdateFunctionCodeRequest updateFunctionCodeRequest = UpdateFunctionCodeRequest.builder()
                .functionName(functionName)
                .zipFile(functionCode.zipFile())
                .build();

        // Make sure multiple threads don't do this at the same time
        synchronized (this) {
            Failsafe.with(lambdaIamRoleRetryPolicy).get(() ->
                    lambdaClient.updateFunctionCode(updateFunctionCodeRequest));
        }

        Map<String, String> existingEnvironment = getFunctionEnvironment(functionName);

        HashMap<String, String> newEnvironment = updateGgpFunctionConfInEnvironment(functionConf, existingEnvironment);

        Environment lambdaEnvironment = Environment.builder().variables(newEnvironment).build();

        loggingHelper.logInfoWithName(log, baseFunctionName, "Updating Lambda function configuration");

        UpdateFunctionConfigurationRequest updateFunctionConfigurationRequest = UpdateFunctionConfigurationRequest.builder()
                .functionName(functionName)
                .role(role.arn())
                .handler(functionConf.getHandlerName())
                .runtime(runtime)
                .environment(lambdaEnvironment)
                .build();

        // Make sure multiple threads don't do this at the same time
        synchronized (this) {
            return Failsafe.with(lambdaIamRoleRetryPolicy).get(() ->
                    lambdaClient.updateFunctionConfiguration(updateFunctionConfigurationRequest));
        }
    }

    @NotNull
    private HashMap<String, String> updateGgpFunctionConfInEnvironment(FunctionConf functionConf, Map<String, String> existingEnvironment) {
        // The map that comes from the AWS SDK is unmodifiable. We need to make sure we return a regular hash map so we can modify it.
        HashMap<String, String> newEnvironment = new HashMap<>(existingEnvironment);

        // Overwrite any existing function configuration information
        newEnvironment.put(GGP_FUNCTION_CONF, functionConf.getRawConfig());
        return newEnvironment;
    }

    @Override
    public Map<String, String> getFunctionEnvironment(String functionName) {
        GetFunctionConfigurationResponse getFunctionConfigurationResponse = getFunctionConfigurationByName(functionName);

        return Optional.ofNullable(getFunctionConfigurationResponse.environment())
                .map(EnvironmentResponse::variables)
                .orElseGet(HashMap::new);
    }

    @Override
    public GetFunctionConfigurationResponse getFunctionConfigurationByName(String functionName) {
        GetFunctionConfigurationRequest getFunctionConfigurationRequest = GetFunctionConfigurationRequest.builder()
                .functionName(functionName)
                .build();

        return lambdaClient.getFunctionConfiguration(getFunctionConfigurationRequest);
    }

    private CreateFunctionResponse createNewLambdaFunction(FunctionConf functionConf, Role role, String baseFunctionName, String functionName, FunctionCode functionCode, String runtime, RetryPolicy<LambdaResponse> lambdaIamRoleRetryPolicy) {
        loggingHelper.logInfoWithName(log, baseFunctionName, "Creating new Lambda function");

        // No environment, start with an empty one
        HashMap<String, String> newEnvironment = updateGgpFunctionConfInEnvironment(functionConf, new HashMap<>());

        Environment lambdaEnvironment = Environment.builder().variables(newEnvironment).build();

        CreateFunctionRequest createFunctionRequest = CreateFunctionRequest.builder()
                .functionName(functionName)
                .role(role.arn())
                .handler(functionConf.getHandlerName())
                .code(functionCode)
                .runtime(runtime)
                .environment(lambdaEnvironment)
                .build();

        // Make sure multiple threads don't do this at the same time
        synchronized (this) {
            return Failsafe.with(lambdaIamRoleRetryPolicy).get(() ->
                    lambdaClient.createFunction(createFunctionRequest));
        }
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

    @Override
    public String findFullFunctionArnByPartialName(String partialName) {
        ListFunctionsResponse listFunctionsResponse;

        String partialNameWithoutAlias = partialName.substring(0, partialName.lastIndexOf(":"));
        String escapedPartialName = StringEscapeUtils.escapeJava(partialNameWithoutAlias);
        String patternString = "^" + escapedPartialName.replaceAll("~", ".*") + "$";
        Pattern pattern = Pattern.compile(patternString);

        String alias = partialName.substring(partialName.lastIndexOf(":") + 1);

        Optional<String> optionalFunctionArn = Optional.empty();
        Optional<String> optionalNextMarker = Optional.empty();

        do {
            ListFunctionsRequest.Builder listFunctionsRequestBuilder = ListFunctionsRequest.builder();
            optionalNextMarker.ifPresent(listFunctionsRequestBuilder::marker);

            listFunctionsResponse = lambdaClient.listFunctions(listFunctionsRequestBuilder.build());

            optionalNextMarker = Optional.ofNullable(listFunctionsResponse.nextMarker());

            List<String> functionArns = listFunctionsResponse.functions().stream()
                    .filter(function -> pattern.matcher(function.functionName()).find())
                    .map(FunctionConfiguration::functionArn)
                    .collect(Collectors.toList());

            if (functionArns.size() > 1) {
                return throwMoreThanOneLambdaMatchedException(partialName);
            } else if (functionArns.size() == 1) {
                if (optionalFunctionArn.isPresent()) {
                    return throwMoreThanOneLambdaMatchedException(partialName);
                }

                optionalFunctionArn = Optional.ofNullable(functionArns.get(0));
            }
        } while (optionalNextMarker.isPresent());

        if (optionalFunctionArn.isPresent()) {
            String fullFunctionArn = optionalFunctionArn.get() + ":" + alias;

            if (!v2LambdaHelper.functionExists(ImmutableFunctionName.builder().name(fullFunctionArn).build())) {
                throw new RuntimeException("The specified Lambda ARN [" + fullFunctionArn + "] does not exist");
            }

            return fullFunctionArn;
        }

        throw new RuntimeException("No Lambda function matched the partial name [" + partialName + "]");
    }

    private String throwMoreThanOneLambdaMatchedException(String partialName) {
        throw new RuntimeException("More than one Lambda function matched the partial name [" + partialName + "]");
    }
}
