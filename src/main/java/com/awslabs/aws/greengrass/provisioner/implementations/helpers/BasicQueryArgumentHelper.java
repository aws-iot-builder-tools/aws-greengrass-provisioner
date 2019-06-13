package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.QueryArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.QueryArgumentHelper;
import com.beust.jcommander.JCommander;

import javax.inject.Inject;

public class BasicQueryArgumentHelper implements QueryArgumentHelper {
    @Inject
    public BasicQueryArgumentHelper() {
    }

    @Override
    public void displayUsage() {
        QueryArguments queryArguments = new QueryArguments();

        JCommander.newBuilder()
                .addObject(queryArguments)
                .build()
                .usage();
    }

    @Override
    public QueryArguments parseArguments(String[] args) {
        QueryArguments queryArguments = new QueryArguments();

        JCommander.newBuilder()
                .addObject(queryArguments)
                .build()
                .parse(args);

        if (!queryArguments.isRequiredOptionSet()) {
            throw new RuntimeException("This is not a query request");
        }

        if (queryArguments.groupName == null) {
            throw new RuntimeException("Group name is required for all operations");
        }

        return queryArguments;
    }
}
