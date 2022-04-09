package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.*;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.arguments.HsiParameters;
import com.awslabs.aws.greengrass.provisioner.data.conf.*;
import com.awslabs.aws.greengrass.provisioner.data.exceptions.IamReassociationNecessaryException;
import com.awslabs.aws.greengrass.provisioner.docker.BasicProgressHandler;
import com.awslabs.aws.greengrass.provisioner.docker.EcrDockerHelper;
import com.awslabs.aws.greengrass.provisioner.docker.OfficialGreengrassImageDockerHelper;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.EcrDockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.OfficialGreengrassImageDockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.ExceptionHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.awslabs.general.helpers.interfaces.JsonHelper;
import com.awslabs.iam.data.*;
import com.awslabs.iam.helpers.interfaces.V2IamHelper;
import com.awslabs.iot.data.ImmutablePolicyDocument;
import com.awslabs.iot.data.ImmutablePolicyName;
import com.awslabs.iot.data.PolicyName;
import com.awslabs.iot.data.*;
import com.awslabs.iot.helpers.interfaces.V2GreengrassHelper;
import com.awslabs.iot.helpers.interfaces.V2IotHelper;
import com.awslabs.lambda.data.FunctionName;
import com.awslabs.lambda.data.ImmutableFunctionName;
import com.awslabs.s3.helpers.data.ImmutableS3Bucket;
import com.awslabs.s3.helpers.data.ImmutableS3Path;
import com.awslabs.s3.helpers.data.S3Bucket;
import com.awslabs.s3.helpers.data.S3Path;
import com.awslabs.s3.helpers.interfaces.V2S3Helper;
import com.google.common.collect.ImmutableSet;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.typesafe.config.*;
import io.vavr.control.Try;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.*;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iot.model.CreateRoleAliasResponse;
// import software.amazon.awssdk.services.s3.S3Client;

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
import java.util.stream.Stream;

import static io.vavr.API.*;
import static io.vavr.Predicates.instanceOf;

public class BasicDeploymentHelper implements DeploymentHelper {
    public static final IpRange ALL_IPS = IpRange.builder().cidrIp("0.0.0.0/0").build();
    public static final int SSH_PORT = 22;
    public static final int MOSH_START_PORT = 60000;
    public static final int MOSH_END_PORT = 61000;
    private static final String USER_DIR = "user.dir";
    private static final String UBUNTU_AMI_ACCOUNT_ID = "099720109477";
    private static final String AWS_AMI_ACCOUNT_ID = "137112412989";
    private static final String X86_UBUNTU_18_04_LTS_AMI_FILTER = "ubuntu/images/hvm-ssd/ubuntu-bionic-18.04-amd64-server-??????????";
    private static final String ARM64_UBUNTU_18_04_LTS_AMI_FILTER = "ubuntu/images/hvm-ssd/ubuntu-bionic-18.04-arm64-server-??????????";
    private static final String X86_AMAZON_LINUX_2_AMI_FILTER = "amzn2-ami-hvm-2.0.????????-x86_64-gp2";
    private static final String ARM64_AMAZON_LINUX_2_AMI_FILTER = "amzn2-ami-hvm-2.0.????????-arm64-gp2";
    private static final String DOES_NOT_EXIST = "does not exist";
    private static final String SCREEN_NOT_AVAILABLE_ERROR_MESSAGE = "screen is not available on the host. Screen must be available to use this feature";
    private static final String GREENGRASS_SESSION_NAME = "greengrass";
    private static final String SCREEN_SESSION_NAME_IN_USE_ERROR_MESSAGE = String.join("", "A screen session with the specified name [", GREENGRASS_SESSION_NAME, "] already exists. Maybe Greengrass is already running on this host. If so, connect to the system and close the screen session before trying again.");
    private static final String GREENGRASS_EC2_INSTANCE_TAG_PREFIX = "greengrass";
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
    V2IamHelper iamHelper;
    @Inject
    GreengrassHelper greengrassHelper;
    @Inject
    V2GreengrassHelper v2GreengrassHelper;
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
    EnvironmentHelper environmentHelper;
    @Inject
    IotHelper iotHelper;
    @Inject
    V2IotHelper v2IotHelper;
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
    // @Inject
    // S3Client s3Client;
    @Inject
    JsonHelper jsonHelper;
    @Inject
    SshHelper sshHelper;
    @Inject
    TypeSafeConfigHelper typeSafeConfigHelper;
    @Inject
    ConnectorHelper connectorHelper;
    @Inject
    V2S3Helper v2S3Helper;

    private Optional<List<VirtualTarEntry>> installScriptVirtualTarEntries = Optional.empty();
    private Optional<List<VirtualTarEntry>> oemVirtualTarEntries = Optional.empty();

    @Inject
    public BasicDeploymentHelper() {
    }

    @Override
    public DeploymentConf getDeploymentConf(ThingName coreThingName, DeploymentArguments deploymentArguments, GreengrassGroupName greengrassGroupName) {
        File deploymentConfigFile = new File(deploymentArguments.deploymentConfigFilename);

        if (!deploymentConfigFile.exists()) {
            throw new RuntimeException(String.join("", "The specified deployment configuration file [", deploymentArguments.deploymentConfigFilename, "] does not exist."));
        }

        Config config = ConfigFactory.parseFile(deploymentConfigFile)
                .withValue(EnvironmentHelper.ACCOUNT_ID, ConfigValueFactory.fromAnyRef(iamHelper.getAccountId().getId()))
                .withValue(EnvironmentHelper.REGION, ConfigValueFactory.fromAnyRef(awsHelper.getCurrentRegion().id()))
                .withValue(EnvironmentHelper.AWS_IOT_THING_NAME, ConfigValueFactory.fromAnyRef(coreThingName.getName()))
                .withFallback(getFallbackConfig(deploymentArguments.deploymentConfigFolderPath))
                .resolve();

        return buildDeploymentConf(deploymentArguments.deploymentConfigFilename, config, greengrassGroupName);
    }

    private DeploymentConf buildDeploymentConf(String deploymentConfigFilename, Config config, GreengrassGroupName greengrassGroupName) {
        ImmutableDeploymentConf.Builder deploymentConfBuilder = ImmutableDeploymentConf.builder();

        log.warn("No value specified for core syncShadow, defaulting to true");

        log.warn(String.join("", "***deploymentConfigFilename*** [", deploymentConfigFilename, "]"));
        String trimmedDeploymentName = deploymentConfigFilename.replaceAll(".conf$", "").replaceAll("^.*/", "");
        log.warn(String.join("", "***trimmedDeploymentName*** [", trimmedDeploymentName, "]"));
        deploymentConfBuilder.name(trimmedDeploymentName);
        List<FunctionName> functionNames = config.getStringList("conf.functions")
                .stream().map(name -> ImmutableFunctionName.builder().name(name).build())
                .collect(Collectors.toList());
        deploymentConfBuilder.functions(functionNames);

        deploymentConfBuilder.groupName(greengrassGroupName);

        try {
            deploymentConfBuilder.isSyncShadow(config.getBoolean("conf.core.syncShadow"));
        } catch (ConfigException.Missing e) {
            log.warn("No value specified for core syncShadow, defaulting to true");
            deploymentConfBuilder.isSyncShadow(true);
        }

        // Roles

        // Core role info
        deploymentConfBuilder.coreRoleConf(RoleConf.fromConfigAndPrefix(typeSafeConfigHelper, config, "conf.core"));

        // Lambda role info
        deploymentConfBuilder.lambdaRoleConf(RoleConf.fromConfigAndPrefix(typeSafeConfigHelper, config, "conf.lambda"));

        // Service role info
        deploymentConfBuilder.serviceRoleConf(RoleConf.fromConfigAndPrefix(typeSafeConfigHelper, config, "conf.service"));

        // Connector confs
        deploymentConfBuilder.connectors(config.getStringList("conf.connectors"));

        // Logger conf
        deploymentConfBuilder.loggers(getLoggers(typeSafeConfigHelper, config, "conf.loggers"));

        setEnvironmentVariables(deploymentConfBuilder, config);

        return deploymentConfBuilder.build();
    }

    private Optional<List<software.amazon.awssdk.services.greengrass.model.Logger>> getLoggers(TypeSafeConfigHelper typeSafeConfigHelper, Config config, String prefix) {
        String loggerPrefix = String.join(".", prefix);

        try {
            List<? extends ConfigObject> configObjects = config.getObjectList(loggerPrefix);

            return Optional.of(configObjects.stream()
                    .map(this::toLogger)
                    .collect(Collectors.toList()));
        } catch (ConfigException.Missing e) {
            log.warn("Logger configuration missing");

            return Optional.empty();
        }
    }

