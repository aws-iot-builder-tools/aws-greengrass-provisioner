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
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.MavenBuilder;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BasicFunctionHelper implements FunctionHelper {
    private final Logger log = LoggerFactory.getLogger(BasicFunctionHelper.class);
    @Inject
    GreengrassHelper greengrassHelper;
    @Inject
    LambdaHelper lambdaHelper;
    @Inject
    MavenBuilder mavenBuilder;
    @Inject
    GradleBuilder gradleBuilder;
    @Inject
    ProcessHelper processHelper;
    @Inject
    ExecutorHelper executorHelper;
    @Inject
    GGConstants ggConstants;
    @Inject
    GGVariables ggVariables;

    @Inject
    public BasicFunctionHelper() {
    }

    private Path getFunctionPath(File functionConf) {
        return functionConf.toPath().getParent();
    }

    private File getFunctionConfPath(String functionName) {
        return Try.of(() -> innerGetFunctionConfPath(functionName)).get();
    }

    private File innerGetFunctionConfPath(String functionName) throws IOException {
        if (functionName.startsWith(HTTPS)) {
            return getFunctionFromGit(functionName);
        } else {
            return getLocalFunction(functionName);
        }
    }

    private File getLocalFunction(String functionName) throws IOException {
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


        FileUtils.copyDirectory(new File(sourceDirectory), tempFile);

        return tempPath.resolve(FUNCTION_CONF).toFile();
    }

    private File getFunctionFromGit(String functionName) throws IOException {
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
            cloneName = functionName.replaceAll(":[^:]*$", "");
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
        List<File> enabledFunctionConfigFiles = getEnabledFunctionConfigFiles(deploymentConf);

        // Find any functions with missing config files
        detectMissingConfigFiles(enabledFunctionConfigFiles);

        return buildFunctionConfObjects(defaultEnvironment, deploymentConf, enabledFunctionConfigFiles, defaultFunctionIsolationMode);
    }

    private List<FunctionConf> buildFunctionConfObjects(Map<String, String> defaultEnvironment, DeploymentConf deploymentConf, List<File> enabledFunctionConfigFiles, FunctionIsolationMode defaultFunctionIsolationMode) {
        List<String> enabledFunctions = new ArrayList<>();

        List<FunctionConf> enabledFunctionConfObjects = new ArrayList<>();

        if (!ggConstants.getFunctionDefaultsConf().exists()) {
            log.warn(ggConstants.getFunctionDefaultsConf().toString() + " does not exist.  All function configurations MUST contain all required values.");
        }

        for (File enabledFunctionConf : enabledFunctionConfigFiles) {
            Path functionPath = getFunctionPath(enabledFunctionConf);

            FunctionConf functionConf = Try.of(() -> getFunctionConf(defaultEnvironment, deploymentConf, enabledFunctionConf, functionPath, defaultFunctionIsolationMode)).get();

            enabledFunctions.add(functionConf.getFunctionName());
            enabledFunctionConfObjects.add(functionConf);
        }

        if (enabledFunctions.size() > 0) {
            log.info("Enabled functions: ");
            enabledFunctions.stream()
                    .forEach(functionName -> log.info("  " + functionName));
        } else {
            log.warn("NO FUNCTIONS ENABLED");
        }

        return enabledFunctionConfObjects;
    }

    private FunctionConf getFunctionConf(Map<String, String> defaultEnvironment, DeploymentConf deploymentConf, File enabledFunctionConf, Path functionPath, FunctionIsolationMode defaultFunctionIsolationMode) {
        ImmutableFunctionConf.Builder functionConfBuilder = ImmutableFunctionConf.builder();

        // Load function config file and use function.defaults.conf as the fallback for missing values
        Config config = ConfigFactory.parseFile(enabledFunctionConf);
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

        functionConfBuilder.buildDirectory(functionPath);

        Language language = Language.valueOf(config.getString("conf.language"));

        if (language.equals(Language.Python)) {
            log.warn("Legacy Python function forced to Python 2.7");
            language = Language.PYTHON2_7;
        } else if (language.equals(Language.Java)) {
            log.warn("Legacy Java function forced to Java 8");
            language = Language.JAVA8;
        } else if (language.equals(Language.Node)) {
            log.warn("Legacy Node function forced to Node 8.10");
            language = Language.NODEJS8_10;
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

        List<String> connectedShadows = getConnectedShadows(functionConfBuilder, config);
        functionConfBuilder.connectedShadows(connectedShadows);

        // Use the environment variables from the deployment and then add the environment variables from the function
        functionConfBuilder.putAllEnvironmentVariables(deploymentConf.getEnvironmentVariables());
        setEnvironmentVariablesFromConf(functionConfBuilder, config);
        addConnectedShadowsToEnvironment(functionConfBuilder, connectedShadows);

        File cfTemplate = getFunctionCfTemplatePath(functionPath).toFile();

        if (cfTemplate.exists()) {
            functionConfBuilder.cfTemplate(cfTemplate);
        }

        return functionConfBuilder.build();
    }

    private void addConnectedShadowsToEnvironment(ImmutableFunctionConf.Builder functionConfBuilder, List<String> connectedShadows) {
        int index = 0;

        for (String connectedShadow : connectedShadows) {
            functionConfBuilder.putEnvironmentVariables("CONNECTED_SHADOW_" + index, connectedShadow);
            index++;
        }
    }

    private void detectMissingConfigFiles(List<File> enabledFunctionConfigFiles) {
        List<String> missingConfigFunctions = enabledFunctionConfigFiles.stream()
                .filter(not(File::exists))
                .map(File::getPath)
                .collect(Collectors.toList());

        if (missingConfigFunctions.size() > 0) {
            log.error("Missing config files (this is NOT OK in normal deployments): ");
            missingConfigFunctions.stream()
                    .forEach(functionName -> log.error("  " + functionName));
            throw new RuntimeException("Missing configuration files, can not build deployment");
        }
    }

    private List<File> getEnabledFunctionConfigFiles(DeploymentConf deploymentConf) {
        // Get all of the functions they've requested
        List<File> enabledFunctionConfigFiles = deploymentConf.getFunctions().stream().map(functionName -> getFunctionConfPath(functionName)).collect(Collectors.toList());

        return enabledFunctionConfigFiles;
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

            LocalDeviceResource localDeviceResource = com.awslabs.aws.greengrass.provisioner.data.resources.ImmutableLocalDeviceResource.builder()
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

    private Optional<String> getName(Config temp) {
        // Get the config value or simply return empty if it isn't specified
        return Try.of(() -> Optional.of(temp.getString("name")))
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
    public void verifyFunctionsAreBuildable(List<FunctionConf> functionConfs) {
        List<FunctionConf> javaMavenFunctions = functionConfs.stream()
                .filter(getJavaPredicate())
                .filter(functionConf -> mavenBuilder.isMavenFunction(functionConf))
                .collect(Collectors.toList());

        List<FunctionConf> javaGradleFunctions = functionConfs.stream()
                .filter(getJavaPredicate())
                .filter(functionConf -> gradleBuilder.isGradleFunction(functionConf))
                .collect(Collectors.toList());

        List<FunctionConf> python2Functions = functionConfs.stream()
                .filter(getPython2Predicate())
                .collect(Collectors.toList());

        List<FunctionConf> python3Functions = functionConfs.stream()
                .filter(getPython3Predicate())
                .collect(Collectors.toList());

        List<FunctionConf> nodeFunctions = functionConfs.stream()
                .filter(getNodePredicate())
                .collect(Collectors.toList());

        List<FunctionConf> executableFunctions = functionConfs.stream()
                .filter(getExecutablePredicate())
                .collect(Collectors.toList());

        List<FunctionConf> allBuildableFunctions = new ArrayList<>();
        allBuildableFunctions.addAll(javaMavenFunctions);
        allBuildableFunctions.addAll(javaGradleFunctions);
        allBuildableFunctions.addAll(python2Functions);
        allBuildableFunctions.addAll(python3Functions);
        allBuildableFunctions.addAll(nodeFunctions);
        allBuildableFunctions.addAll(executableFunctions);

        if (allBuildableFunctions.size() != functionConfs.size()) {
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
        Predicate<FunctionConf> node610Predicate = functionConf -> functionConf.getLanguage().equals(Language.NODEJS6_10);
        Predicate<FunctionConf> node810Predicate = functionConf -> functionConf.getLanguage().equals(Language.NODEJS8_10);

        return node610Predicate.or(node810Predicate);
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
        // Get a list of all the build steps we will call
        List<Callable<ZipFilePathAndFunctionConf>> buildSteps = getCallableBuildSteps(functionConfList);

        List<ZipFilePathAndFunctionConf> zipFilePathAndFunctionConfs = executorHelper.run(log, buildSteps);

        // Were there any errors?
        List<ZipFilePathAndFunctionConf> errors = zipFilePathAndFunctionConfs.stream()
                .filter(immutableZipFilePathAndFunctionConf -> immutableZipFilePathAndFunctionConf.getError().isPresent())
                .collect(Collectors.toList());

        if (errors.size() != 0) {
            log.error("Errors detected in Lambda functions");

            errors.forEach(this::logErrorInLambdaFunction);

            System.exit(1);
        }

        // Create or update the functions as necessary
        List<Either<CreateFunctionResponse, UpdateFunctionConfigurationResponse>> lambdaResponses = zipFilePathAndFunctionConfs.stream()
                .map(zipFilePathAndFunctionConf -> lambdaHelper.createOrUpdateFunction(zipFilePathAndFunctionConf.getFunctionConf(), lambdaRole, zipFilePathAndFunctionConf.getZipFilePath()))
                .collect(Collectors.toList());

        // Get the function ARNs
        List<String> functionArns = lambdaResponses.stream()
                .map(either -> either.fold(CreateFunctionResponse::functionArn, UpdateFunctionConfigurationResponse::functionArn))
                .collect(Collectors.toList());

        // Create a map of the function ARN to function conf
        Map<String, FunctionConf> functionArnToFunctionConfMap = getFunctionArnToFunctionConfMap(functionConfList, functionArns);

        // Convert the alias ARNs into variables to be put in the environment of each function
        Map<String, String> environmentVariablesForLocalLambdas = functionArnToFunctionConfMap.entrySet().stream()
                .map(functionArnAndFunctionConf -> getNameToAliasEntry(functionArnAndFunctionConf.getValue(), functionArnAndFunctionConf.getKey()))
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

        // Put the environment variables into each environment and create new function confs for them all
        functionConfList = functionConfList.stream()
                .map(functionConf -> setEnvironmentVariables(environmentVariablesForLocalLambdas, functionConf))
                .collect(Collectors.toList());

        // Publish all of the functions to Lambda
        List<LambdaFunctionArnInfo> lambdaFunctionArnInfoList = functionConfList.stream()
                .map(FunctionConf::getGroupFunctionName)
                .map(lambdaHelper::publishLambdaFunctionVersion)
                .collect(Collectors.toList());

        // Create aliases for all of the functions in Lambda
        Map<LambdaFunctionArnInfo, FunctionConf> lambdaFunctionArnToFunctionConfMap = getLambdaFunctionArnToFunctionConfMap(lambdaFunctionArnInfoList, functionConfList);

        List<String> aliases = lambdaFunctionArnToFunctionConfMap.entrySet().stream()
                .map(entry -> lambdaHelper.createAlias(entry.getValue(), entry.getKey().getQualifier()))
                .collect(Collectors.toList());

        lambdaFunctionArnToFunctionConfMap = addAliasesToMap(lambdaFunctionArnToFunctionConfMap, aliases);

        // Build the function models
        Map<Function, FunctionConf> functionToConfMap = lambdaFunctionArnToFunctionConfMap.entrySet().stream()
                .map(entry -> buildGreengrassFunctionModel(entry.getKey(), entry.getValue()))
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));

        return functionToConfMap;
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

    private AbstractMap.SimpleEntry<Function, FunctionConf> buildGreengrassFunctionModel(LambdaFunctionArnInfo lambdaFunctionArnInfo, FunctionConf functionConf) {
        Function function = greengrassHelper.buildFunctionModel(lambdaFunctionArnInfo.getAliasArn().get(), functionConf);

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

    private void logErrorInLambdaFunction(ZipFilePathAndFunctionConf error) {
        log.error("Function [" + error.getFunctionConf().getFunctionName() + "]");
        log.error("  Error [" + error.getError().get() + "]");
    }

    private List<Callable<ZipFilePathAndFunctionConf>> getCallableBuildSteps(List<FunctionConf> functionConfList) {
        List<Callable<ZipFilePathAndFunctionConf>> buildSteps = new ArrayList<>();

        buildSteps.addAll(functionConfList.stream()
                .filter(getExecutablePredicate())
                .map(functionConf ->
                        (Callable<ZipFilePathAndFunctionConf>) () -> lambdaHelper.buildExecutableFunction(functionConf))
                .collect(Collectors.toList()));

        buildSteps.addAll(functionConfList.stream()
                .filter(getJavaPredicate())
                .filter(mavenBuilder::isMavenFunction)
                .map(functionConf ->
                        (Callable<ZipFilePathAndFunctionConf>) () -> lambdaHelper.buildJavaFunction(functionConf))
                .collect(Collectors.toList()));

        buildSteps.addAll(functionConfList.stream()
                .filter(getJavaPredicate())
                .filter(gradleBuilder::isGradleFunction)
                .map(functionConf ->
                        (Callable<ZipFilePathAndFunctionConf>) () -> lambdaHelper.buildJavaFunction(functionConf))
                .collect(Collectors.toList()));

        buildSteps.addAll(functionConfList.stream()
                .filter(getPython2Predicate())
                .map(functionConf ->
                        (Callable<ZipFilePathAndFunctionConf>) () -> lambdaHelper.buildPython2Function(functionConf))
                .collect(Collectors.toList()));

        buildSteps.addAll(functionConfList.stream()
                .filter(getPython3Predicate())
                .map(functionConf ->
                        (Callable<ZipFilePathAndFunctionConf>) () -> lambdaHelper.buildPython3Function(functionConf))
                .collect(Collectors.toList()));

        buildSteps.addAll(functionConfList.stream()
                .filter(getNodePredicate())
                .map(functionConf ->
                        (Callable<ZipFilePathAndFunctionConf>) () -> lambdaHelper.buildNodeFunction(functionConf))
                .collect(Collectors.toList()));

        return buildSteps;
    }
}
