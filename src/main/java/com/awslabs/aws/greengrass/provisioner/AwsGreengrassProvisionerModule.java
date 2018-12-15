package com.awslabs.aws.greengrass.provisioner;

import com.awslabs.aws.greengrass.provisioner.docker.BasicDockerHelper;
import com.awslabs.aws.greengrass.provisioner.docker.BasicProgressHandler;
import com.awslabs.aws.greengrass.provisioner.docker.UnixSocketDockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerHelper;
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
import com.spotify.docker.client.ProgressHandler;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
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

@Module
public abstract class AwsGreengrassProvisionerModule {
    // Create a bunch of providers for default clients that check for errors
    @Provides
    public static IotClient iotClient(IotClientProvider iotClientProvider) {
        return iotClientProvider.get();
    }

    @Provides
    public static Ec2Client ec2Client(Ec2ClientProvider ec2ClientProvider) {
        return ec2ClientProvider.get();
    }

    @Provides
    public static IamClient iamClient(IamClientProvider iamClientProvider) {
        return iamClientProvider.get();
    }

    @Provides
    public static StsClient stsClient(StsClientProvider stsClientProvider) {
        return stsClientProvider.get();
    }

    @Provides
    public static GreengrassClient greengrassClient(GreengrassClientProvider greengrassClientProvider) {
        return greengrassClientProvider.get();
    }

    @Provides
    public static LambdaClient lambdaClient(LambdaClientProvider lambdaClientProvider) {
        return lambdaClientProvider.get();
    }

    @Provides
    public static CloudFormationClient cloudFormationClient(CloudFormationClientProvider cloudFormationClientProvider) {
        return cloudFormationClientProvider.get();
    }

    @Provides
    public static EcrClient ecrClient(EcrClientProvider ecrClientProvider) {
        return ecrClientProvider.get();
    }

    // Docker
    @Provides
    public static AwsRegionProviderChain awsRegionProviderChain() {
        return new DefaultAwsRegionProviderChain();
    }

    @Binds
    public abstract GGConstants ggConstants(BasicGGConstants basicGGConstants);

    @Binds
    public abstract PolicyHelper policyHelper(BasicPolicyHelper basicPolicyHelper);

    @Binds
    public abstract IoHelper ioHelper(BasicIoHelper basicIoHelper);

    @Binds
    public abstract JsonHelper jsonHelper(BasicJsonHelper basicJsonHelper);

    @Binds
    public abstract AwsHelper awsHelper(BasicAwsHelper basicAwsHelper);

    @Binds
    public abstract ScriptHelper scriptHelper(BasicScriptHelper basicScriptHelper);

    @Binds
    public abstract GGVariables ggVariables(BasicGGVariables basicGGVariables);

    @Binds
    public abstract IotHelper iotHelper(BasicIotHelper basicIotHelper);

    @Binds
    public abstract ResourceHelper resourceHelper(BasicResourceHelper basicResourceHelper);

    @Binds
    public abstract ConfigFileHelper configFileHelper(BasicConfigFileHelper basicConfigFileHelper);

    @Binds
    public abstract GreengrassHelper greengrassHelper(BasicGreengrassHelper basicGreengrassHelper);

    @Binds
    public abstract IamHelper iamHelper(BasicIamHelper basicIamHelper);

    @Binds
    public abstract LambdaHelper lambdaHelper(BasicLambdaHelper basicLambdaHelper);

    @Binds
    public abstract PythonBuilder pythonBuilder(BasicPythonBuilder basicPythonBuilder);

    @Binds
    public abstract NodeBuilder nodeBuilder(BasicNodeBuilder basicNodeBuilder);

    @Binds
    public abstract ProcessHelper processHelper(BasicProcessHelper basicProcessHelper);

    @Binds
    public abstract MavenBuilder mavenBuilder(BasicMavenBuilder basicMavenBuilder);

    @Binds
    public abstract GradleBuilder gradleBuilder(BasicGradleBuilder basicGradleBuilder);

    @Binds
    public abstract FunctionHelper functionHelper(BasicFunctionHelper basicFunctionHelper);

    @Binds
    public abstract ArchiveHelper archiveHelper(BasicArchiveHelper basicArchiveHelper);

    @Binds
    public abstract GGDHelper ggdHelper(BasicGGDHelper basicGGDHelper);

    @Binds
    public abstract DeploymentHelper deploymentHelper(BasicDeploymentHelper basicDeploymentHelper);

    @Binds
    public abstract SubscriptionHelper subscriptionHelper(BasicSubscriptionHelper basicSubscriptionHelper);

    @Binds
    public abstract GlobalDefaultHelper globalDefaultHelper(BasicGlobalDefaultHelper basicGlobalDefaultHelper);

    @Binds
    public abstract CloudFormationHelper cloudFormationHelper(BasicCloudFormationHelper basicCloudFormationHelper);

    @Binds
    public abstract DockerHelper dockerHelper(BasicDockerHelper basicDockerHelper);

    @Binds
    public abstract DeploymentArgumentHelper deploymentArgumentHelper(BasicDeploymentArgumentHelper basicDeploymentArgumentHelper);

    @Binds
    public abstract UpdateArgumentHelper updateArgumentHelper(BasicUpdateArgumentHelper basicUpdateArgumentHelper);

    @Binds
    public abstract QueryArgumentHelper queryArgumentHelper(BasicQueryArgumentHelper basicQueryArgumentHelper);

    @Binds
    public abstract LoggingHelper loggingHelper(BasicLoggingHelper basicLoggingHelper);

    @Binds
    public abstract EnvironmentHelper environmentHelper(BasicEnvironmentHelper basicEnvironmentHelper);

    @Binds
    public abstract ExecutorHelper executorHelper(SingleThreadedExecutorHelper singleThreadedExecutorHelper);
//    public abstract ExecutorHelper executorHelper(ParallelExecutorHelper parallelExecutorHelper);

    // Centralized error handling for SDK errors
    @Binds
    public abstract SdkErrorHandler sdkErrorHandler(BasicSdkErrorHandler basicSdkErrorHandler);

    @Binds
    public abstract DockerClientProvider dockerClientProvider(UnixSocketDockerClientProvider unixSocketDockerClientProvider);

    @Binds
    public abstract IdExtractor idExtractor(BasicIdExtractor basicIdExtractor);

    @Binds
    public abstract GroupQueryHelper groupQueryProcessor(BasicGroupQueryHelper basicGroupQueryProcessor);

    @Binds
    public abstract GroupUpdateHelper groupUpdateProcessor(BasicGroupUpdateHelper basicGroupUpdateProcessor);

    @Binds
    public abstract ThreadHelper threadHelper(BasicThreadHelper basicThreadHelper);

    @Binds
    public abstract ProgressHandler progressHandler(BasicProgressHandler basicProgressHandler);
}
