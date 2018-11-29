package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.amazonaws.services.greengrass.model.Function;

public interface GGConstants {
    String getArchitectureNameList();

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
}
