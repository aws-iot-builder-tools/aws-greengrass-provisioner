package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.JsonHelper;
import com.google.gson.GsonBuilder;

import javax.inject.Inject;

public class BasicJsonHelper implements JsonHelper {
    @Inject
    public BasicJsonHelper() {
    }

    @Override
    public String toJson(Object object) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(object);
    }
}
