package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ExecutorHelper;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// NOTE: This fails if multiple Gradle projects are trying to build a dependent project at the same time!

@Slf4j
public class ParallelExecutorHelper implements ExecutorHelper {
    @Inject
    public ParallelExecutorHelper() {
    }

    @Override
    public ExecutorService getExecutor() {
        return Executors.newCachedThreadPool();
    }
}
