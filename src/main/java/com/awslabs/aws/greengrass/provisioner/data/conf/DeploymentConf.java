package com.awslabs.aws.greengrass.provisioner.data.conf;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class DeploymentConf {
    private String name;

    private String groupName;

    private List<String> functions;

    private String coreRoleName;

    private String coreRoleAssumeRolePolicy;

    private List<String> coreRolePolicies;

    private String corePolicy;

    private String lambdaRoleName;

    private String lambdaRoleAssumeRolePolicy;

    private List<String> ggds;

    @Singular
    private Map<String, String> environmentVariables;
}
