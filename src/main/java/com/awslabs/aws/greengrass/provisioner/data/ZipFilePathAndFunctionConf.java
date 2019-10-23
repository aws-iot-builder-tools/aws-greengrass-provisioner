package com.awslabs.aws.greengrass.provisioner.data;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
public abstract class ZipFilePathAndFunctionConf {
    public abstract Optional<String> getError();

    public abstract Optional<String> getZipFilePath();

    public abstract FunctionConf getFunctionConf();
}
