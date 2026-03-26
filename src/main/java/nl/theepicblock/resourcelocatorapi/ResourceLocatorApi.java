package nl.theepicblock.resourcelocatorapi;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackType;
import nl.theepicblock.resourcelocatorapi.api.AssetContainer;
import nl.theepicblock.resourcelocatorapi.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceLocatorApi {
	public static final Logger LOGGER = LoggerFactory.getLogger("resource-locator-api");

	public static AssetContainer createGlobalAssetContainer() {
		var loader = FabricLoader.getInstance();

		// Create empty composite pack
		var compositePack = new CompositeResourcePack(PackType.CLIENT_RESOURCES);

		if (loader.isModLoaded("advanced_runtime_resource_pack")) {
			ArrpProvider.addPacksBeforeVanilla(compositePack);
		}

		if (loader.isModLoaded("quilt_resource_loader")) {
			QslProvider.addPacks(compositePack);
		} else {
			FapiProvider.addPacks(compositePack);
		}

		if (loader.isModLoaded("advanced_runtime_resource_pack")) {
			ArrpProvider.addPacksAfterVanilla(compositePack);
		}

		RawFileProvider.addPacks(compositePack);

		return compositePack;
	}
}
