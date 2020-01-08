package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import software.amazon.awssdk.utils.builder.SdkBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TypeSafeConfigHelper {
    /**
     * Get a configuration object at the specified path, convert it to a string, and return it
     *
     * @param config
     * @param path
     * @return a string or empty if the specified path is not found
     */
    Optional<String> getStringOrReturnEmpty(Config config, String path);

    /**
     * Get a configuration object at the specified path, convert it to a string list, and return it
     *
     * @param config
     * @param path
     * @return a string list or empty if the specified path is not found
     */
    Optional<List<String>> getStringListOrReturnEmpty(Config config, String path);

    /**
     * Get a configuration object at the specified path, convert it to JSON, and return it
     *
     * @param config
     * @param path
     * @return JSON or empty if the specified path is not found
     */
    Optional<String> getObjectAndRenderOrReturnEmpty(Config config, String path);

    <T, S extends SdkBuilder<?, T>> T convertToSdkObject(ConfigObject configObject, Class<S> builderClass);

    Config addDefaultValues(Map<String, String> defaults, Optional<Config> optionalConfig);
}
