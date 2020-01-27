package com.awslabs.aws.greengrass.provisioner.data.conf;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.TypeSafeConfigHelper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigObject;
import io.vavr.control.Try;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.model.Connector;

import java.util.List;
import java.util.Optional;

@Value.Immutable
public abstract class ConnectorConf {
    private static final Logger log = LoggerFactory.getLogger(ConnectorConf.class);

    public abstract List<String> getFromCloudSubscriptions();

    public abstract List<String> getToCloudSubscriptions();

    public abstract List<String> getOutputTopics();

    public abstract List<String> getInputTopics();

    public abstract Connector getConnector();

    public abstract Optional<List<String>> getCoreRoleIamManagedPolicies();

    public abstract Optional<String> getCoreRoleIamPolicy();

    public abstract Optional<List<String>> getServiceRoleIamManagedPolicies();

    public abstract Optional<String> getServiceRoleIamPolicy();
}