    private software.amazon.awssdk.services.greengrass.model.Logger toLogger(ConfigObject configObject) {
        Optional<String> componentString = Optional.ofNullable(configObject.get("component")).map(value -> value.render(ConfigRenderOptions.concise()));
        Optional<String> loggerLevelString = Optional.ofNullable(configObject.get("loggerLevel")).map(value -> value.render(ConfigRenderOptions.concise()));
        Optional<String> loggerTypeString = Optional.ofNullable(configObject.get("loggerType")).map(value -> value.render(ConfigRenderOptions.concise()));
        Optional<String> spaceString = Optional.ofNullable(configObject.get("space")).map(value -> value.render(ConfigRenderOptions.concise()));

        if (!componentString.isPresent()) {
            throw new RuntimeException("Component value missing in logger configuration, can not continue");
        }

        LoggerComponent loggerComponent = LoggerComponent.fromValue(removeQuotes(componentString.get()));

        if (loggerComponent == null) {
            throw new RuntimeException(String.join("", "Failed to parse logger component [", componentString.get(), "]"));
        }

        if (!loggerLevelString.isPresent()) {
            throw new RuntimeException("Logger level value missing in logger configuration, can not continue");
        }

        LoggerLevel loggerLevel = LoggerLevel.fromValue(removeQuotes(loggerLevelString.get()));

        if (loggerLevel == null) {
            throw new RuntimeException(String.join("", "Failed to parse logger level [", loggerLevelString.get(), "]"));
        }

        if (!loggerTypeString.isPresent()) {
            throw new RuntimeException("Logger type value missing in logger configuration, can not continue");
        }

        LoggerType loggerType = LoggerType.fromValue(removeQuotes(loggerTypeString.get()));

        if (loggerType == null) {
            throw new RuntimeException(String.join("", "Failed to parse logger type [", loggerTypeString.get(), "]"));
        }

        if (loggerType.equals(LoggerType.FILE_SYSTEM) && !spaceString.isPresent()) {
            throw new RuntimeException("Logger space value missing in logger configuration with a filesystem logger, can not continue");
        } else if (loggerType.equals(LoggerType.AWS_CLOUD_WATCH) && spaceString.isPresent()) {
            throw new RuntimeException("Logger space value present in logger configuration with a CloudWatch logger, can not continue");
        }

        Optional<Integer> optionalSpace = spaceString.map(this::removeQuotes).map(Integer::parseInt);

        software.amazon.awssdk.services.greengrass.model.Logger.Builder builder = software.amazon.awssdk.services.greengrass.model.Logger.builder();
        builder.id(ioHelper.getUuid());
        builder.component(loggerComponent);
        builder.level(loggerLevel);
        builder.type(loggerType);
        optionalSpace.ifPresent(builder::space);

        return builder.build();
    }

    private String removeQuotes(String input) {
        return input.replaceAll("\"", "");
    }

    private void setEnvironmentVariables(ImmutableDeploymentConf.Builder deploymentConfBuilder, Config config) {
        Try.run(() -> innerSetEnvironmentVariables(deploymentConfBuilder, config))
                .recover(ConfigException.Missing.class, this::logNoEnvironmentVariablesForDeployment)
                .get();
    }

    private Void logNoEnvironmentVariablesForDeployment(Throwable throwable) {
        log.info("No environment variables specified in this deployment");

        return null;
    }

    private void innerSetEnvironmentVariables(ImmutableDeploymentConf.Builder deploymentConfBuilder, Config config) {
        ConfigObject configObject = config.getObject("conf.environmentVariables");

        if (configObject.size() == 0) {
            log.info("- No environment variables specified for this deployment");
        }

        Config tempConfig = configObject.toConfig();

        for (Map.Entry<String, ConfigValue> configValueEntry : tempConfig.entrySet()) {
            deploymentConfBuilder.putEnvironmentVariables(configValueEntry.getKey(), String.valueOf(configValueEntry.getValue().unwrapped()));
        }
    }

    private Config getFallbackConfig(String deploymentConfigFilePath) {
        File deploymentDefaultsConf = new File(String.join("/", deploymentConfigFilePath, "deployment.defaults.conf"));
        return ConfigFactory.parseFile(deploymentDefaultsConf);
    }

    /**
     * Create a deployment and wait for its status to change //
     *
     * @param serviceRole
     * @param coreRole
     * @param greengrassGroupId
     * @param groupVersionId
     */
    @Override
    public void createAndWaitForDeployment(Optional<Role> serviceRole, Optional<Role> coreRole, GreengrassGroupId greengrassGroupId, String groupVersionId) {
        log.info("Creating a deployment");
        log.info(String.join("", "Group ID [", greengrassGroupId.getGroupId(), "]"));
        log.info(String.join("", "Group version ID [", groupVersionId, "]"));
        String initialDeploymentId = greengrassHelper.createDeployment(greengrassGroupId, groupVersionId);
        log.info(String.join("", "Deployment created [", initialDeploymentId, "]"));

        DeploymentStatus deploymentStatus = getDeploymentStatus(serviceRole, coreRole, greengrassGroupId, groupVersionId, initialDeploymentId);

        if (!DeploymentStatus.SUCCESSFUL.equals(deploymentStatus)) {
            // Not successful, throw an exception to bail out
            throw new RuntimeException("Deployment failed");
        }

        log.info("Deployment successful");
    }

    private DeploymentStatus getDeploymentStatus(Optional<Role> serviceRole, Optional<Role> coreRole, GreengrassGroupId greengrassGroupId, String groupVersionId, String initialDeploymentId) {
        // Using a StringBuilder here allows us to update the deployment ID if a redeployment is necessary
        StringBuilder deploymentId = new StringBuilder();
        deploymentId.append(initialDeploymentId);

        //////////////////////////////////////////////
        // Wait for the deployment status to change //
        //////////////////////////////////////////////

        RetryPolicy<DeploymentStatus> deploymentStatusRetryPolicy = new RetryPolicy<DeploymentStatus>()
                // If we need a redeployment we'll handle that up to three times
                .withMaxRetries(3)
                .handleIf(throwable -> requiresIamReassociation(throwable, deploymentId, serviceRole, coreRole, greengrassGroupId, groupVersionId));

        DeploymentStatus deploymentStatus = Failsafe.with(deploymentStatusRetryPolicy)
                .get(() -> greengrassHelper.waitForDeploymentStatusToChange(greengrassGroupId, deploymentId.toString()));

        return deploymentStatus;
    }

    private boolean requiresIamReassociation(Throwable throwable, StringBuilder deploymentId, Optional<Role> serviceRole, Optional<Role> coreRole, GreengrassGroupId greengrassGroupId, String groupVersionId) {
        // Is this the exception we expected?
        if (!(throwable instanceof IamReassociationNecessaryException)) {
            // No, ignore it
            return false;
        }

        // Do we have the roles necessary to attempt a reassociation?
        if (!serviceRole.isPresent() || !coreRole.isPresent()) {
            // No, ignore it
            return false;
        }

        // We have both roles, we can try to reassociate them
        String newDeploymentId = iamDisassociateReassociateAndLetSettle(serviceRole, coreRole, greengrassGroupId, groupVersionId);

        // Clear out the old deployment ID
        deploymentId.setLength(0);

        // Use the new deployment ID
        deploymentId.append(newDeploymentId);

        // Tell the caller to retry if they can
        return true;
    }

    private String iamDisassociateReassociateAndLetSettle(Optional<Role> greengrassServiceRole, Optional<Role> greengrassRole, GreengrassGroupId greengrassGroupId, String groupVersionId) {
        String deploymentId;
        log.warn("There was a problem with IAM roles, attempting a new deployment");

        // Disassociate roles
        log.warn("Disassociating Greengrass service role");
        greengrassHelper.disassociateServiceRoleFromAccount();
        log.warn("Disassociating role from group");
        greengrassHelper.disassociateRoleFromGroup(greengrassGroupId);

        log.warn("Letting IAM settle...");

        ioHelper.sleep(30000);

        // Reassociate roles
        log.warn("Reassociating Greengrass service role");
        associateServiceRoleToAccount(greengrassServiceRole.get());
        log.warn("Reassociating Greengrass group role");
        associateRoleToGroup(greengrassRole.get(), greengrassGroupId);

        log.warn("Letting IAM settle...");

        ioHelper.sleep(30000);

        log.warn("Trying another deployment");
        deploymentId = greengrassHelper.createDeployment(greengrassGroupId, groupVersionId);
        log.warn(String.join("", "Deployment created [", deploymentId, "]"));

        log.warn("Letting deployment settle...");

        ioHelper.sleep(30000);

        return deploymentId;
    }

    @Override
    public void associateRoleToGroup(Role greengrassRole, GreengrassGroupId greengrassGroupId) {
        log.info("Associating the Greengrass role to the group");
        greengrassHelper.associateRoleToGroup(greengrassGroupId, greengrassRole);
    }

    @Override
    public void associateServiceRoleToAccount(Role greengrassServiceRole) {
        // NOTE: If you leave this out you may get errors related to Greengrass being unable to access your account to do deployments
        log.info("Associating Greengrass service role to account");
        greengrassHelper.associateServiceRoleToAccount(greengrassServiceRole);
    }

