package nl.theepicblock.resourcelocatorapi.impl;

import net.fabricmc.fabric.impl.resource.pack.ModResourcePackCreator;

public class FapiProvider {
    public static void addPacks(CompositeResourcePack pack) {
        ModResourcePackCreator.CLIENT_RESOURCE_PACK_PROVIDER.loadPacks(pack::append);
    }
}
