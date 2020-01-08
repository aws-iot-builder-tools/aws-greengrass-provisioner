package com.awslabs.aws.greengrass.provisioner.data.conf;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Value.Immutable
public abstract class Connectors {
    private static final Logger log = LoggerFactory.getLogger(Connectors.class);

    public abstract List<ConnectorConf> getConnectorConfs();
}
