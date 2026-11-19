package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.throwing.SprayerUseTracker;
import com.mercuriusxeno.goo.network.SprayMistPayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Draws Mist's tick: goo-coloured fog puffs leaving the sprayer's nozzle along the cone's rays and fading near the
 * mist's reach, in place of drawn drips.
 * decision mister-draws-mist-and-lays-one-layer-on-bare-spots
 */
public final class SprayMist {

    /**
     * Puffs a tick of mist draws, each leaving the nozzle along its own ray of the cone.
     * operator report 2026-11-19: the mist was excessive, a cloud floating over the target; it should spray a cone,
     * as Petrify does
     */
    private static final int PUFFS_A_TICK = 18;
    /**
     * A puff's launch speed for each block the mist reaches: a fog puff slows by a twentieth a tick and lives eight
     * to sixteen ticks, carrying about nine times its launch speed, so a puff fades near the mist's reach.
     */
    private static final double SPEED_PER_BLOCK = 0.11;
    /** How much slower than the full launch a puff may leave, so the cone fills along its length. */
    private static final double SPEED_SPREAD = 0.6;
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
        Vec3 from = mc.level.getEntity(payload.casterId()) instanceof Player caster && caster.isUsingItem()
                ? SprayerUseTracker.nozzleEnd(caster, 1f) : payload.from();
        for (int puff = 0; puff < PUFFS_A_TICK; puff++) {
            Vec3 ray = StreamCone.launchDirection(payload.axis(), payload.coneShape(), Math.sqrt(random.nextDouble()),
                    random.nextDouble());
            Vec3 launch = ray.scale(payload.reach() * SPEED_PER_BLOCK * (1 - random.nextDouble() * SPEED_SPREAD));
            mc.level.addParticle(fog, from.x, from.y, from.z, launch.x, launch.y, launch.z);
        }
    }
}
