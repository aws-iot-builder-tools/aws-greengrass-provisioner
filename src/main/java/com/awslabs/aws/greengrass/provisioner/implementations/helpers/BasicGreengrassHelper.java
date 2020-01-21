package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.DeploymentStatus;
import com.awslabs.aws.greengrass.provisioner.data.conf.ConnectorConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.resources.*;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.awslabs.aws.iot.resultsiterator.helpers.interfaces.GreengrassIdExtractor;
import com.google.common.collect.ImmutableSet;
import io.vavr.control.Try;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.GreengrassClient;
import software.amazon.awssdk.services.greengrass.model.*;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iot.model.ResourceNotFoundException;

import javax.inject.Inject;
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
    GGConstants ggConstants;
    @Inject
    GreengrassIdExtractor idExtractor;
    @Inject
    GreengrassResourceHelper greengrassResourceHelper;
    @Inject
    ConnectorHelper connectorHelper;

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
    public Optional<GroupInformation> getGroupInformation(String groupNameOrGroupId) {
        ListGroupsRequest listGroupsRequest = ListGroupsRequest.builder().build();

        ListGroupsResponse listGroupsResponse;

        do {
            listGroupsResponse = greengrassClient.listGroups(listGroupsRequest);

            for (GroupInformation groupInformation : listGroupsResponse.groups()) {
                if (groupInformation.name().equals(groupNameOrGroupId)) {
                    return Optional.ofNullable(groupInformation);
                }

                if (groupInformation.id().equals(groupNameOrGroupId)) {
                    return Optional.ofNullable(groupInformation);
                }
            }

            listGroupsRequest = ListGroupsRequest.builder().nextToken(listGroupsResponse.nextToken()).build();
        } while (listGroupsResponse.nextToken() != null);

        log.warn("No group was found with name or ID [" + groupNameOrGroupId + "]");
        return Optional.empty();
    }

    private Optional<String> getGroupId(String groupName) {
        Optional<GroupInformation> optionalGroupInformation = getGroupInformation(groupName);

        return optionalGroupInformation.map(GroupInformation::id);
    }

    private String getCoreDefinitionId(String coreDefinitionName) {
        ListCoreDefinitionsRequest listCoreDefinitionsRequest = ListCoreDefinitionsRequest.builder().build();

        ListCoreDefinitionsResponse listCoreDefinitionsResponse;

        do {
            listCoreDefinitionsResponse = greengrassClient.listCoreDefinitions(listCoreDefinitionsRequest);

            for (DefinitionInformation definitionInformation : listCoreDefinitionsResponse.definitions()) {
                if (definitionInformation.name() == null) {
                    continue;
                }

                if (definitionInformation.name().equals(coreDefinitionName)) {
                    return definitionInformation.id();
                }
            }

            listCoreDefinitionsRequest = ListCoreDefinitionsRequest.builder().nextToken(listCoreDefinitionsResponse.nextToken()).build();
        } while (listCoreDefinitionsResponse.nextToken() != null);

        return null;
    }

    private String getDeviceDefinitionId(String deviceDefinitionName) {
        ListDeviceDefinitionsRequest listDeviceDefinitionsRequest = ListDeviceDefinitionsRequest.builder().build();

        ListDeviceDefinitionsResponse listDeviceDefinitionsResponse;

        do {
            listDeviceDefinitionsResponse = greengrassClient.listDeviceDefinitions(listDeviceDefinitionsRequest);

            for (DefinitionInformation definitionInformation : listDeviceDefinitionsResponse.definitions()) {
                if (definitionInformation.name() == null) {
                    continue;
                }

                if (definitionInformation.name().equals(deviceDefinitionName)) {
                    return definitionInformation.id();
                }
            }

            listDeviceDefinitionsRequest = ListDeviceDefinitionsRequest.builder().nextToken(listDeviceDefinitionsResponse.nextToken()).build();
        } while (listDeviceDefinitionsResponse.nextToken() != null);

        return null;
    }

    @Override
    public String createGroupIfNecessary(String groupName) {
        Optional<String> optionalGroupId = getGroupId(groupName);

        if (optionalGroupId.isPresent()) {
            log.info("Group already exists, not creating a new one");
            return optionalGroupId.get();
        }

        log.info("Group does not exist, creating a new one");
        CreateGroupRequest createGroupRequest = CreateGroupRequest.builder()
                .name(groupName)
                .build();

        CreateGroupResponse createGroupResponse = greengrassClient.createGroup(createGroupRequest);

        return createGroupResponse.id();
    }

    @Override
    public boolean groupExists(String groupName) {
        Optional<String> optionalGroupId = getGroupId(groupName);

        return optionalGroupId.isPresent();
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

        String coreDefinitionId = getCoreDefinitionId(coreDefinitionName);

        if (coreDefinitionId == null) {
            CreateCoreDefinitionRequest createCoreDefinitionRequest = CreateCoreDefinitionRequest.builder()
                    .name(coreDefinitionName)
                    .build();

            CreateCoreDefinitionResponse createCoreDefinitionResponse = greengrassClient.createCoreDefinition(createCoreDefinitionRequest);
            coreDefinitionId = createCoreDefinitionResponse.id();
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

        for (LocalDeviceResource localDeviceResource : functionConf.getLocalDeviceResources()) {
            ResourceAccessPolicy resourceAccessPolicy = ResourceAccessPolicy.builder()
                    .resourceId(localDeviceResource.getName())
                    .permission(localDeviceResource.isReadWrite() ? Permission.RW : Permission.RO)
                    .build();

            resourceAccessPolicies.add(resourceAccessPolicy);
        }

        for (LocalVolumeResource localVolumeResource : functionConf.getLocalVolumeResources()) {
            ResourceAccessPolicy resourceAccessPolicy = ResourceAccessPolicy.builder()
                    .resourceId(localVolumeResource.getName())
                    .permission(localVolumeResource.isReadWrite() ? Permission.RW : Permission.RO)
                    .build();

            resourceAccessPolicies.add(resourceAccessPolicy);
        }

        for (LocalS3Resource localS3Resource : functionConf.getLocalS3Resources()) {
            ResourceAccessPolicy resourceAccessPolicy = ResourceAccessPolicy.builder()
                    .resourceId(localS3Resource.getName())
                    .permission(Permission.RW)
                    .build();

            resourceAccessPolicies.add(resourceAccessPolicy);
        }

        for (LocalSageMakerResource localSageMakerResource : functionConf.getLocalSageMakerResources()) {
            ResourceAccessPolicy resourceAccessPolicy = ResourceAccessPolicy.builder()
                    .resourceId(localSageMakerResource.getName())
                    .permission(Permission.RW)
                    .build();

            resourceAccessPolicies.add(resourceAccessPolicy);
        }

        for (LocalSecretsManagerResource localSecretsManagerResource : functionConf.getLocalSecretsManagerResources()) {
            ResourceAccessPolicy resourceAccessPolicy = ResourceAccessPolicy.builder()
                    .resourceId(localSecretsManagerResource.getResourceName())
                    .permission(Permission.RO)
                    .build();

            resourceAccessPolicies.add(resourceAccessPolicy);
        }

        FunctionConfigurationEnvironment.Builder functionConfigurationEnvironmentBuilder = FunctionConfigurationEnvironment.builder()
                .variables(functionConf.getEnvironmentVariables());

        FunctionConfiguration.Builder functionConfigurationBuilder = FunctionConfiguration.builder()
                .encodingType(functionConf.getEncodingType())
                .pinned(functionConf.isPinned())
                .timeout(functionConf.getTimeoutInSeconds());

        FunctionExecutionConfig.Builder functionExecutionConfigBuilder = FunctionExecutionConfig.builder();

        if (functionConf.isGreengrassContainer()) {
            functionExecutionConfigBuilder = functionExecutionConfigBuilder.isolationMode(FunctionIsolationMode.GREENGRASS_CONTAINER);
            functionConfigurationEnvironmentBuilder.accessSysfs(functionConf.isAccessSysFs())
                    .resourceAccessPolicies(resourceAccessPolicies);

            functionConfigurationBuilder = functionConfigurationBuilder.memorySize(functionConf.getMemorySizeInKb());
        } else {
            functionExecutionConfigBuilder = functionExecutionConfigBuilder.isolationMode(FunctionIsolationMode.NO_CONTAINER)
                    .runAs(FunctionRunAsConfig.builder().uid(functionConf.getUid()).gid(functionConf.getGid()).build());
        }

        functionConfigurationEnvironmentBuilder = functionConfigurationEnvironmentBuilder.execution(functionExecutionConfigBuilder.build());

        functionConfigurationBuilder = functionConfigurationBuilder.environment(functionConfigurationEnvironmentBuilder.build());

        Function function = Function.builder()
                .functionArn(functionArn)
                .id(ioHelper.getUuid())
                .functionConfiguration(functionConfigurationBuilder.build())
                .build();

        return function;
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
        String deviceDefinitionId = getDeviceDefinitionId(deviceDefinitionName);

        if (deviceDefinitionId == null) {
            CreateDeviceDefinitionRequest createDeviceDefinitionRequest = CreateDeviceDefinitionRequest.builder()
                    .name(deviceDefinitionName)
                    .build();

            CreateDeviceDefinitionResponse createDeviceDefinitionResponse = greengrassClient.createDeviceDefinition(createDeviceDefinitionRequest);
            deviceDefinitionId = createDeviceDefinitionResponse.id();
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
        Logger lambdaLogger = Logger.builder()
                .id(ioHelper.getUuid())
                .component(LoggerComponent.LAMBDA)
                .level(LoggerLevel.INFO)
                .type(LoggerType.FILE_SYSTEM)
                .space(DEFAULT_LOGGER_SPACE_IN_KB)
                .build();

        Logger systemLogger = Logger.builder()
                .id(ioHelper.getUuid())
                .component(LoggerComponent.GREENGRASS_SYSTEM)
                .level(LoggerLevel.INFO)
                .type(LoggerType.FILE_SYSTEM)
                .space(DEFAULT_LOGGER_SPACE_IN_KB)
                .build();

        Logger cloudwatchLambdaLogger = Logger.builder()
                .id(ioHelper.getUuid())
                .component(LoggerComponent.LAMBDA)
                .level(LoggerLevel.INFO)
                .type(LoggerType.AWS_CLOUD_WATCH)
                .build();

        Logger cloudwatchSystemLogger = Logger.builder()
                .id(ioHelper.getUuid())
                .component(LoggerComponent.GREENGRASS_SYSTEM)
                .level(LoggerLevel.INFO)
                .type(LoggerType.AWS_CLOUD_WATCH)
                .build();

        LoggerDefinitionVersion loggerDefinitionVersion = LoggerDefinitionVersion.builder()
                .loggers(lambdaLogger,
                        systemLogger,
                        cloudwatchLambdaLogger,
                        cloudwatchSystemLogger)
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
        Optional<GroupInformation> optionalGroupInformation = getGroupInformation(groupId);
        GroupVersion currentGroupVersion = null;
        GroupInformation groupInformation = null;

        CreateGroupVersionRequest createGroupVersionRequest = CreateGroupVersionRequest.builder()
                .groupId(groupId)
                .build();

        if (optionalGroupInformation.isPresent()) {
            groupInformation = optionalGroupInformation.get();

            if (groupInformation.latestVersion() == null) {
                // Group exists but has no versions yet, don't use the group information
                groupInformation = null;
            }
        }

        if (groupInformation == null) {
            log.warn("Group [" + groupId + "] not found or has no previous versions, creating group version from scratch");

            // There is no current version so just use the new version as our reference
            currentGroupVersion = newGroupVersion;
        } else {
            currentGroupVersion = getLatestGroupVersion(groupInformation);
        }

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

        log.info("Checking deployment status...");

        GetDeploymentStatusResponse getDeploymentStatusResponse = greengrassClient.getDeploymentStatus(getDeploymentStatusRequest);
        String deploymentStatus = getDeploymentStatusResponse.deploymentStatus();

        switch (deploymentStatus) {
            case IN_PROGRESS:
            case SUCCESS:
                return DeploymentStatus.SUCCESSFUL;
            case FAILURE:
                String errorMessage = getDeploymentStatusResponse.errorMessage();

                log.error("Greengrass service reported an error [" + errorMessage + "]");

                if (errorMessage.contains("TES service role is not associated with this account")) {
                    // No service role associated with this account
                    log.error("A service role is not associated with this account for Greengrass. See the Greengrass service role documentation for more information [https://docs.aws.amazon.com/greengrass/latest/developerguide/service-role.html]");
                    return DeploymentStatus.FAILED;
                }

                if (errorMessage.contains("GreenGrass is not authorized to assume the Service Role associated with this account")) {
                    // Service role may be missing
                    log.error("The service role associated with this account may be missing. Check that the role returned from the CLI command 'aws greengrass get-service-role-for-account' still exists. See the Greengrass service role documentation for more information [https://docs.aws.amazon.com/greengrass/latest/developerguide/service-role.html]");
                    return DeploymentStatus.FAILED;
                }

                if (errorMessage.contains("Greengrass does not have permission to read the object")) {
                    // Greengrass probably can't read a SageMaker model
                    log.error("If you are using a SageMaker model your Greengrass service role may not have access to the bucket where your model is stored.");
                    return DeploymentStatus.FAILED;
                }

                if (errorMessage.contains("refers to a resource transfer-learning-example with nonexistent S3 object")) {
                    log.error("If you are using a SageMaker model your model appears to no longer exist in S3.");
                    return DeploymentStatus.FAILED;
                }

                if (errorMessage.contains("group config is invalid")) {
                    // Can't succeed if the definition is not valid
                    return DeploymentStatus.FAILED;
                }

                if (errorMessage.contains("We cannot deploy because the group definition is invalid or corrupted")) {
                    // Can't succeed if the definition is not valid
                    return DeploymentStatus.FAILED;
                }

                if (errorMessage.contains("Artifact download retry exceeded the max retries")) {
                    // Can't succeed if the artifact can't be downloaded
                    return DeploymentStatus.FAILED;
                }

                if (errorMessage.contains("Greengrass is not configured to run lambdas with root permissions")) {
                    return DeploymentStatus.FAILED;
                }

                if (errorMessage.contains("user or group doesn't have permission on the file")) {
                    return DeploymentStatus.FAILED;
                }

                if (errorMessage.contains("file doesn't exist")) {
                    return DeploymentStatus.FAILED;
                }

                if (errorMessage.contains("configuration parameter") && (errorMessage.contains("does not match required pattern"))) {
                    log.error("One or more configuration parameters were specified that did not match the allowed patterns. Adjust the values and try again.");
                    return DeploymentStatus.FAILED;
                }

                // Possible error messages we've encountered
                //   "The security token included in the request is invalid."
                //   "We're having a problem right now.  Please try again in a few minutes."

                return DeploymentStatus.NEEDS_NEW_DEPLOYMENT;
            case BUILDING:
                log.info("Deployment is being built...");
                return DeploymentStatus.BUILDING;
        }

        log.error("Unexpected deployment status [" + deploymentStatus + "]");

        return DeploymentStatus.FAILED;
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
        List<LocalSecretsManagerResource> localSecretsManagerResources = greengrassResourceHelper.flatMapResources(functionsInGreengrassContainer, FunctionConf::getLocalSecretsManagerResources);

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
    public Device getDevice(String thingName) {
        Try.of(() -> iotHelper.getThingArn(thingName))
                .recover(ResourceNotFoundException.class, throwable -> rethrowResourceNotFoundException(thingName))
                .get();

        String certificateArn = iotHelper.getThingPrincipal(thingName);

        if (certificateArn == null) {
            throw new RuntimeException("Thing [" + thingName + "] does not have a certificate attached");
        }

        return Device.builder()
                .certificateArn(certificateArn)
                .id(ioHelper.getUuid())
                .syncShadow(true)
                .thingArn(iotHelper.getThingArn(thingName))
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
    public Optional<GroupVersion> getLatestGroupVersion(String groupId) {
        Optional<GroupInformation> optionalGroupInformation = getGroupInformation(groupId);

        if (!optionalGroupInformation.isPresent()) {
            return Optional.empty();
        }

        GroupInformation groupInformation = optionalGroupInformation.get();

        if (groupInformation.latestVersion() == null) {
            // Can not get the latest version if the latest version field is NULL
            return Optional.empty();
        }

        return Optional.of(getLatestGroupVersion(groupInformation));
    }

    @Override
    public Optional<String> getCoreCertificateArn(String groupId) {
        Optional<GroupVersion> optionalGroupVersion = getLatestGroupVersion(groupId);

        if (!optionalGroupVersion.isPresent()) {
            return Optional.empty();
        }

        GroupVersion groupVersion = optionalGroupVersion.get();

        String coreDefinitionVersionArn = groupVersion.coreDefinitionVersionArn();
        String coreDefinitionVersionId = idExtractor.extractVersionId(coreDefinitionVersionArn);
        String coreDefinitionId = idExtractor.extractId(coreDefinitionVersionArn);

        GetCoreDefinitionVersionRequest getCoreDefinitionVersionRequest = GetCoreDefinitionVersionRequest.builder()
                .coreDefinitionVersionId(coreDefinitionVersionId)
                .coreDefinitionId(coreDefinitionId)
                .build();

        GetCoreDefinitionVersionResponse coreDefinitionVersionResponse = greengrassClient.getCoreDefinitionVersion(getCoreDefinitionVersionRequest);

        return Optional.ofNullable(coreDefinitionVersionResponse.definition())
                .map(CoreDefinitionVersion::cores)
                .filter(list -> list.size() != 0)
                .map(list -> list.get(0))
                .map(Core::certificateArn);
    }

    @Override
    public GroupVersion getLatestGroupVersion(GroupInformation groupInformation) {
        GetGroupVersionResponse groupVersionResponse = getGroupVersionResponse(groupInformation);

        return groupVersionResponse.definition();
    }

    @Override
    public String getGroupId(GroupInformation groupInformation) {
        GetGroupVersionResponse groupVersionResponse = getGroupVersionResponse(groupInformation);

        return groupVersionResponse.id();
    }

    private GetGroupVersionResponse getGroupVersionResponse(GroupInformation groupInformation) {
        GetGroupVersionRequest getGroupVersionRequest = GetGroupVersionRequest.builder()
                .groupId(groupInformation.id())
                .groupVersionId(groupInformation.latestVersion())
                .build();

        return greengrassClient.getGroupVersion(getGroupVersionRequest);
    }

    @Override
    public List<Function> getFunctions(GroupInformation groupInformation) {
        FunctionDefinitionVersion functionDefinition = getFunctionDefinitionVersion(groupInformation);

        // The returned list is an unmodifiable list, copy it to an array list so callers can modify it
        List<Function> functions = new ArrayList<>(functionDefinition.functions());

        return functions;
    }

    @Override
    public FunctionIsolationMode getDefaultIsolationMode(GroupInformation groupInformation) {
        FunctionDefinitionVersion functionDefinition = getFunctionDefinitionVersion(groupInformation);

        Optional<FunctionIsolationMode> optionalFunctionIsolationMode = Optional.ofNullable(functionDefinition.defaultConfig())
                .map(FunctionDefaultConfig::execution)
                .map(FunctionDefaultExecutionConfig::isolationMode);

        if (!optionalFunctionIsolationMode.isPresent()) {
            log.warn("Default function isolation mode was not present, defaulting to Greengrass container");
            return FunctionIsolationMode.GREENGRASS_CONTAINER;
        }

        return optionalFunctionIsolationMode.get();
    }

    @Override
    public FunctionDefinitionVersion getFunctionDefinitionVersion(GroupInformation groupInformation) {
        GroupVersion groupVersion = getLatestGroupVersion(groupInformation);

        String functionDefinitionVersionArn = groupVersion.functionDefinitionVersionArn();

        GetFunctionDefinitionVersionRequest getFunctionDefinitionVersionRequest = GetFunctionDefinitionVersionRequest.builder()
                .functionDefinitionId(idExtractor.extractId(functionDefinitionVersionArn))
                .functionDefinitionVersionId(idExtractor.extractVersionId(functionDefinitionVersionArn))
                .build();

        GetFunctionDefinitionVersionResponse getFunctionDefinitionVersionResponse = greengrassClient.getFunctionDefinitionVersion(getFunctionDefinitionVersionRequest);

        return getFunctionDefinitionVersionResponse.definition();
    }

    @Override
    public List<Device> getDevices(GroupInformation groupInformation) {
        GroupVersion groupVersion = getLatestGroupVersion(groupInformation);

        String deviceDefinitionVersionArn = groupVersion.deviceDefinitionVersionArn();

        GetDeviceDefinitionVersionRequest getDeviceDefinitionVersionRequest = GetDeviceDefinitionVersionRequest.builder()
                .deviceDefinitionId(idExtractor.extractId(deviceDefinitionVersionArn))
                .deviceDefinitionVersionId(idExtractor.extractVersionId(deviceDefinitionVersionArn))
                .build();

        GetDeviceDefinitionVersionResponse getDeviceDefinitionVersionResponse = greengrassClient.getDeviceDefinitionVersion(getDeviceDefinitionVersionRequest);

        DeviceDefinitionVersion deviceDefinition = getDeviceDefinitionVersionResponse.definition();

        // The returned list is an unmodifiable list, copy it to an array list so callers can modify it
        List<Device> devices = new ArrayList<>(deviceDefinition.devices());

        return devices;
    }

    @Override
    public List<Subscription> getSubscriptions(GroupInformation groupInformation) {
        GroupVersion groupVersion = getLatestGroupVersion(groupInformation);

        String subscriptionDefinitionVersionArn = groupVersion.subscriptionDefinitionVersionArn();

        GetSubscriptionDefinitionVersionRequest getSubscriptionDefinitionVersionRequest = GetSubscriptionDefinitionVersionRequest.builder()
                .subscriptionDefinitionId(idExtractor.extractId(subscriptionDefinitionVersionArn))
                .subscriptionDefinitionVersionId(idExtractor.extractVersionId(subscriptionDefinitionVersionArn))
                .build();

        GetSubscriptionDefinitionVersionResponse getSubscriptionDefinitionVersionResponse = greengrassClient.getSubscriptionDefinitionVersion(getSubscriptionDefinitionVersionRequest);

        SubscriptionDefinitionVersion subscriptionDefinition = getSubscriptionDefinitionVersionResponse.definition();

        // The returned list is an unmodifiable list, copy it to an array list so callers can modify it
        List<Subscription> subscriptions = new ArrayList<>(subscriptionDefinition.subscriptions());

        return subscriptions;
    }

    @Override
    public GetGroupCertificateAuthorityResponse getGroupCa(GroupInformation groupInformation) {
        ListGroupCertificateAuthoritiesRequest listGroupCertificateAuthoritiesRequest = ListGroupCertificateAuthoritiesRequest.builder()
                .groupId(groupInformation.id())
                .build();

        ListGroupCertificateAuthoritiesResponse listGroupCertificateAuthoritiesResponse = greengrassClient.listGroupCertificateAuthorities(listGroupCertificateAuthoritiesRequest);

        if (listGroupCertificateAuthoritiesResponse.groupCertificateAuthorities().size() != 1) {
            log.error("Currently we do not support multiple group CAs");
            return null;
        }

        GetGroupCertificateAuthorityRequest getGroupCertificateAuthorityRequest = GetGroupCertificateAuthorityRequest.builder()
                .groupId(groupInformation.id())
                .certificateAuthorityId(listGroupCertificateAuthoritiesResponse.groupCertificateAuthorities().get(0).groupCertificateAuthorityId())
                .build();

        GetGroupCertificateAuthorityResponse getGroupCertificateAuthorityResponse = greengrassClient.getGroupCertificateAuthority(getGroupCertificateAuthorityRequest);

        return getGroupCertificateAuthorityResponse;
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
}
