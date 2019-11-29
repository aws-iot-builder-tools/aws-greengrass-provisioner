package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

public interface SecretsManagerHelper {
    String getSecretNameFromArn(String arn);

    String getSecretArnFromName(String name);
}
