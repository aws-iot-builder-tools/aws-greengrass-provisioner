package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.Arguments;
import io.vavr.control.Try;
import org.slf4j.Logger;

import java.util.Arrays;

public interface Operation<T extends Arguments> {
    Void execute(T arguments);

    ArgumentHelper<T> getArgumentHelper();

    T getArguments();

    default boolean matches(String[] args) {
        return Arrays.stream(args).anyMatch(arg -> arg.equals(getArguments().getRequiredOptionName()));
    }

    default boolean execute(Logger log, String[] args) {
        Try.of(() -> getArgumentHelper().parseArguments(args))
                .onFailure(throwable -> {
                    throw new RuntimeException(throwable);
                })
                .onSuccess(success -> {
                    if (success.isHelp()) {
                        getArgumentHelper().displayUsage();
                        return;
                    }

                    execute(success);
                });

        return true;
    }
}
