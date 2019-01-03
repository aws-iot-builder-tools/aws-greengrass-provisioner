package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.data.DeploymentStatus;
import com.awslabs.aws.greengrass.provisioner.data.KeysAndCertificate;
import com.awslabs.aws.greengrass.provisioner.data.VirtualTarEntry;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.conf.DeploymentConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.GGDConf;
import com.awslabs.aws.greengrass.provisioner.data.functions.BuildableFunction;
import com.awslabs.aws.greengrass.provisioner.data.functions.BuildableJavaMavenFunction;
import com.awslabs.aws.greengrass.provisioner.docker.BasicProgressHandler;
import com.awslabs.aws.greengrass.provisioner.docker.GreengrassDockerHelper;
import com.awslabs.aws.greengrass.provisioner.docker.NormalDockerHelper;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.GreengrassDockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.NormalDockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.google.common.collect.ImmutableSet;
import com.jcraft.jsch.*;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.typesafe.config.*;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.greengrass.model.Device;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.GroupVersion;
import software.amazon.awssdk.services.greengrass.model.Subscription;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iot.model.CreateRoleAliasResponse;

import javax.inject.Inject;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner.serviceRoleName;

@Slf4j
public class BasicDeploymentHelper implements DeploymentHelper {
    public static final String USER_DIR = "user.dir";

    public static final String AWS_AMI_ACCOUNT_ID = "099720109477";
    public static final String UBUNTU_16_04_LTS_AMI_FILTER = "ubuntu/images/hvm-ssd/ubuntu-xenial-16.04-amd64-server-????????";

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
    NormalDockerHelper normalDockerHelper;
    @Inject
    GreengrassDockerHelper greengrassDockerHelper;
    @Inject
    NormalDockerClientProvider normalDockerClientProvider;
    @Inject
    GreengrassDockerClientProvider greengrassDockerClientProvider;
    @Inject
    BasicProgressHandler basicProgressHandler;
    @Inject
    Ec2Client ec2Client;
    @Inject
    GlobalDefaultHelper globalDefaultHelper;
    @Inject
    ThreadHelper threadHelper;

    private Optional<List<VirtualTarEntry>> installScriptVirtualTarEntries = Optional.empty();
    private Optional<List<VirtualTarEntry>> oemVirtualTarEntries = Optional.empty();
    private Optional<List<VirtualTarEntry>> ggdVirtualTarEntries = Optional.empty();

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
            throw new RuntimeException(e);
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
        return ConfigFactory.parseFile(ggConstants.getDeploymentDefaultsConf());
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
        String initialDeploymentId = greengrassHelper.createDeployment(groupId, groupVersionId);
        log.info("Deployment created [" + initialDeploymentId + "]");

        Optional<DeploymentStatus> optionalDeploymentStatus = threadHelper.timeLimitTask(getDeploymentCheckTask(greengrassServiceRole, greengrassRole, groupId, groupVersionId, initialDeploymentId), 5, TimeUnit.MINUTES);

        if (!optionalDeploymentStatus.isPresent() || !optionalDeploymentStatus.get().equals(DeploymentStatus.SUCCESSFUL)) {
            log.error("Deployment failed");
            return false;
        }

