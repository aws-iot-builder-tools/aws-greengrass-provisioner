package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ExecutorHelper;
import org.gradle.internal.concurrent.ThreadFactoryImpl;

import javax.inject.Inject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SingleThreadedExecutorHelper implements ExecutorHelper {
    @Inject
    public SingleThreadedExecutorHelper() {
    }

    @Override
    public ExecutorService getExecutor() {
        return Executors.newSingleThreadExecutor(new ThreadFactoryImpl(SingleThreadedExecutorHelper.class.getSimpleName()));
    }
}
