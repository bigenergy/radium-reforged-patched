package me.jellysquid.mods.lithium.common.util;

import me.jellysquid.mods.lithium.common.LithiumMod;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts how often tags were loaded: when the server has loaded or reloaded its data packs and when the client has
 * received the tags of a server. Cached results of tests that mods can make depend on tags are outdated when the
 * count has changed.
 */
@Mod.EventBusSubscriber(modid = LithiumMod.MODID)
public class TagReloadCounter {
    private static final AtomicInteger TAG_RELOADS = new AtomicInteger();

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        TAG_RELOADS.incrementAndGet();
    }

    public static int getTagReloads() {
        return TAG_RELOADS.get();
    }
}
