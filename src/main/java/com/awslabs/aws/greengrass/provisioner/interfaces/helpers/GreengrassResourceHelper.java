package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import software.amazon.awssdk.services.greengrass.model.ResourceDefinitionVersion;

import java.util.List;

public interface GreengrassResourceHelper {
    void validateResourceDefinitionVersion(ResourceDefinitionVersion resourceDefinitionVersion);

    <R> List<R> flatMapResources(List<FunctionConf> functionConfs, java.util.function.Function<FunctionConf, List<R>> method);
}
