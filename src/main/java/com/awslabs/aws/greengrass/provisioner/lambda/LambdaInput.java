package com.awslabs.aws.greengrass.provisioner.lambda;

public class LambdaInput {
    public String accessKeyId;
    public String secretKey;
    public String sessionToken;
    public String csr;
    public String certificateArn;
    public boolean serviceRoleExists;
    public String coreRoleName;
    public String corePolicyName;
    public String groupName;
    public String keyPath;
}
