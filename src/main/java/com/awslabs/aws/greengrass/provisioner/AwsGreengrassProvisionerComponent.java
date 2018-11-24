package com.awslabs.aws.greengrass.provisioner;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = AwsGreengrassProvisionerModule.class)
public interface AwsGreengrassProvisionerComponent {
    AwsGreengrassProvisioner GG_PROVISIONER();

    SdkErrorHandler SDK_ERROR_HANDLER();
}
