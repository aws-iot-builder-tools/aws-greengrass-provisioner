package com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.service;

import com.amazonaws.services.lambda.runtime.Context;

import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model.DeployRequest;
import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model.DeployResponse;
import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.ObjectMapperFactory;
import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model.BaseRequest;
import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model.BaseResponse;

import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.data.arguments.Arguments;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
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

public class DeployService implements BaseService {
  public static final String CONFIG_JSON_KEY = "config/config.json";
  
  @Inject
  IoHelper ioHelper;
  @Inject
  JsonHelper jsonHelper;

  @Inject
  public DeployService() {
  }

  @Override
  public Optional<BaseResponse> execute(BaseRequest request, Context context) {
    Optional<BaseResponse> result = Optional.empty();

    if(request instanceof DeployRequest){
      ObjectMapper mapper = ObjectMapperFactory.create();
      try {
        System.out.println("Im " + this.getClass().getName() + " and the input is " + mapper.writeValueAsString(request));
      } catch (JsonProcessingException e) {
        e.printStackTrace();
      }

      Map oemJson = this.innerHandleRequest((DeployRequest) request, context);

      DeployResponse res = new DeployResponse();

      res.setGroupId((String)oemJson.get("config/group-id.txt"));

      result=Optional.of(res);
    }

    return result;
  }

  private Map innerHandleRequest(DeployRequest lambdaInput, Context context) {
    validateRequiredParameters(lambdaInput);

    setEnvironmentVariablesForCredentialsIfNecessary(lambdaInput);

    File outputFile = Try.of(() -> ioHelper.getTempFile("oem", "json")).get();
    List<String> args = new ArrayList<>();

    args.add(Arguments.SHORT_GROUP_NAME_OPTION);
    args.add(lambdaInput.getGroupName());

    args.add(DeploymentArguments.SHORT_DEPLOYMENT_CONFIG_OPTION);
    if (lambdaInput.getDeploymentConfigFilename() != null) {
        args.add(lambdaInput.getDeploymentConfigFilename());
    } else {
        args.add("/opt/ggp-config/deployments/node-webserver.conf");
    }

    args.add(DeploymentArguments.LONG_OEM_JSON_OUTPUT_OPTION);
    args.add(outputFile.getAbsolutePath());

    AwsGreengrassProvisioner.main(args.toArray(new String[args.size()]));

    Map<String, String> oemJson = jsonHelper.fromJson(Map.class, ioHelper.readFileAsString(outputFile).getBytes());

    return oemJson;
  }

  private void setEnvironmentVariablesForCredentialsIfNecessary(DeployRequest lambdaInput) {
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

  private void validateRequiredParameters(DeployRequest lambdaInput) {
      if (lambdaInput.getGroupName() == null) {
          throw new RuntimeException("No group name specified");
      }

      if (lambdaInput.getDeploymentConfigFilename() == null) {
          throw new RuntimeException("No deployment config file specified");
      }
  }
}
