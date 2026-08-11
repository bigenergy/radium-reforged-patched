package me.jellysquid.mods.lithium.mixin.chunk.entity_status_iteration;

import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.stream.Stream;

/**
 * Iterate a snapshot of a section's entities while its tracking status changes.
 *
 * <h2>The crash</h2>
 * {@code updateTrackingStatus(ChunkPos, EntityTrackingStatus)} walks every
 * section of the chunk and, for each, streams its entities straight out of the
 * live backing list, handing them to {@code startTicking} / {@code stopTicking}
 * / {@code startTracking} / {@code stopTracking}.
 * <p>
 * Those four call into {@code LevelCallback}, which is where the world — and
 * every mod listening to it — gets to react. Vanilla itself discards entities
 * from there: {@code onTrackingEnd} kills an ender dragon's sub-entities. A
 * discard removes the entity from the very list being streamed, and the
 * iterator throws {@link java.util.ConcurrentModificationException}, taking the
 * whole server tick with it.
 * <p>
 * Observed on a 21-player 1.20.1 server:
 * <pre>
 * java.util.ConcurrentModificationException
 *   at java.util.ArrayList$Itr.checkForComodification
 *   at PersistentEntitySectionManager.lambda$updateChunkStatus$6(line 175)
 *   at PersistentEntitySectionManager.updateChunkStatus(line 160)
 * </pre>
 *
 * <h2>Why fix it here</h2>
 * Radium does not cause this — the mixins it already applies to that class are
 * plain {@code @Accessor}s. But Radium is a server-side performance fork, and a
 * crash that kills the tick loop under load is exactly the kind of fragility a
 * server fork is expected to absorb. The cost is one array copy per section per
 * status change, which happens on chunk load and unload, not per tick.
 *
 * <h2>Scope</h2>
 * Only the iteration is made safe. Whatever a callback does to the entity
 * remains its own business — the entity is still discarded, it simply no longer
 * corrupts the traversal that triggered it.
 */
@Mixin(ServerEntityManager.class)
public class ServerEntityManagerMixin<T extends EntityLike> {

    /**
     * The target is the LAMBDA, not {@code updateTrackingStatus} itself.
     *
     * {@code updateTrackingStatus(ChunkPos, EntityTrackingStatus)} does nothing
     * but hand a lambda to {@code forEach} over the chunk's sections. Every call
     * to {@code stream()} — all four of them — lives inside that lambda, and
     * Mixin does not descend into lambdas from the enclosing method. Targeting
     * the outer method resolves no instructions at all, which is exactly what
     * the IDE reports: "Cannot resolve any target instructions in target class".
     *
     * The lambda is unmapped, so it keeps its intermediary name. Verified by
     * disassembling the project's own Yarn-mapped jar:
     *
     *   private void method_31825(EntityTrackingStatus, EntityTrackingSection)
     *       -> swapStatus, shouldTick, shouldTrack, and 4x stream()
     *
     * Being an intermediary name, this is version-fragile: a mappings bump can
     * renumber it. If this mixin ever stops applying, disassemble
     * ServerEntityManager and look for the private method that calls both
     * swapStatus and stream().
     */
    @Redirect(
            method = "method_31825(Lnet/minecraft/world/entity/EntityTrackingStatus;Lnet/minecraft/world/entity/EntityTrackingSection;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/EntityTrackingSection;stream()Ljava/util/stream/Stream;"
            )
    )
    private Stream<T> iterateSnapshot(EntityTrackingSection<T> section) {
        // toList() copies eagerly. Streaming from the copy leaves callbacks free
        // to add or remove entities from the section without disturbing us.
        List<T> snapshot = section.stream().toList();
        return snapshot.stream();
    }
}
