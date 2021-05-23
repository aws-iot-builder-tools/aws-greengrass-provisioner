package com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model;

public enum RequestEventType {
    Provision,
    Deploy;

    public int getValue() {
        return this.ordinal();
    }

    public static RequestEventType forValue(int value) {
        return values()[value];
    }
}
