package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.particle.GnatParticle;
import com.mercuriusxeno.goo.client.particle.GnatSteering;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.network.GnatSeekPayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Plays a tick of Decay's bite on the client: gnats leave the glove slow and
 * sprayed wide around the aim, then speed up and steer every tick toward the
 * mob the swarm bites, so their paths curve onto it and follow it as it moves
 * (decision decay-gnats-degrade-each-block-once).
 */
public final class GnatSeek {

    /** Gnats launched each bitten tick, as many as the unbitten spray sends. */
    private static final int GNATS_PER_TICK = 6;

    private GnatSeek() {
    }

    /**
     * Handles a bitten tick on the client thread.
     *
     * @param payload the bitten tick
     * @param context the network context
     */
    public static void handle(GnatSeekPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> launch(payload));
    }

    private static void launch(GnatSeekPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !(level.getEntity(payload.preyId()) instanceof Entity prey)) {
            return;
        }
        Vec3 glove = gloveOf(mc, payload);
        RandomSource random = level.getRandom();
        for (int i = 0; i < GNATS_PER_TICK; i++) {
            Vec3 launch = GnatSteering.launch(payload.aim(), random.nextDouble(), random.nextDouble());
            if (mc.particleEngine.createParticle(GooParticles.GNAT.get(), glove.x, glove.y, glove.z, launch.x,
                    launch.y, launch.z) instanceof GnatParticle gnat) {
                gnat.seek(() -> prey.getBoundingBox().getCenter());
            }
        }
    }

    /**
     * Where the caster's glove stands: the local player's glove as drawn this
     * frame, or the glove hand another caster's stream left from.
     *
     * @param mc      the client
     * @param payload the bitten tick
     * @return where the glove stands
     */
    private static Vec3 gloveOf(Minecraft mc, GnatSeekPayload payload) {
        Entity caster = mc.player;
        if (caster != null && caster.getId() == payload.casterId()) {
            return GloveAim.handPosition(mc.gameRenderer.getMainCamera());
        }
        return payload.glove();
    }
}
