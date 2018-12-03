package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.amazonaws.regions.Region;
import com.amazonaws.services.greengrass.model.Device;
import com.amazonaws.services.greengrass.model.Function;
import com.amazonaws.services.greengrass.model.GroupVersion;
import com.amazonaws.services.greengrass.model.Subscription;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;
import com.amazonaws.services.iot.model.CreateRoleAliasResult;
import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.data.DeploymentStatus;
import com.awslabs.aws.greengrass.provisioner.data.VirtualTarEntry;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.conf.DeploymentConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.GGDConf;
import com.awslabs.aws.greengrass.provisioner.data.functions.BuildableFunction;
import com.awslabs.aws.greengrass.provisioner.data.functions.BuildableJavaMavenFunction;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerHelper;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerPushHandler;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.GreengrassBuildImageResultCallback;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerClientException;
import com.google.common.collect.ImmutableSet;
import com.typesafe.config.*;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.*;
import java.util.stream.Collectors;

import static com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner.serviceRoleName;

@Slf4j
public class BasicDeploymentHelper implements DeploymentHelper {
    public static final String DEPLOYMENT_DEFAULTS_CONF = "deployments/deployment.defaults.conf";
    public static final String BUILD = "build";
    public static final String USER_DIR = "user.dir";

    public static final String CERTS_DIRECTORY_PREFIX = "certs";
    public static final String CONFIG_DIRECTORY_PREFIX = "config";

    private final int normalFilePermissions = 0644;
    private final int scriptPermissions = 0755;
    @Inject
    ConfigFileHelper configFileHelper;
    @Inject
    AwsHelper awsHelper;
    @Inject
    ScriptHelper scriptHelper;
    @Inject
    IamHelper iamHelper;
    @Inject
    GreengrassHelper greengrassHelper;
    @Inject
    IoHelper ioHelper;
    @Inject
    PolicyHelper policyHelper;
    @Inject
    GGVariables ggVariables;
    @Inject
    GGConstants ggConstants;
    @Inject
    FunctionHelper functionHelper;
    @Inject
    SubscriptionHelper subscriptionHelper;
    @Inject
    GGDHelper ggdHelper;
    @Inject
    EnvironmentHelper environmentHelper;
    @Inject
    IotHelper iotHelper;
    @Inject
    CloudFormationHelper cloudFormationHelper;
    @Inject
    ArchiveHelper archiveHelper;
    @Inject
    DockerHelper dockerHelper;
    @Inject
    DockerClientProvider dockerClientProvider;
    @Inject
    DockerPushHandler dockerPushHandler;

    @Inject
    Provider<GreengrassBuildImageResultCallback> greengrassBuildImageResultCallbackProvider;
    private Optional<List<VirtualTarEntry>> installScriptVirtualTarEntries = Optional.empty();
    private Optional<List<VirtualTarEntry>> oemVirtualTarEntries = Optional.empty();
    private Optional<List<VirtualTarEntry>> ggdVirtualTarEntries = Optional.empty();

    private GreengrassBuildImageResultCallback greengrassBuildImageResultCallback;

    @Inject
    public BasicDeploymentHelper() {
    }

