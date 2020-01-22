package com.awslabs.aws.greengrass.provisioner;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.Operation;
import com.awslabs.aws.iot.resultsiterator.helpers.v2.V2HelperModule;
import com.awslabs.aws.iot.resultsiterator.helpers.v2.interfaces.V2SdkErrorHandler;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import com.typesafe.config.ConfigException;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkClientException;

import javax.inject.Inject;
import java.util.Optional;
import java.util.Set;

public class AwsGreengrassProvisioner implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(AwsGreengrassProvisioner.class);
    private static Optional<Injector> optionalInjector = Optional.empty();
    @Inject
    Set<Operation> operations;
    private String[] args;

    @Inject
    public AwsGreengrassProvisioner() {
    }

    public static void main(String[] args) {
        V2SdkErrorHandler sdkErrorHandler = getSdkErrorHandler();

        Try.run(() -> runProvisioner(args))
                .onFailure(throwable -> handleProvisionerFailure(sdkErrorHandler, throwable))
                .get();
    }

    private static void handleProvisionerFailure(V2SdkErrorHandler sdkErrorHandler, Throwable throwable) {
        // Sometimes dependency injection exceptions mask the actual SDK exception
        if ((throwable instanceof ProvisionException) && (throwable.getCause() instanceof SdkClientException)) {
            throwable = throwable.getCause();
        }

        if (throwable instanceof SdkClientException) {
            sdkErrorHandler.handleSdkError((SdkClientException) throwable);
        }

        // Print out the stack trace and log the error message as the final line of output
        throwable.printStackTrace();
        log.error(throwable.getMessage());

        if (throwable instanceof ConfigException) {
            log.error("If configuration values are missing or not resolving properly make sure the deployment.defaults.conf, function.defaults.conf, and connector.defaults.conf files are up to date. The issue could be to a configuration file layout change. Updating the AWS Greengrass Lambda Functions example repo usually fixes this.");
        }

        // Make sure the JVM actually exits
        System.exit(1);
    }

    private static void runProvisioner(String[] args) {
        AwsGreengrassProvisioner awsGreengrassProvisioner = getAwsGreengrassProvisioner();

        awsGreengrassProvisioner.setArgs(args);

        awsGreengrassProvisioner.run();
    }

    public static V2SdkErrorHandler getSdkErrorHandler() {
        return getInjector().getInstance(V2SdkErrorHandler.class);
    }

    public static AwsGreengrassProvisioner getAwsGreengrassProvisioner() {
        return getInjector().getInstance(AwsGreengrassProvisioner.class);
    }

    public static Injector getInjector() {
        if (!optionalInjector.isPresent()) {
            optionalInjector = Optional.of(Guice.createInjector(new AwsGreengrassProvisionerModule(), new V2HelperModule()));
        }

        return optionalInjector.get();
    }

    public void run() {
        Try.of(() -> operations.stream()
                // Find an operation with arguments that match
                .filter(operation -> operation.matches(args))
                .findFirst()
                // Execute the operation
                .map(operation -> operation.execute(args)))
                // If the operation fails then log the error
                .onFailure(throwable -> log.error(throwable.getMessage()))
                // If the operation succeeds make sure the result isn't empty. An empty result means that nothing was done.
                .onSuccess(this::logIfNoOperationSpecified)
                .get();
    }

    private void logIfNoOperationSpecified(Optional<Boolean> success) {
        if (!success.isPresent()) {
            log.error("No operation specified");
        }
    }

    private void setArgs(String[] args) {
        this.args = args;
    }
}
