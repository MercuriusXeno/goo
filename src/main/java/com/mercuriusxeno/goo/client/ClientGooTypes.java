package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * Reaches a goo type's registry entry from the client, for render and
 * tooltip code that holds a type key and no level of its own. The entry
 * comes from the level the client is in, which holds the registry the
 * server synced on join, so a datapack's colors show without a rebuild
 * (decision type-json-colors). A key no entry stands behind, or a client in
 * no level, reads white rather than failing a frame.
 */
public final class ClientGooTypes {

    /**
     * RGB answered for a key no entry stands behind, so untinted goo reads white.
     */
    private static final int UNRESOLVED_COLOR = 0xFFFFFF;

    private ClientGooTypes() {
    }

    /**
     * The entry for a type key in the level the client is in.
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
     * @return the radial menu RGB of its entry
     */
    public static int wheel(ResourceKey<GooTypeDefinition> key) {
        GooTypeDefinition definition = definition(key);
        return definition == null ? UNRESOLVED_COLOR : GooColors.wheel(definition);
    }

    /**
     * @param key the goo type's registry key
     * @return the hovered radial segment RGB of its entry
     */
    public static int bright(ResourceKey<GooTypeDefinition> key) {
        GooTypeDefinition definition = definition(key);
        return definition == null ? UNRESOLVED_COLOR : GooColors.bright(definition);
    }

    /**
     * @param key the goo type's registry key
     * @return the aim arc, ghost fill and fade wall RGB of its entry
     */
    public static int highlight(ResourceKey<GooTypeDefinition> key) {
        GooTypeDefinition definition = definition(key);
        return definition == null ? UNRESOLVED_COLOR : GooColors.highlight(definition);
    }

    /**
     * @param key the goo type's registry key
     * @return the wireframe and outline RGB of its entry
     */
    public static int edge(ResourceKey<GooTypeDefinition> key) {
        GooTypeDefinition definition = definition(key);
        return definition == null ? UNRESOLVED_COLOR : GooColors.edge(definition);
    }

    /**
     * @param key the goo type's registry key
     * @return the type's one color, the highlight of its entry
     */
    public static int color(ResourceKey<GooTypeDefinition> key) {
        return highlight(key);
    }
}
