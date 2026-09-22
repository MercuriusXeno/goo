package com.mercuriusxeno.goo;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.List;

/**
 * Keys into the {@code goo:goo_type} datapack registry: the registry key
 * itself and one element key per bundled goo type, for code that names a
 * type the way {@code Blocks.STONE} names a block. A type a datapack adds
 * has no constant here and is reached by its own {@link ResourceKey}.
 */
public final class GooTypes {

    /**
     * Registry path, which is also the directory under
     * {@code data/<namespace>/goo/} that holds each type's JSON.
     */
    public static final String REGISTRY_PATH = "goo_type";

    /**
     * The datapack registry every goo type lives in.
     */
    public static final ResourceKey<Registry<GooTypeDefinition>> REGISTRY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Goo.MODID, REGISTRY_PATH));

    /**
     * A type key as its id, the form the GOO_TYPE data component persists
     * (decision generic-goo-items).
     */
    public static final Codec<ResourceKey<GooTypeDefinition>> KEY_CODEC = ResourceKey.codec(REGISTRY);

    /**
     * A type key on the wire, the form the GOO_TYPE data component syncs.
     */
    public static final StreamCodec<ByteBuf, ResourceKey<GooTypeDefinition>> KEY_STREAM_CODEC =
            ResourceKey.streamCodec(REGISTRY);

    public static final ResourceKey<GooTypeDefinition> AEON = bundled("aeon");
    public static final ResourceKey<GooTypeDefinition> BLAZE = bundled("blaze");
    public static final ResourceKey<GooTypeDefinition> CRYSTAL = bundled("crystal");
    public static final ResourceKey<GooTypeDefinition> ENDER = bundled("ender");
    public static final ResourceKey<GooTypeDefinition> FROST = bundled("frost");
    public static final ResourceKey<GooTypeDefinition> GLOW = bundled("glow");
    public static final ResourceKey<GooTypeDefinition> HEX = bundled("hex");
    public static final ResourceKey<GooTypeDefinition> LEAF = bundled("leaf");
    public static final ResourceKey<GooTypeDefinition> METAL = bundled("metal");
    public static final ResourceKey<GooTypeDefinition> NETHER = bundled("nether");
    public static final ResourceKey<GooTypeDefinition> PULSE = bundled("pulse");
    public static final ResourceKey<GooTypeDefinition> ROCK = bundled("rock");
    public static final ResourceKey<GooTypeDefinition> SHROOM = bundled("shroom");
    public static final ResourceKey<GooTypeDefinition> TYPHOON = bundled("typhoon");
    public static final ResourceKey<GooTypeDefinition> UNSTABLE = bundled("unstable");
    public static final ResourceKey<GooTypeDefinition> VITAL = bundled("vital");

    /**
     * Every type the mod ships as JSON, in constant order.
     */
    public static final List<ResourceKey<GooTypeDefinition>> BUNDLED = List.of(
            AEON, BLAZE, CRYSTAL, ENDER, FROST, GLOW, HEX, LEAF,
            METAL, NETHER, PULSE, ROCK, SHROOM, TYPHOON, UNSTABLE, VITAL);

    private GooTypes() {
    }

    /**
     * Builds the element key for a type in the goo namespace.
     *
     * @param path the type id, which is the JSON file name without its extension
     * @return the key addressing that type in {@link #REGISTRY}
     */
    public static ResourceKey<GooTypeDefinition> bundled(String path) {
        return ResourceKey.create(REGISTRY, Identifier.fromNamespaceAndPath(Goo.MODID, path));
    }
}
