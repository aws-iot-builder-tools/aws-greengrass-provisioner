package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.QueryArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.QueryArgumentHelper;
import com.beust.jcommander.JCommander;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

@Slf4j
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
            queryArguments.setError("This is not a query request");
            return queryArguments;
        }

        if (queryArguments.groupName == null) {
            queryArguments.setError("Group name is required for all operations");
            return queryArguments;
        }

        return queryArguments;
    }
}
