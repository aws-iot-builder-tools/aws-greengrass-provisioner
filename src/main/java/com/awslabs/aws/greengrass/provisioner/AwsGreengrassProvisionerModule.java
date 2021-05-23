package com.awslabs.aws.greengrass.provisioner;

import com.awslabs.aws.greengrass.provisioner.data.diagnostics.*;
import com.awslabs.aws.greengrass.provisioner.docker.BasicProgressHandler;
import com.awslabs.aws.greengrass.provisioner.implementations.builders.*;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.*;
import com.awslabs.aws.greengrass.provisioner.interfaces.ExceptionHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.*;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.awslabs.resultsiterator.v2.V2HelperModule;
import com.awslabs.resultsiterator.v2.implementations.V2SafeProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotify.docker.client.ProgressHandler;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.service.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Module(includes = {V2HelperModule.class})
public class AwsGreengrassProvisionerModule {
    // Normal clients that need no special configuration
    // NOTE: Using this pattern allows us to wrap the creation of these clients in some error checking code that can give the user information on what to do in the case of a failure

    @Provides
    public Ec2Client provideEc2Client() {
        return new V2SafeProvider<>(Ec2Client::create).get();
    }

    @Provides
    public CloudWatchLogsClient provideCloudWatchLogsClient() {
        return new V2SafeProvider<>(CloudWatchLogsClient::create).get();
    }

    @Provides
    public CloudFormationClient provideCloudFormationClient() {
        return new V2SafeProvider<>(CloudFormationClient::create).get();
    }

    @Provides
    public EcrClient provideEcrClient() {
        return new V2SafeProvider<>(EcrClient::create).get();
    }

    @Provides
    public SecretsManagerClient provideSecretsManagerClient() {
        return new V2SafeProvider<>(SecretsManagerClient::create).get();
    }

    @Provides
    public GGConstants provideGGConstants(BasicGGConstants basicGGConstants) {
        return basicGGConstants;
    }

    @Provides
    public PolicyHelper providePolicyHelper(BasicPolicyHelper basicPolicyHelper) {
        return basicPolicyHelper;
    }

    @Provides
    public IoHelper provideIoHelper(BasicIoHelper basicIoHelper) {
        return basicIoHelper;
    }

    @Provides
    public AwsHelper provideAwsHelper(BasicAwsHelper basicAwsHelper) {
        return basicAwsHelper;
    }

    @Provides
    public ScriptHelper provideScriptHelper(BasicScriptHelper basicScriptHelper) {
        return basicScriptHelper;
    }

    @Provides
    public GGVariables provideGGVariables(BasicGGVariables basicGGVariables) {
        return basicGGVariables;
    }

    @Provides
    public IotHelper provideIotHelper(BasicIotHelper basicIotHelper) {
        return basicIotHelper;
    }

    @Provides
    public ConnectorHelper provideConnectorHelper(BasicConnectorHelper basicConnectorHelper) {
        return basicConnectorHelper;
    }

    @Provides
    public JavaResourceHelper provideJavaResourceHelper(BasicJavaResourceHelper basicJavaResourceHelper) {
        return basicJavaResourceHelper;
    }

    @Provides
    public GreengrassResourceHelper provideGreengrassResourceHelper(BasicGreengrassResourceHelper basicGreengrassResourceHelper) {
        return basicGreengrassResourceHelper;
    }

    @Provides
    public ConfigFileHelper provideConfigFileHelper(BasicConfigFileHelper basicConfigFileHelper) {
        return basicConfigFileHelper;
    }

    @Provides
    public GreengrassHelper provideGreengrassHelper(BasicGreengrassHelper basicGreengrassHelper) {
        return basicGreengrassHelper;
    }

    @Provides
    public Python2Builder providePython2Builder(BasicPython2Builder basicPython2Builder) {
        return basicPython2Builder;
    }

    @Provides
    public Python3Builder providePython3Builder(BasicPython3Builder basicPython3Builder) {
        return basicPython3Builder;
    }

