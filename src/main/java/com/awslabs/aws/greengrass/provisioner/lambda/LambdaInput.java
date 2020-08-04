package com.awslabs.aws.greengrass.provisioner.lambda;

public class LambdaInput {
    public String AccessKeyId;
    public String SecretAccessKey;
    public String SessionToken;
    public String Region;
    public String Csr;
    public String CertificateArn;
    public boolean ServiceRoleExists;
    public String CoreRoleName;
    public String CorePolicyName;
    public String GroupName;
    public String KeyPath;
}
