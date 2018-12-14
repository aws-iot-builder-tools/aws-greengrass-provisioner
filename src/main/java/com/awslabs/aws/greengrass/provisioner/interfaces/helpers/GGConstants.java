package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import software.amazon.awssdk.services.greengrass.model.Function;

import java.io.File;

public interface GGConstants {
    String getRootCaUrl();

    String getRootCaName();

    String getConfigFileName();

    String getGreengrassDaemonName();

    String getCorePublicCertificateName();

    String getCorePrivateKeyName();

    String trimGgdPrefix(String thingName);

    String getDevicePublicCertificateName(String thingName);

    String getDevicePrivateKeyName(String thingName);

    String getGgIpDetectorArn();

    Function getGgIpDetectorFunction();

    String getGgShadowServiceName();

    String getGgdPrefix();

    String getBuildDirectory();

    String getCertsDirectoryPrefix();

    String getConfigDirectoryPrefix();

    String getOfficialGreengrassDockerImage();

    String getDefaultsConf();

    File getFunctionDefaultsConf();

    String getConfGreengrassContainer();

    File getDeploymentDefaultsConf();

    String getGgdDefaultsConf();
}
