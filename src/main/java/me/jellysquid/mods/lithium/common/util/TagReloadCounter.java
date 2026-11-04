package me.jellysquid.mods.lithium.common.util;

import me.jellysquid.mods.lithium.common.LithiumMod;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts how often tags were bound, on the server after loading or reloading data packs and on the client after
 * receiving them. Cached results of tests that mods can make depend on tags are outdated once the count changes.
 */
@Mod.EventBusSubscriber(modid = LithiumMod.MODID)
public final class TagReloadCounter {
    private static final AtomicInteger TAG_RELOADS = new AtomicInteger();

    private TagReloadCounter() {
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        TAG_RELOADS.incrementAndGet();
    }

    public static int getTagReloads() {
        return TAG_RELOADS.get();
    }
}
