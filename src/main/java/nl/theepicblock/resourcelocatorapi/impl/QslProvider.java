package nl.theepicblock.resourcelocatorapi.impl;


import java.util.ArrayList;
import net.minecraft.server.packs.PackResources;

public class QslProvider {
    public static void addPacks(CompositeResourcePack pack) {
        var packs = new ArrayList<PackResources>();
        //ResourceLoaderImpl.appendModResourcePacks(packs, ResourceType.CLIENT_RESOURCES, null);
        packs.forEach(pack::append);

        //ModResourcePackProvider.CLIENT_RESOURCE_PACK_PROVIDER.register(pack::append);
    }
}
