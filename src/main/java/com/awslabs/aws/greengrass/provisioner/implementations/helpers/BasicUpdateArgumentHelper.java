package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.UpdateArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.UpdateArgumentHelper;
import com.beust.jcommander.JCommander;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

@Slf4j
public class BasicUpdateArgumentHelper implements UpdateArgumentHelper {
    @Inject
    public BasicUpdateArgumentHelper() {
    }

    @Override
    public void displayUsage() {
        UpdateArguments updateArguments = new UpdateArguments();

        JCommander.newBuilder()
                .addObject(updateArguments)
                .build()
                .usage();
    }

    @Override
    public UpdateArguments parseArguments(String[] args) {
        UpdateArguments updateArguments = new UpdateArguments();

        JCommander.newBuilder()
                .addObject(updateArguments)
                .build()
                .parse(args);

        if (!updateArguments.isRequiredOptionSet()) {
            throw new RuntimeException("This is not an update request");
        }

        if (updateArguments.groupName == null) {
            throw new RuntimeException("Group name is required for all operations");
        }

        Try triedSubscriptionTableEntriesPresent = Try.of(() -> subscriptionTableEntriesPresent(updateArguments));

        if ((updateArguments.addSubscription || updateArguments.removeSubscription) &&
                triedSubscriptionTableEntriesPresent.isFailure()) {
            // They want to add/remove a subscription but haven't specified the necessary subscription table entries
            throw new RuntimeException(triedSubscriptionTableEntriesPresent.getCause());
        }

        if ((updateArguments.addFunction != null) || (updateArguments.removeFunction != null)) {
            // They want to add/remove a function...
            if (updateArguments.functionAlias == null) {
                // ...but haven't specified the alias
                throw new RuntimeException("No function alias specified");
            }
        }

        if (updateArguments.addFunction != null) {
            // They want to add a function...
            if (updateArguments.functionBinary == false) {
                log.warn("Binary encoding not specified, defaulting to JSON");
            }

            if (updateArguments.functionPinned == false) {
                log.warn("Function not specified as pinned, defaulting to event driven only");
            }
        }

        return updateArguments;
    }

    private Void subscriptionTableEntriesPresent(UpdateArguments updateArguments) {
        if (updateArguments.subscriptionSource == null) {
            throw new RuntimeException("No source was specified");
        }

        if (updateArguments.subscriptionSubject == null) {
            throw new RuntimeException("No subject was specified");
        }

        if (updateArguments.subscriptionTarget == null) {
            throw new RuntimeException("No target was specified");
        }

        return null;
    }
}
