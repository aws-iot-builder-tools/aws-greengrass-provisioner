package com.awslabs.aws.greengrass.provisioner.docker.interfaces;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.PushResponseItem;

import java.util.Optional;

public interface DockerPushHandler extends ResultCallback<PushResponseItem> {
    void await() throws InterruptedException;

    Optional<Throwable> getPushError();
}
