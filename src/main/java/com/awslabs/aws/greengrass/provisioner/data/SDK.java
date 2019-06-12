package com.awslabs.aws.greengrass.provisioner.data;

import java.util.Optional;

public enum SDK {
    NODEJS("aws-greengrass-core-sdk-js-1.4.0.tar.gz", "f2179ec50e9e85011d4dba68db146651712f694b", "aws-greengrass-core-sdk-js.zip");

    private final String fullSdkFilename;
    private final Optional<String> hash;
    private final String innerSdkZipFilename;

    SDK(String fullSdkFilename, String hash, String innerSdkZipFilename) {
        this.fullSdkFilename = fullSdkFilename;
        this.hash = Optional.ofNullable(hash);
        this.innerSdkZipFilename = innerSdkZipFilename;
    }

    public String getFOUNDATION() {
        return "foundation";
    }

    public String getFullSdkFilename() {
        return fullSdkFilename;
    }

    public Optional<String> getHash() {
        return hash;
    }

    public String getInnerSdkZipFilename() {
        return innerSdkZipFilename;
    }

    public String getFullSdkPath() {
        return String.join("/", getFOUNDATION(), fullSdkFilename);
    }

    public String getInnerSdkZipPath() {
        return String.join("/", getFOUNDATION(), innerSdkZipFilename);
    }
}
