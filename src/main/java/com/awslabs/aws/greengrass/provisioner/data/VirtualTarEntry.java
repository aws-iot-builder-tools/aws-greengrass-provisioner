package com.awslabs.aws.greengrass.provisioner.data;

import lombok.Builder;
import lombok.Data;
import org.kamranzafar.jtar.TarEntry;

import java.io.InputStream;

@Data
@Builder
public class VirtualTarEntry {
    private byte[] content;

    private String filename;

    private int permissions;

    private TarEntry tarEntry;

    private InputStream inputStream;
}
