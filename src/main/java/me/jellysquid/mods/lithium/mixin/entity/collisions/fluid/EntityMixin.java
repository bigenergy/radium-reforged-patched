package me.jellysquid.mods.lithium.mixin.entity.collisions.fluid;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import me.jellysquid.mods.lithium.common.block.BlockCountingSection;
import me.jellysquid.mods.lithium.common.block.BlockStateFlags;
import me.jellysquid.mods.lithium.common.entity.FluidCachingEntity;
import me.jellysquid.mods.lithium.common.util.Pos;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraftforge.common.extensions.IForgeEntity;
import net.minecraftforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

@Mixin(value = Entity.class, priority = 900)
public abstract class EntityMixin implements FluidCachingEntity, IForgeEntity {
    @Shadow
    public abstract Box getBoundingBox();

    @Shadow
    public World world;

    @Shadow
    protected Object2DoubleMap<FluidType> forgeFluidTypeHeight;

    @Shadow
    @Deprecated
    protected Object2DoubleMap<TagKey<Fluid>> fluidHeight;

    @Shadow
    abstract void checkWaterState();

    @Shadow
    public abstract boolean isInFluidType();

    @Shadow
    @Nullable
    public abstract Entity getVehicle();

    @Shadow
    public float fallDistance;

    @Shadow
    public abstract void extinguish();

    private boolean radium$isInModdedFluid;

    /**
     * @author 2No2Name, embeddedt
     * @reason Skip computing fluid heights if we know none of the relevant chunk sections contain any fluids.
     */
    @Inject(
            method = "updateFluidHeightAndDoFluidPushing",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;isPushedByFluids()Z",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 0
    )
    public void tryShortcutFluidPushing(CallbackInfo ci, Box box, int x1, int x2, int y1, int y2, int z1, int z2, double zero) {
        int chunkX1 = x1 >> 4;
        int chunkZ1 = z1 >> 4;
        int chunkX2 = ((x2 - 1) >> 4);
        int chunkZ2 = ((z2 - 1) >> 4);
        int chunkYIndex1 = Math.max(Pos.SectionYIndex.fromBlockCoord(this.world, y1), Pos.SectionYIndex.getMinYSectionIndex(this.world));
        int chunkYIndex2 = Math.min(Pos.SectionYIndex.fromBlockCoord(this.world, y2 - 1), Pos.SectionYIndex.getMaxYSectionIndexInclusive(this.world));
        for (int chunkX = chunkX1; chunkX <= chunkX2; chunkX++) {
            for (int chunkZ = chunkZ1; chunkZ <= chunkZ2; chunkZ++) {
                Chunk chunk = this.world.getChunk(chunkX, chunkZ);
                for (int chunkYIndex = chunkYIndex1; chunkYIndex <= chunkYIndex2; chunkYIndex++) {
                    ChunkSection section = chunk.getSectionArray()[chunkYIndex];
                    if (((BlockCountingSection) section).mayContainAny(BlockStateFlags.ANY_FLUID)) {
                        //fluid found, cannot skip code
                        return;
                    }
                }
            }
        }

        //side effects of not finding a fluid.
        if(!this.forgeFluidTypeHeight.isEmpty()) {
            // only call clear() if map contains anything, because array maps use naive clear
            this.forgeFluidTypeHeight.clear();
        }
        ci.cancel();
    }

    /**
     * @author embeddedt
     * @reason Track when the entity is in a non-vanilla fluid type. This flag is used to skip looping through
     * fluid types when entities are only in vanilla fluids.
     */
    @Inject(method = "setFluidTypeHeight", at = @At("RETURN"))
    private void markInModdedFluid(FluidType type, double height, CallbackInfo ci) {
        if(!type.isAir() && !type.isVanilla()) {
            this.radium$isInModdedFluid = true;
        }
    }

    /**
     * @author embeddedt
     * @reason Early-exit when not in a modded fluid, avoid streams & allocations for other calculations
     */
    @Overwrite
    protected boolean updateWaterState() {
        this.fluidHeight.clear();
        this.forgeFluidTypeHeight.clear();
        this.radium$isInModdedFluid = false;
        this.checkWaterState();

        if (this.radium$isInModdedFluid && !(this.getVehicle() instanceof BoatEntity)) {
            float fallDistanceModifier = Float.MAX_VALUE;
            boolean canExtinguish = false;

            for(FluidType type : this.forgeFluidTypeHeight.keySet()) {
                if(!type.isAir() && !type.isVanilla()) {
                    fallDistanceModifier = Math.min(this.getFluidFallDistanceModifier(type), fallDistanceModifier);
                    canExtinguish |= this.canFluidExtinguish(type);
                }
            }

            if (fallDistanceModifier != Float.MAX_VALUE) {
                this.fallDistance *= fallDistanceModifier;
            }

            if (canExtinguish) {
                this.extinguish();
            }
        }

        return this.isInFluidType();
    }

    /**
     * @author embeddedt
     * @reason early-exit for entities with no fluids (the likely case), avoid streams
     */
    @Overwrite
    public final boolean isInFluidType(BiPredicate<FluidType, Double> predicate, boolean forAllTypes) {
        if(this.forgeFluidTypeHeight.isEmpty()) {
            return false;
        } else {
            ObjectIterator<Object2DoubleMap.Entry<FluidType>> it = Object2DoubleMaps.fastIterator(this.forgeFluidTypeHeight);
            if(forAllTypes) {
                // Check if all fluids match
                while (it.hasNext()) {
                    var entry = it.next();
                    if(!predicate.test(entry.getKey(), entry.getDoubleValue())) {
                        return false;
                    }
                }
                return true;
            } else {
                // Check if any fluid matches
                while (it.hasNext()) {
                    var entry = it.next();
                    if(predicate.test(entry.getKey(), entry.getDoubleValue())) {
                        return true;
                    }
                }
                return false;
            }

        }
    }
}
