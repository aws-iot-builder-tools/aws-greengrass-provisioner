package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

public interface JsonHelper {
    String toJson(Object object);

    <T> T fromJson(Class<T> clazz, byte[] json);
}
