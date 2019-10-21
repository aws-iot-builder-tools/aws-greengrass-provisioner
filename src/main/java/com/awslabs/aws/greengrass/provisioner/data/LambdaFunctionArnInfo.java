package com.awslabs.aws.greengrass.provisioner.data;

import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
public abstract class LambdaFunctionArnInfo {
    public abstract Optional<String> getError();

    public abstract String getQualifier();

    public abstract String getBaseArn();

    public abstract String getQualifiedArn();

    public String getGroupFunctionName() {
        return getBaseArn().replaceAll("^.*:", "");
    }

    // This is optional because when the function is first built the alias ARN is not available
    public abstract Optional<String> getAliasArn();
}
