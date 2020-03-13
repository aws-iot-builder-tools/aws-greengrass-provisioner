package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.iot.data.ThingName;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.FunctionConfiguration;

import javax.inject.Inject;
import java.io.File;

public class BasicGGConstants implements GGConstants {
    private static final String DEVICE_KEY = "device.key";
    private static final String DEVICE_CRT = "device.crt";
    private static final String DEPLOYMENTS_DIRECTORY = "deployments";
    @Inject
    IoHelper ioHelper;

    @Inject
    public BasicGGConstants() {
    }

    @Override
    public String getRootCaUrl() {
        return "https://www.amazontrust.com/repository/AmazonRootCA1.pem";
    }

    @Override
    public String getRootCaName() {
        return "root.ca.pem";
    }

    @Override
    public String getConfigFileName() {
        return "config.json";
    }

    @Override
    public String getGreengrassDaemonName() {
        return "/greengrass/ggc/core/greengrassd";
    }

    @Override
    public String getCorePublicCertificateName() {
        return "core.crt";
    }

    @Override
    public String getCorePrivateKeyName() {
        return "core.key";
    }

    @Override
    public String getGgIpDetectorArn() {
        return "arn:aws:lambda:::function:GGIPDetector:1";
    }

    @Override
    public String getGgShadowServiceName() {
        return "GGShadowService";
    }

    @Override
    public String getGgdPrefix() {
        return "ggd";
    }

    @Override
    public String getBuildDirectory() {
        return "build";
    }

    @Override
    public String getCertsDirectoryPrefix() {
        return "certs";
    }

    @Override
    public String getConfigDirectoryPrefix() {
        return "config";
    }

    @Override
    public String getOfficialGreengrassAccountId() {
        return "216483018798";
    }

    @Override
    public String getDefaultsConf() {
        return "defaults.conf";
    }

    @Override
    public File getFunctionDefaultsConf() {
        return new File(String.join("/", DEPLOYMENTS_DIRECTORY, "function.defaults.conf"));
    }

    @Override
    public File getConnectorDefaultsConf() {
        return new File(String.join("/", DEPLOYMENTS_DIRECTORY, "connector.defaults.conf"));
    }

    @Override
    public String getConfGreengrassContainer() {
        return "conf.greengrassContainer";
    }

    @Override
    public File getDeploymentDefaultsConf() {
        return new File(String.join("/", DEPLOYMENTS_DIRECTORY, "deployment.defaults.conf"));
    }

    @Override
    public Config getDeploymentDefaults() {
        return ConfigFactory.parseFile(getDeploymentDefaultsConf());
    }

    @Override
    public String getGgdDefaultsConf() {
        return "ggds/ggd.defaults.conf";
    }

    @Override
    public String getOfficialGreengrassEcrEndpoint() {
        return String.join(".",
                getOfficialGreengrassAccountId(),
                "dkr.ecr.us-west-2.amazonaws.com");
    }

    @Override
    public String getOfficialGreengrassDockerImage() {
        return String.join("/",
                getOfficialGreengrassEcrEndpoint(),
                "aws-iot-greengrass:1.10.0-amazonlinux");
    }

    @Override
    public String trimGgdPrefix(ThingName thingName) {
        return thingName.getName().replaceFirst(getGgdPrefix() + "-", "");
    }

    @Override
    public String getDevicePublicCertificateName(ThingName thingName) {
        return String.join(".", getGgdPrefix(), thingName.getName(), DEVICE_CRT);
    }

    @Override
    public String getDevicePrivateKeyName(ThingName thingName) {
        return String.join(".", getGgdPrefix(), thingName.getName(), DEVICE_KEY);
    }

    @Override
    public Function getGgIpDetectorFunction() {
        FunctionConfiguration functionConfiguration = FunctionConfiguration.builder()
                .memorySize(32768)
                .pinned(true)
                .timeout(3)
                .build();

        Function function = Function.builder()
                .functionArn(getGgIpDetectorArn())
                .id(ioHelper.getUuid())
                .functionConfiguration(functionConfiguration)
                .build();

        return function;
    }
}
