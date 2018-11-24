package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface ProcessHelper {
    ProcessBuilder getProcessBuilder(List<String> programAndArguments);

    Optional<Integer> getOutputFromProcess(Logger logger, ProcessBuilder pb, boolean waitForExit, Optional<Consumer<String>> stdoutConsumer, Optional<Consumer<String>> stderrConsumer);
}
