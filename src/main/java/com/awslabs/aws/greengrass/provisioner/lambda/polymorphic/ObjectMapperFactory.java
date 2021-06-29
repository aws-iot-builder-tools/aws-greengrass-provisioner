package com.awslabs.aws.greengrass.provisioner.lambda.polymorphic;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class ObjectMapperFactory {

    public static ObjectMapper create() {
        ObjectMapper objectMapper = new ObjectMapper();
        // objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
        // objectMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // objectMapper.setVisibilityChecker(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
        //                 .withFieldVisibility(Visibility.ANY)
        //                 .withGetterVisibility(Visibility.NONE)
        //                 .withSetterVisibility(Visibility.NONE)
        //                 .withCreatorVisibility(Visibility.NONE));

        return objectMapper;
    }
}