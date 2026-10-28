package me.jellysquid.mods.lithium.common.config;

import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Settings that are not mixin rules and therefore do not belong in {@code lithium.properties}.
 * <p>
 * This reads its own file instead of registering a Forge {@code ModConfig}, because Forge only loads those in the
 * CONFIG_LOAD phase, while the values here are needed earlier: block states initialize during mod construction, and
 * chunk worker threads reach the same code while the server is loading.
 */
public final class RadiumConfig {
    private static final String FILE_NAME = "radium.properties";

    private static final String CLASS_ANALYSIS_ERRORS_KEY = "logging.class_analysis_errors";

    private static final Properties PROPERTIES = read();

    /**
     * Whether to report modded classes that cannot be inspected. Each class is reported once either way, but packs
     * whose offending mods are abandoned can turn the warnings off completely.
     */
    public static final boolean LOG_CLASS_ANALYSIS_ERRORS = getBoolean(CLASS_ANALYSIS_ERRORS_KEY, true);

    private RadiumConfig() {
    }

    private static boolean getBoolean(String key, boolean fallback) {
        String value = PROPERTIES.getProperty(key);
        if (value == null) {
            return fallback;
        }

        value = value.trim();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }

        LogManager.getLogger("Radium").warn("Invalid value '{}' for option '{}' in {}, using {}", value, key, FILE_NAME, fallback);
        return fallback;
    }

    private static Properties read() {
        Properties properties = new Properties();

        Path path;
        try {
            path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        } catch (Throwable t) {
            // No config directory available, which happens in environments that do not run the Forge loader.
            return properties;
        }

        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
            } catch (IOException e) {
                LogManager.getLogger("Radium").warn("Could not read {}, using defaults", FILE_NAME, e);
            }
        } else {
            writeDefaults(path);
        }

        return properties;
    }

    private static void writeDefaults(Path path) {
        Properties defaults = new Properties();
        defaults.setProperty(CLASS_ANALYSIS_ERRORS_KEY, "true");

        try (OutputStream out = Files.newOutputStream(path)) {
            defaults.store(out, "Radium settings that are not mixin rules. Mixin rules live in lithium.properties."
                    + System.lineSeparator()
                    + "# " + CLASS_ANALYSIS_ERRORS_KEY + ": warn about modded classes Radium cannot inspect."
                    + System.lineSeparator()
                    + "# Each such class is only reported once; set this to false to drop the warnings entirely.");
        } catch (IOException e) {
            LogManager.getLogger("Radium").warn("Could not write {}", FILE_NAME, e);
        }
    }
}
