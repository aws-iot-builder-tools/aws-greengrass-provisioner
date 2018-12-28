package com.awslabs.aws.greengrass.provisioner;

import com.awslabs.aws.greengrass.provisioner.docker.BasicProgressHandler;
import com.awslabs.aws.greengrass.provisioner.implementations.builders.BasicGradleBuilder;
import com.awslabs.aws.greengrass.provisioner.implementations.builders.BasicMavenBuilder;
import com.awslabs.aws.greengrass.provisioner.implementations.builders.BasicNodeBuilder;
import com.awslabs.aws.greengrass.provisioner.implementations.builders.BasicPythonBuilder;
import com.awslabs.aws.greengrass.provisioner.implementations.clientproviders.*;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.*;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.GradleBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.MavenBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.NodeBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.PythonBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.google.inject.AbstractModule;
import com.spotify.docker.client.ProgressHandler;
import software.amazon.awssdk.regions.providers.AwsRegionProviderChain;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.greengrass.GreengrassClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.sts.StsClient;

public class AwsGreengrassProvisionerModule extends AbstractModule {
    @Override
    public void configure() {
        // Create a bunch of providers for default clients that check for errors
        bind(IotClient.class).toProvider(IotClientProvider.class);
        bind(Ec2Client.class).toProvider(Ec2ClientProvider.class);
        bind(IamClient.class).toProvider(IamClientProvider.class);
        bind(StsClient.class).toProvider(StsClientProvider.class);
        bind(GreengrassClient.class).toProvider(GreengrassClientProvider.class);
        bind(LambdaClient.class).toProvider(LambdaClientProvider.class);
        bind(CloudFormationClient.class).toProvider(CloudFormationClientProvider.class);
        bind(EcrClient.class).toProvider(EcrClientProvider.class);
//        bind(AwsRegionProviderChain.class).toProvider(DefaultAwsRegionProviderChain::new);

        bind(GGConstants.class).to(BasicGGConstants.class);
        bind(PolicyHelper.class).to(BasicPolicyHelper.class);
        bind(IoHelper.class).to(BasicIoHelper.class);
        bind(JsonHelper.class).to(BasicJsonHelper.class);
        bind(AwsHelper.class).to(BasicAwsHelper.class);
        bind(ScriptHelper.class).to(BasicScriptHelper.class);
        bind(GGVariables.class).to(BasicGGVariables.class);
        bind(IotHelper.class).to(BasicIotHelper.class);

        bind(ResourceHelper.class).to(BasicResourceHelper.class);
        bind(ConfigFileHelper.class).to(BasicConfigFileHelper.class);
        bind(GreengrassHelper.class).to(BasicGreengrassHelper.class);
        bind(IamHelper.class).to(BasicIamHelper.class);
        bind(LambdaHelper.class).to(BasicLambdaHelper.class);
        bind(PythonBuilder.class).to(BasicPythonBuilder.class);
        bind(NodeBuilder.class).to(BasicNodeBuilder.class);
        bind(ProcessHelper.class).to(BasicProcessHelper.class);
        bind(MavenBuilder.class).to(BasicMavenBuilder.class);
        bind(GradleBuilder.class).to(BasicGradleBuilder.class);
        bind(FunctionHelper.class).to(BasicFunctionHelper.class);
        bind(ArchiveHelper.class).to(BasicArchiveHelper.class);
        bind(GGDHelper.class).to(BasicGGDHelper.class);
        bind(DeploymentHelper.class).to(BasicDeploymentHelper.class);
        bind(SubscriptionHelper.class).to(BasicSubscriptionHelper.class);
        bind(GlobalDefaultHelper.class).to(BasicGlobalDefaultHelper.class);
        bind(CloudFormationHelper.class).to(BasicCloudFormationHelper.class);
        bind(DeploymentArgumentHelper.class).to(BasicDeploymentArgumentHelper.class);
        bind(UpdateArgumentHelper.class).to(BasicUpdateArgumentHelper.class);
        bind(QueryArgumentHelper.class).to(BasicQueryArgumentHelper.class);
        bind(LoggingHelper.class).to(BasicLoggingHelper.class);
        bind(EnvironmentHelper.class).to(BasicEnvironmentHelper.class);
        bind(ExecutorHelper.class).to(SingleThreadedExecutorHelper.class);
        //bind(ExecutorHelper.class).to(ParallelExecutorHelper.class);

        // Centralized error handling for SDK errors
        bind(SdkErrorHandler.class).to(BasicSdkErrorHandler.class);

        bind(IdExtractor.class).to(BasicIdExtractor.class);
        bind(GroupQueryHelper.class).to(BasicGroupQueryHelper.class);
        bind(GroupUpdateHelper.class).to(BasicGroupUpdateHelper.class);
        bind(ThreadHelper.class).to(BasicThreadHelper.class);
        bind(ProgressHandler.class).to(BasicProgressHandler.class);
    }
}
