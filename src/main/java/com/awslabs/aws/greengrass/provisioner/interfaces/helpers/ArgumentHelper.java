package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.data.EC2LinuxVersion;
import com.awslabs.aws.greengrass.provisioner.data.arguments.Arguments;
import io.vavr.control.Try;

public interface ArgumentHelper<T extends Arguments> {
    void displayUsage();

    T parseArguments(String[] args);

    default Architecture getArchitecture(String architectureString) {
        return Try.of(() -> Architecture.valueOf(architectureString))
                .recover(IllegalArgumentException.class, throwable -> throwDescriptiveArchitectureException(architectureString))
                .get();

    }

    default Architecture throwDescriptiveArchitectureException(String architectureString) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[");
        stringBuilder.append(architectureString);
        stringBuilder.append("] is not a valid architecture.");
        stringBuilder.append("\r\n");
        stringBuilder.append("Valid options are: ");
        stringBuilder.append(Architecture.getList());

        throw new RuntimeException(stringBuilder.toString());
    }

    default EC2LinuxVersion getEc2LinuxVersion(String ec2Launch) {
        return Try.of(() -> EC2LinuxVersion.valueOf(ec2Launch))
                .recover(IllegalArgumentException.class, throwable -> throwDescriptiveEc2LinuxVersionException(ec2Launch))
                .get();

    }

    default EC2LinuxVersion throwDescriptiveEc2LinuxVersionException(String ec2Launch) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[");
        stringBuilder.append(ec2Launch);
        stringBuilder.append("] is not a valid EC2 Linux version.");
        stringBuilder.append("\r\n");
        stringBuilder.append("Valid options are: ");
        stringBuilder.append(EC2LinuxVersion.getList());

        throw new RuntimeException(stringBuilder.toString());
    }

}
