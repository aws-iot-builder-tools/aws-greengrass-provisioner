package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.Arguments;
import io.vavr.control.Try;

import java.util.Arrays;

public interface Operation<T extends Arguments> {
    Void execute(T arguments);

    ArgumentHelper<T> getArgumentHelper();

    T getArguments();

    default boolean matches(String[] args) {
        return Arrays.stream(args).anyMatch(arg -> arg.equals(getArguments().getRequiredOptionName()));
    }

    default boolean execute(String[] args) {
        Try.of(() -> getArgumentHelper().parseArguments(args))
                .onSuccess(success -> executeOrDisplayHelp(success))
                .get();

        return true;
    }

    default void executeOrDisplayHelp(T success) {
        if (success.isHelp()) {
            getArgumentHelper().displayUsage();
            return;
        }

        execute(success);
    }
}
