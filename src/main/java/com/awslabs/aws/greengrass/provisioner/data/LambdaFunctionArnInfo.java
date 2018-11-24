package com.awslabs.aws.greengrass.provisioner.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LambdaFunctionArnInfo {
    private String qualifier;
    private String baseArn;
    private String qualifiedArn;
    private String aliasArn;
}
