package com.awslabs.aws.greengrass.provisioner.data.resources;

public interface LocalReadOnlyOrReadWriteResource extends LocalResource {
    boolean isReadWrite();
}
