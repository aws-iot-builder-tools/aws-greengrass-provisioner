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

        List<T> results = Try.of(() -> invokeAll(callables, executorService))
                .onFailure(throwable -> logAndRethrow(log, throwable))
                .andFinallyTry(executorService::shutdown)
                .get();

        return results;
    }

    default void logAndRethrow(Logger log, Throwable throwable) {
        log.error(String.join("", "Task execution failed [", throwable.getMessage(), "]"));
        throw new RuntimeException(throwable);
    }

    default <T> List<T> invokeAll(List<Callable<T>> callables, ExecutorService executorService) throws InterruptedException {
        // Invoke all of the tasks and collect the results
        return executorService.invokeAll(callables)
                .stream()
                // Get the value from the future
                .map(future -> Try.of(future::get))
                // Get the value from the Try
                .map(Try::get)
                .collect(Collectors.toList());
    }

    ExecutorService getExecutor();
}
