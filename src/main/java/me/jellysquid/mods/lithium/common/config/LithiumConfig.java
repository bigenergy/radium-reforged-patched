package me.jellysquid.mods.lithium.common.config;

import me.jellysquid.mods.lithium.common.LithiumMod;
import me.jellysquid.mods.lithium.common.compat.worldedit.WorldEditCompat;
import net.caffeinemc.caffeineconfig.AbstractCaffeineConfigMixinPlugin;
import net.caffeinemc.caffeineconfig.CaffeineConfig;
import net.caffeinemc.caffeineconfig.Option;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.forgespi.language.IModInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class LithiumConfig extends AbstractCaffeineConfigMixinPlugin {

    private static final String OPTION_OVERRIDE_KEY = "lithium:options";

    @FunctionalInterface
    private interface ConfigLookup {
        Optional<Object> get(String... path);
    }

    // CaffeineConfig only looks for overrides inside the [[mods]] block, and it expects the option
    // names to arrive as flat keys. Neither holds on Forge: mods declare the table at the root of
    // mods.toml, and TOML expands a dotted key like `mixin.ai.poi` into nested tables. Read both
    // locations ourselves and rebuild the dotted names.
    private void applyModOverrides(CaffeineConfig config) {
        for (ModFileInfo modFile : LoadingModList.get().getModFiles()) {
            List<IModInfo> mods = modFile.getMods();
            if (!mods.isEmpty()) {
                readOverrides(config, mods.get(0).getModId(), modFile::getConfigElement, new String[]{OPTION_OVERRIDE_KEY}, "");
            }
        }

        for (ModInfo mod : LoadingModList.get().getMods()) {
            readOverrides(config, mod.getModId(), mod::getConfigElement, new String[]{OPTION_OVERRIDE_KEY}, "");
        }
    }

    private void readOverrides(CaffeineConfig config, String modId, ConfigLookup lookup, String[] path, String prefix) {
        if (!(lookup.get(path).orElse(null) instanceof Map<?, ?> table)) {
            return;
        }

        for (Map.Entry<?, ?> entry : table.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }

            String name = prefix.isEmpty() ? key : prefix + "." + key;

            if (entry.getValue() instanceof Boolean enabled) {
                applyModOverride(config, modId, name, enabled);
            } else {
                String[] childPath = Arrays.copyOf(path, path.length + 1);
                childPath[path.length] = key;

                readOverrides(config, modId, lookup, childPath, name);
            }
        }
    }

    private void applyModOverride(CaffeineConfig config, String modId, String name, boolean enabled) {
        Option option = config.getOption(name);

        if (option == null) {
            config.getLogger().warn("Mod '{}' attempted to override option '{}', which doesn't exist, ignoring", modId, name);
            return;
        }

        if (!option.isOverrideable()) {
            config.getLogger().warn("Mod '{}' attempted to override option '{}' that is not overrideable, ignoring", modId, name);
            return;
        }

        // Disabling an option wins over enabling it, same as CaffeineConfig does for its own overrides.
        if (!enabled && option.isEnabled()) {
            option.clearModsDefiningValue();
        }

        if (!enabled || option.isEnabled() || option.getDefiningMods().isEmpty()) {
            option.addModOverride(enabled, modId);
        }
    }

    // C2ME ships every module as its own mod id. First id is the Forge port (c2meF),
    // second is the Fabric original as loaded through Sinytra Connector.
    private static final String[] C2ME_NOTICKVD = {"c2me_notickvd", "c2me-notickvd"};
    private static final String[] C2ME_OPTS_CHUNK_ACCESS = {"c2me_opts_chunk_access", "c2me-opts-chunk-access"};

    private static String findLoadedMod(String... modIds) {
        for (String modId : modIds) {
            if (LoadingModList.get().getModFileById(modId) != null) {
                return modId;
            }
        }

        return null;
    }

    private CaffeineConfig applyC2MECompat(CaffeineConfig config) {
        // notickvd keeps chunks past the simulation distance accessible but not ticking, and
        // relies on its redirect in ThreadedAnvilChunkStorage.updateChunkTracking to send them.
        // player_chunk_tick overwrites updatePosition and watches chunks through its own
        // startWatchingChunk, which reads getWorldChunk() and gets null for everything that
        // does not tick, so those chunks are never sent to the client.
        String notickvd = findLoadedMod(C2ME_NOTICKVD);
        if (notickvd != null) {
            config.getOption("mixin.world.player_chunk_tick").addModOverride(false, notickvd);
        }

        // opts_chunk_access injects at HEAD of ServerChunkManager.getChunk to route off-thread
        // requests into its own non-blocking path; chunk_access overwrites that method and drops
        // the injection. Upstream C2ME papered over this with an ASM transformer, but c2meF has
        // removed it, so off-thread chunk requests end up blocking on the server thread again.
        String chunkAccess = findLoadedMod(C2ME_OPTS_CHUNK_ACCESS);
        if (chunkAccess != null) {
            config.getOption("mixin.world.chunk_access").addModOverride(false, chunkAccess);
        }

        return config;
    }

    private CaffeineConfig applyLithiumCompat(CaffeineConfig config) {
        applyModOverrides(config);

        if (LoadingModList.get().getModFileById("ferritecore") != null) { // https://github.com/malte0811/FerriteCore/blob/1.20.0/Fabric/src/main/resources/fabric.mod.json#L38
            config.getOption("mixin.alloc.blockstate").addModOverride(false, "ferritecore");
        }

          if (LoadingModList.get().getModFileById("aperture_innovations") != null) {
            config.getOption("mixin.entity.collisions.movement").addModOverride(false, "aperture_innovations");
        }

        applyC2MECompat(config);

        Option option = config.getOption("mixin.block.hopper.worldedit_compat");
        if (!option.isEnabled() && WorldEditCompat.WORLD_EDIT_PRESENT) {
            option.addModOverride(true, "radium");
        }

        if (!LoadingModList.get().getErrors().isEmpty()) {
            for (Option op : config.getOptions().values()) {
                op.addModOverride(false, "fml-loading-error");
            }
        }

        return config;
    }

    public LithiumConfig() {
        super();

        LithiumMod.CONFIG = this;
    }

    @Override
    protected CaffeineConfig createConfig() {
        CaffeineConfig.Builder builder = CaffeineConfig.builder("Radium")
                .withInfoUrl("https://github.com/jellysquid3/lithium-fabric/wiki/Configuration-File")
                .withSettingsKey("lithium:options");

        // Defines the default rules which can be configured by the user or other mods.
        InputStream defaultPropertiesStream = LithiumConfig.class.getResourceAsStream("/assets/lithium/lithium-mixin-config-default.properties");
        if (defaultPropertiesStream == null) {
            throw new IllegalStateException("Lithium mixin config default properties could not be read!");
        }
        try (BufferedReader propertiesReader = new BufferedReader(new InputStreamReader(defaultPropertiesStream))) {
            Properties properties = new Properties();
            properties.load(propertiesReader);
            properties.forEach((ruleName, enabled) -> builder.addMixinRule((String) ruleName, Boolean.parseBoolean((String) enabled)));
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException("Lithium mixin config default properties could not be read!");
        }
        InputStream dependenciesStream = LithiumConfig.class.getResourceAsStream("/assets/lithium/lithium-mixin-config-dependencies.properties");
        if (dependenciesStream == null) {
            throw new IllegalStateException("Lithium mixin config dependencies could not be read!");
        }
        try (BufferedReader propertiesReader = new BufferedReader(new InputStreamReader(dependenciesStream))) {
            Properties properties = new Properties();
            properties.load(propertiesReader);
            properties.forEach(
                    (o1, o2) -> {
                        String rulename = (String) o1;
                        String dependencies = (String) o2;
                        String[] dependenciesSplit = dependencies.split(",");
                        for (String dependency : dependenciesSplit) {
                            String[] split = dependency.split(":");
                            if (split.length != 2) {
                                return;
                            }
                            String dependencyName = split[0];
                            String requiredState = split[1];
                            builder.addRuleDependency(rulename, dependencyName, Boolean.parseBoolean(requiredState));
                        }
                    }
            );
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException("Lithium mixin config dependencies could not be read!");
        }

        return applyLithiumCompat(builder.build(FMLPaths.CONFIGDIR.get().resolve("lithium.properties")));
    }

    @Override
    protected String mixinPackageRoot() {
        return "me.jellysquid.mods.lithium.mixin.";
    }
}
