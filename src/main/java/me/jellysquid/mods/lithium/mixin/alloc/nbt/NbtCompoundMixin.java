package me.jellysquid.mods.lithium.mixin.alloc.nbt;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.Map;

/**
 * Use {@link Object2ObjectOpenHashMap} instead of {@link HashMap} to reduce NBT memory consumption and improve
 * iteration speed.
 *
 * @author Maity
 */
@Mixin(NbtCompound.class)
public class NbtCompoundMixin {

    @Shadow
    @Final
    private Map<String, NbtElement> entries;

    @ModifyArg(
            method = "<init>()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtCompound;<init>(Ljava/util/Map;)V")
    )
    private static Map<String, NbtElement> useFasterCollection(Map<String, NbtElement> oldMap) {
        return new Object2ObjectOpenHashMap<>();
    }

    @Redirect(
            method = "<init>()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;",
                    remap = false
            )
    )
    private static HashMap<?, ?> removeOldMapAlloc() {
        return null;
    }

    /**
     * @reason Use faster collection
     * @author Maity
     */
    @Overwrite
    public NbtCompound copy() {
        // [VanillaCopy] HashMap is replaced with Object2ObjectOpenHashMap.
        //
        // The map is filled eagerly instead of being handed a lazy view.
        //
        // The previous implementation passed Maps.transformValues(...) straight
        // into the fastutil constructor. That view is lazy: the constructor asks
        // it for size(), sizes the table from that answer, and only afterwards
        // walks entrySet() — invoking NbtElement::copy once per getValue() call
        // along the way. Any change to the backing map between those two steps,
        // or a second traversal of the same view, leaves the new map with a
        // table that disagrees with its size field.
        //
        // A fastutil map in that state does not fail on construction. It fails
        // later, on the first iteration that walks into the displaced-entry
        // path, as "Cannot invoke ObjectArrayList.get(int) because this.wrapped
        // is null" — far from the copy that actually produced it. See issue #52.
        //
        // Building it explicitly also drops the Guava wrapper allocation and
        // guarantees copy() runs exactly once per value, which is what the
        // vanilla method promises.
        Object2ObjectOpenHashMap<String, NbtElement> map = new Object2ObjectOpenHashMap<>(this.entries.size());

        for (Map.Entry<String, NbtElement> entry : this.entries.entrySet()) {
            map.put(entry.getKey(), entry.getValue().copy());
        }

        return new NbtCompound(map);
    }

    @Mixin(targets = "net.minecraft.nbt.NbtCompound$1")
    static class Type {

        @ModifyVariable(
                method = "read(Ljava/io/DataInput;ILnet/minecraft/nbt/NbtTagSizeTracker;)Lnet/minecraft/nbt/NbtCompound;",
                at = @At(
                        value = "INVOKE_ASSIGN",
                        target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;",
                        remap = false
                )
        )
        private Map<String, NbtElement> useFasterCollection(Map<String, NbtElement> map) {
            return new Object2ObjectOpenHashMap<>();
        }

        @Redirect(
                method = "read(Ljava/io/DataInput;ILnet/minecraft/nbt/NbtTagSizeTracker;)Lnet/minecraft/nbt/NbtCompound;",
                at = @At(
                        value = "INVOKE",
                        target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;",
                        remap = false
                )
        )
        private HashMap<?, ?> removeOldMapAlloc() {
            return null;
        }
    }
}
