package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * Reaches a goo type's registry entry from the client, for render and
 * tooltip code that holds a {@link GooType} and no level of its own. The
 * entry comes from the level the client is in, which holds the registry
 * the server synced on join, so a datapack's colors show without a rebuild
 * (decision type-json-colors).
 */
public final class ClientGooTypes {

    private static final String NO_LEVEL = "No client level to resolve goo type ";
    /**
     * RGB answered for a key no entry stands behind, so untinted goo reads white.
     */
    private static final int UNRESOLVED_COLOR = 0xFFFFFF;

    private ClientGooTypes() {
    }

    /**
     * The entry for a type in the level the client is in.
     *
     * @param type the goo type
     * @return its registry entry
     * @throws IllegalStateException when the client is in no level
     */
    public static GooTypeDefinition definition(GooType type) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            throw new IllegalStateException(NO_LEVEL + type.getId());
        }
        return type.holder(level.registryAccess()).value();
    }

    /**
     * The entry for a type key in the level the client is in, for render
     * code holding a datapack key rather than an enum value.
     *
     * @param key the goo type's registry key
     * @return its registry entry, or null when the client is in no level or the key resolves to nothing
     */
    public static @Nullable GooTypeDefinition definition(ResourceKey<GooTypeDefinition> key) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.registryAccess().get(key).map(Holder::value).orElse(null);
    }

    /**
     * @param key the goo type's registry key
     * @return the type's one color, the highlight of its entry, or white where the key resolves to nothing
     */
    public static int color(ResourceKey<GooTypeDefinition> key) {
        GooTypeDefinition definition = definition(key);
        return definition == null ? UNRESOLVED_COLOR : GooColors.get(definition);
    }

    /**
     * @param type the goo type
     * @return the radial menu RGB of its entry
     */
    public static int wheel(GooType type) {
        return GooColors.wheel(definition(type));
    }

    /**
     * @param type the goo type
     * @return the hovered radial segment RGB of its entry
     */
    public static int bright(GooType type) {
        return GooColors.bright(definition(type));
    }

    /**
     * @param type the goo type
     * @return the aim arc, ghost fill and fade wall RGB of its entry
     */
    public static int highlight(GooType type) {
        return GooColors.highlight(definition(type));
    }

    /**
     * @param type the goo type
     * @return the wireframe and outline RGB of its entry
     */
    public static int edge(GooType type) {
        return GooColors.edge(definition(type));
    }

    /**
     * @param type the goo type
     * @return the type's one color, the highlight of its entry
     */
    public static int color(GooType type) {
        return GooColors.get(definition(type));
    }
}
