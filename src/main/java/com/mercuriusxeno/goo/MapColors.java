package com.mercuriusxeno.goo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.level.material.MapColor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Names the vanilla map colors so a type JSON can spell one, {@code "fire"}
 * or {@code "color_red"}, the way {@link MapColor}'s constants are named.
 * Vanilla keeps no registry of map colors, so the names come from the
 * constants themselves.
 */
public final class MapColors {

    private static final String UNKNOWN = "Unknown map color: ";
    private static final Map<String, MapColor> BY_NAME;
    private static final Map<MapColor, String> NAMES;

    /**
     * A map color as its lower-case constant name.
     */
    public static final Codec<MapColor> CODEC = Codec.STRING.comapFlatMap(MapColors::byName, MapColors::nameOf);

    static {
        Map<String, MapColor> byName = new HashMap<>();
        try {
            for (Field field : MapColor.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == MapColor.class) {
                    byName.put(field.getName().toLowerCase(Locale.ROOT), (MapColor) field.get(null));
                }
            }
        } catch (IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
        BY_NAME = Collections.unmodifiableMap(byName);
        NAMES = invert(BY_NAME);
    }

    private MapColors() {
    }

    /**
     * @param name a constant name, any case
     * @return the color, or an error naming the text that matched nothing
     */
    public static DataResult<MapColor> byName(String name) {
        MapColor color = BY_NAME.get(name.toLowerCase(Locale.ROOT));
        return color == null ? DataResult.error(() -> UNKNOWN + name) : DataResult.success(color);
    }

    /**
     * @param color a vanilla map color
     * @return its lower-case constant name, or its id as text for a color no constant holds
     */
    public static String nameOf(MapColor color) {
        String name = NAMES.get(color);
        return name != null ? name : Integer.toString(color.id);
    }

    private static Map<MapColor, String> invert(Map<String, MapColor> byName) {
        Map<MapColor, String> names = new HashMap<>();
        byName.forEach((name, color) -> names.put(color, name));
        return Collections.unmodifiableMap(names);
    }
}
