package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The rule choosing which goo type a world goo pocket on lunar holds, one pure function of the spot's biome.
 * decision frost-astral-and-xeno-goo-spawn-periodically-on-lunar
 * decision crystal-and-metal-goo-spawn-in-the-highlands-and-maria
 */
public final class LunarGooPockets {

    /** Lunar's four surface biomes. */
    public static final ResourceKey<Biome> MARIA = lunarBiome("lunar_maria");
    public static final ResourceKey<Biome> HIGHLANDS = lunarBiome("lunar_highlands");
    public static final ResourceKey<Biome> SHADOWED_BASINS = lunarBiome("lunar_shadowed_basins");
    public static final ResourceKey<Biome> ELDRITCH_GROUND = lunarBiome("lunar_eldritch_ground");

    /**
     * Astral can be anywhere on lunar; frost keeps to the shadowed basins, xeno to eldritch ground, crystal to the
     * highlands and metal to maria.
     * decision crystal-and-metal-goo-spawn-in-the-highlands-and-maria
     */
    private static final Map<ResourceKey<Biome>, List<ResourceKey<GooTypeDefinition>>> GOO_TYPES_BY_BIOME = Map.of(
            MARIA, List.of(GooTypes.ASTRAL, GooTypes.METAL),
            HIGHLANDS, List.of(GooTypes.ASTRAL, GooTypes.CRYSTAL),
            SHADOWED_BASINS, List.of(GooTypes.ASTRAL, GooTypes.FROST),
            ELDRITCH_GROUND, List.of(GooTypes.ASTRAL, GooTypes.XENO));

    private LunarGooPockets() {
    }

    /**
     * The goo types a pocket in a biome can hold.
     *
     * @param biome the pocket's biome
     * @return its goo types, none for a biome off lunar's surface
     */
    public static List<ResourceKey<GooTypeDefinition>> gooTypesFor(ResourceKey<Biome> biome) {
        return GOO_TYPES_BY_BIOME.getOrDefault(biome, List.of());
    }

    /**
     * The goo type one pocket in a biome holds, rolled evenly among the biome's goo types.
     *
     * @param biome  the pocket's biome
     * @param random the roll
     * @return its goo type, empty for a biome off lunar's surface
     */
    public static Optional<ResourceKey<GooTypeDefinition>> gooTypeFor(ResourceKey<Biome> biome, RandomSource random) {
        List<ResourceKey<GooTypeDefinition>> types = gooTypesFor(biome);
        if (types.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(types.get(random.nextInt(types.size())));
    }

    private static ResourceKey<Biome> lunarBiome(String path) {
        return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Goo.MODID, path));
    }
}
