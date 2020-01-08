package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

public interface S3Helper {
    boolean bucketExists(String bucketName);

    boolean objectExists(String bucket, String key);
}
