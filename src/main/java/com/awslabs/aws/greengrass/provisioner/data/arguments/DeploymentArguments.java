package com.awslabs.aws.greengrass.provisioner.data.arguments;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.data.EC2LinuxVersion;
import com.beust.jcommander.Parameter;

public class DeploymentArguments extends Arguments {
    public static final String SHORT_DEPLOYMENT_CONFIG_OPTION = "-d";
    public static final String LONG_OEM_OUTPUT_OPTION = "--oem";
    public static final String LONG_OEM_JSON_OUTPUT_OPTION = "--oem-json";
    public static final String LONG_EC2_LAUNCH_OPTION = "--ec2-launch";
    public static final String LONG_CORE_ROLE_NAME_OPTION = "--core-role-name";
    public static final String LONG_SERVICE_ROLE_EXISTS_OPTION = "--service-role-exists";
    public static final String LONG_CORE_POLICY_NAME_OPTION = "--core-policy-name";
    public static final String LONG_FORCE_CREATE_NEW_KEYS_OPTION = "--force-create-new-keys";
    public static final String LONG_CSR_OPTION = "--csr";
    public static final String LONG_CERTIFICATE_ARN_OPTION = "--certificate-arn";
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
    private final String LONG_NO_SYSTEMD_OPTION = "--no-systemd";
    private final String LONG_LAUNCH_OPTION = "--launch";
    private final String LONG_DOCKER_LAUNCH_OPTION = "--docker-launch";
    private final String LONG_HSI_OPTION = "--hsi";
    private final String LONG_S3_BUCKET_OPTION = "--s3-bucket";
    private final String LONG_S3_DIRECTORY_OPTION = "--s3-directory";
    private final String LONG_MQTT_PORT_OPTION = "--mqtt-port";
    @Parameter(names = {LONG_ARCHITECTURE_OPTION, SHORT_ARCHITECTURE_OPTION}, description = "Architecture (X86_64, ARM32, ARM64)")
    public String architectureString;
    //    private static final String LONG_DOCKER_SCRIPT_OUTPUT_OPTION = "--docker-script";
    public Architecture architecture;
    @Parameter(names = {LONG_GROUP_NAME_OPTION, SHORT_GROUP_NAME_OPTION}, description = "The name of the Greengrass group")
    public String groupName;
    @Parameter(names = {SHORT_DEPLOYMENT_CONFIG_OPTION}, description = "The location of the deployment configuration file")
    public String deploymentConfigFilename;
    public String deploymentConfigFolderPath;
    public String functionConfigPath;
    public String connectorConfigPath;
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
    @Parameter(names = {LONG_OEM_JSON_OUTPUT_OPTION}, description = "Generate JSON with OEM files and store it in the specified location")
    public String oemJsonOutput;
    @Parameter(names = {LONG_NO_SYSTEMD_OPTION}, description = "Disable systemd support in config.json")
    public boolean noSystemD;
    @Parameter(names = {LONG_EC2_LAUNCH_OPTION}, description = "Launch an EC2 instance for this deployment")
    public String ec2Launch;
    public EC2LinuxVersion ec2LinuxVersion;
    @Parameter(names = {LONG_DOCKER_LAUNCH_OPTION}, description = "Launch an this deployment in a Docker container locally")
    public boolean dockerLaunch;
    @Parameter(names = {LONG_LAUNCH_OPTION}, description = "Launch the bootstrapping script on a system via SSH")
    public String launch;
    public String launchUser;
    public String launchHost;
    @Parameter(names = {LONG_HSI_OPTION}, description = "Use Greengrass Hardware Security Integration (HSI)", converter = HsiParametersConverter.class)
    public HsiParameters hsiParameters;
    //    @Parameter(names = {LONG_DOCKER_SCRIPT_OUTPUT_OPTION}, description = "Generate a script to install Docker and run the Greengrass container [docker.GROUP_NAME.sh] (implies " + LONG_BUILD_CONTAINER_OPTION + ")")
    //    public boolean dockerScriptOutput;
    @Parameter(names = {LONG_S3_BUCKET_OPTION}, description = "S3 bucket to store output files")
    public String s3Bucket;
    @Parameter(names = {LONG_S3_DIRECTORY_OPTION}, description = "S3 directory inside the bucket to store output files")
    public String s3Directory;
    @Parameter(names = {LONG_CORE_ROLE_NAME_OPTION}, description = "The name of an existing role to use for the group")
    public String coreRoleName;
    @Parameter(names = {LONG_SERVICE_ROLE_EXISTS_OPTION}, description = "The service role already exists, do not try to create it")
    public boolean serviceRoleExists;
    @Parameter(names = {LONG_CORE_POLICY_NAME_OPTION}, description = "The name of an existing IoT policy to use for the group")
    public String corePolicyName;
    @Parameter(names = {LONG_FORCE_CREATE_NEW_KEYS_OPTION}, description = "Force creation of new keys for the core if they cannot be found")
    public boolean forceCreateNewKeysOption;
    @Parameter(names = {LONG_CSR_OPTION}, description = "A CSR to sign and use for the core certificate")
    public String csr;
    @Parameter(names = {LONG_CERTIFICATE_ARN_OPTION}, description = "The full ARN of an existing certificate to use for the core")
    public String certificateArn;
    @Parameter(names = {LONG_MQTT_PORT_OPTION}, description = "The MQTT port that the Greengrass core should listen on")
    public int mqttPort;
    @Parameter(names = "--help", help = true)
    private boolean help;

    @Override
    public String getRequiredOptionName() {
        return SHORT_DEPLOYMENT_CONFIG_OPTION;
    }

    @Override
    public boolean isRequiredOptionSet() {
        return (deploymentConfigFilename != null);
    }

    @Override
    public boolean isHelp() {
        return help;
    }
}
