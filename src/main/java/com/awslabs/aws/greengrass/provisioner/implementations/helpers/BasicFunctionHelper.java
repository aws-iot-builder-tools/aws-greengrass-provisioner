package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.ImmutableLambdaFunctionArnInfo;
import com.awslabs.aws.greengrass.provisioner.data.LambdaFunctionArnInfo;
import com.awslabs.aws.greengrass.provisioner.data.Language;
import com.awslabs.aws.greengrass.provisioner.data.ZipFilePathAndFunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.DeploymentConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.ImmutableFunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.resources.*;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.GradleBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.typesafe.config.*;
import io.vavr.control.Either;
import io.vavr.control.Try;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.model.EncodingType;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.FunctionIsolationMode;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationResponse;

import javax.inject.Inject;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BasicFunctionHelper implements FunctionHelper {
    public static final String NO_COLONS_REGEX = "[^:]*";
    public static final String ARN_AWS_LAMBDA_FUNCTION_PREFIX_REGEX = "arn:aws:lambda:" + NO_COLONS_REGEX + ":" + NO_COLONS_REGEX + ":function:";
    public static final String FULL_LAMBDA_ARN_CHECK_REGEX = String.join("", ARN_AWS_LAMBDA_FUNCTION_PREFIX_REGEX, NO_COLONS_REGEX, ":", NO_COLONS_REGEX);
    public static final String EXISTING_LAMBDA_FUNCTION_WILDCARD = "~";
    public static final String ENDS_WITH_ALIAS_REGEX = "[^:]*:[^:]*$";
    private final Logger log = LoggerFactory.getLogger(BasicFunctionHelper.class);
    @Inject
    GreengrassHelper greengrassHelper;
    @Inject
    LambdaHelper lambdaHelper;
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
    public BasicFunctionHelper() {
    }

    private Path getFunctionPath(File functionConf) {
        return functionConf.toPath().getParent();
    }

    private Either<String, File> getFunctionConfPathOrArn(String functionName) {
        return Try.of(() -> innerGetFunctionConfPathOrArn(functionName)).get();
    }

    private Either<String, File> innerGetFunctionConfPathOrArn(String functionName) throws IOException {
        if (functionName.contains(EXISTING_LAMBDA_FUNCTION_WILDCARD)) {
            if (functionName.matches(ENDS_WITH_ALIAS_REGEX)) {
                return Either.left(lambdaHelper.findFullFunctionArnByPartialName(functionName));
            } else {
                throw new RuntimeException("Lambda function reference [" + functionName + "] does not include an alias. Append a colon and the alias to the partial name and try again.");
            }
        }

        if (functionName.startsWith(HTTPS)) {
            return Either.right(getGitFunctionConfFile(functionName));
        } else if (functionName.matches(ARN_AWS_LAMBDA_FUNCTION_PREFIX_REGEX + ".*$")) {
            throwExceptionIfLambdaArnIsIncomplete(functionName);

            return Either.left(functionName);
        } else {
            return Either.right(getLocalFunctionConfFile(functionName));
        }
    }

    private void throwExceptionIfLambdaArnIsIncomplete(String functionName) {
        if (!functionName.matches(FULL_LAMBDA_ARN_CHECK_REGEX)) {
            throw new RuntimeException("Existing Lambda ARN [" + functionName + "] is malformed or incomplete. The Lambda ARN must be the fully qualified ARN with an alias.");
        }
    }

    private String getLambdaFunctionConf(String functionName) {
        Map<String, String> existingEnvironment = lambdaHelper.getFunctionEnvironment(functionName);

        Optional<String> optionalFunctionConfString = Optional.ofNullable(existingEnvironment.get(LambdaHelper.GGP_FUNCTION_CONF));

        if (!optionalFunctionConfString.isPresent()) {
            throw new RuntimeException("This function does not contain the required [" + LambdaHelper.GGP_FUNCTION_CONF + "] key. Add this key to the function and try again.");
        }

        return optionalFunctionConfString.get();
    }

    private File getLocalFunctionConfFile(String functionName) throws IOException {
        String sourceDirectory = String.join("/", FUNCTIONS, functionName);
        Path sourcePath = new File(sourceDirectory).toPath();

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
            throw new RuntimeException("The git URL specified [" + functionName + "] is in a format that was not understood (1)");
        }

        String last = components[components.length - 1];

        String repoName;
        String directoryName;
        String cloneName;

        if (last.contains(":")) {
            String[] repoAndDirectory = last.split(":");

            if (repoAndDirectory.length != 2) {
                throw new RuntimeException("The git URL specified [" + functionName + "] is in a format that was not understood (2)");
            }

            repoName = repoAndDirectory[0];
            directoryName = repoAndDirectory[1];
            cloneName = functionName.replaceAll(":" + NO_COLONS_REGEX + "$", "");
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

    private <R> Predicate<R> not(Predicate<R> predicate) {
        return predicate.negate();
    }

    @Override
    public List<FunctionConf> getFunctionConfObjects(Map<String, String> defaultEnvironment, DeploymentConf deploymentConf, FunctionIsolationMode defaultFunctionIsolationMode) {
        // Get each enabled function conf file OR the ARN of an existing function
        List<Either<String, File>> enabledFunctions = getEnabledFunctions(deploymentConf);

        warnAboutMissingDefaultsIfNecessary();

        return getFunctionConfs(defaultEnvironment, deploymentConf, enabledFunctions, defaultFunctionIsolationMode);
    }

    private void warnAboutMissingDefaultsIfNecessary() {
        if (!ggConstants.getFunctionDefaultsConf().exists()) {
            log.warn(ggConstants.getFunctionDefaultsConf().toString() + " does not exist.  All function configurations MUST contain all required values.");
        }
    }

    private List<FunctionConf> getFunctionConfs(Map<String, String> defaultEnvironment, DeploymentConf deploymentConf, List<Either<String, File>> enabledFunctionConfs, FunctionIsolationMode defaultFunctionIsolationMode) {
        // Find any functions with missing config files
        detectMissingConfigFiles(enabledFunctionConfs);

        return getFunctionConfObjects(defaultEnvironment, deploymentConf, enabledFunctionConfs, defaultFunctionIsolationMode);
    }

    private List<FunctionConf> getFunctionConfObjects(Map<String, String> defaultEnvironment, DeploymentConf deploymentConf, List<Either<String, File>> enabledFunctionConfs, FunctionIsolationMode defaultFunctionIsolationMode) {
        List<FunctionConf> enabledFunctionConfObjects = new ArrayList<>();

        for (Either<String, File> enabledFunctionConf : enabledFunctionConfs) {
            FunctionConf functionConf = Try.of(() -> getFunctionConf(defaultEnvironment, deploymentConf, enabledFunctionConf, defaultFunctionIsolationMode)).get();

            enabledFunctionConfObjects.add(functionConf);
        }

        if (enabledFunctionConfObjects.size() > 0) {
            log.info("Enabled functions: ");
            enabledFunctionConfObjects
                    .forEach(functionConf -> log.info("  " + functionConf.getFunctionName()));
        } else {
            log.warn("NO FUNCTIONS ENABLED");
        }

        return enabledFunctionConfObjects;
    }

    private FunctionConf getFunctionConf(Map<String, String> defaultEnvironment, DeploymentConf deploymentConf, Either<String, File> functionConf, FunctionIsolationMode defaultFunctionIsolationMode) {
        ImmutableFunctionConf.Builder functionConfBuilder = ImmutableFunctionConf.builder();

        Config config;
        Optional<Path> optionalFunctionPath = Optional.empty();

        // Store the config file info and load it using function.defaults.conf as the fallback for missing values

        if (functionConf.isLeft()) {
            // We have the function conf in Lambda
            String functionArn = functionConf.getLeft();
            functionConfBuilder.existingArn(functionArn);

            String functionConfFromLambda = getLambdaFunctionConf(functionArn);
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

        Config functionDefaults = ggVariables.getFunctionDefaults();

        // Make sure we use the calculated default function isolation mode as the default (forced to no container when using Docker)
        functionDefaults = functionDefaults.withValue(ggConstants.getConfGreengrassContainer(), ConfigValueFactory.fromAnyRef(FunctionIsolationMode.GREENGRASS_CONTAINER.equals(defaultFunctionIsolationMode) ? true : false));

        config = config.withFallback(functionDefaults);

        // Add the default environment values to the config so they can be used for resolution
        //   (eg. "${AWS_IOT_THING_NAME}" used in the function configuration)
        for (Map.Entry<String, String> entry : defaultEnvironment.entrySet()) {
            config = config.withValue(entry.getKey(), ConfigValueFactory.fromAnyRef(entry.getValue()));
        }

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
        functionConfBuilder.functionName(config.getString("conf.functionName"));
        functionConfBuilder.groupName(deploymentConf.getGroupName());
        functionConfBuilder.handlerName(config.getString("conf.handlerName"));
        functionConfBuilder.aliasName(config.getString("conf.aliasName"));
        functionConfBuilder.memorySizeInKb(config.getInt("conf.memorySizeInKb"));
        functionConfBuilder.isPinned(config.getBoolean("conf.pinned"));
        functionConfBuilder.timeoutInSeconds(config.getInt("conf.timeoutInSeconds"));
        functionConfBuilder.fromCloudSubscriptions(config.getStringList("conf.fromCloudSubscriptions"));
        functionConfBuilder.toCloudSubscriptions(config.getStringList("conf.toCloudSubscriptions"));
        functionConfBuilder.outputTopics(config.getStringList("conf.outputTopics"));
        functionConfBuilder.inputTopics(config.getStringList("conf.inputTopics"));
        functionConfBuilder.isAccessSysFs(config.getBoolean("conf.accessSysFs"));
        functionConfBuilder.isGreengrassContainer(config.getBoolean(ggConstants.getConfGreengrassContainer()));
        functionConfBuilder.uid(config.getInt("conf.uid"));
        functionConfBuilder.gid(config.getInt("conf.gid"));

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

        return functionConfBuilder.build();
    }

    private void addConnectedShadowsToEnvironment(ImmutableFunctionConf.Builder functionConfBuilder, List<String> connectedShadows) {
        IntStream.range(0, connectedShadows.size())
                .forEach(index -> functionConfBuilder.putEnvironmentVariables("CONNECTED_SHADOW_" + index, connectedShadows.get(index)));
    }

    private void addSecretNamesToEnvironment(ImmutableFunctionConf.Builder functionConfBuilder, List<String> secretNames) {
        IntStream.range(0, secretNames.size())
                .forEach(index -> functionConfBuilder.putEnvironmentVariables("SECRET_" + index, secretNames.get(index)));
    }

    private void detectMissingConfigFiles(List<Either<String, File>> enabledFunctionConfigFiles) {
        List<String> missingConfigFunctions = enabledFunctionConfigFiles.stream()
                .filter(Either::isRight)
                .map(Either::get)
                .filter(not(File::exists))
                .map(File::getPath)
                .collect(Collectors.toList());

        if (missingConfigFunctions.size() > 0) {
            log.error("Missing config files (this is NOT OK in normal deployments): ");
            missingConfigFunctions
                    .forEach(functionName -> log.error("  " + functionName));
            throw new RuntimeException("Missing configuration files, can not build deployment");
        }
    }

    private List<Either<String, File>> getEnabledFunctions(DeploymentConf deploymentConf) {
        // Get all of the functions they've requested
        return deploymentConf.getFunctions().stream()
                .map(this::getFunctionConfPathOrArn)
                .collect(Collectors.toList());
    }

    private List<String> getConnectedShadows(ImmutableFunctionConf.Builder functionConfBuilder, Config config) {
        List<String> connectedShadows = config.getStringList("conf.connectedShadows");

        if (connectedShadows.size() == 0) {
            log.debug("No connected shadows specified for [" + functionConfBuilder.build().getFunctionName() + "] function");
            return new ArrayList<>();
        }

        return connectedShadows;
    }

    private void setLocalDeviceResourcesConfig(ImmutableFunctionConf.Builder functionConfBuilder, Config config) {
        List<? extends ConfigObject> configObjectList = config.getObjectList("conf.localDeviceResources");

        if (configObjectList.size() == 0) {
            log.debug("No local device resources specified for [" + functionConfBuilder.build().getFunctionName() + "] function");
            return;
        }

        for (ConfigObject configObject : configObjectList) {
            Config temp = configObject.toConfig();

            String path = temp.getString(PATH);

            Optional<String> optionalName = getName(temp);
            String name = makeNameSafe(path, optionalName);

            LocalDeviceResource localDeviceResource = ImmutableLocalDeviceResource.builder()
                    .name(name)
                    .path(path)
                    .isReadWrite(temp.getBoolean(READ_WRITE))
                    .build();
            functionConfBuilder.addLocalDeviceResources(localDeviceResource);
        }
    }

    private void setLocalVolumeResourcesConfig(ImmutableFunctionConf.Builder functionConfBuilder, Config config) {
        List<? extends ConfigObject> configObjectList = config.getObjectList("conf.localVolumeResources");

        if (configObjectList.size() == 0) {
            log.debug("No local volume resources specified for [" + functionConfBuilder.build().getFunctionName() + "] function");
            return;
        }

        for (ConfigObject configObject : configObjectList) {
            Config temp = configObject.toConfig();
            String sourcePath = temp.getString("sourcePath");
            String destinationPath;

            destinationPath = Try.of(() -> temp.getString("destinationPath"))
                    .recover(ConfigException.Missing.class, throwable -> sourcePath)
                    .get();

            Optional<String> optionalName = getName(temp);
            String name = makeNameSafe(sourcePath, optionalName);

            LocalVolumeResource localVolumeResource = ImmutableLocalVolumeResource.builder()
                    .name(name)
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
            log.debug("No local S3 resources specified for [" + functionConfBuilder.build().getFunctionName() + "] function");
            return;
        }

        for (ConfigObject configObject : configObjectList) {
            Config temp = configObject.toConfig();
            String uri = temp.getString(URI);
            String path = temp.getString(PATH);

            Optional<String> optionalName = getName(temp);
            String name = makeNameSafe(path, optionalName);

            LocalS3Resource localS3Resource = ImmutableLocalS3Resource.builder()
                    .name(name)
                    .uri(uri)
                    .path(path)
                    .build();

            functionConfBuilder.addLocalS3Resources(localS3Resource);
        }
    }

    private void setLocalSageMakerResourcesConfig(ImmutableFunctionConf.Builder functionConfBuilder, Config config) {
        List<? extends ConfigObject> configObjectList = config.getObjectList("conf.localSageMakerResources");

        if (configObjectList.size() == 0) {
            log.debug("No local SageMaker resources specified for [" + functionConfBuilder.build().getFunctionName() + "] function");
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
                throw new RuntimeException("SageMaker ARN looks malformed [" + arn + "]");
            }

            if (!arnComponents[2].equals("sagemaker")) {
                throw new RuntimeException("SageMaker ARN does not look like a SageMaker ARN [" + arn + "]");
            }

            arnComponents = arnComponents[5].split("/");

            if (arnComponents.length < 2) {
                throw new RuntimeException("SageMaker ARN looks malformed [" + arn + "]");
            }

            if (!arnComponents[0].equals(TRAINING_JOB)) {
                throw new RuntimeException("SageMaker ARNs must be training job ARNs, not model ARNs or other types of ARNs [" + arn + "]");
            }

            Optional<String> optionalName = getName(temp);
            String name = makeNameSafe(path, optionalName);

            LocalSageMakerResource localSageMakerResource = ImmutableLocalSageMakerResource.builder()
                    .name(name)
                    .arn(arn)
                    .path(path)
                    .build();

            functionConfBuilder.addLocalSageMakerResources(localSageMakerResource);
        }
    }

    private List<String> setLocalSecretsManagerResources(ImmutableFunctionConf.Builder functionConfBuilder, Config config) {
        List<String> secretNames = new ArrayList<>();

        List<String> idList = config.getStringList("conf.localSecretsManagerResources");

        String functionName = functionConfBuilder.build().getFunctionName();

        if (idList.size() == 0) {
            log.debug("No local secrets manager resources specified for [" + functionName + "] function");

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
                throw new RuntimeException("Secrets manager ARN looks malformed [" + arn + "]");
            }

            if (!arnComponents[2].equals("secretsmanager")) {
                throw new RuntimeException("Secrets manager ARN does not look like a secrets manager ARN [" + arn + "], third component is not 'secretsmanager'");
            }

            if (!arnComponents[5].equals("secret")) {
                throw new RuntimeException("Secrets manager ARN does not look like a secrets manager ARN [" + arn + "], second to last component is not 'secret'");
            }

            String resourceName = arnComponents[6];

            String secretName = secretsManagerHelper.getSecretNameFromArn(arn);

            secretNames.add(secretName);

            LocalSecretsManagerResource localSecretsManagerResource = ImmutableLocalSecretsManagerResource.builder()
                    .arn(arn)
                    .secretName(secretName)
                    .resourceName(resourceName)
                    .build();

            log.info("Adding secret resource [" + resourceName + "] with name [" + secretName + "] to function [" + functionName + "]");

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

    private String makeNameSafe(String path, Optional<String> name) {
        // Device names can't have special characters in them - https://docs.aws.amazon.com/greengrass/latest/apireference/createresourcedefinition-post.html
        return Optional.of(name.orElse(path)
                .replaceAll("[^a-zA-Z0-9:_-]", "-")
                .replaceFirst("^-", "")
                .replaceFirst("-$", "")
                .trim())
                .orElseThrow(() -> new RuntimeException("Name cannot be empty"));
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
                .forEach(functionConf -> log.error("\t" + functionConf.getFunctionName()));

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
    public Map<Function, FunctionConf> buildFunctionsAndGenerateMap(List<FunctionConf> functionConfList, Role lambdaRole) {
        List<ZipFilePathAndFunctionConf> builtFunctions = buildExistingFunctions(functionConfList);

        // Create or update the functions as necessary
        List<Either<CreateFunctionResponse, UpdateFunctionConfigurationResponse>> lambdaResponsesForBuiltFunctions = builtFunctions.stream()
                .map(zipFilePathAndFunctionConf -> lambdaHelper.createOrUpdateFunction(zipFilePathAndFunctionConf.getFunctionConf(), lambdaRole, zipFilePathAndFunctionConf.getZipFilePath().get()))
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
        List<String> aliases = builtLambdaFunctionArnToFunctionConfMap.entrySet().stream()
                .map(entry -> lambdaHelper.createAlias(entry.getValue(), entry.getKey().getQualifier()))
                .collect(Collectors.toList());

        builtLambdaFunctionArnToFunctionConfMap = addAliasesToMap(builtLambdaFunctionArnToFunctionConfMap, aliases);

        return builtLambdaFunctionArnToFunctionConfMap;
    }

    private Map<LambdaFunctionArnInfo, FunctionConf> addAliasesToMap(Map<LambdaFunctionArnInfo, FunctionConf> lambdaFunctionArnToFunctionConfMap, List<String> aliases) {
        return lambdaFunctionArnToFunctionConfMap.entrySet().stream()
                .map(entry -> addAliasToMap(entry, aliases))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<LambdaFunctionArnInfo, FunctionConf> addAliasToMap(Map.Entry<LambdaFunctionArnInfo, FunctionConf> entry, List<String> aliases) {
        String alias = findStringThatStartsWith(aliases, entry.getKey().getBaseArn());

        LambdaFunctionArnInfo updatedLambdaFunctionArnInfo = ImmutableLambdaFunctionArnInfo.builder().from(entry.getKey())
                .aliasArn(alias)
                .build();

        return new AbstractMap.SimpleEntry<>(updatedLambdaFunctionArnInfo, entry.getValue());
    }

    @NotNull
    private Map<String, FunctionConf> getFunctionArnToFunctionConfMap(List<FunctionConf> functionConfList, List<String> functionArns) {
        return functionConfList.stream()
                .collect(Collectors.toMap(functionConf -> findStringThatEndsWith(functionArns, functionConf.getGroupFunctionName()), functionConf -> functionConf));
    }

    @NotNull
    private Map<LambdaFunctionArnInfo, FunctionConf> getLambdaFunctionArnToFunctionConfMap(List<LambdaFunctionArnInfo> lambdaFunctionArnInfoList, List<FunctionConf> functionConfList) {
        return functionConfList.stream()
                .collect(Collectors.toMap(functionConf -> findLambdaFunctionArnInfoThatEndsWith(lambdaFunctionArnInfoList, functionConf.getGroupFunctionName()), functionConf -> functionConf));
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

    private AbstractMap.SimpleEntry<Function, FunctionConf> buildGreengrassFunctionModel(String aliasArn, FunctionConf functionConf) {
        Function function = greengrassHelper.buildFunctionModel(aliasArn, functionConf);

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
        String nameInEnvironment = LOCAL_LAMBDA + functionConf.getFunctionName();
        String aliasArn = String.join(":", functionArn, functionConf.getAliasName());

        return new AbstractMap.SimpleEntry<>(nameInEnvironment, aliasArn);
    }

    private AbstractMap.SimpleEntry<String, String> getNameToAliasEntry(FunctionConf functionConf) {
        String nameInEnvironment = LOCAL_LAMBDA + functionConf.getFunctionName();

        return new AbstractMap.SimpleEntry<>(nameInEnvironment, functionConf.getExistingArn().get());
    }

    private void logErrorInLambdaFunction(ZipFilePathAndFunctionConf error) {
        log.error("- Function [" + error.getFunctionConf().getFunctionName() + "]");
        log.error("  Error [" + error.getError().get() + "]");
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
