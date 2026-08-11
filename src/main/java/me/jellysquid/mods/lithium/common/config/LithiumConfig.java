package me.jellysquid.mods.lithium.common.config;

import me.jellysquid.mods.lithium.common.LithiumMod;
import me.jellysquid.mods.lithium.common.compat.worldedit.WorldEditCompat;
import net.caffeinemc.caffeineconfig.AbstractCaffeineConfigMixinPlugin;
import net.caffeinemc.caffeineconfig.CaffeineConfig;
import net.caffeinemc.caffeineconfig.Option;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

public class LithiumConfig extends AbstractCaffeineConfigMixinPlugin {

    private CaffeineConfig applyLithiumCompat(CaffeineConfig config) {
        if (LoadingModList.get().getModFileById("ferritecore") != null) { // https://github.com/malte0811/FerriteCore/blob/1.20.0/Fabric/src/main/resources/fabric.mod.json#L38
            config.getOption("mixin.alloc.blockstate").addModOverride(false, "ferritecore");
        }

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
