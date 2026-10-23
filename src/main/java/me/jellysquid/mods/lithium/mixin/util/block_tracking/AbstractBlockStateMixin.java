package me.jellysquid.mods.lithium.mixin.util.block_tracking;

import me.jellysquid.mods.lithium.common.block.BlockStateFlagHolder;
import me.jellysquid.mods.lithium.common.block.BlockStateFlags;
import me.jellysquid.mods.lithium.common.block.TrackedBlockStatePredicate;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class AbstractBlockStateMixin implements BlockStateFlagHolder {
    private int flags;

    @Inject(method = "initShapeCache", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        this.initFlags();
    }

    private void initFlags() {
        //Read the flags before setting the latch: another thread may already be inside
        //BlockStateFlags' static initializer (chunk sections touch it off the server thread), and
        //setting the latch while it is still constructing its predicates makes that initializer
        //throw, which leaves the class permanently unusable. Reading the field first blocks until
        //the initializer has finished on whichever thread started it.
        TrackedBlockStatePredicate[] trackedFlags = BlockStateFlags.FLAGS;
        TrackedBlockStatePredicate.FULLY_INITIALIZED.set(true);

        int flags = 0;

        for (int i = 0; i < trackedFlags.length; i++) {
            //noinspection ConstantConditions
            if (trackedFlags[i].test((BlockState) (Object) this)) {
                flags |= 1 << i;
            }
        }

        this.flags = flags;
    }

    @Override
    public int getAllFlags() {
        return this.flags;
    }
}
