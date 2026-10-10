package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.RadiantAuraPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Radiant's held cue as every client tracking the caster sees it: a few
 * small glow motes drifting up off the caster's glove hand each tick the
 * caster holds Radiant, beside the wisps appearing in the dark around them;
 * nothing tints the view and nothing loops a sound (operator rulings 2026-10-09).
 * decision radiant-wisps-where-light-is-low
 */
public final class RadiantAura {

    /** Motes each hold tick sends off the glove. */
    static final int MOTES_PER_TICK = 2;
    private static final int MOTE_RGB = 0xFFE628;
    private static final float MOTE_SIZE = 0.6f;
    private static final double SPREAD = 0.12;
    private static final double DRIFT_UP = 0.03;
    private static final double DRIFT_SIDEWAYS = 0.01;
    private static final double HALF = 0.5;
    /** A jitter spans its reach on both sides. */
    private static final double BOTH_WAYS = 2;
    private static final DustParticleOptions MOTE = new DustParticleOptions(MOTE_RGB, MOTE_SIZE);

    private RadiantAura() {
    }

    /**
     * Handles a hold tick on the client thread, drifting motes off the caster's glove.
     *
     * @param payload the hold payload
     * @param context the network context
     */
    public static void onPayload(RadiantAuraPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !(mc.level.getEntity(payload.casterId()) instanceof Entity caster)) {
                return;
            }
            Vec3 hand = GloveHand.of(mc, caster, 1f);
            RandomSource random = mc.level.getRandom();
            for (int mote = 0; mote < MOTES_PER_TICK; mote++) {
                mc.level.addParticle(MOTE, hand.x + jitter(random, SPREAD), hand.y + jitter(random, SPREAD),
                        hand.z + jitter(random, SPREAD), jitter(random, DRIFT_SIDEWAYS), DRIFT_UP,
                        jitter(random, DRIFT_SIDEWAYS));
            }
        });
    }

    private static double jitter(RandomSource random, double reach) {
        return (random.nextDouble() - HALF) * reach * BOTH_WAYS;
    }
}
