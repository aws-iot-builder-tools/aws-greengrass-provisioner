package com.awslabs.aws.greengrass.provisioner.implementations.builders;

import com.awslabs.aws.greengrass.provisioner.data.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.SDK;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.LoggingHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ProcessHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.PythonBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.zip.ZipUtil;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    public BasicPythonBuilder() {
    }

    @Override
    public void buildFunctionIfNecessary(FunctionConf functionConf) {
        // Snapshot the directory before installing dependencies
        List<Path> beforeSnapshot = getDirectorySnapshot(functionConf);

        loggingHelper.logInfoWithName(log, functionConf.getFunctionName(), "Copying Greengrass SDK");
        copySdk(log, functionConf);

        if (functionConf.getDependencies().size() > 0) {
            loggingHelper.logInfoWithName(log, functionConf.getFunctionName(), "Installing Python dependencies");

            installDependencies(functionConf);
        }

        // Snapshot the directory after installing dependencies
        List<Path> afterSnapshot = getDirectorySnapshot(functionConf);

        List<Path> addedFiles = new ArrayList<>(afterSnapshot);
        addedFiles.removeAll(beforeSnapshot);

        loggingHelper.logInfoWithName(log, functionConf.getFunctionName(), "Packaging function for AWS Lambda");
        File tempFile;

        try {
            tempFile = File.createTempFile("python-lambda-build", "zip");
            tempFile.deleteOnExit();
        } catch (IOException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException(e);
        }

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
                .forEach(this::touch);

        ZipUtil.pack(functionConf.getBuildDirectory(), tempFile);

        try {
            Files.move(tempFile.toPath(), new File(getArchivePath(functionConf)).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException(e);
        }
    }

    private void installDependencies(FunctionConf functionConf) {
        try {
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
                processBuilder.directory(functionConf.getBuildDirectory());

                List<String> stdoutStrings = new ArrayList<>();
                List<String> stderrStrings = new ArrayList<>();

                Optional<Integer> exitVal = processHelper.getOutputFromProcess(log, processBuilder, true, Optional.of(stdoutStrings::add), Optional.of(stderrStrings::add));

                if (!exitVal.isPresent() || exitVal.get() != 0) {
                    log.error("Failed to install Python dependency.  Make sure Python and pip are installed and on your path.");
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    private void touch(File file) {
        try {
            new FileOutputStream(file).close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<Path> getDirectorySnapshot(FunctionConf functionConf) {
        try {
            return Files.list(functionConf.getBuildDirectory().toPath()).collect(Collectors.toList());
        } catch (IOException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public void verifyHandlerExists(FunctionConf functionConf) {
        String buildDirectory = functionConf.getBuildDirectory().toString();
        String handlerName = functionConf.getHandlerName();

        if (!handlerName.contains(".")) {
            throw new UnsupportedOperationException("Python handler name [" + handlerName + "] does not contain a dot separator (e.g. LambdaFunction.function_handler)");
        }

        String[] handlerNameParts = handlerName.split("\\.");

        if (handlerNameParts.length != 2) {
            throw new UnsupportedOperationException("Python handler name [" + handlerName + "] contains too many dot separators or is missing the handler function name (e.g. LambdaFunction.function_handler)");
        }

        String handlerFilename = handlerNameParts[0];
        String handlerFunctionName = handlerNameParts[1];

        File handlerFile = new File(String.join("/", buildDirectory, handlerFilename + ".py"));

        if (!handlerFile.exists()) {
            throw new UnsupportedOperationException("Python handler file [" + handlerFile.toPath() + "] does not exist");
        }

        try {
            // Use a regex to find what the function should look like
            String pattern = "^def\\s+" + handlerFunctionName + "\\s*\\(.*";

            if (Files.lines(handlerFile.toPath())
                    .noneMatch(line -> line.matches(pattern))) {
                throw new UnsupportedOperationException("Python handler function name [" + handlerFunctionName + "] does not appear to exist in [" + handlerFile.toPath() + "].  This function will not work.  Stopping build.");
            }
        } catch (IOException e) {
            throw new UnsupportedOperationException(e);
        }
    }
}
