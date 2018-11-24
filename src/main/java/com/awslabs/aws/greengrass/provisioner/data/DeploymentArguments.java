package com.awslabs.aws.greengrass.provisioner.data;

import com.beust.jcommander.Parameter;
import lombok.Getter;
import lombok.Setter;

public class DeploymentArguments extends Arguments {
    private final String SHORT_DEPLOYMENT_CONFIG_OPTION = "-d";
    @Getter
    private final String requiredOptionName = SHORT_DEPLOYMENT_CONFIG_OPTION;
    private final String LONG_ARCHITECTURE_OPTION = "--arch";
    private final String SHORT_ARCHITECTURE_OPTION = "-a";
    private final String LONG_BUILD_CONTAINER_OPTION = "--build-container";
    private final String SHORT_BUILD_CONTAINER_OPTION = "-c";
    private final String LONG_PUSH_CONTAINER_OPTION = "--push-container";
    private final String SHORT_PUSH_CONTAINER_OPTION = "-p";
    private final String LONG_ECR_REPOSITORY_NAME_OPTION = "--ecr-repository-name";
    private final String SHORT_ECR_REPOSITORY_NAME_OPTION = "-r";
    private final String ECR_REPOSITORY_DEFAULT_NAME = "greengrass";
    private final String LONG_ECR_IMAGE_NAME_OPTION = "--ecr-image-name";
    private final String SHORT_ECR_IMAGE_NAME_OPTION = "-i";
    private final String LONG_SCRIPT_OUTPUT_OPTION = "--script";
    private final String LONG_OEM_OUTPUT_OPTION = "--oem";
    private final String LONG_GGD_OUTPUT_OPTION = "--ggd";
    //    private static final String LONG_DOCKER_SCRIPT_OUTPUT_OPTION = "--docker-script";

    @Parameter(names = {LONG_ARCHITECTURE_OPTION, SHORT_ARCHITECTURE_OPTION}, description = "Architecture (X86_64, ARM32, ARM64)")
    public String architectureString;
    public Architecture architecture;
    @Parameter(names = {LONG_GROUP_NAME_OPTION, SHORT_GROUP_NAME_OPTION}, description = "The name of the Greengrass group")
    public String groupName;
    @Parameter(names = {SHORT_DEPLOYMENT_CONFIG_OPTION}, description = "The location of the deployment configuration file")
    public String deploymentConfigFilename;
    @Parameter(names = {LONG_BUILD_CONTAINER_OPTION, SHORT_BUILD_CONTAINER_OPTION}, description = "Build a container with this Greengrass configuration")
    public boolean buildContainer;
    @Parameter(names = {LONG_PUSH_CONTAINER_OPTION, SHORT_PUSH_CONTAINER_OPTION}, description = "Push a container with this Greengrass configuration to ECR (implies " + LONG_BUILD_CONTAINER_OPTION + ")")
    public boolean pushContainer;
    @Parameter(names = {LONG_ECR_REPOSITORY_NAME_OPTION, SHORT_ECR_REPOSITORY_NAME_OPTION}, description = "The name of the ECR repository (default: " + ECR_REPOSITORY_DEFAULT_NAME + ")")
    public String ecrRepositoryNameString = ECR_REPOSITORY_DEFAULT_NAME;
    @Parameter(names = {LONG_ECR_IMAGE_NAME_OPTION, SHORT_ECR_IMAGE_NAME_OPTION}, description = "The name of the ECR image (defaults to the group name)")
    public String ecrImageNameString;
    @Parameter(names = {LONG_SCRIPT_OUTPUT_OPTION}, description = "Generate an install script [gg.GROUP_NAME.sh]")
    public boolean scriptOutput;
    @Parameter(names = {LONG_OEM_OUTPUT_OPTION}, description = "Generate tar.gz with OEM files [oem.GROUP_NAME.sh] (config.json and certs)")
    public boolean oemOutput;
    @Parameter(names = {LONG_GGD_OUTPUT_OPTION}, description = "Generate Greengrass Device scripts [ggd.GROUP_NAME.sh]")
    public boolean ggdOutput;
    //    @Parameter(names = {LONG_DOCKER_SCRIPT_OUTPUT_OPTION}, description = "Generate a script to install Docker and run the Greengrass container [docker.GROUP_NAME.sh] (implies " + LONG_BUILD_CONTAINER_OPTION + ")")
    //    public boolean dockerScriptOutput;
    @Parameter(names = "--help", help = true)
    public boolean help;

    @Getter
    @Setter
    private String error;

    @Override
    public boolean isRequiredOptionSet() {
        return (deploymentConfigFilename != null);
    }
}
