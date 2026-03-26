package nl.theepicblock.resourcelocatorapi.impl;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import nl.theepicblock.resourcelocatorapi.ResourceLocatorApi;
import nl.theepicblock.resourcelocatorapi.api.AssetContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.IdentifierException;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.Tuple;

public class CompositeResourcePack implements AssetContainer {
    /**
     * All packs contained in this compositepack
     */
    private final Set<PackResources> resourcePacks = new LinkedHashSet<>();
    /**
     * For every namespace, this map contains a list of {@link PackResources}'s that contain assets in that namespace. For optimization purposes.
     */
    private final Map<String, List<PackResources>> packsPerNamespace = new HashMap<>();
    /**
     * The {@link PackType} that this resource pack is. (data packs are a type of resource pack)
     */
    private final PackType type;

    public CompositeResourcePack(PackType type) {
        this.type = type;
    }

    public void append(Pack packProfile) {
        append(packProfile.open());
    }

    /**
     * Adds a {@link PackResources} to the composite pack, making all of its assets available
     */
    public void append(PackResources pack) {
        if (!resourcePacks.contains(pack)) {
            resourcePacks.add(pack);

            for (var namespace : pack.getNamespaces(type)) {
                packsPerNamespace.computeIfAbsent(namespace, value -> new ArrayList<>()).add(pack);
            }
        }
    }

    @Override
    public @Nullable IoSupplier<InputStream> getAsset(String namespace, String path) {
        var packs = packsPerNamespace.get(namespace);
        if (packs == null) return null;

        Identifier id;
        try {
            id = Identifier.fromNamespaceAndPath(namespace, path);
        } catch (IdentifierException e) {
            ResourceLocatorApi.LOGGER.warn("Trying to retrieve asset at an invalid location: "+e.getMessage());
            return null;
        }

        for (var pack : packs) {
            var asset = pack.getResource(type, id);
            if (asset != null) {
                return asset;
            }
        }
        return null;
    }

    @Override
    public @NotNull List<IoSupplier<InputStream>> getAllAssets(String namespace, String path) {
        var packs = packsPerNamespace.get(namespace);
        if (packs == null) return Collections.emptyList();

        Identifier id;
        try {
            id = Identifier.fromNamespaceAndPath(namespace, path);
        } catch (IdentifierException e) {
            ResourceLocatorApi.LOGGER.warn("Trying to lookup assets at an invalid location: "+e.getMessage());
            return Collections.emptyList();
        }

        var list = new ArrayList<IoSupplier<InputStream>>();

        for (var pack : packs) {
            var asset = pack.getResource(type, id);
            if (asset != null) {
                list.add(asset);
            }
        }

        return list;
    }

    @Override
    public @NotNull Set<String> getNamespaces() {
        return packsPerNamespace.keySet();
    }

    @Override
    public boolean containsAsset(String namespace, String path) {
        var packs = packsPerNamespace.get(namespace);
        if (packs == null) return false;

        try {
            var id = Identifier.fromNamespaceAndPath(namespace, path);
            for (var pack : packs) {
                if (pack.getResource(type, id) != null) {
                    return true;
                }
            }
        } catch (IdentifierException e) {
            ResourceLocatorApi.LOGGER.warn("Trying to check if an invalid location contains an asset: "+e.getMessage());
        }
        return false;
    }

    @Override
    public @NotNull Set<Tuple<Identifier, IoSupplier<InputStream>>> locateFiles(String prefix) {
        var returnSet = new ObjectArraySet<Tuple<Identifier, IoSupplier<InputStream>>>();
        for (var pack : this.resourcePacks) {
            for (var namespace : pack.getNamespaces(type)) {
                pack.listResources(type, namespace, prefix, (identifier, inputStreamSupplier) -> {
                    returnSet.add(new Tuple<>(identifier, inputStreamSupplier));
                });
            }
        }
        return returnSet;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append("Resource Locator API - Composite resource pack");
        for (var pack : resourcePacks) {
            String name;
            if (pack instanceof MoreContextPack moreContextPack) {
                name = moreContextPack.resourcelocatorapi$getFullName();
            } else {
                name = pack.packId();
            }
            builder.append("\n - ").append(name);
        }

        return builder.toString();
    }

    @Override
    public void close() throws Exception {
        for (var pack : resourcePacks) {
            pack.close();
        }
    }
}

