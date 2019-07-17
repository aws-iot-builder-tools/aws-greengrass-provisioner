package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.*;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.conf.*;
import com.awslabs.aws.greengrass.provisioner.data.functions.BuildableFunction;
import com.awslabs.aws.greengrass.provisioner.data.functions.BuildableJavaMavenFunction;
import com.awslabs.aws.greengrass.provisioner.docker.BasicProgressHandler;
import com.awslabs.aws.greengrass.provisioner.docker.EcrDockerHelper;
import com.awslabs.aws.greengrass.provisioner.docker.OfficialGreengrassImageDockerHelper;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.EcrDockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.OfficialGreengrassImageDockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.implementations.clientproviders.S3ClientProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.ExceptionHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.google.common.collect.ImmutableSet;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.typesafe.config.*;
import io.vavr.control.Try;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.awaitility.core.ConditionTimeoutException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.*;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iot.model.CreateRoleAliasResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.vavr.API.*;
import static io.vavr.Predicates.instanceOf;
import static org.awaitility.Awaitility.await;

public class BasicDeploymentHelper implements DeploymentHelper {
    private static final String USER_DIR = "user.dir";
    private static final String UBUNTU_AMI_ACCOUNT_ID = "099720109477";
    private static final String AWS_AMI_ACCOUNT_ID = "137112412989";
    private static final String X86_UBUNTU_18_04_LTS_AMI_FILTER = "ubuntu/images/hvm-ssd/ubuntu-bionic-18.04-amd64-server-??????????";
    private static final String ARM64_UBUNTU_18_04_LTS_AMI_FILTER = "ubuntu/images/hvm-ssd/ubuntu-bionic-18.04-arm64-server-??????????";
    private static final String X86_AMAZON_LINUX_2_AMI_FILTER = "amzn2-ami-hvm-2.0.????????-x86_64-gp2";
    private static final String ARM64_AMAZON_LINUX_2_AMI_FILTER = "amzn2-ami-hvm-2.0.????????-arm64-gp2";
    private static final String SSH_CONNECTED_MESSAGE = "Connected to host via SSH";
    private static final String SSH_TIMED_OUT_MESSAGE = "SSH connection timed out, instance may still be starting up...";
    private static final String SSH_CONNECTION_REFUSED_MESSAGE = "SSH connection refused, instance may still be starting up...";
    private static final String SSH_ERROR_MESSAGE = "There was an SSH error [{}]";
    private static final String DOES_NOT_EXIST = "does not exist";
    private static final String SCREEN_NOT_AVAILABLE_ERROR_MESSAGE = "screen is not available on the host. Screen must be available to use this feature";
    private static final String SCREEN_SESSION_NAME_IN_USE_ERROR_MESSAGE = "A screen session with the specified name already exists. Maybe Greengrass is already running on this host. If so, connect to the system and close the screen session before trying again.";
    private static final String EMPTY = "EMPTY";
    private final Logger log = LoggerFactory.getLogger(BasicDeploymentHelper.class);
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
    EcrDockerHelper ecrDockerHelper;
    @Inject
    OfficialGreengrassImageDockerHelper officialGreengrassImageDockerHelper;
    @Inject
    EcrDockerClientProvider ecrDockerClientProvider;
    @Inject
    OfficialGreengrassImageDockerClientProvider officialGreengrassImageDockerClientProvider;
    @Inject
    BasicProgressHandler basicProgressHandler;
    @Inject
    Ec2Client ec2Client;
    @Inject
    ThreadHelper threadHelper;
    @Inject
    DeploymentArgumentHelper deploymentArgumentHelper;
    @Inject
    ExceptionHelper exceptionHelper;
    @Inject
    S3ClientProvider s3ClientProvider;
    @Inject
    JsonHelper jsonHelper;

    private Optional<List<VirtualTarEntry>> installScriptVirtualTarEntries = Optional.empty();
    private Optional<List<VirtualTarEntry>> oemVirtualTarEntries = Optional.empty();
    private Optional<List<VirtualTarEntry>> ggdVirtualTarEntries = Optional.empty();

    @Inject
    public BasicDeploymentHelper() {
    }

    @Override
    public DeploymentConf getDeploymentConf(String deploymentConfigFilename, String groupName) {
        File deploymentConfigFile = new File(deploymentConfigFilename);

        if (!deploymentConfigFile.exists()) {
            throw new RuntimeException("The specified deployment configuration file [" + deploymentConfigFilename + "] does not exist.");
        }

        Config config = ConfigFactory.parseFile(deploymentConfigFile)
                .withValue("ACCOUNT_ID", ConfigValueFactory.fromAnyRef(iamHelper.getAccountId()))
                .withFallback(getFallbackConfig())
                .resolve();

        return buildDeploymentConf(deploymentConfigFilename, config, groupName);
    }

