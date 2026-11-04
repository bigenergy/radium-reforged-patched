package me.jellysquid.mods.lithium.mixin.world.block_entity_ticking.support_cache;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fix {@link net.minecraft.block.AbstractBlock.AbstractBlockState#onBlockAdded(World, BlockPos, BlockState, boolean)}
 * being able to change the blockState but the blockEntity's cached state still being set to the old blockState.
 * This only affects hoppers, as hoppers are the only block with a blockentity that also implements onBlockAdded.
 *
 * @author 2No2name
 */
@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    @Shadow
    @Nullable
    public abstract BlockEntity getBlockEntity(BlockPos pos, WorldChunk.CreationType creationType);

    @Redirect(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/BlockEntityProvider;createBlockEntity(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Lnet/minecraft/block/entity/BlockEntity;"
            )
    )
    private BlockEntity createBlockEntityWithCachedStateFix(BlockEntityProvider blockEntityProvider, BlockPos pos, BlockState state) {
        BlockState worldState = this.getBlockState(pos);
        if (worldState.getBlock() == state.getBlock()) {
            return blockEntityProvider.createBlockEntity(pos, worldState);
        }

        // onBlockAdded replaced the block. Passing the other block's state to this block's createBlockEntity breaks
        // mods that read their own properties from it, and keeping the old state would let a block entity of a block
        // that is gone pass the support test and tick. Create the block entity of the block now in the world, if any.
        if (worldState.hasBlockEntity() && worldState.getBlock() instanceof BlockEntityProvider worldProvider) {
            return worldProvider.createBlockEntity(pos, worldState);
        }
        return null;
    }

    // Does not capture the block entity as a local variable: Forge adds two boolean locals to this method, which makes
    // such a capture fail and the game stop on start. The block entity is looked up the way vanilla just did instead.
    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/entity/BlockEntity;setCachedState(Lnet/minecraft/block/BlockState;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void fixCachedState(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        BlockState worldState = this.getBlockState(pos);
        if (worldState != state) {
            BlockEntity blockEntity = this.getBlockEntity(pos, WorldChunk.CreationType.CHECK);
            if (blockEntity != null) {
                //noinspection deprecation
                blockEntity.setCachedState(worldState);
            }
        }
    }
}
