package me.jellysquid.mods.lithium.mixin.world.block_entity_ticking.support_cache;

import me.jellysquid.mods.lithium.common.util.TagReloadCounter;
import me.jellysquid.mods.lithium.common.world.blockentity.SupportCache;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements SupportCache {
    @Shadow
    private BlockState cachedState;

    private boolean supportTestResult;
    private BlockState supportTestState;
    private BlockEntityType<?> supportTestType;
    private int supportTestTagReloads;

    @Override
    public BlockState lithium$getCachedStateRaw() {
        return this.cachedState;
    }

    @Override
    public boolean lithium$isSupported(BlockEntityType<?> type) {
        BlockState state = this.cachedState;
        int tagReloads = TagReloadCounter.getTagReloads();

        // Tested when the result is first needed rather than in the constructor, where subclass fields that a modded
        // getType() reads are not set yet. Tested again when the state or the type changed or tags were reloaded,
        // since mods override getType() and extend BlockEntityType.supports, some of them with block tags.
        if (state != this.supportTestState || type != this.supportTestType || tagReloads != this.supportTestTagReloads) {
            this.supportTestResult = type.supports(state);
            this.supportTestState = state;
            this.supportTestType = type;
            this.supportTestTagReloads = tagReloads;
        }

        return this.supportTestResult;
    }
}
