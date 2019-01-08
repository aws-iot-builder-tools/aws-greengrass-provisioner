package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import io.vavr.control.Try;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public interface ExecutorHelper {
    default <T> List<T> run(Logger log, List<Callable<T>> callables) {
        // Get an executor
        ExecutorService executorService = getExecutor();

        List<T> results = Try.of(() -> {
            // Invoke all of the tasks and collect the results
            return executorService.invokeAll(callables)
                    .stream()
                    .map(future -> Try.of(() -> future.get()).get())
                    .collect(Collectors.toList());
        })
                .onFailure(throwable -> {
                    log.error("Parallel task execution failed [" + throwable.getMessage() + "]");
                    throw new RuntimeException(throwable);
                })
                .andFinallyTry(() -> executorService.shutdown())
                .get();

        return results;
    }

    ExecutorService getExecutor();
}
