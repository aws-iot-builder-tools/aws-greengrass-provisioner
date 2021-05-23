package com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@JsonTypeName("Provision")
public class ProvisionResponse extends BaseResponse {

    @Override
    public RequestEventType getEventType() { return RequestEventType.Provision; }

    @JsonProperty(value = "configJson", required = true)
    private String configJson;

    @JsonProperty(value = "rootCaPem", required = true)
    private String rootCaPem;

    @JsonProperty(value = "coreKey", required = true)
    private String coreKey;

    @JsonProperty(value = "coreCrt", required = true)
    private String coreCrt;

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public String getRootCaPem() {
        return rootCaPem;
    }

    public void setRootCaPem(String rootCaPem) {
        this.rootCaPem = rootCaPem;
    }

    public String getCoreKey() {
        return coreKey;
    }

    public void setCoreKey(String coreKey) {
        this.coreKey = coreKey;
    }

    public String getCoreCrt() {
        return coreCrt;
    }

    public void setCoreCrt(String coreCrt) {
        this.coreCrt = coreCrt;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }

}
