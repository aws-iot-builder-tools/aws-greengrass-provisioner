package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.*;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.CloudFormationHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class BasicCloudFormationHelper implements CloudFormationHelper {
    public static final String CREATE_IN_PROGRESS = "CREATE_IN_PROGRESS";
    public static final String UPDATE_IN_PROGRESS = "UPDATE_IN_PROGRESS";
    public static final String ROLLBACK = "ROLLBACK";
    @Inject
    AmazonCloudFormationClient amazonCloudFormationClient;
    @Inject
    IoHelper ioHelper;

    @Inject
    public BasicCloudFormationHelper() {
    }

    private boolean hasCloudFormationTemplate(FunctionConf functionConf) {
        return (functionConf.getCfTemplate() != null);
    }

    @Override
    public Optional<String> deployCloudFormationTemplate(Map<String, String> environment, String groupName, FunctionConf functionConf) {
        if (!hasCloudFormationTemplate(functionConf)) {
            return Optional.empty();
        }

        // Determine a unique stack name for this Greengrass group to avoid conflicts
        String stackName = String.join("-", groupName, functionConf.getFunctionName());
        stackName = stackName.replaceAll("[^-a-zA-Z0-9]", "-");

        // NOTE: CloudFormation parameters cannot have underscores in them so we strip them below
        // Create real CloudFormation parameters for the parameters in the function's environment
        List<Parameter> parameters = environment.entrySet().stream()
                .map(e -> new Parameter()
                        .withParameterKey(e.getKey().replaceAll("_", ""))
                        .withParameterValue(e.getValue()))
                .collect(Collectors.toList());

        log.info("Launching CloudFormation template for [" + functionConf.getFunctionName() + "], stack name [" + stackName + "]");
        CreateStackRequest createStackRequest = new CreateStackRequest()
                .withStackName(stackName)
                .withCapabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM)
                .withParameters(parameters)
                .withTemplateBody(ioHelper.readFileAsString(functionConf.getCfTemplate()));

        try {
            CreateStackResult createStackResult = amazonCloudFormationClient.createStack(createStackRequest);

            log.info("CloudFormation stack launched [" + stackName + ", " + createStackResult.getStackId() + "]");

            return Optional.of(stackName);
        } catch (AlreadyExistsException e) {
            log.warn("CloudFormation stack [" + stackName + "] already exists, attempting update");
        }

        try {
            UpdateStackRequest updateStackRequest = new UpdateStackRequest()
                    .withStackName(stackName)
                    .withCapabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM)
                    .withParameters(parameters)
                    .withTemplateBody(ioHelper.readFileAsString(functionConf.getCfTemplate()));

            amazonCloudFormationClient.updateStack(updateStackRequest);
        } catch (AmazonCloudFormationException e) {
            if (e.getMessage().contains("No updates are to be performed")) {
                log.info("No updates necessary for CloudFormation stack [" + stackName + "]");
                return Optional.empty();
            }
        }

        return Optional.of(stackName);
    }

    @Override
    public void waitForStackToLaunch(String stackName) {
        DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest()
                .withStackName(stackName);
        DescribeStacksResult describeStacksResult = amazonCloudFormationClient.describeStacks(describeStacksRequest);

        String stackStatus = describeStacksResult.getStacks().get(0).getStackStatus();

        while (stackStatus.equals(CREATE_IN_PROGRESS) ||
                stackStatus.equals(UPDATE_IN_PROGRESS)) {
            if (stackStatus.contains(ROLLBACK)) {
                throw new UnsupportedOperationException("CloudFormation stack [" + stackName + "] failed to launch");
            }

            String action = "creating";

            if (stackStatus.equals(UPDATE_IN_PROGRESS)) {
                action = "updating";
            }

            log.info("Waiting for stack to finish " + action + " [" + stackName + ", " + stackStatus + "]...");

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            describeStacksResult = amazonCloudFormationClient.describeStacks(describeStacksRequest);
            stackStatus = describeStacksResult.getStacks().get(0).getStackStatus();
        }

        log.info("CloudFormation stack ready [" + stackName + "]");
    }
}
