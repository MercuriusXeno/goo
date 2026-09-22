package com.mercuriusxeno.goo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final String UNKNOWN_ID = "Not a goo type id: ";
    private static final char NAMESPACE_SEPARATOR = ':';

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

    /**
     * A type key as its short id: the bare path for a bundled type, the
     * namespaced id for a datapack type, which is the spelling data files,
     * commands and payloads use (decision datapack-goo-registry). A bare id
     * reads as the goo namespace, so data written against the enum still loads.
     */
    public static final Codec<ResourceKey<GooTypeDefinition>> ID_CODEC = Codec.STRING.comapFlatMap(
            id -> {
                ResourceKey<GooTypeDefinition> key = byId(id);
                return key == null ? DataResult.error(() -> UNKNOWN_ID + id) : DataResult.success(key);
            },
            GooTypes::id);

    /**
     * Orders type keys by identifier, namespace then path, which for the
     * bundled set is the order the enum constants held.
     */
    public static final Comparator<ResourceKey<GooTypeDefinition>> ORDER = Comparator
            .comparing((ResourceKey<GooTypeDefinition> key) -> key.identifier().getNamespace())
            .thenComparing(key -> key.identifier().getPath());

    /**
     * The keys of every type the registry held when a level last captured
     * them, in {@link #ORDER}; the bundled set until a level does.
     */
    private static final AtomicReference<List<ResourceKey<GooTypeDefinition>>> CAPTURED =
            new AtomicReference<>(BUNDLED);

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

    /**
     * @param key a goo type's key
     * @return the bare path for a bundled type, the namespaced id otherwise
     */
    public static String id(ResourceKey<GooTypeDefinition> key) {
        Identifier identifier = key.identifier();
        return Goo.MODID.equals(identifier.getNamespace()) ? identifier.getPath() : identifier.toString();
    }

    /**
     * @param id a bare path, read in the goo namespace, or a namespaced id
     * @return the key it names, or null for text no identifier accepts
     */
    public static @Nullable ResourceKey<GooTypeDefinition> byId(String id) {
        Identifier identifier = id.indexOf(NAMESPACE_SEPARATOR) < 0
                ? Identifier.tryBuild(Goo.MODID, id)
                : Identifier.tryParse(id);
        return identifier == null ? null : ResourceKey.create(REGISTRY, identifier);
    }

    /**
     * The key an id names when the last capture holds that type, which is
     * what a payload from a client is checked against.
     *
     * @param id a bare path or a namespaced id
     * @return the key, or null for text naming no captured type
     */
    public static @Nullable ResourceKey<GooTypeDefinition> known(String id) {
        ResourceKey<GooTypeDefinition> key = byId(id);
        return key != null && order().contains(key) ? key : null;
    }

    /**
     * Parses an id the way the enum's valueOf did: text naming a type the
     * last capture holds answers its key, and anything else is refused.
     *
     * @param id a bare path or a namespaced id
     * @return the key it names
     * @throws IllegalArgumentException for text naming no captured type
     */
    public static ResourceKey<GooTypeDefinition> parseKnown(String id) {
        ResourceKey<GooTypeDefinition> key = known(id);
        if (key == null) {
            throw new IllegalArgumentException(UNKNOWN_ID + id);
        }
        return key;
    }

    /**
     * @param registries the registry access of the level in hand
     * @param key        a goo type's key
     * @return the loaded entry for that type
     * @throws IllegalStateException when the datapacks in force hold no JSON for the key
     */
    public static GooTypeDefinition definition(HolderLookup.Provider registries, ResourceKey<GooTypeDefinition> key) {
        return registries.getOrThrow(key).value();
    }

    /**
     * @param registries the registry access of the level in hand
     * @param key        a goo type's key
     * @return whether the registry holds an entry for the key
     */
    public static boolean exists(HolderLookup.Provider registries, ResourceKey<GooTypeDefinition> key) {
        return registries.get(key).isPresent();
    }

    /**
     * Every type the registry holds, in {@link #ORDER}.
     *
     * @param registries the registry access of the level in hand
     * @return the keys, a datapack's included
     */
    public static List<ResourceKey<GooTypeDefinition>> all(HolderLookup.Provider registries) {
        return registries.lookupOrThrow(REGISTRY).listElementIds().sorted(ORDER).toList();
    }

    /**
     * Captures the registry's keys for code that runs with no level in
     * hand, such as item fluid handlers and the radial wheel. A server
     * captures at datapack sync and a client at login; the dynamic registry
     * stands unchanged between those.
     *
     * @param registries the registry access of the level in hand
     */
    public static void capture(HolderLookup.Provider registries) {
        CAPTURED.set(all(registries));
    }

    /**
     * @return the keys the last capture held, in {@link #ORDER}, or the bundled set before any capture
     */
    public static List<ResourceKey<GooTypeDefinition>> order() {
        return CAPTURED.get();
    }

    /**
     * @param key a goo type's key
     * @return its position in {@link #order()}, or -1 for a key the capture does not hold
     */
    public static int indexOf(ResourceKey<GooTypeDefinition> key) {
        return order().indexOf(key);
    }
}