    @Provides
    public LambdaHelper provideLambdaHelper(BasicLambdaHelper basicLambdaHelper) {
        return basicLambdaHelper;
    }

    @Provides
    public NodeBuilder provideNodeBuilder(BasicNodeBuilder basicNodeBuilder) {
        return basicNodeBuilder;
    }

    @Provides
    public ExecutableBuilder provideExecutableBuilder(BasicExecutableBuilder basicExecutableBuilder) {
        return basicExecutableBuilder;
    }

    @Provides
    public ProcessHelper provideProcessHelper(BasicProcessHelper basicProcessHelper) {
        return basicProcessHelper;
    }

    @Provides
    public GradleBuilder provideGradleBuilder(BasicGradleBuilder basicGradleBuilder) {
        return basicGradleBuilder;
    }

    @Provides
    public FunctionHelper provideFunctionHelper(BasicFunctionHelper basicFunctionHelper) {
        return basicFunctionHelper;
    }

    @Provides
    public ArchiveHelper provideArchiveHelper(BasicArchiveHelper basicArchiveHelper) {
        return basicArchiveHelper;
    }

    @Provides
    public SubscriptionHelper provideSubscriptionHelper(BasicSubscriptionHelper basicSubscriptionHelper) {
        return basicSubscriptionHelper;
    }

    @Provides
    public GlobalDefaultHelper provideGlobalDefaultHelper(BasicGlobalDefaultHelper basicGlobalDefaultHelper) {
        return basicGlobalDefaultHelper;
    }

    @Provides
    public CloudFormationHelper provideCloudFormationHelper(BasicCloudFormationHelper basicCloudFormationHelper) {
        return basicCloudFormationHelper;
    }

    @Provides
    public LoggingHelper provideLoggingHelper(BasicLoggingHelper basicLoggingHelper) {
        return basicLoggingHelper;
    }

    @Provides
    public EnvironmentHelper provideEnvironmentHelper(BasicEnvironmentHelper basicEnvironmentHelper) {
        return basicEnvironmentHelper;
    }

    @Provides
    public ExecutorHelper provideExecutorHelper(SingleThreadedExecutorHelper singleThreadedExecutorHelper) {
        return singleThreadedExecutorHelper;
    }

    @Provides
    public SecretsManagerHelper provideSecretsManagerHelper(BasicSecretsManagerHelper basicSecretsManagerHelper) {
        return basicSecretsManagerHelper;
    }

    @Provides
    public TypeSafeConfigHelper provideTypeSafeConfigHelper(BasicTypeSafeConfigHelper basicTypeSafeConfigHelper) {
        return basicTypeSafeConfigHelper;
    }

    // Argument helpers
    @Provides
    public DeploymentArgumentHelper provideDeploymentArgumentHelper(BasicDeploymentArgumentHelper basicDeploymentArgumentHelper) {
        return basicDeploymentArgumentHelper;
    }

    @Provides
    public UpdateArgumentHelper provideUpdateArgumentHelper(BasicUpdateArgumentHelper basicUpdateArgumentHelper) {
        return basicUpdateArgumentHelper;
    }

    @Provides
    public QueryArgumentHelper provideQueryArgumentHelper(BasicQueryArgumentHelper basicQueryArgumentHelper) {
        return basicQueryArgumentHelper;
    }

    @Provides
    public TestArgumentHelper provideTestArgumentHelper(BasicTestArgumentHelper basicTestArgumentHelper) {
        return basicTestArgumentHelper;
    }

    @Provides
    public HsiBootstrapArgumentHelper provideHsiBootstrapArgumentHelper(BasicHsiBootstrapArgumentHelper basicHsiBootstrapArgumentHelper) {
        return basicHsiBootstrapArgumentHelper;
    }

    @Provides
    public ThreadHelper provideThreadHelper(BasicThreadHelper basicThreadHelper) {
        return basicThreadHelper;
    }

    @Provides
    public ProgressHandler provideProgressHandler(BasicProgressHandler basicProgressHandler) {
        return basicProgressHandler;
    }

