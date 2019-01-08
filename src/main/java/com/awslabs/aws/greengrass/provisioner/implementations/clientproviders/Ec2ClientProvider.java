package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;
import software.amazon.awssdk.services.ec2.Ec2Client;

import javax.inject.Inject;

public class Ec2ClientProvider implements SafeProvider<Ec2Client> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public Ec2ClientProvider() {
    }

    @Override
    public Ec2Client get() {
        return safeGet(sdkErrorHandler);
    }

    public Ec2Client unsafeGet() {
        return Ec2Client.create();
    }
}
