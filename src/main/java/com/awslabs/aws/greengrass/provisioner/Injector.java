package com.awslabs.aws.greengrass.provisioner;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.awslabs.aws.greengrass.provisioner.lambda.AwsGreengrassProvisionerLambda;
import com.awslabs.general.helpers.interfaces.JsonHelper;
import com.awslabs.iot.helpers.interfaces.V2GreengrassHelper;
import com.awslabs.iot.helpers.interfaces.V2IotHelper;
import com.awslabs.resultsiterator.v2.interfaces.V2SdkErrorHandler;
import dagger.Component;

import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.AwsGreengrassProvisionerPolymorphLambda;
import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.service.ProvisionService;
import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.service.DeployService;

@Component(modules = AwsGreengrassProvisionerModule.class)
public interface Injector {
    AwsGreengrassProvisioner awsGreengrassProvisioner();

    V2SdkErrorHandler v2SdkErrorHandler();

    AwsGreengrassProvisionerLambda awsGreengrassProvisionerLambda();

    AwsGreengrassProvisionerPolymorphLambda awsGreengrassProvisionerPolymorphLambda();

    ProvisionService provisionService();
    DeployService deployService();

    IoHelper ioHelper();

    ThreadHelper threadHelper();

    GlobalDefaultHelper globalDefaultHelper();

    ProcessHelper processHelper();

    GreengrassHelper greengrassHelper();

    V2GreengrassHelper v2GreengrassHelper();

    V2IotHelper v2IotHelper();

    DeploymentHelper deploymentHelper();

    GGVariables ggVariables();

    GGConstants ggConstants();

    JsonHelper jsonHelper();

    IotHelper iotHelper();
}
