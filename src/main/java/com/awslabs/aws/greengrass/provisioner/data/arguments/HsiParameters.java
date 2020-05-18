package com.awslabs.aws.greengrass.provisioner.data.arguments;

import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
public abstract class HsiParameters {
    public abstract Optional<String> getPkcs11EngineForCurl();

    public abstract Optional<String> getOpenSSLEngine();

    public abstract String getP11Provider();

    public abstract String getSlotLabel();

    public abstract String getSlotUserPin();

    @Value.Default
    public String getPkcsPath() {
        // This is the default PKCS11 value that many systems use
        return "pkcs11:object=iotkey;type=private";
    }

    public String getCurlPkcsPath() {
        String token = String.join("", "token=", getSlotLabel());
        String pinValue = String.join("", "pin-value=", getSlotUserPin());

        return String.join(";", getPkcsPath(), token, pinValue);
    }
}
