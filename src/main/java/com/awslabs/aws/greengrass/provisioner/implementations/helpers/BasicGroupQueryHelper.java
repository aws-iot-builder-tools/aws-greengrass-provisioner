package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.amazonaws.services.greengrass.model.*;
import com.awslabs.aws.greengrass.provisioner.data.QueryArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GreengrassHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GroupQueryHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.JsonHelper;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

@Slf4j
public class BasicGroupQueryHelper implements GroupQueryHelper {
    @Inject
    GreengrassHelper greengrassHelper;
    @Inject
    IoHelper ioHelper;
    @Inject
    JsonHelper jsonHelper;

    @Inject
    public BasicGroupQueryHelper() {
    }

    @Override
    public void doQuery(QueryArguments queryArguments) {
        if (!queryArguments.getGroupCa &&
                !queryArguments.listSubscriptions &&
                !queryArguments.listFunctions &&
                !queryArguments.listDevices) {
            log.error("No query specified");
            return;
        }

        Optional<GroupInformation> optionalGroupInformation = greengrassHelper.getGroupInformation(queryArguments.groupName);

        if (!optionalGroupInformation.isPresent()) {
            log.error("Group [" + queryArguments.groupName + "] not found");
            return;
        }

        GroupInformation groupInformation = optionalGroupInformation.get();

        if (queryArguments.getGroupCa) {
            GetGroupCertificateAuthorityResult getGroupCertificateAuthorityResult = greengrassHelper.getGroupCa(groupInformation);

            if (getGroupCertificateAuthorityResult == null) {
                log.error("Couldn't get the group CA");
                return;
            }

            String pem = getGroupCertificateAuthorityResult.getPemEncodedCertificate();
            log.info("Group CA for group [" + queryArguments.groupName + "]\n" + pem);

            String outputFilename = "build/" + queryArguments.groupName + "_Core_CA.pem";

            writeToFile(queryArguments, pem, outputFilename);
            return;
        }

        if (queryArguments.listSubscriptions) {
            List<Subscription> subscriptions = greengrassHelper.getSubscriptions(groupInformation);

            log.info("Subscriptions:");
            String output = jsonHelper.toJson(subscriptions);
            log.info(output);

            String outputFilename = "build/" + queryArguments.groupName + "_subscription_table.json";

            writeToFile(queryArguments, output, outputFilename);
            return;
        }

        if (queryArguments.listFunctions) {
            List<Function> functions = greengrassHelper.getFunctions(groupInformation);

            log.info("Functions:");
            String output = jsonHelper.toJson(functions);
            log.info(output);

            String outputFilename = "build/" + queryArguments.groupName + "_function_table.json";

            writeToFile(queryArguments, output, outputFilename);
            return;
        }

        if (queryArguments.listDevices) {
            List<Device> devices = greengrassHelper.getDevices(groupInformation);

            log.info("Devices:");
            String output = jsonHelper.toJson(devices);
            log.info(output);

            String outputFilename = "build/" + queryArguments.groupName + "_device_table.json";

            writeToFile(queryArguments, output, outputFilename);
            return;
        }

        throw new UnsupportedOperationException("This should never happen.  This is a bug.");
    }

    private void writeToFile(QueryArguments queryArguments, String output, String outputFilename) {
        if (queryArguments.writeToFile) {
            ioHelper.writeFile(outputFilename, output.getBytes());
            log.info("This data was also written to [" + outputFilename + "]");
        }
    }

}
