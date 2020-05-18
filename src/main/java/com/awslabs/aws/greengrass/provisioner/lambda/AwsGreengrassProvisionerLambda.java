package com.awslabs.aws.greengrass.provisioner.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.data.arguments.Arguments;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.DeploymentHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.general.helpers.interfaces.JsonHelper;
import io.vavr.control.Try;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AwsGreengrassProvisionerLambda implements RequestHandler<LambdaInput, Map> {
    public static final String CONFIG_JSON_KEY = "config/config.json";
    @Inject
    IoHelper ioHelper;
    @Inject
    JsonHelper jsonHelper;

    @Inject
    public AwsGreengrassProvisionerLambda() {
    }

    @Override
    public Map handleRequest(LambdaInput lambdaInput, Context context) {
        AwsGreengrassProvisionerLambda awsGreengrassProvisionerLambda = AwsGreengrassProvisioner.getInjector().awsGreengrassProvisionerLambda();
        return awsGreengrassProvisionerLambda.innerHandleRequest(lambdaInput, context);
    }

    private Map innerHandleRequest(LambdaInput lambdaInput, Context context) {
        validateRequiredParameters(lambdaInput);

        setEnvironmentVariablesForCredentialsIfNecessary(lambdaInput);

        File outputFile = Try.of(() -> ioHelper.getTempFile("oem", "json")).get();
        List<String> args = new ArrayList<>();

        args.add(Arguments.SHORT_GROUP_NAME_OPTION);
        args.add(lambdaInput.GroupName);

        args.add(DeploymentArguments.SHORT_DEPLOYMENT_CONFIG_OPTION);
        args.add(DeploymentHelper.EMPTY);

        args.add(DeploymentArguments.LONG_CORE_ROLE_NAME_OPTION);
        args.add(lambdaInput.CoreRoleName);

        args.add(DeploymentArguments.LONG_CORE_POLICY_NAME_OPTION);
        args.add(lambdaInput.CorePolicyName);

        args.add(DeploymentArguments.LONG_OEM_JSON_OUTPUT_OPTION);
        args.add(outputFile.getAbsolutePath());

        if (lambdaInput.ServiceRoleExists) {
            args.add(DeploymentArguments.LONG_SERVICE_ROLE_EXISTS_OPTION);
        }

        Optional.ofNullable(lambdaInput.Csr).ifPresent(csr -> addCsrOption(args, csr));
        Optional.ofNullable(lambdaInput.CertificateArn).ifPresent(certificateArn -> addCertificateArnOption(args, certificateArn));

        AwsGreengrassProvisioner.main(args.toArray(new String[args.size()]));

        Map<String, String> oemJson = jsonHelper.fromJson(Map.class, ioHelper.readFileAsString(outputFile).getBytes());

        // Use a different private key location if they've specified it
        if (lambdaInput.KeyPath != null) {
            Map<String, String> configMap = jsonHelper.fromJson(Map.class, oemJson.get(CONFIG_JSON_KEY).getBytes());
            configMap.put("keyPath", lambdaInput.KeyPath);
            oemJson.put(CONFIG_JSON_KEY, jsonHelper.toJson(configMap));
        }

        return oemJson;
    }

    private void setEnvironmentVariablesForCredentialsIfNecessary(LambdaInput lambdaInput) {
        if ((lambdaInput.AccessKeyId == null) || (lambdaInput.SecretAccessKey == null) || (lambdaInput.SessionToken == null)) {
            return;
        }

        System.setProperty("aws.accessKeyId", lambdaInput.AccessKeyId);
        System.setProperty("aws.secretAccessKey", lambdaInput.SecretAccessKey);
        System.setProperty("aws.sessionToken", lambdaInput.SessionToken);
    }

    private void addCsrOption(List<String> args, String csr) {
        if (csr.isEmpty()) {
            return;
        }

        args.add(DeploymentArguments.LONG_CSR_OPTION);
        args.add(csr);
    }

    private void addCertificateArnOption(List<String> args, String certificateArn) {
        if (certificateArn.isEmpty()) {
            return;
        }

        args.add(DeploymentArguments.LONG_CERTIFICATE_ARN_OPTION);
        args.add(certificateArn);
    }

    private void validateRequiredParameters(LambdaInput lambdaInput) {
        if (lambdaInput.GroupName == null) {
            throw new RuntimeException("No group name specified");
        }

        if (lambdaInput.CoreRoleName == null) {
            throw new RuntimeException("No core role name specified");
        }

        if (lambdaInput.CorePolicyName == null) {
            throw new RuntimeException("No core policy name specified");
        }

        boolean csrPresent = !Optional.ofNullable(lambdaInput.Csr).orElse("").isEmpty();
        boolean certificateArnPresent = !Optional.ofNullable(lambdaInput.CertificateArn).orElse("").isEmpty();

        if (csrPresent && certificateArnPresent) {
            throw new RuntimeException(String.join("", "Either specify a CSR [", lambdaInput.Csr, "], a certificate ARN [", lambdaInput.CertificateArn, "], or neither. Both CSR and certificate ARN options can not be present simultaneously."));
        }

        if ((lambdaInput.AccessKeyId != null) && (lambdaInput.SecretAccessKey != null)) {
            if (lambdaInput.SessionToken == null) {
                throw new RuntimeException("No session token detected for input credentials. Only temporary credentials can be passed in to this Lambda function for security reasons. Obtain temporary credentials from STS for this user/role and try again.");
            }
        }
    }
}
