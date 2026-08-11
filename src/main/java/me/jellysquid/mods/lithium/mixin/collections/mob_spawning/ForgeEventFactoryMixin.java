package me.jellysquid.mods.lithium.mixin.collections.mob_spawning;

import me.jellysquid.mods.lithium.common.world.PotentialSpawnsExtended;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.level.LevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ForgeEventFactory.class)
public class ForgeEventFactoryMixin {
    /**
     * @author embeddedt
     * @reason Avoid the overhead of re-creating a pool in the event that the spawn list was not changed.
     */
    @Inject(method = "getPotentialSpawns",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/collection/Pool;of(Ljava/util/List;)Lnet/minecraft/util/collection/Pool;"),
            locals = LocalCapture.CAPTURE_FAILHARD,
            cancellable = true
    )
    private static void reusePoolIfPossible(WorldAccess level, SpawnGroup category, BlockPos pos, Pool<SpawnSettings.SpawnEntry> oldList, CallbackInfoReturnable<Pool<SpawnSettings.SpawnEntry>> cir, LevelEvent.PotentialSpawns event) {
        if(!((PotentialSpawnsExtended)event).radium$wasListModified()) {
            cir.setReturnValue(oldList);
        }
    }
}