    @Override
    public DeploymentConf getDeploymentConf(String deploymentConfigFilename, String groupName) {
        try {
            File deploymentConfigFile = new File(deploymentConfigFilename);

            if (!deploymentConfigFile.exists()) {
                return DeploymentConf.builder()
                        .error("The specified deployment configuration file [" + deploymentConfigFilename + "] does not exist.")
                        .build();
            }

            Config config = ConfigFactory.parseFile(deploymentConfigFile);
            config = config.withValue("ACCOUNT_ID", ConfigValueFactory.fromAnyRef(iamHelper.getAccountId()));
            config = config.withFallback(getFallbackConfig());
            config = config.resolve();


            return buildDeploymentConf(deploymentConfigFilename, config, groupName);
        } catch (ConfigException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    private DeploymentConf buildDeploymentConf(String deploymentConfigFilename, Config config, String groupName) {
        DeploymentConf.DeploymentConfBuilder deploymentConfBuilder = DeploymentConf.builder();

        String trimmedDeploymentName = deploymentConfigFilename.replaceAll(".conf$", "").replaceAll("^.*/", "");
        deploymentConfBuilder.name(trimmedDeploymentName);
        deploymentConfBuilder.functions(config.getStringList("conf.functions"));

        deploymentConfBuilder.groupName(groupName);

        deploymentConfBuilder.coreRoleName(config.getString("conf.core.roleName"));
        deploymentConfBuilder.coreRoleAssumeRolePolicy(config.getObject("conf.core.roleAssumeRolePolicy").render(ConfigRenderOptions.concise()));
        deploymentConfBuilder.coreRolePolicies(config.getStringList("conf.core.rolePolicies"));
        deploymentConfBuilder.corePolicy(config.getObject("conf.core.policy").render(ConfigRenderOptions.concise()));

        deploymentConfBuilder.lambdaRoleName(config.getString("conf.lambda.roleName"));
        deploymentConfBuilder.lambdaRoleAssumeRolePolicy(config.getObject("conf.lambda.roleAssumeRolePolicy").render(ConfigRenderOptions.concise()));

        deploymentConfBuilder.ggds(config.getStringList("conf.ggds"));

        setEnvironmentVariables(deploymentConfBuilder, config);

        return deploymentConfBuilder.build();
    }

    private void setEnvironmentVariables(DeploymentConf.DeploymentConfBuilder deploymentConfBuilder, Config config) {
        try {
            ConfigObject configObject = config.getObject("conf.environmentVariables");

            if (configObject.size() == 0) {
                log.info("- No environment variables specified for this deployment");
            }

            Config tempConfig = configObject.toConfig();

            for (Map.Entry<String, ConfigValue> configValueEntry : tempConfig.entrySet()) {
                deploymentConfBuilder.environmentVariable(configValueEntry.getKey(), String.valueOf(configValueEntry.getValue().unwrapped()));
            }
        } catch (ConfigException.Missing e) {
            log.info("No environment variables specified in this deployment");
        }
    }

    private Config getFallbackConfig() {
        return ConfigFactory.parseFile(new File(DEPLOYMENT_DEFAULTS_CONF));
    }

    /**
     * Create a deployment and wait for its status to change //
     *
     * @param greengrassServiceRole
     * @param greengrassRole
     * @param groupId
     * @param groupVersionId
     * @return false on failure, true on success
     */
    @Override
    public boolean createAndWaitForDeployment(java.util.Optional<Role> greengrassServiceRole, Optional<Role> greengrassRole, String groupId, String groupVersionId) {
        log.info("Creating a deployment");
        log.info("Group ID [" + groupId + "]");
        log.info("Group version ID [" + groupVersionId + "]");
        String deploymentId = greengrassHelper.createDeployment(groupId, groupVersionId);
        log.info("Deployment created [" + deploymentId + "]");

        DeploymentStatus deploymentStatus;

        do {
            //////////////////////////////////////////////
            // Wait for the deployment status to change //
            //////////////////////////////////////////////

            deploymentStatus = greengrassHelper.waitForDeploymentStatusToChange(groupId, deploymentId);

            if (deploymentStatus.equals(DeploymentStatus.NEEDS_NEW_DEPLOYMENT)) {
                if (greengrassServiceRole.isPresent() && greengrassRole.isPresent()) {
                    log.warn("There was a problem with IAM roles, attempting a new deployment");

                    // Disassociate roles
                    log.warn("Disassociating Greengrass service role");
                    greengrassHelper.disassociateServiceRoleFromAccount();
                    log.warn("Disassociating role from group");
                    greengrassHelper.disassociateRoleFromGroup(groupId);

                    log.warn("Letting IAM settle...");

                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // Reassociate roles
                    log.warn("Reassociating Greengrass service role");
                    associateServiceRoleToAccount(greengrassServiceRole.get());
                    log.warn("Reassociating Greengrass group role");
                    associateRoleToGroup(greengrassRole.get(), groupId);

                    log.warn("Letting IAM settle...");

                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    log.warn("Trying another deployment");
                    deploymentId = greengrassHelper.createDeployment(groupId, groupVersionId);
                    log.warn("Deployment created [" + deploymentId + "]");

                    log.warn("Letting deployment settle...");

                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    log.error("Deployment failed due to IAM issue.");
                    return true;
                }
            } else if (deploymentStatus.equals(DeploymentStatus.BUILDING)) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } while (!deploymentStatus.equals(DeploymentStatus.FAILED) &&
                !deploymentStatus.equals(DeploymentStatus.SUCCESSFUL));

        if (!deploymentStatus.equals(DeploymentStatus.SUCCESSFUL)) {
            log.error("Deployment failed");
            return false;
        }

        log.info("Deployment successful");
        return true;
    }

    @Override
    public void associateRoleToGroup(Role greengrassRole, String groupId) {
        log.info("Associating the Greengrass role to the group");
        greengrassHelper.associateRoleToGroup(groupId, greengrassRole);
    }

    @Override
    public void associateServiceRoleToAccount(Role greengrassServiceRole) {
        // NOTE: If you leave this out you may get errors related to Greengrass being unable to access your account to do deployments
        log.info("Associating Greengrass service role to account");
        greengrassHelper.associateServiceRoleToAccount(greengrassServiceRole);
    }

    @Override
    public void doDeployment(DeploymentArguments deploymentArguments) {
        // Make the directories for build, if necessary
        ioHelper.createDirectoryIfNecessary(BUILD);

        ///////////////////////////////////////
        // Load the deployment configuration //
        ///////////////////////////////////////

        DeploymentConf deploymentConf = getDeploymentConf(deploymentArguments.deploymentConfigFilename, deploymentArguments.groupName);

        if (deploymentConf.getError() != null) {
            log.error(deploymentConf.getError());
            return;
        }

        // Create the service role
        Role greengrassServiceRole = createServiceRole(deploymentConf);

        // Create the role for the core
        Role greengrassRole = createGreengrassRole(deploymentConf);

        ///////////////////////////
        // Create the role alias //
        ///////////////////////////

        CreateRoleAliasResult createRoleAliasResult = iotHelper.createRoleAliasIfNecessary(greengrassServiceRole, serviceRoleName);

        ///////////////////////////////////////////////////
        // Create an AWS Greengrass Group and get its ID //
        ///////////////////////////////////////////////////

        log.info("Creating a Greengrass group, if necessary");
        String groupId = greengrassHelper.createGroupIfNecessary(deploymentArguments.groupName);

        ////////////////////////////////////
        // Create things and certificates //
        ////////////////////////////////////

        log.info("Creating core thing");
        String coreThingName = ggVariables.getCoreThingName(deploymentArguments.groupName);
        String coreThingArn = iotHelper.createThing(coreThingName);

        //////////////////////////////////
        // Create or reuse certificates //
        //////////////////////////////////

        log.info("Getting keys and certificate for core thing");
        CreateKeysAndCertificateResult coreKeysAndCertificate = iotHelper.createOrLoadKeysAndCertificate(groupId, CORE_SUB_NAME);

        String coreCertificateArn = coreKeysAndCertificate.getCertificateArn();

        //////////////////////////////////
        // Policy creation for the core //
        //////////////////////////////////

        log.info("Creating and attaching policies to core");
        iotHelper.createPolicyIfNecessary(ggVariables.getCorePolicyName(deploymentArguments.groupName), deploymentConf.getCorePolicy());
        iotHelper.attachPrincipalPolicy(ggVariables.getCorePolicyName(deploymentArguments.groupName), coreCertificateArn);
        iotHelper.attachThingPrincipal(ggVariables.getCoreThingName(deploymentArguments.groupName), coreCertificateArn);

        ////////////////////////////////////////////////
        // Associate the Greengrass role to the group //
        ////////////////////////////////////////////////

        associateRoleToGroup(greengrassRole, groupId);

        ////////////////////////////////////////////
        // Create a core definition and a version //
        ////////////////////////////////////////////

        log.info("Creating core definition");
        String coreDefinitionVersionArn = greengrassHelper.createCoreDefinitionAndVersion(ggVariables.getCoreDefinitionName(deploymentArguments.groupName), coreCertificateArn, coreThingArn);

        //////////////////////////////////////////////
        // Create a logger definition and a version //
        //////////////////////////////////////////////

        log.info("Creating logger definition");
        String loggerDefinitionVersionArn = greengrassHelper.createDefaultLoggerDefinitionAndVersion();

        //////////////////////////////////////////////
        // Create the Lambda role for the functions //
        //////////////////////////////////////////////

        log.info("Creating Lambda role");
        Role lambdaRole = iamHelper.createRoleIfNecessary(deploymentConf.getLambdaRoleName(), deploymentConf.getLambdaRoleAssumeRolePolicy());

        ////////////////////////////////////////////////////////
        // Start building the subscription and function lists //
        ////////////////////////////////////////////////////////

        List<Subscription> subscriptions = new ArrayList<>();

        ///////////////////////////////////////////////////
        // Find enabled functions and their mapping info //
        ///////////////////////////////////////////////////

        Map<String, String> defaultEnvironment = environmentHelper.getDefaultEnvironment(groupId, coreThingName, coreThingArn, deploymentArguments.groupName);

        List<FunctionConf> functionConfs = functionHelper.getFunctionConfObjects(defaultEnvironment, deploymentConf);

        /////////////////////////////////////////////////////
        // Launch any CloudFormation templates we've found //
        /////////////////////////////////////////////////////

        List<String> cloudFormationStacksLaunched = functionConfs.stream()
                .map(functionConf -> cloudFormationHelper.deployCloudFormationTemplate(defaultEnvironment, deploymentArguments.groupName, functionConf))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        /////////////////////////
        // Build the functions //
        /////////////////////////

        // Get a list of the buildable functions
        List<BuildableFunction> buildableFunctions = functionHelper.getBuildableFunctions(functionConfs, lambdaRole);

        // Install Java dependencies if necessary
        buildableFunctions.stream()
                .filter(buildableFunction -> buildableFunction instanceof BuildableJavaMavenFunction)
                .findFirst()
                .ifPresent(buildableFunction -> functionHelper.installJavaDependencies());

        // Get the map of functions to function configuration (builds functions and publishes them to Lambda)
        Map<Function, FunctionConf> functionToConfMap = functionHelper.buildFunctionsAndGenerateMap(buildableFunctions);

        ///////////////////////////////////////////
        // Find GGD configs and its mapping info //
        ///////////////////////////////////////////

        List<GGDConf> ggdConfs = new ArrayList<>();

        for (String ggd : deploymentConf.getGgds()) {
            ggdConfs.add(ggdHelper.getGGDConf(deploymentArguments.groupName, ggd));
        }

        ////////////////////////////
        // Set up local resources //
        ////////////////////////////

        log.info("Creating resource definition");
        String resourceDefinitionVersionArn = greengrassHelper.createResourceDefinitionVersion(functionConfs);

        /////////////////////////////////////////////////////////////////////////
        // Build the function definition for the Lambda function and a version //
        /////////////////////////////////////////////////////////////////////////

        log.info("Creating function definition");
        String functionDefinitionVersionArn = greengrassHelper.createFunctionDefinitionVersion(ImmutableSet.copyOf(functionToConfMap.keySet()));

        //////////////////////////////////////////////////
        // Create all of the things from the GGD config //
        //////////////////////////////////////////////////

        Set<String> thingArns = new HashSet<>();

        log.info("Creating Greengrass device things");

        Set<String> thingNames = ggdConfs.stream().map(GGDConf::getThingName).collect(Collectors.toSet());

        for (String thingName : thingNames) {
            String deviceThingArn = iotHelper.createThing(thingName);
            thingArns.add(deviceThingArn);

            String ggdThingName = getGgdThingName(thingName);
            String ggdPolicyName = String.join("_", ggdThingName, "Policy");

            log.info("- Creating keys and certificate for Greengrass device thing [" + thingName + "]");
            CreateKeysAndCertificateResult deviceKeysAndCertificate = iotHelper.createOrLoadKeysAndCertificate(groupId, ggdThingName);

            String deviceCertificateArn = deviceKeysAndCertificate.getCertificateArn();

            log.info("Creating and attaching policies to Greengrass device thing");
            iotHelper.createPolicyIfNecessary(ggdPolicyName, policyHelper.buildDevicePolicyDocument(deviceThingArn));
            iotHelper.attachPrincipalPolicy(ggdPolicyName, deviceCertificateArn);
            iotHelper.attachThingPrincipal(thingName, deviceCertificateArn);
        }

        //////////////////////////////////////////////////////
        // Connection functions to cloud, GGDs, and shadows //
        //////////////////////////////////////////////////////

        subscriptions.addAll(functionToConfMap.entrySet().stream()
                .flatMap(entry -> subscriptionHelper.createCloudSubscriptionsForArn(
                        entry.getValue().getFromCloudSubscriptions(),
                        entry.getValue().getToCloudSubscriptions(),
                        entry.getKey().getFunctionArn()).stream())
                .collect(Collectors.toList()));

        subscriptions.addAll(subscriptionHelper.connectFunctionsAndDevices(functionToConfMap, ggdConfs));
        subscriptions.addAll(subscriptionHelper.connectFunctionsToShadows(functionToConfMap));

        //////////////////////////////////////////////////////
        // Get a list of all of the connected thing shadows //
        //////////////////////////////////////////////////////

        Set<String> connectedShadowThings = new HashSet<>();

        for (FunctionConf functionConf : functionToConfMap.values()) {
            connectedShadowThings.addAll(functionConf.getConnectedShadows());

            for (String connectedShadow : functionConf.getConnectedShadows()) {
                // Make sure all of the connected shadows exist
                iotHelper.createThing(connectedShadow);
            }
        }

        for (GGDConf ggdConf : ggdConfs) {
            connectedShadowThings.addAll(ggdConf.getConnectedShadows());
        }

        List<Device> devices = new ArrayList<>();

        thingNames.addAll(connectedShadowThings);

        for (String thingName : thingNames) {
            devices.add(greengrassHelper.getDevice(thingName));
        }

        ////////////////////////////////////////////
        // Get a list of all GGD PIP dependencies //
        ////////////////////////////////////////////

        Set<String> ggdPipDependencies = ggdConfs.stream()
                .flatMap(ggdConf -> ggdConf.getDependencies().stream())
                .collect(Collectors.toSet());

        ///////////////////////////////////////
        // Connect GGDs to cloud and shadows //
        ///////////////////////////////////////

        for (GGDConf ggdConf : ggdConfs) {
            String deviceThingArn = iotHelper.getThingArn(ggdConf.getThingName());
            subscriptions.addAll(subscriptionHelper.createCloudSubscriptionsForArn(ggdConf.getFromCloudSubscriptions(), ggdConf.getToCloudSubscriptions(), deviceThingArn));

            for (String connectedShadow : ggdConf.getConnectedShadows()) {
                subscriptions.addAll(subscriptionHelper.createShadowSubscriptions(deviceThingArn, connectedShadow));
            }
        }

        //////////////////////////////////
        // Connection functions and GGD //
        //////////////////////////////////

        log.info("Creating device definition");
        String deviceDefinitionVersionArn = greengrassHelper.createDeviceDefinitionAndVersion(ggVariables.getDeviceDefinitionName(deploymentArguments.groupName), devices);

        //////////////////////////////////////////////////////
        // Create the subscription definition from our list //
        //////////////////////////////////////////////////////

        log.info("Creating subscription definition");
        String subscriptionDefinitionVersionArn = greengrassHelper.createSubscriptionDefinitionAndVersion(subscriptions);

        ////////////////////////////////////
        // Create a minimal group version //
        ////////////////////////////////////

        log.info("Creating group version");

        GroupVersion groupVersion = new GroupVersion()
                .withCoreDefinitionVersionArn(coreDefinitionVersionArn)
                .withFunctionDefinitionVersionArn(functionDefinitionVersionArn)
                .withSubscriptionDefinitionVersionArn(subscriptionDefinitionVersionArn)
                .withDeviceDefinitionVersionArn(deviceDefinitionVersionArn)
                .withLoggerDefinitionVersionArn(loggerDefinitionVersionArn)
                .withResourceDefinitionVersionArn(resourceDefinitionVersionArn);

        String groupVersionId = greengrassHelper.createGroupVersion(groupId, groupVersion);

        /////////////////////////////////////////////
        // Do all of the output file related stuff //
        /////////////////////////////////////////////

        buildOutputFiles(deploymentArguments, createRoleAliasResult, groupId, coreThingName, coreThingArn, coreKeysAndCertificate, ggdConfs, thingNames, ggdPipDependencies);

        ///////////////////////////////////////////////////
        // Start the Docker container build if necessary //
        ///////////////////////////////////////////////////

        if (deploymentArguments.buildContainer == true) {
            log.info("Configuring container build");
            greengrassBuildImageResultCallback = greengrassBuildImageResultCallbackProvider.get();

            dockerHelper.setEcrRepositoryName(Optional.ofNullable(deploymentArguments.ecrRepositoryNameString));
            dockerHelper.setEcrImageName(Optional.ofNullable(deploymentArguments.ecrImageNameString));
            String imageName = dockerHelper.getImageName();
            String currentDirectory = System.getProperty(USER_DIR);
            File dockerfile = dockerHelper.getDockerfileForArchitecture(deploymentArguments.architecture);

            dockerClientProvider.get().buildImageCmd()
                    // NOTE: Base directory MUST come before Dockerfile or the build will fail!
                    .withBaseDirectory(new File(currentDirectory))
                    .withDockerfile(dockerfile)
                    .withBuildArg("GROUP_NAME", deploymentArguments.groupName)
                    .withTags(Collections.singleton(imageName))
                    .exec(greengrassBuildImageResultCallback);
            log.info("Building container in background");
        }

        // Create a deployment and wait for it to succeed.  Return if it fails.
        if (!createAndWaitForDeployment(Optional.of(greengrassServiceRole), Optional.of(greengrassRole), groupId, groupVersionId)) {
            // Deployment failed
            return;
        }

        ///////////////////////////////////////////////////////
        // Wait for the Docker build to finish, if necessary //
        ///////////////////////////////////////////////////////

        if (deploymentArguments.buildContainer == true) {
            try {
                log.info("Waiting for Docker build to complete...");
                String imageId = greengrassBuildImageResultCallback.awaitImageId();

                if (imageId != null) {
                    pushContainerIfNecessary(deploymentArguments, imageId);
                } else if (greengrassBuildImageResultCallback.error()) {
                    throw new UnsupportedOperationException(greengrassBuildImageResultCallback.getBuildError().get());
                } else {
                    throw new UnsupportedOperationException("No image ID received from Docker, this is likely a bug in the provisioner");
                }
            } catch (DockerClientException e) {
                log.error("Docker build failed [" + e.getMessage() + "]");
                e.printStackTrace();
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for Docker build");
                e.printStackTrace();
            }
        }

        //////////////////////////////////////////////////////////////////////////
        // Wait for the CloudFormation stacks to finish launching, if necessary //
        //////////////////////////////////////////////////////////////////////////

        if (cloudFormationStacksLaunched.size() != 0) {
            waitForStacksToLaunch(cloudFormationStacksLaunched);
        }
    }

    /**
     * Create service role required for Greengrass
     *
     * @param deploymentConf
     * @return
     */
    private Role createServiceRole(DeploymentConf deploymentConf) {
        List<String> serviceRolePolicies = Arrays.asList("arn:aws:iam::aws:policy/service-role/AWSGreengrassResourceAccessRolePolicy",
                "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess");
        log.info("Creating Greengrass service role [" + serviceRoleName + "]");
        Role greengrassServiceRole = iamHelper.createRoleIfNecessary(serviceRoleName, deploymentConf.getCoreRoleAssumeRolePolicy());

        serviceRolePolicies.stream()
                .forEach(policy -> iamHelper.attachRolePolicy(greengrassServiceRole, policy));

        associateServiceRoleToAccount(greengrassServiceRole);
        return greengrassServiceRole;
    }

    public void buildOutputFiles(DeploymentArguments deploymentArguments, CreateRoleAliasResult createRoleAliasResult, String groupId, String awsIotThingName, String awsIotThingArn, CreateKeysAndCertificateResult coreKeysAndCertificate, List<GGDConf> ggdConfs, Set<String> thingNames, Set<String> ggdPipDependencies) {
        if (deploymentArguments.scriptOutput == true) {
            installScriptVirtualTarEntries = Optional.of(new ArrayList<>());
        }

        if (deploymentArguments.oemOutput == true) {
            oemVirtualTarEntries = Optional.of(new ArrayList<>());
        }

        if (deploymentArguments.ggdOutput == true) {
            ggdVirtualTarEntries = Optional.of(new ArrayList<>());
        }

        if (!installScriptVirtualTarEntries.isPresent() &&
                !oemVirtualTarEntries.isPresent() &&
                !ggdVirtualTarEntries.isPresent()) {
            log.warn("Not building any output files.  No output files specified (script, OEM, or GGD)");
            return;
        }

        log.info("Adding keys and certificate files to archive");
        archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, ggConstants.getCorePrivateKeyName(), coreKeysAndCertificate.getKeyPair().getPrivateKey().getBytes(), normalFilePermissions);
        archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, ggConstants.getCorePublicCertificateName(), coreKeysAndCertificate.getCertificatePem().getBytes(), normalFilePermissions);
        archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", CERTS_DIRECTORY_PREFIX, ggConstants.getCorePrivateKeyName()), coreKeysAndCertificate.getKeyPair().getPrivateKey().getBytes(), normalFilePermissions);
        archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", CERTS_DIRECTORY_PREFIX, ggConstants.getCorePublicCertificateName()), coreKeysAndCertificate.getCertificatePem().getBytes(), normalFilePermissions);

        for (String thingName : thingNames) {
            log.info("- Adding keys and certificate files to archive");
            CreateKeysAndCertificateResult deviceKeysAndCertificate = iotHelper.createOrLoadKeysAndCertificate(groupId, getGgdThingName(thingName));
            archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, ggConstants.getDevicePrivateKeyName(thingName), deviceKeysAndCertificate.getKeyPair().getPrivateKey().getBytes(), normalFilePermissions);
            archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, ggConstants.getDevicePublicCertificateName(thingName), deviceKeysAndCertificate.getCertificatePem().getBytes(), normalFilePermissions);
            archiveHelper.addVirtualTarEntry(ggdVirtualTarEntries, ggConstants.getDevicePrivateKeyName(thingName), deviceKeysAndCertificate.getKeyPair().getPrivateKey().getBytes(), normalFilePermissions);
            archiveHelper.addVirtualTarEntry(ggdVirtualTarEntries, ggConstants.getDevicePublicCertificateName(thingName), deviceKeysAndCertificate.getCertificatePem().getBytes(), normalFilePermissions);
        }

        ///////////////////////
        // Build config.json //
        ///////////////////////

        Region currentRegion = awsHelper.getCurrentRegion();

        log.info("Building config.json");
        String configJson = configFileHelper.generateConfigJson(ggConstants.getRootCaName(),
                ggConstants.getCorePublicCertificateName(),
                ggConstants.getCorePrivateKeyName(),
                awsIotThingArn,
                iotHelper.getEndpoint(),
                currentRegion);

        log.info("Adding config.json to archive");
        installScriptVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, ggConstants.getConfigFileName(), configJson.getBytes(), normalFilePermissions));
        oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", CONFIG_DIRECTORY_PREFIX, ggConstants.getConfigFileName()), configJson.getBytes(), normalFilePermissions));

        /////////////////////////
        // Get the AWS root CA //
        /////////////////////////

        log.info("Getting root CA");
        installScriptVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, ggConstants.getRootCaName(), ioHelper.download(ggConstants.getRootCaUrl()).getBytes(), normalFilePermissions));
        oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", CERTS_DIRECTORY_PREFIX, ggConstants.getRootCaName()), ioHelper.download(ggConstants.getRootCaUrl()).getBytes(), normalFilePermissions));
        ggdVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(ggdVirtualTarEntries, ggConstants.getRootCaName(), ioHelper.download(ggConstants.getRootCaUrl()).getBytes(), normalFilePermissions));

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Add some extra files to the OEM deployment so that Docker based deployments can do a redeployment on startup //
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", CONFIG_DIRECTORY_PREFIX, "group-id.txt"), groupId.getBytes(), normalFilePermissions));
        oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", CONFIG_DIRECTORY_PREFIX, "credential-provider-url.txt"), iotHelper.getCredentialProviderUrl().getBytes(), normalFilePermissions));
        oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", CONFIG_DIRECTORY_PREFIX, "thing-name.txt"), awsIotThingName.getBytes(), normalFilePermissions));
        oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", CONFIG_DIRECTORY_PREFIX, "role-alias-name.txt"), createRoleAliasResult.getRoleAlias().getBytes(), normalFilePermissions));
        oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", CONFIG_DIRECTORY_PREFIX, "region.txt"), currentRegion.getName().getBytes(), normalFilePermissions));

        ///////////////////////
        // Build the scripts //
        ///////////////////////

        String baseGgShScriptName = getBaseGgScriptName(deploymentArguments);
        String ggShScriptName = getGgShScriptName(deploymentArguments);

        log.info("Adding scripts to archive");
        Optional<Architecture> architecture = getArchitecture(deploymentArguments);
        installScriptVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, scriptHelper.getInstallScriptName(), scriptHelper.generateInstallScript(architecture.get()).getBytes(), scriptPermissions));
        installScriptVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, scriptHelper.getStartScriptName(), scriptHelper.generateStartScript(architecture.get()).getBytes(), scriptPermissions));
        installScriptVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, scriptHelper.getStopScriptName(), scriptHelper.generateStopScript(architecture.get()).getBytes(), scriptPermissions));
        installScriptVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, scriptHelper.getCleanScriptName(), scriptHelper.generateCleanScript(architecture.get(), baseGgShScriptName).getBytes(), scriptPermissions));
        installScriptVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, scriptHelper.getMonitorScriptName(), scriptHelper.generateMonitorScript(architecture.get()).getBytes(), scriptPermissions));
        installScriptVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, scriptHelper.getSystemdScriptName(), scriptHelper.generateSystemdScript().getBytes(), scriptPermissions));

        for (GGDConf ggdConf : ggdConfs) {
            File mainScript = null;
            String mainScriptName = ggdConf.getScriptName() + ".py";

            for (String filename : ggdConf.getFiles()) {
                File file = new File(ggdConf.getRootPath() + "/" + filename);

                if (file.getName().equals(mainScriptName)) {
                    mainScript = file;
                }

                installScriptVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, file.getPath(), ioHelper.readFile(file), scriptPermissions));
                ggdVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(ggdVirtualTarEntries, file.getPath(), ioHelper.readFile(file), scriptPermissions));
            }

            if (mainScript == null) {
                String message = "Main GGD script not found for [" + ggdConf.getScriptName() + "], exiting";
                log.error(message);
                throw new UnsupportedOperationException(message);
            }

            File finalMainScript = mainScript;

            installScriptVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, "run-" + ggdConf.getScriptName() + ".sh", scriptHelper.generateRunScript(architecture, finalMainScript.getPath(), ggdConf.getThingName()).getBytes(), scriptPermissions));
            ggdVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(ggdVirtualTarEntries, "run-" + ggdConf.getScriptName() + ".sh", scriptHelper.generateRunScript(architecture, finalMainScript.getPath(), ggdConf.getThingName()).getBytes(), scriptPermissions));
        }

        ///////////////////////////
        // Package everything up //
        ///////////////////////////

        if (installScriptVirtualTarEntries.isPresent()) {
            log.info("Adding Greengrass binary to archive");
            URL architectureUrl = getArchitectureUrl(deploymentArguments);
            installScriptVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, architecture.get().getFilename(), ioHelper.readFile(architectureUrl), normalFilePermissions));

            log.info("Building script [" + ggShScriptName + "]");
            ByteArrayOutputStream ggScriptTemplate = new ByteArrayOutputStream();

            try {
                ggScriptTemplate.write(scriptHelper.generateGgScript(ggdPipDependencies).getBytes());
                ggScriptTemplate.write("PAYLOAD:\n".getBytes());
                ggScriptTemplate.write(getByteArrayOutputStream(installScriptVirtualTarEntries).get().toByteArray());
            } catch (IOException e) {
                throw new UnsupportedOperationException(e);
            }

            log.info("Writing script [" + ggShScriptName + "]");
            ioHelper.writeFile(ggShScriptName, ggScriptTemplate.toByteArray());
            ioHelper.makeExecutable(ggShScriptName);
        }

        if (oemVirtualTarEntries.isPresent()) {
            String oemArchiveName = String.join("/", BUILD, String.join(".", "oem", deploymentArguments.groupName, "tar"));
            log.info("Writing OEM file [" + oemArchiveName + "]");
            ioHelper.writeFile(oemArchiveName, getByteArrayOutputStream(oemVirtualTarEntries).get().toByteArray());
            ioHelper.makeExecutable(oemArchiveName);
        }

        if (ggdVirtualTarEntries.isPresent()) {
            String ggdArchiveName = String.join("/", BUILD, String.join(".", "ggd", deploymentArguments.groupName, "tar"));
            log.info("Writing GGD file [" + ggdArchiveName + "]");
            ioHelper.writeFile(ggdArchiveName, getByteArrayOutputStream(ggdVirtualTarEntries).get().toByteArray());
            ioHelper.makeExecutable(ggdArchiveName);
        }
    }

    private String getGgShScriptName(DeploymentArguments deploymentArguments) {
        return String.join("/", BUILD, getBaseGgScriptName(deploymentArguments));
    }

    private String getBaseGgScriptName(DeploymentArguments deploymentArguments) {
        return String.join(".", "gg", deploymentArguments.groupName, "sh");
    }

    public void pushContainerIfNecessary(DeploymentArguments deploymentArguments, String imageId) throws InterruptedException {
        if (deploymentArguments.pushContainer != true) {
            return;
        }

        String ecrEndpoint = dockerHelper.getEcrProxyEndpoint();
        dockerHelper.createEcrRepositoryIfNecessary();
        String shortEcrEndpoint = ecrEndpoint.substring("https://".length()); // Remove leading https://
        String shortEcrEndpointAndRepo = String.join("/", shortEcrEndpoint, deploymentArguments.ecrRepositoryNameString);

        DockerClient dockerClient = dockerClientProvider.get();

        dockerClient.tagImageCmd(imageId, shortEcrEndpointAndRepo, deploymentArguments.groupName).exec();
        dockerClient.pushImageCmd(shortEcrEndpointAndRepo)
                .withAuthConfig(dockerClient.authConfig())
                .exec(dockerPushHandler);
        dockerPushHandler.await();

        Optional<Throwable> pushError = dockerPushHandler.getPushError();

        if (pushError.isPresent()) {
            log.error("Docker push failed [" + pushError.get().getMessage() + "]");
        }

        String containerName = shortEcrEndpointAndRepo + ":" + deploymentArguments.groupName;
        log.info("Container pushed to [" + containerName + "]");

        String baseDockerScriptName = String.join(".", "docker", deploymentArguments.groupName, "sh");
        String dockerShScriptName = String.join("/", BUILD, baseDockerScriptName);

        log.info("To run this container on Ubuntu on EC2 do the following:");
        log.info(" - Attach a role to the EC2 instance that gives it access to ECR");
        log.info(" - Run the Docker script [" + dockerShScriptName + "]");

        /* Temporarily removed until Ubuntu issues are sorted out
        if (!deploymentArguments.dockerScriptOutput == true) {
            log.warn("The Docker script was NOT built on this run");
        } else {
            StringBuilder stringBuilder = new StringBuilder();

            stringBuilder.append("#!/usr/bin/env bash\n");
            stringBuilder.append("\n");
            stringBuilder.append("set -e\n");
            stringBuilder.append("\n");
            stringBuilder.append("if [ ! -e docker.configured ]; then\n");
            stringBuilder.append("  sudo apt update && sudo apt install -y awscli\n");
            stringBuilder.append("  curl -fsSL get.docker.com -o get-docker.sh && sudo sh get-docker.sh && sudo usermod -aG docker ubuntu\n");
            stringBuilder.append("  sudo sh -c 'echo {\\\"storage-driver\\\":\\\"devicemapper\\\"} > /etc/docker/daemon.json' && sudo systemctl restart docker\n");
            stringBuilder.append("  sudo $(AWS_DEFAULT_REGION=" + awsHelper.getCurrentRegion() + " aws ecr get-login | sed -e 's/-e none //')\n");
            stringBuilder.append("  touch docker.configured\n");
            stringBuilder.append("fi\n");
            stringBuilder.append("sudo docker run -it --network host --privileged " + containerName + "\n");

            ioHelper.writeFile(dockerShScriptName, stringBuilder.toString().getBytes());
            ioHelper.makeExecutable(dockerShScriptName);
        }
        */
    }

    private void waitForStacksToLaunch(List<String> cloudFormationStacksLaunched) {
        log.info("Waiting for your stacks to launch...");

        cloudFormationStacksLaunched.stream()
                .forEach(cloudFormationHelper::waitForStackToLaunch);
    }

    public Optional<ByteArrayOutputStream> getByteArrayOutputStream
            (Optional<List<VirtualTarEntry>> virtualTarEntries) {
        Optional<ByteArrayOutputStream> baos;

        try {
            baos = archiveHelper.tar(virtualTarEntries);
        } catch (IOException e) {
            throw new UnsupportedOperationException(e);
        }

        return baos;
    }

    /**
     * Create IAM resources and configuration required for Greengrass
     *
     * @param deploymentConf
     * @return
     */
    private Role createGreengrassRole(DeploymentConf deploymentConf) {
        log.info("Creating Greengrass role [" + deploymentConf.getCoreRoleName() + "]");
        Role greengrassRole = iamHelper.createRoleIfNecessary(deploymentConf.getCoreRoleName(), deploymentConf.getCoreRoleAssumeRolePolicy());

        log.info("Attaching role policies to Greengrass role [" + deploymentConf.getCoreRoleName() + "]");

        log.info("Attaching role policies to Greengrass service role");
        for (String coreRolePolicy : deploymentConf.getCoreRolePolicies()) {
            log.info("- " + coreRolePolicy);
            iamHelper.attachRolePolicy(greengrassRole, coreRolePolicy);
        }
        return greengrassRole;
    }

    private String getGgdThingName(String thingName) {
        return String.join("-", ggConstants.getGgdPrefix(), thingName);
    }

    private Optional<Architecture> getArchitecture(DeploymentArguments deploymentArguments) {
        return Optional.ofNullable(deploymentArguments.architecture);
    }

    private URL getArchitectureUrl(DeploymentArguments deploymentArguments) {
        Optional<Architecture> architecture = getArchitecture(deploymentArguments);
        Optional<URL> architectureUrlOptional = architecture.map(Architecture::getResourceUrl).orElse(Optional.empty());

        if (architecture.isPresent() && !architectureUrlOptional.isPresent()) {
            log.error("The GG software for your architecture [" + architecture.get().getFilename() + "] is not available, please download it from the Greengrass console and put it in the [" + architecture.get().getDIST() + "] directory");
            System.exit(3);
        }

        return architectureUrlOptional.get();
    }

}
