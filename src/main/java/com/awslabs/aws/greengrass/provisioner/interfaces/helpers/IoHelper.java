package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;
import com.awslabs.aws.greengrass.provisioner.data.KeysAndCertificate;
import io.vavr.control.Try;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

public interface IoHelper {
    default void writeFile(String filename, byte[] contents) {
        Try.withResources(() -> new FileOutputStream(filename))
                .of(out -> {
                    out.write(contents);
                    return null;
                });

        makeWritable(filename);
    }

    default String getUuid() {
        return UUID.randomUUID().toString();
    }

    default byte[] readFile(String filename) {
        return Try.of(() -> Files.readAllBytes(Paths.get(filename))).get();
    }

    default String readFileAsString(File file) {
        byte[] data = readFile(file);

        return new String(data);
    }

    default byte[] readFile(File file) {
        return Try.of(() -> Files.readAllBytes(file.toPath())).get();
    }

    default byte[] readFile(URL url) {
        return Try.withResources(() -> url.openStream())
                .of(inputStream -> {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];

                    int len = inputStream.read(buffer);

                    while (len != -1) {
                        baos.write(buffer, 0, len);
                        len = inputStream.read(buffer);
                    }

                    return baos.toByteArray();
                })
                .get();
    }

    default void download(String url, File file) {
        Try.of(() -> {
            // From: http://stackoverflow.com/a/921400
            URL website = new URL(url);
            ReadableByteChannel rbc = Channels.newChannel(website.openStream());
            FileOutputStream fos = new FileOutputStream(file);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            return null;
        });
    }

    default String download(String url) {
        return Try.of(() -> new Scanner(new URL(url).openStream(), "UTF-8").useDelimiter("\\A").next()).get();
    }

    default void makeExecutable(String filename) {
        File file = new File(filename);
        // When using Docker the executable flag gets set as root only, setting the second parameter to false makes it executable by all
        file.setExecutable(true, false);
    }

    default void makeReadable(String filename) {
        File file = new File(filename);
        // When using Docker the read flag gets set as root only, setting the second parameter to false makes it readable by all
        file.setReadable(true, false);
    }

    default void makeWritable(String filename) {
        File file = new File(filename);
        // When using Docker the write gets set as root only, setting the second parameter to false makes it writable by all
        file.setWritable(true, false);
    }

    default void createDirectoryIfNecessary(String path) {
        new File(path).mkdirs();
        makeWritable(path);
    }

    default boolean exists(String path) {
        return new File(path).exists();
    }

    default String serializeObject(Object object, JsonHelper jsonHelper) {
        return jsonHelper.toJson(object);
    }

    default Object deserializeObject(byte[] bytes, JsonHelper jsonHelper) {
        return Try.of(() -> jsonHelper.fromJson(KeysAndCertificate.class, bytes))
                .onFailure(exception -> Try.of(() -> {
                    // Try again
                    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                    ObjectInputStream ois = new ObjectInputStream(bais);
                    Object object = ois.readObject();
                    ois.close();

                    return object;
                })).get();
    }

    default String calcSHA1(URL url) {
        try (InputStream input = url.openStream()) {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int len = input.read(buffer);

            while (len != -1) {
                sha1.update(buffer, 0, len);
                len = input.read(buffer);
            }

            return Hex.encodeHexString(sha1.digest()).toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    default String serializeKeys(CreateKeysAndCertificateResponse createKeysAndCertificateResponse, JsonHelper jsonHelper) {
        KeysAndCertificate keysAndCertificate = KeysAndCertificate.builder()
                .certificateArn(createKeysAndCertificateResponse.certificateArn())
                .certificateId(createKeysAndCertificateResponse.certificateId())
                .certificatePem(createKeysAndCertificateResponse.certificatePem())
                .keyPair(createKeysAndCertificateResponse.keyPair()).build();

        return serializeObject(keysAndCertificate, jsonHelper);
    }

    default KeysAndCertificate deserializeKeys(byte[] readFile, JsonHelper jsonHelper) {
        Object object = deserializeObject(readFile, jsonHelper);

        if (object instanceof KeysAndCertificate) {
            return (KeysAndCertificate) object;
        }

        if (object instanceof CreateKeysAndCertificateResponse) {
            return KeysAndCertificate.from((CreateKeysAndCertificateResponse) object);
        }

        if (object instanceof CreateKeysAndCertificateResult) {
            return KeysAndCertificate.from((CreateKeysAndCertificateResult) object);
        }

        throw new RuntimeException("Couldn't deserialize keys.  This is a bug.");
    }

    default Optional<InputStream> extractTar(String tarFile, String filenameToExtract) {
        try (final TarArchiveInputStream tarIn = new TarArchiveInputStream(new FileInputStream(new File(tarFile)))) {
            return getInputStreamFromTar(tarIn, filenameToExtract);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    default Optional<InputStream> extractTarGz(String tarGzFile, String filenameToExtract) {
        try (final TarArchiveInputStream tarIn = new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(new File(tarGzFile))))) {
            return getInputStreamFromTar(tarIn, filenameToExtract);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    default Optional<InputStream> getInputStreamFromTar(TarArchiveInputStream tarIn, String filenameToExtract) throws IOException {
        TarArchiveEntry tarEntry = tarIn.getNextTarEntry();

        while (tarEntry != null) {
            String currentFileName = tarEntry.getName();

            if (!currentFileName.endsWith(filenameToExtract)) {
                tarEntry = tarIn.getNextTarEntry();
                continue;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            int length = 0;

            byte[] buffer = new byte[16384];

            while ((length = tarIn.read(buffer)) != -1) {
                baos.write(buffer, 0, length);
            }

            return Optional.of(new ByteArrayInputStream(baos.toByteArray()));
        }

        return Optional.empty();
    }

    /**
     * Returns a temp file that is deleted when the JVM exits
     *
     * @param prefix
     * @param suffix
     * @return
     */
    default File getTempFile(String prefix, String suffix) {
        return Try.of(() -> {
            File innerTempFile = File.createTempFile(prefix, suffix);
            innerTempFile.deleteOnExit();
            return innerTempFile;
        }).get();
    }

    default void sleep(int milliseconds) {
        Try.of(() -> {
            Thread.sleep(milliseconds);
            return null;
        });
    }
}
