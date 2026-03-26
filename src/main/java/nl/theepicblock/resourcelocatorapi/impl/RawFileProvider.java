package nl.theepicblock.resourcelocatorapi.impl;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.IdentifierException;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import nl.theepicblock.resourcelocatorapi.ResourceLocatorApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RawFileProvider {
    public static void addPacks(CompositeResourcePack pack) {
        var modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");

        if (!Files.isDirectory(modsDir)) {
            ResourceLocatorApi.LOGGER.error("Mods folder isn't a directory");
        }

        try (var stream = Files.walk(modsDir, FileVisitOption.FOLLOW_LINKS)) {
            stream.forEach(path -> {
                if (isValidFile(path)) {
                    try (var inputStream = new FileInputStream(path.toFile());
                        var bufStream = new BufferedInputStream(inputStream)) {
                        handleFile(pack, bufStream, path.toString());
                    } catch (FileNotFoundException e) {
                        ResourceLocatorApi.LOGGER.error("File seems to have disappeared "+path, e);
                    } catch (IOException e) {
                        ResourceLocatorApi.LOGGER.error("IO exception reading "+path, e);
                    }
                }
            });
        } catch (IOException e) {
            ResourceLocatorApi.LOGGER.error("IO exception whilst trying to walk files", e);
        }
    }

    /**
     * @author FabricMC
     */
    private static boolean isValidFile(Path path) {
        if (!Files.isRegularFile(path)) return false;

        try {
            if (Files.isHidden(path)) return false;
        } catch (IOException e) {
            ResourceLocatorApi.LOGGER.error("Error checking if file is hidden: "+path, e);
            return false;
        }

        String fileName = path.getFileName().toString();

        return fileName.endsWith(".jar") && !fileName.startsWith(".");
    }

    private static void handleFile(CompositeResourcePack pack, InputStream stream, String path) {
        var newPack = new BufferResourcePack("Rawfile ("+path+")");
        try {
            var zipStream = new ZipInputStream(stream);
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                if (entry.getName().startsWith("META-INF/jars/") || entry.getName().startsWith("/META-INF/jars/")) {
                    handleFile(pack, zipStream, entry.getName() + " in " + path);
                } else if (entry.getName().startsWith("assets/") || entry.getName().startsWith("/assets/")) {
                    var buffer = readMod(zipStream);
                    var split = entry.getName().substring(1).split("/", 3);
                    if (split.length == 3) {
                        var id = Identifier.tryBuild(split[1], split[2]);
                        if (id != null) {
                            newPack.putAsset(id, buffer);
                        }
                    }
                }
            }
        } catch (IOException e) {
            ResourceLocatorApi.LOGGER.error("Error whilst reading zip "+path, e);
        }
        pack.append(newPack);
    }

    /**
     * @author FabricMC
     */
    static ByteBuffer readMod(InputStream is) throws IOException {
        int available = is.available();
        boolean availableGood = available > 1;
        byte[] buffer = new byte[availableGood ? available : 30_000];
        int offset = 0;
        int len;

        while ((len = is.read(buffer, offset, buffer.length - offset)) >= 0) {
            offset += len;

            if (offset == buffer.length) {
                if (availableGood) {
                    int val = is.read();
                    if (val < 0) break;

                    availableGood = false;
                    buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, 30_000));
                    buffer[offset++] = (byte) val;
                } else {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
            }
        }

        return ByteBuffer.wrap(buffer, 0, offset);
    }

    private static final class BufferResourcePack extends AbstractPackResources implements MoreContextPack {
        private final Map<Identifier, ByteBuffer> assets = new HashMap<>();
        private final Map<Identifier, ByteBuffer> data = new HashMap<>();

        private BufferResourcePack(String name) {
            super(new PackLocationInfo(name, Component.literal(name), PackSource.DEFAULT, Optional.empty()));
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getRootResource(String... segments) {
            if (segments.length >= 3) {
                return this.createSupplier(segments[0].equals("assets") ? PackType.CLIENT_RESOURCES : PackType.SERVER_DATA,
                        Identifier.fromNamespaceAndPath(segments[0], String.join("/", List.of(segments).subList(2, segments.length)))
                        );
            }
            return null;
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
            return this.createSupplier(type, id);
        }

        @Override
        public void listResources(PackType type, String namespace, String prefix, ResourceOutput consumer) {
            var map = type == PackType.CLIENT_RESOURCES ? this.assets : this.data;
            map.forEach((path, buf) -> {
                if (path.getNamespace().equals(namespace) && path.getPath().startsWith(prefix)) {
                    try {
                        consumer.accept(path, () -> new ByteBufferInputStream(buf));
                    } catch (IdentifierException e) {
                        ResourceLocatorApi.LOGGER.warn("Invalid path in pack, ignoring: "+ path);
                    }
                }
            });
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return (type == PackType.CLIENT_RESOURCES ? this.assets : this.data).keySet().stream()
                    .map(Identifier::getNamespace)
                    .collect(Collectors.toSet());
        }

        @Override
        public void close() {

        }

        private @Nullable IoSupplier<InputStream> createSupplier(PackType type, Identifier identifier) {
            var buf = (type == PackType.CLIENT_RESOURCES ? this.assets : this.data).get(identifier);
            if (buf == null) return null;
            return () -> new ByteBufferInputStream(buf);
        }

        public void putAsset(Identifier id, ByteBuffer buf) {
            this.assets.put(id, buf);
        }


        public void putData(Identifier id, ByteBuffer buf) {
            this.data.put(id, buf);
        }

        @Override
        public String resourcelocatorapi$getFullName() {
            return this.packId();
        }
    }

    private static final class ByteBufferInputStream extends InputStream {
        private final ByteBuffer buffer;
        private int pos;

        private ByteBufferInputStream(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        /**
         * @author FabricMC
         */
        @Override
        public int read() throws IOException {
            if (pos >= buffer.limit()) {
                return -1;
            } else {
                return buffer.get(pos++) & 0xff;
            }
        }

        /**
         * @author FabricMC
         */
        @Override
        public int read(byte @NotNull [] b, int off, int len) throws IOException {
            int rem = buffer.limit() - pos;

            if (rem <= 0) {
                return -1;
            } else {
                len = Math.min(len, rem);
                System.arraycopy(buffer.array(), pos, b, off, len);
                pos += len;

                return len;
            }
        }
    }
}
