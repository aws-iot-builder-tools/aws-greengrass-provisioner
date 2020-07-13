import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GreengrassHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.iot.data.GreengrassGroupName;
import com.awslabs.iot.data.ImmutableGreengrassGroupName;
import com.awslabs.iot.helpers.interfaces.V2GreengrassHelper;
import com.awslabs.lambda.data.FunctionAlias;
import com.awslabs.lambda.data.FunctionName;
import com.awslabs.lambda.data.ImmutableFunctionAlias;
import com.awslabs.lambda.data.ImmutableFunctionName;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.GroupInformation;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class GreengrassUpdateWithoutDockerIT {
    private static final String HELLO_WORLD_NODE = "HelloWorldNode";
    private static final String HELLO_WORLD_PYTHON3 = "HelloWorldPython3";
    private static Logger log = LoggerFactory.getLogger(GreengrassUpdateWithoutDockerIT.class);
    @Rule
    public ExpectedSystemExit expectedSystemExit = ExpectedSystemExit.none();
    GreengrassITShared greengrassITShared;
    GreengrassHelper greengrassHelper;
    V2GreengrassHelper v2GreengrassHelper;
    IoHelper ioHelper;

    @Before
    public void beforeTestSetup() throws IOException {
        greengrassITShared = new GreengrassITShared();
        GreengrassITShared.beforeTestSetup();

        greengrassHelper = AwsGreengrassProvisioner.getInjector().greengrassHelper();
        v2GreengrassHelper = AwsGreengrassProvisioner.getInjector().v2GreengrassHelper();
        ioHelper = AwsGreengrassProvisioner.getInjector().ioHelper();
    }

    @After
    public void afterTestTeardown() throws IOException {
        GreengrassITShared.cleanDirectories();
    }

    // Test set 1: Build a group with Python 2 Hello World, build another group with Node Hello World, add Node Hello World to first group
    @Test
    public void shouldAddNodeFunctionToGroup() {
        // Create two groups with different names so we can do the update later
        Optional<GreengrassGroupName> optionalGroup1Name = Optional.of(ImmutableGreengrassGroupName.builder().groupName(ioHelper.getUuid()).build());
        Optional<GreengrassGroupName> optionalGroup2Name = Optional.of(ImmutableGreengrassGroupName.builder().groupName(ioHelper.getUuid()).build());

        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getPython3HelloWorldDeploymentCommand(optionalGroup1Name)));
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getNodeHelloWorldDeploymentCommand(optionalGroup2Name)));

        GreengrassGroupName group1Name = optionalGroup1Name.get();
        GreengrassGroupName group2Name = optionalGroup2Name.get();

        // Get the information for group 2 so we can pull the Hello World function out of it
        GroupInformation group2Information = v2GreengrassHelper.getGroupInformation(group2Name).findFirst()
                .orElseThrow(() -> new RuntimeException("Group 2 information not present"));

        // Pull the Hello World function out of the group
        Optional<Function> optionalGroup2HelloWorldNodeFunction = getFunctionFromGroupInformation(group2Information, HELLO_WORLD_NODE);

        // Make sure the function is present
        Assert.assertTrue(optionalGroup2HelloWorldNodeFunction.isPresent());

        // Get the function information, extract the short name, and create a new alias
        Function helloWorldNodeFunction = optionalGroup2HelloWorldNodeFunction.get();
        FunctionName helloWorldNodeFunctionName = extractFunctionName(helloWorldNodeFunction);
        FunctionAlias functionAlias = ImmutableFunctionAlias.builder().alias(ioHelper.getUuid()).build();

        // Make sure the function isn't present in group 1 before the update
        GroupInformation group1InformationBeforeUpdate = v2GreengrassHelper.getGroupInformation(group1Name).findFirst()
                .orElseThrow(() -> new RuntimeException("Group 1 information not present before update"));

        Optional<Function> optionalGroup1HelloWorldNodeFunctionBeforeUpdate = getFunctionFromGroupInformation(group1InformationBeforeUpdate, HELLO_WORLD_NODE);

        Assert.assertFalse(optionalGroup1HelloWorldNodeFunctionBeforeUpdate.isPresent());

        // Update group 1 with the function
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getAddFunctionCommand(group1Name, helloWorldNodeFunctionName, functionAlias)));

        // Make sure the function is present in group 1 after the update
        GroupInformation group1InformationAfterUpdate = v2GreengrassHelper.getGroupInformation(group1Name).findFirst()
                .orElseThrow(() -> new RuntimeException("Group 1 information not present after update"));

        Optional<Function> optionalGroup1HelloWorldNodeFunctionAfterUpdate = getFunctionFromGroupInformation(group1InformationAfterUpdate, HELLO_WORLD_NODE);

        Assert.assertTrue(optionalGroup1HelloWorldNodeFunctionAfterUpdate.isPresent());
    }

    // Test set 2: Build a group with Python 2 Hello World, remove the Python 2 Hello World function
    @Test
    public void shouldRemovePythonFunctionFromGroup() {
        // Create one group with a known name
        Optional<GreengrassGroupName> optionalGroupName = Optional.of(ImmutableGreengrassGroupName.builder().groupName(ioHelper.getUuid()).build());

        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getPython3HelloWorldDeploymentCommand(optionalGroupName)));

        GreengrassGroupName groupName = optionalGroupName.get();

        // Get the information for the group so we can pull the Hello World function out of it
        GroupInformation groupInformation = v2GreengrassHelper.getGroupInformation(groupName).findFirst()
                .orElseThrow(() -> new RuntimeException("Group information not present"));

        // Pull the Hello World function out of the group
        Optional<Function> optionalGroupHelloWorldPythonFunction = getFunctionFromGroupInformation(groupInformation, HELLO_WORLD_PYTHON3);

        // Make sure the function is present
        Assert.assertTrue(optionalGroupHelloWorldPythonFunction.isPresent());

        // Get the function information, extract the short name, and create a new alias
        Function helloWorldPythonFunction = optionalGroupHelloWorldPythonFunction.get();
        FunctionName helloWorldPythonFunctionName = extractFunctionName(helloWorldPythonFunction);
        FunctionAlias helloWorldPythonFunctionAlias = extractFunctionAlias(helloWorldPythonFunction);

        // Remove the function from the group
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getRemoveFunctionCommand(groupName, helloWorldPythonFunctionName, helloWorldPythonFunctionAlias)));

        // Make sure the function is not present in the group after the update
        GroupInformation groupInformationAfterUpdate = v2GreengrassHelper.getGroupInformation(groupName).findFirst()
                .orElseThrow(() -> new RuntimeException("Group information not present after update"));

        Optional<Function> optionalGroupHelloWorldPythonFunctionAfterUpdate = getFunctionFromGroupInformation(groupInformationAfterUpdate, HELLO_WORLD_PYTHON3);

        Assert.assertFalse(optionalGroupHelloWorldPythonFunctionAfterUpdate.isPresent());
    }

    @NotNull
    private Optional<Function> getFunctionFromGroupInformation(GroupInformation groupInformation, String functionName) {
        List<Function> group2Functions = v2GreengrassHelper.getFunctions(groupInformation)
                .orElseThrow(() -> new RuntimeException("Group not found, can not continue"));

        return group2Functions.stream()
                .filter(function -> function.functionArn().contains(functionName))
                .findFirst();
    }

    private FunctionName extractFunctionName(Function function) {
        String returnValue = function.functionArn().replaceAll("^.*:function:", "");
        returnValue = returnValue.replaceAll(":.*$", "");

        return ImmutableFunctionName.builder().name(returnValue).build();
    }

    private FunctionAlias extractFunctionAlias(Function function) {
        String returnValue = function.functionArn().replaceAll("^.*:function:", "");
        returnValue = returnValue.replaceAll("^.*:", "");

        return ImmutableFunctionAlias.builder().alias(returnValue).build();
    }
}
