package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

public interface IdExtractor {
    String extractId(String arn);

    String extractVersionId(String arn);
}
