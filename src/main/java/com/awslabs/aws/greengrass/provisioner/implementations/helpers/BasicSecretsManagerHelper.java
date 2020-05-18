package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SecretsManagerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretResponse;

import javax.inject.Inject;

public class BasicSecretsManagerHelper implements SecretsManagerHelper {
    private final Logger log = LoggerFactory.getLogger(BasicSecretsManagerHelper.class);
    @Inject
    SecretsManagerClient secretsManagerClient;

    @Inject
    public BasicSecretsManagerHelper() {
    }

    @Override
    public String getSecretNameFromArn(String arn) {
        DescribeSecretResponse describeSecretResponse = getDescribeSecretResponse(arn);

        throwExceptionOnSecretDeleted(describeSecretResponse);

        return describeSecretResponse.name();
    }

    @Override
    public String getSecretArnFromName(String name) {
        DescribeSecretResponse describeSecretResponse = getDescribeSecretResponse(name);

        throwExceptionOnSecretDeleted(describeSecretResponse);

        return describeSecretResponse.arn();
    }

    private void throwExceptionOnSecretDeleted(DescribeSecretResponse describeSecretResponse) {
        if (describeSecretResponse.deletedDate() == null) {
            // Secret has not been deleted, it can be used
            return;
        }

        throw new RuntimeException(String.join("", "The secret [", describeSecretResponse.name(), "] is scheduled for deletion. It can not be used in a Greengrass configuration."));
    }

    public DescribeSecretResponse getDescribeSecretResponse(String id) {
        DescribeSecretRequest describeSecretRequest = DescribeSecretRequest.builder()
                .secretId(id)
                .build();

        return secretsManagerClient.describeSecret(describeSecretRequest);
    }
}