        log.info("Deployment successful");
        return true;
    }

    private Callable<DeploymentStatus> getDeploymentCheckTask(Optional<Role> greengrassServiceRole, Optional<Role> greengrassRole, String groupId, String groupVersionId, String initialDeploymentId) {
        return () -> {
            DeploymentStatus deploymentStatus;

            String deploymentId = initialDeploymentId;

            while (true) {
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

                        Thread.sleep(30000);

                        // Reassociate roles
                        log.warn("Reassociating Greengrass service role");
                        associateServiceRoleToAccount(greengrassServiceRole.get());
                        log.warn("Reassociating Greengrass group role");
                        associateRoleToGroup(greengrassRole.get(), groupId);

                        log.warn("Letting IAM settle...");

                        Thread.sleep(30000);

                        log.warn("Trying another deployment");
                        deploymentId = greengrassHelper.createDeployment(groupId, groupVersionId);
                        log.warn("Deployment created [" + deploymentId + "]");

                        log.warn("Letting deployment settle...");

                        Thread.sleep(30000);
                    } else {
                        log.error("Deployment failed due to IAM issue.");
                        return DeploymentStatus.FAILED;
                    }
                } else if (deploymentStatus.equals(DeploymentStatus.BUILDING)) {
                    Thread.sleep(5000);
                }

                if (deploymentStatus.equals(DeploymentStatus.FAILED) ||
                        deploymentStatus.equals(DeploymentStatus.SUCCESSFUL)) {
                    return deploymentStatus;
                }
            }
        };
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
        ioHelper.createDirectoryIfNecessary(ggConstants.getBuildDirectory());

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

        CreateRoleAliasResponse createRoleAliasResponse = iotHelper.createRoleAliasIfNecessary(greengrassServiceRole, serviceRoleName);

        ///////////////////////////////////////////////////
        // Create an AWS Greengrass Group and get its ID //
        ///////////////////////////////////////////////////

        if (greengrassHelper.groupExists(deploymentArguments.groupName) &&
                deploymentArguments.ec2Launch) {
            log.error("Group [" + deploymentArguments.groupName + "] already exists, cannot launch another EC2 instance for this group.  You can update the group configuration by not specifying the EC2 launch option.");
            return;
        }

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
        KeysAndCertificate coreKeysAndCertificate = iotHelper.createOrLoadKeysAndCertificate(groupId, CORE_SUB_NAME);

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

        ////////////////////////////////////////////////////
        // Determine if any functions need to run as root //
        ////////////////////////////////////////////////////

        boolean functionsRunningAsRoot = functionConfs.stream()
                .filter(functionConf -> (functionConf.getUid() == 0) || (functionConf.getGid() == 0))
                .anyMatch(functionConf -> !functionConf.isGreengrassContainer());

        if (functionsRunningAsRoot) {
            log.warn("At least one function was detected that is configured to run outside of the Greengrass container as root");
        }

        ////////////////////////////////////////////////////////////////////////////
        // Determine if any functions are running inside the Greengrass container //
        ////////////////////////////////////////////////////////////////////////////

        List<String> functionsRunningInGreengrassContainer = functionConfs.stream()
                .filter(functionConf -> functionConf.isGreengrassContainer())
                .map(FunctionConf::getFunctionName)
                .collect(Collectors.toList());


        ////////////////////////////////////////////////////////////////////////////////////////////////
        // Check if Docker launching was specified with functions running in the Greengrass container //
        ////////////////////////////////////////////////////////////////////////////////////////////////

        if (deploymentArguments.dockerLaunch && !functionsRunningInGreengrassContainer.isEmpty()) {
            log.error("The following functions are marked to run in the Greengrass container:");

            functionsRunningInGreengrassContainer.stream()
                    .forEach(name -> log.error("  " + name));

            log.error("When running in Docker all functions must be running without the Greengrass container.");
            log.error("Set the greengrassContainer option to false in the functions.default.conf and/or the individual function configurations and try again.");
            System.exit(1);
        }

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
            KeysAndCertificate deviceKeysAndCertificate = iotHelper.createOrLoadKeysAndCertificate(groupId, ggdThingName);

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
                        entry.getKey().functionArn()).stream())
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

        GroupVersion groupVersion = GroupVersion.builder()
                .coreDefinitionVersionArn(coreDefinitionVersionArn)
                .functionDefinitionVersionArn(functionDefinitionVersionArn)
                .subscriptionDefinitionVersionArn(subscriptionDefinitionVersionArn)
                .deviceDefinitionVersionArn(deviceDefinitionVersionArn)
                .loggerDefinitionVersionArn(loggerDefinitionVersionArn)
                .resourceDefinitionVersionArn(resourceDefinitionVersionArn)
                .build();

        String groupVersionId = greengrassHelper.createGroupVersion(groupId, groupVersion);

        /////////////////////////////////////////////
        // Do all of the output file related stuff //
        /////////////////////////////////////////////

        buildOutputFiles(deploymentArguments,
                createRoleAliasResponse,
                groupId,
                coreThingName,
                coreThingArn,
                coreKeysAndCertificate,
                ggdConfs,
                thingNames,
                ggdPipDependencies,
                functionsRunningAsRoot);

        //////////////////////////////////////////////////
        // Start building the EC2 instance if necessary //
        //////////////////////////////////////////////////

        Optional<String> optionalInstanceId = Optional.empty();

        if (deploymentArguments.ec2Launch) {
            log.info("Launching EC2 instance");
            optionalInstanceId = launchEc2Instance(deploymentArguments.groupName);

            if (!optionalInstanceId.isPresent()) {
                // Something went wrong, bail out
                return;
            }
        }

        ///////////////////////////////////////////////////
        // Start the Docker container build if necessary //
        ///////////////////////////////////////////////////

        if (deploymentArguments.buildContainer == true) {
            log.info("Configuring container build");

            normalDockerHelper.setEcrRepositoryName(Optional.ofNullable(deploymentArguments.ecrRepositoryNameString));
            normalDockerHelper.setEcrImageName(Optional.ofNullable(deploymentArguments.ecrImageNameString));
            String imageName = normalDockerHelper.getImageName();
            String currentDirectory = System.getProperty(USER_DIR);

            File dockerfile = greengrassDockerHelper.getDockerfileForArchitecture(deploymentArguments.architecture);
            String dockerfileTemplate = ioHelper.readFileAsString(dockerfile);
            dockerfileTemplate = dockerfileTemplate.replaceAll("GROUP_NAME", deploymentArguments.groupName);

            // Add the group name and UUID so we don't accidentally overwrite an existing file
            File tempDockerfile = dockerfile.toPath().getParent().resolve(
                    String.join(".", "Dockerfile", deploymentArguments.groupName, ioHelper.getUuid())).toFile();
            ioHelper.writeFile(tempDockerfile.toString(), dockerfileTemplate.getBytes());
            tempDockerfile.deleteOnExit();

            try (DockerClient dockerClient = greengrassDockerClientProvider.get()) {
                log.info("Building container");

                String imageId = dockerClient.build(new File(currentDirectory).toPath(),
                        basicProgressHandler,
                        DockerClient.BuildParam.dockerfile(tempDockerfile.toPath()));

                dockerClient.tag(imageId, imageName);
                pushContainerIfNecessary(deploymentArguments, imageId);
            } catch (DockerException e) {
                log.error("Container build failed");
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                log.error("Container build failed");
                throw new RuntimeException(e);
            } catch (IOException e) {
                log.error("Container build failed");
                throw new RuntimeException(e);
            }
        }

        // Create a deployment and wait for it to succeed.  Return if it fails.
        if (!createAndWaitForDeployment(Optional.of(greengrassServiceRole), Optional.of(greengrassRole), groupId, groupVersionId)) {
            // Deployment failed
            return;
        }

        //////////////////////////////////////////////
        // Launch the Docker container if necessary //
        //////////////////////////////////////////////

        if (deploymentArguments.dockerLaunch) {
            log.info("Launching Docker container");
            greengrassDockerHelper.pullImage(ggConstants.getOfficialGreengrassDockerImage());
            greengrassDockerHelper.createAndStartContainer(ggConstants.getOfficialGreengrassDockerImage(), deploymentArguments.groupName);
        }

        ///////////////////////////////////////////////////////
        // Wait for the EC2 instance to launch, if necessary //
        ///////////////////////////////////////////////////////

        if (optionalInstanceId.isPresent()) {
            String instanceId = optionalInstanceId.get();

            DescribeInstancesRequest describeInstancesRequest = DescribeInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();

            DescribeInstancesResponse describeInstancesResponse = ec2Client.describeInstances(describeInstancesRequest);

            Optional<Reservation> optionalReservation = describeInstancesResponse.reservations().stream().findFirst();

            if (!optionalReservation.isPresent()) {
                log.error("Error finding the EC2 reservation to wait for the instance to finish launching, this should never happen");
                return;
            }

            Reservation reservation = optionalReservation.get();

            Optional<Instance> optionalInstance = reservation.instances().stream().findFirst();

            if (!optionalInstance.isPresent()) {
                log.error("Error finding the EC2 instance to wait for it to finish launching, this should never happen");
                return;
            }

            Instance instance = optionalInstance.get();

            String publicIpAddress = instance.publicIpAddress();

            if (publicIpAddress == null) {
                log.error("Public IP address returned from EC2 was NULL, skipping EC2 setup");
                return;
            }

            String user = "ubuntu";

            JSch jsch = new JSch();

            Optional<String> optionalHomeDirectory = globalDefaultHelper.getHomeDirectory();

            if (optionalHomeDirectory.isPresent()) {
                Path sshDirectory = new File(String.join("/", optionalHomeDirectory.get(), ".ssh")).toPath();
                try {
                    List<String> privateKeyFiles = Files.list(sshDirectory)
                            .filter(path -> ioHelper.readFileAsString(path.toFile()).contains("BEGIN RSA PRIVATE KEY"))
                            .map(Path::toString)
                            .collect(Collectors.toList());

                    for (String privateKeyFile : privateKeyFiles) {
                        try {
                            jsch.addIdentity(privateKeyFile);
                        } catch (JSchException e) {
                            log.error("Issue with private key file [" + privateKeyFile + "], skipping");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            Optional<Session> optionalSession = threadHelper.timeLimitTask(getSshSessionTask(publicIpAddress, user, jsch), 2, TimeUnit.MINUTES);

            if (!optionalSession.isPresent()) {
                log.error("Failed to connect and bootstrap the instance via SSH");
                return;
            }

            Session session = optionalSession.get();

            threadHelper.timeLimitTask(getCopyAndBootstrapTask(deploymentArguments, publicIpAddress, user, session), 5, TimeUnit.MINUTES);
        }

        //////////////////////////////////////////////////////////////////////////
        // Wait for the CloudFormation stacks to finish launching, if necessary //
        //////////////////////////////////////////////////////////////////////////

        if (cloudFormationStacksLaunched.size() != 0) {
            waitForStacksToLaunch(cloudFormationStacksLaunched);
        }
    }

    private Callable<Boolean> getCopyAndBootstrapTask(DeploymentArguments deploymentArguments, String publicIpAddress, String user, Session session) {
        return () -> {
            String filename = String.join(".", "gg", deploymentArguments.groupName, "sh");
            String localFilename = String.join("/", "build", filename);
            String remoteFilename = filename;
            log.info("Copying bootstrap script to instance via scp...");
            sendFile(session, localFilename, remoteFilename);
            runCommand(session, String.join(" ", "chmod", "+x", "./" + remoteFilename));
            log.info("Running bootstrap script on instance in screen, connect to the instance [" + user + "@" + publicIpAddress + "] and run 'screen -r' to see the progress");
            runCommandInScreen(session, String.join(" ", "./" + remoteFilename, "--now"));
            session.disconnect();
            return true;
        };
    }

    private Callable<Session> getSshSessionTask(String publicIpAddress, String user, JSch jsch) {
        return () -> {
            while (true) {
                try {
                    Session session = jsch.getSession(user, publicIpAddress, 22);
                    Properties config = new Properties();
                    config.put("StrictHostKeyChecking", "no");
                    session.setConfig(config);
                    session.connect(10000);
                    log.info("Connected to EC2 instance");

                    return session;
                } catch (JSchException e) {
                    String message = e.getMessage();

                    if (message.contains("timeout")) {
                        log.warn("SSH connection timed out, instance may still be starting up...");

                        Thread.sleep(5000);
                        continue;
                    }

                    if (message.contains("Connection refused")) {
                        log.warn("SSH connection refused, instance may still be starting up...");

                        Thread.sleep(5000);
                        continue;
                    }

                    log.error("There was an SSH error [" + message + "]");
                }
            }
        };
    }

    private void runCommandInScreen(Session session, String command) throws JSchException, IOException {
        runCommand(session, String.join(" ", "screen", "-d", "-m", command));
    }

    private String runCommand(Session session, String command) throws JSchException, IOException {
        Channel channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);

        StringBuilder stringBuilder = new StringBuilder();

        try (InputStream commandOutput = channel.getInputStream()) {
            channel.connect();
            int readByte = commandOutput.read();

            while (readByte != 0xffffffff) {
                stringBuilder.append((char) readByte);
                readByte = commandOutput.read();
            }

        } finally {
            channel.disconnect();
        }

        return stringBuilder.toString();
    }

    private void sendFile(Session session, String localFilename, String remoteFilename) throws JSchException, IOException {
        boolean preserveTimestamp = false;

        // exec 'scp -t rfile' remotely
        remoteFilename = remoteFilename.replace("'", "'\"'\"'");
        remoteFilename = "'" + remoteFilename + "'";

        String command = "scp " + (preserveTimestamp ? "-p" : "") + " -t " + remoteFilename;

        Channel channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);

        // get I/O streams for remote scp
        try (OutputStream outputStream = channel.getOutputStream();
             InputStream inputStream = channel.getInputStream()) {
            channel.connect();

            if (checkAck(inputStream) != 0) {
                log.error("Bad acknowledgement while secure copying file, bailing out");
                return;
            }

            File localFile = new File(localFilename);

            if (preserveTimestamp) {
                command = "T " + (localFile.lastModified() / 1000) + " 0";
                // The access time should be sent here,
                // but it is not accessible with JavaAPI ;-<
                command += (" " + (localFile.lastModified() / 1000) + " 0\n");

                outputStream.write(command.getBytes());
                outputStream.flush();

                if (checkAck(inputStream) != 0) {
                    return;
                }
            }

            // send "C0644 filesize filename", where filename should not include '/'
            long filesize = localFile.length();

            command = "C0644 " + filesize + " ";

            if (localFilename.lastIndexOf('/') > 0) {
                command += localFilename.substring(localFilename.lastIndexOf('/') + 1);
            } else {
                command += localFilename;
            }

            command += "\n";

            outputStream.write(command.getBytes());
            outputStream.flush();

            if (checkAck(inputStream) != 0) {
                return;
            }

            byte[] bytes = new byte[1024];

            // send a content of localFilename
            try (FileInputStream fileInputStream = new FileInputStream(localFilename)) {
                while (true) {
                    int length = fileInputStream.read(bytes, 0, bytes.length);
                    if (length <= 0) break;
                    outputStream.write(bytes, 0, length); //out.flush();
                }
            }

            // send '\0'
            bytes[0] = 0;

            outputStream.write(bytes, 0, 1);
            outputStream.flush();

            if (checkAck(inputStream) != 0) {
                return;
            }
        }

        channel.disconnect();
    }

    int checkAck(InputStream inputStream) throws IOException {
        int statusByte = inputStream.read();

        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1

        if (statusByte == 0) return statusByte;
        if (statusByte == -1) return statusByte;

        if (statusByte == 1 || statusByte == 2) {
            StringBuilder stringBuilder = new StringBuilder();

            int nextChar;

            do {
                nextChar = inputStream.read();
                stringBuilder.append((char) nextChar);
            }
            while (nextChar != '\n');

            if (statusByte == 1) { // error
                log.error(stringBuilder.toString());
            }

            if (statusByte == 2) { // fatal error
                log.error(stringBuilder.toString());
            }
        }

        return statusByte;
    }

    private Optional<String> launchEc2Instance(String groupName) {
        String instanceTagName = String.join("-", "greengrass", groupName);

        DescribeImagesRequest describeImagesRequest = DescribeImagesRequest.builder()
                .owners(AWS_AMI_ACCOUNT_ID)
                .filters(Filter.builder().name("name").values(UBUNTU_16_04_LTS_AMI_FILTER).build(),
                        Filter.builder().name("state").values("available").build())
                .build();

        DescribeImagesResponse describeImagesResponse = ec2Client.describeImages(describeImagesRequest);
        Optional<Image> optionalImage = describeImagesResponse.images().stream().findFirst();

        if (!optionalImage.isPresent()) {
            log.error("No Ubuntu 16.04 LTS image found in this region, not launching the instance");
            return Optional.empty();
        }

        DescribeKeyPairsResponse describeKeyPairsResponse = ec2Client.describeKeyPairs();
        Optional<KeyPairInfo> optionalKeyPairInfo = describeKeyPairsResponse.keyPairs().stream().sorted(Comparator.comparing(KeyPairInfo::keyName)).findFirst();

        if (!optionalKeyPairInfo.isPresent()) {
            log.error("No SSH keys found in your account, not launching the instance");
            return Optional.empty();
        }

        KeyPairInfo keyPairInfo = optionalKeyPairInfo.get();

        log.warn("Automatically chose the first key pair available [" + keyPairInfo.keyName() + "]");

        Image image = optionalImage.get();

        IpPermission sshPermission = IpPermission.builder()
                .toPort(22)
                .fromPort(22)
                .ipProtocol("tcp")
                .ipRanges(IpRange.builder().cidrIp("0.0.0.0/0").build())
                .build();

        String securityGroupName = String.join("-", instanceTagName, ioHelper.getUuid());

        CreateSecurityGroupRequest createSecurityGroupRequest = CreateSecurityGroupRequest.builder()
                .groupName(securityGroupName)
                .description("Security group for Greengrass Core [" + instanceTagName + "]")
                .build();

        ec2Client.createSecurityGroup(createSecurityGroupRequest);

        AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest = AuthorizeSecurityGroupIngressRequest.builder()
                .groupName(securityGroupName)
                .ipPermissions(sshPermission)
                .build();

        ec2Client.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest);

        RunInstancesRequest run_request = RunInstancesRequest.builder()
                .imageId(image.imageId())
                .instanceType(InstanceType.T2_MICRO)
                .maxCount(1)
                .minCount(1)
                .keyName(keyPairInfo.keyName())
                .securityGroups(securityGroupName)
                .build();

        RunInstancesResponse response = ec2Client.runInstances(run_request);

        Optional<String> optionalInstanceId = response.instances().stream().findFirst().map(Instance::instanceId);

        if (!optionalInstanceId.isPresent()) {
            log.error("Couldn't find an instance ID, this should never happen, not launching the instance");
            return Optional.empty();
        }

        String instanceId = optionalInstanceId.get();

        Tag tag = Tag.builder()
                .key("Name")
                .value(instanceTagName)
                .build();

        CreateTagsRequest tag_request = CreateTagsRequest.builder()
                .tags(tag)
                .resources(instanceId)
                .build();

        Optional<Boolean> optionalSuccess = threadHelper.timeLimitTask(getCreateTagsTask(tag_request), 2, TimeUnit.MINUTES);

        if (!optionalSuccess.isPresent() || optionalSuccess.get().equals(Boolean.FALSE)) {
            log.error("Failed to find the instance in EC2, it was not launched");
            return Optional.empty();
        }

        log.info("Launched instance [" + instanceId + "] with tag [" + instanceTagName + "]");

        return Optional.of(instanceId);
    }

    private Callable<Boolean> getCreateTagsTask(CreateTagsRequest tag_request) {
        return () -> {
            while (true) {
                try {
                    ec2Client.createTags(tag_request);
                    return true;
                } catch (Ec2Exception e) {
                    if (e.getMessage().contains("does not exist")) {
                        log.warn("Instance may still be starting, trying again...");

                        Thread.sleep(5000);
                    } else {
                        log.error("An exception occurred [" + e.getMessage() + "], not launching the instance");
                        return false;
                    }
                }
            }
        };
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

    public void buildOutputFiles(DeploymentArguments deploymentArguments, CreateRoleAliasResponse createRoleAliasResponse, String groupId, String awsIotThingName, String awsIotThingArn, KeysAndCertificate coreKeysAndCertificate, List<GGDConf> ggdConfs, Set<String> thingNames, Set<String> ggdPipDependencies, boolean functionsRunningAsRoot) {
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
        archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, ggConstants.getCorePrivateKeyName(), coreKeysAndCertificate.getKeyPair().privateKey().getBytes(), normalFilePermissions);
        archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, ggConstants.getCorePublicCertificateName(), coreKeysAndCertificate.getCertificatePem().getBytes(), normalFilePermissions);
        archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", ggConstants.getCertsDirectoryPrefix(), ggConstants.getCorePrivateKeyName()), coreKeysAndCertificate.getKeyPair().privateKey().getBytes(), normalFilePermissions);
        archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", ggConstants.getCertsDirectoryPrefix(), ggConstants.getCorePublicCertificateName()), coreKeysAndCertificate.getCertificatePem().getBytes(), normalFilePermissions);

        for (String thingName : thingNames) {
            log.info("- Adding keys and certificate files to archive");
            KeysAndCertificate deviceKeysAndCertificate = iotHelper.createOrLoadKeysAndCertificate(groupId, getGgdThingName(thingName));
            archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, ggConstants.getDevicePrivateKeyName(thingName), deviceKeysAndCertificate.getKeyPair().privateKey().getBytes(), normalFilePermissions);
            archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, ggConstants.getDevicePublicCertificateName(thingName), deviceKeysAndCertificate.getCertificatePem().getBytes(), normalFilePermissions);
            archiveHelper.addVirtualTarEntry(ggdVirtualTarEntries, ggConstants.getDevicePrivateKeyName(thingName), deviceKeysAndCertificate.getKeyPair().privateKey().getBytes(), normalFilePermissions);
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
                currentRegion,
                deploymentArguments,
                functionsRunningAsRoot);

        log.info("Adding config.json to archive");
        installScriptVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, ggConstants.getConfigFileName(), configJson.getBytes(), normalFilePermissions));
        oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), ggConstants.getConfigFileName()), configJson.getBytes(), normalFilePermissions));

        /////////////////////////
        // Get the AWS root CA //
        /////////////////////////

        log.info("Getting root CA");
        installScriptVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, ggConstants.getRootCaName(), ioHelper.download(ggConstants.getRootCaUrl()).getBytes(), normalFilePermissions));
        oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", ggConstants.getCertsDirectoryPrefix(), ggConstants.getRootCaName()), ioHelper.download(ggConstants.getRootCaUrl()).getBytes(), normalFilePermissions));
        ggdVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(ggdVirtualTarEntries, ggConstants.getRootCaName(), ioHelper.download(ggConstants.getRootCaUrl()).getBytes(), normalFilePermissions));

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Add some extra files to the OEM deployment so that Docker based deployments can do a redeployment on startup //
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "group-id.txt"), groupId.getBytes(), normalFilePermissions));
        oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "credential-provider-url.txt"), iotHelper.getCredentialProviderUrl().getBytes(), normalFilePermissions));
        oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "thing-name.txt"), awsIotThingName.getBytes(), normalFilePermissions));
        oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "role-alias-name.txt"), createRoleAliasResponse.roleAlias().getBytes(), normalFilePermissions));
        oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "region.txt"), currentRegion.id().getBytes(), normalFilePermissions));

        ///////////////////////
        // Build the scripts //
        ///////////////////////

        String baseGgShScriptName = ggVariables.getBaseGgScriptName(deploymentArguments.groupName);
        String ggShScriptName = ggVariables.getGgShScriptName(deploymentArguments.groupName);

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
                throw new RuntimeException(message);
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
                throw new RuntimeException(e);
            }

            log.info("Writing script [" + ggShScriptName + "]");
            ioHelper.writeFile(ggShScriptName, ggScriptTemplate.toByteArray());
            ioHelper.makeExecutable(ggShScriptName);
        }

        if (oemVirtualTarEntries.isPresent()) {
            String oemArchiveName = ggVariables.getOemArchiveName(deploymentArguments.groupName);
            log.info("Writing OEM file [" + oemArchiveName + "]");
            ioHelper.writeFile(oemArchiveName, getByteArrayOutputStream(oemVirtualTarEntries).get().toByteArray());
            ioHelper.makeExecutable(oemArchiveName);
        }

        if (ggdVirtualTarEntries.isPresent()) {
            String ggdArchiveName = ggVariables.getGgdArchiveName(deploymentArguments.groupName);
            log.info("Writing GGD file [" + ggdArchiveName + "]");
            ioHelper.writeFile(ggdArchiveName, getByteArrayOutputStream(ggdVirtualTarEntries).get().toByteArray());
            ioHelper.makeExecutable(ggdArchiveName);
        }
    }

    public void pushContainerIfNecessary(DeploymentArguments deploymentArguments, String imageId) throws InterruptedException {
        if (deploymentArguments.pushContainer != true) {
            return;
        }

        String ecrEndpoint = normalDockerHelper.getEcrProxyEndpoint();
        normalDockerHelper.createEcrRepositoryIfNecessary();
        String shortEcrEndpoint = ecrEndpoint.substring("https://".length()); // Remove leading https://
        String shortEcrEndpointAndRepo = String.join("/", shortEcrEndpoint, deploymentArguments.ecrRepositoryNameString);

        try (DockerClient dockerClient = normalDockerClientProvider.get()) {
            try {
                dockerClient.tag(imageId, String.join(":", shortEcrEndpointAndRepo, deploymentArguments.groupName));
            } catch (DockerException e) {
                throw new RuntimeException(e);
            }

            try {
                dockerClient.push(shortEcrEndpointAndRepo, basicProgressHandler, normalDockerClientProvider.getRegistryAuthSupplier().authFor(""));
            } catch (DockerException e) {
                log.error("Docker push failed [" + e.getMessage() + "]");
                throw new RuntimeException(e);
            }
        }

        String containerName = shortEcrEndpointAndRepo + ":" + deploymentArguments.groupName;
        log.info("Container pushed to [" + containerName + "]");

        /* Temporarily removed until Ubuntu issues are sorted out
        String baseDockerScriptName = String.join(".", "docker", deploymentArguments.groupName, "sh");
        String dockerShScriptName = String.join("/", BUILD, baseDockerScriptName);

        log.info("To run this container on Ubuntu on EC2 do the following:");
        log.info(" - Attach a role to the EC2 instance that gives it access to ECR");
        log.info(" - Run the Docker script [" + dockerShScriptName + "]");

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
            throw new RuntimeException(e);
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
