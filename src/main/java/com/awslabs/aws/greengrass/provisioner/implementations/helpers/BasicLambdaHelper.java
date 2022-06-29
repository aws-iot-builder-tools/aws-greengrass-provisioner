package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.*;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.*;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.LambdaHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.LoggingHelper;
import com.awslabs.lambda.data.FunctionAliasArn;
import com.awslabs.lambda.data.FunctionName;
import com.awslabs.lambda.data.ImmutableFunctionAliasArn;
import com.awslabs.lambda.helpers.interfaces.V2LambdaHelper;
import io.vavr.control.Either;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.text.StringEscapeUtils;
import org.gradle.tooling.BuildException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;
import software.amazon.awssdk.services.lambda.waiters.LambdaWaiter;

import javax.inject.Inject;
import java.io.File;
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
        log.info(String.join("", "Creating executable/native function [", functionConf.getFunctionName().getName(), "]"));

        String zipFilePath = String.join("/", functionConf.getBuildDirectory().get().toString(),
                String.join(".", functionConf.getFunctionName().getName(), "zip"));

        File zipFile = new File(zipFilePath);

        if (!zipFile.exists()) {
            log.warn(String.join("", ZIP_ARCHIVE_FOR_EXECUTABLE_NATIVE_FUNCTION_NOT_PRESENT, "[", zipFile.getName(), "], attempting build"));
            executableBuilder.buildExecutableFunctionIfNecessary(functionConf);
        }

        if (!zipFile.exists()) {
            throw new RuntimeException(String.join("", ZIP_ARCHIVE_FOR_EXECUTABLE_NATIVE_FUNCTION_NOT_PRESENT, "[", zipFile.getName(), "]"));
        }

        return ImmutableZipFilePathAndFunctionConf.builder()
                .zipFilePath(zipFilePath)
                .functionConf(functionConf)
                .build();
    }

    @Override
    public ImmutableZipFilePathAndFunctionConf buildJavaFunction(FunctionConf functionConf) {
        log.info(String.join("", "Creating Java function [", functionConf.getFunctionName().getName(), "]"));

        if (gradleBuilder.isGradleFunction(functionConf)) {
            return buildGradleFunction(functionConf);

        } else {
            throw new RuntimeException(String.join("", "This function [", functionConf.getFunctionName().getName(), "] is not a Gradle project.  It cannot be built automatically."));
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
        log.info(String.join("", "Creating Python 2 function [", functionConf.getFunctionName().getName(), "]"));

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
        log.info(String.join("", "Creating Python 3 function [", functionConf.getFunctionName().getName(), "]"));

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
        log.info(String.join("", "Creating Node function [", functionConf.getFunctionName().getName(), "]"));

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
    public Either<CreateFunctionResponse, UpdateFunctionConfigurationResponse> createOrUpdateFunction(FunctionConfAndFunctionCode functionConfAndFunctionCode, Role role) {
        String runtime;

        FunctionConf functionConf = functionConfAndFunctionCode.functionConf();
        FunctionCode functionCode = functionConfAndFunctionCode.functionCode();

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

        if (v2LambdaHelper.functionExists(functionConf.getGroupFunctionName())) {

            LambdaWaiter waiter = lambdaClient.waiter();

            GetFunctionConfigurationRequest functionRequest = GetFunctionConfigurationRequest.builder().functionName(functionConf.getFunctionName()).build();

            WaiterResponse<GetFunctionConfigurationResponse> waitUntilFunctionActive = waiter.waitUntilFunctionActive(functionRequest);
            
            waitUntilFunctionActive.matched().response().ifPresent(System.out::println);
            
            Optional<GetFunctionResponse> newGetFunctionResponseOptional = v2LambdaHelper.getFunction(functionConf.getFunctionName());
            
            if (newGetFunctionResponseOptional.isPresent()) {
                GetFunctionResponse getFunctionResponse = newGetFunctionResponseOptional.get();
                
                log.info(String.join("", "createOrUpdateFunction Function New State [", functionConf.getFunctionName(), ":", getFunctionResponse.configuration().state().toString(), "]"));
            }

            // Update the function
            return Either.right(updateExistingLambdaFunction(functionConf, role, functionConf.getFunctionName(), functionConf.getGroupFunctionName(), functionCode, runtime, lambdaIamRoleRetryPolicy));
        }

        // Create a new function
        return Either.left(createNewLambdaFunction(functionConf, role, functionConf.getFunctionName(), functionConf.getGroupFunctionName(), functionCode, runtime, lambdaIamRoleRetryPolicy));
    }

/*
    @Override
    public Either<CreateFunctionResponse, UpdateFunctionConfigurationResponse> createOrUpdateFunction(FunctionConfAndFunctionCode functionConfAndFunctionCode, Role role) {
        String runtime;

        FunctionConf functionConf = functionConfAndFunctionCode.functionConf();
        FunctionCode functionCode = functionConfAndFunctionCode.functionCode();

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

        if (v2LambdaHelper.functionExists(functionConf.getGroupFunctionName())) {
            // Update the function
            return Either.right(updateExistingLambdaFunction(functionConf, role, functionConf.getFunctionName(), functionConf.getGroupFunctionName(), functionCode, runtime, lambdaIamRoleRetryPolicy));
        }

        // Create a new function
        return Either.left(createNewLambdaFunction(functionConf, role, functionConf.getFunctionName(), functionConf.getGroupFunctionName(), functionCode, runtime, lambdaIamRoleRetryPolicy));
    }
*/

    @Override
    public LambdaFunctionArnInfo publishLambdaFunctionVersion(FunctionName functionName) {
        Optional<GetFunctionResponse> getFunctionResponseOptional = v2LambdaHelper.getFunction(functionName);
        
        if (getFunctionResponseOptional.isPresent()) {
            GetFunctionResponse getFunctionResponse = getFunctionResponseOptional.get();
            
            log.info(String.join("", "publishLambdaFunctionVersion Function State [", functionName.getName(), ":", getFunctionResponse.configuration().state().toString(), "]"));
        }
        
        LambdaWaiter waiter = lambdaClient.waiter();
        
        GetFunctionConfigurationRequest functionRequest = GetFunctionConfigurationRequest.builder().functionName(functionName.getName()).build();
        
        WaiterResponse<GetFunctionConfigurationResponse> waitUntilFunctionActive = waiter.waitUntilFunctionActive(functionRequest);
        
        waitUntilFunctionActive.matched().response().ifPresent(System.out::println);
        
        Optional<GetFunctionResponse> newGetFunctionResponseOptional = v2LambdaHelper.getFunction(functionName);
        
        if (newGetFunctionResponseOptional.isPresent()) {
            GetFunctionResponse getFunctionResponse = newGetFunctionResponseOptional.get();
            
            log.info(String.join("", "publishLambdaFunctionVersion Function New State [", functionName.getName(), ":", getFunctionResponse.configuration().state().toString(), "]"));
        }
        
        PublishVersionResponse publishVersionResponse = v2LambdaHelper.publishFunctionVersion(functionName);

        String qualifier = publishVersionResponse.version();
        String qualifiedArn = publishVersionResponse.functionArn();
        String baseArn = qualifiedArn.replaceAll(String.join("", ":", qualifier, "$"), "");

        return ImmutableLambdaFunctionArnInfo.builder()
                .qualifier(qualifier)
                .qualifiedArn(qualifiedArn)
                .baseArn(baseArn)
                .build();
    }

/*
    @Override
    public LambdaFunctionArnInfo publishLambdaFunctionVersion(FunctionName functionName) {
        
        PublishVersionResponse publishVersionResponse = v2LambdaHelper.publishFunctionVersion(functionName);

        String qualifier = publishVersionResponse.version();
        String qualifiedArn = publishVersionResponse.functionArn();
        String baseArn = qualifiedArn.replaceAll(String.join("", ":", qualifier, "$"), "");

        return ImmutableLambdaFunctionArnInfo.builder()
                .qualifier(qualifier)
                .qualifiedArn(qualifiedArn)
                .baseArn(baseArn)
                .build();
    }
*/
    private UpdateFunctionConfigurationResponse updateExistingLambdaFunction(FunctionConf functionConf, Role role, FunctionName baseFunctionName, FunctionName functionName, FunctionCode functionCode, String runtime, RetryPolicy<LambdaResponse> lambdaIamRoleRetryPolicy) {
        loggingHelper.logInfoWithName(log, baseFunctionName.getName(), "Updating Lambda function code");

        UpdateFunctionCodeRequest.Builder updateFunctionCodeRequestBuilder = UpdateFunctionCodeRequest.builder()
                .functionName(functionName.getName());

        if (functionCode.zipFile() != null) {
            updateFunctionCodeRequestBuilder.zipFile(functionCode.zipFile());
        } else {
            updateFunctionCodeRequestBuilder.s3Bucket(functionCode.s3Bucket());
            updateFunctionCodeRequestBuilder.s3Key(functionCode.s3Key());
        }

        UpdateFunctionCodeRequest updateFunctionCodeRequest = updateFunctionCodeRequestBuilder.build();

        // Make sure multiple threads don't do this at the same time
        synchronized (this) {
            Failsafe.with(lambdaIamRoleRetryPolicy).get(() ->
                    lambdaClient.updateFunctionCode(updateFunctionCodeRequest));
        }

        Map<String, String> existingEnvironment = v2LambdaHelper.getFunctionEnvironment(functionName);

        HashMap<String, String> newEnvironment = updateGgpFunctionConfInEnvironment(functionConf, existingEnvironment);

        Environment lambdaEnvironment = Environment.builder().variables(newEnvironment).build();

        loggingHelper.logInfoWithName(log, baseFunctionName.getName(), "Updating Lambda function configuration");

        UpdateFunctionConfigurationRequest updateFunctionConfigurationRequest = UpdateFunctionConfigurationRequest.builder()
                .functionName(functionName.getName())
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

    private CreateFunctionResponse createNewLambdaFunction(FunctionConf functionConf, Role role, FunctionName baseFunctionName, FunctionName functionName, FunctionCode functionCode, String runtime, RetryPolicy<LambdaResponse> lambdaIamRoleRetryPolicy) {
        loggingHelper.logInfoWithName(log, baseFunctionName.getName(), "Creating new Lambda function");

        // No environment, start with an empty one
        HashMap<String, String> newEnvironment = updateGgpFunctionConfInEnvironment(functionConf, new HashMap<>());

        Environment lambdaEnvironment = Environment.builder().variables(newEnvironment).build();

        CreateFunctionRequest createFunctionRequest = CreateFunctionRequest.builder()
                .functionName(functionName.getName())
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
    public FunctionAliasArn findFullFunctionArnByPartialName(String partialName) {
        ListFunctionsResponse listFunctionsResponse;

        String partialNameWithoutAlias = partialName.substring(0, partialName.lastIndexOf(":"));
        String escapedPartialName = StringEscapeUtils.escapeJava(partialNameWithoutAlias);
        String patternString = String.join("", "^", escapedPartialName.replaceAll("~", ".*"), "$");
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
            String fullFunctionArn = String.join("", optionalFunctionArn.get(), ":", alias);

            FunctionAliasArn functionAliasArn = ImmutableFunctionAliasArn.builder().aliasArn(fullFunctionArn).build();

            if (!v2LambdaHelper.functionExists(functionAliasArn)) {
                throw new RuntimeException(String.join("", "The specified Lambda ARN [", fullFunctionArn, "] does not exist"));
            }

            return functionAliasArn;
        }

        throw new RuntimeException(String.join("", "No Lambda function matched the partial name [", partialName, "]"));
    }

    private FunctionAliasArn throwMoreThanOneLambdaMatchedException(String partialName) {
        throw new RuntimeException(String.join("", "More than one Lambda function matched the partial name [", partialName, "]"));
    }
}
