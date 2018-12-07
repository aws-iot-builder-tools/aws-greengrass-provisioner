package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import lombok.Getter;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.FunctionConfiguration;

import javax.inject.Inject;


public class BasicGGConstants implements GGConstants {
    public static final String DEVICE_KEY = "device.key";
    public static final String DEVICE_CRT = "device.crt";
    @Getter(lazy = true)
    private final String architectureNameList = buildArchitectureNameList();
    @Getter
    private final String rootCaUrl = "https://www.symantec.com/content/en/us/enterprise/verisign/roots/VeriSign-Class%203-Public-Primary-Certification-Authority-G5.pem";
    @Getter
    private final String rootCaName = "root.ca.pem";
    @Getter
    private final String configFileName = "config.json";
    @Getter
    private final String greengrassDaemonName = "/greengrass/ggc/core/greengrassd";
    @Getter
    private final String lambdaDummyRoleName = "GGLambdaDummyRole";
    @Getter
    private final String corePublicCertificateName = "core.crt";
    @Getter
    private final String corePrivateKeyName = "core.key";
    @Getter
    private final String ggIpDetectorArn = "arn:aws:lambda:::function:GGIPDetector:1";
    @Getter
    private final String ggShadowServiceName = "GGShadowService";
    @Getter
    private final String ggdPrefix = "ggd";
    @Inject
    IoHelper ioHelper;
    @Getter(lazy = true)
    private final Function ggIpDetectorFunction = buildGgIpDetectorFunction();

    @Inject
    public BasicGGConstants() {
    }

    @Override
    public String trimGgdPrefix(String thingName) {
        return thingName.replaceFirst(ggdPrefix + "-", "");
    }

    @Override
    public String getDevicePublicCertificateName(String thingName) {
        return String.join(".", ggdPrefix, thingName, DEVICE_CRT);
    }

    @Override
    public String getDevicePrivateKeyName(String thingName) {
        return String.join(".", ggdPrefix, thingName, DEVICE_KEY);
    }

    public String buildArchitectureNameList() {
        StringBuilder stringBuilder = new StringBuilder();
        String separator = "";

        for (Architecture architecture : Architecture.values()) {
            stringBuilder.append(separator);
            stringBuilder.append(architecture.toString());
            separator = ", ";
        }

        return stringBuilder.toString();
    }

    private Function buildGgIpDetectorFunction() {
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
