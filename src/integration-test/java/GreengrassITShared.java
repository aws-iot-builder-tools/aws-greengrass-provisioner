import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.data.arguments.Arguments;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.arguments.UpdateArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ThreadHelper;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

class GreengrassITShared {
    static final File NODEJS_SDK_FROM_BUILD = new File("./build/foundation/aws-greengrass-core-sdk-js.zip");
    static final File NODEJS_SDK_REQUIRED_FOR_TESTING = new File("foundation/aws-greengrass-core-sdk-js.zip");
    static final File MASTER_DEPLOYMENTS = new File("../aws-greengrass-lambda-functions/deployments");
    static final File MASTER_FUNCTIONS = new File("../aws-greengrass-lambda-functions/functions");
    static final File MASTER_FOUNDATION = new File("../aws-greengrass-lambda-functions/foundation");
    static final String TIMMATTISON_AWS_GREENGRASS_PROVISIONER = "timmattison/aws-greengrass-provisioner";
    static final File TEMP_DEPLOYMENTS = new File("deployments");
    static final File TEMP_FUNCTIONS = new File("functions");
    static final File TEMP_FOUNDATION = new File("foundation");
    static final File TEMP_CREDENTIALS = new File("credentials");
    /**
     * This is used to generate files that can be used in end-to-end tests
     */
    static final String OEM_OUTPUT_OPTION = DeploymentArguments.LONG_OEM_OUTPUT_OPTION;
    static final String DEPLOYMENT_OPTION = String.join(" ", OEM_OUTPUT_OPTION, "-d deployments/");
    static final String FORCE_CREATE_NEW_KEYS_DEPLOYMENT_OPTION = String.join(" ", DeploymentArguments.LONG_FORCE_CREATE_NEW_KEYS_OPTION, DEPLOYMENT_OPTION);
    static final String ARM32_OPTION = String.join(" ", Arguments.SHORT_ARCHITECTURE_OPTION, "ARM32");
    static final String EC2_LAUNCH_OPTION = DeploymentArguments.LONG_EC2_LAUNCH_OPTION;
    static final String CDD_SKELETON_DEPLOYMENT = "cdd-skeleton.conf";
    static final String PYTHON2_HELLO_WORLD_DEPLOYMENT = "python2-hello-world.conf";
    static final String PYTHON3_HELLO_WORLD_DEPLOYMENT = "python3-hello-world.conf";
    static final String LIFX_DEPLOYMENT = "lifx.conf";
    static final String NODE_WEBSERVER_DEPLOYMENT = "web-server-node.conf";
    static final String NODE_HELLO_WORLD_DEPLOYMENT = "node-hello-world.conf";
    static final String X86_64_SAMPLE_C_DEPLOYMENT = "X86_64SampleC.conf";
    static final String ALL_HELLO_WORLD_DEPLOYMENT = "all-hello-world.conf";
    static final String FAIL_DEPLOYMENT = "FAKE";
    private final String GROUP_NAME = AwsGreengrassProvisioner.getInjector().getInstance(IoHelper.class).getUuid();
    private final IoHelper ioHelper = AwsGreengrassProvisioner.getInjector().getInstance(IoHelper.class);

    static void cleanDirectories() throws IOException {
        FileUtils.deleteDirectory(TEMP_DEPLOYMENTS);
        FileUtils.deleteDirectory(TEMP_FUNCTIONS);
        FileUtils.deleteDirectory(TEMP_FOUNDATION);
        FileUtils.deleteDirectory(TEMP_CREDENTIALS);
    }

    static ThreadHelper getThreadHelper() {
        return AwsGreengrassProvisioner.getInjector().getInstance(ThreadHelper.class);
    }

    static void beforeTestSetup() throws IOException {
        cleanDirectories();

        FileUtils.copyDirectory(GreengrassITShared.MASTER_DEPLOYMENTS, GreengrassITShared.TEMP_DEPLOYMENTS);
        FileUtils.copyDirectory(GreengrassITShared.MASTER_FUNCTIONS, GreengrassITShared.TEMP_FUNCTIONS);
        FileUtils.copyDirectory(GreengrassITShared.MASTER_FOUNDATION, GreengrassITShared.TEMP_FOUNDATION);
        FileUtils.copyFile(GreengrassITShared.NODEJS_SDK_FROM_BUILD, GreengrassITShared.NODEJS_SDK_REQUIRED_FOR_TESTING);
    }

    String getGroupName() {
        return GROUP_NAME;
    }

    private String getGroupOption(Optional<String> groupName) {
        return String.join(" ", "-g", groupName.orElse(GROUP_NAME));
    }

    String getCddSkeletonDeploymentCommand(Optional<String> groupName) {
        return String.join("", FORCE_CREATE_NEW_KEYS_DEPLOYMENT_OPTION, CDD_SKELETON_DEPLOYMENT, " ", getGroupOption(groupName));
    }

    String getFailDeploymentCommand(Optional<String> groupName) {
        return String.join("", FORCE_CREATE_NEW_KEYS_DEPLOYMENT_OPTION, FAIL_DEPLOYMENT, " ", getGroupOption(groupName));
    }

