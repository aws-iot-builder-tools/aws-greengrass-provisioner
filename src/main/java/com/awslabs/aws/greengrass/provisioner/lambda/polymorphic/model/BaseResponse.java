package com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;


@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "eventType",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProvisionResponse.class, name = "Provision"),
        @JsonSubTypes.Type(value = DeployRequest.class, name = "Deploy"),
})
public abstract class BaseResponse {

    @JsonProperty(value="eventType", required = true)
    private RequestEventType eventType;
    
    public abstract RequestEventType getEventType();

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }
}
