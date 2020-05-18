package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import org.slf4j.Logger;

public interface LoggingHelper {
    default void logInfoWithName(Logger log, String prefix, String message) {
        log.info(String.join("", "- [", prefix, "] - ", message));
    }
}