    @Override
    public void execute(DeploymentArguments deploymentArguments) {
        log.warn(String.join("", "***deploymentArguments.deploymentConfigFolderPath*** [", deploymentArguments.deploymentConfigFolderPath, "]"));

        // Make the directories for build, if necessary
        ioHelper.createDirectoryIfNecessary(ggConstants.getBuildDirectory());

        // Get the Greengrass group name
        GreengrassGroupName greengrassGroupName = ImmutableGreengrassGroupName.builder().groupName(deploymentArguments.groupName).build();

        // Get the core thing name
        ImmutableThingName coreThingName = ggVariables.getCoreThingName(greengrassGroupName);

        ///////////////////////////////////////
        // Load the deployment configuration //
        ///////////////////////////////////////

        DeploymentConf deploymentConf;

        if (isEmptyDeployment(deploymentArguments)) {
            deploymentConf = getEmptyDeploymentConf(deploymentArguments, greengrassGroupName);
        } else {
            deploymentConf = Try.of(() -> getDeploymentConf(coreThingName, deploymentArguments, greengrassGroupName)).get();
        }

        ///////////////////////////////////////////////////
        // Create an AWS Greengrass Group and get its ID //
        ///////////////////////////////////////////////////

        if (v2GreengrassHelper.groupExists(greengrassGroupName) && (deploymentArguments.ec2LinuxVersion != null)) {
            throw new RuntimeException(String.join("", "Group [", deploymentArguments.groupName, "] already exists, cannot launch another EC2 instance for this group.  You can update the group configuration by not specifying the EC2 launch option."));
        }

        log.info("Creating a Greengrass group, if necessary");
        String groupId = greengrassHelper.createGroupIfNecessary(greengrassGroupName);
        GreengrassGroupId greengrassGroupId = ImmutableGreengrassGroupId.builder().groupId(groupId).build();

        ///////////////////////
        // Create core thing //
        ///////////////////////

        log.info("Creating core thing");
        ThingArn coreThingArn = v2IotHelper.createThing(coreThingName);

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
            Config functionDefaults = ConfigFactory.parseFile(new File(String.join("/", deploymentArguments.deploymentConfigFolderPath, "function.defaults.conf")));
            boolean greengrassContainer = functionDefaults.getBoolean(ggConstants.getConfGreengrassContainer());

            // If we're not doing a Docker launch use the default values in the configuration file
            defaultFunctionIsolationMode = (greengrassContainer ? FunctionIsolationMode.GREENGRASS_CONTAINER : FunctionIsolationMode.NO_CONTAINER);

            // If we're not doing a Docker launch use the default values in the configuration file
            // defaultFunctionIsolationMode = ggVariables.getDefaultFunctionIsolationMode();
        }

        ////////////////////////////////////////////////////////////
        // Build the default environment for all Lambda functions //
        ////////////////////////////////////////////////////////////

        Map<String, String> defaultEnvironment = environmentHelper.getDefaultEnvironment(greengrassGroupId, coreThingName, coreThingArn, greengrassGroupName);

        // Get a config object with the default environment values (eg. "${AWS_IOT_THING_NAME}" used in the function and connector configuration)
        Config defaultConfig = typeSafeConfigHelper.addDefaultValues(defaultEnvironment, Optional.empty());

        //////////////////////////////////////////////////////////////////////
        // Find enabled functions and create function conf objects for them //
        //////////////////////////////////////////////////////////////////////

        List<FunctionConf> functionConfs = getFunctionConfs(deploymentArguments, deploymentConf, defaultFunctionIsolationMode, defaultConfig);

        ///////////////////////////
        // Create the connectors //
        ///////////////////////////

        List<ConnectorConf> connectorConfs = connectorHelper.getConnectorConfObjects(deploymentArguments, defaultConfig, deploymentConf.getConnectors());

        log.info("Creating connector definition");
        Optional<String> optionalConnectionDefinitionVersionArn = greengrassHelper.createConnectorDefinitionVersion(connectorConfs);

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Merge any additional permissions that the functions need into the core and service role configurations //
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////

        List<Map> additionalCoreRoleIamPolicies = getRoleIamPolicies(functionConfs.stream()
                .map(FunctionConf::getCoreRoleIamPolicy));

        List<String> additionalCoreRoleIamManagedPolicies = getRoleIamManagedPolicies(functionConfs.stream()
                .map(FunctionConf::getCoreRoleIamManagedPolicies));

        RoleConf mergedCoreRoleConf = mergeRoleConf(deploymentConf.getCoreRoleConf(), additionalCoreRoleIamPolicies, additionalCoreRoleIamManagedPolicies);

        // Use the merged core role conf
        deploymentConf = ImmutableDeploymentConf.builder().from(deploymentConf)
                .coreRoleConf(mergedCoreRoleConf)
                .build();

        if (deploymentConf.getServiceRoleConf().isPresent()) {
            List<Map> additionalServiceRoleIamPolicies = getRoleIamPolicies(functionConfs.stream()
                    .map(FunctionConf::getServiceRoleIamPolicy));

            List<String> additionalServiceRoleIamManagedPolicies = getRoleIamManagedPolicies(functionConfs.stream()
                    .map(FunctionConf::getServiceRoleIamManagedPolicies));

            RoleConf mergedServiceRoleConf = mergeRoleConf(deploymentConf.getServiceRoleConf().get(), additionalServiceRoleIamPolicies, additionalServiceRoleIamManagedPolicies);

            // Use the merged service role conf
            deploymentConf = ImmutableDeploymentConf.builder().from(deploymentConf)
                    .serviceRoleConf(mergedServiceRoleConf)
                    .build();
        }

        /////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Merge any additional permissions that the connectors need into the core and service role configurations //
        /////////////////////////////////////////////////////////////////////////////////////////////////////////////

        additionalCoreRoleIamPolicies = getRoleIamPolicies(connectorConfs.stream()
                .map(ConnectorConf::getCoreRoleIamPolicy));

        additionalCoreRoleIamManagedPolicies = getRoleIamManagedPolicies(connectorConfs.stream()
                .map(ConnectorConf::getCoreRoleIamManagedPolicies));

        mergedCoreRoleConf = mergeRoleConf(deploymentConf.getCoreRoleConf(), additionalCoreRoleIamPolicies, additionalCoreRoleIamManagedPolicies);

        // Use the merged core role conf
        deploymentConf = ImmutableDeploymentConf.builder().from(deploymentConf)
                .coreRoleConf(mergedCoreRoleConf)
                .build();

        if (deploymentConf.getServiceRoleConf().isPresent()) {
            List<Map> additionalServiceRoleIamPolicies = getRoleIamPolicies(connectorConfs.stream()
                    .map(ConnectorConf::getServiceRoleIamPolicy));

            List<String> additionalServiceRoleIamManagedPolicies = getRoleIamManagedPolicies(connectorConfs.stream()
                    .map(ConnectorConf::getServiceRoleIamManagedPolicies));

            RoleConf mergedServiceRoleConf = mergeRoleConf(deploymentConf.getServiceRoleConf().get(), additionalServiceRoleIamPolicies, additionalServiceRoleIamManagedPolicies);

            // Use the merged service role conf
            deploymentConf = ImmutableDeploymentConf.builder().from(deploymentConf)
                    .serviceRoleConf(mergedServiceRoleConf)
                    .build();
        }

        //////////////////////////////////////////////////////////
        // Create the service role and role alias, if necessary //
        //////////////////////////////////////////////////////////

        Optional<Role> optionalGreengrassServiceRole;
        Optional<CreateRoleAliasResponse> optionalCreateRoleAliasResponse;

        // Create the role for the core, if necessary
        Role coreRole;

        if (deploymentArguments.coreRoleName != null) {
            RoleName coreRoleName = ImmutableRoleName.builder().name(deploymentArguments.coreRoleName).build();
            Optional<Role> optionalCoreRole = iamHelper.getRole(coreRoleName);

            if (!optionalCoreRole.isPresent()) {
                throw new RuntimeException(String.join("", "Greengrass core role is not present or GetRole failed due to insufficient permissions on [", deploymentArguments.coreRoleName, "]"));
            }

            coreRole = optionalCoreRole.get();
        } else {
            coreRole = createCoreRole(deploymentConf.getCoreRoleConf());
        }

        if (!deploymentArguments.serviceRoleExists) {
            // If the service role does not exist we should create it
            Role serviceRole = createServiceRole(deploymentConf.getServiceRoleConf().get());
            optionalGreengrassServiceRole = Optional.of(serviceRole);
        } else {
            // The service role exists already, do not try to create or modify it
            optionalGreengrassServiceRole = Optional.empty();
        }

        // Create the role alias so we can use IoT as a credentials provider with certificate based authentication
        if (!deploymentConf.getCoreRoleConf().getAlias().isPresent()) {
            throw new RuntimeException(String.join("", "No role alias specified for the Greengrass core role [", deploymentConf.getCoreRoleConf().getName(), "]"));
        }

        ImmutableRoleAlias coreRoleAlias = ImmutableRoleAlias.builder().name(deploymentConf.getCoreRoleConf().getAlias().get()).build();
        log.info(String.join("", "Creating core role alias [", coreRoleAlias.getName(), "]"));
        optionalCreateRoleAliasResponse = Optional.of(v2IotHelper.forceCreateRoleAlias(coreRole, coreRoleAlias));

        //////////////////////////////////
        // Create or reuse certificates //
        //////////////////////////////////

        Optional<GroupVersion> optionalGroupVersion = v2GreengrassHelper.getLatestGroupVersionByNameOrId(groupId);
        Optional<KeysAndCertificate> optionalCoreKeysAndCertificate = Optional.empty();

        Optional<CertificateArn> optionalCoreCertificateArn = Optional.empty();

