package me.jellysquid.mods.lithium.common.entity;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Skipping the equipment comparison relies on every equipment change going through the vanilla setter, which is
 * where the change gets recorded. Modded entities that override the equipment getter or setter usually keep their
 * equipment somewhere else (Ice and Fire dragons, Rats) and change it without calling that setter, so they have to
 * keep the vanilla comparison.
 */
public final class VanillaEquipmentStorage {
    private static final ClassValue<Boolean> USED_BY = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return compute(type);
        }
    };

    private VanillaEquipmentStorage() {
    }

    public static boolean isUsedBy(Class<?> type) {
        return USED_BY.get(type);
    }

    private static boolean compute(Class<?> type) {
        // Vanilla overrides call the vanilla setter, so only classes added by mods need to be inspected.
        for (Class<?> clazz = type; clazz != null && !clazz.getName().startsWith("net.minecraft."); clazz = clazz.getSuperclass()) {
            try {
                if (declaresEquipmentAccessor(clazz)) {
                    return false;
                }
            } catch (LinkageError e) {
                // A signature references a class that is not available on this side, so the class cannot be inspected.
                return false;
            }
        }

        return true;
    }

    // Matched by signature rather than by name, since method names differ between development and production mappings.
    private static boolean declaresEquipmentAccessor(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isPrivate(modifiers) || method.isSynthetic()) {
                continue;
            }

            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1 && params[0] == EquipmentSlot.class && method.getReturnType() == ItemStack.class) {
                return true;
            }
            if (params.length == 2 && params[0] == EquipmentSlot.class && params[1] == ItemStack.class && method.getReturnType() == void.class) {
                return true;
            }
        }

        return false;
    }
}
