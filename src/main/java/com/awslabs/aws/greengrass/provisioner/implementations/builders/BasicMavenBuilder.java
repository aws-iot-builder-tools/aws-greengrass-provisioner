package com.awslabs.aws.greengrass.provisioner.implementations.builders;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.MavenBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ExecutorHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.LoggingHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ProcessHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ResourceHelper;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.shared.invoker.*;

import javax.inject.Inject;
import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;

@Slf4j
public class BasicMavenBuilder implements MavenBuilder {
    public static final String CLEAN = "clean";
    public static final String COMPILE = "compile";
    public static final String PACKAGE = "package";
    public static final String INSTALL_INSTALL_FILE = "install:install-file";

    public static final List<String> CLEAN_COMPILE_PACKAGE = Arrays.asList(CLEAN, COMPILE, PACKAGE);
    public static final List<String> INSTALL = Arrays.asList(INSTALL_INSTALL_FILE);

    public static final List<String> BUILD_OPTIONS = CLEAN_COMPILE_PACKAGE;

    public static final String MVN_EXECUTABLE = "mvn";
    public static final String FILE = "file";
    public static final String CDD_BASELINE_VERSION_0_3 = "0.3";
    public static final String CDD_BASELINE_DEPENDENCIES_VERSION_0_3 = "0.3";
    public static final String GROUP_ID = "groupId";
    public static final String ARTIFACT_ID = "artifactId";
    public static final String COM_AWSLABS = "com.awslabs";
    public static final String COM_AMAZONAWS = "com.amazonaws";
    public static final String CDDBASELINEJAVA = "cddbaselinejava";
    public static final String VERSION = "version";
    public static final String PACKAGING = "packaging";
    public static final String JAR = "jar";
    public static final String VERSION_1_2 = "1.2";
    public static final String POM = "pom";
    public static final String CDDBASELINEJAVADEPENDENCIES = "cddbaselinejavadependencies";
    public static final String GREENGRASS_LAMBDA = "greengrass-lambda";
    public static final String FOUNDATION_GREENGRASS_JAVA_SDK_1_2_JAR = "/foundation/GreengrassJavaSDK-1.2.jar.zip";
    public static final String FOUNDATION_CDDBASELINE_JAVA_JAR = "/foundation/CDDBaseline-0.3.jar.zip";
    public static final String FOUNDATION_CDDBASELINE_DEPENDENCIES_JAVA_POM = "/foundation/CDDBaselineJavaDependencies/pom.xml";
    public static final String MAVEN_HOME_PREFIX = "Maven home: ";
    public static final String M_2_HOME_KEY = "M2_HOME";
    public static final String NO_COMPILER_ERROR = "No compiler is provided in this environment.";
    public static final String VERSION_OPTION = "--version";

    @Inject
    ProcessHelper processHelper;
    @Inject
    LoggingHelper loggingHelper;
    @Inject
    ResourceHelper resourceHelper;
    @Inject
    ExecutorHelper executorHelper;

    @Inject
    public BasicMavenBuilder() {
    }

    @Override
    public String getArchivePath(FunctionConf functionConf) {
        return functionConf.getBuildDirectory().toString() + "/target/" + functionConf.getFunctionName() + ".jar";
    }

    @Override
    public String getPomXmlPath(FunctionConf functionConf) {
        return functionConf.getBuildDirectory().toString() + "/pom.xml";
    }

    @Override
    public boolean isMavenFunction(FunctionConf functionConf) {
        if (new File(getPomXmlPath(functionConf)).exists()) {
            return true;
        }

        return false;
    }

    @Override
    public void buildJavaFunctionIfNecessary(FunctionConf functionConf) {
        runMaven(functionConf, BUILD_OPTIONS);
    }

    @Override
    public void installDependencies() {
        // Build the functions in parallel (invokeAll) and get all of the alias ARNs and function configurations
        List<Callable<Void>> buildSteps = new ArrayList<>();
        buildSteps.add(this::installGreengrassJavaSdkJar);
        buildSteps.add(this::installCddBaselineDependencies);

        executorHelper.run(log, buildSteps);

        installCddBaseline();
    }

