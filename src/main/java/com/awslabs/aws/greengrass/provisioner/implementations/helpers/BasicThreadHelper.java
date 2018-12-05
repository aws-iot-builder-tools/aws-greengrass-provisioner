package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ExecutorHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ThreadHelper;
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

        Future<T> future = executor.submit(callable);

        try {
            return Optional.ofNullable(future.get(timeout, timeUnit));
        } catch (InterruptedException e) {
            log.error("Task interrupted [" + e.getMessage() + "]");
            return Optional.empty();
        } catch (ExecutionException e) {
            log.error("Task exception [" + e.getMessage() + "]");
            return Optional.empty();
        } catch (TimeoutException e) {
            return Optional.empty();
        } finally {
            executor.shutdownNow();
        }
    }
}
