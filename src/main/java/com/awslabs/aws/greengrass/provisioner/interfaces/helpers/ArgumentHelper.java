package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
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
}
