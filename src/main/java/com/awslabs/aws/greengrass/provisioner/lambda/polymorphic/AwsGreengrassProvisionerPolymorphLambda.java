package com.awslabs.aws.greengrass.provisioner.lambda.polymorphic;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
// import java.util.HashSet;
import java.util.stream.Collectors;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model.BaseRequest;
import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model.BaseResponse;
import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.service.BaseService;

public class AwsGreengrassProvisionerPolymorphLambda implements RequestStreamHandler {
    ObjectMapper mapper = ObjectMapperFactory.create();
    
    @Inject
    Set<BaseService> baseServices;

    @Inject
    public AwsGreengrassProvisionerPolymorphLambda() {
    }

	@Override
	public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
		
		BaseRequest req = null;
		try {
			req = mapper.readValue(inputStream, BaseRequest.class);
			context.getLogger().log("Request is " + mapper.writeValueAsString(req));
		} catch (IOException e) {
			e.printStackTrace();
		}
		// BaseResponse resp = new AwsGreengrassProvisionerPolymorphLambda().process(req, context);

        AwsGreengrassProvisionerPolymorphLambda awsGreengrassProvisionerPolymorphLambda = AwsGreengrassProvisioner.getInjector().awsGreengrassProvisionerPolymorphLambda();

        BaseResponse resp = awsGreengrassProvisionerPolymorphLambda.process(req, context);
		mapper.writeValue(outputStream, resp);
	}

	public BaseResponse process(BaseRequest req, Context context) {

        List<Optional<BaseResponse>> responses = baseServices.stream()
                .map(lambdaService -> lambdaService.execute(req, context))
                .filter(result -> result.isPresent())
                .collect(Collectors.toList());
        try {
            System.out.println("The response is --> " + mapper.writeValueAsString(responses.get(0).get()));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return responses.get(0).get();
    }    
}
