package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.docker.EcrDockerHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.awslabs.s3.helpers.interfaces.V2S3Helper;
import com.beust.jcommander.JCommander;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.Bucket;

import javax.inject.Inject;
import java.util.Optional;
import java.io.File;
import java.nio.file.*;

public class BasicDeploymentArgumentHelper implements DeploymentArgumentHelper {
    public static final int DEFAULT_MQTT_PORT = 8883;
    private final Logger log = LoggerFactory.getLogger(BasicDeploymentArgumentHelper.class);
    @Inject
    GlobalDefaultHelper globalDefaultHelper;
    @Inject
    EcrDockerHelper ecrDockerHelper;
    @Inject
    GGConstants ggConstants;
    @Inject
    IoHelper ioHelper;
    @Inject
    V2S3Helper s3Helper;
    @Inject
    SshHelper sshHelper;
    @Inject
    TypeSafeConfigHelper typeSafeConfigHelper;

    @Inject
    public BasicDeploymentArgumentHelper() {
    }

    @Override
    public void displayUsage() {
        DeploymentArguments deploymentArguments = new DeploymentArguments();

        JCommander.newBuilder()
                .addObject(deploymentArguments)
                .build()
                .usage();
    }

    @Override
    public DeploymentArguments parseArguments(String[] args) {
        DeploymentArguments deploymentArguments = new DeploymentArguments();

        JCommander.newBuilder()
                .addObject(deploymentArguments)
                .build()
                .parse(args);

        Optional<Config> defaults = globalDefaultHelper.getGlobalDefaults(ggConstants.getDefaultsConf());

        deploymentArguments.architectureString = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.architectureString, typeSafeConfigHelper.getStringDefault(defaults, "conf.architecture"));
        deploymentArguments.groupName = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.groupName, typeSafeConfigHelper.getStringDefault(defaults, "conf.groupName"));
        deploymentArguments.deploymentConfigFilename = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.deploymentConfigFilename, typeSafeConfigHelper.getStringDefault(defaults, "conf.deploymentConfig"));
        deploymentArguments.buildContainer = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.buildContainer, typeSafeConfigHelper.getBooleanDefault(defaults, "conf.containerBuild"));
        deploymentArguments.pushContainer = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.pushContainer, typeSafeConfigHelper.getBooleanDefault(defaults, "conf.containerPush"));
        deploymentArguments.scriptOutput = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.scriptOutput, typeSafeConfigHelper.getBooleanDefault(defaults, "conf.scriptBuild"));
        deploymentArguments.oemOutput = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.oemOutput, typeSafeConfigHelper.getBooleanDefault(defaults, "conf.oemBuild"));
        // deploymentArguments.dockerScriptOutput = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.dockerScriptOutput, typeSafeConfigHelper.getBooleanDefault(defaults, "conf.dockerScriptBuild"));
        deploymentArguments.ecrRepositoryNameString = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.ecrRepositoryNameString, typeSafeConfigHelper.getStringDefault(defaults, "conf.ecrRepositoryName"));
        deploymentArguments.noSystemD = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.noSystemD, typeSafeConfigHelper.getBooleanDefault(defaults, "conf.noSystemD"));
        deploymentArguments.ec2Launch = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.ec2Launch, typeSafeConfigHelper.getStringDefault(defaults, "conf.ec2Launch"));
        deploymentArguments.dockerLaunch = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.dockerLaunch, typeSafeConfigHelper.getBooleanDefault(defaults, "conf.dockerLaunch"));
        deploymentArguments.launch = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.launch, typeSafeConfigHelper.getStringDefault(defaults, "conf.launch"));
        deploymentArguments.s3Bucket = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.s3Bucket, typeSafeConfigHelper.getStringDefault(defaults, "conf.s3Bucket"));
        deploymentArguments.s3Directory = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.s3Directory, typeSafeConfigHelper.getStringDefault(defaults, "conf.s3Directory"));
        deploymentArguments.csr = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.csr, typeSafeConfigHelper.getStringDefault(defaults, "conf.csr"));
        deploymentArguments.certificateArn = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.certificateArn, typeSafeConfigHelper.getStringDefault(defaults, "conf.certificateArn"));
        deploymentArguments.mqttPort = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.mqttPort, typeSafeConfigHelper.getIntegerDefault(defaults, "conf.mqttPort"));

        if (deploymentArguments.pushContainer) {
            // If they want to push a container then we have to build it
            deploymentArguments.buildContainer = true;
        }

        if (deploymentArguments.ec2Launch != null) {
            deploymentArguments.ec2LinuxVersion = getEc2LinuxVersion(deploymentArguments.ec2Launch);
        }

        if ((deploymentArguments.ec2LinuxVersion != null) && deploymentArguments.dockerLaunch && (deploymentArguments.launch != null)) {
            throw new RuntimeException("The EC2 launch, Docker launch, and launch options are mutually exclusive.  Only specify one of them.");
        }

        if (deploymentArguments.ec2LinuxVersion != null) {
            // If we are launching an EC2 instance we need to build the scripts
            if (deploymentArguments.architectureString == null) {
                log.warn("No architecture specified for EC2, defaulting to X86-64");
                deploymentArguments.architectureString = Architecture.X86_64.toString();
            }

            deploymentArguments.scriptOutput = true;

            if (deploymentArguments.buildContainer) {
                throw new RuntimeException("Can't build a container for an EC2 launch");
            }
        }

        if ((deploymentArguments.hsiParameters != null) && deploymentArguments.dockerLaunch) {
            throw new RuntimeException("HSI is not supported in Docker yet");
        }

        if ((deploymentArguments.hsiParameters != null) && (deploymentArguments.certificateArn == null)) {
            throw new RuntimeException("Certificate ARN must be specified when using HSI");
        }

        if ((deploymentArguments.dockerLaunch) || (deploymentArguments.buildContainer)) {
            // Force OEM file output with Docker launch or container build
            deploymentArguments.oemOutput = true;
        }

        if ((deploymentArguments.buildContainer) && (ioHelper.isRunningInDocker())) {
            throw new RuntimeException("Can't build a container from inside of Docker yet");
        }

        if ((deploymentArguments.launch != null)) {
            // Force script file output with Docker launch
            deploymentArguments.scriptOutput = true;
        }

        if (deploymentArguments.groupName == null) {
            if ((deploymentArguments.ec2LinuxVersion != null) && !deploymentArguments.dockerLaunch && (deploymentArguments.launch != null)) {
                throw new RuntimeException("Group name is required for all operations");
            }

            deploymentArguments.groupName = ioHelper.getRandomName();
            // Filter out dot characters, sometimes the library uses the value "jr." which is not allowed in a group name
            deploymentArguments.groupName = deploymentArguments.groupName.replaceAll("\\.", "");
            // Filter out normal apostrophes, and special apostrophes (from "farfetch’d")
            deploymentArguments.groupName = deploymentArguments.groupName.replaceAll("'", "");
            deploymentArguments.groupName = deploymentArguments.groupName.replaceAll("’", "");
            log.info(String.join("", "No group name specified, group name auto-generated [", deploymentArguments.groupName, "]"));
        }

        // Depends on group name being set
        deploymentArguments.ecrImageNameString = typeSafeConfigHelper.getValueOrDefault(deploymentArguments.ecrImageNameString, Optional.of(deploymentArguments.groupName));

        if (deploymentArguments.architectureString != null) {
            deploymentArguments.architecture = getArchitecture(deploymentArguments.architectureString);

            if (deploymentArguments.architecture.equals(Architecture.ARM32)) {
                log.warn("Legacy ARM32 architecture value detected, switching to ARMV7L_RASPBIAN");
                deploymentArguments.architecture = Architecture.ARMV7L_RASPBIAN;
            } else if (deploymentArguments.architecture.equals(Architecture.ARM64)) {
                log.warn("Legacy ARM64 architecture value detected, switching to ARMV8");
                deploymentArguments.architecture = Architecture.ARMV8;
            }
        }

        if ((deploymentArguments.ec2LinuxVersion != null) &&
                (!deploymentArguments.architecture.equals(Architecture.ARMV8) && (!deploymentArguments.architecture.equals(Architecture.X86_64)))) {
            throw new RuntimeException("EC2 launch supports X86_64 and ARMV8 architectures only");
        }

        /*
        if (deploymentArguments.dockerScriptOutput == true) {
            // If they want Docker script output then we have to build a container
            deploymentArguments.buildContainer = true;
        }
        */

        if ((deploymentArguments.buildContainer || deploymentArguments.scriptOutput) && (deploymentArguments.architecture == null)) {
            throw new RuntimeException("Architecture must be specified when building a container or installation script");
        }

        if (deploymentArguments.deploymentConfigFilename == null) {
            throw new RuntimeException("A deployment configuration file name is required");
        } else {
            if (!deploymentArguments.deploymentConfigFilename.equals(DeploymentHelper.EMPTY)) {
                File deploymentConfigFile = new File(deploymentArguments.deploymentConfigFilename);
                deploymentArguments.deploymentConfigFolderPath = deploymentConfigFile.getParent();
                Path deploymentConfigFolderPath = Paths.get(deploymentArguments.deploymentConfigFolderPath);
                deploymentArguments.functionConfigPath = String.join("/", deploymentConfigFolderPath.getParent().toString(), "functions");
                deploymentArguments.connectorConfigPath = String.join("/", deploymentConfigFolderPath.getParent().toString(), "connectors");    
            }
        }

        if (deploymentArguments.buildContainer) {
            if (!ecrDockerHelper.getDockerfileForArchitecture(deploymentArguments.architecture).exists()) {
                throw new RuntimeException(String.join("", "No dockerfile exists for architecture [", deploymentArguments.architecture.toString(), "]"));
            }
        }

        if (deploymentArguments.buildContainer || deploymentArguments.dockerLaunch) {
            if (!ecrDockerHelper.isDockerAvailable()) {
                throw new RuntimeException("Docker is not available, cannot continue");
            }
        }

        if (deploymentArguments.s3Bucket != null) {
            if (deploymentArguments.oemJsonOutput != null) {
                throw new RuntimeException("S3 output is not supported when using OEM JSON output mode");
            }

            if (deploymentArguments.s3Directory == null) {
                throw new RuntimeException("S3 bucket specified without S3 directory. S3 directory is required. Set S3 directory to '/' to store the output in the root of the bucket.");
            }

            // At this point the S3 options look good, make sure that the bucket exists
            if (!s3Helper.bucketExists(Bucket.builder().name(deploymentArguments.s3Bucket).build())) {
                throw new RuntimeException("Specified S3 bucket does not exist. The bucket must already exist before running the provisioner");
            }
        } else if (deploymentArguments.s3Directory != null) {
            throw new RuntimeException("S3 directory was specified with no S3 bucket. S3 bucket is required.");
        }

        if (deploymentArguments.launch != null) {
            String[] userAndHost = sshHelper.getUserAndHost("launch destination", deploymentArguments.launch);

            deploymentArguments.launchUser = userAndHost[0];
            deploymentArguments.launchHost = userAndHost[1];
        }

        if (deploymentArguments.mqttPort == 0) {
            // No value was set, use the default
            log.warn(String.join("", "No MQTT port value was set, using default [", String.valueOf(DEFAULT_MQTT_PORT), "]"));
            deploymentArguments.mqttPort = DEFAULT_MQTT_PORT;
        }

        if (((!deploymentArguments.scriptOutput) && (!deploymentArguments.oemOutput))
                && (deploymentArguments.mqttPort != DEFAULT_MQTT_PORT)) {
            // No script and OEM files are being generated but a non-default MQTT port was specified
            throw new RuntimeException(String.join("", "A non-default MQTT port was specified [", String.valueOf(deploymentArguments.mqttPort), "] but no output files are being generated. Can not continue. The MQTT port can only be set in the config.json. It cannot be changed from the cloud."));
        }

        return deploymentArguments;
    }
}
