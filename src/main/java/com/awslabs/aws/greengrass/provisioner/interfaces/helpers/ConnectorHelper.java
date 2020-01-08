package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.ConnectorConf;
import com.typesafe.config.Config;
import software.amazon.awssdk.services.greengrass.model.ConnectorDefinitionVersion;

import java.util.List;

public interface ConnectorHelper {
    List<ConnectorConf> getConnectorConfObjects(Config defaultConfig, List<String> connectorConfFileNames);

    void validateConnectorDefinitionVersion(ConnectorDefinitionVersion connectorDefinitionVersion);
}
