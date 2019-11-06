package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.VirtualTarEntry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface ArchiveHelper {
    void addVirtualTarEntry(Optional<List<VirtualTarEntry>> virtualTarEntries, String filename, byte[] content, int permissions);

    void addVirtualTarEntry(List<VirtualTarEntry> virtualTarEntries, String filename, byte[] content, int permissions);

    VirtualTarEntry createVirtualTarEntry(String filename, byte[] content, int permissions);

    Optional<ByteArrayOutputStream> tar(Optional<List<VirtualTarEntry>> virtualTarEntryList) throws IOException;
}
