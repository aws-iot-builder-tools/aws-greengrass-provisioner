package com.awslabs.aws.greengrass.provisioner.data.arguments;

public abstract class Arguments {
    public static final String SHORT_GROUP_NAME_OPTION = "-g";
    public static final String SHORT_ARCHITECTURE_OPTION = "-a";
    final String LONG_GROUP_NAME_OPTION = "--group-name";
    final String LONG_ARCHITECTURE_OPTION = "--arch";

    abstract public String getRequiredOptionName();

    abstract public boolean isRequiredOptionSet();

    abstract public boolean isHelp();
}
