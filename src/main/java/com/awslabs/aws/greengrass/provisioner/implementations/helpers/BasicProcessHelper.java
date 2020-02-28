package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ProcessHelper;
import io.vavr.control.Try;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import software.amazon.awssdk.auth.credentials.AwsCredentials;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class BasicProcessHelper implements ProcessHelper {
    private static final String AWS_SECRET_KEY = "AWS_SECRET_KEY";
    private static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    private static final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    private static final String AWS_ACCESS_KEY = "AWS_ACCESS_KEY";
    private static final Consumer<String> NOOP = s -> {
    };

    @Inject
    // Minor hack for integration tests
    public AwsCredentials awsCredentials;

    @Inject
    public BasicProcessHelper() {
    }

    @Override
    public ProcessBuilder getProcessBuilder(List<String> programAndArguments) {
        List<String> output = new ArrayList<>();

        if (SystemUtils.IS_OS_WINDOWS) {
            output.add("cmd.exe");
            output.add("/C");
        }

        output.addAll(programAndArguments);

        ProcessBuilder processBuilder = new ProcessBuilder(output);

        // Add in the access key ID and secret access key for when we are running processes that need them like IDT
        Map<String, String> environment = processBuilder.environment();
        // NOTE: Device Tester v1.2 does not work in Docker without AWS_ACCESS_KEY and AWS_SECRET_KEY in the environment
        environment.put(AWS_ACCESS_KEY, awsCredentials.accessKeyId());
        environment.put(AWS_ACCESS_KEY_ID, awsCredentials.accessKeyId());
        environment.put(AWS_SECRET_KEY, awsCredentials.secretAccessKey());
        environment.put(AWS_SECRET_ACCESS_KEY, awsCredentials.secretAccessKey());

        return processBuilder;
    }

    @Override
    public Optional<Integer> getOutputFromProcess(Logger logger, ProcessBuilder pb, boolean waitForExit, Optional<Consumer<String>> stdoutConsumer, Optional<Consumer<String>> stderrConsumer) {
        return Try.of(() -> innerGetOutputFromProcess(pb, waitForExit, stdoutConsumer, stderrConsumer))
                .recover(Exception.class, throwable -> logExceptionMessageAndReturnEmpty(logger, throwable))
                .get();
    }

    private Optional<Integer> logExceptionMessageAndReturnEmpty(Logger logger, Exception throwable) {
        logger.error(throwable.getMessage());

        return Optional.empty();
    }

    private Optional<Integer> innerGetOutputFromProcess(ProcessBuilder pb, boolean waitForExit, Optional<Consumer<String>> stdoutConsumer, Optional<Consumer<String>> stderrConsumer) throws IOException, InterruptedException {
        Process p = pb.start();

        BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
        BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));

        Thread stdoutThread = new Thread(() -> stdout.lines().forEach(stdoutConsumer.orElse(NOOP)));
        stdoutThread.start();

        Thread stderrThread = new Thread(() -> stderr.lines().forEach(stderrConsumer.orElse(NOOP)));
        stderrThread.start();

        // Did they want to wait for the process to exit?
        if (waitForExit) {
            // Yes, wait for the process to exit
            p.waitFor();

            // Wait for the processing of the STDOUT stream to finish
            stdoutThread.join();

            // Wait for the processing of the STDERR stream to finish
            stderrThread.join();

            return Optional.of(p.exitValue());
        } else {
            return Optional.empty();
        }
    }
}
