package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;

import java.util.Map;
import java.util.Optional;

public interface CloudFormationHelper {
    /**
     * Creates or updates a CloudFormation based stack
     *
     * @param environment
     * @param awsIotThingName
     * @param functionConf
     * @return empty if no stack was launched or updated, an optional of the stack name if a stack was launched or updated
     */
    Optional<String> deployCloudFormationTemplate(Map<String, String> environment, String awsIotThingName, FunctionConf functionConf);

    void waitForStackToLaunch(String stackName);
}
