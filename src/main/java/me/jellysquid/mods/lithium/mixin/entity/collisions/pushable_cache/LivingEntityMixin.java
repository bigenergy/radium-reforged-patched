package me.jellysquid.mods.lithium.mixin.entity.collisions.pushable_cache;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Caches {@link LivingEntity#isPushable()} for the duration of a game tick.
 *
 * WHY. Cramming tests are quadratic: every entity in a section asks every
 * other entity whether it can be pushed. With N entities in one section
 * that is N*N calls to isPushable() per tick, and the answer does not
 * depend on who is asking - only on the entity being asked. Computing it
 * once per entity per tick turns N*N into N.
 *
 * WHY IT MATTERS MORE WITH MODS. In vanilla isPushable() is cheap:
 *
 *   isAlive() && !isSpectator() && !isClimbing()
 *
 * but isClimbing() is a popular mixin target. Profiling a modded server
 * showed Botania hooking it to look up a Curios slot, which walks the
 * capability dispatcher on every single call - 0.61% of the server thread
 * spent inside cramming tests alone, plus Pehkui on the same method.
 * The existing unpushable_cramming optimization does not help there: its
 * mask only hides entities standing in climbable blocks, and it only
 * engages once at least half of a section is unpushable, which never
 * happens in a mob farm where everything is pushable.
 *
 * CORRECTNESS. The cached answer can be at most one tick stale. In
 * practice that means an entity which dies partway through a tick stays
 * pushable until the end of it. Cramming and pushing are already
 * approximate - entities resolve overlaps over several ticks - so a
 * single tick of delay is not observable.
 *
 * The cache is deliberately keyed on world time rather than the entity's
 * own age: age only advances while the entity ticks, so a non-ticking
 * entity would keep a stale answer forever.
 *
 * The HEAD injector returns early on a hit, which means other mixins on
 * isPushable() do not run for the rest of that tick. That is intended -
 * their result was already folded into the value we cached on the first
 * call this tick.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    @Unique
    private long lithiumPushableCacheTime = Long.MIN_VALUE;

    @Unique
    private boolean lithiumPushableCacheValue;

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "isPushable()Z", at = @At("HEAD"), cancellable = true)
    private void lithiumUseCachedPushability(CallbackInfoReturnable<Boolean> cir) {
        if (this.getWorld().getTime() == this.lithiumPushableCacheTime) {
            cir.setReturnValue(this.lithiumPushableCacheValue);
        }
    }

    /**
     * Also fires on the early return inserted above when the injectors
     * happen to be applied in that order. Harmless: it rewrites the same
     * value under the same timestamp.
     */
    @Inject(method = "isPushable()Z", at = @At("RETURN"))
    private void lithiumCachePushability(CallbackInfoReturnable<Boolean> cir) {
        this.lithiumPushableCacheTime = this.getWorld().getTime();
        this.lithiumPushableCacheValue = cir.getReturnValueZ();
    }
}
