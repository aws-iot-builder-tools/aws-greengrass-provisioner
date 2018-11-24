package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.UpdateArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.UpdateArgumentHelper;
import com.beust.jcommander.JCommander;
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
            updateArguments.setError("This is not an update request");
            return updateArguments;
        }

        if (updateArguments.groupName == null) {
            updateArguments.setError("Group name is required for all operations");
            return updateArguments;
        }

        if ((updateArguments.addSubscription || updateArguments.removeSubscription) &&
                !subscriptionTableEntriesPresent(updateArguments)) {
            // They want to add/remove a subscription but haven't specified the necessary subscription table entries
            return updateArguments;
        }

        if ((updateArguments.addFunction != null) || (updateArguments.removeFunction != null)) {
            // They want to add/remove a function...
            if (updateArguments.functionAlias == null) {
                // ...but haven't specified the alias
                updateArguments.setError("No function alias specified");
                return updateArguments;
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

    private boolean subscriptionTableEntriesPresent(UpdateArguments updateArguments) {
        if (updateArguments.subscriptionSource == null) {
            updateArguments.setError("No source was specified");
            return false;
        }

        if (updateArguments.subscriptionSubject == null) {
            updateArguments.setError("No subject was specified");
            return false;
        }

        if (updateArguments.subscriptionTarget == null) {
            updateArguments.setError("No target was specified");
            return false;
        }

        return true;
    }
}
