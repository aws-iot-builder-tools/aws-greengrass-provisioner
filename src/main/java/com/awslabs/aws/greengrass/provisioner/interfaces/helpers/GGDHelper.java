package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.GGDConf;

public interface GGDHelper {
    GGDConf getGGDConf(String groupName, String ggdName);
}
