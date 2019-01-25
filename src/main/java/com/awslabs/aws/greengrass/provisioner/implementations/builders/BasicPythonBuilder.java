package com.awslabs.aws.greengrass.provisioner.implementations.builders;

import com.awslabs.aws.greengrass.provisioner.data.SDK;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.PythonBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.LoggingHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ProcessHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ResourceHelper;
import io.vavr.control.Try;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.zip.ZipUtil;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j

public class BasicPythonBuilder implements PythonBuilder {
    private final String DIST_INFO = ".dist-info";
    private final String BIN = "bin";
    private final String INIT_PY = "__init__.py";
    @Getter
    private final SDK sdk = SDK.PYTHON;
    @Getter
    private final String sdkDestinationPath = ".";
    @Inject
    ProcessHelper processHelper;
    @Inject
    LoggingHelper loggingHelper;
    @Inject
    ResourceHelper resourceHelper;
    @Inject
    IoHelper ioHelper;

    @Inject
    public BasicPythonBuilder() {
    }

    @Override
    public void buildFunctionIfNecessary(FunctionConf functionConf) {
        // Snapshot the directory before installing dependencies
        List<Path> beforeSnapshot = getDirectorySnapshot(functionConf);

        loggingHelper.logInfoWithName(log, functionConf.getFunctionName(), "Copying Greengrass SDK");
        copySdk(log, functionConf, resourceHelper, ioHelper);

        if (functionConf.getDependencies().size() > 0) {
            loggingHelper.logInfoWithName(log, functionConf.getFunctionName(), "Installing Python dependencies");

            installDependencies(functionConf);
        }

        // Snapshot the directory after installing dependencies
        List<Path> afterSnapshot = getDirectorySnapshot(functionConf);

        List<Path> addedFiles = new ArrayList<>(afterSnapshot);
        addedFiles.removeAll(beforeSnapshot);

        loggingHelper.logInfoWithName(log, functionConf.getFunctionName(), "Packaging function for AWS Lambda");

        File tempFile = Try.of(() -> ioHelper.getTempFile("python-lambda-build", "zip")).get();

        // Get the directories, longest named directories first
        List<Path> addedDirectories = addedFiles
                .stream()
                .filter(path -> path.toFile().isDirectory())
                .sorted(Comparator.comparingInt(path -> path.toString().length()).reversed())
                .collect(Collectors.toList());

        // Get the possible Python directories (don't include *dist-info and bin)
        // NOTE: This is an esoteric fix for Zope being broken which breaks Twisted - https://github.com/kpdyer/fteproxy/issues/66
        List<Path> possibleBrokenPythonDirectories = addedDirectories
                .stream()
                .filter(path -> !path.toString().endsWith(DIST_INFO))
                .filter(path -> !path.toString().endsWith(BIN))
                .filter(path -> !path.resolve(INIT_PY).toFile().exists())
                .collect(Collectors.toList());

        // "Touch" the file to fix this issue
        possibleBrokenPythonDirectories.stream()
                .map(path -> path.resolve(INIT_PY).toFile())
                .forEach(this::touchAndIgnoreExceptions);

        ZipUtil.pack(functionConf.getBuildDirectory().toFile(), tempFile);

        moveDeploymentPackage(functionConf, tempFile);
    }

    private void installDependencies(FunctionConf functionConf) {
        for (String dependency : functionConf.getDependencies()) {
            loggingHelper.logInfoWithName(log, functionConf.getFunctionName(), "Retrieving Python dependency [" + dependency + "]");
            List<String> programAndArguments = new ArrayList<>();
            programAndArguments.add("pip");
            programAndArguments.add("install");
            programAndArguments.add("--upgrade");
            programAndArguments.add(dependency);
            programAndArguments.add("-t");
            programAndArguments.add(".");

            ProcessBuilder processBuilder = processHelper.getProcessBuilder(programAndArguments);
            processBuilder.directory(functionConf.getBuildDirectory().toFile());

            List<String> stdoutStrings = new ArrayList<>();
            List<String> stderrStrings = new ArrayList<>();

            Optional<Integer> exitVal = processHelper.getOutputFromProcess(log, processBuilder, true, Optional.of(stdoutStrings::add), Optional.of(stderrStrings::add));

            if (!exitVal.isPresent() || exitVal.get() != 0) {
                log.error("Failed to install Python dependency.  Make sure Python and pip are installed and on your path.");
                System.exit(1);
            }
        }
    }

    private void touchAndIgnoreExceptions(File file) {
        Try.of(() -> touch(file)).get();
    }

    private Void touch(File file) throws IOException {
        new FileOutputStream(file).close();

        return null;
    }

    private List<Path> getDirectorySnapshot(FunctionConf functionConf) {
        return Try.of(() -> Files.list(functionConf.getBuildDirectory()).collect(Collectors.toList())).get();
    }

    @Override
    public Optional<String> verifyHandlerExists(FunctionConf functionConf) {
        String buildDirectory = functionConf.getBuildDirectory().toString();
        String handlerName = functionConf.getHandlerName();

        if (!handlerName.contains(".")) {
            return Optional.of("Python handler name [" + handlerName + "] does not contain a dot separator (e.g. LambdaFunction.function_handler)");
        }

        String[] handlerNameParts = handlerName.split("\\.");

        if (handlerNameParts.length != 2) {
            return Optional.of("Python handler name [" + handlerName + "] contains too many dot separators or is missing the handler function name (e.g. LambdaFunction.function_handler)");
        }

        String handlerFilename = handlerNameParts[0];
        String handlerFunctionName = handlerNameParts[1];

        File handlerFile = new File(String.join("/", buildDirectory, handlerFilename + ".py"));

        if (!handlerFile.exists()) {
            return Optional.of("Python handler file [" + handlerFile.toPath() + "] does not exist");
        }

        return Try.of(() -> innerVerifyHandlerExists(handlerFunctionName, handlerFile))
                .recover(IOException.class, throwable -> Optional.of(throwable.getMessage()))
                .get();
    }

    private Optional<String> innerVerifyHandlerExists(String handlerFunctionName, File handlerFile) throws IOException {
        // Use a regex to find what the function should look like
        String pattern = "^def\\s+" + handlerFunctionName + "\\s*\\(.*";

        if (Files.lines(handlerFile.toPath())
                .noneMatch(line -> line.matches(pattern))) {
            return Optional.of("Python handler function name [" + handlerFunctionName + "] does not appear to exist.  Has the file been saved?  This function will not work.  Stopping build.");
        }

        return Optional.empty();
    }
}
