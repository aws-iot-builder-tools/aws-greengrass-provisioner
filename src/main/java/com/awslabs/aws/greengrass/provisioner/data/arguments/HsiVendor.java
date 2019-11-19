package com.awslabs.aws.greengrass.provisioner.data.arguments;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum HsiVendor {
    SoftHSM2("pkcs11", Constants.STANDARD_PKCS11_URL),
    Zymbit("zymkey_ssl", Constants.STANDARD_PKCS11_URL);

    private final String engineName;
    private final String pkcs11Url;

    HsiVendor(String engineName, String pkcs11Url) {
        this.engineName = engineName;
        this.pkcs11Url = pkcs11Url;
    }

    public static String getList() {
        return Arrays.stream(HsiVendor.values())
                .map(HsiVendor::name)
                .collect(Collectors.joining(", "));
    }

    public String getEngineName() {
        return engineName;
    }

    public String getPkcs11Url() {
        return pkcs11Url;
    }

    private static class Constants {
        public static final String STANDARD_PKCS11_URL = "pkcs11:token=greengrass;object=iotkey;type=private;pin-value=1234";
    }
}
