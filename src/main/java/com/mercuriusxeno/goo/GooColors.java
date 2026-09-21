package com.mercuriusxeno.goo;

import net.minecraft.core.HolderLookup;

/**
 * Reads a goo type's four RGB channels from its registry entry: wheel
 * (radial menu), bright (hovered segment), highlight (aim arc, ghost fill,
 * fade walls) and edge (wireframe contours). Decision type-json-colors: the
 * channels live in the type JSON, so this holds no file and no cache.
 */
public final class GooColors {

    private GooColors() {
    }

    /**
     * @param type the goo type's registry entry
     * @return the radial menu RGB
     */
    public static int wheel(GooTypeDefinition type) {
        return type.wheel();
    }

    /**
     * @param type the goo type's registry entry
     * @return the hovered radial segment RGB
     */
    public static int bright(GooTypeDefinition type) {
        return type.bright();
    }

    /**
     * @param type the goo type's registry entry
     * @return the aim arc, ghost fill and fade wall RGB
     */
    public static int highlight(GooTypeDefinition type) {
        return type.highlight();
    }

    /**
     * @param type the goo type's registry entry
     * @return the wireframe and outline RGB
     */
    public static int edge(GooTypeDefinition type) {
        return type.edge();
    }

    /**
     * The type's one color where a caller names no channel: the highlight,
     * which is the channel most read.
     *
     * @param type the goo type's registry entry
     * @return the highlight RGB
     */
    public static int get(GooTypeDefinition type) {
        return highlight(type);
    }

    /**
     * Resolves an enum-era type through the registry and answers its
     * highlight, for callers holding a registry access and no entry.
     *
     * @param registries the registry access of the level in hand
     * @param type       the goo type
     * @return the highlight RGB
     */
    public static int get(HolderLookup.Provider registries, GooType type) {
        return get(type.holder(registries).value());
    }
}
