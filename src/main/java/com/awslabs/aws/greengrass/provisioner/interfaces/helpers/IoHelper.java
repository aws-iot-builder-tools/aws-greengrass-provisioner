package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import org.apache.commons.codec.binary.Hex;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Scanner;
import java.util.UUID;

public interface IoHelper {
    default void writeFile(String filename, byte[] contents) {
        try (FileOutputStream out = new FileOutputStream(filename)) {
            out.write(contents);
        } catch (FileNotFoundException e) {
            throw new UnsupportedOperationException(e);
        } catch (IOException e) {
            throw new UnsupportedOperationException(e);
        }

        makeWritable(filename);
    }

    default String getUuid() {
        return UUID.randomUUID().toString();
    }

    default byte[] readFile(String filename) {
        try {
            return Files.readAllBytes(Paths.get(filename));
        } catch (IOException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    default String readFileAsString(File file) {
        byte[] data = readFile(file);

        return new String(data);
    }

    default byte[] readFile(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    default byte[] readFile(URL url) {
        try (InputStream inputStream = url.openStream()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];

            int len = inputStream.read(buffer);

            while (len != -1) {
                baos.write(buffer, 0, len);
                len = inputStream.read(buffer);
            }

            return baos.toByteArray();
        } catch (IOException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    default void download(String url, File file) {
        try {
            // From: http://stackoverflow.com/a/921400
            URL website = new URL(url);
            ReadableByteChannel rbc = Channels.newChannel(website.openStream());
            FileOutputStream fos = new FileOutputStream(file);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch (IOException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    default String download(String url) {
        try {
            return new Scanner(new URL(url).openStream(), "UTF-8").useDelimiter("\\A").next();
        } catch (IOException e) {
            throw new UnsupportedOperationException(e);
        }
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

    default byte[] serializeObject(Object object) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(object);
            oos.close();

            return baos.toByteArray();
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    default Object deserializeObject(byte[] bytes) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bais);
            Object object = ois.readObject();
            ois.close();

            return object;
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
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
            throw new UnsupportedOperationException(e);
        }
    }
}
