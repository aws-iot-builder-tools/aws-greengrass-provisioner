package com.awslabs.aws.greengrass.provisioner;

import com.awslabs.aws.greengrass.provisioner.data.diagnostics.*;
import com.awslabs.aws.greengrass.provisioner.docker.BasicProgressHandler;
import com.awslabs.aws.greengrass.provisioner.implementations.builders.*;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.*;
import com.awslabs.aws.greengrass.provisioner.interfaces.ExceptionHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.*;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.spotify.docker.client.ProgressHandler;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProviderChain;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.greengrass.GreengrassClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sts.StsClient;

public class AwsGreengrassProvisionerModule extends AbstractModule {
    @Override
    public void configure() {
        // Normal clients that need no special configuration
        // NOTE: Using this pattern allows us to wrap the creation of these clients in some error checking code that can give the user information on what to do in the case of a failure
        bind(IotClient.class).toProvider(new SafeProvider<>(IotClient::create));
        bind(Ec2Client.class).toProvider(new SafeProvider<>(Ec2Client::create));
        bind(StsClient.class).toProvider(new SafeProvider<>(StsClient::create));
        bind(S3Client.class).toProvider(new SafeProvider<>(S3Client::create));
        bind(CloudWatchLogsClient.class).toProvider(new SafeProvider<>(CloudWatchLogsClient::create));
        bind(GreengrassClient.class).toProvider(new SafeProvider<>(GreengrassClient::create));
        bind(LambdaClient.class).toProvider(new SafeProvider<>(LambdaClient::create));
        bind(CloudFormationClient.class).toProvider(new SafeProvider<>(CloudFormationClient::create));
        bind(EcrClient.class).toProvider(new SafeProvider<>(EcrClient::create));
        bind(SecretsManagerClient.class).toProvider(new SafeProvider<>(SecretsManagerClient::create));
        bind(AwsRegionProviderChain.class).toProvider(new SafeProvider<>(DefaultAwsRegionProviderChain::new));

        // Clients that need special configuration
        // NOTE: Using this pattern allows us to wrap the creation of these clients in some error checking code that can give the user information on what to do in the case of a failure
        bind(IamClient.class).toProvider(new SafeProvider<>(() -> IamClient.builder().region(Region.AWS_GLOBAL).build()));
        bind(AwsCredentials.class).toProvider(new SafeProvider<>(() -> DefaultCredentialsProvider.create().resolveCredentials()));

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
        bind(Python2Builder.class).to(BasicPython2Builder.class);
        bind(Python3Builder.class).to(BasicPython3Builder.class);
        bind(LambdaHelper.class).to(BasicLambdaHelper.class);
        bind(NodeBuilder.class).to(BasicNodeBuilder.class);
        bind(ExecutableBuilder.class).to(BasicExecutableBuilder.class);
        bind(ProcessHelper.class).to(BasicProcessHelper.class);
        bind(GradleBuilder.class).to(BasicGradleBuilder.class);
        bind(FunctionHelper.class).to(BasicFunctionHelper.class);
        bind(ArchiveHelper.class).to(BasicArchiveHelper.class);
        bind(GGDHelper.class).to(BasicGGDHelper.class);
        bind(SubscriptionHelper.class).to(BasicSubscriptionHelper.class);
        bind(GlobalDefaultHelper.class).to(BasicGlobalDefaultHelper.class);
        bind(CloudFormationHelper.class).to(BasicCloudFormationHelper.class);
        bind(LoggingHelper.class).to(BasicLoggingHelper.class);
        bind(EnvironmentHelper.class).to(BasicEnvironmentHelper.class);
        bind(ExecutorHelper.class).to(SingleThreadedExecutorHelper.class);
        bind(SecretsManagerHelper.class).to(BasicSecretsManagerHelper.class);
        //bind(ExecutorHelper.class).to(ParallelExecutorHelper.class);

        // Argument helpers
        bind(DeploymentArgumentHelper.class).to(BasicDeploymentArgumentHelper.class);
        bind(UpdateArgumentHelper.class).to(BasicUpdateArgumentHelper.class);
        bind(QueryArgumentHelper.class).to(BasicQueryArgumentHelper.class);
        bind(TestArgumentHelper.class).to(BasicTestArgumentHelper.class);
        bind(HsiBootstrapArgumentHelper.class).to(BasicHsiBootstrapArgumentHelper.class);

        // Centralized error handling for SDK errors
        bind(SdkErrorHandler.class).to(BasicSdkErrorHandler.class);

        bind(IdExtractor.class).to(BasicIdExtractor.class);
        bind(ThreadHelper.class).to(BasicThreadHelper.class);
        bind(ProgressHandler.class).to(BasicProgressHandler.class);

        // Operations that the user can execute
        Multibinder<Operation> operationMultibinder = Multibinder.newSetBinder(binder(), Operation.class);
        operationMultibinder.addBinding().to(BasicDeploymentHelper.class);
        operationMultibinder.addBinding().to(BasicGroupQueryHelper.class);
        operationMultibinder.addBinding().to(BasicGroupUpdateHelper.class);
        operationMultibinder.addBinding().to(BasicGroupTestHelper.class);
        operationMultibinder.addBinding().to(BasicHsiBootstrapHelper.class);

        bind(DeploymentHelper.class).to(BasicDeploymentHelper.class);
        bind(GroupQueryHelper.class).to(BasicGroupQueryHelper.class);
        bind(GroupUpdateHelper.class).to(BasicGroupUpdateHelper.class);
        bind(GroupTestHelper.class).to(BasicGroupTestHelper.class);
        bind(HsiBootstrapHelper.class).to(BasicHsiBootstrapHelper.class);

        bind(DeviceTesterHelper.class).to(BasicDeviceTesterHelper.class);

        bind(ExceptionHelper.class).to(BasicExceptionHelper.class);

        bind(DiagnosticsHelper.class).to(BasicDiagnosticsHelper.class);

        bind(SshHelper.class).to(BasicSshHelper.class);

        // Diagnostic rules used in the diagnostic helper
        Multibinder<DiagnosticRule> diagnosticRuleMultibinder = Multibinder.newSetBinder(binder(), DiagnosticRule.class);
        diagnosticRuleMultibinder.addBinding().to(TooManyIpsDiagnosticRule.class);
        diagnosticRuleMultibinder.addBinding().to(NoConnectivityInformationDiagnosticRule.class);
        diagnosticRuleMultibinder.addBinding().to(MissingRuntimeWithTextErrorDiagnosticRule1.class);
        diagnosticRuleMultibinder.addBinding().to(MissingRuntimeWithJsonErrorDiagnosticRule1.class);
        diagnosticRuleMultibinder.addBinding().to(MissingRuntimeWithJsonErrorDiagnosticRule2.class);
        diagnosticRuleMultibinder.addBinding().to(FunctionTimingOutDiagnosticRule.class);
        diagnosticRuleMultibinder.addBinding().to(FunctionTimedOutDiagnosticRule.class);
    }
}
