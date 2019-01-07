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

        return Try.of(() -> {
            Future<T> future = executor.submit(callable);

            return Optional.ofNullable(future.get(timeout, timeUnit));
        })
                .recover(InterruptedException.class, throwable -> {
                    log.error("Task interrupted [" + throwable.getMessage() + "]");
                    return Optional.empty();
                })
                .recover(ExecutionException.class, throwable -> {
                    log.error("Task exception [" + throwable.getMessage() + "]");
                    return Optional.empty();
                })
                .recover(TimeoutException.class, throwable -> Optional.empty())
                .andFinally(() -> executor.shutdownNow())
                .get();
    }
}
