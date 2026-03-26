package nl.theepicblock.resourcelocatorapi.mixin;

import net.fabricmc.fabric.impl.resource.pack.ModNioPackResources;
import nl.theepicblock.resourcelocatorapi.impl.MoreContextPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ModNioPackResources.class)
public abstract class MoreContextFapi implements MoreContextPack {
    @Shadow public abstract String packId();

    @Override
    public String resourcelocatorapi$getFullName() {
        return this.packId();
    }
}
