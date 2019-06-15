package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;

import javax.inject.Inject;

public class CloudWatchLogsClientProvider implements SafeProvider<CloudWatchLogsClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public CloudWatchLogsClientProvider() {
    }

    @Override
    public CloudWatchLogsClient get() {
        return safeGet(sdkErrorHandler);
    }

    public CloudWatchLogsClient unsafeGet() {
        return CloudWatchLogsClient.create();
    }
}