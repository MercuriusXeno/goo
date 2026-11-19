package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.network.SprayMistPayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Draws a mister's tick: a fine mist of goo-coloured fog puffs drifting out along the cone, as far as the mist
 * reaches, in place of drawn drips.
 * decision mister-draws-mist-and-lays-one-layer-on-bare-spots
 */
public final class SprayMist {

    /**
     * Puffs a tick of mist draws, enough that the puffs living at once fill the cone.
     * operator report 2026-11-18: the mister showed no visual; then, the mist was barely there
     */
    private static final int PUFFS_A_TICK = 64;
    /** How fast a puff drifts out along its ray, blocks a tick, slow so the mist lingers where it hangs. */
    private static final double DRIFT_A_TICK = 0.04;
    /** The nearest a puff starts along its ray, as a share of the reach, clear of the caster's view. */
    private static final double NEAREST_START = 0.1;
    private static final int OPAQUE = 0xFF000000;

    private SprayMist() {
    }

    /**
     * Draws the tick's mist on the client thread.
     *
     * @param payload the mist's tick
     * @param context the network context
     */
    public static void handle(SprayMistPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> draw(payload));
    }

    private static void draw(SprayMistPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        RandomSource random = mc.level.getRandom();
        ColorParticleOption fog = ColorParticleOption.create(GooParticles.GOO_FOG.get(),
                ClientGooTypes.color(payload.gooType()) | OPAQUE);
        for (int puff = 0; puff < PUFFS_A_TICK; puff++) {
            Vec3 ray = StreamCone.launchDirection(payload.axis(), payload.coneShape(), Math.sqrt(random.nextDouble()),
                    random.nextDouble());
            Vec3 start = payload.eye().add(ray.scale(payload.reach()
                    * (NEAREST_START + (1 - NEAREST_START) * random.nextDouble())));
            Vec3 drift = ray.scale(DRIFT_A_TICK);
            mc.level.addParticle(fog, start.x, start.y, start.z, drift.x, drift.y, drift.z);
        }
    }
}
