package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.docker.EcrDockerHelper;
import com.awslabs.aws.greengrass.provisioner.implementations.clientproviders.S3ClientProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.beust.jcommander.JCommander;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLocationResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import javax.inject.Inject;
import java.util.Optional;

public class BasicDeploymentArgumentHelper implements DeploymentArgumentHelper {
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
    S3ClientProvider s3ClientProvider;
    @Inject
    SshHelper sshHelper;

    @Inject
    public BasicDeploymentArgumentHelper() {
    }

    private String getValueOrDefault(String value, Optional<String> defaultValue) {
        return value == null ? defaultValue.orElse(null) : value;
    }

    private boolean getValueOrDefault(boolean value, Optional<Boolean> defaultValue) {
        return value == false ? defaultValue.orElse(false) : value;
    }

    private Optional<String> getStringDefault(Optional<Config> defaults, String name) {
        // Get the defaults from a config file
        if (!defaults.isPresent()) {
            return Optional.empty();
        }

        return Try.of(() -> Optional.of(defaults.get().getString(name)))
                .recover(ConfigException.Missing.class, throwable -> Optional.empty())
                .get();
    }

    private Optional<Boolean> getBooleanDefault(Optional<Config> defaults, String name) {
        // Get the defaults from a config file
        if (!defaults.isPresent()) {
            return Optional.empty();
        }

        return Try.of(() -> Optional.of(defaults.get().getBoolean(name)))
                .recover(ConfigException.Missing.class, throwable -> Optional.empty())
                .get();
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

        deploymentArguments.architectureString = getValueOrDefault(deploymentArguments.architectureString, getStringDefault(defaults, "conf.architecture"));
        deploymentArguments.groupName = getValueOrDefault(deploymentArguments.groupName, getStringDefault(defaults, "conf.groupName"));
        deploymentArguments.deploymentConfigFilename = getValueOrDefault(deploymentArguments.deploymentConfigFilename, getStringDefault(defaults, "conf.deploymentConfig"));
        deploymentArguments.buildContainer = getValueOrDefault(deploymentArguments.buildContainer, getBooleanDefault(defaults, "conf.containerBuild"));
        deploymentArguments.pushContainer = getValueOrDefault(deploymentArguments.pushContainer, getBooleanDefault(defaults, "conf.containerPush"));
        deploymentArguments.scriptOutput = getValueOrDefault(deploymentArguments.scriptOutput, getBooleanDefault(defaults, "conf.scriptBuild"));
        deploymentArguments.oemOutput = getValueOrDefault(deploymentArguments.oemOutput, getBooleanDefault(defaults, "conf.oemBuild"));
        deploymentArguments.ggdOutput = getValueOrDefault(deploymentArguments.ggdOutput, getBooleanDefault(defaults, "conf.ggdBuild"));
        // deploymentArguments.dockerScriptOutput = getValueOrDefault(deploymentArguments.dockerScriptOutput, getBooleanDefault(defaults, "conf.dockerScriptBuild"));
        deploymentArguments.ecrRepositoryNameString = getValueOrDefault(deploymentArguments.ecrRepositoryNameString, getStringDefault(defaults, "conf.ecrRepositoryName"));
        deploymentArguments.noSystemD = getValueOrDefault(deploymentArguments.noSystemD, getBooleanDefault(defaults, "conf.noSystemD"));
        deploymentArguments.ec2Launch = getValueOrDefault(deploymentArguments.ec2Launch, getStringDefault(defaults, "conf.ec2Launch"));
        deploymentArguments.dockerLaunch = getValueOrDefault(deploymentArguments.dockerLaunch, getBooleanDefault(defaults, "conf.dockerLaunch"));
        deploymentArguments.launch = getValueOrDefault(deploymentArguments.launch, getStringDefault(defaults, "conf.launch"));
        deploymentArguments.s3Bucket = getValueOrDefault(deploymentArguments.s3Bucket, getStringDefault(defaults, "conf.s3Bucket"));
        deploymentArguments.s3Directory = getValueOrDefault(deploymentArguments.s3Directory, getStringDefault(defaults, "conf.s3Directory"));
        deploymentArguments.csr = getValueOrDefault(deploymentArguments.csr, getStringDefault(defaults, "conf.csr"));
        deploymentArguments.certificateArn = getValueOrDefault(deploymentArguments.certificateArn, getStringDefault(defaults, "conf.certificateArn"));

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
            log.info("No group name specified, group name auto-generated [" + deploymentArguments.groupName + "]");
        }

        // Depends on group name being set
        deploymentArguments.ecrImageNameString = getValueOrDefault(deploymentArguments.ecrImageNameString, Optional.of(deploymentArguments.groupName));

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
        }

        if (deploymentArguments.buildContainer) {
            if (!ecrDockerHelper.getDockerfileForArchitecture(deploymentArguments.architecture).exists()) {
                throw new RuntimeException("No dockerfile exists for architecture [" + deploymentArguments.architecture.toString() + "]");
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

            if ((!deploymentArguments.oemOutput) && (!deploymentArguments.scriptOutput) && (!deploymentArguments.ggdOutput)) {
                throw new RuntimeException("S3 destination specified but not output files will be generated. You must specify at least one of OEM, script, and GGD output to use S3.");
            }

            S3Client s3Client = s3ClientProvider.get();

            // At this point the S3 options look good, make sure that the bucket exists
            GetBucketLocationRequest getBucketLocationRequest = GetBucketLocationRequest.builder()
                    .bucket(deploymentArguments.s3Bucket)
                    .build();

            Try.of(() -> s3Client.getBucketLocation(getBucketLocationRequest))
                    .recover(NoSuchBucketException.class, this::throwBucketDoesNotExistError)
                    .get();
        } else if (deploymentArguments.s3Directory != null) {
            throw new RuntimeException("S3 directory was specified with no S3 bucket. S3 bucket is required.");
        }

        if (deploymentArguments.launch != null) {
            String[] userAndHost = sshHelper.getUserAndHost("launch destination", deploymentArguments.launch);

            deploymentArguments.launchUser = userAndHost[0];
            deploymentArguments.launchHost = userAndHost[1];
        }

        return deploymentArguments;
    }

    private GetBucketLocationResponse throwBucketDoesNotExistError(Throwable throwable) {
        throw new RuntimeException("Specified S3 bucket does not exist. The bucket must already exist before running the provisioner");
    }
}
