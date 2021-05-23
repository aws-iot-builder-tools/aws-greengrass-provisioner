package com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@JsonTypeName("Deploy")
public class DeployResponse extends BaseResponse {

    @Override
    public RequestEventType getEventType() { return RequestEventType.Deploy; }

    @JsonProperty(value="groupId", required = true)
    private String groupId;

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }

}
