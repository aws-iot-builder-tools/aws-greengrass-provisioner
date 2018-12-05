package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.typesafe.config.Config;

import java.util.Optional;

public interface GlobalDefaultHelper {
    Optional<Config> getGlobalDefaults(String path);

    Optional<String> getHomeDirectory();
}
