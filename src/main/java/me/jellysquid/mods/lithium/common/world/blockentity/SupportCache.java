package me.jellysquid.mods.lithium.common.world.blockentity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;

public interface SupportCache {
    /**
     * @return the cached state of the block entity as stored in its field, which is the state the support test is
     * done with. Unlike BlockEntity.getCachedState(), subclasses cannot override this.
     */
    BlockState getCachedStateDirect();

    /**
     * @param type the type of the block entity, as BlockEntity.getType() returns it at the time of the call
     * @return whether the type supports the cached state of the block entity. The result of the test is reused until
     * the cached state, the type or the tags change.
     */
    boolean isSupported(BlockEntityType<?> type);
}
