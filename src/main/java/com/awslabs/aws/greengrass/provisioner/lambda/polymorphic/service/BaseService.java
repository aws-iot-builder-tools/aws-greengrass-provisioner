package com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.service;

import com.amazonaws.services.lambda.runtime.Context;

import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model.BaseRequest;
import com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model.BaseResponse;

import java.util.Optional;

public interface BaseService {

  Optional<BaseResponse> execute(BaseRequest request, Context context);
}
