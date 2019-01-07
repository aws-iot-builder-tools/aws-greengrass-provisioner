package com.awslabs.aws.greengrass.provisioner;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkClientException;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
public class AwsGreengrassProvisioner implements Runnable {
    public static final String serviceRoleName = "Greengrass_ServiceRole";
    private static Optional<Injector> optionalInjector = Optional.empty();
    @Inject
    GroupQueryHelper groupQueryHelper;
    @Inject
    GroupUpdateHelper groupUpdateHelper;
    @Inject
    DeploymentHelper deploymentHelper;
    private String[] args;

    @Inject
    public AwsGreengrassProvisioner() {
    }

    public static void main(String[] args) {
        SdkErrorHandler sdkErrorHandler = getSdkErrorHandler();

        Try.of(() -> {
            AwsGreengrassProvisioner awsGreengrassProvisioner = getAwsGreengrassProvisioner();

            awsGreengrassProvisioner.setArgs(args);

            awsGreengrassProvisioner.run();

            return null;
        }).onFailure(throwable -> {
            // Sometimes dependency injection exceptions mask the actual SDK exception
            if ((throwable instanceof ProvisionException) && (throwable.getCause() instanceof SdkClientException)) {
                throwable = throwable.getCause();
            }

            if (throwable instanceof SdkClientException) {
                sdkErrorHandler.handleSdkError((SdkClientException) throwable);
            }

            throw new RuntimeException(throwable);
        });
    }

    public static SdkErrorHandler getSdkErrorHandler() {
        return getInjector().getInstance(SdkErrorHandler.class);
    }

    public static AwsGreengrassProvisioner getAwsGreengrassProvisioner() {
        return getInjector().getInstance(AwsGreengrassProvisioner.class);
    }

    public static Injector getInjector() {
        if (!optionalInjector.isPresent()) {
            optionalInjector = Optional.of(Guice.createInjector(new AwsGreengrassProvisionerModule()));
        }

        return optionalInjector.get();
    }

    public void run() {
        List<Operation> operations =
                Arrays.asList(deploymentHelper, groupUpdateHelper, groupQueryHelper);

        Try.of(() -> operations.stream()
                // Find an operation with arguments that match
                .filter(operation -> operation.matches(args))
                .findFirst()
                // Execute the operation
                .map(operation -> operation.execute(log, args)))
                // If the operation fails then log the error
                .onFailure(throwable -> log.error(throwable.getMessage()))
                // If the operation succeeds make sure the result isn't empty. An empty result means that nothing was done.
                .onSuccess(success -> {
                    if (!success.isPresent()) {
                        log.error("No operation specified");
                    }
                });
    }

    public void setArgs(String[] args) {
        this.args = args;
    }
}
