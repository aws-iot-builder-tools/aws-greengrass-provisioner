package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.amazonaws.services.greengrass.AWSGreengrassClient;
import com.amazonaws.services.greengrass.model.*;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.iot.model.ResourceNotFoundException;
import com.awslabs.aws.greengrass.provisioner.data.DeploymentStatus;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.resources.LocalDeviceResource;
import com.awslabs.aws.greengrass.provisioner.data.resources.LocalS3Resource;
import com.awslabs.aws.greengrass.provisioner.data.resources.LocalSageMakerResource;
import com.awslabs.aws.greengrass.provisioner.data.resources.LocalVolumeResource;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;

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
    AWSGreengrassClient awsGreengrassClient;
    @Inject
    IoHelper ioHelper;
    @Inject
    IotHelper iotHelper;
    @Inject
    GGConstants ggConstants;
    @Inject
    IdExtractor idExtractor;
    @Inject
    JsonHelper jsonHelper;

    @Inject
    public BasicGreengrassHelper() {
    }

    @Override
    public void associateServiceRoleToAccount(Role role) {
        AssociateServiceRoleToAccountRequest associateServiceRoleToAccountRequest = new AssociateServiceRoleToAccountRequest()
                .withRoleArn(role.getArn());

        awsGreengrassClient.associateServiceRoleToAccount(associateServiceRoleToAccountRequest);
    }

    @Override
    public Optional<GroupInformation> getGroupInformation(String groupNameOrGroupId) {
        ListGroupsRequest listGroupsRequest = new ListGroupsRequest();

        ListGroupsResult listGroupsResult;

        do {
            listGroupsResult = awsGreengrassClient.listGroups(listGroupsRequest);

            for (GroupInformation groupInformation : listGroupsResult.getGroups()) {
                if (groupInformation.getName().equals(groupNameOrGroupId)) {
                    return Optional.ofNullable(groupInformation);
                }

                if (groupInformation.getId().equals(groupNameOrGroupId)) {
                    return Optional.ofNullable(groupInformation);
                }
            }

            listGroupsRequest.setNextToken(listGroupsResult.getNextToken());
        } while (listGroupsResult.getNextToken() != null);

        log.warn("No group was found with name or ID [" + groupNameOrGroupId + "]");
        return Optional.empty();
    }

    private Optional<String> getGroupId(String groupName) {
        Optional<GroupInformation> optionalGroupInformation = getGroupInformation(groupName);

        return optionalGroupInformation.map(GroupInformation::getId);
    }

    private String getCoreDefinitionId(String coreDefinitionName) {
        ListCoreDefinitionsRequest listCoreDefinitionsRequest = new ListCoreDefinitionsRequest();

        ListCoreDefinitionsResult listCoreDefinitionsResult;

        do {
            listCoreDefinitionsResult = awsGreengrassClient.listCoreDefinitions(listCoreDefinitionsRequest);

            for (DefinitionInformation definitionInformation : listCoreDefinitionsResult.getDefinitions()) {
                if (definitionInformation.getName() == null) {
                    continue;
                }

                if (definitionInformation.getName().equals(coreDefinitionName)) {
                    return definitionInformation.getId();
                }
            }

            listCoreDefinitionsRequest.setNextToken(listCoreDefinitionsResult.getNextToken());
        } while (listCoreDefinitionsResult.getNextToken() != null);

        return null;
    }

    private String getDeviceDefinitionId(String deviceDefinitionName) {
        ListDeviceDefinitionsRequest listDeviceDefinitionsRequest = new ListDeviceDefinitionsRequest();

        ListDeviceDefinitionsResult listDeviceDefinitionsResult;

        do {
            listDeviceDefinitionsResult = awsGreengrassClient.listDeviceDefinitions(listDeviceDefinitionsRequest);

            for (DefinitionInformation definitionInformation : listDeviceDefinitionsResult.getDefinitions()) {
                if (definitionInformation.getName() == null) {
                    continue;
                }

                if (definitionInformation.getName().equals(deviceDefinitionName)) {
                    return definitionInformation.getId();
                }
            }

            listDeviceDefinitionsRequest.setNextToken(listDeviceDefinitionsResult.getNextToken());
        } while (listDeviceDefinitionsResult.getNextToken() != null);

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
        CreateGroupRequest createGroupRequest = new CreateGroupRequest()
                .withName(groupName);

        CreateGroupResult createGroupResult = awsGreengrassClient.createGroup(createGroupRequest);

        return createGroupResult.getId();
    }

    @Override
    public void associateRoleToGroup(String groupId, Role greengrassRole) {
        AssociateRoleToGroupRequest associateRoleToGroupRequest = new AssociateRoleToGroupRequest()
                .withGroupId(groupId)
                .withRoleArn(greengrassRole.getArn());

        awsGreengrassClient.associateRoleToGroup(associateRoleToGroupRequest);
    }

    @Override
    public String createCoreDefinitionAndVersion(String coreDefinitionName, String coreCertificateArn, String coreThingArn) {
        String uuid = ioHelper.getUuid();

        String coreDefinitionId = getCoreDefinitionId(coreDefinitionName);

        if (coreDefinitionId == null) {
            CreateCoreDefinitionRequest createCoreDefinitionRequest = new CreateCoreDefinitionRequest()
                    .withName(coreDefinitionName);

            CreateCoreDefinitionResult createCoreDefinitionResult = awsGreengrassClient.createCoreDefinition(createCoreDefinitionRequest);
            coreDefinitionId = createCoreDefinitionResult.getId();
        }

        Core core = new Core()
                .withCertificateArn(coreCertificateArn)
                .withId(uuid)
                .withSyncShadow(false)
                .withThingArn(coreThingArn);

        CreateCoreDefinitionVersionRequest createCoreDefinitionVersionRequest = new CreateCoreDefinitionVersionRequest()
                .withCoreDefinitionId(coreDefinitionId)
                .withCores(core);

        CreateCoreDefinitionVersionResult createCoreDefinitionVersionResult = awsGreengrassClient.createCoreDefinitionVersion(createCoreDefinitionVersionRequest);
        return createCoreDefinitionVersionResult.getArn();
    }

    @Override
    public Function buildFunctionModel(String functionArn, FunctionConf functionConf) {
        List<ResourceAccessPolicy> resourceAccessPolicies = new ArrayList<>();

        for (LocalDeviceResource localDeviceResource : functionConf.getLocalDeviceResources()) {
            ResourceAccessPolicy resourceAccessPolicy = new ResourceAccessPolicy()
                    .withResourceId(localDeviceResource.getName())
                    .withPermission(localDeviceResource.isReadWrite() ? Permission.Rw : Permission.Ro);

            resourceAccessPolicies.add(resourceAccessPolicy);
        }

        for (LocalVolumeResource localVolumeResource : functionConf.getLocalVolumeResources()) {
            ResourceAccessPolicy resourceAccessPolicy = new ResourceAccessPolicy()
                    .withResourceId(localVolumeResource.getName())
                    .withPermission(localVolumeResource.isReadWrite() ? Permission.Rw : Permission.Ro);

            resourceAccessPolicies.add(resourceAccessPolicy);
        }

        for (LocalS3Resource localS3Resource : functionConf.getLocalS3Resources()) {
            ResourceAccessPolicy resourceAccessPolicy = new ResourceAccessPolicy()
                    .withResourceId(localS3Resource.getName())
                    .withPermission(Permission.Rw);

            resourceAccessPolicies.add(resourceAccessPolicy);
        }

        for (LocalSageMakerResource localSageMakerResource : functionConf.getLocalSageMakerResources()) {
            ResourceAccessPolicy resourceAccessPolicy = new ResourceAccessPolicy()
                    .withResourceId(localSageMakerResource.getName())
                    .withPermission(Permission.Rw);

            resourceAccessPolicies.add(resourceAccessPolicy);
        }

        FunctionConfigurationEnvironment functionConfigurationEnvironment = new FunctionConfigurationEnvironment()
                .withAccessSysfs(functionConf.isAccessSysFs())
                .withResourceAccessPolicies(resourceAccessPolicies)
                .withVariables(functionConf.getEnvironmentVariables());

        FunctionConfiguration functionConfiguration = new FunctionConfiguration()
                .withEncodingType(functionConf.getEncodingType())
                .withMemorySize(functionConf.getMemorySizeInKb())
                .withPinned(functionConf.isPinned())
                .withTimeout(functionConf.getTimeoutInSeconds())
                .withEnvironment(functionConfigurationEnvironment);

        Function function = new Function()
                .withFunctionArn(functionArn)
                .withId(ioHelper.getUuid())
                .withFunctionConfiguration(functionConfiguration);

        return function;
    }

    @Override
    public Function buildFunctionModel(String functionArn,
                                       com.amazonaws.services.lambda.model.FunctionConfiguration lambdaFunctionConfiguration,
                                       Map<String, String> defaultEnvironment,
                                       EncodingType encodingType,
                                       boolean pinned) {
        FunctionConfigurationEnvironment functionConfigurationEnvironment = new FunctionConfigurationEnvironment()
                .withAccessSysfs(true)
                .withVariables(defaultEnvironment);

        FunctionConfiguration functionConfiguration = new FunctionConfiguration()
                .withEncodingType(encodingType)
                .withMemorySize(lambdaFunctionConfiguration.getMemorySize() * 1024 * 1024)
                .withPinned(pinned)
                .withTimeout(lambdaFunctionConfiguration.getTimeout())
                .withEnvironment(functionConfigurationEnvironment);

        Function function = new Function()
                .withFunctionArn(functionArn)
                .withId(ioHelper.getUuid())
                .withFunctionConfiguration(functionConfiguration);

        return function;
    }

    @Override
    public String createFunctionDefinitionVersion(Set<Function> functions) {
        functions = functions.stream()
                .filter(function -> !function.getFunctionArn().equals(ggConstants.getGgIpDetectorArn()))
                .collect(Collectors.toSet());

        ImmutableSet<Function> allFunctions = ImmutableSet.<Function>builder()
                .addAll(functions)
                .add(ggConstants.getGgIpDetectorFunction())
                .build();

        FunctionDefinitionVersion functionDefinitionVersion = new FunctionDefinitionVersion()
                .withFunctions(allFunctions);

        CreateFunctionDefinitionRequest createFunctionDefinitionRequest = new CreateFunctionDefinitionRequest()
                .withName(DEFAULT)
                .withInitialVersion(functionDefinitionVersion);

        CreateFunctionDefinitionResult createFunctionDefinitionResult = awsGreengrassClient.createFunctionDefinition(createFunctionDefinitionRequest);
        return createFunctionDefinitionResult.getLatestVersionArn();
    }

    @Override
    public String createDeviceDefinitionAndVersion(String deviceDefinitionName, List<Device> devices) {
        String deviceDefinitionId = getDeviceDefinitionId(deviceDefinitionName);

        if (deviceDefinitionId == null) {
            CreateDeviceDefinitionRequest createDeviceDefinitionRequest = new CreateDeviceDefinitionRequest()
                    .withName(deviceDefinitionName);

            CreateDeviceDefinitionResult createDeviceDefinitionResult = awsGreengrassClient.createDeviceDefinition(createDeviceDefinitionRequest);
            deviceDefinitionId = createDeviceDefinitionResult.getId();
        }

        CreateDeviceDefinitionVersionRequest createDeviceDefinitionVersionRequest = new CreateDeviceDefinitionVersionRequest()
                .withDeviceDefinitionId(deviceDefinitionId)
                .withDevices(devices);

        CreateDeviceDefinitionVersionResult createDeviceDefinitionVersionResult = awsGreengrassClient.createDeviceDefinitionVersion(createDeviceDefinitionVersionRequest);
        return createDeviceDefinitionVersionResult.getArn();
    }

    @Override
    public String createSubscriptionDefinitionAndVersion(List<Subscription> subscriptions) {
        SubscriptionDefinitionVersion subscriptionDefinitionVersion = new SubscriptionDefinitionVersion()
                .withSubscriptions(subscriptions);

        CreateSubscriptionDefinitionRequest createSubscriptionDefinitionRequest = new CreateSubscriptionDefinitionRequest()
                .withName(DEFAULT)
                .withInitialVersion(subscriptionDefinitionVersion);

        CreateSubscriptionDefinitionResult createSubscriptionDefinitionResult = awsGreengrassClient.createSubscriptionDefinition(createSubscriptionDefinitionRequest);
        return createSubscriptionDefinitionResult.getLatestVersionArn();
    }

    @Override
    public String createDefaultLoggerDefinitionAndVersion() {
        Logger lambdaLogger = new Logger()
                .withId(ioHelper.getUuid())
                .withComponent(LoggerComponent.Lambda)
                .withLevel(LoggerLevel.INFO)
                .withType(LoggerType.FileSystem)
                .withSpace(DEFAULT_LOGGER_SPACE_IN_KB);

        Logger systemLogger = new Logger()
                .withId(ioHelper.getUuid())
                .withComponent(LoggerComponent.GreengrassSystem)
                .withLevel(LoggerLevel.INFO)
                .withType(LoggerType.FileSystem)
                .withSpace(DEFAULT_LOGGER_SPACE_IN_KB);

        Logger cloudwatchLambdaLogger = new Logger()
                .withId(ioHelper.getUuid())
                .withComponent(LoggerComponent.Lambda)
                .withLevel(LoggerLevel.INFO)
                .withType(LoggerType.AWSCloudWatch);

        Logger cloudwatchSystemLogger = new Logger()
                .withId(ioHelper.getUuid())
                .withComponent(LoggerComponent.GreengrassSystem)
                .withLevel(LoggerLevel.INFO)
                .withType(LoggerType.AWSCloudWatch);

        LoggerDefinitionVersion loggerDefinitionVersion = new LoggerDefinitionVersion()
                .withLoggers(lambdaLogger,
                        systemLogger,
                        cloudwatchLambdaLogger,
                        cloudwatchSystemLogger);

        CreateLoggerDefinitionRequest createLoggerDefinitionRequest = new CreateLoggerDefinitionRequest()
                .withName(DEFAULT)
                .withInitialVersion(loggerDefinitionVersion);

        CreateLoggerDefinitionResult createLoggerDefinitionResult = awsGreengrassClient.createLoggerDefinition(createLoggerDefinitionRequest);

        return createLoggerDefinitionResult.getLatestVersionArn();
    }

    @Override
    public String createGroupVersion(String groupId, GroupVersion newGroupVersion) {
        Optional<GroupInformation> optionalGroupInformation = getGroupInformation(groupId);
        GroupVersion currentGroupVersion = null;
        GroupInformation groupInformation = null;

        CreateGroupVersionRequest createGroupVersionRequest = new CreateGroupVersionRequest()
                .withGroupId(groupId);

        if (optionalGroupInformation.isPresent()) {
            groupInformation = optionalGroupInformation.get();

            if (groupInformation.getLatestVersion() == null) {
                // Group exists but has no versions yet, don't use the group information
                groupInformation = null;
            }
        }

        if (groupInformation == null) {
            log.warn("Group [" + groupId + "] not found or has no previous versions, creating group version from scratch");

            // There is no current version so just use the new version as our reference
            currentGroupVersion = newGroupVersion;
        } else {
            GetGroupVersionResult latestGroupVersion = getLatestGroupVersion(groupInformation);

            currentGroupVersion = latestGroupVersion.getDefinition();
        }

        // When an ARN in the new version is NULL we take it from the current version.  This allows us to do updates more easily.
        mergeCurrentAndNewVersion(newGroupVersion, currentGroupVersion, createGroupVersionRequest);

        CreateGroupVersionResult createGroupVersionResult = awsGreengrassClient.createGroupVersion(createGroupVersionRequest);

        return createGroupVersionResult.getVersion();
    }

    private void mergeCurrentAndNewVersion(GroupVersion newGroupVersion, GroupVersion currentGroupVersion, CreateGroupVersionRequest createGroupVersionRequest) {
        if (newGroupVersion.getCoreDefinitionVersionArn() == null) {
            createGroupVersionRequest.setCoreDefinitionVersionArn(currentGroupVersion.getCoreDefinitionVersionArn());
        } else {
            createGroupVersionRequest.setCoreDefinitionVersionArn(newGroupVersion.getCoreDefinitionVersionArn());
        }

        if (newGroupVersion.getFunctionDefinitionVersionArn() == null) {
            createGroupVersionRequest.setFunctionDefinitionVersionArn(currentGroupVersion.getFunctionDefinitionVersionArn());
        } else {
            createGroupVersionRequest.setFunctionDefinitionVersionArn(newGroupVersion.getFunctionDefinitionVersionArn());
        }

        if (newGroupVersion.getSubscriptionDefinitionVersionArn() == null) {
            createGroupVersionRequest.setSubscriptionDefinitionVersionArn(currentGroupVersion.getSubscriptionDefinitionVersionArn());
        } else {
            createGroupVersionRequest.setSubscriptionDefinitionVersionArn(newGroupVersion.getSubscriptionDefinitionVersionArn());
        }

        if (newGroupVersion.getDeviceDefinitionVersionArn() == null) {
            createGroupVersionRequest.setDeviceDefinitionVersionArn(currentGroupVersion.getDeviceDefinitionVersionArn());
        } else {
            createGroupVersionRequest.setDeviceDefinitionVersionArn(newGroupVersion.getDeviceDefinitionVersionArn());
        }

        if (newGroupVersion.getLoggerDefinitionVersionArn() == null) {
            createGroupVersionRequest.setLoggerDefinitionVersionArn(currentGroupVersion.getLoggerDefinitionVersionArn());
        } else {
            createGroupVersionRequest.setLoggerDefinitionVersionArn(newGroupVersion.getLoggerDefinitionVersionArn());
        }

        if (newGroupVersion.getResourceDefinitionVersionArn() == null) {
            createGroupVersionRequest.setResourceDefinitionVersionArn(currentGroupVersion.getResourceDefinitionVersionArn());
        } else {
            createGroupVersionRequest.setResourceDefinitionVersionArn(newGroupVersion.getResourceDefinitionVersionArn());
        }
    }

    @Override
    public String createDeployment(String groupId, String groupVersionId) {
        CreateDeploymentRequest createDeploymentRequest = new CreateDeploymentRequest()
                .withGroupId(groupId)
                .withGroupVersionId(groupVersionId)
                .withDeploymentType(DeploymentType.NewDeployment);

        CreateDeploymentResult createDeploymentResult = awsGreengrassClient.createDeployment(createDeploymentRequest);
        return createDeploymentResult.getDeploymentId();
    }

    @Override
    public DeploymentStatus waitForDeploymentStatusToChange(String groupId, String deploymentId) {
        GetDeploymentStatusRequest getDeploymentStatusRequest = new GetDeploymentStatusRequest()
                .withGroupId(groupId)
                .withDeploymentId(deploymentId);

        log.info("Checking deployment status...");

        GetDeploymentStatusResult getDeploymentStatusResult = awsGreengrassClient.getDeploymentStatus(getDeploymentStatusRequest);
        String deploymentStatus = getDeploymentStatusResult.getDeploymentStatus();

        if (deploymentStatus.equals(IN_PROGRESS) || deploymentStatus.equals(SUCCESS)) {
            return DeploymentStatus.SUCCESSFUL;
        } else if (deploymentStatus.equals(FAILURE)) {
            String errorMessage = getDeploymentStatusResult.getErrorMessage();

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
                GroupOwnerSetting groupOwnerSetting = new GroupOwnerSetting()
                        .withAutoAddGroupOwner(true);

                LocalDeviceResourceData localDeviceResourceData = new LocalDeviceResourceData()
                        .withGroupOwnerSetting(groupOwnerSetting)
                        .withSourcePath(localDeviceResource.getPath());

                ResourceDataContainer resourceDataContainer = new ResourceDataContainer()
                        .withLocalDeviceResourceData(localDeviceResourceData);

                resources.add(createResource(resourceDataContainer, localDeviceResource.getName(), localDeviceResource.getName()));
            }

            for (LocalVolumeResource localVolumeResource : functionConf.getLocalVolumeResources()) {
                GroupOwnerSetting groupOwnerSetting = new GroupOwnerSetting()
                        .withAutoAddGroupOwner(true);

                LocalVolumeResourceData localVolumeResourceData = new LocalVolumeResourceData()
                        .withGroupOwnerSetting(groupOwnerSetting)
                        .withSourcePath(localVolumeResource.getSourcePath())
                        .withDestinationPath(localVolumeResource.getDestinationPath());

                ResourceDataContainer resourceDataContainer = new ResourceDataContainer()
                        .withLocalVolumeResourceData(localVolumeResourceData);

                resources.add(createResource(resourceDataContainer, localVolumeResource.getName(), localVolumeResource.getName()));
            }

            for (LocalS3Resource localS3Resource : functionConf.getLocalS3Resources()) {
                S3MachineLearningModelResourceData s3MachineLearningModelResourceData = new S3MachineLearningModelResourceData()
                        .withS3Uri(localS3Resource.getUri())
                        .withDestinationPath(localS3Resource.getPath());

                ResourceDataContainer resourceDataContainer = new ResourceDataContainer()
                        .withS3MachineLearningModelResourceData(s3MachineLearningModelResourceData);

                resources.add(createResource(resourceDataContainer, localS3Resource.getName(), localS3Resource.getName()));
            }

            for (LocalSageMakerResource localSageMakerResource : functionConf.getLocalSageMakerResources()) {
                SageMakerMachineLearningModelResourceData sageMakerMachineLearningModelResourceData = new SageMakerMachineLearningModelResourceData()
                        .withSageMakerJobArn(localSageMakerResource.getArn())
                        .withDestinationPath(localSageMakerResource.getPath());

                ResourceDataContainer resourceDataContainer = new ResourceDataContainer()
                        .withSageMakerMachineLearningModelResourceData(sageMakerMachineLearningModelResourceData);

                resources.add(createResource(resourceDataContainer, localSageMakerResource.getName(), localSageMakerResource.getName()));
            }
        }

        ResourceDefinitionVersion resourceDefinitionVersion = new ResourceDefinitionVersion()
                .withResources(resources);

        validateResourceDefinitionVersion(resourceDefinitionVersion);

        CreateResourceDefinitionRequest createResourceDefinitionRequest = new CreateResourceDefinitionRequest()
                .withInitialVersion(resourceDefinitionVersion)
                .withName(ioHelper.getUuid());

        CreateResourceDefinitionResult createResourceDefinitionResult = awsGreengrassClient.createResourceDefinition(createResourceDefinitionRequest);

        return createResourceDefinitionResult.getLatestVersionArn();
    }

    private void validateResourceDefinitionVersion(ResourceDefinitionVersion resourceDefinitionVersion) {
        List<Resource> resource = resourceDefinitionVersion.getResources();

        List<LocalDeviceResourceData> localDeviceResources = resource.stream()
                .map(res -> res.getResourceDataContainer().getLocalDeviceResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateLocalDeviceSourcePaths(localDeviceResources);

        List<LocalVolumeResourceData> localVolumeResources = resource.stream()
                .map(res -> res.getResourceDataContainer().getLocalVolumeResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateLocalVolumeSourcePaths(localVolumeResources);

        List<S3MachineLearningModelResourceData> localS3Resources = resource.stream()
                .map(res -> res.getResourceDataContainer().getS3MachineLearningModelResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateS3DestinationPaths(localS3Resources);

        List<SageMakerMachineLearningModelResourceData> localSageMakerResources = resource.stream()
                .map(res -> res.getResourceDataContainer().getSageMakerMachineLearningModelResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateSageMakerDestinationPaths(localSageMakerResources);
    }

    private void disallowDuplicateLocalDeviceSourcePaths(List<LocalDeviceResourceData> resources) {
        List<String> sourcePaths = resources.stream()
                .map(res -> res.getSourcePath())
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
                .map(res -> res.getSourcePath())
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

    private void disallowDuplicateSageMakerDestinationPaths(List<SageMakerMachineLearningModelResourceData> resources) {
        List<String> sourcePaths = resources.stream()
                .map(res -> res.getDestinationPath())
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
                .map(res -> res.getDestinationPath())
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
        Resource resource = new Resource()
                .withResourceDataContainer(resourceDataContainer)
                .withName(name)
                .withId(id);

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

        return new Device()
                .withCertificateArn(certificateArn)
                .withId(ioHelper.getUuid())
                .withSyncShadow(true)
                .withThingArn(iotHelper.getThingArn(thingName));
    }

    @Override
    public void disassociateServiceRoleFromAccount() {
        awsGreengrassClient.disassociateServiceRoleFromAccount(new DisassociateServiceRoleFromAccountRequest());
    }

    @Override
    public void disassociateRoleFromGroup(String groupId) {
        awsGreengrassClient.disassociateRoleFromGroup(new DisassociateRoleFromGroupRequest()
                .withGroupId(groupId));
    }

    @Override
    public GetGroupVersionResult getLatestGroupVersion(GroupInformation groupInformation) {
        GetGroupVersionRequest getGroupVersionRequest = new GetGroupVersionRequest()
                .withGroupId(groupInformation.getId())
                .withGroupVersionId(groupInformation.getLatestVersion());

        GetGroupVersionResult groupVersionResult = awsGreengrassClient.getGroupVersion(getGroupVersionRequest);

        return groupVersionResult;
    }

    private GroupVersion getGroupVersion(GroupInformation groupInformation) {
        GetGroupVersionResult latestGroupVersion = getLatestGroupVersion(groupInformation);

        GroupVersion groupVersion = latestGroupVersion.getDefinition();

        return groupVersion;
    }

    @Override
    public List<Function> getFunctions(GroupInformation groupInformation) {
        GroupVersion groupVersion = getGroupVersion(groupInformation);

        String functionDefinitionVersionArn = groupVersion.getFunctionDefinitionVersionArn();

        GetFunctionDefinitionVersionRequest getFunctionDefinitionVersionRequest = new GetFunctionDefinitionVersionRequest()
                .withFunctionDefinitionId(idExtractor.extractId(functionDefinitionVersionArn))
                .withFunctionDefinitionVersionId(idExtractor.extractVersionId(functionDefinitionVersionArn));
        GetFunctionDefinitionVersionResult getFunctionDefinitionVersionResult = awsGreengrassClient.getFunctionDefinitionVersion(getFunctionDefinitionVersionRequest);

        FunctionDefinitionVersion functionDefinition = getFunctionDefinitionVersionResult.getDefinition();
        List<Function> functions = functionDefinition.getFunctions();

        return functions;
    }

    @Override
    public List<Device> getDevices(GroupInformation groupInformation) {
        GroupVersion groupVersion = getGroupVersion(groupInformation);

        String deviceDefinitionVersionArn = groupVersion.getDeviceDefinitionVersionArn();

        GetDeviceDefinitionVersionRequest getDeviceDefinitionVersionRequest = new GetDeviceDefinitionVersionRequest()
                .withDeviceDefinitionId(idExtractor.extractId(deviceDefinitionVersionArn))
                .withDeviceDefinitionVersionId(idExtractor.extractVersionId(deviceDefinitionVersionArn));
        GetDeviceDefinitionVersionResult getDeviceDefinitionVersionResult = awsGreengrassClient.getDeviceDefinitionVersion(getDeviceDefinitionVersionRequest);

        DeviceDefinitionVersion deviceDefinition = getDeviceDefinitionVersionResult.getDefinition();
        List<Device> devices = deviceDefinition.getDevices();

        return devices;
    }

    @Override
    public List<Subscription> getSubscriptions(GroupInformation groupInformation) {
        GroupVersion groupVersion = getGroupVersion(groupInformation);

        String subscriptionDefinitionVersionArn = groupVersion.getSubscriptionDefinitionVersionArn();

        GetSubscriptionDefinitionVersionRequest getSubscriptionDefinitionVersionRequest = new GetSubscriptionDefinitionVersionRequest()
                .withSubscriptionDefinitionId(idExtractor.extractId(subscriptionDefinitionVersionArn))
                .withSubscriptionDefinitionVersionId(idExtractor.extractVersionId(subscriptionDefinitionVersionArn));
        GetSubscriptionDefinitionVersionResult getSubscriptionDefinitionVersionResult = awsGreengrassClient.getSubscriptionDefinitionVersion(getSubscriptionDefinitionVersionRequest);

        SubscriptionDefinitionVersion subscriptionDefinition = getSubscriptionDefinitionVersionResult.getDefinition();
        List<Subscription> subscriptions = subscriptionDefinition.getSubscriptions();

        return subscriptions;
    }

    @Override
    public GetGroupCertificateAuthorityResult getGroupCa(GroupInformation groupInformation) {
        ListGroupCertificateAuthoritiesRequest listGroupCertificateAuthoritiesRequest = new ListGroupCertificateAuthoritiesRequest()
                .withGroupId(groupInformation.getId());

        ListGroupCertificateAuthoritiesResult listGroupCertificateAuthoritiesResult = awsGreengrassClient.listGroupCertificateAuthorities(listGroupCertificateAuthoritiesRequest);

        if (listGroupCertificateAuthoritiesResult.getGroupCertificateAuthorities().size() != 1) {
            log.error("Currently we do not support multiple group CAs");
            return null;
        }

        GetGroupCertificateAuthorityRequest getGroupCertificateAuthorityRequest = new GetGroupCertificateAuthorityRequest()
                .withGroupId(groupInformation.getId())
                .withCertificateAuthorityId(listGroupCertificateAuthoritiesResult.getGroupCertificateAuthorities().get(0).getGroupCertificateAuthorityId());

        GetGroupCertificateAuthorityResult getGroupCertificateAuthorityResult = awsGreengrassClient.getGroupCertificateAuthority(getGroupCertificateAuthorityRequest);

        return getGroupCertificateAuthorityResult;
    }
}
