package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Reaches a goo type's registry entry from the client, for render and
 * tooltip code that holds a {@link GooType} and no level of its own. The
 * entry comes from the level the client is in, which holds the registry
 * the server synced on join, so a datapack's colors show without a rebuild
 * (decision type-json-colors).
 */
public final class ClientGooTypes {

    private static final String NO_LEVEL = "No client level to resolve goo type ";

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
