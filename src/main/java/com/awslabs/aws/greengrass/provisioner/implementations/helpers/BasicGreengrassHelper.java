package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.DeploymentStatus;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.resources.LocalDeviceResource;
import com.awslabs.aws.greengrass.provisioner.data.resources.LocalS3Resource;
import com.awslabs.aws.greengrass.provisioner.data.resources.LocalSageMakerResource;
import com.awslabs.aws.greengrass.provisioner.data.resources.LocalVolumeResource;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.greengrass.GreengrassClient;
import software.amazon.awssdk.services.greengrass.model.*;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iot.model.ResourceNotFoundException;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class BasicGreengrassHelper implements GreengrassHelper {
    public static final String DEFAULT = "Default";
    public static final int DEFAULT_LOGGER_SPACE_IN_KB = 128 * 1024;
    public static final String FAILURE = "Failure";
    public static final String IN_PROGRESS = "InProgress";
    public static final String SUCCESS = "Success";
    public static final String BUILDING = "Building";
    @Inject
    GreengrassClient greengrassClient;
    @Inject
    IoHelper ioHelper;
    @Inject
    IotHelper iotHelper;
    @Inject
    GGConstants ggConstants;
    @Inject
    IdExtractor idExtractor;

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
    public String createCoreDefinitionAndVersion(String coreDefinitionName, String coreCertificateArn, String coreThingArn) {
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
                .syncShadow(false)
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
    public String createFunctionDefinitionVersion(Set<Function> functions) {
        functions = functions.stream()
                .filter(function -> !function.functionArn().equals(ggConstants.getGgIpDetectorArn()))
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

        if (FunctionHelper.getDefaultFunctionIsolationMode().equals(FunctionIsolationMode.NO_CONTAINER)) {
            log.warn("Isolation mode set to NoContainer in function defaults file, setting default isolation mode for the group to NoContainer");

            functionDefinitionVersionBuilder.defaultConfig(
                    FunctionDefaultConfig.builder()
                            .execution(FunctionDefaultExecutionConfig.builder()
                                    .isolationMode(FunctionIsolationMode.NO_CONTAINER)
                                    .build())
                            .build());

            // Scrub all functions without an isolation mode specified
            functionsToScrubBuilder.addAll(functionsWithoutIsolationModeSpecified);
        }

        // Get the list of functions we need to scrub
        Set<Function> functionsToScrub = functionsToScrubBuilder.build();

        // Get the list of functions we don't need to scrub
        Set<Function> nonScrubbedFunctions = allFunctions.stream()
                .filter(function -> !functionsToScrub.contains(function))
                .collect(Collectors.toSet());

        // Scrub the necessary functions
        Set<Function> scrubbedFunctions = functionsToScrubBuilder.build().stream()
                .map(scrubFunctionForNoContainer())
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

    private java.util.function.Function<Function, Function> scrubFunctionForNoContainer() {
        return function -> {
            Function.Builder functionBuilder = function.toBuilder();
            FunctionConfiguration.Builder functionConfigurationBuilder = function.functionConfiguration().toBuilder();
            functionConfigurationBuilder.memorySize(null);
            functionBuilder.functionConfiguration(functionConfigurationBuilder.build());

            log.warn("Scrubbing memory size from function [" + function.functionArn() + "] since it is running without Greengrass container isolation");

            return functionBuilder.build();
        };
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
            GetGroupVersionResponse latestGroupVersion = getLatestGroupVersion(groupInformation);

            currentGroupVersion = latestGroupVersion.definition();
        }

        // When an ARN in the new version is NULL we take it from the current version.  This allows us to do updates more easily.
        createGroupVersionRequest = mergeCurrentAndNewVersion(newGroupVersion, currentGroupVersion, createGroupVersionRequest.toBuilder());

        CreateGroupVersionResponse createGroupVersionResponse = greengrassClient.createGroupVersion(createGroupVersionRequest);

        return createGroupVersionResponse.version();
    }

    private CreateGroupVersionRequest mergeCurrentAndNewVersion(GroupVersion newGroupVersion, GroupVersion currentGroupVersion, CreateGroupVersionRequest.Builder createGroupVersionRequestBuilder) {
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

        if (deploymentStatus.equals(IN_PROGRESS) || deploymentStatus.equals(SUCCESS)) {
            return DeploymentStatus.SUCCESSFUL;
        } else if (deploymentStatus.equals(FAILURE)) {
            String errorMessage = getDeploymentStatusResponse.errorMessage();

            log.error("Greengrass service reported an error [" + errorMessage + "]");

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

            // Possible error messages we've encountered
            //   "The security token included in the request is invalid."
            //   "We're having a problem right now.  Please try again in a few minutes."

            return DeploymentStatus.NEEDS_NEW_DEPLOYMENT;
        } else if (deploymentStatus.equals(BUILDING)) {
            log.info("Deployment is being built...");
            return DeploymentStatus.BUILDING;
        }

        log.error("Unexpected deployment status [" + deploymentStatus + "]");

        return DeploymentStatus.FAILED;
    }

    @Override
    public String createResourceDefinitionVersion(List<FunctionConf> functionConfs) {
        List<Resource> resources = new ArrayList<>();

        for (FunctionConf functionConf : functionConfs) {
            for (LocalDeviceResource localDeviceResource : functionConf.getLocalDeviceResources()) {
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

                resources.add(createResource(resourceDataContainer, localDeviceResource.getName(), localDeviceResource.getName()));
            }

            for (LocalVolumeResource localVolumeResource : functionConf.getLocalVolumeResources()) {
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

                resources.add(createResource(resourceDataContainer, localVolumeResource.getName(), localVolumeResource.getName()));
            }

            for (LocalS3Resource localS3Resource : functionConf.getLocalS3Resources()) {
                S3MachineLearningModelResourceData s3MachineLearningModelResourceData = S3MachineLearningModelResourceData.builder()
                        .s3Uri(localS3Resource.getUri())
                        .destinationPath(localS3Resource.getPath())
                        .build();

                ResourceDataContainer resourceDataContainer = ResourceDataContainer.builder()
                        .s3MachineLearningModelResourceData(s3MachineLearningModelResourceData)
                        .build();

                resources.add(createResource(resourceDataContainer, localS3Resource.getName(), localS3Resource.getName()));
            }

            for (LocalSageMakerResource localSageMakerResource : functionConf.getLocalSageMakerResources()) {
                SageMakerMachineLearningModelResourceData sageMakerMachineLearningModelResourceData = SageMakerMachineLearningModelResourceData.builder()
                        .sageMakerJobArn(localSageMakerResource.getArn())
                        .destinationPath(localSageMakerResource.getPath())
                        .build();

                ResourceDataContainer resourceDataContainer = ResourceDataContainer.builder()
                        .sageMakerMachineLearningModelResourceData(sageMakerMachineLearningModelResourceData)
                        .build();

                resources.add(createResource(resourceDataContainer, localSageMakerResource.getName(), localSageMakerResource.getName()));
            }
        }

        ResourceDefinitionVersion resourceDefinitionVersion = ResourceDefinitionVersion.builder()
                .resources(resources)
                .build();

        validateResourceDefinitionVersion(resourceDefinitionVersion);

        CreateResourceDefinitionRequest createResourceDefinitionRequest = CreateResourceDefinitionRequest.builder()
                .initialVersion(resourceDefinitionVersion)
                .name(ioHelper.getUuid())
                .build();

        CreateResourceDefinitionResponse createResourceDefinitionResponse = greengrassClient.createResourceDefinition(createResourceDefinitionRequest);

        return createResourceDefinitionResponse.latestVersionArn();
    }

    private void validateResourceDefinitionVersion(ResourceDefinitionVersion resourceDefinitionVersion) {
        List<Resource> resource = resourceDefinitionVersion.resources();

        List<LocalDeviceResourceData> localDeviceResources = resource.stream()
                .map(res -> res.resourceDataContainer().localDeviceResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateLocalDeviceSourcePaths(localDeviceResources);

        List<LocalVolumeResourceData> localVolumeResources = resource.stream()
                .map(res -> res.resourceDataContainer().localVolumeResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateLocalVolumeSourcePaths(localVolumeResources);

        List<S3MachineLearningModelResourceData> localS3Resources = resource.stream()
                .map(res -> res.resourceDataContainer().s3MachineLearningModelResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateS3DestinationPaths(localS3Resources);

        List<SageMakerMachineLearningModelResourceData> localSageMakerResources = resource.stream()
                .map(res -> res.resourceDataContainer().sageMakerMachineLearningModelResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateSageMakerDestinationPaths(localSageMakerResources);
    }

    private void disallowDuplicateLocalDeviceSourcePaths(List<LocalDeviceResourceData> resources) {
        List<String> sourcePaths = resources.stream()
                .map(res -> res.sourcePath())
                .collect(Collectors.toList());

        Optional<List<String>> duplicates = findDuplicates(sourcePaths);

        if (duplicates.isPresent()) {
            String duplicatesString = duplicates.get().stream()
                    .map(string -> "\"" + string + "\"")
                    .collect(Collectors.joining(", "));

            log.error("Duplicate local device resource source paths defined [" + duplicatesString + "].  Greengrass will not accept this configuration.");
            throw new UnsupportedOperationException("Invalid resource configuration");
        }
    }

    private void disallowDuplicateLocalVolumeSourcePaths(List<LocalVolumeResourceData> resources) {
        List<String> sourcePaths = resources.stream()
                .map(res -> res.sourcePath())
                .collect(Collectors.toList());

        Optional<List<String>> duplicates = findDuplicates(sourcePaths);

        if (duplicates.isPresent()) {
            String duplicatesString = duplicates.get().stream()
                    .map(string -> "\"" + string + "\"")
                    .collect(Collectors.joining(", "));

            log.error("Duplicate local volume resource source paths defined [" + duplicatesString + "].  Greengrass will not accept this configuration.");
            throw new UnsupportedOperationException("Invalid resource configuration");
        }
    }

    private void disallowDuplicateSageMakerDestinationPaths(List<SageMakerMachineLearningModelResourceData> resources) {
        List<String> sourcePaths = resources.stream()
                .map(res -> res.destinationPath())
                .collect(Collectors.toList());

        Optional<List<String>> duplicates = findDuplicates(sourcePaths);

        if (duplicates.isPresent()) {
            String duplicatesString = duplicates.get().stream()
                    .map(string -> "\"" + string + "\"")
                    .collect(Collectors.joining(", "));

            log.error("Duplicate local SageMaker resource destination paths defined [" + duplicatesString + "].  Greengrass will not accept this configuration.");
            throw new UnsupportedOperationException("Invalid resource configuration");
        }
    }

    private void disallowDuplicateS3DestinationPaths(List<S3MachineLearningModelResourceData> resources) {
        List<String> sourcePaths = resources.stream()
                .map(res -> res.destinationPath())
                .collect(Collectors.toList());

        Optional<List<String>> duplicates = findDuplicates(sourcePaths);

        if (duplicates.isPresent()) {
            String duplicatesString = duplicates.get().stream()
                    .map(string -> "\"" + string + "\"")
                    .collect(Collectors.joining(", "));

            log.error("Duplicate local S3 resource destination paths defined [" + duplicatesString + "].  Greengrass will not accept this configuration.");
            throw new UnsupportedOperationException("Invalid resource configuration");
        }
    }

    private Optional<List<String>> findDuplicates(List<String> inputList) {
        List<String> outputList = new ArrayList<>(inputList);
        Set<String> deduplicatedList = new HashSet<>(inputList);

        if (outputList.size() != deduplicatedList.size()) {
            deduplicatedList.stream()
                    .forEach(outputList::remove);

            return Optional.ofNullable(outputList);
        }

        return Optional.empty();
    }

    private Resource createResource(ResourceDataContainer resourceDataContainer, String name, String id) {
        Resource resource = Resource.builder()
                .resourceDataContainer(resourceDataContainer)
                .name(name)
                .id(id)
                .build();

        return resource;
    }

    @Override
    public Device getDevice(String thingName) {
        try {
            iotHelper.getThingArn(thingName);
        } catch (ResourceNotFoundException e) {
            throw new UnsupportedOperationException("Thing [" + thingName + "] does not exist");
        }

        String certificateArn = iotHelper.getThingPrincipal(thingName);

        if (certificateArn == null) {
            throw new UnsupportedOperationException("Thing [" + thingName + "] does not have a certificate attached");
        }

        return Device.builder()
                .certificateArn(certificateArn)
                .id(ioHelper.getUuid())
                .syncShadow(true)
                .thingArn(iotHelper.getThingArn(thingName))
                .build();
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
    public GetGroupVersionResponse getLatestGroupVersion(GroupInformation groupInformation) {
        GetGroupVersionRequest getGroupVersionRequest = GetGroupVersionRequest.builder()
                .groupId(groupInformation.id())
                .groupVersionId(groupInformation.latestVersion())
                .build();

        GetGroupVersionResponse groupVersionResponse = greengrassClient.getGroupVersion(getGroupVersionRequest);

        return groupVersionResponse;
    }

    private GroupVersion getGroupVersion(GroupInformation groupInformation) {
        GetGroupVersionResponse latestGroupVersion = getLatestGroupVersion(groupInformation);

        GroupVersion groupVersion = latestGroupVersion.definition();

        return groupVersion;
    }

    @Override
    public List<Function> getFunctions(GroupInformation groupInformation) {
        GroupVersion groupVersion = getGroupVersion(groupInformation);

        String functionDefinitionVersionArn = groupVersion.functionDefinitionVersionArn();

        GetFunctionDefinitionVersionRequest getFunctionDefinitionVersionRequest = GetFunctionDefinitionVersionRequest.builder()
                .functionDefinitionId(idExtractor.extractId(functionDefinitionVersionArn))
                .functionDefinitionVersionId(idExtractor.extractVersionId(functionDefinitionVersionArn))
                .build();

        GetFunctionDefinitionVersionResponse getFunctionDefinitionVersionResponse = greengrassClient.getFunctionDefinitionVersion(getFunctionDefinitionVersionRequest);

        FunctionDefinitionVersion functionDefinition = getFunctionDefinitionVersionResponse.definition();
        List<Function> functions = functionDefinition.functions();

        return functions;
    }

    @Override
    public List<Device> getDevices(GroupInformation groupInformation) {
        GroupVersion groupVersion = getGroupVersion(groupInformation);

        String deviceDefinitionVersionArn = groupVersion.deviceDefinitionVersionArn();

        GetDeviceDefinitionVersionRequest getDeviceDefinitionVersionRequest = GetDeviceDefinitionVersionRequest.builder()
                .deviceDefinitionId(idExtractor.extractId(deviceDefinitionVersionArn))
                .deviceDefinitionVersionId(idExtractor.extractVersionId(deviceDefinitionVersionArn))
                .build();

        GetDeviceDefinitionVersionResponse getDeviceDefinitionVersionResponse = greengrassClient.getDeviceDefinitionVersion(getDeviceDefinitionVersionRequest);

        DeviceDefinitionVersion deviceDefinition = getDeviceDefinitionVersionResponse.definition();
        List<Device> devices = deviceDefinition.devices();

        return devices;
    }

    @Override
    public List<Subscription> getSubscriptions(GroupInformation groupInformation) {
        GroupVersion groupVersion = getGroupVersion(groupInformation);

        String subscriptionDefinitionVersionArn = groupVersion.subscriptionDefinitionVersionArn();

        GetSubscriptionDefinitionVersionRequest getSubscriptionDefinitionVersionRequest = GetSubscriptionDefinitionVersionRequest.builder()
                .subscriptionDefinitionId(idExtractor.extractId(subscriptionDefinitionVersionArn))
                .subscriptionDefinitionVersionId(idExtractor.extractVersionId(subscriptionDefinitionVersionArn))
                .build();

        GetSubscriptionDefinitionVersionResponse getSubscriptionDefinitionVersionResponse = greengrassClient.getSubscriptionDefinitionVersion(getSubscriptionDefinitionVersionRequest);

        SubscriptionDefinitionVersion subscriptionDefinition = getSubscriptionDefinitionVersionResponse.definition();
        List<Subscription> subscriptions = subscriptionDefinition.subscriptions();

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
}
