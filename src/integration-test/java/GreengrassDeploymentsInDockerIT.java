import com.awslabs.aws.greengrass.provisioner.implementations.clientproviders.AwsCredentialsProvider;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class GreengrassDeploymentsInDockerIT {
    public static final String TIMMATTISON_AWS_GREENGRASS_PROVISIONER = "timmattison/aws-greengrass-provisioner";
    private Logger log = LoggerFactory.getLogger(GreengrassDeploymentsInDockerIT.class);
    private BasicThreadHelper basicThreadHelper;
    private String groupOption = "";

    @Before
    public void setup() {
        BasicIoHelper basicIoHelper = new BasicIoHelper();
        groupOption = groupOption.join(" ", "-g", basicIoHelper.getUuid());

        SingleThreadedExecutorHelper singleThreadedExecutorHelper = new SingleThreadedExecutorHelper();
        basicThreadHelper = new BasicThreadHelper();
        basicThreadHelper.executorHelper = singleThreadedExecutorHelper;
    }

    private String getHome() {
        BasicGlobalDefaultHelper basicGlobalDefaultHelper = new BasicGlobalDefaultHelper();
        return basicGlobalDefaultHelper.getHomeDirectory().get();
    }

    private String getBranch() {
        BasicProcessHelper basicProcessHelper = new BasicProcessHelper();
        basicProcessHelper.awsCredentialsProvider = mock(AwsCredentialsProvider.class);
        doReturn(DefaultCredentialsProvider.create().resolveCredentials()).when(basicProcessHelper.awsCredentialsProvider).get();

        List<String> programAndArguments = new ArrayList<>();
        programAndArguments.add("git");
        programAndArguments.add("symbolic-ref");
        programAndArguments.add("--short");
        programAndArguments.add("HEAD");

        ProcessBuilder processBuilder = basicProcessHelper.getProcessBuilder(programAndArguments);

        StringBuilder stringBuilder = new StringBuilder();

        basicProcessHelper.getOutputFromProcess(log, processBuilder, true, Optional.of(stringBuilder::append), Optional.empty());

        return stringBuilder.toString();
    }

    private String getContainerName() {
        return String.join(":", TIMMATTISON_AWS_GREENGRASS_PROVISIONER, getBranch());
    }

    private void asdf() {
        /*
        -v $PWD/functions:/functions \
        -v $PWD/credentials:/credentials \
        -v $PWD/ggds:/ggds \
        -v $PWD/build:/build \
        -v $HOME/.ssh:/root/.ssh \
        -v $PWD/dtoutput:/dtoutput \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -it --rm \
        -e AWS_REGION=$REGION \
        -e AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID \
        -e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY \
        $DOCKER_SESSION_TOKEN \
        timmattison/aws-greengrass-provisioner:$BRANCH $@
         */
    }

    private GenericContainer startAndGetContainer(String arguments) {
        String hostAwsCredentialsPath = String.join("/", getHome(), ".aws");
        String containerAwsCredentialsPath = "/root/.aws";

        String hostFoundationPath = String.join("/", "..", "aws-greengrass-lambda-functions", "foundation");
        String containerFoundationPath = "/foundation";

        String hostDeploymentsPath = String.join("/", "..", "aws-greengrass-lambda-functions", "deployments");
        String containerDeploymentsPath = "/deployments";

        String hostFunctionsPath = String.join("/", "..", "aws-greengrass-lambda-functions", "functions");
        String containerFunctionsPath = "/functions";

        String baseCommand = "java -jar AwsGreengrassProvisioner.jar";
        String command = baseCommand.join(" ", arguments);

        GenericContainer genericContainer = new GenericContainer<>(getContainerName())
                .withCopyFileToContainer(MountableFile.forHostPath(new File(hostAwsCredentialsPath).toPath()), containerAwsCredentialsPath)
                .withCopyFileToContainer(MountableFile.forHostPath(new File(hostFoundationPath).toPath()), containerFoundationPath)
                .withCopyFileToContainer(MountableFile.forHostPath(new File(hostDeploymentsPath).toPath()), containerDeploymentsPath)
                .withCopyFileToContainer(MountableFile.forHostPath(new File(hostFunctionsPath).toPath()), containerFunctionsPath)
                .withCommand(command);

        genericContainer.start();

        return genericContainer;
    }

    private void waitForContainerToFinish(GenericContainer genericContainer) {
//        Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);
//        genericContainer.followOutput(logConsumer);

        basicThreadHelper.timeLimitTask(() -> {
                    while (genericContainer.isRunning()) {
                        Thread.sleep(1000);
//                        log.info("Waiting for container to finish...");
                    }
                    return Optional.empty();
                },
                2, TimeUnit.MINUTES);
    }

    @Test
    public void shouldFail() {
        GenericContainer genericContainer = startAndGetContainer(String.join(" ", "-d FAKE", groupOption));
        waitForContainerToFinish(genericContainer);

        log.info(genericContainer.getLogs());
        Assert.assertThat(genericContainer.getCurrentContainerInfo().getState().getExitCode(), is(not(equalTo(0))));
    }

    @Test
    public void checkJavaBuild() {
        GenericContainer genericContainer = startAndGetContainer(String.join(" ", "-d deployments/cdd-skeleton.conf", groupOption));
        waitForContainerToFinish(genericContainer);

        log.info(genericContainer.getLogs());
        Assert.assertThat(genericContainer.getCurrentContainerInfo().getState().getExitCode(), is(equalTo(0)));
    }

    @Test
    public void checkPythonBuild() {
        GenericContainer genericContainer = startAndGetContainer(String.join(" ", "-d deployments/python-hello-world.conf", groupOption));
        waitForContainerToFinish(genericContainer);

        log.info(genericContainer.getLogs());
        Assert.assertThat(genericContainer.getCurrentContainerInfo().getState().getExitCode(), is(equalTo(0)));
    }

    @Test
    public void checkPythonBuildWithDependencies() {
        GenericContainer genericContainer = startAndGetContainer(String.join(" ", "-d deployments/lifx.conf", groupOption));
        waitForContainerToFinish(genericContainer);

        log.info(genericContainer.getLogs());
        Assert.assertThat(genericContainer.getCurrentContainerInfo().getState().getExitCode(), is(equalTo(0)));
    }

    @Test
    public void checkNodeBuild() {
        GenericContainer genericContainer = startAndGetContainer(String.join(" ", "-d deployments/node-hello-world.conf", groupOption));
        waitForContainerToFinish(genericContainer);

        log.info(genericContainer.getLogs());
        Assert.assertThat(genericContainer.getCurrentContainerInfo().getState().getExitCode(), is(equalTo(0)));
    }

    @Test
    public void checkCombinedBuild() {
        GenericContainer genericContainer = startAndGetContainer(String.join(" ", "-d deployments/all-hello-world.conf", groupOption));
        waitForContainerToFinish(genericContainer);

        log.info(genericContainer.getLogs());
        Assert.assertThat(genericContainer.getCurrentContainerInfo().getState().getExitCode(), is(equalTo(0)));
    }
}
