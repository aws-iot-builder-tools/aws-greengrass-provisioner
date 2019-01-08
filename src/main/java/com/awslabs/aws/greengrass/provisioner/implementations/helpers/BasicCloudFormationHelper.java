package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.CloudFormationHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class BasicCloudFormationHelper implements CloudFormationHelper {
    @Inject
    CloudFormationClient cloudFormationClient;
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
                .map(e -> Parameter.builder()
                        .parameterKey(e.getKey().replaceAll("_", ""))
                        .parameterValue(e.getValue())
                        .build())
                .collect(Collectors.toList());

        log.info("Launching CloudFormation template for [" + functionConf.getFunctionName() + "], stack name [" + stackName + "]");
        CreateStackRequest createStackRequest = CreateStackRequest.builder()
                .stackName(stackName)
                .capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM)
                .parameters(parameters)
                .templateBody(ioHelper.readFileAsString(functionConf.getCfTemplate()))
                .build();

        String finalStackName = stackName;

        boolean stackCreated = Try.of(() -> {
            CreateStackResponse createStackResponse = cloudFormationClient.createStack(createStackRequest);

            log.info("CloudFormation stack launched [" + finalStackName + ", " + createStackResponse.stackId() + "]");

            return true;
        })
                .recover(AlreadyExistsException.class, throwable -> {
                    log.warn("CloudFormation stack [" + finalStackName + "] already exists, attempting update");
                    return false;
                })
                .get();

        if (stackCreated) {
            return Optional.of(stackName);
        }

        return Try.of(() -> {
            UpdateStackRequest updateStackRequest = UpdateStackRequest.builder()
                    .stackName(finalStackName)
                    .capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM)
                    .parameters(parameters)
                    .templateBody(ioHelper.readFileAsString(functionConf.getCfTemplate()))
                    .build();

            cloudFormationClient.updateStack(updateStackRequest);

            return Optional.of(finalStackName);
        })
                .recover(CloudFormationException.class, throwable -> {
                    if (throwable.getMessage().contains("No updates are to be performed")) {
                        log.info("No updates necessary for CloudFormation stack [" + finalStackName + "]");
                        return Optional.empty();
                    }

                    throw new RuntimeException(throwable);
                })
                .get();
    }

    @Override
    public void waitForStackToLaunch(String stackName) {
        DescribeStacksRequest describeStacksRequest = DescribeStacksRequest.builder()
                .stackName(stackName)
                .build();
        DescribeStacksResponse describeStacksResponse = cloudFormationClient.describeStacks(describeStacksRequest);

        StackStatus stackStatus = describeStacksResponse.stacks().get(0).stackStatus();

        while (stackStatus.equals(StackStatus.CREATE_IN_PROGRESS) ||
                stackStatus.equals(StackStatus.UPDATE_IN_PROGRESS)) {
            if (stackStatus.equals(StackStatus.ROLLBACK_IN_PROGRESS) ||
                    (stackStatus.equals(StackStatus.ROLLBACK_COMPLETE)) ||
                    (stackStatus.equals(StackStatus.ROLLBACK_FAILED))) {
                throw new RuntimeException("CloudFormation stack [" + stackName + "] failed to launch");
            }

            String action = "creating";

            if (stackStatus.equals(StackStatus.UPDATE_IN_PROGRESS)) {
                action = "updating";
            }

            log.info("Waiting for stack to finish " + action + " [" + stackName + ", " + stackStatus + "]...");

            ioHelper.sleep(10000);

            describeStacksResponse = cloudFormationClient.describeStacks(describeStacksRequest);
            stackStatus = describeStacksResponse.stacks().get(0).stackStatus();
        }

        log.info("CloudFormation stack ready [" + stackName + "]");
    }
}
