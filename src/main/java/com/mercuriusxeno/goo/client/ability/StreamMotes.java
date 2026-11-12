package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.client.particle.GloveMote;
import com.mercuriusxeno.goo.client.particle.GloveSwing;
import com.mercuriusxeno.goo.client.particle.SprayDripParticle;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.StreamMotesPayload;
import com.mercuriusxeno.goo.throwing.CoatRay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Optional;

/**
 * Plays a tick of a glove stream's motes on the client: each flies from the
 * glove along its own heading inside the cone, or onto the end of a coat ray
 * for a stream whose spray follows its coat, carrying the caster's travel,
 * and a goo mote swings with its caster's glove over its opening ticks
 * (decisions glove-stream-motes-carry-the-players-travel-velocity,
 * glove-stream-motes-swing-with-the-glove-at-launch, goop-and-slick-spray-follows-the-coat).
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
                .ifPresent(particle -> launch(fromTheDrawnGlove(payload), particle)));
    }

    /**
     * The stream's tick launched from the caster's glove where it is drawn
     * this frame, in place of the hand the server's echo of an earlier tick
     * carries, so the spray leaves the hand of a caster on the move.
     * goop-and-slick-spray-throws-goo-drips-that-splat
     *
     * @param payload the stream's tick as the server sent it
     * @return the tick with its apex on the drawn glove
     */
    static StreamMotesPayload fromTheDrawnGlove(StreamMotesPayload payload) {
        return new StreamMotesPayload(payload.casterId(), CasterPoses.gloveOf(payload.casterId(), payload.apex()),
                payload.axis(), payload.particle(), payload.coneDegrees(), payload.speed(), payload.travel(),
                payload.coat());
    }

    private static void launch(StreamMotesPayload payload, SimpleParticleType particle) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        RandomSource random = mc.level.getRandom();
        CoatRay.BlockClip clip = CoatRay.clipping(mc.level);
        Vec3 apex = payload.apex();
        for (int i = 0; i < MOTES_PER_TICK; i++) {
            CoatRay.ConeShare share = new CoatRay.ConeShare(random.nextDouble(), random.nextDouble());
            Optional<Vec3> coatEnd = GooStreamHandler.coatEnd(payload, share, clip);
            Vec3 launch = GooStreamHandler.moteLaunch(payload, share, coatEnd);
            Particle mote = mc.particleEngine.createParticle(particle, apex.x, apex.y, apex.z, launch.x, launch.y,
                    launch.z);
            if (mote instanceof SprayDripParticle drip && coatEnd.isPresent()) {
                // goop-and-slick-spray-throws-goo-drips-that-splat: the drip rides the glove, then lands on the coat
                drip.rideWith(payload.casterId(), CasterPoses::poseOf, coatEnd.get(), payload.travel());
            } else if (mote instanceof GloveMote gloveMote) {
                gloveMote.swingWith(new GloveSwing.Tracker(payload.casterId(), CasterPoses::poseOf));
            }
        }
    }
}
