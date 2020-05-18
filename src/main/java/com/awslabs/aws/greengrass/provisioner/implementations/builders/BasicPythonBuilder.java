package com.awslabs.aws.greengrass.provisioner.implementations.builders;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.PythonBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.LoggingHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ProcessHelper;
import io.vavr.control.Try;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.zip.ZipUtil;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class BasicPythonBuilder implements PythonBuilder {
    private static final String PACKAGE_DIRECTORY = "package";
    private static final String REQUIREMENTS_TXT = "requirements.txt";
    private final Logger log = LoggerFactory.getLogger(BasicPythonBuilder.class);
    @Inject
    ProcessHelper processHelper;
    @Inject
    LoggingHelper loggingHelper;

    private List<Path> getDirectorySnapshot(Path directory) {
        return Try.of(() -> Files.list(directory).collect(Collectors.toList())).get();
    }

    private void cleanUpPackageDirectory(File absolutePackageDirectory) {
        // Delete any existing package directory
        Try.run(() -> FileUtils.deleteDirectory(absolutePackageDirectory)).get();
    }

    private void copyToDirectory(Path path, File destination) {
        File file = path.toFile();

        if (file.isDirectory()) {
            File dirDestination = new File(String.join("/", destination.getPath(), file.getName()));
            Try.run(() -> FileUtils.copyDirectory(file, dirDestination)).get();
        } else {
            Try.run(() -> FileUtils.copyToDirectory(file, destination)).get();
        }
    }

    @Override
    public void buildFunctionIfNecessary(FunctionConf functionConf) {
        File baseDirectory = functionConf.getBuildDirectory().get().toFile();

        // Determine the absolute path of the package directory
        File absolutePackageDirectory = new File(String.join("/", baseDirectory.getAbsolutePath(), PACKAGE_DIRECTORY));

        // Determine what the output ZIP file name will be
        String zipFileName = String.join(".", functionConf.getFunctionName().getName(), "zip");
        Path zipFilePath = baseDirectory.toPath().resolve(zipFileName);

        // Delete any existing package directory
        cleanUpPackageDirectory(absolutePackageDirectory);

        // Delete any existing ZIP file
        Try.of(() -> Files.deleteIfExists(zipFilePath.toAbsolutePath())).get();

        // Get a snapshot of all of the files we need to copy to the
        List<Path> filesToCopyToPackageDirectory = getDirectorySnapshot(baseDirectory.toPath());

        if (hasDependencies(baseDirectory.toPath())) {
            loggingHelper.logInfoWithName(log, functionConf.getFunctionName().getName(), "Retrieving Python dependencies");

            // Install the requirements in a package directory
            List<String> programAndArguments = new ArrayList<>();
            programAndArguments.add(getPip());
            programAndArguments.add("install");
            programAndArguments.add("-r");
            programAndArguments.add(REQUIREMENTS_TXT);
            programAndArguments.add("-t");
            programAndArguments.add(absolutePackageDirectory.getPath());

            ProcessBuilder processBuilder = processHelper.getProcessBuilder(programAndArguments);
            processBuilder.directory(baseDirectory);

            List<String> stdoutStrings = new ArrayList<>();
            List<String> stderrStrings = new ArrayList<>();

            Optional<Integer> exitVal = processHelper.getOutputFromProcess(log, processBuilder, true, Optional.of(stdoutStrings::add), Optional.of(stderrStrings::add));

            checkPipStatus(exitVal, stdoutStrings, stderrStrings);
        } else {
            loggingHelper.logInfoWithName(log, functionConf.getFunctionName().getName(), "No Python dependencies to install");
        }

        // Now the dependencies are in the directory, copy the rest of the necessary files in
        filesToCopyToPackageDirectory.forEach(file -> copyToDirectory(file, absolutePackageDirectory));

        // Package up everything into a deployment package ZIP file
        ZipUtil.pack(absolutePackageDirectory, zipFilePath.toFile());

        // Delete the package directory
        cleanUpPackageDirectory(absolutePackageDirectory);
    }

    @Override
    public boolean hasDependencies(Path buildDirectory) {
        return buildDirectory.resolve(REQUIREMENTS_TXT).toFile().exists();
    }

    private void checkPipStatus(Optional<Integer> exitVal, List<String> stdoutStrings, List<String> stderrStrings) {
        if (!exitVal.isPresent() || exitVal.get() != 0) {
            log.error("Something went wrong with pip");

            if (stderrStrings.stream().anyMatch(string -> string.contains("'clang' failed"))) {
                stdoutStrings.forEach(log::warn);
                stderrStrings.forEach(log::error);

                log.error("Building this function failed because a dependency failed to compile. This can happen when a dependency needs to build a native library. Error messages are above.");

                System.exit(1);
            }

            if (stderrStrings.stream().anyMatch(string -> string.contains("Could not find a version that satisfies the requirement")) ||
                    stderrStrings.stream().anyMatch(string -> string.contains("No matching distribution found"))) {
                stdoutStrings.forEach(log::warn);
                stderrStrings.forEach(log::error);

                log.error("Building this function failed because a dependency was not available. Error messages are above.");

                System.exit(1);
            }

            if (isCorrectPipVersion()) {
                log.error("pip version is correct but the Python dependency failed to install");
            } else {
                log.error("pip version appears to be incorrect or pip is missing");
            }

            log.error("To resolve:");
            log.error("1) Make sure Python and pip are installed and on your path");
            log.error("2) Make sure pip version is 19.x (pip --version) and install it with get-pip.py if necessary (https://pip.pypa.io/en/stable/installing/)");
            log.error("3) Try installing the dependency with pip and see if pip returns any installation errors");

            System.exit(1);
        }
    }

    protected abstract String getPip();

    private boolean isCorrectPipVersion() {
        List<String> programAndArguments = new ArrayList<>();
        programAndArguments.add(getPip());
        programAndArguments.add("--version");

        ProcessBuilder processBuilder = processHelper.getProcessBuilder(programAndArguments);

        String stdoutStrings = "";

        processHelper.getOutputFromProcess(log, processBuilder, true, Optional.of(stdoutStrings::concat), Optional.empty());

        // We expect pip 19.x only!
        return stdoutStrings.startsWith("pip 19.");
    }

    private void touchAndIgnoreExceptions(File file) {
        Try.run(() -> new FileOutputStream(file).close()).get();
    }

    @Override
    public Optional<String> verifyHandlerExists(FunctionConf functionConf) {
        String buildDirectory = functionConf.getBuildDirectory().get().toString();
        String handlerName = functionConf.getHandlerName();

        if (!handlerName.contains(".")) {
            return Optional.of(String.join("", "Python handler name [", handlerName, "] does not contain a dot separator (e.g. LambdaFunction.function_handler)"));
        }

        String[] handlerNameParts = handlerName.split("\\.");

        if (handlerNameParts.length != 2) {
            return Optional.of(String.join("", "Python handler name [", handlerName, "] contains too many dot separators or is missing the handler function name (e.g. LambdaFunction.function_handler)"));
        }

        String handlerFilename = handlerNameParts[0];
        String handlerFunctionName = handlerNameParts[1];

        File handlerFile = new File(String.join("/", buildDirectory, String.join("", handlerFilename, ".py")));

        if (!handlerFile.exists()) {
            return Optional.of(String.join("", "Python handler file [", handlerFile.toPath().toString(), "] does not exist"));
        }

        return Try.of(() -> innerVerifyHandlerExists(handlerFunctionName, handlerFile))
                .recover(IOException.class, throwable -> Optional.of(throwable.getMessage()))
                .get();
    }

    private Optional<String> innerVerifyHandlerExists(String handlerFunctionName, File handlerFile) throws IOException {
        // Use a regex to find what the function should look like
        String pattern = String.join("", "^def\\s+", handlerFunctionName, "\\s*\\(.*");

        if (Files.lines(handlerFile.toPath())
                .noneMatch(line -> line.matches(pattern))) {
            return Optional.of(String.join("", "Python handler function name [", handlerFunctionName, "] does not appear to exist.  Has the file been saved?  This function will not work.  Stopping build."));
        }

        return Optional.empty();
    }
}
