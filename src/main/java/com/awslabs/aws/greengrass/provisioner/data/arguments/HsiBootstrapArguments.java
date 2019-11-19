package com.awslabs.aws.greengrass.provisioner.data.arguments;

import com.beust.jcommander.Parameter;

public class HsiBootstrapArguments extends Arguments {
    private final String LONG_HSI_BOOTSTRAP_OPTION = "--hsi-bootstrap";
    private final String LONG_VENDOR_OPTION = "--vendor";
    private final String LONG_TARGET_OPTION = "--target";
    @Parameter(names = {LONG_HSI_BOOTSTRAP_OPTION}, description = "Bootstrap a system for HSI")
    public boolean hsiBootstrap;
    @Parameter(names = {LONG_VENDOR_OPTION}, description = "The name of the HSI vendor")
    public String hsiVendorString;
    public HsiVendor hsiVendor;
    @Parameter(names = {LONG_TARGET_OPTION}, description = "The target system to bootstrap (in user@host format)")
    public String target;
    public String targetUser;
    public String targetHost;
    @Parameter(names = "--help", help = true)
    private boolean help;

    @Override
    public boolean isHelp() {
        return help;
    }

    @Override
    public String getRequiredOptionName() {
        return LONG_HSI_BOOTSTRAP_OPTION;
    }

    @Override
    public boolean isRequiredOptionSet() {
        return hsiBootstrap;
    }
}
