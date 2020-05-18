package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ExecutorHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ThreadHelper;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.*;

public class BasicThreadHelper implements ThreadHelper {
    private final Logger log = LoggerFactory.getLogger(BasicThreadHelper.class);
    @Inject
    public ExecutorHelper executorHelper;

    @Inject
    public BasicThreadHelper() {
    }

    @Override
    public <T> Optional<T> timeLimitTask(Callable<T> callable, int timeout, TimeUnit timeUnit) {
        ExecutorService executor = executorHelper.getExecutor();

        return Try.of(() -> submitToExecutor(callable, timeout, timeUnit, executor))
                .recover(InterruptedException.class, this::logInterruptedMessage)
                .recover(ExecutionException.class, this::logExceptionMessage)
                .recover(TimeoutException.class, throwable -> Optional.empty())
                .andFinally(executor::shutdownNow)
                .get();
    }

    private <T> Optional<T> logExceptionMessage(ExecutionException throwable) {
        log.error(String.join("", "Task exception [", throwable.getMessage(), "]"));
        return Optional.empty();
    }

    private <T> Optional<T> logInterruptedMessage(InterruptedException throwable) {
        log.error(String.join("", "Task interrupted [", throwable.getMessage(), "]"));
        return Optional.empty();
    }

    private <T> Optional<T> submitToExecutor(Callable<T> callable, int timeout, TimeUnit timeUnit, ExecutorService executor) throws InterruptedException, ExecutionException, TimeoutException {
        Future<T> future = executor.submit(callable);

        return Optional.ofNullable(future.get(timeout, timeUnit));
    }
}
