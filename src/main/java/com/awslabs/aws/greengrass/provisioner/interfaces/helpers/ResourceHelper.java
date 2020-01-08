package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import software.amazon.awssdk.services.greengrass.model.ResourceDefinitionVersion;

import java.io.IOException;
import java.io.InputStream;

public interface ResourceHelper {
    InputStream getResourceAsStream(String resourcePath);

    InputStream getFileOrResourceAsStream(String sourcePath);

    String resourceToString(String filename);

    String resourceToTempFile(String filename) throws IOException;

    void validateResourceDefinitionVersion(ResourceDefinitionVersion resourceDefinitionVersion);
}
