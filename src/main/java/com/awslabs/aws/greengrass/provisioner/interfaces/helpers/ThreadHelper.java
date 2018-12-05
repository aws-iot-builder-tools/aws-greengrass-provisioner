package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public interface ThreadHelper {
    <T> Optional<T> timeLimitTask(Callable<T> callable, int timeout, TimeUnit timeUnit);
}
