package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;

import java.util.Map;
import java.util.Optional;

public interface CloudFormationHelper {
    Optional<String> deployCloudFormationTemplate(Map<String, String> environment, String awsIotThingName, FunctionConf functionConf);

    void waitForStackToLaunch(String stackName);
}
