package com.awslabs.aws.greengrass.provisioner.data;

import lombok.Getter;

import java.net.URL;
import java.util.Optional;

import static com.amazonaws.util.ClassLoaderHelper.getResource;

public enum SDK {
    NODEJS("aws-greengrass-core-sdk-js-1.2.0.tar.gz", "7702b921d8f1e1d3635157fee11044db1bc53dfd", "aws_greengrass_core_sdk_js/sdk", "aws-greengrass-core-sdk-js.zip"),
    PYTHON("greengrass-core-python-sdk-1.2.0.tar.gz", "47e5198fe3bc731219e44ea2636c8718ac6510c3", "aws_greengrass_core_sdk/sdk", "python_sdk_1_2_0.zip");

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
