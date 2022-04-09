package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.*;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.conf.DeploymentConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.ImmutableFunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.resources.*;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.GradleBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.awslabs.lambda.data.*;
import com.awslabs.lambda.helpers.interfaces.V2LambdaHelper;
import com.awslabs.s3.helpers.data.ImmutableS3Bucket;
import com.awslabs.s3.helpers.data.ImmutableS3Key;
import com.awslabs.s3.helpers.data.S3Bucket;
import com.awslabs.s3.helpers.data.S3Key;
import com.awslabs.s3.helpers.interfaces.V2S3Helper;
import com.typesafe.config.*;
import io.vavr.Tuple3;
import io.vavr.control.Either;
import io.vavr.control.Try;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.util.encoders.Base64;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.greengrass.model.EncodingType;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.FunctionIsolationMode;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationResponse;

import javax.inject.Inject;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BasicFunctionHelper implements FunctionHelper {
    public static final String NO_COLONS_REGEX = "[^:]*";
    public static final String ARN_AWS_LAMBDA_FUNCTION_PREFIX_REGEX = String.join("", "arn:aws:lambda:", NO_COLONS_REGEX, ":", NO_COLONS_REGEX, ":function:");
    public static final String FULL_LAMBDA_ARN_CHECK_REGEX = String.join("", ARN_AWS_LAMBDA_FUNCTION_PREFIX_REGEX, NO_COLONS_REGEX, ":", NO_COLONS_REGEX);
    public static final String EXISTING_LAMBDA_FUNCTION_WILDCARD = "~";
    public static final String ENDS_WITH_ALIAS_REGEX = "[^:]*:[^:]*$";
    public static final int LAMBDA_FUNCTION_DIRECT_UPLOAD_SIZE_LIMIT_IN_BYTES = 69905067;
    private final Logger log = LoggerFactory.getLogger(BasicFunctionHelper.class);
    @Inject
    GreengrassHelper greengrassHelper;
    @Inject
    LambdaHelper lambdaHelper;
    @Inject
    V2LambdaHelper v2LambdaHelper;
    @Inject
    GradleBuilder gradleBuilder;
    @Inject
    ProcessHelper processHelper;
    @Inject
    GGConstants ggConstants;
    @Inject
    GGVariables ggVariables;
    @Inject
    IoHelper ioHelper;
    @Inject
    SecretsManagerHelper secretsManagerHelper;
    @Inject
    TypeSafeConfigHelper typeSafeConfigHelper;
    @Inject
    V2S3Helper v2S3Helper;

    @Inject
    public BasicFunctionHelper() {
    }

    private Path getFunctionPath(File functionConf) {
        return functionConf.toPath().getParent();
    }

    private Either<FunctionAliasArn, File> getFunctionAliasArnOrFunctionConfPath(String pathOrUrlOrArn, DeploymentArguments deploymentArguments) throws IOException {
        if (pathOrUrlOrArn.contains(EXISTING_LAMBDA_FUNCTION_WILDCARD)) {
            if (pathOrUrlOrArn.matches(ENDS_WITH_ALIAS_REGEX)) {
                return Either.left(lambdaHelper.findFullFunctionArnByPartialName(pathOrUrlOrArn));
            } else {
                throw new RuntimeException(String.join("", "Lambda function reference [", pathOrUrlOrArn, "] does not include an alias. Append a colon and the alias to the partial name and try again."));
            }
        }

        if (pathOrUrlOrArn.startsWith(HTTPS)) {
            return Either.right(getGitFunctionConfFile(pathOrUrlOrArn));
        } else if (pathOrUrlOrArn.matches(String.join("", ARN_AWS_LAMBDA_FUNCTION_PREFIX_REGEX, ".*$"))) {
            throwExceptionIfLambdaArnIsIncomplete(pathOrUrlOrArn);

            return Either.left(ImmutableFunctionAliasArn.builder().aliasArn(pathOrUrlOrArn).build());
        } else {
            return Either.right(getLocalFunctionConfFile(pathOrUrlOrArn, deploymentArguments));
        }
    }

    private void throwExceptionIfLambdaArnIsIncomplete(String functionName) {
        if (!functionName.matches(FULL_LAMBDA_ARN_CHECK_REGEX)) {
            throw new RuntimeException(String.join("", "Existing Lambda ARN [", functionName, "] is malformed or incomplete. The Lambda ARN must be the fully qualified ARN with an alias."));
        }
    }

    private String getLambdaFunctionConf(FunctionAliasArn functionAliasArn) {
        Map<String, String> existingEnvironment = v2LambdaHelper.getFunctionEnvironment(functionAliasArn);

        Optional<String> optionalFunctionConfString = Optional.ofNullable(existingEnvironment.get(LambdaHelper.GGP_FUNCTION_CONF));

        if (!optionalFunctionConfString.isPresent()) {
            throw new RuntimeException(String.join("", "This function does not contain the required [", LambdaHelper.GGP_FUNCTION_CONF, "] key. Add this key to the function and try again."));
        }

        return optionalFunctionConfString.get();
    }

    private File getLocalFunctionConfFile(String functionName, DeploymentArguments deploymentArguments) throws IOException {
        // String sourceDirectory = String.join("/", FUNCTIONS, functionName);
        String sourceDirectory = String.join("/", deploymentArguments.functionConfigPath, functionName);
        Path sourcePath = new File(sourceDirectory).toPath();

        log.warn(String.join("", "***sourceDirectory*** [", sourceDirectory, "]"));

        if (gradleBuilder.isGradleFunction(sourcePath)) {
            // Build Gradle based applications in place
            return sourcePath.resolve(FUNCTION_CONF).toFile();
        }

        // Build all non-Gradle based applications in the temp directory
        Path tempPath = Files.createTempDirectory(functionName);
        File tempFile = tempPath.toFile();
        tempFile.deleteOnExit();

        FileUtils.copyDirectory(new File(sourceDirectory), tempFile, getFileFilter(), true);

        return tempPath.resolve(FUNCTION_CONF).toFile();
    }

    private FileFilter getFileFilter() {
        // Omit the venv directory because it contains symlinks that break things in Docker
        return file -> !isPythonVirtualEnvDirectory(file);
    }

    private boolean isPythonVirtualEnvDirectory(File file) {
        // Is it named venv?
        if (!file.getName().equals("venv")) {
            return false;
        }

        // Is it a directory?
        if (!file.isDirectory()) {
            return false;
        }

        // Does it contain the virtual environment configuration?
        if (file.toPath().resolve("pyvenv.cfg").toFile().exists()) {
            // Yes, skip it
            return true;
        }

        // Does it contain a lib directory?
        if (file.toPath().resolve("lib").toFile().exists()) {
            return true;
        }

        // Didn't hit any of the paths we want to avoid, allow it
        return false;
    }

    private File getGitFunctionConfFile(String functionName) throws IOException {
        // This is a git repo, fetch it
        String tempFunctionName = functionName.substring(HTTPS.length());

        String[] components = tempFunctionName.split("/");

        if (components.length < 3) {
            throw new RuntimeException(String.join("", "The git URL specified [", functionName, "] is in a format that was not understood (1)"));
        }

        String last = components[components.length - 1];

        String repoName;
        String directoryName;
        String cloneName;

        if (last.contains(":")) {
            String[] repoAndDirectory = last.split(":");

            if (repoAndDirectory.length != 2) {
                throw new RuntimeException(String.join("", "The git URL specified [", functionName, "] is in a format that was not understood (2)"));
            }

            repoName = repoAndDirectory[0];
            directoryName = repoAndDirectory[1];
            cloneName = functionName.replaceAll(String.join("", ":", NO_COLONS_REGEX, "$"), "");
        } else {
            repoName = last;
            directoryName = ".";
            cloneName = functionName;
        }

        Path tempDir = runGitClone(repoName, directoryName, cloneName);

        File functionConf = tempDir.resolve(directoryName).resolve(FUNCTION_CONF).toFile();

        if (!functionConf.exists()) {
            throw new RuntimeException("This function and repo doesn't contain a function.conf");
        }

        return functionConf;
    }

    private Path runGitClone(String repoName, String directoryName, String cloneName) throws IOException {
        String prefix = String.join("-", repoName, directoryName);

        Path tempDir = Files.createTempDirectory(prefix);
        tempDir.toFile().deleteOnExit();

        List<String> programAndArguments = new ArrayList<>();

        programAndArguments.add("git");
        programAndArguments.add("clone");
        programAndArguments.add(cloneName);
        programAndArguments.add(tempDir.toString());

        ProcessBuilder processBuilder = processHelper.getProcessBuilder(programAndArguments);

        List<String> stdoutStrings = new ArrayList<>();
        List<String> stderrStrings = new ArrayList<>();

        Optional<Integer> exitVal = processHelper.getOutputFromProcess(log, processBuilder, true, Optional.of(stdoutStrings::add), Optional.of(stderrStrings::add));

        if (!exitVal.isPresent()) {
            // Assume success
        } else if (exitVal.get() != 0) {
            // Error
            stderrStrings.stream().forEach(log::error);
            throw new RuntimeException("An error occurred while checking out the git repo");
        }

        return tempDir;
    }

    private Path getFunctionCfTemplatePath(Path function) {
        return function.resolve("function.cf.yaml");
    }

    @Override
    public List<FunctionConf> getFunctionConfObjects(DeploymentArguments deploymentArguments, Config defaultConfig, DeploymentConf deploymentConf, FunctionIsolationMode defaultFunctionIsolationMode) {
        // Get each enabled function conf file OR the ARN of an existing function
        List<Either<FunctionAliasArn, File>> enabledFunctions = getEnabledFunctions(deploymentConf, deploymentArguments);

        warnAboutMissingDefaultsIfNecessary();

        return getFunctionConfs(deploymentArguments, defaultConfig, deploymentConf, enabledFunctions, defaultFunctionIsolationMode);

    }

    private void warnAboutMissingDefaultsIfNecessary() {
        if (!ggConstants.getFunctionDefaultsConf().exists()) {
            log.warn(String.join("", ggConstants.getFunctionDefaultsConf().toString(), " does not exist.  All function configurations MUST contain all required values."));
        }
    }

    private List<FunctionConf> getFunctionConfs(DeploymentArguments deploymentArguments, Config defaultConfig, DeploymentConf deploymentConf, List<Either<FunctionAliasArn, File>> enabledFunctionConfs, FunctionIsolationMode defaultFunctionIsolationMode) {
        // Find any functions with missing config files
        ioHelper.detectMissingConfigs(log, "function", enabledFunctionConfs);

        return getFunctionConfObjectList(deploymentArguments, defaultConfig, deploymentConf, enabledFunctionConfs, defaultFunctionIsolationMode);
    }

    private List<FunctionConf> getFunctionConfObjectList(DeploymentArguments deploymentArguments, Config defaultConfig, DeploymentConf deploymentConf, List<Either<FunctionAliasArn, File>> enabledFunctionConfs, FunctionIsolationMode defaultFunctionIsolationMode) {
        List<FunctionConf> enabledFunctionConfObjects = new ArrayList<>();

        for (Either<FunctionAliasArn, File> enabledFunctionConf : enabledFunctionConfs) {
            FunctionConf functionConf = Try.of(() -> getFunctionConf(deploymentArguments, defaultConfig, deploymentConf, enabledFunctionConf, defaultFunctionIsolationMode)).get();

            enabledFunctionConfObjects.add(functionConf);
        }

        if (enabledFunctionConfObjects.size() > 0) {
            log.info("Enabled functions: ");
            enabledFunctionConfObjects
                    .forEach(functionConf -> log.info(String.join("", "  ", functionConf.getFunctionName().getName())));
        } else {
            log.warn("NO FUNCTIONS ENABLED");
        }

        return enabledFunctionConfObjects;
    }

    private FunctionConf getFunctionConf(DeploymentArguments deploymentArguments, Config defaultConfig, DeploymentConf deploymentConf, Either<FunctionAliasArn, File> functionConf, FunctionIsolationMode defaultFunctionIsolationMode) {
        ImmutableFunctionConf.Builder functionConfBuilder = ImmutableFunctionConf.builder();

        Config config;
        Optional<Path> optionalFunctionPath = Optional.empty();

        // Store the config file info and load it using function.defaults.conf as the fallback for missing values

        if (functionConf.isLeft()) {
            // We have the function conf in Lambda
            FunctionAliasArn functionAliasArn = functionConf.getLeft();
            functionConfBuilder.existingArn(functionAliasArn.getAliasArn());

            String functionConfFromLambda = getLambdaFunctionConf(functionAliasArn);
            functionConfBuilder.rawConfig(functionConfFromLambda);

            config = ConfigFactory.parseString(functionConfFromLambda);
        } else {
            // We have the function conf in a file
            File functionConfFile = functionConf.get();

            functionConfBuilder.rawConfig(ioHelper.readFileAsString(functionConfFile));

            config = ConfigFactory.parseFile(functionConfFile);

            Path functionPath = getFunctionPath(functionConfFile);
            functionConfBuilder.buildDirectory(functionPath);

            optionalFunctionPath = Optional.of(functionPath);
        }

        // Config functionDefaults = ggVariables.getFunctionDefaults();
        Config functionDefaults = ConfigFactory.parseFile(new File(String.join("/", deploymentArguments.deploymentConfigFolderPath, "function.defaults.conf")));

        // Make sure we use the calculated default function isolation mode as the default (forced to no container when using Docker)
        functionDefaults = functionDefaults.withValue(ggConstants.getConfGreengrassContainer(), ConfigValueFactory.fromAnyRef(FunctionIsolationMode.GREENGRASS_CONTAINER.equals(defaultFunctionIsolationMode) ? true : false));

        // Use the function.defaults.conf values
        config = config.withFallback(functionDefaults);

        // Use the default config (environment) values
        config = config.withFallback(defaultConfig);

        // Resolve the entire fallback config
        config = config.resolve();

        Language language = Language.valueOf(config.getString("conf.language"));

        if (language.equals(Language.Python)) {
            log.warn("Legacy Python function forced to Python 2.7");
            language = Language.PYTHON2_7;
        } else if (language.equals(Language.Java)) {
            log.warn("Legacy Java function forced to Java 8");
            language = Language.JAVA8;
        } else if (language.equals(Language.Node)) {
            log.warn("Legacy Node function forced to Node 12.x");
            language = Language.NODEJS12_X;
        }

        functionConfBuilder.language(language);
        functionConfBuilder.encodingType(EncodingType.fromValue(config.getString("conf.encodingType").toLowerCase()));
        functionConfBuilder.functionName(ImmutableFunctionName.builder().name(config.getString("conf.functionName")).build());
        functionConfBuilder.groupName(deploymentConf.getGroupName());
        functionConfBuilder.handlerName(config.getString("conf.handlerName"));
        functionConfBuilder.aliasName(ImmutableFunctionAlias.builder().alias(config.getString("conf.aliasName")).build());
        functionConfBuilder.memorySizeInKb(config.getInt("conf.memorySizeInKb"));
        functionConfBuilder.isPinned(config.getBoolean("conf.pinned"));
        functionConfBuilder.timeoutInSeconds(config.getInt("conf.timeoutInSeconds"));
        functionConfBuilder.fromCloudSubscriptions(config.getStringList(GGConstants.CONF_FROM_CLOUD_SUBSCRIPTIONS));
        functionConfBuilder.toCloudSubscriptions(config.getStringList(GGConstants.CONF_TO_CLOUD_SUBSCRIPTIONS));
        functionConfBuilder.outputTopics(config.getStringList(GGConstants.CONF_OUTPUT_TOPICS));
        functionConfBuilder.inputTopics(config.getStringList(GGConstants.CONF_INPUT_TOPICS));
        functionConfBuilder.isAccessSysFs(config.getBoolean("conf.accessSysFs"));
        functionConfBuilder.isGreengrassContainer(config.getBoolean(ggConstants.getConfGreengrassContainer()));
        functionConfBuilder.uid(typeSafeConfigHelper.getIntegerDefault(Optional.of(config), "conf.uid"));
        functionConfBuilder.gid(typeSafeConfigHelper.getIntegerDefault(Optional.of(config), "conf.gid"));

        setLocalDeviceResourcesConfig(functionConfBuilder, config);
        setLocalVolumeResourcesConfig(functionConfBuilder, config);
        setLocalSageMakerResourcesConfig(functionConfBuilder, config);
        setLocalS3ResourcesConfig(functionConfBuilder, config);
        List<String> secretNames = setLocalSecretsManagerResources(functionConfBuilder, config);

        List<String> connectedShadows = getConnectedShadows(functionConfBuilder, config);
        functionConfBuilder.connectedShadows(connectedShadows);

        // Use the environment variables from the deployment and then add the environment variables from the function
        functionConfBuilder.putAllEnvironmentVariables(deploymentConf.getEnvironmentVariables());
        setEnvironmentVariablesFromConf(functionConfBuilder, config);
        addConnectedShadowsToEnvironment(functionConfBuilder, connectedShadows);
        addSecretNamesToEnvironment(functionConfBuilder, secretNames);

        if (optionalFunctionPath.isPresent()) {
            File cfTemplate = getFunctionCfTemplatePath(optionalFunctionPath.get()).toFile();

            if (cfTemplate.exists()) {
                functionConfBuilder.cfTemplate(cfTemplate);
            }
        }

        // Optional string lists for core role
        functionConfBuilder.coreRoleIamManagedPolicies(typeSafeConfigHelper.getStringListOrReturnEmpty(config, "conf.coreRoleIamManagedPolicies"));

        // Optional JSON policy for core role
        functionConfBuilder.coreRoleIamPolicy(typeSafeConfigHelper.getObjectAndRenderOrReturnEmpty(config, "conf.coreRoleIamPolicy"));

        // Optional string lists for service role
        functionConfBuilder.serviceRoleIamManagedPolicies(typeSafeConfigHelper.getStringListOrReturnEmpty(config, "conf.serviceRoleIamManagedPolicies"));

        // Optional JSON policy for service role
        functionConfBuilder.serviceRoleIamPolicy(typeSafeConfigHelper.getObjectAndRenderOrReturnEmpty(config, "conf.serviceRoleIamPolicy"));

        return functionConfBuilder.build();
    }

    private void addConnectedShadowsToEnvironment(ImmutableFunctionConf.Builder functionConfBuilder, List<String> connectedShadows) {
        IntStream.range(0, connectedShadows.size())
                .forEach(index -> functionConfBuilder.putEnvironmentVariables(String.join("", "CONNECTED_SHADOW_", String.valueOf(index)), connectedShadows.get(index)));
    }

    private void addSecretNamesToEnvironment(ImmutableFunctionConf.Builder functionConfBuilder, List<String> secretNames) {
        IntStream.range(0, secretNames.size())
                .forEach(index -> functionConfBuilder.putEnvironmentVariables(String.join("", "SECRET_", String.valueOf(index)), secretNames.get(index)));
    }

    private List<Either<FunctionAliasArn, File>> getEnabledFunctions(DeploymentConf deploymentConf, DeploymentArguments deploymentArguments) {
        // Get all of the functions they've requested
        return deploymentConf.getFunctions().stream()
                .map(FunctionName::getName)
                .map(pathOrUrlOrArn -> Try.of(() -> getFunctionAliasArnOrFunctionConfPath(pathOrUrlOrArn, deploymentArguments)).get())
                .collect(Collectors.toList());
    }

    private List<String> getConnectedShadows(ImmutableFunctionConf.Builder functionConfBuilder, Config config) {
        List<String> connectedShadows = config.getStringList("conf.connectedShadows");

        if (connectedShadows.size() == 0) {
            log.debug(String.join("", "No connected shadows specified for [", functionConfBuilder.build().getFunctionName().getName(), "] function"));
            return new ArrayList<>();
        }

        return connectedShadows;
    }

    private void setLocalDeviceResourcesConfig(ImmutableFunctionConf.Builder functionConfBuilder, Config config) {
        List<? extends ConfigObject> configObjectList = config.getObjectList("conf.localDeviceResources");

        if (configObjectList.size() == 0) {
            log.debug(String.join("", "No local device resources specified for [", functionConfBuilder.build().getFunctionName().getName(), "] function"));
            return;
        }

        for (ConfigObject configObject : configObjectList) {
            Config temp = configObject.toConfig();

            String path = temp.getString(PATH);

            LocalDeviceResource localDeviceResource = ImmutableLocalDeviceResource.builder()
                    .path(path)
                    .isReadWrite(temp.getBoolean(READ_WRITE))
                    .build();
            functionConfBuilder.addLocalDeviceResources(localDeviceResource);
        }
    }

    private void setLocalVolumeResourcesConfig(ImmutableFunctionConf.Builder functionConfBuilder, Config config) {
        List<? extends ConfigObject> configObjectList = config.getObjectList("conf.localVolumeResources");

        if (configObjectList.size() == 0) {
            log.debug(String.join("", "No local volume resources specified for [", functionConfBuilder.build().getFunctionName().getName(), "] function"));
            return;
        }

        for (ConfigObject configObject : configObjectList) {
            Config temp = configObject.toConfig();
            String sourcePath = temp.getString("sourcePath");
            String destinationPath;

            destinationPath = Try.of(() -> temp.getString("destinationPath"))
                    .recover(ConfigException.Missing.class, throwable -> sourcePath)
                    .get();

            LocalVolumeResource localVolumeResource = ImmutableLocalVolumeResource.builder()
                    .sourcePath(sourcePath)
                    .destinationPath(destinationPath)
                    .isReadWrite(temp.getBoolean(READ_WRITE))
                    .build();
            functionConfBuilder.addLocalVolumeResources(localVolumeResource);
        }
    }

    private void setLocalS3ResourcesConfig(ImmutableFunctionConf.Builder functionConfBuilder, Config config) {
        List<? extends ConfigObject> configObjectList = config.getObjectList("conf.localS3Resources");

        if (configObjectList.size() == 0) {
            log.debug(String.join("", "No local S3 resources specified for [", functionConfBuilder.build().getFunctionName().getName(), "] function"));
            return;
        }

        for (ConfigObject configObject : configObjectList) {
            Config temp = configObject.toConfig();
            String uri = temp.getString(URI);
            String path = temp.getString(PATH);

            LocalS3Resource localS3Resource = ImmutableLocalS3Resource.builder()
                    .uri(uri)
                    .path(path)
                    .build();

            functionConfBuilder.addLocalS3Resources(localS3Resource);
        }
    }

    private void setLocalSageMakerResourcesConfig(ImmutableFunctionConf.Builder functionConfBuilder, Config config) {
        List<? extends ConfigObject> configObjectList = config.getObjectList("conf.localSageMakerResources");

        if (configObjectList.size() == 0) {
            log.debug(String.join("", "No local SageMaker resources specified for [", functionConfBuilder.build().getFunctionName().getName(), "] function"));
            return;
        }

        for (ConfigObject configObject : configObjectList) {
            Config temp = configObject.toConfig();
            String arn = temp.getString(ARN);
            String path = temp.getString(PATH);

            String[] arnComponents = arn.split(":");

            // Validate the ARN. Regex for the ARN is: arn:aws[a-z\-]*:sagemaker:[a-z0-9\-]*:[0-9]{12}:training-job/.*
            // Regex from https://docs.aws.amazon.com/sagemaker/latest/dg/API_CreateTrainingJob.html

            if (arnComponents.length != 6) {
                throw new RuntimeException(String.join("", "SageMaker ARN looks malformed [", arn, "]"));
            }

            if (!arnComponents[2].equals("sagemaker")) {
                throw new RuntimeException(String.join("", "SageMaker ARN does not look like a SageMaker ARN [", arn, "]"));
            }

            arnComponents = arnComponents[5].split("/");

            if (arnComponents.length < 2) {
                throw new RuntimeException(String.join("", "SageMaker ARN looks malformed [", arn, "]"));
            }

            if (!arnComponents[0].equals(TRAINING_JOB)) {
                throw new RuntimeException(String.join("", "SageMaker ARNs must be training job ARNs, not model ARNs or other types of ARNs [", arn, "]"));
            }

            LocalSageMakerResource localSageMakerResource = ImmutableLocalSageMakerResource.builder()
                    .arn(arn)
                    .path(path)
                    .build();

            functionConfBuilder.addLocalSageMakerResources(localSageMakerResource);
        }
    }

    private List<String> setLocalSecretsManagerResources(ImmutableFunctionConf.Builder functionConfBuilder, Config config) {
        List<String> secretNames = new ArrayList<>();

        List<String> idList = config.getStringList("conf.localSecretsManagerResources");

        String functionNameString = functionConfBuilder.build().getFunctionName().getName();

        if (idList.size() == 0) {
            log.debug(String.join("", "No local secrets manager resources specified for [", functionNameString, "] function"));

            return secretNames;
        }

        for (String id : idList) {
            String arn = id;

            if (!id.contains(":")) {
                // No colons specified, check to see if this is reference to the name
                arn = secretsManagerHelper.getSecretArnFromName(id);
            }

            String[] arnComponents = arn.split(":");

            // Validate the ARN. Example ARN: arn:aws:secretsmanager:us-west-2:123456789012:secret:MyTestDatabaseSecret-a1b2c3
            // Example ARN from https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_CreateSecret.html

            if (arnComponents.length != 7) {
                throw new RuntimeException(String.join("", "Secrets manager ARN looks malformed [", arn, "]"));
            }

            if (!arnComponents[2].equals("secretsmanager")) {
                throw new RuntimeException(String.join("", "Secrets manager ARN does not look like a secrets manager ARN [", arn, "], third component is not 'secretsmanager'"));
            }

            if (!arnComponents[5].equals("secret")) {
                throw new RuntimeException(String.join("", "Secrets manager ARN does not look like a secrets manager ARN [", arn, "], second to last component is not 'secret'"));
            }

            String resourceName = arnComponents[6];

            String secretName = secretsManagerHelper.getSecretNameFromArn(arn);

            secretNames.add(secretName);

            LocalSecretsManagerResource localSecretsManagerResource = ImmutableLocalSecretsManagerResource.builder()
                    .arn(arn)
                    .secretName(secretName)
                    .resourceName(resourceName)
                    .build();

            log.info(String.join("", "Adding secret resource [", resourceName, "] with name [", secretName, "] to function [", functionNameString, "]"));

            functionConfBuilder.addLocalSecretsManagerResources(localSecretsManagerResource);
        }

        return secretNames;
    }

    private Optional<String> getName(Config config) {
        // Get the config value or simply return empty if it isn't specified
        return Try.of(() -> Optional.of(config.getString("name")))
                .recover(ConfigException.Missing.class, throwable -> Optional.empty())
                .get();
    }

    private void setEnvironmentVariablesFromConf(ImmutableFunctionConf.Builder functionConfBuilder, Config config) {
        ConfigObject configObject = config.getObject("conf.environmentVariables");

        if (configObject.size() == 0) {
            log.info("- No environment variables specified for this function");
        }

        Config tempConfig = configObject.toConfig();

        for (Map.Entry<String, ConfigValue> configValueEntry : tempConfig.entrySet()) {
            functionConfBuilder.putEnvironmentVariables(configValueEntry.getKey(), String.valueOf(configValueEntry.getValue().unwrapped()));
        }
    }

    @Override
    public void verifyFunctionsAreSupported(List<FunctionConf> functionConfs) {
        // Only check the function confs that aren't already in Lambda
        List<FunctionConf> functionConfsToCheck = getBuildableFunctions(functionConfs);

        List<FunctionConf> javaGradleFunctions = functionConfsToCheck.stream()
                .filter(getJavaPredicate())
                .filter(functionConf -> gradleBuilder.isGradleFunction(functionConf))
                .collect(Collectors.toList());

        List<FunctionConf> python2Functions = functionConfsToCheck.stream()
                .filter(getPython2Predicate())
                .collect(Collectors.toList());

        List<FunctionConf> python3Functions = functionConfsToCheck.stream()
                .filter(getPython3Predicate())
                .collect(Collectors.toList());

        List<FunctionConf> nodeFunctions = functionConfsToCheck.stream()
                .filter(getNodePredicate())
                .collect(Collectors.toList());

        List<FunctionConf> executableFunctions = functionConfsToCheck.stream()
                .filter(getExecutablePredicate())
                .collect(Collectors.toList());

        List<FunctionConf> allBuildableFunctions = new ArrayList<>();
        allBuildableFunctions.addAll(javaGradleFunctions);
        allBuildableFunctions.addAll(python2Functions);
        allBuildableFunctions.addAll(python3Functions);
        allBuildableFunctions.addAll(nodeFunctions);
        allBuildableFunctions.addAll(executableFunctions);

        if (allBuildableFunctions.size() != functionConfsToCheck.size()) {
            // If there is a mismatch here it means that some of the functions are not able to be built
            List<FunctionConf> functionsNotBuilt = functionConfs.stream()
                    .filter(functionConf -> !allBuildableFunctions.contains(functionConf))
                    .collect(Collectors.toList());

            throwRuntimeExceptionForNonBuildableFunctions(functionsNotBuilt);
        }
    }

    private void throwRuntimeExceptionForNonBuildableFunctions(List<FunctionConf> functionsNotBuilt) {
        log.error("The following function(s) are not buildable:");

        functionsNotBuilt
                .forEach(functionConf -> log.error(String.join("", "\t", functionConf.getFunctionName().getName())));

        throw new RuntimeException("This is a bug, cannot continue");
    }

    @Override
    public Predicate<FunctionConf> getPython2Predicate() {
        return functionConf -> functionConf.getLanguage().equals(Language.PYTHON2_7);
    }

    @Override
    public Predicate<FunctionConf> getPython3Predicate() {
        return functionConf -> functionConf.getLanguage().equals(Language.PYTHON3_7);
    }

    @Override
    public Predicate<FunctionConf> getNodePredicate() {
        return functionConf -> functionConf.getLanguage().equals(Language.NODEJS12_X);
    }

    @Override
    public Predicate<FunctionConf> getExecutablePredicate() {
        return functionConf -> functionConf.getLanguage().equals(Language.EXECUTABLE);
    }

    @Override
    public Predicate<FunctionConf> getJavaPredicate() {
        return functionConf -> functionConf.getLanguage().equals(Language.JAVA8);
    }

    @Override
    public Map<Function, FunctionConf> buildFunctionsAndGenerateMap(String s3Bucket, String s3Directory, List<FunctionConf> functionConfList, Role lambdaRole) {
        List<FunctionConfAndFunctionCode> builtFunctions = buildExistingFunctions(functionConfList).stream()
                .map(zipFilePathAndFunctionConf -> convertZipFileToFunctionCode(s3Bucket, s3Directory, zipFilePathAndFunctionConf))
                .collect(Collectors.toList());

        // Create or update the functions as necessary
        List<Either<CreateFunctionResponse, UpdateFunctionConfigurationResponse>> lambdaResponsesForBuiltFunctions = builtFunctions.stream()
                .map(functionConfAndFunctionCode -> lambdaHelper.createOrUpdateFunction(functionConfAndFunctionCode, lambdaRole))
                .collect(Collectors.toList());

        // Get the function ARNs from the built functions
        List<String> functionArnsForBuiltFunctions = lambdaResponsesForBuiltFunctions.stream()
                .map(either -> either.fold(CreateFunctionResponse::functionArn, UpdateFunctionConfigurationResponse::functionArn))
                .collect(Collectors.toList());

        // Create a map of the unqualified function ARN to the function conf for functions we built
        Map<String, FunctionConf> builtFunctionArnMap = getFunctionArnToFunctionConfMap(getBuildableFunctions(functionConfList), functionArnsForBuiltFunctions);

        // Convert the alias ARNs for built functions into variables to be put in the environment of each function
        Map<String, String> environmentVariablesForLocalBuiltLambdas = builtFunctionArnMap.entrySet().stream()
                .map(functionArnAndFunctionConf -> getNameToAliasEntry(functionArnAndFunctionConf.getValue(), functionArnAndFunctionConf.getKey()))
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

        // Convert the alias ARNs for existing functions into variables to be put in the environment of each function
        Map<String, String> environmentVariablesForLocalExistingLambdas = getExistingFunctions(functionConfList).stream()
                .map(this::getNameToAliasEntry)
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

        // Combine the list of all of the local function ARNs
        Map<String, String> environmentVariablesForLocalLambdas = new HashMap<>();
        environmentVariablesForLocalLambdas.putAll(environmentVariablesForLocalBuiltLambdas);
        environmentVariablesForLocalLambdas.putAll(environmentVariablesForLocalExistingLambdas);

        // Put the environment variables into each environment and create new function confs for them all
        List<FunctionConf> functionConfsWithEnvironmentVariables = functionConfList.stream()
                .map(functionConf -> setEnvironmentVariables(environmentVariablesForLocalLambdas, functionConf))
                .collect(Collectors.toList());

        return buildCombinedMapOfGreengrassFunctionModels(functionConfsWithEnvironmentVariables);
    }

    private ImmutableFunctionConfAndFunctionCode convertZipFileToFunctionCode(String s3BucketString, String s3DirectoryString, ZipFilePathAndFunctionConf zipFilePathAndFunctionConf) {
        File zipFile = new File(zipFilePathAndFunctionConf.getZipFilePath().get());
        String functionName = zipFilePathAndFunctionConf.getFunctionConf().getFunctionName().getName();

        FunctionCode functionCode;

        int base64Length = Base64.encode(ioHelper.readFile(zipFile)).length;

        // Base64 encoded length can not be larger than this constant value or the direct upload will fail
        if (base64Length > LAMBDA_FUNCTION_DIRECT_UPLOAD_SIZE_LIMIT_IN_BYTES) {
            // File is too big, need to put it in S3
            if ((s3BucketString == null) || (s3DirectoryString == null)) {
                throw new RuntimeException(String.join("", "Lambda function [", functionName, "] is greater than the direct upload limit for Lambda. It must be uploaded to S3. Re-run GGP with the --s3-bucket and --s3-directory options specified to upload to S3 automatically."));
            }

            S3Bucket s3Bucket = ImmutableS3Bucket.builder().bucket(s3BucketString).build();
            String s3KeyString = String.join("/", s3DirectoryString, zipFile.getName());
            S3Key s3Key = ImmutableS3Key.builder().key(s3KeyString).build();

            log.info(String.join("", "Lambda function [", functionName, "] is being uploaded to S3 - ", s3Bucket.bucket(), ", ", s3Key.key()));
            v2S3Helper.copyToS3(s3Bucket, s3Key, zipFile);
            log.info(String.join("", "Lambda function [", functionName, "] has been uploaded to S3 - ", s3Bucket.bucket(), ", ", s3Key.key()));

            functionCode = FunctionCode.builder()
                    .s3Bucket(s3Bucket.bucket())
                    .s3Key(s3Key.key())
                    .build();
        } else {
            functionCode = FunctionCode.builder()
                    .zipFile(SdkBytes.fromByteBuffer(ByteBuffer.wrap(ioHelper.readFile(zipFile))))
                    .build();
        }

        return ImmutableFunctionConfAndFunctionCode.builder()
                .functionConf(zipFilePathAndFunctionConf.getFunctionConf())
                .functionCode(functionCode)
                .build();
    }

    @NotNull
    private List<FunctionConf> getExistingFunctions(List<FunctionConf> functionConfList) {
        return functionConfList.stream()
                .filter(functionConf -> functionConf.getExistingArn().isPresent())
                .collect(Collectors.toList());
    }

    @NotNull
    private List<FunctionConf> getBuildableFunctions(List<FunctionConf> functionConfList) {
        return functionConfList.stream()
                .filter(functionConf -> !functionConf.getExistingArn().isPresent())
                .collect(Collectors.toList());
    }

    @NotNull
    private List<ZipFilePathAndFunctionConf> buildExistingFunctions(List<FunctionConf> functionConfList) {
        // Get the list of functions to be built
        List<FunctionConf> functionsToBeBuilt = getBuildableFunctions(functionConfList);

        // Build the functions
        List<ZipFilePathAndFunctionConf> builtFunctions = buildFunctions(functionsToBeBuilt);

        // Were there any errors?
        List<ZipFilePathAndFunctionConf> buildProcessErrors = builtFunctions.stream()
                .filter(immutableZipFilePathAndFunctionConf -> immutableZipFilePathAndFunctionConf.getError().isPresent())
                .collect(Collectors.toList());

        if (buildProcessErrors.size() != 0) {
            log.error("Errors detected when building Lambda functions");

            buildProcessErrors.forEach(this::logErrorInLambdaFunction);

            System.exit(1);
        }

        return builtFunctions;
    }

    @NotNull
    private Map<Function, FunctionConf> buildCombinedMapOfGreengrassFunctionModels(List<FunctionConf> functionConfList) {
        // Get the map for functions that are built (conversion step adds the lambda ARN to the function conf)
        Map<Function, FunctionConf> builtFunctionToConfMap = buildGreengrassFunctionModels(convertToCompleteFunctionConf(getBuiltFunctionMap(functionConfList)));

        // Get the map for functions that exist already
        Map<Function, FunctionConf> existingFunctionToConfMap = buildGreengrassFunctionModels(getExistingFunctions(functionConfList));

        // Combine the built and existing functions
        Map<Function, FunctionConf> functionToConfMap = new HashMap<>();
        functionToConfMap.putAll(builtFunctionToConfMap);
        functionToConfMap.putAll(existingFunctionToConfMap);

        return functionToConfMap;
    }

    private List<FunctionConf> convertToCompleteFunctionConf(Map<LambdaFunctionArnInfo, FunctionConf> map) {
        return map.entrySet().stream()
                .map(entry -> ImmutableFunctionConf.builder().from(entry.getValue()).existingArn(entry.getKey().getAliasArn().get()).build())
                .collect(Collectors.toList());
    }

    private Map<LambdaFunctionArnInfo, FunctionConf> getBuiltFunctionMap(List<FunctionConf> functionConfList) {
        // Get the list of functions that were built
        List<FunctionConf> builtFunctions = getBuildableFunctions(functionConfList);

        // Publish all of the built functions to Lambda
        List<LambdaFunctionArnInfo> builtFunctionLambdaInfo = builtFunctions.stream()
                .map(FunctionConf::getGroupFunctionName)
                .map(lambdaHelper::publishLambdaFunctionVersion)
                .collect(Collectors.toList());

        // Associate the Lambda function ARNs with the built function list
        Map<LambdaFunctionArnInfo, FunctionConf> builtLambdaFunctionArnToFunctionConfMap = getLambdaFunctionArnToFunctionConfMap(builtFunctionLambdaInfo, builtFunctions);

        // Create aliases for all of the built functions in Lambda
        List<FunctionAliasArn> aliases = builtLambdaFunctionArnToFunctionConfMap.entrySet().stream()
                .map(entry -> new Tuple3<>(entry.getValue().getGroupFunctionName(),
                        ImmutableFunctionVersion.builder().version(entry.getKey().getQualifier()).build(),
                        entry.getValue().getAliasName()))
                .map(tuple3 -> v2LambdaHelper.createAlias(tuple3._1, tuple3._2, tuple3._3))
                .collect(Collectors.toList());

        builtLambdaFunctionArnToFunctionConfMap = addAliasesToMap(builtLambdaFunctionArnToFunctionConfMap, aliases);

        return builtLambdaFunctionArnToFunctionConfMap;
    }

    private Map<LambdaFunctionArnInfo, FunctionConf> addAliasesToMap(Map<LambdaFunctionArnInfo, FunctionConf> lambdaFunctionArnToFunctionConfMap, List<FunctionAliasArn> aliases) {
        return lambdaFunctionArnToFunctionConfMap.entrySet().stream()
                .map(entry -> addAliasToMap(entry, aliases))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<LambdaFunctionArnInfo, FunctionConf> addAliasToMap(Map.Entry<LambdaFunctionArnInfo, FunctionConf> entry, List<FunctionAliasArn> aliases) {
        List<String> aliasStrings = aliases.stream().map(FunctionAliasArn::getAliasArn).collect(Collectors.toList());
        String alias = findStringThatStartsWith(aliasStrings, entry.getKey().getBaseArn());

        LambdaFunctionArnInfo updatedLambdaFunctionArnInfo = ImmutableLambdaFunctionArnInfo.builder().from(entry.getKey())
                .aliasArn(alias)
                .build();

        return new AbstractMap.SimpleEntry<>(updatedLambdaFunctionArnInfo, entry.getValue());
    }

    @NotNull
    private Map<String, FunctionConf> getFunctionArnToFunctionConfMap(List<FunctionConf> functionConfList, List<String> functionArns) {
        return functionConfList.stream()
                .collect(Collectors.toMap(functionConf -> findStringThatEndsWith(functionArns, functionConf.getGroupFunctionName().getName()), functionConf -> functionConf));
    }

    @NotNull
    private Map<LambdaFunctionArnInfo, FunctionConf> getLambdaFunctionArnToFunctionConfMap(List<LambdaFunctionArnInfo> lambdaFunctionArnInfoList, List<FunctionConf> functionConfList) {
        return functionConfList.stream()
                .collect(Collectors.toMap(functionConf -> findLambdaFunctionArnInfoThatEndsWith(lambdaFunctionArnInfoList, functionConf.getGroupFunctionName().getName()), functionConf -> functionConf));
    }

    private String findStringThatEndsWith(List<String> strings, String endsWithString) {
        // This will throw an exception if the value isn't found
        return strings.stream()
                .filter(string -> string.endsWith(endsWithString))
                .findFirst()
                .get();
    }

    private String findStringThatStartsWith(List<String> strings, String startsWithString) {
        // This will throw an exception if the value isn't found
        return strings.stream()
                .filter(string -> string.startsWith(startsWithString))
                .findFirst()
                .get();
    }

    private LambdaFunctionArnInfo findLambdaFunctionArnInfoThatEndsWith(List<LambdaFunctionArnInfo> lambdaFunctionArnInfoList, String endsWithString) {
        // This will throw an exception if the value isn't found
        return lambdaFunctionArnInfoList.stream()
                .filter(lambdaFunctionArnInfo -> lambdaFunctionArnInfo.getBaseArn().endsWith(endsWithString))
                .findFirst()
                .get();
    }

    private Map<Function, FunctionConf> buildGreengrassFunctionModels(List<FunctionConf> functionConfs) {
        return functionConfs.stream()
                .map(this::buildGreengrassFunctionModel)
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }

    private AbstractMap.SimpleEntry<Function, FunctionConf> buildGreengrassFunctionModel(FunctionConf functionConf) {
        Function function = greengrassHelper.buildFunctionModel(functionConf.getExistingArn().get(), functionConf);

        return new AbstractMap.SimpleEntry<>(function, functionConf);
    }

    private FunctionConf setEnvironmentVariables(Map<String, String> environmentVariablesForLocalLambdas, FunctionConf functionConf) {
        Map<String, String> environmentVariables = new HashMap<>(functionConf.getEnvironmentVariables());
        environmentVariables.putAll(environmentVariablesForLocalLambdas);
        ImmutableFunctionConf.Builder functionConfBuilder = ImmutableFunctionConf.builder().from(functionConf);

        // Remove any duplicate keys. Values specified in the function.conf take precedence over generated values
        functionConf.getEnvironmentVariables().keySet()
                .forEach(environmentVariables::remove);

        functionConfBuilder.putAllEnvironmentVariables(environmentVariables);
        return functionConfBuilder.build();
    }

    private AbstractMap.SimpleEntry<String, String> getNameToAliasEntry(FunctionConf functionConf, String functionArn) {
        String nameInEnvironment = String.join("", LOCAL_LAMBDA, functionConf.getFunctionName().getName());
        String aliasArn = String.join(":", functionArn, functionConf.getAliasName().getAlias());

        return new AbstractMap.SimpleEntry<>(nameInEnvironment, aliasArn);
    }

    private AbstractMap.SimpleEntry<String, String> getNameToAliasEntry(FunctionConf functionConf) {
        String nameInEnvironment = String.join("", LOCAL_LAMBDA, functionConf.getFunctionName().getName());

        return new AbstractMap.SimpleEntry<>(nameInEnvironment, functionConf.getExistingArn().get());
    }

    private void logErrorInLambdaFunction(ZipFilePathAndFunctionConf error) {
        log.error(String.join("", "- Function [", error.getFunctionConf().getFunctionName().getName(), "]"));
        log.error(String.join("", "  Error [", error.getError().get(), "]"));
    }

    private List<ZipFilePathAndFunctionConf> buildFunctions(List<FunctionConf> functionConfList) {
        List<ZipFilePathAndFunctionConf> executableFunctions = functionConfList.stream()
                .filter(getExecutablePredicate())
                .map(lambdaHelper::buildExecutableFunction)
                .collect(Collectors.toList());

        List<ZipFilePathAndFunctionConf> gradleFunctions = functionConfList.stream()
                .filter(getJavaPredicate())
                .filter(gradleBuilder::isGradleFunction)
                .map(lambdaHelper::buildJavaFunction)
                .collect(Collectors.toList());

        List<ZipFilePathAndFunctionConf> python2Functions = functionConfList.stream()
                .filter(getPython2Predicate())
                .map(lambdaHelper::buildPython2Function)
                .collect(Collectors.toList());

        List<ZipFilePathAndFunctionConf> python3Functions = functionConfList.stream()
                .filter(getPython3Predicate())
                .map(lambdaHelper::buildPython3Function)
                .collect(Collectors.toList());

        List<ZipFilePathAndFunctionConf> nodeFunctions = functionConfList.stream()
                .filter(getNodePredicate())
                .map(lambdaHelper::buildNodeFunction)
                .collect(Collectors.toList());

        List<ZipFilePathAndFunctionConf> allFunctions = new ArrayList<>();

        allFunctions.addAll(executableFunctions);
        allFunctions.addAll(gradleFunctions);
        allFunctions.addAll(python2Functions);
        allFunctions.addAll(python3Functions);
        allFunctions.addAll(nodeFunctions);

        return allFunctions;
    }
}
