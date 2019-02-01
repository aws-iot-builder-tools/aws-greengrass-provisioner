package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.docker.EcrDockerHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.DeploymentArgumentHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GlobalDefaultHelper;
import com.beust.jcommander.JCommander;
import com.oblac.nomen.Nomen;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.Optional;

@Slf4j
public class BasicDeploymentArgumentHelper implements DeploymentArgumentHelper {
    @Inject
    GlobalDefaultHelper globalDefaultHelper;
    @Inject
    EcrDockerHelper ecrDockerHelper;
    @Inject
    GGConstants ggConstants;

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
        deploymentArguments.ec2Launch = getValueOrDefault(deploymentArguments.ec2Launch, getBooleanDefault(defaults, "conf.ec2Launch"));
        deploymentArguments.dockerLaunch = getValueOrDefault(deploymentArguments.dockerLaunch, getBooleanDefault(defaults, "conf.dockerLaunch"));
        deploymentArguments.hsiSoftHsm2 = getValueOrDefault(deploymentArguments.hsiSoftHsm2, getBooleanDefault(defaults, "conf.hsiSoftHsm2"));

        if (deploymentArguments.ec2Launch && deploymentArguments.dockerLaunch) {
            throw new RuntimeException("The EC2 and Docker launch options are mutually exclusive.  Only specify one of them.");
        }

        if (deploymentArguments.ec2Launch) {
            // If we are launching an EC2 instance we need to build the scripts
            deploymentArguments.architectureString = Architecture.X86_64.toString();
            deploymentArguments.scriptOutput = true;

            if (deploymentArguments.buildContainer) {
                throw new RuntimeException("Can't build a container for an EC2 launch");
            }
        }

        if (deploymentArguments.hsiSoftHsm2 && deploymentArguments.dockerLaunch) {
            throw new RuntimeException("HSI with SoftHSM2 is not supported in Docker yet");
        }

        if (deploymentArguments.hsiSoftHsm2) {
            // Force script output with HSI SoftHSM2
            deploymentArguments.scriptOutput = true;
        }

        if (deploymentArguments.dockerLaunch) {
            // Force OEM file output with Docker launch
            deploymentArguments.oemOutput = true;
        }

        if (deploymentArguments.groupName == null) {
            if (!deploymentArguments.ec2Launch && !deploymentArguments.dockerLaunch) {
                throw new RuntimeException("Group name is required for all operations");
            }

            deploymentArguments.groupName = Nomen.est().separator("-").space("-").adjective().pokemon().get();
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
        }

        if (deploymentArguments.buildContainer == true) {
            // If we are building a container we need the OEM files
            deploymentArguments.oemOutput = true;
        }

        /*
        if (deploymentArguments.dockerScriptOutput == true) {
            // If they want Docker script output then we have to build a container
            deploymentArguments.buildContainer = true;
        }
        */

        if (deploymentArguments.pushContainer == true) {
            // If they want to push a container then we have to build it
            deploymentArguments.buildContainer = true;
        }

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

        return deploymentArguments;
    }
}
