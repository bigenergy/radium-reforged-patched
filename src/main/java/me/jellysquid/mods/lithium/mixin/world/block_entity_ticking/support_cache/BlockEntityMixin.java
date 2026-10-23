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
    public BlockState getCachedStateDirect() {
        return this.cachedState;
    }

    @Override
    public boolean isSupported(BlockEntityType<?> type) {
        BlockState state = this.cachedState;
        int tagReloads = TagReloadCounter.getTagReloads();

        // The test is done when its result is first needed, not while the block entity is constructed, and again
        // when the cached state or the type has changed or tags were reloaded: mods override BlockEntity.getType()
        // and extend BlockEntityType.supports, some of them with block tags
        if (state != this.supportTestState || type != this.supportTestType ||
                tagReloads != this.supportTestTagReloads) {
            this.supportTestResult = type.supports(state);
            this.supportTestState = state;
            this.supportTestType = type;
            this.supportTestTagReloads = tagReloads;
        }

        return this.supportTestResult;
    }
}