    String getAddFunctionCommand(String groupName, String functionName, String functionAlias) {
        return String.join(" ",
                getGroupOption(Optional.of(groupName)),
                UpdateArguments.LONG_UPDATE_GROUP_OPTION,
                UpdateArguments.LONG_ADD_FUNCTION_OPTION, functionName,
                UpdateArguments.LONG_FUNCTION_ALIAS_OPTION, functionAlias);
    }

    String getRemoveFunctionCommand(String groupName, String functionName, String functionAlias) {
        return String.join(" ",
                getGroupOption(Optional.of(groupName)),
                UpdateArguments.LONG_UPDATE_GROUP_OPTION,
                UpdateArguments.LONG_REMOVE_FUNCTION_OPTION, functionName,
                UpdateArguments.LONG_FUNCTION_ALIAS_OPTION, functionAlias);
    }

    String getPython2HelloWorldDeploymentCommand(Optional<String> groupName) {
        return String.join("", FORCE_CREATE_NEW_KEYS_DEPLOYMENT_OPTION, PYTHON2_HELLO_WORLD_DEPLOYMENT, " ", getGroupOption(groupName));
    }

    String getPython3HelloWorldDeploymentCommand(Optional<String> groupName) {
        return String.join("", FORCE_CREATE_NEW_KEYS_DEPLOYMENT_OPTION, PYTHON3_HELLO_WORLD_DEPLOYMENT, " ", getGroupOption(groupName));
    }

    String getPython3HelloWorldDeploymentCommandWithoutForceNewKeys(Optional<String> groupName) {
        return String.join("", DEPLOYMENT_OPTION, PYTHON3_HELLO_WORLD_DEPLOYMENT, " ", getGroupOption(groupName));
    }

    String getLifxDeploymentCommand(Optional<String> groupName) {
        return String.join("", FORCE_CREATE_NEW_KEYS_DEPLOYMENT_OPTION, LIFX_DEPLOYMENT, " ", getGroupOption(groupName));
    }

    String getNodeWebserverDeploymentCommand(Optional<String> groupName) {
        return String.join("", FORCE_CREATE_NEW_KEYS_DEPLOYMENT_OPTION, NODE_WEBSERVER_DEPLOYMENT, " ", getGroupOption(groupName));
    }

    String getNodeHelloWorldDeploymentCommand(Optional<String> groupName) {
        return String.join("", FORCE_CREATE_NEW_KEYS_DEPLOYMENT_OPTION, NODE_HELLO_WORLD_DEPLOYMENT, " ", getGroupOption(groupName));
    }

    String getX86_64SampleCDeploymentCommand(Optional<String> groupName) {
        return String.join("", FORCE_CREATE_NEW_KEYS_DEPLOYMENT_OPTION, X86_64_SAMPLE_C_DEPLOYMENT, " ", getGroupOption(groupName));
    }

    String getAllHelloWorldDeploymentCommand(Optional<String> groupName) {
        return String.join("", FORCE_CREATE_NEW_KEYS_DEPLOYMENT_OPTION, ALL_HELLO_WORLD_DEPLOYMENT, " ", getGroupOption(groupName));
    }

    String getEc2Arm32NodeHelloWorldDeploymentCommand(Optional<String> groupName) {
        return String.join("", FORCE_CREATE_NEW_KEYS_DEPLOYMENT_OPTION, NODE_HELLO_WORLD_DEPLOYMENT, " ", getGroupOption(groupName), " ", ARM32_OPTION, " ", EC2_LAUNCH_OPTION);
    }

    String[] split(String input) {
        return input.split(" ");
    }

    @NotNull
    private String getReusedFunctionDeploymentConfContents(String groupName) {
        String functionToReuse = String.join("-", groupName, "HelloWorldPython3:PROD");
        return "conf { functions = [\"~" + functionToReuse + "\"] }";
    }

    @NotNull
    File setupReusedFunctionDeploymentConf(Optional<String> optionalDirectoryPrefix, String groupName) throws IOException {
        String tempDeploymentConfContents = getReusedFunctionDeploymentConfContents(groupName);
        return writeTempDeploymentConfFile(optionalDirectoryPrefix, tempDeploymentConfContents);
    }

    @NotNull
    private File writeTempDeploymentConfFile(Optional<String> optionalDirectoryPrefix, String tempDeploymentConfContents) throws IOException {
        String directory = "deployments";

        if (optionalDirectoryPrefix.isPresent()) {
            directory = optionalDirectoryPrefix.get() + "/" + directory;
        }

        File tempDeploymentConfFile = File.createTempFile("temp-deployment-", ".conf", new File(directory));
        tempDeploymentConfFile.deleteOnExit();
        ioHelper.writeFile(tempDeploymentConfFile, tempDeploymentConfContents.getBytes());
        return tempDeploymentConfFile;
    }

    @NotNull
    String getReusedFunctionDeploymentCommand(File tempDeploymentConfFile) {
        return String.join("", DeploymentArguments.LONG_FORCE_CREATE_NEW_KEYS_OPTION, " ", "--oem", " ", "-d", " ", "deployments/", tempDeploymentConfFile.getName(), " ", "-g", " ", ioHelper.getUuid());
    }
}
