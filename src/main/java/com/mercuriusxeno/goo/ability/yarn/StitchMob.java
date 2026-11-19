package com.mercuriusxeno.goo.ability.yarn;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * One entry of the table a slime on Stitch rolls its mob from: a passive mob
 * and the layers of Stitch it takes, more for a more complex mob, which are
 * also its weight in the roll. The table is the {@code goo:stitch_mob}
 * datapack registry, one {@code data/<namespace>/goo/stitch_mob/<id>.json}
 * per entry.
 * stitch-is-yarns-residual-that-turns-slimes-into-passive-mobs
 *
 * @param entity the passive mob's entity type id
 * @param layers the layers of Stitch it takes, one to a whole cell's fifteen
 */
public record StitchMob(Identifier entity, int layers) {

    private static final String FIELD_ENTITY = "entity";
    private static final String FIELD_LAYERS = "layers";

    /** The datapack registry holding the table. */
    public static final ResourceKey<Registry<StitchMob>> REGISTRY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Goo.MODID, "stitch_mob"));

    /** Codec for an entry. */
    public static final Codec<StitchMob> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_ENTITY).forGetter(StitchMob::entity),
            Codec.intRange(1, VolumeCellBlock.MAX_LAYERS).fieldOf(FIELD_LAYERS).forGetter(StitchMob::layers)
    ).apply(inst, StitchMob::new));
}
