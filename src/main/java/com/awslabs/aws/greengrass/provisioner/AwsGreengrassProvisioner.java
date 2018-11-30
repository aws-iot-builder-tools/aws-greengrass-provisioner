package com.awslabs.aws.greengrass.provisioner;

import com.amazonaws.SdkClientException;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.arguments.QueryArguments;
import com.awslabs.aws.greengrass.provisioner.data.arguments.UpdateArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.beust.jcommander.ParameterException;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.Arrays;

@Slf4j
public class AwsGreengrassProvisioner implements Runnable {
    public static final String serviceRoleName = "Greengrass_ServiceRole";

    @Inject
    DeploymentArgumentHelper deploymentArgumentHelper;
    @Inject
    UpdateArgumentHelper updateArgumentHelper;
    @Inject
    QueryArgumentHelper queryArgumentHelper;
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
        SdkErrorHandler sdkErrorHandler = null;

        try {
            sdkErrorHandler = DaggerAwsGreengrassProvisionerComponent.create().SDK_ERROR_HANDLER();
            AwsGreengrassProvisioner awsGreengrassProvisioner = DaggerAwsGreengrassProvisionerComponent.create().GG_PROVISIONER();

            awsGreengrassProvisioner.setArgs(args);

            awsGreengrassProvisioner.run();
        } catch (SdkClientException e) {
            sdkErrorHandler.handleSdkError(e);
        }
    }

    public void run() {
        if (contains(new DeploymentArguments().getRequiredOptionName(), args)) {
            // This looks like a deployment
            String deploymentError;
            DeploymentArguments deploymentArguments = null;

            try {
                deploymentArguments = deploymentArgumentHelper.parseArguments(args);
                deploymentError = deploymentArguments.getError();

            } catch (ParameterException e) {
                deploymentError = e.getMessage();
            }

            if ((deploymentError == null) && deploymentArguments.help) {
                deploymentArgumentHelper.displayUsage();
                return;
            } else if (deploymentError != null) {
                log.error(deploymentError);
            } else {
                deploymentHelper.doDeployment(deploymentArguments);
            }

            return;
        }

        if (contains(new UpdateArguments().getRequiredOptionName(), args)) {
            // This looks like an update
            String updateError;
            UpdateArguments updateArguments = null;

            try {
                updateArguments = updateArgumentHelper.parseArguments(args);
                updateError = updateArguments.getError();

            } catch (ParameterException e) {
                updateError = e.getMessage();
            }

            if ((updateError == null) && updateArguments.help) {
                updateArgumentHelper.displayUsage();
                return;
            } else if (updateError != null) {
                log.error(updateError);
            } else {
                groupUpdateHelper.doUpdate(updateArguments);
            }

            return;
        }

        if (contains(new QueryArguments().getRequiredOptionName(), args)) {
            // This looks like a query
            String queryError;
            QueryArguments queryArguments = null;

            try {
                queryArguments = queryArgumentHelper.parseArguments(args);
                queryError = queryArguments.getError();

            } catch (ParameterException e) {
                queryError = e.getMessage();
            }

            if ((queryError == null) && queryArguments.help) {
                queryArgumentHelper.displayUsage();
                return;
            } else if (queryError != null) {
                log.error(queryError);
            } else {
                groupQueryHelper.doQuery(queryArguments);
            }

            return;
        }

        // Couldn't figure out what they wanted to do, just exit
        log.error("No operation specified");
    }

    public void setArgs(String[] args) {
        this.args = args;
    }


    private boolean contains(String requiredOption, String[] args) {
        return Arrays.stream(args).anyMatch(arg -> arg.equals(requiredOption));
    }
}
