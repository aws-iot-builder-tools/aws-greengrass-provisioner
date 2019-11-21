package com.awslabs.aws.greengrass.provisioner.data.conf;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigRenderOptions;
import io.vavr.control.Try;
import org.immutables.value.Value;

import java.util.List;
import java.util.Optional;

@Value.Immutable
public abstract class RoleConf {
    public abstract String getName();

    public abstract Optional<String> getAlias();

    public abstract Optional<String> getAssumeRolePolicy();

    public abstract Optional<List<String>> getIamManagedPolicies();

    public abstract Optional<String> getIamPolicy();

    public abstract Optional<String> getIotPolicy();

    public static ImmutableRoleConf fromConfigAndPrefix(Config config, String prefix) {
        String rolePrefix = String.join(".", prefix, "role");

        ImmutableRoleConf.Builder roleConfBuilder = ImmutableRoleConf.builder();

        // Required string values
        roleConfBuilder.name(config.getString(String.join(".", rolePrefix, "name")));

        // Optional string values
        roleConfBuilder.alias(getStringOrReturnEmpty(config, String.join(".", rolePrefix, "alias")));

        // Optional string lists
        roleConfBuilder.iamManagedPolicies(getStringListOrReturnEmpty(config, String.join(".",rolePrefix, "iamManagedPolicies")));

        // Optional JSON strings
        roleConfBuilder.assumeRolePolicy(getObjectAndRenderOrReturnEmpty(config, String.join(".", rolePrefix, "assumeRolePolicy")));
        roleConfBuilder.iotPolicy(getObjectAndRenderOrReturnEmpty(config, String.join(".", rolePrefix, "iotPolicy")));
        roleConfBuilder.iamPolicy(getObjectAndRenderOrReturnEmpty(config, String.join(".", rolePrefix, "iamPolicy")));

        return roleConfBuilder.build();
    }

    private static Optional<String> getStringOrReturnEmpty(Config config, String path) {
        return Optional.ofNullable(Try.of(() -> config.getString(path))
                .recover(ConfigException.Missing.class, throwable -> null)
                .get());
    }

    private static Optional<List<String>> getStringListOrReturnEmpty(Config config, String path) {
        return Optional.ofNullable(Try.of(() -> config.getStringList(path))
                .recover(ConfigException.Missing.class, throwable -> null)
                .get());
    }

    private static Optional<String> getObjectAndRenderOrReturnEmpty(Config config, String path) {
        return Optional.ofNullable(Try.of(() -> config.getObject(path))
                .recover(ConfigException.Missing.class, throwable -> null)
                .get())
                .map(object -> object.render(ConfigRenderOptions.concise()));
    }
}
