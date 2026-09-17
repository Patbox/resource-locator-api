package nl.theepicblock.resourcelocatorapi.impl;

import net.fabricmc.fabric.impl.resource.pack.BuiltinModPackSource;
import net.fabricmc.fabric.impl.resource.pack.ModNioPackResources;
import net.fabricmc.fabric.impl.resource.pack.ModResourcePackCreator;

public class FapiProvider {
    @SuppressWarnings("UnstableApiUsage") // We knowingly bypass regular resource loading. The relevant code is try-catched so it fails gracefully if internals change
    public static void addPacks(CompositeResourcePack outputPack) {
        ModResourcePackCreator.CLIENT_RESOURCE_PACK_PROVIDER.loadPacks(pack -> {
            var source = pack.getPackSource();
            if (source instanceof BuiltinModPackSource) {
                var packResources = pack.open();
                // Only add if it would be enabled by default
                // This means if a mod adds (for example) a disabled-by-default programmer art pack
                // then that won't be included
                if (packResources instanceof ModNioPackResources mPack && mPack.getActivationType().isEnabledByDefault()) {
                    packResources.forEach(outputPack::append);
                } else {
                    packResources.close();
                }
            } else {
                outputPack.append(pack);
            }
        });
    }
}
