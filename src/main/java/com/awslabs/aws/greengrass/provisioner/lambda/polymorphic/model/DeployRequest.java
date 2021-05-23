package com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@JsonTypeName("Deploy")
public class DeployRequest extends BaseRequest {

    @Override
    public RequestEventType getEventType() { return RequestEventType.Deploy; }

    @JsonProperty(value = "deploymentConfigFilename", required = true)
    private String deploymentConfigFilename;

    public String getDeploymentConfigFilename() {
        return deploymentConfigFilename;
    }

    public void setDeploymentConfigFilename(String deploymentConfigFilename) {
        this.deploymentConfigFilename = deploymentConfigFilename;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }

}
