package com.mercuriusxeno.goo;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * The types of goo that make up everything in the world.
 */
public enum GooType implements StringRepresentable {
    AEON("aeon"),
    BLAZE("blaze"),
    CRYSTAL("crystal"),
    ENDER("ender"),
    FROST("frost"),
    GLOW("glow"),
    HEX("hex"),
    LEAF("leaf"),
    METAL("metal"),
    NETHER("nether"),
    PULSE("pulse"),
    ROCK("rock"),
    SHROOM("shroom"),
    TYPHOON("typhoon"),
    UNSTABLE("unstable"),
    VITAL("vital");

    /**
     * Codec that serializes a GooType as its string id.
     */
    public static final com.mojang.serialization.Codec<GooType> CODEC =
            com.mojang.serialization.Codec.STRING.xmap(
                    id -> {
                        GooType t = fromId(id);
                        if (t == null) {
                            throw new IllegalArgumentException("Unknown goo type: " + id);
                        }
                        return t;
                    },
                    GooType::getId);
    /**
     * Translation key prefix for goo type display names.
     */
    private static final String TRANSLATION_PREFIX = "goo.type.";
    private final String id;

    GooType(String id) {
        this.id = id;
    }

    @org.jspecify.annotations.Nullable
    public static GooType fromId(String id) {
        for (GooType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Returns the lowercase string identifier for this goo type.
     *
     * @return the goo type ID string
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the RGB color used for rendering this goo type: the highlight
     * channel of its registry entry.
     *
     * @param registries the registry access of the level in hand
     * @return the RGB color int
     */
    public int getColor(HolderLookup.Provider registries) {
        return GooColors.get(registries, this);
    }

    /**
     * Returns the key addressing this type's entry in the goo type registry.
     *
     * @return the registry key sharing this type's id
     */
    public ResourceKey<GooTypeDefinition> key() {
        return GooTypes.bundled(id);
    }

    /**
     * Resolves this type's registry entry from a level's registry access, so
     * enum-era call sites reach the datapack definition while the enum stands.
     *
     * @param registries the registry access of the level in hand
     * @return the loaded entry for this type
     * @throws IllegalStateException when the datapacks in force hold no JSON for this type
     */
    public Holder.Reference<GooTypeDefinition> holder(HolderLookup.Provider registries) {
        return registries.getOrThrow(key());
    }

    @Override
    public @NonNull String getSerializedName() {
        return id;
    }

    /**
     * Returns the translation key for this goo type's display name.
     *
     * @return the translation key string
     */
    public String getTranslationKey() {
        return TRANSLATION_PREFIX + id;
    }
}
