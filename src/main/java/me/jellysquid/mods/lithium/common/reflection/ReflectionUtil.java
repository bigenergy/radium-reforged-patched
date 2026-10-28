package me.jellysquid.mods.lithium.common.reflection;

import me.jellysquid.mods.lithium.common.config.RadiumConfig;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ReflectionUtil {
    private static final Logger LOGGER = LogManager.getLogger("Radium Class Analysis");

    /**
     * A class is analysed once per block state and once per entity type, so a single broken class would otherwise
     * repeat the same warning hundreds of times. Report each class once.
     */
    private static final Set<String> REPORTED_CLASSES = ConcurrentHashMap.newKeySet();

    public static boolean hasMethodOverride(Class<?> clazz, Class<?> superclass, boolean fallbackResult, String methodName, Class<?>... methodArgs) {
        while (clazz != null && clazz != superclass && superclass.isAssignableFrom(clazz)) {
            try {
                clazz.getDeclaredMethod(methodName, methodArgs);
                return true;
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            } catch (NoClassDefFoundError | RuntimeException error) {
                reportUnanalysableClass(clazz, error);
                return fallbackResult;
            } catch (Throwable e) {
                final String crashedClass = clazz.getName();
                CrashReport crashReport = CrashReport.create(e, "Radium Class Analysis");
                CrashReportSection crashReportSection = crashReport.addElement(e.getClass().toString() + " when getting declared methods.");
                crashReportSection.add("Analyzed class", crashedClass);
                crashReportSection.add("Analyzed method name", methodName);
                crashReportSection.add("Analyzed method args", methodArgs);

                throw new CrashException(crashReport);
            }
        }
        return false;
    }

    private static void reportUnanalysableClass(Class<?> clazz, Throwable error) {
        if (!RadiumConfig.LOG_CLASS_ANALYSIS_ERRORS || !REPORTED_CLASSES.add(clazz.getName())) {
            return;
        }

        LOGGER.warn("Radium Class Analysis Error: Class " + clazz.getName() + " cannot be analysed, because" +
                " getting declared methods crashes with " + error.getClass().getSimpleName() + ": " + error.getMessage() +
                ". This is usually caused by modded" +
                " entities declaring methods that have a return type or parameter type that is annotated" +
                " with @OnlyIn(Dist.CLIENT). Loading the type is not possible, because" +
                " it only exists in the CLIENT environment. The recommended fix is to annotate the method with" +
                " this argument or return type with the same annotation." +
                " Lithium handles this error by assuming the class cannot be included in some optimizations." +
                " Further errors for this class are not logged; set logging.class_analysis_errors=false" +
                " in config/radium.properties to silence these warnings entirely.");
    }
}
