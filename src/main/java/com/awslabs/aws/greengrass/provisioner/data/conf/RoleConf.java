package com.awslabs.aws.greengrass.provisioner.data.conf;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.TypeSafeConfigHelper;
import com.typesafe.config.Config;
import org.immutables.value.Value;

import java.util.List;
import java.util.Optional;

@Value.Immutable
public abstract class RoleConf {
    public static ImmutableRoleConf fromConfigAndPrefix(TypeSafeConfigHelper typeSafeConfigHelper, Config config, String prefix) {
        String rolePrefix = String.join(".", prefix, "role");

        ImmutableRoleConf.Builder roleConfBuilder = ImmutableRoleConf.builder();

        // Required string values
        roleConfBuilder.name(config.getString(String.join(".", rolePrefix, "name")));

        // Optional string values
        roleConfBuilder.alias(typeSafeConfigHelper.getStringOrReturnEmpty(config, String.join(".", rolePrefix, "alias")));

        // Optional string lists
        roleConfBuilder.iamManagedPolicies(typeSafeConfigHelper.getStringListOrReturnEmpty(config, String.join(".", rolePrefix, "iamManagedPolicies")));

        // Optional JSON strings
        roleConfBuilder.assumeRolePolicy(typeSafeConfigHelper.getObjectAndRenderOrReturnEmpty(config, String.join(".", rolePrefix, "assumeRolePolicy")));
        roleConfBuilder.iotPolicy(typeSafeConfigHelper.getObjectAndRenderOrReturnEmpty(config, String.join(".", rolePrefix, "iotPolicy")));
        roleConfBuilder.iamPolicy(typeSafeConfigHelper.getObjectAndRenderOrReturnEmpty(config, String.join(".", rolePrefix, "iamPolicy")));

        return roleConfBuilder.build();
    }

    public abstract String getName();

    public abstract Optional<String> getAlias();

    public abstract Optional<String> getAssumeRolePolicy();

    public abstract Optional<List<String>> getIamManagedPolicies();

    public abstract Optional<String> getIamPolicy();

    public abstract Optional<String> getIotPolicy();
}