        if (deploymentArguments.certificateArn != null) {
            // Use the certificate ARN supplied by the user, new or existing group
            log.info(String.join("", "Using user supplied certificate ARN for core certificate [", deploymentArguments.certificateArn, "]"));
            optionalCoreCertificateArn = Optional.of(ImmutableCertificateArn.builder().arn(deploymentArguments.certificateArn).build());
        } else if (deploymentArguments.csr != null) {
            // Sign the CSR supplied by the user, new or existing group
            log.info("Using user supplied CSR for core certificate");
            optionalCoreCertificateArn = Optional.of(v2IotHelper.signCsrAndReturnCertificateArn(ImmutableCertificateSigningRequest.builder().request(deploymentArguments.csr).build()));
        } else if (!optionalGroupVersion.isPresent()) {
            // New group, create new keys
            log.info("Group is new, no certificate ARN or CSR supplied, creating new keys");
            KeysAndCertificate coreKeysAndCertificate = iotHelper.createKeysAndCertificateForCore(greengrassGroupName);
            iotHelper.writePublicSignedCertificateFileForCore(coreKeysAndCertificate, greengrassGroupName);
            iotHelper.writePrivateKeyFileForCore(coreKeysAndCertificate, greengrassGroupName);
            iotHelper.writeRootCaFile(greengrassGroupName);
            iotHelper.writeIotCpPropertiesFile(greengrassGroupName, coreThingName, coreRoleAlias);
            optionalCoreKeysAndCertificate = Optional.of(coreKeysAndCertificate);
        } else {
            GroupVersion groupVersion = optionalGroupVersion.get();

            // Existing group, can we find the existing keys?
            optionalCoreKeysAndCertificate = iotHelper.loadKeysAndCertificateForCore(greengrassGroupName);

            if (optionalCoreKeysAndCertificate.isPresent()) {
                // Found keys, we'll reuse them
                log.info("Group is not new, loaded keys from credentials directory");
            } else if (deploymentArguments.forceCreateNewKeysOption) {
                // Didn't find keys but the user has requested that they be recreated
                log.info("Group is not new, user forcing new keys to be created");
                KeysAndCertificate coreKeysAndCertificate = iotHelper.createKeysAndCertificateForCore(greengrassGroupName);
                optionalCoreKeysAndCertificate = Optional.of(coreKeysAndCertificate);
            } else {
                log.info("Group is not new, keys could not be found, but user not forcing new keys to be created");
                log.info("Attempting to get the core certificate ARN from the latest group version information");
                optionalCoreCertificateArn = v2GreengrassHelper.getCoreCertificateArn(groupVersion);
            }
        }

        if (optionalCoreKeysAndCertificate.isPresent()) {
            // If we have keys and certificate then get the certificate ARN
            optionalCoreCertificateArn = optionalCoreKeysAndCertificate.map(KeysAndCertificate::getCertificateArn);
        }

        if (!optionalCoreCertificateArn.isPresent()) {
            // We need the certificate ARN at this point, fail if we don't have it
            StringBuilder message = new StringBuilder();
            message.append("Core certificate information/ARN could not be found. ");
            message.append(String.join("", "If you would like to recreate the keys you must specify the [", DeploymentArguments.LONG_FORCE_CREATE_NEW_KEYS_OPTION, "] option. "));
            message.append(String.join("", "If you'd like to reuse an existing certificate you must specify the [", DeploymentArguments.LONG_CERTIFICATE_ARN_OPTION, "] option."));

            throw new RuntimeException(message.toString());
        }

        CertificateArn coreCertificateArn = optionalCoreCertificateArn.get();

        ////////////////////////////////////////////////////
        // IoT policy creation for the core, if necessary //
        ////////////////////////////////////////////////////

        PolicyName corePolicyName;

        if (deploymentArguments.corePolicyName == null) {
            if (!deploymentConf.getCoreRoleConf().getIotPolicy().isPresent()) {
                throw new RuntimeException(String.join("", "No IoT policy specified for core role [", deploymentConf.getCoreRoleConf().getName(), "]"));
            }

            log.info("Creating policy for core");
            v2IotHelper.createPolicyIfNecessary(ggVariables.getCorePolicyName(greengrassGroupName),
                    ImmutablePolicyDocument.builder().document(deploymentConf.getCoreRoleConf().getIotPolicy().get()).build());
            corePolicyName = ggVariables.getCorePolicyName(greengrassGroupName);
        } else {
            corePolicyName = ImmutablePolicyName.builder().name(deploymentArguments.corePolicyName).build();
        }

        //////////////////////////////////
        // Attach policy to certificate //
        //////////////////////////////////

        v2IotHelper.attachPrincipalPolicy(corePolicyName, coreCertificateArn);

        /////////////////////////////////
        // Attach thing to certificate //
        /////////////////////////////////

        v2IotHelper.attachThingPrincipal(ggVariables.getCoreThingName(greengrassGroupName), coreCertificateArn);

        ////////////////////////////////////////////////
        // Associate the Greengrass role to the group //
        ////////////////////////////////////////////////

        associateRoleToGroup(coreRole, greengrassGroupId);

        ////////////////////////////////////////////
        // Create a core definition and a version //
        ////////////////////////////////////////////

        log.info("Creating core definition");
        String coreDefinitionVersionArn = greengrassHelper.createCoreDefinitionAndVersion(ggVariables.getCoreDefinitionName(greengrassGroupName), coreCertificateArn, coreThingArn, deploymentConf.isSyncShadow());

        //////////////////////////////////////////////
        // Create a logger definition and a version //
        //////////////////////////////////////////////

        log.info("Creating logger definition");
        String loggerDefinitionVersionArn;

        if (!deploymentConf.getLoggers().isPresent()) {
            log.warn("No loggers section defined in configuration files, using default logger configuration");
            loggerDefinitionVersionArn = greengrassHelper.createDefaultLoggerDefinitionAndVersion();
        } else {
            loggerDefinitionVersionArn = greengrassHelper.createLoggerDefinitionAndVersion(deploymentConf.getLoggers().get());
        }

        //////////////////////////////////////////////
        // Create the Lambda role for the functions //
        //////////////////////////////////////////////

        Optional<Role> optionalLambdaRole = Optional.empty();

        if (!deploymentConf.getFunctions().isEmpty()) {
            log.info("Creating Lambda role");

            RoleConf lambdaRoleConf = deploymentConf.getLambdaRoleConf().get();

            requireAssumeRolePolicy(lambdaRoleConf, "Lambda");

            optionalLambdaRole = Optional.of(createRoleFromRoleConf(lambdaRoleConf));
        }

        ////////////////////////////////////////////////////////
        // Start building the subscription and function lists //
        ////////////////////////////////////////////////////////

        List<Subscription> subscriptions = new ArrayList<>();

        ////////////////////////////////////////////////////
        // Determine if any functions need to run as root //
        ////////////////////////////////////////////////////

        boolean functionsRunningAsRoot = functionConfs.stream()
                .anyMatch(functionConf -> ((functionConf.getUid().isPresent() && (functionConf.getUid().get() == 0))
                        || (functionConf.getGid().isPresent() && (functionConf.getGid().get() == 0))));

        if (functionsRunningAsRoot) {
            log.warn("At least one function was detected that is configured to run as root");
        }

        ////////////////////////////////////////////////////////////////////////////
        // Determine if any functions are running inside the Greengrass container //
        ////////////////////////////////////////////////////////////////////////////

        List<FunctionName> functionsRunningInGreengrassContainer = functionConfs.stream()
                .filter(FunctionConf::isGreengrassContainer)
                .map(FunctionConf::getFunctionName)
                .collect(Collectors.toList());

        /////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Check if launching or building a Docker container and functions are running in the Greengrass container //
        /////////////////////////////////////////////////////////////////////////////////////////////////////////////

