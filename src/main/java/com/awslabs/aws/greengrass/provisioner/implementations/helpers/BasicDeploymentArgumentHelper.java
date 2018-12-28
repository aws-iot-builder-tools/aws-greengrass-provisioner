package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.docker.NormalDockerHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.DeploymentArgumentHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GlobalDefaultHelper;
import com.beust.jcommander.JCommander;
import com.oblac.nomen.Nomen;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.Optional;

@Slf4j
public class BasicDeploymentArgumentHelper implements DeploymentArgumentHelper {
    @Inject
    GlobalDefaultHelper globalDefaultHelper;
    @Inject
    NormalDockerHelper normalDockerHelper;
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

        try {
            return Optional.of(defaults.get().getString(name));
        } catch (ConfigException.Missing e) {
            // Ignore
        }

        return Optional.empty();
    }

    private Optional<Boolean> getBooleanDefault(Optional<Config> defaults, String name) {
        // Get the defaults from a config file
        if (!defaults.isPresent()) {
            return Optional.empty();
        }

        try {
            return Optional.of(defaults.get().getBoolean(name));
        } catch (ConfigException.Missing e) {
            // Ignore
        }

        return Optional.empty();
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

        if (deploymentArguments.ec2Launch && deploymentArguments.dockerLaunch) {
            deploymentArguments.setError("The EC2 and Docker launch options are mutually exclusive.  Only specify one of them.");
            return deploymentArguments;
        }

        if (deploymentArguments.ec2Launch) {
            // If we are launching an EC2 instance we need to build the scripts
            deploymentArguments.architectureString = Architecture.X86_64.toString();
            deploymentArguments.scriptOutput = true;

            if (deploymentArguments.buildContainer) {
                deploymentArguments.setError("Can't build a container for an EC2 launch");
                return deploymentArguments;
            }
        }

        if (deploymentArguments.dockerLaunch) {
            // Force OEM file output with Docker launch
            deploymentArguments.oemOutput = true;
        }

        if (deploymentArguments.groupName == null) {
            if (!deploymentArguments.ec2Launch && !deploymentArguments.dockerLaunch) {
                deploymentArguments.setError("Group name is required for all operations");
                return deploymentArguments;
            }

            deploymentArguments.groupName = Nomen.est().separator("-").space("-").adjective().pokemon().get();
            log.info("No group name specified, group name auto-generated [" + deploymentArguments.groupName + "]");
        }

        // Depends on group name being set
        deploymentArguments.ecrImageNameString = getValueOrDefault(deploymentArguments.ecrImageNameString, Optional.of(deploymentArguments.groupName));

        if (deploymentArguments.architectureString != null) {
            try {
                deploymentArguments.architecture = Architecture.valueOf(deploymentArguments.architectureString);
            } catch (IllegalArgumentException e) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("[" + deploymentArguments.architectureString + "] is not a valid architecture.");
                stringBuilder.append("\r\n");
                stringBuilder.append("Valid options are: " + Architecture.getList());
                deploymentArguments.setError(stringBuilder.toString());
                return deploymentArguments;
            }
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
            deploymentArguments.setError("Architecture must be specified when building a container or installation script");
            return deploymentArguments;
        }

        if (deploymentArguments.deploymentConfigFilename == null) {
            deploymentArguments.setError("A deployment configuration file name is required");
            return deploymentArguments;
        }

        if (deploymentArguments.buildContainer) {
            if (!normalDockerHelper.getDockerfileForArchitecture(deploymentArguments.architecture).exists()) {
                deploymentArguments.setError("No dockerfile exists for architecture [" + deploymentArguments.architecture.toString() + "]");
                return deploymentArguments;
            }

            if (!normalDockerHelper.isDockerAvailable()) {
                deploymentArguments.setError("Docker is not available, cannot build a container");
                return deploymentArguments;
            }
        }

        return deploymentArguments;
    }
}
