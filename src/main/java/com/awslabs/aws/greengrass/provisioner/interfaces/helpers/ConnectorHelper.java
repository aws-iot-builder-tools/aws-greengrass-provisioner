package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.ConnectorConf;
import com.typesafe.config.Config;
import software.amazon.awssdk.services.greengrass.model.ConnectorDefinitionVersion;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;

import java.util.List;

public interface ConnectorHelper {
    List<ConnectorConf> getConnectorConfObjects(DeploymentArguments deploymentArguments, Config defaultConfig, List<String> connectorConfFileNames);

    void validateConnectorDefinitionVersion(ConnectorDefinitionVersion connectorDefinitionVersion);
}
