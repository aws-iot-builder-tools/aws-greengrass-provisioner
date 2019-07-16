package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.EC2LinuxVersion;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import io.vavr.control.Try;

public interface DeploymentArgumentHelper extends ArgumentHelper<DeploymentArguments> {
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
