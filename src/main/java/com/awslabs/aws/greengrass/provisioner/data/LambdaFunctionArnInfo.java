package com.awslabs.aws.greengrass.provisioner.data;

import lombok.Builder;
import lombok.Data;

import java.util.Optional;

@Data
@Builder
public class LambdaFunctionArnInfo {
    @Builder.Default
    private final Optional<String> error = Optional.empty();
    private String qualifier;
    private String baseArn;
    private String qualifiedArn;
    private String aliasArn;
}
