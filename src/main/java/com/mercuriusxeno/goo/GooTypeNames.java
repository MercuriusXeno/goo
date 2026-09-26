package com.mercuriusxeno.goo;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Translation keys and name components for goo types named by registry
 * key, so a generic item spells "[Type] [Tier]" from the type it carries
 * (decision generic-goo-items). A bundled type keeps the {@code goo.type.<id>}
 * key the enum spelled; a datapack type's key carries its namespace too.
 */
public final class GooTypeNames {

    /**
     * Prefix of every goo type translation key.
     */
    public static final String TYPE_PREFIX = "goo.type.";
    /**
     * Translation key of the omniblob item name, taking the type name and the tier.
     */
    public static final String OMNIBLOB = "item.goo.goo_omniblob";
    /**
     * Translation key of the goo bucket item name, taking the type name.
     */
    public static final String BUCKET = "item.goo.goo_bucket";
    /**
     * Translation key of the name an item shows when it carries no type.
     */
    public static final String UNTYPED = "goo.type.untyped";

    private static final char SEPARATOR = '.';

    private GooTypeNames() {
    }

    /**
     * @param key a goo type's registry key
     * @return its translation key: the id for a bundled type, namespace then id for a datapack type
     */
    public static String translationKey(ResourceKey<GooTypeDefinition> key) {
        Identifier id = key.identifier();
        return Goo.MODID.equals(id.getNamespace())
                ? TYPE_PREFIX + id.getPath()
                : TYPE_PREFIX + id.getNamespace() + SEPARATOR + id.getPath();
    }

    /**
     * @param key a goo type's registry key, or null for an item carrying no type
     * @return the type's translatable name
     */
    public static MutableComponent name(ResourceKey<GooTypeDefinition> key) {
        return Component.translatable(key == null ? UNTYPED : translationKey(key));
    }

    /**
     * @param key  the type an omniblob carries, or null
     * @param tier the tier name its volume earns
     * @return the omniblob item name, "[Type] [Tier]"
     */
    public static MutableComponent omniblobName(ResourceKey<GooTypeDefinition> key, String tier) {
        return Component.translatable(OMNIBLOB, name(key), tier);
    }

    /**
     * @param key the type a bucket carries, or null
     * @return the bucket item name, "[Type] Goo Bucket"
     */
    public static MutableComponent bucketName(ResourceKey<GooTypeDefinition> key) {
        return Component.translatable(BUCKET, name(key));
    }
}
