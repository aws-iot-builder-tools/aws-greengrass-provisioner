package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.ImmutableVirtualTarEntry;
import com.awslabs.aws.greengrass.provisioner.data.VirtualTarEntry;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ArchiveHelper;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarOutputStream;

import javax.inject.Inject;
import java.io.*;
import java.util.List;
import java.util.Optional;

public class BasicArchiveHelper implements ArchiveHelper {
    @Inject
    public BasicArchiveHelper() {
    }

    @Override
    public void addVirtualTarEntry(Optional<List<VirtualTarEntry>> virtualTarEntries, String filename, byte[] content, int permissions) {
        if (!virtualTarEntries.isPresent()) {
            // This makes it safe to attempt to add entries to archives the user hasn't requested without crashing
            return;
        }

        addVirtualTarEntry(virtualTarEntries.get(), filename, content, permissions);
    }

    @Override
    public void addVirtualTarEntry(List<VirtualTarEntry> virtualTarEntries, String filename, byte[] content, int permissions) {
        virtualTarEntries.add(createVirtualTarEntry(filename, content, permissions));
    }

    @Override
    public VirtualTarEntry createVirtualTarEntry(String filename, byte[] content, int permissions) {
        TarEntry tarEntry = new TarEntry(TarHeader.createHeader(filename, content.length, System.currentTimeMillis() / 1000, false, permissions));

        InputStream inputStream;

        inputStream = new ByteArrayInputStream(content);

        return ImmutableVirtualTarEntry.builder()
                .content(content)
                .filename(filename)
                .permissions(permissions)
                .tarEntry(tarEntry)
                .inputStream(inputStream)
                .build();
    }

    @Override
    public Optional<ByteArrayOutputStream> tar(Optional<List<VirtualTarEntry>> virtualTarEntryList) throws IOException {
        if (!virtualTarEntryList.isPresent()) {
            // This makes it safe to attempt to build archives the user hasn't requested without crashing
            return Optional.empty();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create a TarOutputStream
        TarOutputStream out = new TarOutputStream(baos);

        // Loop through all of the entries and write them into the byte array output stream
        for (VirtualTarEntry virtualTarEntry : virtualTarEntryList.get()) {
            // Put the tar entry/header information for this file (does not write the content!)
            out.putNextEntry(virtualTarEntry.getTarEntry());

            // Grab the file and write its actual contents to the stream
            BufferedInputStream origin = new BufferedInputStream(virtualTarEntry.getInputStream());

            int count;
            byte[] data = new byte[2048];

            while ((count = origin.read(data)) != -1) {
                out.write(data, 0, count);
            }

            out.flush();
            origin.close();
        }

        out.close();

        return Optional.of(baos);
    }
}
