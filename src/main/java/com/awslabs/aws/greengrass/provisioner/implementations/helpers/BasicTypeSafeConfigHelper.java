package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.TypeSafeConfigHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.*;
import io.vavr.control.Try;
import software.amazon.awssdk.utils.builder.SdkBuilder;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BasicTypeSafeConfigHelper implements TypeSafeConfigHelper {
    @Inject
    ObjectMapper objectMapper;

    @Inject
    public BasicTypeSafeConfigHelper() {
    }

    @Override
    public Optional<String> getStringOrReturnEmpty(Config config, String path) {
        return Optional.ofNullable(Try.of(() -> config.getString(path))
                .recover(ConfigException.Missing.class, throwable -> null)
                .get());
    }

    @Override
    public Optional<List<String>> getStringListOrReturnEmpty(Config config, String path) {
        return Optional.ofNullable(Try.of(() -> config.getStringList(path))
                .recover(ConfigException.Missing.class, throwable -> null)
                .get());
    }

    @Override
    public Optional<String> getObjectAndRenderOrReturnEmpty(Config config, String path) {
        return Optional.ofNullable(Try.of(() -> config.getObject(path))
                .recover(ConfigException.Missing.class, throwable -> null)
                .get())
                .map(this::renderToJson);
    }

    @Override
    public <T, S extends SdkBuilder<?, T>> T convertToSdkObject(ConfigObject configObject, Class<S> builderClass) {
        String json = renderToJson(configObject);

        S builder = Try.of(() -> objectMapper.readValue(json, builderClass)).get();
        T returnValue = builder.build();

        return returnValue;
    }

    private String renderToJson(ConfigObject configObject) {
        return configObject.render(ConfigRenderOptions.concise());
    }

    @Override
    public Config addDefaultValues(Map<String, String> defaults, Optional<Config> optionalConfig) {
        Config config = optionalConfig.orElse(ConfigFactory.empty());

        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            config = config.withValue(entry.getKey(), ConfigValueFactory.fromAnyRef(entry.getValue()));
        }

        return config;
    }
}