    // Operations that the user can execute
    @Provides
    @ElementsIntoSet
    public Set<Operation> operations(BasicDeploymentHelper basicDeploymentHelper,
                                     BasicGroupQueryHelper basicGroupQueryHelper,
                                     BasicGroupUpdateHelper basicGroupUpdateHelper,
                                     BasicGroupTestHelper basicGroupTestHelper,
                                     BasicHsiBootstrapHelper basicHsiBootstrapHelper) {
        return new HashSet<>(Arrays.asList(basicDeploymentHelper,
                basicGroupQueryHelper,
                basicGroupUpdateHelper,
                basicGroupTestHelper,
                basicHsiBootstrapHelper));
    }

    @Provides
    public DeploymentHelper provideDeploymentHelper(BasicDeploymentHelper basicDeploymentHelper) {
        return basicDeploymentHelper;
    }

    @Provides
    public GroupQueryHelper provideGroupQueryHelper(BasicGroupQueryHelper basicGroupQueryHelper) {
        return basicGroupQueryHelper;
    }

    @Provides
    public GroupUpdateHelper provideGroupUpdateHelper(BasicGroupUpdateHelper basicGroupUpdateHelper) {
        return basicGroupUpdateHelper;
    }

    @Provides
    public GroupTestHelper provideGroupTestHelper(BasicGroupTestHelper basicGroupTestHelper) {
        return basicGroupTestHelper;
    }

    @Provides
    public HsiBootstrapHelper provideHsiBootstrapHelper(BasicHsiBootstrapHelper basicHsiBootstrapHelper) {
        return basicHsiBootstrapHelper;
    }

    @Provides
    public DeviceTesterHelper provideDeviceTesterHelper(BasicDeviceTesterHelper basicDeviceTesterHelper) {
        return basicDeviceTesterHelper;
    }

    @Provides
    public ExceptionHelper provideExceptionHelper(BasicExceptionHelper basicExceptionHelper) {
        return basicExceptionHelper;
    }

    @Provides
    public DiagnosticsHelper provideDiagnosticsHelper(BasicDiagnosticsHelper basicDiagnosticsHelper) {
        return basicDiagnosticsHelper;
    }

    @Provides
    public SshHelper provideSshHelper(BasicSshHelper basicSshHelper) {
        return basicSshHelper;
    }

    // Diagnostic rules used in the diagnostic helper
    @Provides
    @ElementsIntoSet
    public Set<DiagnosticRule> provideDiagnosticRules(TooManyIpsDiagnosticRule tooManyIpsDiagnosticRule,
                                                      NoConnectivityInformationDiagnosticRule noConnectivityInformationDiagnosticRule,
                                                      MissingRuntimeWithTextErrorDiagnosticRule1 missingRuntimeWithTextErrorDiagnosticRule1,
                                                      MissingRuntimeWithJsonErrorDiagnosticRule1 missingRuntimeWithJsonErrorDiagnosticRule1,
                                                      MissingRuntimeWithJsonErrorDiagnosticRule2 missingRuntimeWithJsonErrorDiagnosticRule2,
                                                      FunctionTimingOutDiagnosticRule functionTimingOutDiagnosticRule,
                                                      FunctionTimedOutDiagnosticRule functionTimedOutDiagnosticRule) {
        return new HashSet<>(Arrays.asList(tooManyIpsDiagnosticRule,
                noConnectivityInformationDiagnosticRule,
                missingRuntimeWithTextErrorDiagnosticRule1,
                missingRuntimeWithJsonErrorDiagnosticRule1,
                missingRuntimeWithJsonErrorDiagnosticRule2,
                functionTimingOutDiagnosticRule,
                functionTimedOutDiagnosticRule
        ));
    }

    @Provides
    public ObjectMapper provideObjectMapper() {
        return new ObjectMapper();
    }

    @Provides
    @ElementsIntoSet
    public Set<BaseService> provideBaseServices(ProvisionService provisionService,
                                                DeployService deployService) {
        return new HashSet<>(Arrays.asList(provisionService,
            deployService
        ));
    }
}
