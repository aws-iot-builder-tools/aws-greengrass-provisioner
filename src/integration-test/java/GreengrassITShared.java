import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicIoHelper;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicThreadHelper;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.SingleThreadedExecutorHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ThreadHelper;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

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
    static final String OEM_OUTPUT_OPTION = "--oem";
    static final String DEPLOYMENT_OPTION = String.join(" ", OEM_OUTPUT_OPTION, "-d deployments/");
    static final String ARM32_OPTION = "-a ARM32";
    static final String EC2_LAUNCH_OPTION = "--ec2-launch";
    static final String CDD_SKELETON_DEPLOYMENT = "cdd-skeleton.conf";
    static final String PYTHON_HELLO_WORLD_DEPLOYMENT = "python-hello-world.conf";
    static final String LIFX_DEPLOYMENT = "lifx.conf";
    static final String NODE_WEBSERVER_DEPLOYMENT = "web-server-node.conf";
    static final String NODE_HELLO_WORLD_DEPLOYMENT = "node-hello-world.conf";
    static final String ALL_HELLO_WORLD_DEPLOYMENT = "all-hello-world.conf";
    static final String FAIL_DEPLOYMENT = "FAKE";
    final String GROUP_NAME = new BasicIoHelper().getUuid();
    final String GROUP_OPTION = String.join(" ", "-g", GROUP_NAME);
    final String CDD_SKELETON_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, CDD_SKELETON_DEPLOYMENT, " ", GROUP_OPTION);
    final String FAIL_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, FAIL_DEPLOYMENT, " ", GROUP_OPTION);
    final String PYTHON_HELLO_WORLD_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, PYTHON_HELLO_WORLD_DEPLOYMENT, " ", GROUP_OPTION);
    final String LIFX_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, LIFX_DEPLOYMENT, " ", GROUP_OPTION);
    final String NODE_WEBSERVER_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, NODE_WEBSERVER_DEPLOYMENT, " ", GROUP_OPTION);
    final String NODE_HELLO_WORLD_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, NODE_HELLO_WORLD_DEPLOYMENT, " ", GROUP_OPTION);
    final String ALL_HELLO_WORLD_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, ALL_HELLO_WORLD_DEPLOYMENT, " ", GROUP_OPTION);
    final String EC2_ARM32_NODE_HELLO_WORLD_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, NODE_HELLO_WORLD_DEPLOYMENT, " ", GROUP_OPTION, " ", ARM32_OPTION, " ", EC2_LAUNCH_OPTION);

    static void cleanDirectories() throws IOException {
        FileUtils.deleteDirectory(TEMP_DEPLOYMENTS);
        FileUtils.deleteDirectory(TEMP_FUNCTIONS);
        FileUtils.deleteDirectory(TEMP_FOUNDATION);
        FileUtils.deleteDirectory(TEMP_CREDENTIALS);
    }

    static ThreadHelper getThreadHelper() {
        SingleThreadedExecutorHelper singleThreadedExecutorHelper = new SingleThreadedExecutorHelper();

        BasicThreadHelper basicThreadHelper = new BasicThreadHelper();
        basicThreadHelper.executorHelper = singleThreadedExecutorHelper;

        return basicThreadHelper;
    }

    static void beforeTestSetup() throws IOException {
        cleanDirectories();

        FileUtils.copyDirectory(GreengrassITShared.MASTER_DEPLOYMENTS, GreengrassITShared.TEMP_DEPLOYMENTS);
        FileUtils.copyDirectory(GreengrassITShared.MASTER_FUNCTIONS, GreengrassITShared.TEMP_FUNCTIONS);
        FileUtils.copyDirectory(GreengrassITShared.MASTER_FOUNDATION, GreengrassITShared.TEMP_FOUNDATION);
        FileUtils.copyFile(GreengrassITShared.NODEJS_SDK_FROM_BUILD, GreengrassITShared.NODEJS_SDK_REQUIRED_FOR_TESTING);
    }
};
