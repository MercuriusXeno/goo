package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.GrudgeRingPayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The Grudges pulsing on this client: each bursts a ring of crimson fog
 * flat out from its caster's middle to the reach its charge resolved, two
 * tones of blood red with the darker one smoldering, and a puff of smoke at
 * the heart of it, a curse rather than a frost.
 * grudge-is-a-charged-crimson-nova-dealing-your-missing-health
 */
public final class GrudgeRings {

    /** The ring's bright crimson and its dark, withered blood red. */
    static final int CRIMSON = 0xFFDC143C;
    static final int WITHERED_CRIMSON = 0xFF5A0612;
    /** Puffs per block of the ring's reach. */
    private static final int PUFFS_PER_BLOCK = 24;
    /**
     * The blocks a fog puff launched at one block a tick travels before it
     * fades: its friction of 0.95 a tick over a lifetime near twelve ticks.
     */
    static final double FOG_TRAVEL_PER_SPEED = 9.2;
    /** How far below the edge's speed a puff may launch, as a share of it, so the ring reads thick. */
    private static final double SPEED_SPREAD = 0.2;
    /** How far above or below the middle a puff may start, a thin band rather than a sheet. */
    private static final double BAND = 0.15;
    /** Smoke puffs at the ring's heart. */
    private static final int HEART_SMOKE = 12;
    private static final double HEART_SMOKE_SPEED = 0.05;
    private static final double TWO_PI = 2 * Math.PI;

    private GrudgeRings() {
    }

    /**
     * Handles the Grudge payload on the client thread.
     *
     * @param payload the Grudge payload
     * @param context the network context
     */
    public static void handle(GrudgeRingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                pulse(level, payload.center(), payload.reach());
            }
        });
    }

    /**
     * The speed a fog puff launches at to fade at the ring's reach.
     *
     * @param reach the ring's reach in blocks
     * @return the launch speed in blocks a tick
     */
    static double edgeSpeed(float reach) {
        return reach / FOG_TRAVEL_PER_SPEED;
    }

    private static void pulse(ClientLevel level, Vec3 center, float reach) {
        RandomSource random = level.getRandom();
        int puffs = Math.max(1, Math.round(reach * PUFFS_PER_BLOCK));
        double speed = edgeSpeed(reach);
        for (int i = 0; i < puffs; i++) {
            double angle = TWO_PI * (i + random.nextDouble()) / puffs;
            double launch = speed * (1 - SPEED_SPREAD * random.nextDouble());
            int color = random.nextBoolean() ? CRIMSON : WITHERED_CRIMSON;
            level.addParticle(ColorParticleOption.create(GooParticles.GOO_FOG.get(), color),
                    center.x, center.y + BAND * (random.nextDouble() - random.nextDouble()), center.z,
                    Math.cos(angle) * launch, 0, Math.sin(angle) * launch);
        }
        for (int i = 0; i < HEART_SMOKE; i++) {
            double angle = TWO_PI * random.nextDouble();
            level.addParticle(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z,
                    Math.cos(angle) * HEART_SMOKE_SPEED, HEART_SMOKE_SPEED, Math.sin(angle) * HEART_SMOKE_SPEED);
        }
    }
}
