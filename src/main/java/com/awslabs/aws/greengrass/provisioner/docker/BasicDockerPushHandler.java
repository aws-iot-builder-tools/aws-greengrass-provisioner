package com.awslabs.aws.greengrass.provisioner.docker;

import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerPushHandler;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.api.model.ResponseItem;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class BasicDockerPushHandler implements DockerPushHandler {
    @Getter
    private Optional<Throwable> pushError = Optional.empty();
    private CountDownLatch finishLatch = new CountDownLatch(1);
    private Map<String, Long> current = new HashMap<>();
    private Map<String, Long> total = new HashMap<>();

    @Inject
    public BasicDockerPushHandler() {
    }

    @Override
    public void onStart(Closeable closeable) {
        log.debug("Docker push start");
    }

    @Override
    public void onNext(PushResponseItem object) {
        if (object.getProgressDetail() == null) {
            // No progress information, do nothing
            return;
        }

        ResponseItem.ProgressDetail progressDetail = object.getProgressDetail();

        if ((progressDetail.getCurrent() == null) || (progressDetail.getTotal() == null)) {
            // Without the current and total we can't indicate progress properly, do nothing
            return;
        }

        String id = object.getId();
        Long currentValue = object.getProgressDetail().getCurrent();
        Long totalValue = object.getProgressDetail().getTotal();

        if (currentValue > totalValue) {
            // If we are finished with the current item remove it from the totals
            current.remove(id);
            total.remove(id);
        } else {
            // Add the position and size for the current object
            current.put(id, currentValue);
            total.put(id, totalValue);
        }

        // Calculate the total and how far along we are
        Long sumTotal = total.values().stream().mapToLong(Long::longValue).sum();
        Long currentTotal = current.values().stream().mapToLong(Long::longValue).sum();

        if (sumTotal.equals(currentTotal)) {
            // Don't print out 100% until we're complete
            return;
        }

        // Calculate a percentage
        double percentComplete = (double) currentTotal / (double) sumTotal;
        percentComplete = Math.round(percentComplete * 10000) / 100.0;

        System.out.print("\r[INFO] ECR push status: " + percentComplete + "% complete     ");
    }

    @Override
    public void onError(Throwable throwable) {
        pushError = Optional.of(throwable);
        finishLatch.countDown();
        log.error("Docker push error [" + throwable.getMessage() + "]");
    }

    @Override
    public void onComplete() {
        System.out.println("\r[INFO] ECR push status: 100% complete     ");

        finishLatch.countDown();
        log.info("Docker push complete");
    }

    @Override
    public void close() {
        log.info("close");
    }

    @Override
    public void await() throws InterruptedException {
        finishLatch.await();
    }
}
