package com.awslabs.aws.greengrass.provisioner.data;

import lombok.Getter;

import java.net.URL;
import java.util.Optional;

import static com.google.common.io.Resources.getResource;

public enum SDK {
    NODEJS("aws-greengrass-core-sdk-js-1.3.1.tar.gz", "e72b12456d788f4938f7131d133b75ba8a80512f", "aws-greengrass-core-sdk-js/sdk", "aws-greengrass-core-sdk-js.zip"),
    PYTHON("greengrass-core-python-sdk-1.3.0.tar.gz", "b4c3208ea6ea7c90bc3bb4f0aa4063c1c9685b7a", "aws_greengrass_core_sdk/sdk", "python_sdk_1_3_0.zip");

    @Getter
    private final String FOUNDATION = "foundation";

    @Getter
    private final String fullSdkFilename;
    @Getter
    private final Optional<String> hash;
    @Getter
    private final String innerSdkPath;
    @Getter
    private final String innerSdkZipFilename;
    @Getter(lazy = true)
    private final String fullSdkPath = innerGetFullSdkPath();
    @Getter(lazy = true)
    private final String innerSdkZipPath = innerGetInnerSdkZipPath();
    @Getter(lazy = true)
    private final Optional<URL> innerSdkZipUrl = innerGetInnerSdkZipUrl();

    SDK(String fullSdkFilename, String hash, String innerSdkPath, String innerSdkZipFilename) {
        this.fullSdkFilename = fullSdkFilename;
        this.hash = Optional.ofNullable(hash);
        this.innerSdkPath = innerSdkPath;
        this.innerSdkZipFilename = innerSdkZipFilename;
    }

    private String innerGetFullSdkPath() {
        return String.join("/", FOUNDATION, fullSdkFilename);
    }

    private String innerGetInnerSdkZipPath() {
        return String.join("/", FOUNDATION, innerSdkZipFilename);
    }

    private Optional<URL> innerGetInnerSdkZipUrl() {
        return Optional.ofNullable(getResource(getInnerSdkZipPath()));
    }
}
