package com.awslabs.aws.greengrass.provisioner.data.resources;

import java.util.Optional;

public interface LocalResource {
    default String getName() {
        return getSafeName();
    }

    String getPath();

    default String getSafeName() {
        // Device names can't have special characters in them - https://docs.aws.amazon.com/greengrass/latest/apireference/createresourcedefinition-post.html
        return Optional.of(getPath()
                .replaceAll("[^a-zA-Z0-9:_-]", "-")
                .replaceFirst("^-", "")
                .replaceFirst("-$", "")
                .trim())
                .orElseThrow(() -> new RuntimeException("Path cannot be empty"));
    }
}
