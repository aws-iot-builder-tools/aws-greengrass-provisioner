package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.HsiBootstrapArguments;
import com.awslabs.aws.greengrass.provisioner.data.arguments.HsiVendor;
import io.vavr.control.Try;

public interface HsiBootstrapArgumentHelper extends ArgumentHelper<HsiBootstrapArguments> {
    default HsiVendor getHsiVendor(String hsiVendorString) {
        return Try.of(() -> HsiVendor.valueOf(hsiVendorString))
                .recover(IllegalArgumentException.class, throwable -> throwDescriptiveHsiVendorException(hsiVendorString))
                .get();
    }

    default void throwDescriptiveHsiVendorException() {
        throwDescriptiveHsiVendorException(null);
    }

    default HsiVendor throwDescriptiveHsiVendorException(String hsiVendorString) {
        StringBuilder stringBuilder = new StringBuilder();

        if (hsiVendorString != null) {
            stringBuilder.append("[");
            stringBuilder.append(hsiVendorString);
            stringBuilder.append("] is not a valid HSI vendor.");
            stringBuilder.append("\r\n");
        } else {
            stringBuilder.append("No HSI vendor was specified.");
            stringBuilder.append("\r\n");
        }

        stringBuilder.append(getValidHsiVendorOptions());

        throw new RuntimeException(stringBuilder.toString());
    }

    default String getValidHsiVendorOptions() {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("Valid options are: ");
        stringBuilder.append(HsiVendor.getList());

        return stringBuilder.toString();
    }
}
