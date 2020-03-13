package com.awslabs.aws.greengrass.provisioner.data.conf;

import com.awslabs.iot.data.GreengrassGroupName;
import com.awslabs.lambda.data.FunctionName;
import org.immutables.value.Value;
import software.amazon.awssdk.services.greengrass.model.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Value.Immutable
public abstract class DeploymentConf {
    public abstract String getName();

    public abstract GreengrassGroupName getGroupName();

    /**
     * This is the list of the functions in the deployment configuration file. Even though it is a list of
     * FunctionName objects they may not be Lambda function names. They can be either a function name, a URL
     * to a Github repo, or an existing Lambda function alias
     * @return
     */
    public abstract List<FunctionName> getFunctions();

    public abstract RoleConf getCoreRoleConf();

    public abstract Optional<RoleConf> getLambdaRoleConf();

    public abstract Optional<RoleConf> getServiceRoleConf();

    public abstract List<String> getGgds();

    public abstract Map<String, String> getEnvironmentVariables();

    public abstract boolean isSyncShadow();

    public abstract List<String> getConnectors();

    public abstract Optional<List<Logger>> getLoggers();
}
