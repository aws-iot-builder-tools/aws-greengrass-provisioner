package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.QueryArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.model.*;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

public class BasicGroupQueryHelper implements GroupQueryHelper {
    private final Logger log = LoggerFactory.getLogger(BasicGroupQueryHelper.class);
    @Inject
    GreengrassHelper greengrassHelper;
    @Inject
    IoHelper ioHelper;
    @Inject
    JsonHelper jsonHelper;
    @Inject
    QueryArgumentHelper queryArgumentHelper;

    @Inject
    public BasicGroupQueryHelper() {
    }

    @Override
    public Void execute(QueryArguments queryArguments) {
        if (!queryArguments.getGroupCa &&
                !queryArguments.listSubscriptions &&
                !queryArguments.listFunctions &&
                !queryArguments.listDevices) {
            throw new RuntimeException("No query specified");
        }

        Optional<GroupInformation> optionalGroupInformation = greengrassHelper.getGroupInformation(queryArguments.groupName);

        if (!optionalGroupInformation.isPresent()) {
            throw new RuntimeException("Group [" + queryArguments.groupName + "] not found");
        }

        GroupInformation groupInformation = optionalGroupInformation.get();

        if (queryArguments.getGroupCa) {
            GetGroupCertificateAuthorityResponse getGroupCertificateAuthorityResponse = greengrassHelper.getGroupCa(groupInformation);

            if (getGroupCertificateAuthorityResponse == null) {
                throw new RuntimeException("Couldn't get the group CA");
            }

            String pem = getGroupCertificateAuthorityResponse.pemEncodedCertificate();
            log.info("Group CA for group [" + queryArguments.groupName + "]\n" + pem);

            String outputFilename = "build/" + queryArguments.groupName + "_Core_CA.pem";

            writeToFile(queryArguments, pem, outputFilename);
            return null;
        }

        if (queryArguments.listSubscriptions) {
            List<Subscription> subscriptions = greengrassHelper.getSubscriptions(groupInformation);

            log.info("Subscriptions:");
            String output = jsonHelper.toJson(subscriptions);
            log.info(output);

            String outputFilename = "build/" + queryArguments.groupName + "_subscription_table.json";

            writeToFile(queryArguments, output, outputFilename);
            return null;
        }

        if (queryArguments.listFunctions) {
            List<Function> functions = greengrassHelper.getFunctions(groupInformation);

            log.info("Functions:");
            String output = jsonHelper.toJson(functions);
            log.info(output);

            String outputFilename = "build/" + queryArguments.groupName + "_function_table.json";

            writeToFile(queryArguments, output, outputFilename);
            return null;
        }

        if (queryArguments.listDevices) {
            List<Device> devices = greengrassHelper.getDevices(groupInformation);

            log.info("Devices:");
            String output = jsonHelper.toJson(devices);
            log.info(output);

            String outputFilename = "build/" + queryArguments.groupName + "_device_table.json";

            writeToFile(queryArguments, output, outputFilename);
            return null;
        }

        throw new RuntimeException("This should never happen.  This is a bug.");
    }

    @Override
    public ArgumentHelper<QueryArguments> getArgumentHelper() {
        return queryArgumentHelper;
    }

    @Override
    public QueryArguments getArguments() {
        return new QueryArguments();
    }

    private void writeToFile(QueryArguments queryArguments, String output, String outputFilename) {
        if (queryArguments.writeToFile) {
            ioHelper.writeFile(outputFilename, output.getBytes());
            log.info("This data was also written to [" + outputFilename + "]");
        }
    }
}
