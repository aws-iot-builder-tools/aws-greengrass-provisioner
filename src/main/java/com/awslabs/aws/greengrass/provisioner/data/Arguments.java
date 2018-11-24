package com.awslabs.aws.greengrass.provisioner.data;

public abstract class Arguments {
    final String LONG_GROUP_NAME_OPTION = "--group-name";
    final String SHORT_GROUP_NAME_OPTION = "-g";

    abstract public String getRequiredOptionName();

    abstract public boolean isRequiredOptionSet();

    abstract void setError(String error);

    abstract String getError();
}
