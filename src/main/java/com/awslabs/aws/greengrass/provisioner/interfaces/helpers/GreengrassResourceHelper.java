package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import software.amazon.awssdk.services.greengrass.model.ResourceDefinitionVersion;

public interface GreengrassResourceHelper {
    void validateResourceDefinitionVersion(ResourceDefinitionVersion resourceDefinitionVersion);
}
