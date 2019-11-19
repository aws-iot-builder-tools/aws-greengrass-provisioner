package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.HsiBootstrapArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.HsiBootstrapArgumentHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SshHelper;
import com.beust.jcommander.JCommander;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class BasicHsiBootstrapArgumentHelper implements HsiBootstrapArgumentHelper {
    private final Logger log = LoggerFactory.getLogger(BasicHsiBootstrapArgumentHelper.class);

    @Inject
    SshHelper sshHelper;

    @Inject
    public BasicHsiBootstrapArgumentHelper() {
    }

    @Override
    public void displayUsage() {
        HsiBootstrapArguments hsiBootstrapArguments = new HsiBootstrapArguments();

        JCommander.newBuilder()
                .addObject(hsiBootstrapArguments)
                .build()
                .usage();
    }

    @Override
    public HsiBootstrapArguments parseArguments(String[] args) {
        HsiBootstrapArguments hsiBootstrapArguments = new HsiBootstrapArguments();

        JCommander.newBuilder()
                .addObject(hsiBootstrapArguments)
                .build()
                .parse(args);

        if (hsiBootstrapArguments.hsiVendorString != null) {
            hsiBootstrapArguments.hsiVendor = getHsiVendor(hsiBootstrapArguments.hsiVendorString);
        }

        if (hsiBootstrapArguments.hsiVendor == null) {
            throwDescriptiveHsiVendorException();
        }

        if (hsiBootstrapArguments.target != null) {
            String[] userAndHost = sshHelper.getUserAndHost("target destination", hsiBootstrapArguments.target);

            hsiBootstrapArguments.targetUser = userAndHost[0];
            hsiBootstrapArguments.targetHost = userAndHost[1];
        }

        if ((hsiBootstrapArguments.targetUser == null) || (hsiBootstrapArguments.targetHost == null)) {
            throw new RuntimeException("A target must be specified for HSI bootstrapping");
        }

        return hsiBootstrapArguments;
    }
}
