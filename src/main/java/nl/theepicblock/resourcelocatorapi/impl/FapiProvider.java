package nl.theepicblock.resourcelocatorapi.impl;

import net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator;

public class FapiProvider {
    public static void addPacks(CompositeResourcePack pack) {
        ModResourcePackCreator.CLIENT_RESOURCE_PACK_PROVIDER.register(pack::append);
    }
}
