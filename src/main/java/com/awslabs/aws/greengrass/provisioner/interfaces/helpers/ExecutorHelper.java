package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public interface ExecutorHelper {
    default <T> List<T> run(Logger log, List<Callable<T>> callables) {
        // Get an executor
        ExecutorService executorService = getExecutor();

        List<T> results = null;

        try {
            // Invoke all of the tasks and collect the results
            results = executorService.invokeAll(callables)
                    .stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new UnsupportedOperationException(e);
                        }
                    })
                    .collect(Collectors.toList());
        } catch (InterruptedException e) {
            log.error("Parallel task execution failed [" + e.getMessage() + "]");
            throw new UnsupportedOperationException(e);
        } finally {
            // Clean up the executor
            executorService.shutdown();
        }

        return results;
    }

    ExecutorService getExecutor();
}
