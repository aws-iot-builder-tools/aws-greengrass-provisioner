package com.awslabs.aws.greengrass.provisioner.lambda.polymorphic.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@JsonTypeName("Provision")
public class ProvisionRequest extends BaseRequest {

    @Override
    public RequestEventType getEventType() { return RequestEventType.Provision; }

    @JsonProperty(value = "coreRoleName", required = true)
    private String coreRoleName;

    @JsonProperty(value = "corePolicyName", required = true)
    private String corePolicyName;

    @JsonProperty(value = "csr", required = false)
    private String csr;

    @JsonProperty(value = "certificateArn", required = false)
    private String certificateArn;

    @JsonProperty(value = "serviceRoleExists", required = false)
    private boolean serviceRoleExists;

    @JsonProperty(value = "keyPath", required = false)
    private String keyPath;

    public String getCoreRoleName() {
        return coreRoleName;
    }

    public void setCoreRoleName(String coreRoleName) {
        this.coreRoleName = coreRoleName;
    }    

    public String getCorePolicyName() {
        return corePolicyName;
    }

    public void setCorePolicyName(String corePolicyName) {
        this.corePolicyName = corePolicyName;
    }

    public String getCsr() {
        return csr;
    }

    public void setCsr(String csr) {
        this.csr = csr;
    }

    public String getCertificateArn() {
        return certificateArn;
    }

    public void setCertificateArn(String certificateArn) {
        this.certificateArn = certificateArn;
    }

    public boolean getServiceRoleExists() {
        return serviceRoleExists;
    }

    public void setServiceRoleExists(boolean serviceRoleExists) {
        this.serviceRoleExists = serviceRoleExists;
    }

    public String getKeyPath() {
        return csr;
    }

    public void setKeyPath(String keyPath) {
        this.keyPath = keyPath;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }

}
