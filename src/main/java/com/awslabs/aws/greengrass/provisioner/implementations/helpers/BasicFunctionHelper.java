package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.*;
import com.awslabs.aws.greengrass.provisioner.data.conf.DeploymentConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.ModifiableFunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.functions.*;
import com.awslabs.aws.greengrass.provisioner.data.resources.*;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.GradleBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.MavenBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.typesafe.config.*;
import io.vavr.control.Try;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.model.EncodingType;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.FunctionIsolationMode;
import software.amazon.awssdk.services.iam.model.Role;

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
    public List<ModifiableFunctionConf> getFunctionConfObjects(Map<String, String> defaultEnvironment, DeploymentConf deploymentConf, FunctionIsolationMode defaultFunctionIsolationMode) {
        List<File> enabledFunctionConfigFiles = getEnabledFunctionConfigFiles(deploymentConf);

        // Find any functions with missing config files
        detectMissingConfigFiles(deploymentConf, enabledFunctionConfigFiles);

        return buildFunctionConfObjects(defaultEnvironment, deploymentConf, enabledFunctionConfigFiles, defaultFunctionIsolationMode);
    }

    private List<ModifiableFunctionConf> buildFunctionConfObjects(Map<String, String> defaultEnvironment, DeploymentConf deploymentConf, List<File> enabledFunctionConfigFiles, FunctionIsolationMode defaultFunctionIsolationMode) {
        List<String> enabledFunctions = new ArrayList<>();

        List<ModifiableFunctionConf> enabledFunctionConfObjects = new ArrayList<>();

        if (!ggConstants.getFunctionDefaultsConf().exists()) {
            log.warn(ggConstants.getFunctionDefaultsConf().toString() + " does not exist.  All function configurations MUST contain all required values.");
        }

        for (File enabledFunctionConf : enabledFunctionConfigFiles) {
            Path functionPath = getFunctionPath(enabledFunctionConf);

            ModifiableFunctionConf functionConf = Try.of(() -> getFunctionConf(defaultEnvironment, deploymentConf, enabledFunctionConf, functionPath, defaultFunctionIsolationMode)).get();

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

    private ModifiableFunctionConf getFunctionConf(Map<String, String> defaultEnvironment, DeploymentConf deploymentConf, File enabledFunctionConf, Path functionPath, FunctionIsolationMode defaultFunctionIsolationMode) {
        ModifiableFunctionConf functionConf = ModifiableFunctionConf.create();

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

        functionConf.setBuildDirectory(functionPath);

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

        if (!config.getStringList("conf.dependencies").isEmpty()) {
            throw new RuntimeException("Specifying dependencies in function.conf is no longer supported. Use requirements.txt for Python and package.json for NodeJS instead.");
        }

        functionConf.setLanguage(language);
        functionConf.setEncodingType(EncodingType.fromValue(config.getString("conf.encodingType").toLowerCase()));
        functionConf.setFunctionName(config.getString("conf.functionName"));
        functionConf.setGroupName(deploymentConf.getGroupName());
        functionConf.setHandlerName(config.getString("conf.handlerName"));
        functionConf.setAliasName(config.getString("conf.aliasName"));
        functionConf.setMemorySizeInKb(config.getInt("conf.memorySizeInKb"));
        functionConf.setIsPinned(config.getBoolean("conf.pinned"));
        functionConf.setTimeoutInSeconds(config.getInt("conf.timeoutInSeconds"));
        functionConf.setFromCloudSubscriptions(config.getStringList("conf.fromCloudSubscriptions"));
        functionConf.setToCloudSubscriptions(config.getStringList("conf.toCloudSubscriptions"));
        functionConf.setOutputTopics(config.getStringList("conf.outputTopics"));
        functionConf.setInputTopics(config.getStringList("conf.inputTopics"));
        functionConf.setIsAccessSysFs(config.getBoolean("conf.accessSysFs"));
        functionConf.setIsGreengrassContainer(config.getBoolean(ggConstants.getConfGreengrassContainer()));
        functionConf.setUid(config.getInt("conf.uid"));
        functionConf.setGid(config.getInt("conf.gid"));

        setLocalDeviceResourcesConfig(functionConf, config);
        setLocalVolumeResourcesConfig(functionConf, config);
        setLocalSageMakerResourcesConfig(functionConf, config);
        setLocalS3ResourcesConfig(functionConf, config);

        List<String> connectedShadows = getConnectedShadows(functionConf, config);
        functionConf.setConnectedShadows(connectedShadows);

        // Use the environment variables from the deployment and then add the environment variables from the function
        functionConf.putAllEnvironmentVariables(deploymentConf.getEnvironmentVariables());
        setEnvironmentVariables(functionConf, config);
        addConnectedShadowsToEnvironment(functionConf, connectedShadows);

        File cfTemplate = getFunctionCfTemplatePath(functionPath).toFile();

        if (cfTemplate.exists()) {
            functionConf.setCfTemplate(cfTemplate);
        }

        return functionConf;
    }

    private void addConnectedShadowsToEnvironment(ModifiableFunctionConf functionConf, List<String> connectedShadows) {
        int index = 0;

        for (String connectedShadow : connectedShadows) {
            functionConf.putEnvironmentVariables("CONNECTED_SHADOW_" + index, connectedShadow);
            index++;
        }
    }

    private void detectMissingConfigFiles(DeploymentConf deploymentConf, List<File> enabledFunctionConfigFiles) {
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

    private List<String> getConnectedShadows(ModifiableFunctionConf functionConf, Config config) {
        List<String> connectedShadows = config.getStringList("conf.connectedShadows");

        if (connectedShadows.size() == 0) {
            log.debug("No connected shadows specified for [" + functionConf.getFunctionName() + "] function");
            return new ArrayList<>();
        }

        return connectedShadows;
    }

    private void setLocalDeviceResourcesConfig(ModifiableFunctionConf functionConf, Config config) {
        List<? extends ConfigObject> configObjectList = config.getObjectList("conf.localDeviceResources");

        if (configObjectList.size() == 0) {
            log.debug("No local device resources specified for [" + functionConf.getFunctionName() + "] function");
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
            functionConf.addLocalDeviceResources(localDeviceResource);
        }
    }

    private void setLocalVolumeResourcesConfig(ModifiableFunctionConf functionConf, Config config) {
        List<? extends ConfigObject> configObjectList = config.getObjectList("conf.localVolumeResources");

        if (configObjectList.size() == 0) {
            log.debug("No local volume resources specified for [" + functionConf.getFunctionName() + "] function");
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
            functionConf.addLocalVolumeResources(localVolumeResource);
        }
    }

    private void setLocalS3ResourcesConfig(ModifiableFunctionConf functionConf, Config config) {
        List<? extends ConfigObject> configObjectList = config.getObjectList("conf.localS3Resources");

        if (configObjectList.size() == 0) {
            log.debug("No local S3 resources specified for [" + functionConf.getFunctionName() + "] function");
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

            functionConf.addLocalS3Resources(localS3Resource);
        }
    }

    private void setLocalSageMakerResourcesConfig(ModifiableFunctionConf functionConf, Config config) {
        List<? extends ConfigObject> configObjectList = config.getObjectList("conf.localSageMakerResources");

        if (configObjectList.size() == 0) {
            log.debug("No local SageMaker resources specified for [" + functionConf.getFunctionName() + "] function");
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

            functionConf.addLocalSageMakerResources(localSageMakerResource);
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

    private void setEnvironmentVariables(ModifiableFunctionConf functionConf, Config config) {
        ConfigObject configObject = config.getObject("conf.environmentVariables");

        if (configObject.size() == 0) {
            log.info("- No environment variables specified for this function");
        }

        Config tempConfig = configObject.toConfig();

        for (Map.Entry<String, ConfigValue> configValueEntry : tempConfig.entrySet()) {
            functionConf.putEnvironmentVariables(configValueEntry.getKey(), String.valueOf(configValueEntry.getValue().unwrapped()));
        }
    }

    @Override
    public List<BuildableFunction> getBuildableFunctions(List<ModifiableFunctionConf> functionConfs, Role lambdaRole) {
        List<BuildableJavaMavenFunction> javaMavenFunctions = functionConfs.stream()
                .filter(getJavaPredicate())
                .filter(functionConf -> mavenBuilder.isMavenFunction(functionConf))
                .map(functionConf -> new BuildableJavaMavenFunction(functionConf, lambdaRole))
                .collect(Collectors.toList());

        List<BuildableJavaGradleFunction> javaGradleFunctions = functionConfs.stream()
                .filter(getJavaPredicate())
                .filter(functionConf -> gradleBuilder.isGradleFunction(functionConf))
                .map(functionConf -> new BuildableJavaGradleFunction(functionConf, lambdaRole))
                .collect(Collectors.toList());

        List<BuildablePythonFunction> pythonFunctions = functionConfs.stream()
                .filter(getPythonPredicate())
                .map(functionConf -> new BuildablePythonFunction(functionConf, lambdaRole))
                .collect(Collectors.toList());

        List<BuildableNodeFunction> nodeFunctions = functionConfs.stream()
                .filter(getNodePredicate())
                .map(functionConf -> new BuildableNodeFunction(functionConf, lambdaRole))
                .collect(Collectors.toList());

        List<BuildableFunction> allFunctions = new ArrayList<>();
        allFunctions.addAll(javaMavenFunctions);
        allFunctions.addAll(javaGradleFunctions);
        allFunctions.addAll(pythonFunctions);
        allFunctions.addAll(nodeFunctions);

        if (allFunctions.size() != functionConfs.size()) {
            // If there is a mismatch here it means that some of the functions are not able to be built
            List<FunctionConf> builtFunctionConfs = allFunctions.stream()
                    .map(BuildableFunction::getFunctionConf)
                    .collect(Collectors.toList());

            List<FunctionConf> functionsNotBuilt = functionConfs.stream()
                    .filter(functionConf -> !builtFunctionConfs.contains(functionConf))
                    .collect(Collectors.toList());

            throwRuntimeExceptionForMissingFunctions(functionsNotBuilt);
        }

        return allFunctions;
    }

    private void throwRuntimeExceptionForMissingFunctions(List<FunctionConf> functionsNotBuilt) {
        log.error("The following function(s) were not built:");

        functionsNotBuilt
                .forEach(functionConf -> log.error("\t" + functionConf.getFunctionName()));

        throw new RuntimeException("This is a bug, cannot continue");
    }

    @Override
    public Predicate<ModifiableFunctionConf> getPythonPredicate() {
        Predicate<ModifiableFunctionConf> python27Predicate = functionConf -> functionConf.getLanguage().equals(Language.PYTHON2_7);
        Predicate<ModifiableFunctionConf> python37Predicate = functionConf -> functionConf.getLanguage().equals(Language.PYTHON3_7);

        return python27Predicate.or(python37Predicate);
    }

    @Override
    public Predicate<ModifiableFunctionConf> getNodePredicate() {
        Predicate<ModifiableFunctionConf> node610Predicate = functionConf -> functionConf.getLanguage().equals(Language.NODEJS6_10);
        Predicate<ModifiableFunctionConf> node810Predicate = functionConf -> functionConf.getLanguage().equals(Language.NODEJS8_10);

        return node610Predicate.or(node810Predicate);
    }

    @Override
    public Predicate<ModifiableFunctionConf> getJavaPredicate() {
        Predicate<ModifiableFunctionConf> java8Predicate = functionConf -> functionConf.getLanguage().equals(Language.JAVA8);

        return java8Predicate;
    }

    @Override
    public Map<Function, ModifiableFunctionConf> buildFunctionsAndGenerateMap(List<BuildableFunction> buildableFunctions) {
        // Get a list of all the build steps we will call
        List<Callable<LambdaFunctionArnInfoAndFunctionConf>> buildSteps = getCallableBuildSteps(buildableFunctions);

        List<LambdaFunctionArnInfoAndFunctionConf> lambdaFunctionArnInfoAndFunctionConfs = executorHelper.run(log, buildSteps);

        // Were there any errors?
        List<LambdaFunctionArnInfoAndFunctionConf> errors = lambdaFunctionArnInfoAndFunctionConfs.stream()
                .filter(lambdaFunctionArnInfoAndFunctionConf -> lambdaFunctionArnInfoAndFunctionConf.getError().isPresent())
                .collect(Collectors.toList());

        if (errors.size() != 0) {
            log.error("Errors detected in Lambda functions");

            errors.stream()
                    .forEach(this::logErrorInLambdaFunction);

            System.exit(1);
        }

        // Convert the alias ARNs into variables to be put in the environment of each function
        Map<String, String> environmentVariablesForLocalLambdas = lambdaFunctionArnInfoAndFunctionConfs.stream()
                .map(lambdaFunctionArnInfoAndFunctionConf -> getNameToAliasEntry(lambdaFunctionArnInfoAndFunctionConf))
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

        // Put the environment variables into each environment
        lambdaFunctionArnInfoAndFunctionConfs.stream()
                .forEach(lambdaFunctionArnInfoAndFunctionConf -> setEnvironmentVariables(environmentVariablesForLocalLambdas, lambdaFunctionArnInfoAndFunctionConf));

        Map<Function, ModifiableFunctionConf> functionToConfMap = new HashMap<>();

        // Build the function models
        lambdaFunctionArnInfoAndFunctionConfs.stream()
                .forEach(lambdaFunctionArnInfoAndFunctionConf -> putFunctionConfIntoFunctionConfMap(functionToConfMap, lambdaFunctionArnInfoAndFunctionConf));

        return functionToConfMap;
    }

    private void putFunctionConfIntoFunctionConfMap(Map<Function, ModifiableFunctionConf> functionToConfMap, LambdaFunctionArnInfoAndFunctionConf lambdaFunctionArnInfoAndFunctionConf) {
        ModifiableFunctionConf functionConf = lambdaFunctionArnInfoAndFunctionConf.getFunctionConf();
        functionToConfMap.put(greengrassHelper.buildFunctionModel(lambdaFunctionArnInfoAndFunctionConf.getLambdaFunctionArnInfo().getAliasArn().get(), functionConf), functionConf);
    }

    private void setEnvironmentVariables(Map<String, String> environmentVariablesForLocalLambdas, LambdaFunctionArnInfoAndFunctionConf lambdaFunctionArnInfoAndFunctionConf) {
        ModifiableFunctionConf functionConf = lambdaFunctionArnInfoAndFunctionConf.getFunctionConf();
        Map<String, String> environmentVariables = new HashMap<>(functionConf.getEnvironmentVariables());
        environmentVariables.putAll(environmentVariablesForLocalLambdas);
        functionConf.putAllEnvironmentVariables(environmentVariables);
    }

    private AbstractMap.SimpleEntry<String, String> getNameToAliasEntry(LambdaFunctionArnInfoAndFunctionConf lambdaFunctionArnInfoAndFunctionConf) {
        FunctionConf functionConf = lambdaFunctionArnInfoAndFunctionConf.getFunctionConf();
        String nameInEnvironment = LOCAL_LAMBDA + functionConf.getFunctionName();
        String aliasArn = lambdaFunctionArnInfoAndFunctionConf.getLambdaFunctionArnInfo().getQualifiedArn();

        return new AbstractMap.SimpleEntry<>(nameInEnvironment, aliasArn);
    }

    private void logErrorInLambdaFunction(LambdaFunctionArnInfoAndFunctionConf error) {
        log.error("Function [" + error.getFunctionConf().getFunctionName() + "]");
        log.error("  Error [" + error.getError().get() + "]");
    }

    private List<Callable<LambdaFunctionArnInfoAndFunctionConf>> getCallableBuildSteps(List<BuildableFunction> buildableFunctions) {
        List<Callable<LambdaFunctionArnInfoAndFunctionConf>> buildSteps = new ArrayList<>();

        buildSteps.addAll(buildableFunctions.stream()
                .filter(buildableFunction -> buildableFunction instanceof BuildableJavaMavenFunction)
                .map(buildableFunction ->
                        (Callable<LambdaFunctionArnInfoAndFunctionConf>) () -> createFunction((BuildableJavaMavenFunction) buildableFunction))
                .collect(Collectors.toList()));

        buildSteps.addAll(buildableFunctions.stream()
                .filter(buildableFunction -> buildableFunction instanceof BuildableJavaGradleFunction)
                .map(buildableFunction ->
                        (Callable<LambdaFunctionArnInfoAndFunctionConf>) () -> createFunction((BuildableJavaGradleFunction) buildableFunction))
                .collect(Collectors.toList()));

        buildSteps.addAll(buildableFunctions.stream()
                .filter(buildableFunction -> buildableFunction instanceof BuildablePythonFunction)
                .map(buildableFunction ->
                        (Callable<LambdaFunctionArnInfoAndFunctionConf>) () -> createFunction((BuildablePythonFunction) buildableFunction))
                .collect(Collectors.toList()));

        buildSteps.addAll(buildableFunctions.stream()
                .filter(buildableFunction -> buildableFunction instanceof BuildableNodeFunction)
                .map(buildableFunction ->
                        (Callable<LambdaFunctionArnInfoAndFunctionConf>) () -> createFunction((BuildableNodeFunction) buildableFunction))
                .collect(Collectors.toList()));
        return buildSteps;
    }

    @Override
    public void installJavaDependencies() {
        mavenBuilder.installDependencies();
    }

    private LambdaFunctionArnInfoAndFunctionConf createFunction(BuildableJavaMavenFunction buildableJavaMavenFunction) {
        ModifiableFunctionConf functionConf = buildableJavaMavenFunction.getFunctionConf();
        Role lambdaRole = buildableJavaMavenFunction.getLambdaRole();

        log.info("Creating Java function [" + functionConf.getFunctionName() + "]");
        LambdaFunctionArnInfo lambdaFunctionArnInfo = lambdaHelper.buildAndCreateJavaFunctionIfNecessary(functionConf, lambdaRole);
        String javaAliasArn = lambdaHelper.createAlias(functionConf, lambdaFunctionArnInfo.getQualifier());
        lambdaFunctionArnInfo = ImmutableLambdaFunctionArnInfo.builder().from(lambdaFunctionArnInfo).aliasArn(javaAliasArn).build();

        return ImmutableLambdaFunctionArnInfoAndFunctionConf.builder()
                .lambdaFunctionArnInfo(lambdaFunctionArnInfo)
                .functionConf(functionConf)
                .build();
    }

    private LambdaFunctionArnInfoAndFunctionConf createFunction(BuildableJavaGradleFunction buildableJavaGradleFunction) {
        ModifiableFunctionConf functionConf = buildableJavaGradleFunction.getFunctionConf();
        Role lambdaRole = buildableJavaGradleFunction.getLambdaRole();

        log.info("Creating Java function [" + functionConf.getFunctionName() + "]");
        LambdaFunctionArnInfo lambdaFunctionArnInfo = lambdaHelper.buildAndCreateJavaFunctionIfNecessary(functionConf, lambdaRole);
        String javaAliasArn = lambdaHelper.createAlias(functionConf, lambdaFunctionArnInfo.getQualifier());
        lambdaFunctionArnInfo = ImmutableLambdaFunctionArnInfo.builder().from(lambdaFunctionArnInfo).aliasArn(javaAliasArn).build();

        return ImmutableLambdaFunctionArnInfoAndFunctionConf.builder()
                .lambdaFunctionArnInfo(lambdaFunctionArnInfo)
                .functionConf(functionConf)
                .build();
    }

    private LambdaFunctionArnInfoAndFunctionConf createFunction(BuildablePythonFunction buildablePythonFunction) {
        ModifiableFunctionConf functionConf = buildablePythonFunction.getFunctionConf();
        Role lambdaRole = buildablePythonFunction.getLambdaRole();

        log.info("Creating Python function [" + functionConf.getFunctionName() + "]");
        LambdaFunctionArnInfo lambdaFunctionArnInfo = lambdaHelper.buildAndCreatePythonFunctionIfNecessary(functionConf, lambdaRole);

        if (lambdaFunctionArnInfo.getError().isPresent()) {
            return ImmutableLambdaFunctionArnInfoAndFunctionConf.builder()
                    .functionConf(functionConf)
                    .error(lambdaFunctionArnInfo.getError())
                    .build();
        }

        String pythonAliasArn = lambdaHelper.createAlias(functionConf, lambdaFunctionArnInfo.getQualifier());
        lambdaFunctionArnInfo = ImmutableLambdaFunctionArnInfo.builder().from(lambdaFunctionArnInfo).aliasArn(pythonAliasArn).build();

        return ImmutableLambdaFunctionArnInfoAndFunctionConf.builder()
                .lambdaFunctionArnInfo(lambdaFunctionArnInfo)
                .functionConf(functionConf)
                .build();
    }

    private LambdaFunctionArnInfoAndFunctionConf createFunction(BuildableNodeFunction buildableNodeFunction) {
        ModifiableFunctionConf functionConf = buildableNodeFunction.getFunctionConf();
        Role lambdaRole = buildableNodeFunction.getLambdaRole();

        log.info("Creating Node function [" + functionConf.getFunctionName() + "]");
        LambdaFunctionArnInfo lambdaFunctionArnInfo = lambdaHelper.buildAndCreateNodeFunctionIfNecessary(functionConf, lambdaRole);

        if (lambdaFunctionArnInfo.getError().isPresent()) {
            return ImmutableLambdaFunctionArnInfoAndFunctionConf.builder()
                    .functionConf(functionConf)
                    .error(lambdaFunctionArnInfo.getError())
                    .build();
        }

        String nodeAliasArn = lambdaHelper.createAlias(functionConf, lambdaFunctionArnInfo.getQualifier());
        lambdaFunctionArnInfo = ImmutableLambdaFunctionArnInfo.builder().from(lambdaFunctionArnInfo).aliasArn(nodeAliasArn).build();

        return ImmutableLambdaFunctionArnInfoAndFunctionConf.builder()
                .lambdaFunctionArnInfo(lambdaFunctionArnInfo)
                .functionConf(functionConf)
                .build();
    }
}
