package org.cloudburstmc.proxypass.network.bedrock.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.extern.log4j.Log4j2;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Log4j2
public class PackDownloader implements AutoCloseable {
    private Map<UUID, Pack> packs;
    private final Path packsPath;
    private final ExecutorService executor;

    public PackDownloader(Path dataPath) {
        this.packsPath = dataPath.resolve("packs");
        try {
            Files.createDirectories(this.packsPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create packs directory", e);
        }

        this.packs = Collections.synchronizedMap(new HashMap<>());

        int availableProcessors = Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(
                Math.max(2, availableProcessors / 2));
    }

    public static class Pack {
        private UUID packId;
        private SortedMap<Integer, ByteBuf> chunks;
        private byte[] contentKey;
        private URL cdnUrl;
        private Path packPath;

        Pack(UUID packId, String contentKey, Path packsPath, String cdnUrl) {
            this.packId = packId;
            this.chunks = new TreeMap<>();
            this.contentKey = contentKey != null ? contentKey.getBytes() : null;
            this.packPath = packsPath.resolve(this.packId.toString() + ".zip");
            try {
                this.cdnUrl = cdnUrl != null && !cdnUrl.isEmpty() ? new URI(cdnUrl).toURL() : null;
            } catch (MalformedURLException | URISyntaxException e) {
                this.cdnUrl = null;
            }
        }

        public void addChunk(int offset, ByteBuf chunk) {
            chunks.put(offset, chunk);
        }

        public void process() {
            if (this.chunks.isEmpty() && this.cdnUrl == null)
                return;
            this.writeStream();
            this.decryptStream();
        }

        private void writeStream() {
            if (this.cdnUrl != null) {
                try (InputStream inputStream = this.cdnUrl.openStream()) {
                    Files.copy(inputStream, this.packPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    log.error("Failed to download pack {} from CDN {}", this.packId, this.cdnUrl, e);
                }
                return;
            }
            try (
                    FileChannel channel = FileChannel.open(
                            packPath,
                            StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING)) {
                for (ByteBuf buf : chunks.values()) {
                    buf.readBytes(channel, buf.readableBytes());
                }
            } catch (IOException e) {
                log.error("Failed to write pack {} to file system", this.packId, e);
            } finally {
                chunks.values().forEach(ByteBuf::release);
            }
        }

        private void decryptStream() {
            try (FileSystem fs = this.orientFileSystem()) {
                this.decryptFileSystem(fs);
            } catch (IOException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                    | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
                log.error("Failed to process pack {}: {}", this.packId, e.getMessage(), e);
            }
        }

        private FileSystem orientFileSystem() throws IOException {
            FileSystem fs = FileSystems.newFileSystem(this.packPath, Collections.emptyMap(), null);
            if (!this.containsManifest(fs) && this.cdnUrl != null) {
                for (Path path : fs.getRootDirectories()) {
                    if (!path.endsWith((".zip")))
                        continue;
                    Files.copy(path, this.packPath, StandardCopyOption.REPLACE_EXISTING);
                    fs.close();
                    fs = FileSystems.newFileSystem(this.packPath, Collections.emptyMap(), null);
                    break;
                }
            }

            if (this.containsManifest(fs)) {
                return fs;
            }

            Path contentDir = null;
            try (
                DirectoryStream<Path> stream = Files.newDirectoryStream(fs.getPath("/"))
            ) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        contentDir = entry;
                        break;
                    }
                }
            }

            if (contentDir != null) {
                this.moveDirectoryContents(contentDir, fs.getPath("/"));
                Files.delete(contentDir);
            }

            return fs;
        }

        private boolean containsManifest(FileSystem fs) {
            return Files.exists(fs.getPath("/manifest.json")) || Files.exists(fs.getPath("/pack_manifest.json"));
        }

        private void moveDirectoryContents(Path source, Path destination) throws IOException {
            List<Path> pathsToMove = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
                for (Path path : stream) {
                    pathsToMove.add(path);
                }
            }

            for (Path path : pathsToMove) {
                Path targetPath = destination.resolve(path.getFileName().toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectory(targetPath);
                    moveDirectoryContents(path, targetPath);
                    Files.delete(path);
                } else {
                    Files.move(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        private void decryptFileSystem(FileSystem fs)
                throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
                InvalidAlgorithmParameterException, IOException, IllegalBlockSizeException, BadPaddingException {
            if (this.contentKey == null || this.contentKey.length == 0 || !Files.exists(fs.getPath("/contents.json")))
                return;

            final Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(this.contentKey, "AES"),
                    new IvParameterSpec(Arrays.copyOfRange(this.contentKey, 0, 16)));
            final ByteBuf contents = Unpooled.wrappedBuffer(Files.readAllBytes(fs.getPath("/contents.json")));
            contents.readerIndex(256);
            final byte[] encryptedContents = new byte[contents.readableBytes()];
            contents.readBytes(encryptedContents);
            Files.write(fs.getPath("/contents.json"), cipher.doFinal(encryptedContents));
            final JsonArray contentsArray = JsonParser.parseString(Files.readString(fs.getPath("/contents.json")))
                    .getAsJsonObject().getAsJsonArray("content");

            for (JsonElement element : contentsArray) {
                final JsonObject contentItem = element.getAsJsonObject();
                if (!contentItem.has("key") || contentItem.get("key").isJsonNull())
                    continue;
                final String key = contentItem.get("key").getAsString();
                final String path = contentItem.get("path").getAsString();
                final Path filePath = fs.getPath("/", path);
                if (!Files.exists(filePath)) {
                    continue;
                }
                if (List.of("manifest.json", "pack_manifest.json", "pack_icon.png", "README.txt").contains(path)) {
                    continue;
                }
                final byte[] encryptedData = Files.readAllBytes(filePath);
                final byte[] keyBytes = key.getBytes(StandardCharsets.ISO_8859_1);
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                        new IvParameterSpec(Arrays.copyOfRange(keyBytes, 0, 16)));
                Files.write(filePath, cipher.doFinal(encryptedData));
            }
        }
    }

    public void registerPack(UUID packId, String cdnUrl, String contentKey) {
        packs.put(packId, new Pack(packId, contentKey, packsPath, cdnUrl));
    }

    public void addChunk(UUID packId, int offset, ByteBuf chunk) {
        Pack pack = packs.get(packId);
        if (pack == null)
            return;
        pack.addChunk(offset, chunk);
    }

    public void processPacks() {
        for (Pack pack : packs.values()) {
            executor.submit(pack::process);
        }
        packs.clear();
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
