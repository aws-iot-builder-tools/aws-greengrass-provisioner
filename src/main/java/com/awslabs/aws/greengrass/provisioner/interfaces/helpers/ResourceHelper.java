package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

public interface ResourceHelper {
    String resourceToString(String filename);

    String resourceToTempFile(String filename);
}
