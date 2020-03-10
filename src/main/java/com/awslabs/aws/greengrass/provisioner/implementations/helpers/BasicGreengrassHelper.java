package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.DeploymentStatus;
import com.awslabs.aws.greengrass.provisioner.data.conf.ConnectorConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.exceptions.IamReassociationNecessaryException;
import com.awslabs.aws.greengrass.provisioner.data.resources.*;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.awslabs.iot.data.ThingArn;
import com.awslabs.iot.data.ThingName;
import com.awslabs.iot.data.ThingPrincipal;
import com.awslabs.iot.helpers.interfaces.GreengrassIdExtractor;
import com.awslabs.iot.helpers.interfaces.V2GreengrassHelper;
import com.awslabs.iot.helpers.interfaces.V2IotHelper;
import com.google.common.collect.ImmutableSet;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import net.jodah.failsafe.RetryPolicy;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.GreengrassClient;
import software.amazon.awssdk.services.greengrass.model.*;
import software.amazon.awssdk.services.iam.model.Role;

import javax.inject.Inject;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BasicGreengrassHelper implements GreengrassHelper {
    private static final String DEFAULT = "Default";
    private static final int DEFAULT_LOGGER_SPACE_IN_KB = 128 * 1024;
    private static final String FAILURE = "Failure";
    private static final String IN_PROGRESS = "InProgress";
    private static final String SUCCESS = "Success";
    private static final String BUILDING = "Building";

    private final org.slf4j.Logger log = LoggerFactory.getLogger(BasicGreengrassHelper.class);
    @Inject
    GreengrassClient greengrassClient;
    @Inject
    IoHelper ioHelper;
    @Inject
    IotHelper iotHelper;
    @Inject
    V2IotHelper v2IotHelper;
    @Inject
    GGConstants ggConstants;
    @Inject
    GreengrassIdExtractor idExtractor;
    @Inject
    GreengrassResourceHelper greengrassResourceHelper;
    @Inject
    ConnectorHelper connectorHelper;
    @Inject
    V2GreengrassHelper v2GreengrassHelper;

    @Inject
    public BasicGreengrassHelper() {
    }

    @Override
    public void associateServiceRoleToAccount(Role role) {
        AssociateServiceRoleToAccountRequest associateServiceRoleToAccountRequest = AssociateServiceRoleToAccountRequest.builder()
                .roleArn(role.arn())
                .build();

        greengrassClient.associateServiceRoleToAccount(associateServiceRoleToAccountRequest);
    }

    @Override
    public String createGroupIfNecessary(String groupName) {
        Optional<GroupInformation> optionalGroupInformation = v2GreengrassHelper.getGroupInformationByName(groupName)
                .findFirst();

        if (optionalGroupInformation.isPresent()) {
            log.info("Group already exists, not creating a new one");
            return optionalGroupInformation.get().id();
        }

        log.info("Group does not exist, creating a new one");
        CreateGroupRequest createGroupRequest = CreateGroupRequest.builder()
                .name(groupName)
                .build();

        CreateGroupResponse createGroupResponse = greengrassClient.createGroup(createGroupRequest);

        return createGroupResponse.id();
    }

    @Override
    public void associateRoleToGroup(String groupId, Role greengrassRole) {
        AssociateRoleToGroupRequest associateRoleToGroupRequest = AssociateRoleToGroupRequest.builder()
                .groupId(groupId)
                .roleArn(greengrassRole.arn())
                .build();

        greengrassClient.associateRoleToGroup(associateRoleToGroupRequest);
    }

    @Override
    public String createCoreDefinitionAndVersion(String coreDefinitionName, String coreCertificateArn, String coreThingArn, boolean syncShadow) {
        String uuid = ioHelper.getUuid();

        Optional<String> optionalCoreDefinitionId = v2GreengrassHelper.getCoreDefinitionIdByName(coreDefinitionName);
        String coreDefinitionId = null;

        if (!optionalCoreDefinitionId.isPresent()) {
            CreateCoreDefinitionRequest createCoreDefinitionRequest = CreateCoreDefinitionRequest.builder()
                    .name(coreDefinitionName)
                    .build();

            CreateCoreDefinitionResponse createCoreDefinitionResponse = greengrassClient.createCoreDefinition(createCoreDefinitionRequest);
            coreDefinitionId = createCoreDefinitionResponse.id();
        } else {
            coreDefinitionId = optionalCoreDefinitionId.get();
        }

        Core core = Core.builder()
                .certificateArn(coreCertificateArn)
                .id(uuid)
                .syncShadow(syncShadow)
                .thingArn(coreThingArn)
                .build();

        CreateCoreDefinitionVersionRequest createCoreDefinitionVersionRequest = CreateCoreDefinitionVersionRequest.builder()
                .coreDefinitionId(coreDefinitionId)
                .cores(core)
                .build();

        CreateCoreDefinitionVersionResponse createCoreDefinitionVersionResponse = greengrassClient.createCoreDefinitionVersion(createCoreDefinitionVersionRequest);
        return createCoreDefinitionVersionResponse.arn();
    }

    @Override
    public Function buildFunctionModel(String functionArn, FunctionConf functionConf) {
        List<ResourceAccessPolicy> resourceAccessPolicies = new ArrayList<>();

        // Local devices and volumes could be either read-only or read-write
        List<LocalReadOnlyOrReadWriteResource> readOnlyOrReadWriteResources = new ArrayList<>();
        readOnlyOrReadWriteResources.addAll(functionConf.getLocalDeviceResources());
        readOnlyOrReadWriteResources.addAll(functionConf.getLocalVolumeResources());

        // Only include local devices and volumes if the function is in the Greengrass container
        if (functionConf.isGreengrassContainer()) {
            resourceAccessPolicies.addAll(readOnlyOrReadWriteResources.stream()
                    .map(this::getResourceAccessPolicy)
                    .collect(Collectors.toList()));
        }

        // S3 and SageMaker resources are always read-write
        List<LocalResource> readWriteResources = new ArrayList<>();
        readWriteResources.addAll(functionConf.getLocalS3Resources());
        readWriteResources.addAll(functionConf.getLocalSageMakerResources());

        // Only include S3 and SageMaker resources if the function is in the Greengrass container
        if (functionConf.isGreengrassContainer()) {
            resourceAccessPolicies.addAll(readWriteResources.stream()
                    .map(this::getReadWriteResourceAccessPolicy)
                    .collect(Collectors.toList()));
        }

        // Secrets manager resources are always read-only
        // NOTE: These are included for all functions, even when not running in the Greengrass container
        resourceAccessPolicies.addAll(functionConf.getLocalSecretsManagerResources().stream()
                .map(this::getSecretManagerResourceAccessPolicy)
                .collect(Collectors.toList()));

        FunctionConfigurationEnvironment.Builder functionConfigurationEnvironmentBuilder = FunctionConfigurationEnvironment.builder()
                .variables(functionConf.getEnvironmentVariables());

        FunctionConfiguration.Builder functionConfigurationBuilder = FunctionConfiguration.builder()
                .encodingType(functionConf.getEncodingType())
                .pinned(functionConf.isPinned())
                .timeout(functionConf.getTimeoutInSeconds());

        FunctionExecutionConfig.Builder functionExecutionConfigBuilder = FunctionExecutionConfig.builder();

        if (functionConf.isGreengrassContainer()) {
            functionExecutionConfigBuilder = functionExecutionConfigBuilder.isolationMode(FunctionIsolationMode.GREENGRASS_CONTAINER);
            functionConfigurationEnvironmentBuilder.accessSysfs(functionConf.isAccessSysFs());

            functionConfigurationBuilder = functionConfigurationBuilder.memorySize(functionConf.getMemorySizeInKb());
        } else {
            functionExecutionConfigBuilder = functionExecutionConfigBuilder.isolationMode(FunctionIsolationMode.NO_CONTAINER);
        }

        functionExecutionConfigBuilder.runAs(FunctionRunAsConfig.builder().uid(functionConf.getUid()).gid(functionConf.getGid()).build());

        functionConfigurationEnvironmentBuilder.resourceAccessPolicies(resourceAccessPolicies);

        functionConfigurationEnvironmentBuilder = functionConfigurationEnvironmentBuilder.execution(functionExecutionConfigBuilder.build());

        functionConfigurationBuilder = functionConfigurationBuilder.environment(functionConfigurationEnvironmentBuilder.build());

        Function function = Function.builder()
                .functionArn(functionArn)
                .id(ioHelper.getUuid())
                .functionConfiguration(functionConfigurationBuilder.build())
                .build();

        return function;
    }

    private ResourceAccessPolicy getSecretManagerResourceAccessPolicy(LocalSecretsManagerResource localSecretsManagerResource) {
        return ResourceAccessPolicy.builder()
                .resourceId(localSecretsManagerResource.getResourceName())
                .permission(Permission.RO)
                .build();
    }

    public ResourceAccessPolicy getResourceAccessPolicy(LocalReadOnlyOrReadWriteResource localReadOnlyOrReadWriteResource) {
        return ResourceAccessPolicy.builder()
                .resourceId(localReadOnlyOrReadWriteResource.getName())
                .permission(localReadOnlyOrReadWriteResource.isReadWrite() ? Permission.RW : Permission.RO)
                .build();
    }

    public ResourceAccessPolicy getReadWriteResourceAccessPolicy(LocalResource localResource) {
        return ResourceAccessPolicy.builder()
                .resourceId(localResource.getName())
                .permission(Permission.RW)
                .build();
    }

    @Override
    public Function buildFunctionModel(String functionArn,
                                       software.amazon.awssdk.services.lambda.model.FunctionConfiguration lambdaFunctionConfiguration,
                                       Map<String, String> defaultEnvironment,
                                       EncodingType encodingType,
                                       boolean pinned) {
        FunctionConfigurationEnvironment functionConfigurationEnvironment = FunctionConfigurationEnvironment.builder()
                .accessSysfs(true)
                .variables(defaultEnvironment)
                .build();

        FunctionConfiguration functionConfiguration = FunctionConfiguration.builder()
                .encodingType(encodingType)
                .memorySize(lambdaFunctionConfiguration.memorySize() * 1024 * 1024)
                .pinned(pinned)
                .timeout(lambdaFunctionConfiguration.timeout())
                .environment(functionConfigurationEnvironment)
                .build();

        Function function = Function.builder()
                .functionArn(functionArn)
                .id(ioHelper.getUuid())
                .functionConfiguration(functionConfiguration)
                .build();

        return function;
    }

    @Override
    public String createFunctionDefinitionVersion(Set<Function> functions, FunctionIsolationMode defaultFunctionIsolationMode) {
        functions = functions.stream()
                .filter(this::notGgIpDetector)
                .collect(Collectors.toSet());

        ImmutableSet<Function> allFunctions = ImmutableSet.<Function>builder()
                .addAll(functions)
                .add(ggConstants.getGgIpDetectorFunction())
                .build();

        FunctionDefinitionVersion.Builder functionDefinitionVersionBuilder = FunctionDefinitionVersion.builder();

        // Separate out the functions:
        //   - Functions that specify an isolation mode
        //   - Functions that don't specify an isolation mode
        //   - Functions that specify an isolation mode of NoContainer

        Set<Function> functionsWithIsolationModeSpecified = allFunctions.stream()
                .filter(function -> function.functionConfiguration().environment() != null)
                .filter(function -> function.functionConfiguration().environment().execution() != null)
                .filter(function -> function.functionConfiguration().environment().execution().isolationMode() != null)
                .collect(Collectors.toSet());

        Set<Function> functionsWithoutIsolationModeSpecified = allFunctions.stream()
                .filter(function -> !functionsWithIsolationModeSpecified.contains(function))
                .collect(Collectors.toSet());

        Set<Function> functionsWithNoContainer = functionsWithIsolationModeSpecified.stream()
                .filter(function -> function.functionConfiguration().environment().execution().isolationMode().equals(FunctionIsolationMode.NO_CONTAINER))
                .collect(Collectors.toSet());

        // Always scrub functions with the NoContainer isolation mode
        ImmutableSet.Builder<Function> functionsToScrubBuilder = ImmutableSet.builder();
        functionsToScrubBuilder.addAll(functionsWithNoContainer);

        if (defaultFunctionIsolationMode.equals(FunctionIsolationMode.NO_CONTAINER)) {
            log.warn("Default isolation mode is no container");

            // Scrub all functions without an isolation mode specified
            functionsToScrubBuilder.addAll(functionsWithoutIsolationModeSpecified);
        } else {
            log.info("Default isolation mode is Greengrass container");
        }

        functionDefinitionVersionBuilder.defaultConfig(
                FunctionDefaultConfig.builder()
                        .execution(FunctionDefaultExecutionConfig.builder()
                                .isolationMode(defaultFunctionIsolationMode)
                                .build())
                        .build());

        // Get the list of functions we need to scrub
        Set<Function> functionsToScrub = functionsToScrubBuilder.build();

        // Get the list of functions we don't need to scrub
        Set<Function> nonScrubbedFunctions = allFunctions.stream()
                .filter(function -> !functionsToScrub.contains(function))
                .collect(Collectors.toSet());

        // Scrub the necessary functions
        Set<Function> scrubbedFunctions = functionsToScrub.stream()
                .map(this::scrubFunctionForNoContainer)
                .collect(Collectors.toSet());

        // Merge the functions back together (scrubbed functions and the functions we don't need to scrub)
        allFunctions = ImmutableSet.<Function>builder()
                .addAll(scrubbedFunctions)
                .addAll(nonScrubbedFunctions)
                .build();

        functionDefinitionVersionBuilder.functions(allFunctions);

        CreateFunctionDefinitionRequest createFunctionDefinitionRequest = CreateFunctionDefinitionRequest.builder()
                .name(DEFAULT)
                .initialVersion(functionDefinitionVersionBuilder.build())
                .build();

        CreateFunctionDefinitionResponse createFunctionDefinitionResponse = greengrassClient.createFunctionDefinition(createFunctionDefinitionRequest);

        return createFunctionDefinitionResponse.latestVersionArn();
    }

    @NotNull
    private boolean notGgIpDetector(Function function) {
        return !function.functionArn().equals(ggConstants.getGgIpDetectorArn());
    }

    private Function scrubFunctionForNoContainer(Function function) {
        log.warn("Scrubbing function configuration for [" + function.functionArn() + "] to run without the Greengrass container");

        Function.Builder functionBuilder = function.toBuilder();
        FunctionConfiguration.Builder functionConfigurationBuilder = function.functionConfiguration().toBuilder();

        // Specifying a memory size for a no container function is not allowed
        functionConfigurationBuilder.memorySize(null);

        // Specifying accessSysFs for no container is not allowed
        FunctionConfigurationEnvironment.Builder functionConfigurationEnvironmentBuilder = Optional.ofNullable(function.functionConfiguration().environment())
                .map(FunctionConfigurationEnvironment::toBuilder)
                .orElse(FunctionConfigurationEnvironment.builder());

        // Force accessSysFs to null so it isn't included
        functionConfigurationEnvironmentBuilder.accessSysfs(null);

        functionConfigurationBuilder.environment(functionConfigurationEnvironmentBuilder.build());

        functionBuilder.functionConfiguration(functionConfigurationBuilder.build());

        return functionBuilder.build();
    }

    @Override
    public String createDeviceDefinitionAndVersion(String deviceDefinitionName, List<Device> devices) {
        Optional<String> optionalDeviceDefinitionId = v2GreengrassHelper.getDeviceDefinitionIdByName(deviceDefinitionName);
        String deviceDefinitionId = null;

        if (!optionalDeviceDefinitionId.isPresent()) {
            CreateDeviceDefinitionRequest createDeviceDefinitionRequest = CreateDeviceDefinitionRequest.builder()
                    .name(deviceDefinitionName)
                    .build();

            CreateDeviceDefinitionResponse createDeviceDefinitionResponse = greengrassClient.createDeviceDefinition(createDeviceDefinitionRequest);
            deviceDefinitionId = createDeviceDefinitionResponse.id();
        } else {
            deviceDefinitionId = optionalDeviceDefinitionId.get();
        }

        CreateDeviceDefinitionVersionRequest createDeviceDefinitionVersionRequest = CreateDeviceDefinitionVersionRequest.builder()
                .deviceDefinitionId(deviceDefinitionId)
                .devices(devices)
                .build();

        CreateDeviceDefinitionVersionResponse createDeviceDefinitionVersionResponse = greengrassClient.createDeviceDefinitionVersion(createDeviceDefinitionVersionRequest);
        return createDeviceDefinitionVersionResponse.arn();
    }

    @Override
    public String createSubscriptionDefinitionAndVersion(List<Subscription> subscriptions) {
        SubscriptionDefinitionVersion subscriptionDefinitionVersion = SubscriptionDefinitionVersion.builder()
                .subscriptions(subscriptions)
                .build();

        CreateSubscriptionDefinitionRequest createSubscriptionDefinitionRequest = CreateSubscriptionDefinitionRequest.builder()
                .name(DEFAULT)
                .initialVersion(subscriptionDefinitionVersion)
                .build();

        CreateSubscriptionDefinitionResponse createSubscriptionDefinitionResponse = greengrassClient.createSubscriptionDefinition(createSubscriptionDefinitionRequest);
        return createSubscriptionDefinitionResponse.latestVersionArn();
    }

    @Override
    public String createDefaultLoggerDefinitionAndVersion() {
        List<Logger> loggers = new ArrayList<>();

        loggers.add(Logger.builder()
                .id(ioHelper.getUuid())
                .component(LoggerComponent.LAMBDA)
                .level(LoggerLevel.INFO)
                .type(LoggerType.FILE_SYSTEM)
                .space(DEFAULT_LOGGER_SPACE_IN_KB)
                .build());

        loggers.add(Logger.builder()
                .id(ioHelper.getUuid())
                .component(LoggerComponent.GREENGRASS_SYSTEM)
                .level(LoggerLevel.INFO)
                .type(LoggerType.FILE_SYSTEM)
                .space(DEFAULT_LOGGER_SPACE_IN_KB)
                .build());

        loggers.add(Logger.builder()
                .id(ioHelper.getUuid())
                .component(LoggerComponent.LAMBDA)
                .level(LoggerLevel.INFO)
                .type(LoggerType.AWS_CLOUD_WATCH)
                .build());

        loggers.add(Logger.builder()
                .id(ioHelper.getUuid())
                .component(LoggerComponent.GREENGRASS_SYSTEM)
                .level(LoggerLevel.INFO)
                .type(LoggerType.AWS_CLOUD_WATCH)
                .build());

        return createLoggerDefinitionAndVersion(loggers);
    }

    @Override
    public String createLoggerDefinitionAndVersion(List<Logger> loggers) {
        LoggerDefinitionVersion loggerDefinitionVersion = LoggerDefinitionVersion.builder()
                .loggers(loggers)
                .build();

        CreateLoggerDefinitionRequest createLoggerDefinitionRequest = CreateLoggerDefinitionRequest.builder()
                .name(DEFAULT)
                .initialVersion(loggerDefinitionVersion)
                .build();

        CreateLoggerDefinitionResponse createLoggerDefinitionResponse = greengrassClient.createLoggerDefinition(createLoggerDefinitionRequest);

        return createLoggerDefinitionResponse.latestVersionArn();
    }

    @Override
    public String createGroupVersion(String groupId, GroupVersion newGroupVersion) {
        Optional<GroupInformation> optionalGroupInformation = v2GreengrassHelper.getGroupInformationById(groupId).findFirst()
                // If the group exists but has no versions yet, don't use the group information
                .filter(groupInformation -> groupInformation.latestVersion() != null);

        GroupVersion currentGroupVersion;

        if (!optionalGroupInformation.isPresent()) {
            log.warn("Group [" + groupId + "] not found or has no previous versions, creating group version from scratch");

            // There is no current version so just use the new version as our reference
            currentGroupVersion = newGroupVersion;
        } else {
            currentGroupVersion = v2GreengrassHelper.getLatestGroupVersionByGroupInformation(optionalGroupInformation.get())
                    .orElseThrow(() -> new RuntimeException("Group not found, can not continue"));
        }

        CreateGroupVersionRequest createGroupVersionRequest = CreateGroupVersionRequest.builder()
                .groupId(groupId)
                .build();

        // When an ARN in the new version is NULL we take it from the current version.  This allows us to do updates more easily.
        createGroupVersionRequest = mergeCurrentAndNewVersion(newGroupVersion, currentGroupVersion, createGroupVersionRequest.toBuilder());

        CreateGroupVersionResponse createGroupVersionResponse = greengrassClient.createGroupVersion(createGroupVersionRequest);

        return createGroupVersionResponse.version();
    }

    private CreateGroupVersionRequest mergeCurrentAndNewVersion(GroupVersion newGroupVersion, GroupVersion currentGroupVersion, CreateGroupVersionRequest.Builder createGroupVersionRequestBuilder) {
        if (newGroupVersion.connectorDefinitionVersionArn() == null) {
            createGroupVersionRequestBuilder.connectorDefinitionVersionArn(currentGroupVersion.connectorDefinitionVersionArn());
        } else {
            createGroupVersionRequestBuilder.connectorDefinitionVersionArn(newGroupVersion.connectorDefinitionVersionArn());
        }

        if (newGroupVersion.coreDefinitionVersionArn() == null) {
            createGroupVersionRequestBuilder.coreDefinitionVersionArn(currentGroupVersion.coreDefinitionVersionArn());
        } else {
            createGroupVersionRequestBuilder.coreDefinitionVersionArn(newGroupVersion.coreDefinitionVersionArn());
        }

        if (newGroupVersion.functionDefinitionVersionArn() == null) {
            createGroupVersionRequestBuilder.functionDefinitionVersionArn(currentGroupVersion.functionDefinitionVersionArn());
        } else {
            createGroupVersionRequestBuilder.functionDefinitionVersionArn(newGroupVersion.functionDefinitionVersionArn());
        }

        if (newGroupVersion.subscriptionDefinitionVersionArn() == null) {
            createGroupVersionRequestBuilder.subscriptionDefinitionVersionArn(currentGroupVersion.subscriptionDefinitionVersionArn());
        } else {
            createGroupVersionRequestBuilder.subscriptionDefinitionVersionArn(newGroupVersion.subscriptionDefinitionVersionArn());
        }

        if (newGroupVersion.deviceDefinitionVersionArn() == null) {
            createGroupVersionRequestBuilder.deviceDefinitionVersionArn(currentGroupVersion.deviceDefinitionVersionArn());
        } else {
            createGroupVersionRequestBuilder.deviceDefinitionVersionArn(newGroupVersion.deviceDefinitionVersionArn());
        }

        if (newGroupVersion.loggerDefinitionVersionArn() == null) {
            createGroupVersionRequestBuilder.loggerDefinitionVersionArn(currentGroupVersion.loggerDefinitionVersionArn());
        } else {
            createGroupVersionRequestBuilder.loggerDefinitionVersionArn(newGroupVersion.loggerDefinitionVersionArn());
        }

        if (newGroupVersion.resourceDefinitionVersionArn() == null) {
            createGroupVersionRequestBuilder.resourceDefinitionVersionArn(currentGroupVersion.resourceDefinitionVersionArn());
        } else {
            createGroupVersionRequestBuilder.resourceDefinitionVersionArn(newGroupVersion.resourceDefinitionVersionArn());
        }

        return createGroupVersionRequestBuilder.build();
    }

    @Override
    public String createDeployment(String groupId, String groupVersionId) {
        CreateDeploymentRequest createDeploymentRequest = CreateDeploymentRequest.builder()
                .groupId(groupId)
                .groupVersionId(groupVersionId)
                .deploymentType(DeploymentType.NEW_DEPLOYMENT)
                .build();

        CreateDeploymentResponse createDeploymentResponse = greengrassClient.createDeployment(createDeploymentRequest);
        return createDeploymentResponse.deploymentId();
    }

    @Override
    public DeploymentStatus waitForDeploymentStatusToChange(String groupId, String deploymentId) {
        GetDeploymentStatusRequest getDeploymentStatusRequest = GetDeploymentStatusRequest.builder()
                .groupId(groupId)
                .deploymentId(deploymentId)
                .build();

        // If the deployment is building we will retry
        RetryPolicy<GetDeploymentStatusResponse> buildingDeploymentPolicy = new RetryPolicy<GetDeploymentStatusResponse>()
                .withDelay(Duration.ofSeconds(5))
                .withMaxRetries(10)
                .handleResultIf(this::isBuilding)
                .onRetry(failure -> log.warn("Waiting for the deployment to transition to in progress..."))
                .onRetriesExceeded(failure -> log.error("Deployment never transitioned to in progress. Cannot continue."));

        // If the deployment has any of these fatal errors we will give up immediately
        Fallback<GetDeploymentStatusResponse> failureDeploymentStatusPolicy = Fallback.of(GetDeploymentStatusResponse.builder().deploymentStatus(FAILURE).build())
                // Greengrass probably can't read a SageMaker model #1
                .handleResultIf(statusResponse -> shouldRedeploy(statusResponse, "Greengrass does not have permission to read the object", "If you are using a SageMaker model your Greengrass service role may not have access to the bucket where your model is stored."))
                // Greengrass probably can't read a SageMaker model #2
                .handleResultIf(statusResponse -> shouldRedeploy(statusResponse, "refers to a resource transfer-learning-example with nonexistent S3 object", "If you are using a SageMaker model your model appears to no longer exist in S3."))
                // Configuration is not valid
                .handleResultIf(statusResponse -> shouldRedeploy(statusResponse, "group config is invalid", "Group configuration is invalid"))
                // Group definition is not valid
                .handleResultIf(statusResponse -> shouldRedeploy(statusResponse, "We cannot deploy because the group definition is invalid or corrupted", "Group definition is invalid"))
                // Artifact could not be downloaded
                .handleResultIf(statusResponse -> shouldRedeploy(statusResponse, "Artifact download retry exceeded the max retries", "Artifact could not be downloaded (exceeded maximum retries)"))
                // Greengrass is not configured to run as root but some Lambda functions require root
                .handleResultIf(statusResponse -> shouldRedeploy(statusResponse, "Greengrass is not configured to run lambdas with root permissions", "Greengrass is not configured to run as root but some Lambda functions are configured to run as root"))
                // The user or group Greengrass is running as does not have access to a file on the host
                .handleResultIf(statusResponse -> shouldRedeploy(statusResponse, "user or group doesn't have permission on the file", "The user or group Greengrass is running as does not have access to a file on the host"))
                // A file is missing on the Greengrass host
                .handleResultIf(statusResponse -> shouldRedeploy(statusResponse, "file doesn't exist", "A file is missing on the Greengrass host"))
                // A configuration parameter is invalid
                .handleResultIf(statusResponse -> shouldRedeploy(statusResponse, Arrays.asList("configuration parameter", "does not match required pattern"), "One or more configuration parameters were specified that did not match the allowed patterns. Adjust the values and try again."));

        // If the deployment is failing because of apparent IAM issues we'll throw an exception and the caller will try again
        Fallback<GetDeploymentStatusResponse> needsIamReassociationPolicy = Fallback.<GetDeploymentStatusResponse>ofException(e -> new IamReassociationNecessaryException())
                // No service role associated with this account
                .handleResultIf(statusResponse -> throwIamReassociationExceptionIfNecessary(statusResponse, "TES service role is not associated with this account", "A service role is not associated with this account for Greengrass. See the Greengrass service role documentation for more information [https://docs.aws.amazon.com/greengrass/latest/developerguide/service-role.html]"))
                // Service role may be missing
                .handleResultIf(statusResponse -> throwIamReassociationExceptionIfNecessary(statusResponse, "GreenGrass is not authorized to assume the Service Role associated with this account", "The service role associated with this account may be missing. Check that the role returned from the CLI command 'aws greengrass get-service-role-for-account' still exists. See the Greengrass service role documentation for more information [https://docs.aws.amazon.com/greengrass/latest/developerguide/service-role.html]"))
                // Invalid security token
                .handleResultIf(statusResponse -> throwIamReassociationExceptionIfNecessary(statusResponse, "security token included in the request is invalid", "Invalid security token, a redeployment is necessary"))
                // Cloud service event
                .handleResultIf(statusResponse -> throwIamReassociationExceptionIfNecessary(statusResponse, "having a problem right now", "Cloud service event, a redeployment is necessary"));

        log.info("Checking deployment status...");

        GetDeploymentStatusResponse getDeploymentStatusResponse = Failsafe.with(needsIamReassociationPolicy, failureDeploymentStatusPolicy, buildingDeploymentPolicy)
                .get(() -> greengrassClient.getDeploymentStatus(getDeploymentStatusRequest));

        String deploymentStatus = getDeploymentStatusResponse.deploymentStatus();

        switch (deploymentStatus) {
            case IN_PROGRESS:
            case SUCCESS:
                return DeploymentStatus.SUCCESSFUL;
            case FAILURE:
                return DeploymentStatus.FAILED;
        }

        throw new RuntimeException("Unexpected deployment status [" + deploymentStatus + "]");
    }

    private boolean throwIamReassociationExceptionIfNecessary(GetDeploymentStatusResponse getDeploymentStatusResponse, String expectedPartialErrorString, String logMessage) {
        // Is this a failure?
        if (shouldRedeploy(getDeploymentStatusResponse, expectedPartialErrorString, logMessage)) {
            // Yes, the caller should throw an exception
            return true;
        }

        // Not a failure, ignore
        return false;
    }

    private boolean isBuilding(GetDeploymentStatusResponse getDeploymentStatusResponse) {
        if (getDeploymentStatusResponse.deploymentStatus().equals(BUILDING)) {
            log.info("Deployment is being built...");

            // A failure in the sense that it is not ready yet
            return true;
        }

        return false;
    }

    private boolean shouldRedeploy(GetDeploymentStatusResponse getDeploymentStatusResponse, String expectedPartialErrorString, String logMessage) {
        return shouldRedeploy(getDeploymentStatusResponse, Collections.singletonList(expectedPartialErrorString), logMessage);
    }

    /**
     * Checks to see if a deployment status error message matches all of the expected partial strings passed in
     *
     * @param getDeploymentStatusResponse
     * @param expectedPartialErrorStrings
     * @param logMessage
     * @return true if all expected strings are found, false if one or more expected strings are not found
     */
    protected boolean shouldRedeploy(GetDeploymentStatusResponse getDeploymentStatusResponse, List<String> expectedPartialErrorStrings, String logMessage) {
        String errorMessage = getDeploymentStatusResponse.errorMessage();

        if (!expectedPartialErrorStrings.stream().allMatch(errorMessage::contains)) {
            // Didn't find one or more of the required matches, this is not a match
            return false;
        }

        // Yes, this is the error we expected
        log.error(logMessage);

        return true;
    }

    @Override
    public String createResourceDefinitionFromFunctionConfs(List<FunctionConf> functionConfs) {
        // Log that the local resources from functions outside of the Greengrass container will be scrubbed
        functionConfs.stream()
                .filter(functionConf -> !functionConf.isGreengrassContainer())
                .filter(this::functionConfHasLocalResources)
                .forEach(this::logLocalResourcesScrubbed);

        // Get functions running in the Greengrass container
        List<FunctionConf> functionsInGreengrassContainer = functionConfs.stream()
                .filter(FunctionConf::isGreengrassContainer)
                .collect(Collectors.toList());

        // Only get the resources for functions in the container
        List<LocalDeviceResource> localDeviceResources = greengrassResourceHelper.flatMapResources(functionsInGreengrassContainer, FunctionConf::getLocalDeviceResources);
        List<LocalVolumeResource> localVolumeResources = greengrassResourceHelper.flatMapResources(functionsInGreengrassContainer, FunctionConf::getLocalVolumeResources);
        List<LocalS3Resource> localS3Resources = greengrassResourceHelper.flatMapResources(functionsInGreengrassContainer, FunctionConf::getLocalS3Resources);
        List<LocalSageMakerResource> localSageMakerResources = greengrassResourceHelper.flatMapResources(functionsInGreengrassContainer, FunctionConf::getLocalSageMakerResources);

        // Secrets from secrets manager work outside of the container so don't filter those
        List<LocalSecretsManagerResource> localSecretsManagerResources = greengrassResourceHelper.flatMapResources(functionConfs, FunctionConf::getLocalSecretsManagerResources);

        return createResourceDefinition(localDeviceResources, localVolumeResources, localS3Resources, localSageMakerResources, localSecretsManagerResources);
    }

    public String createResourceDefinition(List<LocalDeviceResource> localDeviceResources, List<LocalVolumeResource> localVolumeResources, List<LocalS3Resource> localS3Resources, List<LocalSageMakerResource> localSageMakerResources, List<LocalSecretsManagerResource> localSecretsManagerResources) {
        // Filter out duplicate devices (only the path needs to be the same)
        localDeviceResources = localDeviceResources.stream()
                .filter(distinctByKey(LocalDeviceResource::getPath))
                .collect(Collectors.toList());

        // Filter out duplicate volumes (only the source path needs to be the same)
        localVolumeResources = localVolumeResources.stream()
                .filter(distinctByKey(LocalVolumeResource::getPath))
                .collect(Collectors.toList());

        List<Resource> resources = new ArrayList<>();

        // Add those resources to the list
        resources.addAll(processLocalDeviceResources(localDeviceResources));
        resources.addAll(processLocalVolumeResources(localVolumeResources));
        resources.addAll(processLocalS3Resources(localS3Resources));
        resources.addAll(processLocalSageMakerResources(localSageMakerResources));
        resources.addAll(processLocalSecretsManagerResources(localSecretsManagerResources));

        ResourceDefinitionVersion resourceDefinitionVersion = ResourceDefinitionVersion.builder()
                .resources(resources)
                .build();

        greengrassResourceHelper.validateResourceDefinitionVersion(resourceDefinitionVersion);

        CreateResourceDefinitionRequest createResourceDefinitionRequest = CreateResourceDefinitionRequest.builder()
                .initialVersion(resourceDefinitionVersion)
                .name(ioHelper.getUuid())
                .build();

        CreateResourceDefinitionResponse createResourceDefinitionResponse = greengrassClient.createResourceDefinition(createResourceDefinitionRequest);

        return createResourceDefinitionResponse.latestVersionArn();
    }

    // From https://stackoverflow.com/a/27872852/796579
    public <T> Predicate<T> distinctByKey(java.util.function.Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    private boolean functionConfHasLocalResources(FunctionConf functionConf) {
        if (functionConf.getLocalDeviceResources().size() > 0) {
            return true;
        }

        if (functionConf.getLocalS3Resources().size() > 0) {
            return true;
        }

        if (functionConf.getLocalSageMakerResources().size() > 0) {
            return true;
        }

        if (functionConf.getLocalSecretsManagerResources().size() > 0) {
            return true;
        }

        if (functionConf.getLocalVolumeResources().size() > 0) {
            return true;
        }

        // No local resources found
        return false;
    }

    private List<Resource> processLocalSageMakerResources(List<LocalSageMakerResource> localSageMakerResources) {
        return localSageMakerResources.stream()
                .map(this::processLocalSageMakerResource)
                .collect(Collectors.toList());
    }

    private Resource processLocalSageMakerResource(LocalSageMakerResource localSageMakerResource) {
        SageMakerMachineLearningModelResourceData sageMakerMachineLearningModelResourceData = SageMakerMachineLearningModelResourceData.builder()
                .sageMakerJobArn(localSageMakerResource.getArn())
                .destinationPath(localSageMakerResource.getPath())
                .build();

        ResourceDataContainer resourceDataContainer = ResourceDataContainer.builder()
                .sageMakerMachineLearningModelResourceData(sageMakerMachineLearningModelResourceData)
                .build();

        return createResource(resourceDataContainer, localSageMakerResource.getName(), localSageMakerResource.getName());
    }

    private List<Resource> processLocalSecretsManagerResources(List<LocalSecretsManagerResource> localSecretsManagerResources) {
        return localSecretsManagerResources.stream()
                .map(this::processLocalSecretsManagerResource)
                .collect(Collectors.toList());
    }

    private Resource processLocalSecretsManagerResource(LocalSecretsManagerResource localSecretsManagerResource) {
        SecretsManagerSecretResourceData secretsManagerSecretResourceData = SecretsManagerSecretResourceData.builder()
                .arn(localSecretsManagerResource.getArn())
                .build();

        ResourceDataContainer resourceDataContainer = ResourceDataContainer.builder()
                .secretsManagerSecretResourceData(secretsManagerSecretResourceData)
                .build();

        return createResource(resourceDataContainer, localSecretsManagerResource.getResourceName(), localSecretsManagerResource.getResourceName());
    }

    private List<Resource> processLocalS3Resources(List<LocalS3Resource> localS3Resources) {
        return localS3Resources.stream()
                .map(this::processLocalS3Resource)
                .collect(Collectors.toList());
    }

    private Resource processLocalS3Resource(LocalS3Resource localS3Resource) {
        S3MachineLearningModelResourceData s3MachineLearningModelResourceData = S3MachineLearningModelResourceData.builder()
                .s3Uri(localS3Resource.getUri())
                .destinationPath(localS3Resource.getPath())
                .build();

        ResourceDataContainer resourceDataContainer = ResourceDataContainer.builder()
                .s3MachineLearningModelResourceData(s3MachineLearningModelResourceData)
                .build();

        return createResource(resourceDataContainer, localS3Resource.getName(), localS3Resource.getName());
    }

    private List<Resource> processLocalVolumeResources(List<LocalVolumeResource> localVolumeResources) {
        return localVolumeResources.stream()
                .map(this::processLocalVolumeResource)
                .collect(Collectors.toList());
    }

    private Resource processLocalVolumeResource(LocalVolumeResource localVolumeResource) {
        GroupOwnerSetting groupOwnerSetting = GroupOwnerSetting.builder()
                .autoAddGroupOwner(true)
                .build();

        LocalVolumeResourceData localVolumeResourceData = LocalVolumeResourceData.builder()
                .groupOwnerSetting(groupOwnerSetting)
                .sourcePath(localVolumeResource.getSourcePath())
                .destinationPath(localVolumeResource.getDestinationPath())
                .build();

        ResourceDataContainer resourceDataContainer = ResourceDataContainer.builder()
                .localVolumeResourceData(localVolumeResourceData)
                .build();

        return createResource(resourceDataContainer, localVolumeResource.getName(), localVolumeResource.getName());
    }

    private List<Resource> processLocalDeviceResources(List<LocalDeviceResource> localDeviceResources) {
        return localDeviceResources.stream()
                .map(this::processLocalDeviceResource)
                .collect(Collectors.toList());
    }

    private Resource processLocalDeviceResource(LocalDeviceResource localDeviceResource) {
        GroupOwnerSetting groupOwnerSetting = GroupOwnerSetting.builder()
                .autoAddGroupOwner(true)
                .build();

        LocalDeviceResourceData localDeviceResourceData = LocalDeviceResourceData.builder()
                .groupOwnerSetting(groupOwnerSetting)
                .sourcePath(localDeviceResource.getPath())
                .build();

        ResourceDataContainer resourceDataContainer = ResourceDataContainer.builder()
                .localDeviceResourceData(localDeviceResourceData)
                .build();

        return createResource(resourceDataContainer, localDeviceResource.getName(), localDeviceResource.getName());
    }

    private void logLocalResourcesScrubbed(FunctionConf functionConf) {
        log.warn("Scrubbing local resources from [" + functionConf.getFunctionName() + "] because it is not running in the Greengrass container");
    }

    private Resource createResource(ResourceDataContainer resourceDataContainer, String name, String id) {
        return Resource.builder()
                .resourceDataContainer(resourceDataContainer)
                .name(name)
                .id(id)
                .build();
    }

    @Override
    public Device getDevice(ThingName thingName) {
        Optional<ThingArn> optionalThingArn = v2IotHelper.getThingArn(thingName);

        if (!optionalThingArn.isPresent()) {
            rethrowResourceNotFoundException(thingName.getName());
        }

        String thingArn = optionalThingArn.get().getArn();

        Optional<List<ThingPrincipal>> optionalThingPrincipals = v2IotHelper.getThingPrincipals(thingName);

        if (!optionalThingPrincipals.isPresent()) {
            rethrowResourceNotFoundException(thingName.getName());
        }

        List<ThingPrincipal> thingPrincipals = optionalThingPrincipals.get();

        if (thingPrincipals.size() != 1) {
            throw new RuntimeException("More than one principal found for [" + thingName + "], can not continue");
        }

        String certificateArn = thingPrincipals.get(0).getPrincipal();

        return Device.builder()
                .certificateArn(certificateArn)
                .id(ioHelper.getUuid())
                .syncShadow(true)
                .thingArn(thingArn)
                .build();
    }

    /**
     * Rethrows the exception with a more succinct message
     *
     * @param thingName
     * @return
     */
    public String rethrowResourceNotFoundException(String thingName) {
        throw new RuntimeException("Thing [" + thingName + "] does not exist");
    }

    @Override
    public void disassociateServiceRoleFromAccount() {
        greengrassClient.disassociateServiceRoleFromAccount(DisassociateServiceRoleFromAccountRequest.builder().build());
    }

    @Override
    public void disassociateRoleFromGroup(String groupId) {
        greengrassClient.disassociateRoleFromGroup(DisassociateRoleFromGroupRequest.builder()
                .groupId(groupId)
                .build());
    }

    @Override
    public Optional<String> createConnectorDefinitionVersion(List<ConnectorConf> connectorConfList) {
        if (connectorConfList.isEmpty()) {
            return Optional.empty();
        }

        ConnectorDefinitionVersion connectorDefinitionVersion = ConnectorDefinitionVersion.builder()
                .connectors(connectorConfList.stream()
                        .map(ConnectorConf::getConnector)
                        .collect(Collectors.toList()))
                .build();

        connectorHelper.validateConnectorDefinitionVersion(connectorDefinitionVersion);

        CreateConnectorDefinitionRequest createConnectorDefinitionRequest = CreateConnectorDefinitionRequest.builder()
                .name(DEFAULT)
                .initialVersion(connectorDefinitionVersion)
                .build();

        CreateConnectorDefinitionResponse createConnectorDefinitionResponse = greengrassClient.createConnectorDefinition(createConnectorDefinitionRequest);

        return Optional.of(createConnectorDefinitionResponse.latestVersionArn());
    }

    @Override
    public FunctionIsolationMode getDefaultIsolationMode(GroupInformation groupInformation) {
        Optional<FunctionIsolationMode> optionalFunctionIsolationMode = v2GreengrassHelper.getDefaultIsolationModeByGroupInformation(groupInformation);

        if (optionalFunctionIsolationMode.isPresent()) {
            return optionalFunctionIsolationMode.get();
        }

        log.warn("Default function isolation mode was not present, defaulting to Greengrass container");
        return FunctionIsolationMode.GREENGRASS_CONTAINER;
    }
}
