package com.awslabs.aws.greengrass.provisioner.data;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum EC2LinuxVersion {
    AmazonLinux2,
    Ubuntu1804;

    public static String getList() {
        return Arrays.stream(EC2LinuxVersion.values())
                .map(EC2LinuxVersion::name)
                .collect(Collectors.joining(", "));
    }
}