    private void buildPropertiesAndRunMaven(String file, String groupId, String artifactId, String version, String packaging, Optional<File> pomXml, List<String> goals) {
        Map<String, String> baselineJavaProperties = new HashMap<>();
        baselineJavaProperties.put(FILE, file);
        baselineJavaProperties.put(GROUP_ID, groupId);
        baselineJavaProperties.put(ARTIFACT_ID, artifactId);
        baselineJavaProperties.put(VERSION, version);
        baselineJavaProperties.put(PACKAGING, packaging);
        runMaven(pomXml, Optional.empty(), goals, Optional.ofNullable(baselineJavaProperties));
    }

    private Void installCddBaseline() {
        String tempFile = Try.of(() -> resourceHelper.resourceToTempFile(FOUNDATION_CDDBASELINE_JAVA_JAR)).get();
        buildPropertiesAndRunMaven(tempFile, COM_AWSLABS, CDDBASELINEJAVA, CDD_BASELINE_VERSION_0_3, JAR, Optional.empty(), INSTALL);

        return null;
    }

    private Void installGreengrassJavaSdkJar() {
        String tempFile = Try.of(() -> resourceHelper.resourceToTempFile(FOUNDATION_GREENGRASS_JAVA_SDK_1_2_JAR)).get();
        buildPropertiesAndRunMaven(tempFile, COM_AMAZONAWS, GREENGRASS_LAMBDA, VERSION_1_2, JAR, Optional.empty(), INSTALL);

        return null;
    }

    private Void installCddBaselineDependencies() {
        String tempFile = Try.of(() -> resourceHelper.resourceToTempFile(FOUNDATION_CDDBASELINE_DEPENDENCIES_JAVA_POM)).get();
        buildPropertiesAndRunMaven(tempFile, COM_AWSLABS, CDDBASELINEJAVADEPENDENCIES, CDD_BASELINE_DEPENDENCIES_VERSION_0_3, POM, Optional.empty(), INSTALL);

        return null;
    }

    private void runMaven(FunctionConf functionConf, List<String> goals) {
        runMaven(Optional.of(new File(getPomXmlPath(functionConf))), Optional.ofNullable(functionConf.getFunctionName()), goals, Optional.empty());
    }

    @Override
    public void runMaven(Optional<File> pomXmlPath, Optional<String> functionName, List<String> goals, Optional<Map<String, String>> properties) {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setBatchMode(true);

        pomXmlPath.ifPresent(request::setPomFile);

        request.setGoals(goals);

        if (properties.isPresent()) {
            Properties mavenProperties = new Properties();

            properties.get().entrySet().stream()
                    .forEach(entry -> mavenProperties.setProperty(entry.getKey(), entry.getValue()));

            request.setProperties(mavenProperties);
        }

        Invoker invoker = new DefaultInvoker();

        List<String> programAndArguments = new ArrayList<>();

        programAndArguments.add(MVN_EXECUTABLE);
        programAndArguments.add(VERSION_OPTION);

        ProcessBuilder processBuilder = processHelper.getProcessBuilder(programAndArguments);

        List<String> stdoutStrings = new ArrayList<>();
        List<String> stderrStrings = new ArrayList<>();

        Optional<Integer> exitVal = processHelper.getOutputFromProcess(log, processBuilder, true, Optional.of(stdoutStrings::add), Optional.of(stderrStrings::add));

        if (!exitVal.isPresent()) {
            log.error("Fatal error trying to launch Maven.  This may be a bug in the provisioner.");
            System.exit(1);
        }

        if (exitVal.get() != 0) {
            log.error("Couldn't find Maven in path.  Install Maven and try again.");
            System.exit(1);
        }

        Optional<String> m2Home = getM2HomeFromStdout(stdoutStrings);

        if (!m2Home.isPresent() && System.getenv().containsKey(M_2_HOME_KEY)) {
            log.warn("Couldn't determine Maven home.  Attempting to obtain M2_HOME from the environment...");
            m2Home = Optional.ofNullable(System.getenv(M_2_HOME_KEY));
        }

        if (!m2Home.isPresent()) {
            log.error("M2_HOME not specified.  Set M2_HOME and try again.");
            System.exit(1);
        }

        invoker.setMavenHome(new File(m2Home.get()));

        final List<String> outputList = new ArrayList<>();
        final List<String> errorList = new ArrayList<>();

        InvocationOutputHandler invocationOutputHandler = outputList::add;
        InvocationOutputHandler invocationErrorHandler = errorList::add;

        invoker.setOutputHandler(invocationOutputHandler);
        invoker.setErrorHandler(invocationErrorHandler);

        Try.of(() -> invokeMaven(pomXmlPath, functionName, properties, request, invoker, outputList, errorList))
                .get();
    }