    private DeploymentConf buildDeploymentConf(String deploymentConfigFilename, Config config, String groupName) {
        ImmutableDeploymentConf.Builder deploymentConfBuilder = ImmutableDeploymentConf.builder();

        String trimmedDeploymentName = deploymentConfigFilename.replaceAll(".conf$", "").replaceAll("^.*/", "");
        deploymentConfBuilder.name(trimmedDeploymentName);
        deploymentConfBuilder.functions(config.getStringList("conf.functions"));

        deploymentConfBuilder.groupName(groupName);

        try {
            deploymentConfBuilder.isSyncShadow(config.getBoolean("conf.core.syncShadow"));
        } catch (ConfigException.Missing e) {
            log.warn("No value specified for core syncShadow, defaulting to true");
            deploymentConfBuilder.isSyncShadow(true);
        }

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

    private void setEnvironmentVariables(ImmutableDeploymentConf.Builder deploymentConfBuilder, Config config) {
        Try.of(() -> innerSetEnvironmentVariables(deploymentConfBuilder, config))
                .recover(ConfigException.Missing.class, this::logNoEnvironmentVariablesForDeployment)
                .get();
    }

    private Void logNoEnvironmentVariablesForDeployment(Throwable throwable) {
        log.info("No environment variables specified in this deployment");

        return null;
    }

    private Void innerSetEnvironmentVariables(ImmutableDeploymentConf.Builder deploymentConfBuilder, Config config) {
        ConfigObject configObject = config.getObject("conf.environmentVariables");

        if (configObject.size() == 0) {
            log.info("- No environment variables specified for this deployment");
        }

        Config tempConfig = configObject.toConfig();

        for (Map.Entry<String, ConfigValue> configValueEntry : tempConfig.entrySet()) {
            deploymentConfBuilder.putEnvironmentVariables(configValueEntry.getKey(), String.valueOf(configValueEntry.getValue().unwrapped()));
        }

        return null;
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
     * @return
     */
    @Override
    public Void createAndWaitForDeployment(Optional<Role> greengrassServiceRole, Optional<Role> greengrassRole, String groupId, String groupVersionId) {
        log.info("Creating a deployment");
        log.info("Group ID [" + groupId + "]");
        log.info("Group version ID [" + groupVersionId + "]");
        String initialDeploymentId = greengrassHelper.createDeployment(groupId, groupVersionId);
        log.info("Deployment created [" + initialDeploymentId + "]");

        Optional<DeploymentStatus> optionalDeploymentStatus = threadHelper.timeLimitTask(getDeploymentStatusCallable(greengrassServiceRole, greengrassRole, groupId, groupVersionId, initialDeploymentId), 5, TimeUnit.MINUTES);

        if (!optionalDeploymentStatus.isPresent() || !optionalDeploymentStatus.get().equals(DeploymentStatus.SUCCESSFUL)) {
            throw new RuntimeException("Deployment failed");
        }

        log.info("Deployment successful");

        return null;
    }

    private Callable<DeploymentStatus> getDeploymentStatusCallable(Optional<Role> greengrassServiceRole, Optional<Role> greengrassRole, String groupId, String groupVersionId, String initialDeploymentId) {
        return () -> getDeploymentStatus(greengrassServiceRole, greengrassRole, groupId, groupVersionId, initialDeploymentId);
    }

    private DeploymentStatus getDeploymentStatus(Optional<Role> greengrassServiceRole, Optional<Role> greengrassRole, String groupId, String groupVersionId, String initialDeploymentId) {
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

                    ioHelper.sleep(30000);

                    // Reassociate roles
                    log.warn("Reassociating Greengrass service role");
                    associateServiceRoleToAccount(greengrassServiceRole.get());
                    log.warn("Reassociating Greengrass group role");
                    associateRoleToGroup(greengrassRole.get(), groupId);

                    log.warn("Letting IAM settle...");

                    ioHelper.sleep(30000);

                    log.warn("Trying another deployment");
                    deploymentId = greengrassHelper.createDeployment(groupId, groupVersionId);
                    log.warn("Deployment created [" + deploymentId + "]");

                    log.warn("Letting deployment settle...");

                    ioHelper.sleep(30000);
                } else {
                    log.error("Deployment failed due to IAM issue.");
                    return DeploymentStatus.FAILED;
                }
            } else if (deploymentStatus.equals(DeploymentStatus.BUILDING)) {
                ioHelper.sleep(5000);
            }

            if (deploymentStatus.equals(DeploymentStatus.FAILED) ||
                    deploymentStatus.equals(DeploymentStatus.SUCCESSFUL)) {
                return deploymentStatus;
            }
        }
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
    public Void execute(DeploymentArguments deploymentArguments) {
        // Make the directories for build, if necessary
        ioHelper.createDirectoryIfNecessary(ggConstants.getBuildDirectory());

        ///////////////////////////////////////
        // Load the deployment configuration //
        ///////////////////////////////////////

        DeploymentConf deploymentConf;

        if (isEmptyDeployment(deploymentArguments)) {
            deploymentConf = getEmptyDeploymentConf(deploymentArguments);
        } else {
            deploymentConf = Try.of(() -> getDeploymentConf(deploymentArguments.deploymentConfigFilename, deploymentArguments.groupName))
                    .get();
        }

        // Create the service role and role alias, if necessary
        Optional<Role> optionalGreengrassServiceRole;
        Optional<CreateRoleAliasResponse> optionalCreateRoleAliasResponse = Optional.empty();

        if (!deploymentArguments.serviceRoleExists) {
            // If the service role does not exist we should create it
            Role greengrassServiceRole = createServiceRole(deploymentConf);
            optionalCreateRoleAliasResponse = Optional.of(iotHelper.createRoleAliasIfNecessary(greengrassServiceRole, GREENGRASS_SERVICE_ROLE_NAME));
            optionalGreengrassServiceRole = Optional.of(greengrassServiceRole);
        } else {
            // The service role exists already, do not try to create or modify it
            optionalGreengrassServiceRole = Optional.empty();
        }

        // Create the role for the core, if necessary
        Role greengrassRole;

        if (deploymentArguments.coreRoleName != null) {
            Optional<Role> optionalGreengrassRole = iamHelper.getRole(deploymentArguments.coreRoleName);

            if (!optionalGreengrassRole.isPresent()) {
                throw new RuntimeException("Greengrass core role is not present or GetRole failed due to insufficient permissions on [" + deploymentArguments.coreRoleName + "]");
            }

            greengrassRole = optionalGreengrassRole.get();
        } else {
            greengrassRole = createGreengrassRole(deploymentConf);
        }

        ///////////////////////////////////////////////////
        // Create an AWS Greengrass Group and get its ID //
        ///////////////////////////////////////////////////

        if (greengrassHelper.groupExists(deploymentArguments.groupName) && (deploymentArguments.ec2LinuxVersion != null)) {
            throw new RuntimeException("Group [" + deploymentArguments.groupName + "] already exists, cannot launch another EC2 instance for this group.  You can update the group configuration by not specifying the EC2 launch option.");
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

        log.info("Getting keys and certificate for core thing [" + groupId + "]");
        Optional<GroupVersion> optionalGroupVersion = greengrassHelper.getLatestGroupVersion(groupId);
        String coreCertificateArn;
        Optional<KeysAndCertificate> optionalCoreKeysAndCertificate = Optional.empty();

        if (!optionalGroupVersion.isPresent() || deploymentArguments.forceCreateNewKeysOption) {
            // Only create new keys if it is a new group or if the use has requested to force new keys to be created
            KeysAndCertificate coreKeysAndCertificate = iotHelper.createOrLoadKeysAndCertificate(groupId, CORE_SUB_NAME);
            optionalCoreKeysAndCertificate = Optional.of(coreKeysAndCertificate);
            coreCertificateArn = coreKeysAndCertificate.getCertificateArn();
        } else {
            Optional<String> optionalCoreCertificateArn = greengrassHelper.getCoreCertificateArn(groupId);

            if (!optionalCoreCertificateArn.isPresent()) {
                throw new RuntimeException("Core certificate ARN could not be found. If you would like to recreate the keys you must specify the [" + DeploymentArguments.LONG_FORCE_CREATE_NEW_KEYS_OPTION + "] option.");
            }

            coreCertificateArn = optionalCoreCertificateArn.get();
        }

        ////////////////////////////////////////////////
        // Policy creation for the core, if necessary //
        ////////////////////////////////////////////////

        String corePolicyName;

        if (deploymentArguments.corePolicyName == null) {
            log.info("Creating policy for core");
            iotHelper.createPolicyIfNecessary(ggVariables.getCorePolicyName(deploymentArguments.groupName), deploymentConf.getCorePolicy());
            corePolicyName = ggVariables.getCorePolicyName(deploymentArguments.groupName);
        } else {
            corePolicyName = deploymentArguments.corePolicyName;
        }

        //////////////////////////////////
        // Attach policy to certificate //
        //////////////////////////////////

        iotHelper.attachPrincipalPolicy(corePolicyName, coreCertificateArn);

        /////////////////////////////////
        // Attach thing to certificate //
        /////////////////////////////////

        iotHelper.attachThingPrincipal(ggVariables.getCoreThingName(deploymentArguments.groupName), coreCertificateArn);

        ////////////////////////////////////////////////
        // Associate the Greengrass role to the group //
        ////////////////////////////////////////////////

        associateRoleToGroup(greengrassRole, groupId);

        ////////////////////////////////////////////
        // Create a core definition and a version //
        ////////////////////////////////////////////

        log.info("Creating core definition");
        String coreDefinitionVersionArn = greengrassHelper.createCoreDefinitionAndVersion(ggVariables.getCoreDefinitionName(deploymentArguments.groupName), coreCertificateArn, coreThingArn, deploymentConf.isSyncShadow());

        //////////////////////////////////////////////
        // Create a logger definition and a version //
        //////////////////////////////////////////////

        log.info("Creating logger definition");
        String loggerDefinitionVersionArn = greengrassHelper.createDefaultLoggerDefinitionAndVersion();

        //////////////////////////////////////////////
        // Create the Lambda role for the functions //
        //////////////////////////////////////////////

        Optional<Role> optionalLambdaRole = Optional.empty();

        if (!deploymentConf.getFunctions().isEmpty()) {
            log.info("Creating Lambda role");

            if (!deploymentConf.getLambdaRoleName().isPresent()) {
                throw new RuntimeException("Lambda role name not specified");
            }

            if (!deploymentConf.getLambdaRoleAssumeRolePolicy().isPresent()) {
                throw new RuntimeException("Lambda assume role policy not specified");
            }

            optionalLambdaRole = Optional.of(iamHelper.createRoleIfNecessary(deploymentConf.getLambdaRoleName().get(), deploymentConf.getLambdaRoleAssumeRolePolicy().get()));
        }

        ////////////////////////////////////////////////////////
        // Start building the subscription and function lists //
        ////////////////////////////////////////////////////////

        List<Subscription> subscriptions = new ArrayList<>();

        ///////////////////////////////////////////////////
        // Determine the default function isolation mode //
        ///////////////////////////////////////////////////

        FunctionIsolationMode defaultFunctionIsolationMode;

        if (isEmptyDeployment(deploymentArguments)) {
            // If we're doing an empty deployment default to no container mode
            defaultFunctionIsolationMode = FunctionIsolationMode.NO_CONTAINER;
        } else if ((deploymentArguments.dockerLaunch) || (deploymentArguments.buildContainer)) {
            // If we're doing a Docker launch we always use no container
            log.warn("Setting default function isolation mode to no container because we're doing a Docker launch");
            defaultFunctionIsolationMode = FunctionIsolationMode.NO_CONTAINER;
        } else {
            // If we're not doing a Docker launch use the default values in the configuration file
            defaultFunctionIsolationMode = ggVariables.getDefaultFunctionIsolationMode();
        }

        ///////////////////////////////////////////////////
        // Find enabled functions and their mapping info //
        ///////////////////////////////////////////////////

        Map<String, String> defaultEnvironment = environmentHelper.getDefaultEnvironment(groupId, coreThingName, coreThingArn, deploymentArguments.groupName);

        List<ModifiableFunctionConf> functionConfs = functionHelper.getFunctionConfObjects(defaultEnvironment, deploymentConf, defaultFunctionIsolationMode);

        // Find Python functions that may not have had their language updated, this should never happen
        Predicate<FunctionConf> legacyPythonPredicate = functionConf -> functionConf.getLanguage().equals(Language.Python);

        List<FunctionConf> legacyPythonFunctions = functionConfs.stream()
                .filter(legacyPythonPredicate)
                .collect(Collectors.toList());

        if (legacyPythonFunctions.size() != 0) {
            log.error("Some Python functions do not have a Python version specified, this should never happen!");
            legacyPythonFunctions.stream()
                    .map(FunctionConf::getFunctionName)
                    .forEach(log::error);
            throw new UnsupportedOperationException();
        }

        // Find Node functions that may not have had their language updated, this should never happen
        Predicate<FunctionConf> legacyNodePredicate = functionConf -> functionConf.getLanguage().equals(Language.Node);

        List<FunctionConf> legacyNodeFunctions = functionConfs.stream()
                .filter(legacyNodePredicate)
                .collect(Collectors.toList());

        if (legacyNodeFunctions.size() != 0) {
            log.error("Some Node functions do not have a Node version specified, this should never happen!");
            legacyNodeFunctions.stream()
                    .map(FunctionConf::getFunctionName)
                    .forEach(log::error);
            throw new UnsupportedOperationException();
        }

        // Find Java functions that may not have had their language updated, this should never happen
        Predicate<FunctionConf> legacyJavaPredicate = functionConf -> functionConf.getLanguage().equals(Language.Java);

        List<FunctionConf> legacyJavaFunctions = functionConfs.stream()
                .filter(legacyJavaPredicate)
                .collect(Collectors.toList());

        if (legacyJavaFunctions.size() != 0) {
            log.error("Some Java functions do not have a Java version specified, this should never happen!");
            legacyJavaFunctions.stream()
                    .map(FunctionConf::getFunctionName)
                    .forEach(log::error);
            throw new UnsupportedOperationException();
        }

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
                .filter(FunctionConf::isGreengrassContainer)
                .map(FunctionConf::getFunctionName)
                .collect(Collectors.toList());

        /////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Check if launching or building a Docker container and functions are running in the Greengrass container //
        /////////////////////////////////////////////////////////////////////////////////////////////////////////////

        if ((deploymentArguments.dockerLaunch || deploymentArguments.buildContainer) && !functionsRunningInGreengrassContainer.isEmpty()) {
            log.error("The following functions are marked to run in the Greengrass container:");

            functionsRunningInGreengrassContainer
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

        Map<Function, ModifiableFunctionConf> functionToConfMap = new HashMap<>();

        // Only try to create functions if we have a Lambda role
        if (optionalLambdaRole.isPresent()) {
            Role lambdaRole = optionalLambdaRole.get();

            // Get a list of the buildable functions
            List<BuildableFunction> buildableFunctions = functionHelper.getBuildableFunctions(functionConfs, lambdaRole);

            // Install Java dependencies if necessary
            buildableFunctions.stream()
                    .filter(buildableFunction -> buildableFunction instanceof BuildableJavaMavenFunction)
                    .findFirst()
                    .ifPresent(buildableFunction -> functionHelper.installJavaDependencies());

            // Get the map of functions to function configuration (builds functions and publishes them to Lambda)
            functionToConfMap = functionHelper.buildFunctionsAndGenerateMap(buildableFunctions);
        }

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
        String functionDefinitionVersionArn = greengrassHelper.createFunctionDefinitionVersion(ImmutableSet.copyOf(functionToConfMap.keySet()), defaultFunctionIsolationMode);

        //////////////////////////////////////////////////
        // Create all of the things from the GGD config //
        //////////////////////////////////////////////////

        Set<String> thingNames = ggdConfs.stream().map(GGDConf::getThingName).collect(Collectors.toSet());

        if (thingNames.size() > 0) {
            Set<String> thingArns = new HashSet<>();

            log.info("Creating Greengrass device things");

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
                optionalCreateRoleAliasResponse,
                groupId,
                coreThingName,
                coreThingArn,
                optionalCoreKeysAndCertificate,
                ggdConfs,
                thingNames,
                ggdPipDependencies,
                functionsRunningAsRoot);

        //////////////////////////////////////////////////
        // Start building the EC2 instance if necessary //
        //////////////////////////////////////////////////

        Optional<String> optionalInstanceId = Optional.empty();

        if (deploymentArguments.ec2LinuxVersion != null) {
            log.info("Launching EC2 instance");
            optionalInstanceId = launchEc2Instance(deploymentArguments.groupName, deploymentArguments.architecture, deploymentArguments.ec2LinuxVersion);

            if (!optionalInstanceId.isPresent()) {
                // Something went wrong, bail out
                throw new RuntimeException("Couldn't obtain EC2 instance ID, bailing out");
            }
        }

        ///////////////////////////////////////////////////
        // Start the Docker container build if necessary //
        ///////////////////////////////////////////////////

        if (deploymentArguments.buildContainer) {
            log.info("Configuring container build");

            ecrDockerHelper.setEcrRepositoryName(Optional.ofNullable(deploymentArguments.ecrRepositoryNameString));
            ecrDockerHelper.setEcrImageName(Optional.ofNullable(deploymentArguments.ecrImageNameString));
            String imageName = ecrDockerHelper.getImageName();
            String currentDirectory = System.getProperty(USER_DIR);

            File dockerfile = officialGreengrassImageDockerHelper.getDockerfileForArchitecture(deploymentArguments.architecture);
            String dockerfileTemplate = ioHelper.readFileAsString(dockerfile);
            dockerfileTemplate = dockerfileTemplate.replaceAll("GROUP_NAME", deploymentArguments.groupName);

            // Add the group name and UUID so we don't accidentally overwrite an existing file
            File tempDockerfile = dockerfile.toPath().getParent().resolve(
                    String.join(".", "Dockerfile", deploymentArguments.groupName, ioHelper.getUuid())).toFile();
            ioHelper.writeFile(tempDockerfile.toString(), dockerfileTemplate.getBytes());
            tempDockerfile.deleteOnExit();

            try (DockerClient dockerClient = officialGreengrassImageDockerClientProvider.get()) {
                log.info("Building container");

                String imageId = dockerClient.build(new File(currentDirectory).toPath(),
                        basicProgressHandler,
                        DockerClient.BuildParam.dockerfile(tempDockerfile.toPath()));

                dockerClient.tag(imageId, imageName);
                pushContainerIfNecessary(deploymentArguments, imageId);
            } catch (DockerException | InterruptedException | IOException e) {
                log.error("Container build failed");
                throw new RuntimeException(e);
            }
        }

        // Create a deployment and wait for it to succeed.  Return if it fails.
        Try.of(() -> createAndWaitForDeployment(optionalGreengrassServiceRole, Optional.of(greengrassRole), groupId, groupVersionId))
                .get();

        //////////////////////////////////////////////
        // Launch the Docker container if necessary //
        //////////////////////////////////////////////

        if (deploymentArguments.dockerLaunch) {
            log.info("Launching Docker container");
            String officialGreengrassDockerImage = ggConstants.getOfficialGreengrassDockerImage();
            officialGreengrassImageDockerHelper.pullImage(officialGreengrassDockerImage);
            officialGreengrassImageDockerHelper.createAndStartContainer(officialGreengrassDockerImage, deploymentArguments.groupName);
        }

        ///////////////////////////////////////////////////////
        // Wait for the EC2 instance to launch, if necessary //
        ///////////////////////////////////////////////////////

        if (optionalInstanceId.isPresent()) {
            String instanceId = optionalInstanceId.get();

            DescribeInstancesRequest describeInstancesRequest = DescribeInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();

            // Describe instances retry policy
            RetryPolicy<DescribeInstancesResponse> describeInstancesRetryPolicy = new RetryPolicy<DescribeInstancesResponse>()
                    .handleIf(throwable -> throwable.getMessage().contains(DOES_NOT_EXIST))
                    .withDelay(Duration.ofSeconds(5))
                    .withMaxRetries(3)
                    .onRetry(failure -> log.warn("Waiting for the instance to become visible..."))
                    .onRetriesExceeded(failure -> log.error("Instance never became visible. Cannot continue."));

            DescribeInstancesResponse describeInstancesResponse = Failsafe.with(describeInstancesRetryPolicy).get(() ->
                    ec2Client.describeInstances(describeInstancesRequest));

            Optional<Reservation> optionalReservation = describeInstancesResponse.reservations().stream().findFirst();

            if (!optionalReservation.isPresent()) {
                throw new RuntimeException("Error finding the EC2 reservation to wait for the instance to finish launching, this should never happen");
            }

            Reservation reservation = optionalReservation.get();

            Optional<Instance> optionalInstance = reservation.instances().stream().findFirst();

            if (!optionalInstance.isPresent()) {
                throw new RuntimeException("Error finding the EC2 instance to wait for it to finish launching, this should never happen");
            }

            Instance instance = optionalInstance.get();

            String publicIpAddress = instance.publicIpAddress();

            if (publicIpAddress == null) {
                throw new RuntimeException("Public IP address returned from EC2 was NULL, skipping EC2 setup");
            }

            Optional<String> username = Optional.empty();

            if (deploymentArguments.ec2LinuxVersion.equals(EC2LinuxVersion.Ubuntu1804)) {
                username = Optional.of("ubuntu");
            }

            if (deploymentArguments.ec2LinuxVersion.equals(EC2LinuxVersion.AmazonLinux2)) {
                username = Optional.of("ec2-user");
            }

            if (!username.isPresent()) {
                throw new RuntimeException("Unexpected EC2 Linux version requested [" + deploymentArguments.ec2LinuxVersion + "], this is a bug 2 [couldn't determine SSH username]");
            }

            attemptBootstrap(deploymentArguments, publicIpAddress, username.get());
        }

        ///////////////////////////////////////////
        // Launch a non-EC2 system, if necessary //
        ///////////////////////////////////////////

        if (deploymentArguments.launch != null) {
            attemptBootstrap(deploymentArguments, deploymentArguments.launchHost, deploymentArguments.launchUser);
        }

        //////////////////////////////////////////////////////////////////////////
        // Wait for the CloudFormation stacks to finish launching, if necessary //
        //////////////////////////////////////////////////////////////////////////

        if (cloudFormationStacksLaunched.size() != 0) {
            waitForStacksToLaunch(cloudFormationStacksLaunched);
        }

        return null;
    }

    public boolean isEmptyDeployment(DeploymentArguments deploymentArguments) {
        return deploymentArguments.deploymentConfigFilename.equals(EMPTY);
    }

    private DeploymentConf getEmptyDeploymentConf(DeploymentArguments deploymentArguments) {
        return ImmutableDeploymentConf.builder()
                .isSyncShadow(true)
                .name(EMPTY)
                .groupName(deploymentArguments.groupName)
                .coreRoleName(deploymentArguments.coreRoleName)
                .corePolicy(deploymentArguments.corePolicyName)
                .functions(new ArrayList<>())
                .build();
    }

    private void attemptBootstrap(DeploymentArguments deploymentArguments, String ipAddress, String user) {
        Optional<Session> optionalSession = threadHelper.timeLimitTask(
                ioHelper.getSshSessionTask(ipAddress,
                        user,
                        SSH_CONNECTED_MESSAGE,
                        SSH_TIMED_OUT_MESSAGE,
                        SSH_CONNECTION_REFUSED_MESSAGE,
                        SSH_ERROR_MESSAGE), 2, TimeUnit.MINUTES);

        if (!optionalSession.isPresent()) {
            throw new RuntimeException("Failed to connect and bootstrap the instance via SSH");
        }

        Session session = optionalSession.get();

        threadHelper.timeLimitTask(getCopyAndBootstrapCallable(deploymentArguments, ipAddress, user, session), 5, TimeUnit.MINUTES);
    }

    @Override
    public ArgumentHelper<DeploymentArguments> getArgumentHelper() {
        return deploymentArgumentHelper;
    }

    @Override
    public DeploymentArguments getArguments() {
        return new DeploymentArguments();
    }

    private Callable<Boolean> getCopyAndBootstrapCallable(DeploymentArguments deploymentArguments, String publicIpAddress, String user, Session session) {
        return () -> copyAndBootstrap(deploymentArguments, publicIpAddress, user, session);
    }

    private Boolean copyAndBootstrap(DeploymentArguments deploymentArguments, String host, String user, Session session) throws JSchException, IOException {
        String filename = String.join(".", "gg", deploymentArguments.groupName, "sh");
        String localFilename = String.join("/", "build", filename);
        String remoteFilename = filename;
        log.info("Copying bootstrap script to host via scp...");
        ioHelper.sendFile(session, localFilename, remoteFilename);
        ioHelper.runCommand(session, String.join(" ", "chmod", "+x", "./" + remoteFilename));
        log.info("Running bootstrap script on host in screen, connect to the instance [" + user + "@" + host + "] and run 'screen -r' to see the progress");
        runCommandInScreen(session, String.join(" ", "./" + remoteFilename, "--now"), Optional.of("greengrass"), true);
        session.disconnect();
        return true;
    }

    private void runCommandInScreen(Session session, String command, Optional<String> screenSessionName, boolean keepSessionOpen) throws JSchException, IOException {
        AtomicBoolean screenAvailable = new AtomicBoolean(false);

        Consumer<String> screenAvailabilityChecker = getScreenAvailabilityChecker(screenAvailable);

        ioHelper.runCommand(session, String.join(" ", "screen", "--version"), Optional.of(screenAvailabilityChecker));

        // TODO: This wait could be removed with better response handling
        waitForFlagToToggle(screenAvailable, SCREEN_NOT_AVAILABLE_ERROR_MESSAGE);

        String sessionNameOptions = "";

        if (screenSessionName.isPresent()) {
            AtomicBoolean screenSessionNameAvailable = new AtomicBoolean(false);

            Consumer<String> screenSessionNameChecker = getScreenSessionNameChecker(screenSessionNameAvailable);

            sessionNameOptions = String.join(" ", "-S", screenSessionName.get());

            ioHelper.runCommand(session, String.join(" ", "screen", sessionNameOptions, "-Q", "select", "."), Optional.of(screenSessionNameChecker));

            // TODO: This wait could be removed with better response handling
            waitForFlagToToggle(screenSessionNameAvailable, SCREEN_SESSION_NAME_IN_USE_ERROR_MESSAGE);
        }

        if (keepSessionOpen) {
            command = "bash -c \"" + command + "; exec bash\"";
        }

        ioHelper.runCommand(session, String.join(" ", "screen", sessionNameOptions, "-d", "-m", command));
    }

    @NotNull
    private Consumer<String> getScreenAvailabilityChecker(AtomicBoolean flag) {
        return string -> {
            // "screen --version" returns a string like: Screen version 4.05.00 (GNU) 10-Dec-16
            if (!string.contains("Screen")) {
                // Doesn't look like what we want
                return;
            }

            // This should be it
            flag.set(true);
        };
    }

    @NotNull
    private Consumer<String> getScreenSessionNameChecker(AtomicBoolean flag) {
        return string -> {
            // "screen -S session_name -Q select ." returns a string like: No screen session found
            if (!string.contains("No screen session found")) {
                // Doesn't look like what we want
                return;
            }

            // This should be it
            flag.set(true);
        };
    }

    private void waitForFlagToToggle(AtomicBoolean flag, String errorMessage) {
        Try.of(() -> waitForFlagToToggle(flag))
                .recover(ConditionTimeoutException.class, exception -> throwRuntimeException(errorMessage))
                .get();
    }

    private Void throwRuntimeException(String errorMessage) {
        throw new RuntimeException(errorMessage);
    }

    private Void waitForFlagToToggle(AtomicBoolean flag) {
        await()
                .atMost(org.awaitility.Duration.ONE_MINUTE)
                .until(flag::get);

        return null;
    }

    private Optional<String> launchEc2Instance(String groupName, Architecture architecture, EC2LinuxVersion ec2LinuxVersion) {
        String instanceTagName = String.join("-", "greengrass", groupName);

        Optional<String> optionalAccountId = getAccountId(ec2LinuxVersion);

        Optional<InstanceType> instanceType = getInstanceType(architecture);

        Optional<String> optionalNameFilter = getNameFilter(architecture, ec2LinuxVersion);

        if (!optionalAccountId.isPresent()) {
            throw new RuntimeException("Unexpected EC2 Linux version requested [" + ec2LinuxVersion + "], this is a bug 1 [couldn't determine which AMI to use]");
        }

        if (!optionalNameFilter.isPresent() || !instanceType.isPresent()) {
            throw new RuntimeException("Unexpected architecture [" + architecture + "] for EC2 launch");
        }

        Optional<Image> optionalImage = getImage(optionalNameFilter.get(), optionalAccountId.get());

        if (!optionalImage.isPresent()) {
            log.error("No [" + ec2LinuxVersion + "] image found in this region, not launching the instance");
            return Optional.empty();
        }

        DescribeKeyPairsResponse describeKeyPairsResponse = ec2Client.describeKeyPairs();

        // Find the first keypair
        Optional<KeyPairInfo> optionalKeyPairInfo = describeKeyPairsResponse.keyPairs().stream().min(Comparator.comparing(KeyPairInfo::keyName));

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

        // Sometimes the security group isn't immediately visible so we need retries
        RetryPolicy<AuthorizeSecurityGroupIngressResponse> securityGroupRetryPolicy = new RetryPolicy<AuthorizeSecurityGroupIngressResponse>()
                .handleIf(throwable -> throwable.getMessage().contains(DOES_NOT_EXIST))
                .withDelay(Duration.ofSeconds(5))
                .withMaxRetries(6)
                .onRetry(failure -> log.warn("Waiting for security group to become visible..."))
                .onRetriesExceeded(failure -> log.error("Security group never became visible. Cannot continue."));

        AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest = AuthorizeSecurityGroupIngressRequest.builder()
                .groupName(securityGroupName)
                .ipPermissions(sshPermission)
                .build();

        Failsafe.with(securityGroupRetryPolicy).get(() ->
                ec2Client.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest));

        RunInstancesRequest runInstancesRequest = RunInstancesRequest.builder()
                .imageId(image.imageId())
                .instanceType(instanceType.get())
                .maxCount(1)
                .minCount(1)
                .keyName(keyPairInfo.keyName())
                .securityGroups(securityGroupName)
                .build();

        RunInstancesResponse runInstancesResponse = ec2Client.runInstances(runInstancesRequest);

        Optional<String> optionalInstanceId = runInstancesResponse.instances().stream().findFirst().map(Instance::instanceId);

        if (!optionalInstanceId.isPresent()) {
            log.error("Couldn't find an instance ID, this should never happen, not launching the instance");
            return Optional.empty();
        }

        String instanceId = optionalInstanceId.get();

        Tag tag = Tag.builder()
                .key("Name")
                .value(instanceTagName)
                .build();

        CreateTagsRequest createTagsRequest = CreateTagsRequest.builder()
                .tags(tag)
                .resources(instanceId)
                .build();

        RetryPolicy<CreateTagsResponse> createTagsResponseRetryPolicy = new RetryPolicy<CreateTagsResponse>()
                .handleIf(throwable -> throwable.getMessage().contains(DOES_NOT_EXIST))
                .withDelay(Duration.ofSeconds(5))
                .withMaxRetries(3)
                .onRetry(failure -> log.warn("Instance may still be starting, trying again..."))
                .onRetriesExceeded(failure -> log.error("Failed to find the instance in EC2, it was not launched"));

        Failsafe.with(createTagsResponseRetryPolicy).get(() ->
                ec2Client.createTags(createTagsRequest));

        log.info("Launched instance [" + instanceId + "] with tag [" + instanceTagName + "]");

        return Optional.of(instanceId);
    }

    @NotNull
    public Optional<Image> getImage(String nameFilter, String accountId) {
        DescribeImagesRequest describeImagesRequest = DescribeImagesRequest.builder()
                .owners(accountId)
                .filters(Filter.builder().name("name").values(nameFilter).build(),
                        Filter.builder().name("state").values("available").build())
                .build();

        DescribeImagesResponse describeImagesResponse = ec2Client.describeImages(describeImagesRequest);

        return describeImagesResponse.images()
                .stream()
                .sorted(Comparator.comparing(Image::creationDate).reversed())
                .findFirst();
    }

    public Optional<String> getNameFilter(Architecture architecture, EC2LinuxVersion ec2LinuxVersion) {
        Optional<String> nameFilter = Optional.empty();

        if (ec2LinuxVersion.equals(EC2LinuxVersion.Ubuntu1804)) {
            if (architecture.equals(Architecture.X86_64)) {
                nameFilter = Optional.of(X86_UBUNTU_18_04_LTS_AMI_FILTER);
            } else if (architecture.equals(Architecture.ARM64)) {
                nameFilter = Optional.of(ARM64_UBUNTU_18_04_LTS_AMI_FILTER);
            }
        } else if (ec2LinuxVersion.equals(EC2LinuxVersion.AmazonLinux2)) {
            if (architecture.equals(Architecture.X86_64)) {
                nameFilter = Optional.of(X86_AMAZON_LINUX_2_AMI_FILTER);
            } else if (architecture.equals(Architecture.ARM64)) {
                nameFilter = Optional.of(ARM64_AMAZON_LINUX_2_AMI_FILTER);
            }
        }

        return nameFilter;
    }

    public Optional<InstanceType> getInstanceType(Architecture architecture) {
        Optional<InstanceType> instanceType = Optional.empty();

        if (architecture.equals(Architecture.X86_64)) {
            instanceType = Optional.of(InstanceType.T2_MICRO);
        } else if (architecture.equals(Architecture.ARM64)) {
            instanceType = Optional.of(InstanceType.A1_MEDIUM);
        }

        return instanceType;
    }

    public Optional<String> getAccountId(EC2LinuxVersion ec2LinuxVersion) {
        Optional<String> accountId = Optional.empty();

        if (ec2LinuxVersion.equals(EC2LinuxVersion.Ubuntu1804)) {
            accountId = Optional.of(UBUNTU_AMI_ACCOUNT_ID);
        } else if (ec2LinuxVersion.equals(EC2LinuxVersion.AmazonLinux2)) {
            accountId = Optional.of(AWS_AMI_ACCOUNT_ID);
        }

        return accountId;
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
        log.info("Creating Greengrass service role [" + GREENGRASS_SERVICE_ROLE_NAME + "]");

        if (!deploymentConf.getCoreRoleAssumeRolePolicy().isPresent()) {
            throw new RuntimeException("Core assume role policy not specified when creating service role");
        }

        Role greengrassServiceRole = iamHelper.createRoleIfNecessary(GREENGRASS_SERVICE_ROLE_NAME, deploymentConf.getCoreRoleAssumeRolePolicy().get());

        serviceRolePolicies
                .forEach(policy -> iamHelper.attachRolePolicy(greengrassServiceRole, policy));

        associateServiceRoleToAccount(greengrassServiceRole);
        return greengrassServiceRole;
    }

    private void buildOutputFiles(DeploymentArguments deploymentArguments, Optional<CreateRoleAliasResponse> optionalCreateRoleAliasResponse, String groupId, String awsIotThingName, String awsIotThingArn, Optional<KeysAndCertificate> optionalCoreKeysAndCertificate, List<GGDConf> ggdConfs, Set<String> thingNames, Set<String> ggdPipDependencies, boolean functionsRunningAsRoot) {
        if (deploymentArguments.scriptOutput) {
            installScriptVirtualTarEntries = Optional.of(new ArrayList<>());
        }

        if ((deploymentArguments.oemOutput) || (deploymentArguments.oemJsonOutput != null)) {
            oemVirtualTarEntries = Optional.of(new ArrayList<>());
        }

        if (deploymentArguments.ggdOutput) {
            ggdVirtualTarEntries = Optional.of(new ArrayList<>());
        }

        if (!installScriptVirtualTarEntries.isPresent() &&
                !oemVirtualTarEntries.isPresent() &&
                !ggdVirtualTarEntries.isPresent()) {
            log.warn("Not building any output files.  No output files specified (script, OEM, or GGD)");
            return;
        }

        if (!optionalCoreKeysAndCertificate.isPresent()) {
            throw new RuntimeException("Could not find the core keys and certificate, cannot build a complete output file. Specify the [" + DeploymentArguments.LONG_FORCE_CREATE_NEW_KEYS_OPTION + "] option if you need to regenerate the output files.");
        }

        KeysAndCertificate coreKeysAndCertificate = optionalCoreKeysAndCertificate.get();

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

        // Only create these files if we know the role alias
        if (optionalCreateRoleAliasResponse.isPresent()) {
            CreateRoleAliasResponse createRoleAliasResponse = optionalCreateRoleAliasResponse.get();

            oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "group-id.txt"), groupId.getBytes(), normalFilePermissions));
            oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "credential-provider-url.txt"), iotHelper.getCredentialProviderUrl().getBytes(), normalFilePermissions));
            oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "thing-name.txt"), awsIotThingName.getBytes(), normalFilePermissions));
            oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "role-alias-name.txt"), createRoleAliasResponse.roleAlias().getBytes(), normalFilePermissions));
            oemVirtualTarEntries.ifPresent(a -> archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "region.txt"), currentRegion.id().getBytes(), normalFilePermissions));
        }

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

            Try.of(() -> writePayload(ggdPipDependencies, ggScriptTemplate)).get();

            log.info("Writing script [" + ggShScriptName + "]");
            ioHelper.writeFile(ggShScriptName, ggScriptTemplate.toByteArray());
            ioHelper.makeExecutable(ggShScriptName);

            // Copy to S3 if necessary
            copyToS3IfNecessary(deploymentArguments.s3Bucket, deploymentArguments.s3Directory, ggShScriptName);
        }

        if (oemVirtualTarEntries.isPresent()) {
            if (deploymentArguments.oemJsonOutput != null) {
                writeOemJsonOutput(oemVirtualTarEntries.get(), deploymentArguments.oemJsonOutput);
            } else {
                String oemArchiveName = ggVariables.getOemArchiveName(deploymentArguments.groupName);
                log.info("Writing OEM file [" + oemArchiveName + "]");
                ioHelper.writeFile(oemArchiveName, getByteArrayOutputStream(oemVirtualTarEntries).get().toByteArray());
                ioHelper.makeExecutable(oemArchiveName);

                // Copy to S3 if necessary
                copyToS3IfNecessary(deploymentArguments.s3Bucket, deploymentArguments.s3Directory, oemArchiveName);
            }
        }

        if (ggdVirtualTarEntries.isPresent()) {
            String ggdArchiveName = ggVariables.getGgdArchiveName(deploymentArguments.groupName);
            log.info("Writing GGD file [" + ggdArchiveName + "]");
            ioHelper.writeFile(ggdArchiveName, getByteArrayOutputStream(ggdVirtualTarEntries).get().toByteArray());
            ioHelper.makeExecutable(ggdArchiveName);

            // Copy to S3 if necessary
            copyToS3IfNecessary(deploymentArguments.s3Bucket, deploymentArguments.s3Directory, ggdArchiveName);
        }
    }

    private void writeOemJsonOutput(List<VirtualTarEntry> oemVirtualTarEntries, String oemJsonFilename) {
        Map<String, String> oemJson = oemVirtualTarEntries.stream()
                .collect(Collectors.toMap(VirtualTarEntry::getFilename, entry -> new String(entry.getContent())));

        log.info("Writing OEM JSON output to [" + oemJsonFilename + "]");
        ioHelper.writeFile(oemJsonFilename, jsonHelper.toJson(oemJson).getBytes());
    }

    private void copyToS3IfNecessary(String s3Bucket, String s3Directory, String fileName) {
        if (s3Bucket == null) {
            return;
        }

        S3Client s3Client = s3ClientProvider.get();

        if (s3Directory.equals("/")) {
            // Clear out the S3 directory if it is just the root
            s3Directory = "";
        }

        File inputFile = new File(fileName);
        String s3FileName = inputFile.getName();

        // Put the key together from the path
        String key = String.join("/", s3Directory, s3FileName);

        if (key.startsWith("/")) {
            // If there's a leading slash remove it
            key = key.substring(1);
        }

        // Replace any accidental double slashes
        key = key.replaceAll("//", "/");

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Bucket)
                .key(key)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromFile(inputFile));
    }

    private Void writePayload(Set<String> ggdPipDependencies, ByteArrayOutputStream ggScriptTemplate) throws IOException {
        ggScriptTemplate.write(scriptHelper.generateGgScript(ggdPipDependencies).getBytes());
        ggScriptTemplate.write("PAYLOAD:\n".getBytes());
        ggScriptTemplate.write(getByteArrayOutputStream(installScriptVirtualTarEntries).get().toByteArray());

        return null;
    }

    private void pushContainerIfNecessary(DeploymentArguments deploymentArguments, String imageId) throws InterruptedException {
        if (!deploymentArguments.pushContainer) {
            return;
        }

        String ecrEndpoint = ecrDockerHelper.getEcrProxyEndpoint();
        ecrDockerHelper.createEcrRepositoryIfNecessary();
        String shortEcrEndpoint = ecrEndpoint.substring("https://".length()); // Remove leading https://
        String shortEcrEndpointAndRepo = String.join("/", shortEcrEndpoint, deploymentArguments.ecrRepositoryNameString);

        try (DockerClient dockerClient = ecrDockerClientProvider.get()) {
            Try.of(() -> tagImage(deploymentArguments, imageId, shortEcrEndpointAndRepo, dockerClient))
                    .get();

            Try.of(() -> push(shortEcrEndpointAndRepo, dockerClient))
                    .onFailure(throwable -> Match(throwable).of(
                            Case($(instanceOf(DockerException.class)), this::logDockerPushFailedAndThrow),
                            Case($(), exceptionHelper::rethrowAsRuntimeException)))
                    .get();
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

    private Void logDockerPushFailedAndThrow(DockerException x) {
        log.error("Docker push failed [" + x.getMessage() + "]");
        throw new RuntimeException(x);
    }

    private Void push(String shortEcrEndpointAndRepo, DockerClient dockerClient) throws DockerException, InterruptedException {
        dockerClient.push(shortEcrEndpointAndRepo, basicProgressHandler, ecrDockerClientProvider.getRegistryAuthSupplier().authFor(""));
        return null;
    }

    private Void tagImage(DeploymentArguments deploymentArguments, String imageId, String shortEcrEndpointAndRepo, DockerClient dockerClient) throws DockerException, InterruptedException {
        dockerClient.tag(imageId, String.join(":", shortEcrEndpointAndRepo, deploymentArguments.groupName));
        return null;
    }

    private void waitForStacksToLaunch(List<String> cloudFormationStacksLaunched) {
        log.info("Waiting for your stacks to launch...");

        cloudFormationStacksLaunched
                .forEach(cloudFormationHelper::waitForStackToLaunch);
    }

    private Optional<ByteArrayOutputStream> getByteArrayOutputStream
            (Optional<List<VirtualTarEntry>> virtualTarEntries) {
        return Try.of(() -> archiveHelper.tar(virtualTarEntries))
                .get();
    }

    /**
     * Create IAM resources and configuration required for Greengrass
     *
     * @param deploymentConf
     * @return
     */
    private Role createGreengrassRole(DeploymentConf deploymentConf) {
        log.info("Creating Greengrass role [" + deploymentConf.getCoreRoleName() + "]");

        if (!deploymentConf.getCoreRoleAssumeRolePolicy().isPresent()) {
            throw new RuntimeException("Core assume role policy not specified when creating Greengrass role");
        }

        Role greengrassRole = iamHelper.createRoleIfNecessary(deploymentConf.getCoreRoleName(), deploymentConf.getCoreRoleAssumeRolePolicy().get());

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
        Optional<URL> architectureUrlOptional = architecture.flatMap(Architecture::getResourceUrl);

        if (architecture.isPresent() && !architectureUrlOptional.isPresent()) {
            log.error("The GG software for your architecture [" + architecture.get().getFilename() + "] is not available, please download it from the Greengrass console and put it in the [" + architecture.get().getDIST() + "] directory");
            System.exit(3);
        }

        return architectureUrlOptional.get();
    }

}
