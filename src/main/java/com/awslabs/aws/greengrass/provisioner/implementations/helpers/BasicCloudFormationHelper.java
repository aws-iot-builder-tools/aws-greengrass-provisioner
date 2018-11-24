package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.*;
import com.awslabs.aws.greengrass.provisioner.data.FunctionConf;
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
    public static final String ARN_AWS_CLOUDFORMATION = "arn:aws:cloudformation:";
    public static final String STACKS = "stacks/";
    public static final String CREATE_COMPLETE = "CREATE_COMPLETE";
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
    public Optional<String> deployCloudFormationTemplate(Map<String, String> environment, String awsIotThingName, FunctionConf functionConf) {
        if (!hasCloudFormationTemplate(functionConf)) {
            return Optional.empty();
        }

        String stackName = String.join("-", awsIotThingName, functionConf.getFunctionName());
        stackName = stackName.replaceAll("[^-a-zA-Z0-9]", "-");

        // NOTE: CloudFormation parameters cannot have underscores in them so we strip them below
        List<Parameter> parameters = environment.entrySet().stream()
                // NOTE: This key is no longer needed
                .filter(e -> !e.getKey().equals("AWS_GREENGRASS_GROUP_NAME"))
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
            log.warn("CloudFormation stack [" + stackName + "] already exists, skipping");
            return Optional.empty();
        }
    }

    @Override
    public void waitForStackToLaunch(String stackName) {
        DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest()
                .withStackName(stackName);
        DescribeStacksResult describeStacksResult = amazonCloudFormationClient.describeStacks(describeStacksRequest);

        String stackStatus = describeStacksResult.getStacks().get(0).getStackStatus();

        while (!stackStatus.equals(CREATE_COMPLETE)) {
            if (stackStatus.contains(ROLLBACK)) {
                throw new UnsupportedOperationException("CloudFormation stack [" + stackName + "] failed to launch");
            }

            log.info("Waiting for stack to finish launching [" + stackName + ", " + stackStatus + "]...");

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            describeStacksResult = amazonCloudFormationClient.describeStacks(describeStacksRequest);
            stackStatus = describeStacksResult.getStacks().get(0).getStackStatus();
        }

        log.info("CloudFormation stack launched [" + stackName + "]");
    }
}
