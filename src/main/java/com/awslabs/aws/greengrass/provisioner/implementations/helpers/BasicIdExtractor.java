package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IdExtractor;

import javax.inject.Inject;

public class BasicIdExtractor implements IdExtractor {
    @Inject
    public BasicIdExtractor() {
    }

    @Override
    public String extractId(String arn) {
        return arn.replaceFirst("/versions.*$", "").replaceFirst("^.*/", "");
    }

    @Override
    public String extractVersionId(String arn) {
        return arn.substring(arn.lastIndexOf('/') + 1);
    }
}
