package me.jellysquid.mods.lithium.mixin.ai.poi;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Map;

/**
 * Replaces the backing map type with a faster collection type which uses reference equality.
 */
@Mixin(targets = { "net/minecraftforge/registries/GameData$PointOfInterestTypeCallbacks" })
public class PointOfInterestTypesMixin {
    @ModifyArg(method = "onCreate", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/registries/IForgeRegistryInternal;setSlaveMap(Lnet/minecraft/util/Identifier;Ljava/lang/Object;)V"), index = 1)
    private Object changeMapType(Object obj) {
        return new Reference2ReferenceOpenHashMap<>((Map<?, ?>)obj);
    }
}
