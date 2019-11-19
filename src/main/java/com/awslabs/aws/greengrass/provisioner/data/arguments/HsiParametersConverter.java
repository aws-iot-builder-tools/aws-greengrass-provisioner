package com.awslabs.aws.greengrass.provisioner.data.arguments;

import com.beust.jcommander.IStringConverter;

import java.util.Arrays;
import java.util.List;

import static io.vavr.API.*;

public class HsiParametersConverter implements IStringConverter<HsiParameters> {
    public static final String P_11_PROVIDER = "P11Provider";
    public static final String SLOT_LABEL = "slotLabel";
    public static final String SLOT_USER_PIN = "slotUserPin";
    public static final String OPEN_SSLENGINE = "OpenSSLEngine";
    public static final String PKCS11ENGINE_FOR_CURL = "pkcs11EngineForCurl";
    public static final List<String> optionList = Arrays.asList(P_11_PROVIDER, SLOT_LABEL, SLOT_LABEL, OPEN_SSLENGINE);

    @Override
    public HsiParameters convert(String stringValue) {
        List<String> keyValuePairs = Arrays.asList(stringValue.split(","));

        ImmutableHsiParameters.Builder immutableHsiParametersBuilder = ImmutableHsiParameters.builder();

        for (String keyValuePair : keyValuePairs) {
            List<String> keyAndValue = Arrays.asList(keyValuePair.split("="));

            if (keyAndValue.size() != 2) {
                throw new RuntimeException("Key value pair in HSI parameters [" + keyValuePair + "] is not valid");
            }

            String key = keyAndValue.get(0);
            String value = keyAndValue.get(1);

            Match(key).of(
                    Case($(P_11_PROVIDER::equals), matched -> immutableHsiParametersBuilder.p11Provider(value)),
                    Case($(SLOT_LABEL::equals), matched -> immutableHsiParametersBuilder.slotLabel(value)),
                    Case($(SLOT_USER_PIN::equals), matched -> immutableHsiParametersBuilder.slotUserPin(value)),
                    Case($(OPEN_SSLENGINE::equals), matched -> immutableHsiParametersBuilder.openSSLEngine(value)),
                    Case($(PKCS11ENGINE_FOR_CURL::equals), matched -> immutableHsiParametersBuilder.pkcs11EngineForCurl(value)),
                    // Always fail if there was no match
                    Case($(), matched -> {
                        throw new RuntimeException(String.format("No match for HSI option [%s], valid options are [" + String.join(", ", optionList) + "]", matched));
                    }));
        }

        return immutableHsiParametersBuilder.build();
    }
}
