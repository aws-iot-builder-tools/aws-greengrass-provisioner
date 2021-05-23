package com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.service;

import com.amazonaws.services.lambda.runtime.Context;

import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model.ProvisionRequest;
import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model.ProvisionResponse;
import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.ObjectMapperFactory;
import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model.BaseRequest;
import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model.BaseResponse;

import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.data.arguments.Arguments;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.DeploymentHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.general.helpers.interfaces.JsonHelper;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vavr.control.Try;

import javax.inject.Inject;

import java.io.File;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Map;
import java.util.List;

public class ProvisionService implements BaseService {
  public static final String CONFIG_JSON_KEY = "config/config.json";
  
  @Inject
  IoHelper ioHelper;
  @Inject
  JsonHelper jsonHelper;

  @Inject
  public ProvisionService() {
  }

  @Override
  public Optional<BaseResponse> execute(BaseRequest request, Context context) {
    Optional<BaseResponse> result = Optional.empty();

    if(request instanceof ProvisionRequest){
      ObjectMapper mapper = ObjectMapperFactory.create();
      try {
        System.out.println("Im " + this.getClass().getName() + " and the input is " + mapper.writeValueAsString(request));
      } catch (JsonProcessingException e) {
        e.printStackTrace();
      }

      Map oemJson = this.innerHandleRequest((ProvisionRequest) request, context);

      ProvisionResponse res = new ProvisionResponse();

      res.setConfigJson((String)oemJson.get("config/config.json"));
      res.setRootCaPem((String)oemJson.get("certs/root.ca.pem"));
      res.setCoreKey((String)oemJson.get("certs/core.key"));
      res.setCoreCrt((String)oemJson.get("certs/core.crt"));

      result=Optional.of(res);
    }

    return result;
  }

  private Map innerHandleRequest(ProvisionRequest lambdaInput, Context context) {
    validateRequiredParameters(lambdaInput);

    setEnvironmentVariablesForCredentialsIfNecessary(lambdaInput);

    File outputFile = Try.of(() -> ioHelper.getTempFile("oem", "json")).get();
    List<String> args = new ArrayList<>();

    args.add(Arguments.SHORT_GROUP_NAME_OPTION);
    args.add(lambdaInput.getGroupName());

    args.add(DeploymentArguments.SHORT_DEPLOYMENT_CONFIG_OPTION);
    args.add(DeploymentHelper.EMPTY);

    args.add(DeploymentArguments.LONG_CORE_ROLE_NAME_OPTION);
    args.add(lambdaInput.getCoreRoleName());

    args.add(DeploymentArguments.LONG_CORE_POLICY_NAME_OPTION);
    args.add(lambdaInput.getCorePolicyName());

    args.add(DeploymentArguments.LONG_OEM_JSON_OUTPUT_OPTION);
    args.add(outputFile.getAbsolutePath());

    if (lambdaInput.getServiceRoleExists()) {
        args.add(DeploymentArguments.LONG_SERVICE_ROLE_EXISTS_OPTION);
    }

    Optional.ofNullable(lambdaInput.getCsr()).ifPresent(csr -> addCsrOption(args, csr));
    Optional.ofNullable(lambdaInput.getCertificateArn()).ifPresent(certificateArn -> addCertificateArnOption(args, certificateArn));

    AwsGreengrassProvisioner.main(args.toArray(new String[args.size()]));

    Map<String, String> oemJson = jsonHelper.fromJson(Map.class, ioHelper.readFileAsString(outputFile).getBytes());

    // Use a different private key location if they've specified it
    if (lambdaInput.getKeyPath() != null) {
        Map<String, String> configMap = jsonHelper.fromJson(Map.class, oemJson.get(CONFIG_JSON_KEY).getBytes());
        configMap.put("keyPath", lambdaInput.getKeyPath());
        oemJson.put(CONFIG_JSON_KEY, jsonHelper.toJson(configMap));
    }

    return oemJson;
  }

  private void setEnvironmentVariablesForCredentialsIfNecessary(ProvisionRequest lambdaInput) {
      if ((lambdaInput.getAccessKeyId() == null) || (lambdaInput.getSecretAccessKey() == null) || (lambdaInput.getSessionToken() == null)) {
          return;
      }

      System.setProperty("aws.accessKeyId", lambdaInput.getAccessKeyId());
      System.setProperty("aws.secretAccessKey", lambdaInput.getSecretAccessKey());
      System.setProperty("aws.sessionToken", lambdaInput.getSessionToken());

      if (lambdaInput.getRegion() != null) {
          System.setProperty("aws.region", lambdaInput.getRegion());
      }
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

  private void validateRequiredParameters(ProvisionRequest lambdaInput) {
      if (lambdaInput.getGroupName() == null) {
          throw new RuntimeException("No group name specified");
      }

      if (lambdaInput.getCoreRoleName() == null) {
          throw new RuntimeException("No core role name specified");
      }

      if (lambdaInput.getCorePolicyName() == null) {
          throw new RuntimeException("No core policy name specified");
      }

      boolean csrPresent = !Optional.ofNullable(lambdaInput.getCsr()).orElse("").isEmpty();
      boolean certificateArnPresent = !Optional.ofNullable(lambdaInput.getCertificateArn()).orElse("").isEmpty();

      if (csrPresent && certificateArnPresent) {
          throw new RuntimeException(String.join("", "Either specify a CSR [", lambdaInput.getCsr(), "], a certificate ARN [", lambdaInput.getCertificateArn(), "], or neither. Both CSR and certificate ARN options can not be present simultaneously."));
      }

      if ((lambdaInput.getAccessKeyId() != null) && (lambdaInput.getSecretAccessKey() != null)) {
          if (lambdaInput.getSessionToken() == null) {
              throw new RuntimeException("No session token detected for input credentials. Only temporary credentials can be passed in to this Lambda function for security reasons. Obtain temporary credentials from STS for this user/role and try again.");
          }
      }
  }
}
