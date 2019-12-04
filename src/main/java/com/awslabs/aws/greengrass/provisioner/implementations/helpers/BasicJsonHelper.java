package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.JsonHelper;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapterFactory;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.ServiceLoader;

public class BasicJsonHelper implements JsonHelper {
    @Inject
    public BasicJsonHelper() {
    }

    @Override
    public String toJson(Object object) {
        return getGsonBuilder()
                .disableHtmlEscaping()
                .setPrettyPrinting()
                .create()
                .toJson(object);
    }

    @NotNull
    private GsonBuilder getGsonBuilder() {
        GsonBuilder gsonBuilder = new GsonBuilder();

        // This allows us to use GSON with Immutables - https://immutables.github.io/json.html#type-adapter-registration
        for (TypeAdapterFactory factory : ServiceLoader.load(TypeAdapterFactory.class)) {
            gsonBuilder.registerTypeAdapterFactory(factory);
        }

        return gsonBuilder;
    }

    @Override
    public <T> T fromJson(Class<T> clazz, byte[] json) {
        return getGsonBuilder().create()
                .fromJson(new String(json), clazz);
    }
}
