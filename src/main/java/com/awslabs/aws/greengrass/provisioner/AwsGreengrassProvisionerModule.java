package com.awslabs.aws.greengrass.provisioner;

import com.amazonaws.regions.AwsRegionProviderChain;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.ecr.AmazonECRClient;
import com.amazonaws.services.greengrass.AWSGreengrassClient;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iotdata.AWSIotDataClient;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.awslabs.aws.greengrass.provisioner.docker.BasicDockerHelper;
import com.awslabs.aws.greengrass.provisioner.docker.BasicDockerPushHandler;
import com.awslabs.aws.greengrass.provisioner.docker.UnixSocketDockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerHelper;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerPushHandler;
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
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

@Module
public abstract class AwsGreengrassProvisionerModule {
    // Create a bunch of providers for default clients that check for errors
    @Provides
    public static AWSIotClient awsIotClient(AWSIotClientProvider awsIotClientProvider) {
        return awsIotClientProvider.get();
    }

    @Provides
    public static AWSIotDataClient awsIotDataClient(AWSIotDataClientProvider awsIotDataClientProvider) {
        return awsIotDataClientProvider.get();
    }

    @Provides
    public static AmazonIdentityManagementClient amazonIdentityManagementClient(AmazonIdentityManagementClientProvider amazonIdentityManagementClientProvider) {
        return amazonIdentityManagementClientProvider.get();
    }

    @Provides
    public static AWSSecurityTokenServiceClient awsSecurityTokenServiceClient(AWSSecurityTokenServiceClientProvider awsSecurityTokenServiceClientProvider) {
        return awsSecurityTokenServiceClientProvider.get();
    }

    @Provides
    public static AWSGreengrassClient awsGreengrassClient(AWSGreengrassClientProvider awsGreengrassClientProvider) {
        return awsGreengrassClientProvider.get();
    }

    @Provides
    public static AWSLambdaClient awsLambdaClient(AWSLambdaClientProvider awsLambdaClientProvider) {
        return awsLambdaClientProvider.get();
    }

    @Provides
    public static AmazonCloudFormationClient amazonCloudFormationClient(AmazonCloudFormationClientProvider amazonCloudFormationClientProvider) {
        return amazonCloudFormationClientProvider.get();
    }

    @Provides
    public static AmazonECRClient amazonECRClient(AmazonECRClientProvider amazonECRClientProvider) {
        return amazonECRClientProvider.get();
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
    public abstract DockerPushHandler dockerPushHandler(BasicDockerPushHandler basicDockerPushHandler);

    @Binds
    public abstract IdExtractor idExtractor(BasicIdExtractor basicIdExtractor);

    @Binds
    public abstract GroupQueryHelper groupQueryProcessor(BasicGroupQueryHelper basicGroupQueryProcessor);

    @Binds
    public abstract GroupUpdateHelper groupUpdateProcessor(BasicGroupUpdateHelper basicGroupUpdateProcessor);
}
