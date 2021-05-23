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
        @JsonSubTypes.Type(value = ProvisionRequest.class, name = "Provision"),
        @JsonSubTypes.Type(value = DeployRequest.class, name = "Deploy"),
})
public abstract class BaseRequest {

    @JsonProperty(value="eventType", required = true)
    private RequestEventType eventType;
    // private RequestEventType eventType = RequestEventType.values()[0];      

    @JsonProperty(value="groupName", required = true)
    private String groupName;

    @JsonProperty(value="accessKeyId", required = false)
    private String accessKeyId;

    @JsonProperty(value="secretAccessKey", required = false)
    private String secretAccessKey;

    @JsonProperty(value="sessionToken", required = false)
    private String sessionToken;

    @JsonProperty(value="region", required = false)
    private String region;

    public abstract RequestEventType getEventType();

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }
}
