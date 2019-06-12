package com.awslabs.aws.greengrass.provisioner.data.conf;

import org.immutables.value.Value;

import java.util.List;
import java.util.Map;

@Value.Immutable
public abstract class DeploymentConf {
    public abstract String getName();

    public abstract String getGroupName();

    public abstract List<String> getFunctions();

    public abstract String getCoreRoleName();

    public abstract String getCoreRoleAssumeRolePolicy();

    public abstract List<String> getCoreRolePolicies();

    public abstract String getCorePolicy();

    public abstract String getLambdaRoleName();

    public abstract String getLambdaRoleAssumeRolePolicy();

    public abstract List<String> getGgds();

    public abstract Map<String, String> getEnvironmentVariables();
}
