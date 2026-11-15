package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooParticles;
import com.mojang.serialization.Codec;

/**
 * How an {@link ExplodeStep}'s explosion shows to the players near it,
 * written on the step; the goo type's burnout plays with either.
 * blast-alone-takes-the-new-explosion-look
 */
public enum ExplosionLook {
    /**
     * No particles and vanilla's boom, the burnout drawing the explosion
     * alone (decision elemental-explosion-per-type).
     */
    SILENT,
    /**
     * Vanilla's gray-white explosion cloud, with poof and smoke over the
     * broken blocks, beside the burnout, and each broken block throwing its
     * own shards.
     * blast-explosion-plays-vanillas-cloud-poof-and-smoke-with-the-fireball
     * blast-broken-blocks-throw-their-own-break-shards
     */
    VANILLA;

    private static final String WHAT = "explosion look";

    /**
     * Codec reading the lower-case name.
     */
    public static final Codec<ExplosionLook> CODEC = LowerCaseEnumCodec.of(ExplosionLook.class, WHAT);

    /**
     * The particles and boom the explode packet carries for this look.
     *
     * @return the explosion's look
     */
    public GooExplosion.Look shown() {
        return this == VANILLA ? GooExplosion.Look.vanilla().withShards()
                : GooExplosion.Look.silent(GooParticles.SILENT_BLAST.get());
    }
}
