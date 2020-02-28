package com.awslabs.aws.greengrass.provisioner;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.awslabs.aws.greengrass.provisioner.lambda.AwsGreengrassProvisionerLambda;
import com.awslabs.general.helpers.interfaces.JsonHelper;
import com.awslabs.resultsiterator.v2.interfaces.V2SdkErrorHandler;
import dagger.Component;

@Component(modules = AwsGreengrassProvisionerModule.class)
public interface Injector {
    AwsGreengrassProvisioner awsGreengrassProvisioner();
    V2SdkErrorHandler v2SdkErrorHandler();
    AwsGreengrassProvisionerLambda awsGreengrassProvisionerLambda();
    IoHelper ioHelper();
    ThreadHelper threadHelper();
    GlobalDefaultHelper globalDefaultHelper();
    ProcessHelper processHelper();
    GreengrassHelper greengrassHelper();
    DeploymentHelper deploymentHelper();
    GGVariables ggVariables();
    JsonHelper jsonHelper();
    IotHelper iotHelper();
}
