package com.awslabs.aws.greengrass.provisioner.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.JsonHelper;
import io.vavr.control.Try;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AwsGreengrassProvisionerLambda implements RequestHandler<LambdaInput, Map> {
    public static final String CONFIG_JSON_KEY = "config/config.json";
    @Inject
    IoHelper ioHelper;
    @Inject
    JsonHelper jsonHelper;

    @Override
    public Map handleRequest(LambdaInput lambdaInput, Context context) {
        AwsGreengrassProvisionerLambda awsGreengrassProvisionerLambda = AwsGreengrassProvisioner.getInjector().getInstance(AwsGreengrassProvisionerLambda.class);
        return awsGreengrassProvisionerLambda.innerHandleRequest(lambdaInput, context);
    }

    private Map innerHandleRequest(LambdaInput lambdaInput, Context context) {
        validateRequiredParameters(lambdaInput);

        File outputFile = Try.of(() -> ioHelper.getTempFile("oem", "json")).get();
        List<String> args = new ArrayList<>();

        args.add("-g");
        args.add(lambdaInput.groupName);

        args.add("-d");
        args.add("EMPTY");

        args.add("--core-role-name");
        args.add(lambdaInput.coreRoleName);

        args.add("--core-policy-name");
        args.add(lambdaInput.corePolicyName);

        args.add("--oem-json");
        args.add(outputFile.getAbsolutePath());

        if (lambdaInput.serviceRoleExists) {
            args.add("--service-role-exists");
        }

        AwsGreengrassProvisioner.main(args.toArray(new String[args.size()]));

        Map<String, String> oemJson = jsonHelper.fromJson(Map.class, ioHelper.readFileAsString(outputFile).getBytes());

        // Use a different private key location if they've specified it
        if (lambdaInput.keyPath != null) {
            Map<String, String> configMap = jsonHelper.fromJson(Map.class, oemJson.get(CONFIG_JSON_KEY).getBytes());
            configMap.put("keyPath", lambdaInput.keyPath);
            oemJson.put(CONFIG_JSON_KEY, jsonHelper.toJson(configMap));
        }

        return oemJson;
    }

    private void validateRequiredParameters(LambdaInput lambdaInput) {
        if (lambdaInput.groupName == null) {
            throw new RuntimeException("No group name specified");
        }

        if (lambdaInput.coreRoleName == null) {
            throw new RuntimeException("No core role name specified");
        }

        if (lambdaInput.corePolicyName == null) {
            throw new RuntimeException("No core policy name specified");
        }
    }
}
