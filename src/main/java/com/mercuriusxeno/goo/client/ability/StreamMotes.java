package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.client.particle.GloveMote;
import com.mercuriusxeno.goo.client.particle.GloveSwing;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.StreamMotesPayload;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Plays a tick of a glove stream's motes on the client: each flies from the
 * glove along its own heading inside the cone, carrying the caster's travel,
 * and a goo mote swings with its caster's glove over its opening ticks
 * (decisions glove-stream-motes-carry-the-players-travel-velocity,
 * glove-stream-motes-swing-with-the-glove-at-launch).
 */
public final class StreamMotes {

    /** Motes launched each tick of the stream. */
    private static final int MOTES_PER_TICK = 6;

    private StreamMotes() {
    }

    /**
     * Handles a tick of the stream's motes on the client thread.
     *
     * @param payload the stream's tick
     * @param context the network context
     */
    public static void handle(StreamMotesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SimpleParticles.resolve(payload.particle())
                .ifPresent(particle -> launch(payload, particle)));
    }

    private static void launch(StreamMotesPayload payload, SimpleParticleType particle) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        RandomSource random = mc.level.getRandom();
        Vec3 apex = payload.apex();
        for (int i = 0; i < MOTES_PER_TICK; i++) {
            Vec3 heading = StreamCone.launchDirection(payload.axis(), payload.coneDegrees(), random.nextDouble(),
                    random.nextDouble());
            Vec3 launch = GooStreamHandler.moteLaunch(heading, payload.speed(), payload.travel());
            if (mc.particleEngine.createParticle(particle, apex.x, apex.y, apex.z, launch.x, launch.y,
                    launch.z) instanceof GloveMote mote) {
                mote.swingWith(new GloveSwing.Tracker(payload.casterId(), CasterPoses::poseOf));
            }
        }
    }
}