    public Void invokeMaven(Optional<File> pomXmlPath, Optional<String> functionName, Optional<Map<String, String>> properties, InvocationRequest request, Invoker invoker, List<String> outputList, List<String> errorList) throws MavenInvocationException {
        if (functionName.isPresent()) {
            loggingHelper.logInfoWithName(log, functionName.get(), "Attempting Maven build of Java function");
        } else if (pomXmlPath.isPresent()) {
            loggingHelper.logInfoWithName(log, cleanPath(pomXmlPath.get().getAbsolutePath()), "Attempting Maven build");
        } else {
            String name = getInternalName(properties);

            loggingHelper.logInfoWithName(log, "Internal [" + name + "]", "Attempting Maven build");
        }

        InvocationResult result = invoker.execute(request);

        if (result.getExitCode() != 0) {
            Optional<String> noCompilerString = outputList.stream()
                    .filter(s -> s.contains(NO_COMPILER_ERROR))
                    .findFirst();

            if (noCompilerString.isPresent()) {
                log.error("No compiler found.  You may need to install the JDK.");
                System.exit(1);
            }

            printDebugInfo(outputList, errorList);
            throw new RuntimeException("Maven build failed");
        }

        if (functionName.isPresent()) {
            loggingHelper.logInfoWithName(log, functionName.get(), "Finished build of Java function");
        } else if (pomXmlPath.isPresent()) {
            loggingHelper.logInfoWithName(log, cleanPath(pomXmlPath.get().getAbsolutePath()), "Finished Maven build");
        } else {
            String name = getInternalName(properties);

            loggingHelper.logInfoWithName(log, "Internal [" + name + "]", "Finished Maven build");
        }

        return null;
    }

    public String getInternalName(Optional<Map<String, String>> properties) {
        Optional<String> optionalName = Optional.empty();

        if (properties.isPresent()) {
            Map<String, String> map = properties.get();
            optionalName = Optional.ofNullable(map.get("artifactId"));
        }

        return optionalName.orElse("Unknown");
    }

    private String cleanPath(String path) {
        String[] components = path.split("/");

        int length = components.length;

        // If the path isn't long enough for us to trim then just return the path they gave us
        if (length < 2) {
            return path;
        }

        // Does the path end in "pom.xml"?  If so, return just the directory name it is in which should be the function.
        if (components[components.length - 1].equals("pom.xml")) {
            return components[length - 2];
        }

        // If the path doesn't end in "pom.xml" return the last two components of the path.  This is unexpected.
        return String.join("/", components[length - 2], components[length - 1]);
    }

    private void printDebugInfo(List<String> outputList, List<String> errorList) {
        log.warn("Output: ");
        log.warn(String.join("\n", outputList));

        log.error("Error: ");
        log.error(String.join("\n", errorList));

        String error = "- Maven build failed";
        log.error(error);
    }

    private Optional<String> getM2HomeFromStdout(List<String> stdoutStrings) {
        Optional<String> mavenHomeLine = stdoutStrings.stream()
                .filter(s -> s.startsWith(MAVEN_HOME_PREFIX))
                .findFirst();

        if (!mavenHomeLine.isPresent()) {
            return Optional.empty();
        }

        String returnValue = mavenHomeLine.get();

        returnValue = returnValue.substring(MAVEN_HOME_PREFIX.length());

        return Optional.ofNullable(returnValue);
    }

    @Override
    public Optional<String> verifyHandlerExists(FunctionConf functionConf) {
        throw new RuntimeException("Not implemented yet");
    }
}
