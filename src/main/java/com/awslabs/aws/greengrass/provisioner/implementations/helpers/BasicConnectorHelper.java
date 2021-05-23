package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.conf.ConnectorConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.ImmutableConnectorConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.awslabs.s3.helpers.interfaces.V2S3Helper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.model.Connector;
import software.amazon.awssdk.services.greengrass.model.ConnectorDefinitionVersion;
import software.amazon.awssdk.services.s3.model.Bucket;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BasicConnectorHelper implements ConnectorHelper {
    private static final String DOCKER_COMPOSE_FILE_S3_BUCKET = "DockerComposeFileS3Bucket";
    private static final String DOCKER_COMPOSE_FILE_S3_KEY = "DockerComposeFileS3Key";
    private static final String CONNECTORS = "connectors";
    private final Logger log = LoggerFactory.getLogger(BasicConnectorHelper.class);
    @Inject
    GGVariables ggVariables;
    @Inject
    TypeSafeConfigHelper typeSafeConfigHelper;
    @Inject
    IoHelper ioHelper;
    @Inject
    V2S3Helper s3Helper;

    @Inject
    public BasicConnectorHelper() {
    }

    @Override
    public List<ConnectorConf> getConnectorConfObjects(DeploymentArguments deploymentArguments, Config defaultConfig, List<String> connectorConfFileNames) {
        List<ConnectorConf> connectorConfList = new ArrayList<>();
        
        List<File> connectorConfFiles = connectorConfFileNames.stream()
                .map(filename -> String.join(".", filename, "conf"))
                .map(filename -> String.join("/", deploymentArguments.connectorConfigPath, filename))
                .map(File::new)
                .collect(Collectors.toList());

        ioHelper.detectMissingConfigFiles(log, "connector", connectorConfFiles);

        for (File connectorConfFile : connectorConfFiles) {
            ConnectorConf connectorConf = Try.of(() -> getConnectorConf(deploymentArguments, defaultConfig, connectorConfFile)).get();
            connectorConfList.add(connectorConf);
        }

        if (connectorConfList.size() > 0) {
            log.info("Enabled connector configurations: ");
            connectorConfList
                    .forEach(connectorConf -> log.info(String.join("", "  ", connectorConf.getConnector().connectorArn())));
        } else {
            log.warn("No connectors enabled");
        }

        return connectorConfList;
    }

    private ConnectorConf getConnectorConf(DeploymentArguments deploymentArguments, Config defaultConfig, File connectorConfFile) {
        ImmutableConnectorConf.Builder connectorConfBuilder = ImmutableConnectorConf.builder();

        // Config connectorDefaults = ggVariables.getConnectorDefaults();
        Config connectorDefaults = ConfigFactory.parseFile(new File(String.join("/", deploymentArguments.deploymentConfigFolderPath, "connector.defaults.conf")));

        Config config = ConfigFactory.parseFile(connectorConfFile)
                // Use the connector.defaults.conf values
                .withFallback(connectorDefaults)
                // Use the default config (environment) values
                .withFallback(defaultConfig)
                // Resolve the entire fallback config
                .resolve();

        connectorConfBuilder.fromCloudSubscriptions(config.getStringList(GGConstants.CONF_FROM_CLOUD_SUBSCRIPTIONS));
        connectorConfBuilder.toCloudSubscriptions(config.getStringList(GGConstants.CONF_TO_CLOUD_SUBSCRIPTIONS));
        connectorConfBuilder.outputTopics(config.getStringList(GGConstants.CONF_OUTPUT_TOPICS));
        connectorConfBuilder.inputTopics(config.getStringList(GGConstants.CONF_INPUT_TOPICS));
        Connector connector = fromConfigAndPrefix(config, "conf.connector");

        if (connector.id() == null) {
            // Fill in the ID if it wasn't specified
            connector = connector.toBuilder().id(ioHelper.getUuid()).build();
        }

        connectorConfBuilder.connector(connector);

        // Optional string lists for core role
        connectorConfBuilder.coreRoleIamManagedPolicies(typeSafeConfigHelper.getStringListOrReturnEmpty(config, "conf.coreRoleIamManagedPolicies"));

        // Optional JSON policy for core role
        connectorConfBuilder.coreRoleIamPolicy(typeSafeConfigHelper.getObjectAndRenderOrReturnEmpty(config, "conf.coreRoleIamPolicy"));

        // Optional string lists for service role
        connectorConfBuilder.serviceRoleIamManagedPolicies(typeSafeConfigHelper.getStringListOrReturnEmpty(config, "conf.serviceRoleIamManagedPolicies"));

        // Optional JSON policy for service role
        connectorConfBuilder.serviceRoleIamPolicy(typeSafeConfigHelper.getObjectAndRenderOrReturnEmpty(config, "conf.serviceRoleIamPolicy"));

        return connectorConfBuilder.build();
    }

    private Connector fromConfigAndPrefix(Config config, String path) {
        return typeSafeConfigHelper.convertToSdkObject(config.getObject(path), Connector.serializableBuilderClass());
    }

    @Override
    public void validateConnectorDefinitionVersion(ConnectorDefinitionVersion connectorDefinitionVersion) {
        List<Connector> connectorList = connectorDefinitionVersion.connectors();

        validateDockerConnectors(connectorList);
    }

    private void validateDockerConnectors(List<Connector> connectorList) {
        List<Connector> dockerConnectors = connectorList.stream()
                .filter(connector -> connector.connectorArn().contains("::/connectors/DockerApplicationDeployment/versions/"))
                .collect(Collectors.toList());

        List<Connector> testableDockerConnectors = dockerConnectors.stream()
                .filter(Connector::hasParameters)
                .filter(connector -> connector.parameters().containsKey(DOCKER_COMPOSE_FILE_S3_BUCKET))
                .filter(connector -> connector.parameters().containsKey(DOCKER_COMPOSE_FILE_S3_KEY))
                .collect(Collectors.toList());

        if (testableDockerConnectors.isEmpty()) {
            log.warn("No testable Docker connectors detected, not validating the Docker connector configuration");
            return;
        }

        List<Tuple2<Bucket, String>> bucketAndKeyList = testableDockerConnectors.stream()
                .map(Connector::parameters)
                .map(map -> new Tuple2<>(Bucket.builder().name(map.get(DOCKER_COMPOSE_FILE_S3_BUCKET)).build(), map.get(DOCKER_COMPOSE_FILE_S3_KEY)))
                .collect(Collectors.toList());

        for (Tuple2<Bucket, String> bucketAndKey : bucketAndKeyList) {
            Bucket bucket = bucketAndKey._1;
            String key = bucketAndKey._2;

            boolean bucketExists = Try.of(() -> s3Helper.bucketExists(bucket)).get();

            if (!bucketExists) {
                throw new RuntimeException(String.join("", "The bucket [", bucket.name(), "] does not exist. This would cause the deployment to fail. Can not continue."));
            }

            boolean objectExists = Try.of(() -> s3Helper.objectExists(bucket, key)).get();

            if (!objectExists) {
                throw new RuntimeException(String.join("", "The object [", bucket.name(), "/", key, "] does not exist. This would cause the deployment to fail. Can not continue."));
            }
        }
    }
}
