package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ProcessHelper;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class BasicProcessHelper implements ProcessHelper {
    private static final Consumer<String> NOOP = s -> {
    };

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

        return processBuilder;
    }

    @Override
    public Optional<Integer> getOutputFromProcess(Logger logger, ProcessBuilder pb, boolean waitForExit, Optional<Consumer<String>> stdoutConsumer, Optional<Consumer<String>> stderrConsumer) {
        try {
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
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        return Optional.empty();
    }
}
