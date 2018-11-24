package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.VirtualTarEntry;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ArchiveHelper;
import lombok.val;
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
            return;
        }

        virtualTarEntries.get().add(createVirtualTarEntry(filename, content, permissions));
    }

    @Override
    public VirtualTarEntry createVirtualTarEntry(String filename, byte[] content, int permissions) {
        TarEntry tarEntry = new TarEntry(TarHeader.createHeader(filename, content.length, System.currentTimeMillis() / 1000, false, permissions));

        InputStream inputStream;

        inputStream = new ByteArrayInputStream(content);

        return VirtualTarEntry.builder()
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
            return Optional.empty();
        }

        val baos = new ByteArrayOutputStream();

        // Create a TarOutputStream
        TarOutputStream out = new TarOutputStream(baos);

        for (VirtualTarEntry virtualTarEntry : virtualTarEntryList.get()) {
            out.putNextEntry(virtualTarEntry.getTarEntry());
            BufferedInputStream origin = new BufferedInputStream(virtualTarEntry.getInputStream());
            int count;
            byte data[] = new byte[2048];

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
