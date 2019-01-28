package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ExecutorHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ThreadHelper;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
public class BasicThreadHelper implements ThreadHelper {
    @Inject
    ExecutorHelper executorHelper;

    @Inject
    public BasicThreadHelper() {
    }

    @Override
    public <T> Optional<T> timeLimitTask(Callable<T> callable, int timeout, TimeUnit timeUnit) {
        ExecutorService executor = executorHelper.getExecutor();

        return Try.of(() -> submitToExecutor(callable, timeout, timeUnit, executor))
                .recover(InterruptedException.class, throwable -> logInterruptedMessage(throwable))
                .recover(ExecutionException.class, throwable -> logExceptionMessage(throwable))
                .recover(TimeoutException.class, throwable -> Optional.empty())
                .andFinally(() -> executor.shutdownNow())
                .get();
    }

    public <T> Optional<T> logExceptionMessage(ExecutionException throwable) {
        log.error("Task exception [" + throwable.getMessage() + "]");
        return Optional.empty();
    }

    public <T> Optional<T> logInterruptedMessage(InterruptedException throwable) {
        log.error("Task interrupted [" + throwable.getMessage() + "]");
        return Optional.empty();
    }

    public <T> Optional<T> submitToExecutor(Callable<T> callable, int timeout, TimeUnit timeUnit, ExecutorService executor) throws InterruptedException, ExecutionException, TimeoutException {
        Future<T> future = executor.submit(callable);

        return Optional.ofNullable(future.get(timeout, timeUnit));
    }
}
