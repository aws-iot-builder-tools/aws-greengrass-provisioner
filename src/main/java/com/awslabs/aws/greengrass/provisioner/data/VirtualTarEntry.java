package com.awslabs.aws.greengrass.provisioner.data;

import org.immutables.value.Value;
import org.kamranzafar.jtar.TarEntry;

import java.io.InputStream;

@Value.Immutable
public abstract class VirtualTarEntry {
    public abstract byte[] getContent();

    public abstract String getFilename();

    public abstract int getPermissions();

    public abstract TarEntry getTarEntry();

    public abstract InputStream getInputStream();
}