        if ((deploymentArguments.dockerLaunch || deploymentArguments.buildContainer) && !functionsRunningInGreengrassContainer.isEmpty()) {
            log.error("The following functions are marked to run in the Greengrass container:");

            functionsRunningInGreengrassContainer
                    .forEach(name -> log.error(String.join("", "  ", name.getName())));

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

        Map<Function, FunctionConf> functionToConfMap = new HashMap<>();

        // Only try to create functions if we have a Lambda role
        if (optionalLambdaRole.isPresent()) {
            Role lambdaRole = optionalLambdaRole.get();

            // Verify that all of the functions in the list are supported
            functionHelper.verifyFunctionsAreSupported(functionConfs);

            // Get the map of functions to function configuration (builds functions and publishes them to Lambda)
            functionToConfMap = functionHelper.buildFunctionsAndGenerateMap(deploymentArguments.s3Bucket, deploymentArguments.s3Directory, functionConfs, lambdaRole);
        }

        ////////////////////////////
        // Set up local resources //
        ////////////////////////////

        log.info("Creating resource definition");
        String resourceDefinitionVersionArn = greengrassHelper.createResourceDefinitionFromFunctionConfs(functionConfs);

        /////////////////////////////////////////////////////////////////////////
        // Build the function definition for the Lambda function and a version //
        /////////////////////////////////////////////////////////////////////////

        log.info("Creating function definition");
        String functionDefinitionVersionArn = greengrassHelper.createFunctionDefinitionVersion(ImmutableSet.copyOf(functionToConfMap.keySet()), defaultFunctionIsolationMode);

        ///////////////////////////////////////////////
        // Connection functions to cloud and shadows //
        ///////////////////////////////////////////////

        subscriptions.addAll(functionToConfMap.entrySet().stream()
                .flatMap(entry -> subscriptionHelper.createCloudSubscriptionsForArn(
                        entry.getValue().getFromCloudSubscriptions(),
                        entry.getValue().getToCloudSubscriptions(),
                        entry.getKey().functionArn()).stream())
                .collect(Collectors.toList()));

        subscriptions.addAll(subscriptionHelper.connectFunctionsToShadows(functionToConfMap));

        ////////////////////////////////////////
        // Connection functions to each other //
        ////////////////////////////////////////

        subscriptions.addAll(subscriptionHelper.connectFunctions(functionToConfMap));

        //////////////////////////////////////////////////////
        // Get a list of all of the connected thing shadows //
        //////////////////////////////////////////////////////

        Set<ThingName> connectedShadowThings = new HashSet<>();

        for (FunctionConf functionConf : functionToConfMap.values()) {
            connectedShadowThings.addAll(functionConf.getConnectedShadows().stream()
                    .map(connectedShadow -> ImmutableThingName.builder().name(connectedShadow).build())
                    .collect(Collectors.toList()));

            for (String connectedShadow : functionConf.getConnectedShadows()) {
                // Make sure all of the connected shadows exist
                v2IotHelper.createThing(ImmutableThingName.builder().name(connectedShadow).build());
            }
        }

        //////////////////////////////////////////////////////
        // Create the subscription definition from our list //
        //////////////////////////////////////////////////////

        log.info("Creating subscription definition");
        String subscriptionDefinitionVersionArn = greengrassHelper.createSubscriptionDefinitionAndVersion(subscriptions);

        ////////////////////////////////////
        // Create a minimal group version //
        ////////////////////////////////////

        log.info("Creating group version");

        GroupVersion.Builder groupVersionBuilder = GroupVersion.builder();

        // Connector definition can not be empty or the cloud service will reject it
        optionalConnectionDefinitionVersionArn.ifPresent(groupVersionBuilder::connectorDefinitionVersionArn);
        groupVersionBuilder.coreDefinitionVersionArn(coreDefinitionVersionArn);
        groupVersionBuilder.functionDefinitionVersionArn(functionDefinitionVersionArn);
        groupVersionBuilder.loggerDefinitionVersionArn(loggerDefinitionVersionArn);
        groupVersionBuilder.resourceDefinitionVersionArn(resourceDefinitionVersionArn);
        groupVersionBuilder.subscriptionDefinitionVersionArn(subscriptionDefinitionVersionArn);

        GroupVersion groupVersion = groupVersionBuilder.build();

        String groupVersionId = greengrassHelper.createGroupVersion(greengrassGroupId, groupVersion);

        /////////////////////////////////////////////
        // Do all of the output file related stuff //
        /////////////////////////////////////////////

        buildOutputFiles(deploymentArguments,
                greengrassGroupName,
                optionalCreateRoleAliasResponse,
                greengrassGroupId,
                coreThingName,
                coreThingArn,
                optionalCoreKeysAndCertificate,
                coreCertificateArn,
                functionsRunningAsRoot);

        //////////////////////////////////////////////////
        // Start building the EC2 instance if necessary //
        //////////////////////////////////////////////////

        Optional<String> optionalInstanceId = Optional.empty();

        if (deploymentArguments.ec2LinuxVersion != null) {
            log.info("Launching EC2 instance");

            Set<Integer> openPorts = functionConfs.stream()
                    // Get all of the environment variables from each function
                    .map(FunctionConf::getEnvironmentVariables)
                    // Extract the PORT variables
                    .map(environmentVariables -> Optional.ofNullable(environmentVariables.get("PORT")))
                    // Filter out missing values
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    // Parse the string into an integer
                    .map(Integer::parseInt)
                    .collect(Collectors.toSet());

            optionalInstanceId = launchEc2Instance(deploymentArguments.groupName, deploymentArguments.architecture, deploymentArguments.ec2LinuxVersion, deploymentArguments.mqttPort, openPorts);

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

                // Pull the official Greengrass Docker container first
                String officialGreengrassDockerImage = ggConstants.getOfficialGreengrassDockerImage();
                officialGreengrassImageDockerHelper.pullImage(officialGreengrassDockerImage);

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
        Try.run(() -> createAndWaitForDeployment(optionalGreengrassServiceRole, Optional.of(coreRole), greengrassGroupId, groupVersionId))
                .get();

        //////////////////////////////////////////////
        // Launch the Docker container if necessary //
        //////////////////////////////////////////////

        if (deploymentArguments.dockerLaunch) {
            log.info("Launching Docker container");
            String officialGreengrassDockerImage = ggConstants.getOfficialGreengrassDockerImage();
            officialGreengrassImageDockerHelper.pullImage(officialGreengrassDockerImage);
            officialGreengrassImageDockerHelper.createAndStartContainer(officialGreengrassDockerImage, greengrassGroupName);
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
                throw new RuntimeException(String.join("", "Unexpected EC2 Linux version requested [", deploymentArguments.ec2LinuxVersion.name(), "], this is a bug 2 [couldn't determine SSH username]"));
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
    }

    @NotNull
    public List<String> getRoleIamManagedPolicies(Stream<Optional<List<String>>> optionalStream) {
        return optionalStream
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    @NotNull
    public List<Map> getRoleIamPolicies(Stream<Optional<String>> optionalStream) {
        return optionalStream
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(json -> jsonHelper.fromJson(Map.class, json.getBytes()))
                .collect(Collectors.toList());
    }

    private RoleConf mergeRoleConf(RoleConf coreRoleConf, List<Map> additionalIamPolicies, List<String> additionalCoreRoleIamManagedPolicies) {
        // Get the existing IAM policy or an empty map
        Map<String, Object> iamPolicy = coreRoleConf.getIamPolicy().map(string -> jsonHelper.fromJson(Map.class, string.getBytes())).orElse(new HashMap<String, Object>());

        List<Object> flattenedAdditionalIamPolicies = additionalIamPolicies.stream()
                .map(map -> Optional.ofNullable(map.get("Statement")))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(list -> (List<List<Object>>) list)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        if (!flattenedAdditionalIamPolicies.isEmpty()) {
            // If the original IAM policy had no statements then we need to create one to hold the additional values
            List<Object> statements = (List<Object>) iamPolicy.computeIfAbsent("Statement", x -> new ArrayList());

            // If the original IAM policy had no version we need to add that as well
            iamPolicy.computeIfAbsent("Version", x -> "2012-10-17");

            statements.addAll(flattenedAdditionalIamPolicies);

            // De-duplicate statements and put them back in the map
            iamPolicy.put("Statement", new ArrayList<>(new HashSet<>(statements)));
        }

        // Get the existing managed IAM policies or an empty list
        List<String> iamManagedPolicies = coreRoleConf.getIamManagedPolicies().orElse(new ArrayList<>());

        // Add all of the new managed IAM policies if there are any
        iamManagedPolicies.addAll(additionalCoreRoleIamManagedPolicies);

        // De-duplicate IAM managed policies and put them back in the list
        iamManagedPolicies = new ArrayList<>(new HashSet<>(iamManagedPolicies));

        ImmutableRoleConf.Builder builder = ImmutableRoleConf.builder().from(coreRoleConf);

        if (!iamPolicy.isEmpty()) {
            builder.iamPolicy(jsonHelper.toJson(iamPolicy));
        }

        if (!iamManagedPolicies.isEmpty()) {
            builder.iamManagedPolicies(iamManagedPolicies);
        }

        return builder.build();
    }

    @NotNull
    public List<FunctionConf> getFunctionConfs(DeploymentArguments deploymentArguments, DeploymentConf deploymentConf, FunctionIsolationMode defaultFunctionIsolationMode, Config defaultConfig) {
        List<FunctionConf> functionConfs = functionHelper.getFunctionConfObjects(deploymentArguments, defaultConfig, deploymentConf, defaultFunctionIsolationMode);

        // Find Python functions that may not have had their language updated, this should never happen
        Predicate<FunctionConf> legacyPythonPredicate = functionConf -> functionConf.getLanguage().equals(Language.Python);

        List<FunctionConf> legacyPythonFunctions = functionConfs.stream()
                .filter(legacyPythonPredicate)
                .collect(Collectors.toList());

        if (legacyPythonFunctions.size() != 0) {
            log.error("Some Python functions do not have a Python version specified, this should never happen!");
            legacyPythonFunctions.stream()
                    .map(FunctionConf::getFunctionName)
                    .map(FunctionName::getName)
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
                    .map(FunctionName::getName)
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
                    .map(FunctionName::getName)
                    .forEach(log::error);
            throw new UnsupportedOperationException();
        }

        return functionConfs;
    }

    private void requireAssumeRolePolicy(RoleConf roleConf, String type) {
        if (roleConf.getAssumeRolePolicy().isPresent()) {
            return;
        }

        throw new RuntimeException(String.join("", "Assume role policy not specified when creating Greengrass ", type, " role"));
    }

    public boolean isEmptyDeployment(DeploymentArguments deploymentArguments) {
        return deploymentArguments.deploymentConfigFilename.equals(EMPTY);
    }

    private DeploymentConf getEmptyDeploymentConf(DeploymentArguments deploymentArguments, GreengrassGroupName greengrassGroupName) {
        String coreRoleAlias = String.join("", deploymentArguments.coreRoleName, "Alias");

        RoleConf coreRoleConf = ImmutableRoleConf.builder()
                .name(deploymentArguments.coreRoleName)
                .alias(coreRoleAlias)
                .iotPolicy(deploymentArguments.corePolicyName)
                .build();

        return ImmutableDeploymentConf.builder()
                .isSyncShadow(true)
                .name(EMPTY)
                .groupName(greengrassGroupName)
                .coreRoleConf(coreRoleConf)
                .functions(new ArrayList<>())
                .build();
    }

    private void attemptBootstrap(DeploymentArguments deploymentArguments, String ipAddress, String user) {
        Session session = sshHelper.getSshSession(ipAddress, user);

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
        ioHelper.runCommand(session, String.join(" ", "chmod", "+x", String.join("", "./", remoteFilename)));
        log.info(String.join("", "Running bootstrap script on target host in screen, connect to the target host [", user, "@", host, "] and run 'screen -r' to see the progress"));
        runCommandInScreen(session, String.join(" ", String.join("", "./", remoteFilename), "--now"), Optional.of(GREENGRASS_SESSION_NAME), true);
        session.disconnect();
        return true;
    }

    private void runCommandInScreen(Session session, String command, Optional<String> screenSessionName, boolean keepSessionOpen) throws JSchException, IOException {
        AtomicBoolean screenAvailable = new AtomicBoolean(false);

        Consumer<String> screenAvailabilityChecker = getScreenAvailabilityChecker(screenAvailable);

        ioHelper.runCommand(session, String.join(" ", "screen", "--version"), Optional.of(screenAvailabilityChecker));

        if (!screenAvailable.get()) {
            // Screen is not available, throw an exception
            session.disconnect();
            throwRuntimeException(SCREEN_NOT_AVAILABLE_ERROR_MESSAGE);
        }

        String sessionNameOptions = "";

        if (screenSessionName.isPresent()) {
            AtomicBoolean screenSessionNameAvailable = new AtomicBoolean(false);

            Consumer<String> screenSessionNameChecker = getScreenSessionNameChecker(screenSessionNameAvailable);

            sessionNameOptions = String.join(" ", "-S", screenSessionName.get());

            ioHelper.runCommand(session, String.join(" ", "screen", sessionNameOptions, "-Q", "select", "."), Optional.of(screenSessionNameChecker));

            if (!screenSessionNameAvailable.get()) {
                session.disconnect();
                throwRuntimeException(SCREEN_SESSION_NAME_IN_USE_ERROR_MESSAGE);
            }
        }

        if (keepSessionOpen) {
            command = String.join("", "bash -c \"", command, "; exec bash\"");
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

    private Void throwRuntimeException(String errorMessage) {
        throw new RuntimeException(errorMessage);
    }

    private Optional<String> launchEc2Instance(String groupName, Architecture architecture, EC2LinuxVersion ec2LinuxVersion, int mqttPort, Set<Integer> openPorts) {
        String instanceTagName = String.join("-", GREENGRASS_EC2_INSTANCE_TAG_PREFIX, groupName);

        Optional<String> optionalAccountId = getAccountId(ec2LinuxVersion);

        Optional<InstanceType> instanceType = getInstanceType(architecture);

        Optional<String> optionalNameFilter = getNameFilter(architecture, ec2LinuxVersion);

        if (!optionalAccountId.isPresent()) {
            throw new RuntimeException(String.join("", "Unexpected EC2 Linux version requested [", ec2LinuxVersion.name(), "], this is a bug 1 [couldn't determine which AMI to use]"));
        }

        if (!optionalNameFilter.isPresent() || !instanceType.isPresent()) {
            throw new RuntimeException(String.join("", "Unexpected architecture [", architecture.toString(), "] for EC2 launch"));
        }

        Optional<Image> optionalImage = getImage(optionalNameFilter.get(), optionalAccountId.get());

        if (!optionalImage.isPresent()) {
            log.error(String.join("", "No [", ec2LinuxVersion.name(), "] image found in this region, not launching the instance"));
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

        log.warn(String.join("", "Automatically chose the first key pair available [", keyPairInfo.keyName(), "]"));

        Image image = optionalImage.get();

        String securityGroupName = String.join("-", instanceTagName, ioHelper.getUuid());

        CreateSecurityGroupRequest createSecurityGroupRequest = CreateSecurityGroupRequest.builder()
                .groupName(securityGroupName)
                .description(String.join("", "Security group for Greengrass Core [", instanceTagName, "]"))
                .build();

        ec2Client.createSecurityGroup(createSecurityGroupRequest);

        // Sometimes the security group isn't immediately visible so we need retries
        RetryPolicy<AuthorizeSecurityGroupIngressResponse> securityGroupRetryPolicy = new RetryPolicy<AuthorizeSecurityGroupIngressResponse>()
                .handleIf(throwable -> throwable.getMessage().contains(DOES_NOT_EXIST))
                .withDelay(Duration.ofSeconds(5))
                .withMaxRetries(6)
                .onRetry(failure -> log.warn("Waiting for security group to become visible..."))
                .onRetriesExceeded(failure -> log.error("Security group never became visible. Cannot continue."));

        List<IpPermission> ipPermissions = openPorts.stream()
                .map(this::openTcpPortToWorld)
                .collect(Collectors.toList());

        IpPermission sshPermission = openTcpPortToWorld(SSH_PORT);
        IpPermission mqttPermission = openTcpPortToWorld(mqttPort);
        IpPermission moshPermission = openUdpRangeToWorld(MOSH_START_PORT, MOSH_END_PORT);

        ipPermissions.add(sshPermission);
        ipPermissions.add(moshPermission);
        ipPermissions.add(mqttPermission);

        AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest = AuthorizeSecurityGroupIngressRequest.builder()
                .groupName(securityGroupName)
                .ipPermissions(ipPermissions)
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

        log.info(String.join("", "Launched instance [", instanceId, "] with tag [", instanceTagName, "]"));

        return Optional.of(instanceId);
    }

    private IpPermission openTcpPortToWorld(int port) {
        log.warn(String.join("", "Opening security group for inbound TCP traffic on all IPs on port [", String.valueOf(port), "]"));

        return IpPermission.builder()
                .fromPort(port)
                .toPort(port)
                .ipProtocol("tcp")
                .ipRanges(ALL_IPS)
                .build();
    }

    private IpPermission openUdpRangeToWorld(int fromPort, int toPort) {
        log.warn(String.join("", "Opening security group for inbound UDP traffic on all IPs from port [", String.valueOf(fromPort), "] to port [", String.valueOf(toPort), "]"));

        return IpPermission.builder()
                .fromPort(fromPort)
                .toPort(toPort)
                .ipProtocol("udp")
                .ipRanges(ALL_IPS)
                .build();
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
            } else if (architecture.equals(Architecture.ARMV8)) {
                nameFilter = Optional.of(ARM64_UBUNTU_18_04_LTS_AMI_FILTER);
            }
        } else if (ec2LinuxVersion.equals(EC2LinuxVersion.AmazonLinux2)) {
            if (architecture.equals(Architecture.X86_64)) {
                nameFilter = Optional.of(X86_AMAZON_LINUX_2_AMI_FILTER);
            } else if (architecture.equals(Architecture.ARMV8)) {
                nameFilter = Optional.of(ARM64_AMAZON_LINUX_2_AMI_FILTER);
            }
        }

        return nameFilter;
    }

    public Optional<InstanceType> getInstanceType(Architecture architecture) {
        Optional<InstanceType> instanceType = Optional.empty();

        if (architecture.equals(Architecture.X86_64)) {
            instanceType = Optional.of(InstanceType.T3_MICRO);
        } else if (architecture.equals(Architecture.ARMV8)) {
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
     * @param serviceRoleConf
     * @return
     */
    private Role createServiceRole(RoleConf serviceRoleConf) {
        Role serviceRole = createRoleFromRoleConfAndAttachPolicies("service", serviceRoleConf);

        associateServiceRoleToAccount(serviceRole);

        return serviceRole;
    }

    private Role createRoleFromRoleConfAndAttachPolicies(String type, RoleConf roleConf) {
        String name = roleConf.getName();

        log.info(String.join("", "Creating Greengrass ", type, " role [", name, "]"));

        Role role = createRoleFromRoleConf(roleConf);

        log.info(String.join("", "Attaching role policies to Greengrass ", type, " role [", name, "]"));

        Optional<List<ManagedPolicyArn>> managedPolicyArns = Optional.empty();

        if (roleConf.getIamManagedPolicies().isPresent()) {
            managedPolicyArns = Optional.of(roleConf.getIamManagedPolicies().get().stream()
                    .map(managedPolicyArn -> ImmutableManagedPolicyArn.builder().arn(managedPolicyArn).build())
                    .collect(Collectors.toList()));
        }

        iamHelper.attachRolePolicies(role, managedPolicyArns);

        com.awslabs.iam.data.ImmutablePolicyName policyName = com.awslabs.iam.data.ImmutablePolicyName.builder().name("inline-by-ggp").build();

        Optional<InlinePolicy> optionalInlinePolicy = Optional.empty();

        if (roleConf.getIamPolicy().isPresent()) {
            optionalInlinePolicy = Optional.of(ImmutableInlinePolicy.builder().policy(roleConf.getIamPolicy().get()).build());
        }

        iamHelper.putInlinePolicy(role, policyName, optionalInlinePolicy);

        return role;
    }

    private Role createRoleFromRoleConf(RoleConf roleConf) {
        RoleName roleName = ImmutableRoleName.builder().name(roleConf.getName()).build();
        Optional<AssumeRolePolicyDocument> optionalAssumeRolePolicyDocument = Optional.empty();

        if (roleConf.getAssumeRolePolicy().isPresent()) {
            optionalAssumeRolePolicyDocument = Optional.of(ImmutableAssumeRolePolicyDocument.builder().document(roleConf.getAssumeRolePolicy().get()).build());
        }

        return iamHelper.createRoleIfNecessary(roleName, optionalAssumeRolePolicyDocument);
    }

    private void buildOutputFiles(DeploymentArguments deploymentArguments, GreengrassGroupName greengrassGroupName, Optional<CreateRoleAliasResponse> optionalCreateRoleAliasResponse, GreengrassGroupId greengrassGroupId, ThingName awsIotThingName, ThingArn awsIotThingArn, Optional<KeysAndCertificate> optionalCoreKeysAndCertificate, CertificateArn coreCertificateArn, boolean functionsRunningAsRoot) {
        if (deploymentArguments.scriptOutput) {
            installScriptVirtualTarEntries = Optional.of(new ArrayList<>());
        }

        if ((deploymentArguments.oemOutput) || (deploymentArguments.oemJsonOutput != null)) {
            oemVirtualTarEntries = Optional.of(new ArrayList<>());
        }

        if (!installScriptVirtualTarEntries.isPresent() &&
                !oemVirtualTarEntries.isPresent()) {
            log.warn("Not building any output files.  No output files specified (OEM or script)");
            return;
        }

        if (!optionalCoreKeysAndCertificate.isPresent() && (deploymentArguments.hsiParameters == null) && installScriptVirtualTarEntries.isPresent()) {
            // We don't have the keys, HSI parameters weren't set, and the user wants an installation script. This will not work.
            throw new RuntimeException(String.join("", "Could not find the core keys and certificate and no HSI options were set, cannot build a complete output file. Specify the [", DeploymentArguments.LONG_FORCE_CREATE_NEW_KEYS_OPTION, "] option if you need to regenerate the output files."));
        }

        log.info("Adding keys and certificate files to archive");

        String coreCertificatePath = String.join("/", ggConstants.getCertsDirectoryPrefix(), ggConstants.getCorePublicCertificateName());
        String coreCertificateArnPath = String.join(".", coreCertificatePath, "arn");

        if (optionalCoreKeysAndCertificate.isPresent()) {
            KeysAndCertificate coreKeysAndCertificate = optionalCoreKeysAndCertificate.get();

            String coreKeyPath = String.join("/", ggConstants.getCertsDirectoryPrefix(), ggConstants.getCorePrivateKeyName());

            addPrivateAndPublicKeyFiles(installScriptVirtualTarEntries, coreKeysAndCertificate, coreKeyPath, coreCertificatePath);
            addPrivateAndPublicKeyFiles(oemVirtualTarEntries, coreKeysAndCertificate, coreKeyPath, coreCertificatePath);
        } else {
            log.info("- Adding only client certificate to archive");

            CertificatePem coreCertificatePem = v2IotHelper.getCertificatePem(coreCertificateArn).get();
            byte[] pemBytes = coreCertificatePem.getPem().getBytes();

            archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, coreCertificatePath, pemBytes, normalFilePermissions);
            archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, coreCertificatePath, pemBytes, normalFilePermissions);
        }

        // Add a file for the certificate ARN so it is easier to find on the host or when using the Lambda version of GGP
        archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, coreCertificateArnPath, coreCertificateArn.getArn().getBytes(), normalFilePermissions);
        archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, coreCertificateArnPath, coreCertificateArn.getArn().getBytes(), normalFilePermissions);

        ///////////////////////
        // Build config.json //
        ///////////////////////

        Region currentRegion = awsHelper.getCurrentRegion();

        log.info("Building config.json");
        String configJson = configFileHelper.generateConfigJson(ggConstants.getRootCaName(),
                ggConstants.getCorePublicCertificateName(),
                ggConstants.getCorePrivateKeyName(),
                awsIotThingArn,
                v2IotHelper.getEndpoint(V2IotEndpointType.DATA_ATS),
                currentRegion,
                deploymentArguments,
                functionsRunningAsRoot);

        log.info("Adding config.json to archive");
        String configJsonPath = String.join("/", ggConstants.getConfigDirectoryPrefix(), ggConstants.getConfigFileName());
        archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, configJsonPath, configJson.getBytes(), normalFilePermissions);
        archiveHelper.addVirtualTarEntry(oemVirtualTarEntries, configJsonPath, configJson.getBytes(), normalFilePermissions);

        /////////////////////////
        // Get the AWS root CA //
        /////////////////////////

        log.info("Getting root CA");
        String rootCaPath = String.join("/", ggConstants.getCertsDirectoryPrefix(), ggConstants.getRootCaName());

        // Use ifPresent here so we don't download the root CA if we don't need to add it to the archive
        installScriptVirtualTarEntries.ifPresent(archive -> archiveHelper.addVirtualTarEntry(archive, rootCaPath, ioHelper.download(ggConstants.getRootCaUrl()).getBytes(), normalFilePermissions));
        oemVirtualTarEntries.ifPresent(archive -> archiveHelper.addVirtualTarEntry(archive, rootCaPath, ioHelper.download(ggConstants.getRootCaUrl()).getBytes(), normalFilePermissions));

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Add some extra files to the OEM deployment so that Docker based deployments can do a redeployment on startup //
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        // Only create these files if we know the role alias
        if (optionalCreateRoleAliasResponse.isPresent()) {
            CreateRoleAliasResponse createRoleAliasResponse = optionalCreateRoleAliasResponse.get();

            addCredentialProviderFiles(oemVirtualTarEntries, greengrassGroupId, awsIotThingName, currentRegion, createRoleAliasResponse, deploymentArguments.hsiParameters);
            addCredentialProviderFiles(installScriptVirtualTarEntries, greengrassGroupId, awsIotThingName, currentRegion, createRoleAliasResponse, deploymentArguments.hsiParameters);
        }

        ///////////////////////
        // Build the scripts //
        ///////////////////////

        String baseGgShScriptName = ggVariables.getBaseGgScriptName(greengrassGroupName);
        String ggShScriptName = ggVariables.getGgShScriptName(greengrassGroupName);

        log.info("Adding scripts to archive");
        Optional<Architecture> optionalArchitecture = getArchitecture(deploymentArguments);

        // Skip this block if we're not generating the install script so we don't generate all of the other inner scripts
        if (installScriptVirtualTarEntries.isPresent()) {
            archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, scriptHelper.getStartScriptName(), scriptHelper.generateStartScript(optionalArchitecture.get()).getBytes(), scriptPermissions);
            archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, scriptHelper.getStopScriptName(), scriptHelper.generateStopScript(optionalArchitecture.get()).getBytes(), scriptPermissions);
            archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, scriptHelper.getCleanScriptName(), scriptHelper.generateCleanScript(optionalArchitecture.get(), baseGgShScriptName).getBytes(), scriptPermissions);
            archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, scriptHelper.getMonitorScriptName(), scriptHelper.generateMonitorScript(optionalArchitecture.get()).getBytes(), scriptPermissions);
            archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, scriptHelper.getSystemdScriptName(), scriptHelper.generateSystemdScript().getBytes(), scriptPermissions);
            archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, scriptHelper.getCredentialsScriptName(), scriptHelper.generateCredentialsScript().getBytes(), scriptPermissions);
            archiveHelper.addVirtualTarEntry(installScriptVirtualTarEntries, scriptHelper.getUpdateDependenciesScriptName(), scriptHelper.generateUpdateDependenciesScript().getBytes(), scriptPermissions);
        }

        ///////////////////////////
        // Package everything up //
        ///////////////////////////

        if (installScriptVirtualTarEntries.isPresent()) {
            log.info("Adding Greengrass binary to archive");
            Architecture architecture = optionalArchitecture.get();
            URL architectureUrl = getArchitectureUrl(deploymentArguments);

            // Use ifPresent here to avoid reading the file if it isn't necessary
            installScriptVirtualTarEntries.ifPresent(archive -> archiveHelper.addVirtualTarEntry(archive, architecture.getFilename(), ioHelper.readFile(architectureUrl), normalFilePermissions));

            log.info(String.join("", "Building script [", ggShScriptName, "]"));
            ByteArrayOutputStream ggScriptTemplate = new ByteArrayOutputStream();

            Try.run(() -> writePayload(architecture, ggScriptTemplate)).get();

            log.info(String.join("", "Writing script [", ggShScriptName, "]"));
            ioHelper.writeFile(ggShScriptName, ggScriptTemplate.toByteArray());
            ioHelper.makeExecutable(ggShScriptName);

            // Copy to S3 if necessary
            if (deploymentArguments.s3Bucket != null) {
                S3Bucket s3Bucket = ImmutableS3Bucket.builder().bucket(deploymentArguments.s3Bucket).build();
                S3Path s3Path = ImmutableS3Path.builder().path(deploymentArguments.s3Directory).build();
                File file = new File(ggShScriptName);
                v2S3Helper.copyToS3(s3Bucket, s3Path, file);
            }
        }

        if (oemVirtualTarEntries.isPresent()) {
            if (deploymentArguments.oemJsonOutput != null) {
                writeOemJsonOutput(oemVirtualTarEntries.get(), deploymentArguments.oemJsonOutput);
            } else {
                String oemArchiveName = ggVariables.getOemArchiveName(greengrassGroupName);
                log.info(String.join("", "Writing OEM file [", oemArchiveName, "]"));
                ioHelper.writeFile(oemArchiveName, getByteArrayOutputStream(oemVirtualTarEntries).get().toByteArray());
                ioHelper.makeExecutable(oemArchiveName);

                // Copy to S3 if necessary
                if (deploymentArguments.s3Bucket != null) {
                    S3Bucket s3Bucket = ImmutableS3Bucket.builder().bucket(deploymentArguments.s3Bucket).build();
                    S3Path s3Path = ImmutableS3Path.builder().path(deploymentArguments.s3Directory).build();
                    File file = new File(oemArchiveName);
                    v2S3Helper.copyToS3(s3Bucket, s3Path, file);
                }
            }
        }
    }

    private void addCredentialProviderFiles(Optional<List<VirtualTarEntry>> tarEntries, GreengrassGroupId greengrassGroupId, ThingName awsIotThingName, Region currentRegion, CreateRoleAliasResponse createRoleAliasResponse, HsiParameters nullableHsiParameters) {
        archiveHelper.addVirtualTarEntry(tarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "group-id.txt"), greengrassGroupId.getGroupId().getBytes(), normalFilePermissions);
        archiveHelper.addVirtualTarEntry(tarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "credential-provider-url.txt"), v2IotHelper.getCredentialProviderUrl().getBytes(), normalFilePermissions);
        archiveHelper.addVirtualTarEntry(tarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "thing-name.txt"), awsIotThingName.getName().getBytes(), normalFilePermissions);
        archiveHelper.addVirtualTarEntry(tarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "role-alias-name.txt"), createRoleAliasResponse.roleAlias().getBytes(), normalFilePermissions);
        archiveHelper.addVirtualTarEntry(tarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "region.txt"), currentRegion.id().getBytes(), normalFilePermissions);

        Optional<HsiParameters> optionalHsiParameters = Optional.ofNullable(nullableHsiParameters);

        if (!optionalHsiParameters.isPresent()) {
            return;
        }

        HsiParameters hsiParameters = optionalHsiParameters.get();

        hsiParameters.getPkcs11EngineForCurl().ifPresent(pkcs11Engine -> archiveHelper.addVirtualTarEntry(tarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "pkcs11-engine-for-curl.txt"), pkcs11Engine.getBytes(), normalFilePermissions));

        archiveHelper.addVirtualTarEntry(tarEntries, String.join("/", ggConstants.getConfigDirectoryPrefix(), "pkcs11-path-for-curl.txt"), hsiParameters.getCurlPkcsPath().getBytes(), normalFilePermissions);
    }

    private void addPrivateAndPublicKeyFiles(Optional<List<VirtualTarEntry>> tarEntries, KeysAndCertificate coreKeysAndCertificate, String privateKeyPath, String publicCertificatePath) {
        archiveHelper.addVirtualTarEntry(tarEntries, privateKeyPath, coreKeysAndCertificate.getKeyPair().privateKey().getBytes(), normalFilePermissions);
        archiveHelper.addVirtualTarEntry(tarEntries, publicCertificatePath, coreKeysAndCertificate.getCertificatePem().getPem().getBytes(), normalFilePermissions);
    }

    private void writeOemJsonOutput(List<VirtualTarEntry> oemVirtualTarEntries, String oemJsonFilename) {
        Map<String, String> oemJson = oemVirtualTarEntries.stream()
                .collect(Collectors.toMap(VirtualTarEntry::getFilename, entry -> new String(entry.getContent())));

        log.info(String.join("", "Writing OEM JSON output to [", oemJsonFilename, "]"));
        ioHelper.writeFile(oemJsonFilename, jsonHelper.toJson(oemJson).getBytes());
    }

    private void writePayload(Architecture architecture, ByteArrayOutputStream ggScriptTemplate) throws IOException {
        ggScriptTemplate.write(scriptHelper.generateGgScript(architecture).getBytes());
        ggScriptTemplate.write("PAYLOAD:\n".getBytes());
        ggScriptTemplate.write(getByteArrayOutputStream(installScriptVirtualTarEntries).get().toByteArray());
    }

    private void pushContainerIfNecessary(DeploymentArguments deploymentArguments, String imageId) {
        if (!deploymentArguments.pushContainer) {
            return;
        }

        String ecrEndpoint = ecrDockerHelper.getEcrProxyEndpoint();
        ecrDockerHelper.createEcrRepositoryIfNecessary();
        String shortEcrEndpoint = ecrEndpoint.substring("https://".length()); // Remove leading https://
        String shortEcrEndpointAndRepo = String.join("/", shortEcrEndpoint, deploymentArguments.ecrRepositoryNameString);

        try (DockerClient dockerClient = ecrDockerClientProvider.get()) {
            Try.run(() -> tagImage(deploymentArguments, imageId, shortEcrEndpointAndRepo, dockerClient))
                    .get();

            Try.run(() -> push(shortEcrEndpointAndRepo, dockerClient))
                    .onFailure(throwable -> Match(throwable).of(
                            Case($(instanceOf(DockerException.class)), this::logDockerPushFailedAndThrow),
                            Case($(), exceptionHelper::rethrowAsRuntimeException)))
                    .get();
        }

        String containerName = String.join("", shortEcrEndpointAndRepo, ":", deploymentArguments.groupName);
        log.info(String.join("", "Container pushed to [", containerName, "]"));

        /* Temporarily removed until Ubuntu issues are sorted out
        String baseDockerScriptName = String.join(".", "docker", deploymentArguments.groupName, "sh");
        String dockerShScriptName = String.join("/", BUILD_DIRECTORY, baseDockerScriptName);

        log.info("To run this container on Ubuntu on EC2 do the following:");
        log.info(" - Attach a role to the EC2 instance that gives it access to ECR");
        log.info(String.join("", " - Run the Docker script [" , dockerShScriptName , "]"));

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
            stringBuilder.append(String.join("", "  sudo $(AWS_DEFAULT_REGION=" , awsHelper.getCurrentRegion() , " aws ecr get-login | sed -e 's/-e none //')\n"));
            stringBuilder.append("  touch docker.configured\n");
            stringBuilder.append("fi\n");
            stringBuilder.append(String.join("", "sudo docker run -it --network host --privileged " , containerName , "\n"));

            ioHelper.writeFile(dockerShScriptName, stringBuilder.toString().getBytes());
            ioHelper.makeExecutable(dockerShScriptName);
        }
        */
    }

    private Void logDockerPushFailedAndThrow(DockerException dockerException) {
        log.error(String.join("", "Docker push failed [", dockerException.getMessage(), "]"));
        throw new RuntimeException(dockerException);
    }

    private void push(String shortEcrEndpointAndRepo, DockerClient dockerClient) throws DockerException, InterruptedException {
        dockerClient.push(shortEcrEndpointAndRepo, basicProgressHandler, ecrDockerClientProvider.getRegistryAuthSupplier().authFor(""));
    }

    private void tagImage(DeploymentArguments deploymentArguments, String imageId, String shortEcrEndpointAndRepo, DockerClient dockerClient) throws DockerException, InterruptedException {
        dockerClient.tag(imageId, String.join(":", shortEcrEndpointAndRepo, deploymentArguments.groupName));
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
     * @param coreRoleConf
     * @return
     */
    private Role createCoreRole(RoleConf coreRoleConf) {
        requireAssumeRolePolicy(coreRoleConf, "core");

        return createRoleFromRoleConfAndAttachPolicies("core", coreRoleConf);
    }

    private Optional<Architecture> getArchitecture(DeploymentArguments deploymentArguments) {
        return Optional.ofNullable(deploymentArguments.architecture);
    }

    private URL getArchitectureUrl(DeploymentArguments deploymentArguments) {
        Optional<Architecture> architecture = getArchitecture(deploymentArguments);
        Optional<URL> architectureUrlOptional = architecture.flatMap(Architecture::getResourceUrl);

        if (architecture.isPresent() && !architectureUrlOptional.isPresent()) {
            log.error(String.join("", "The GG software for your architecture [", architecture.get().getFilename(), "] is not available, please download it from the Greengrass console and put it in the [", architecture.get().getDIST(), "] directory"));
            System.exit(3);
        }

        return architectureUrlOptional.get();
    }

}
