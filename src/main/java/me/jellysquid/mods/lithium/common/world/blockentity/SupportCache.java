package me.jellysquid.mods.lithium.common.world.blockentity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;

public interface SupportCache {
    /**
     * @return the block entity's cached state as stored in its field. Unlike {@code BlockEntity.getCachedState()},
     * which mods override, this is the state that is kept in sync with the world.
     */
    BlockState lithium$getCachedStateRaw();

    /**
     * @param type the type of the block entity, as {@code BlockEntity.getType()} returned it for this tick
     * @return whether the type supports the block entity's cached state. The result is reused until the cached state,
     * the type or the tags change.
     */
    boolean lithium$isSupported(BlockEntityType<?> type);
}
