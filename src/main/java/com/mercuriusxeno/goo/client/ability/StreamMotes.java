package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.client.particle.GloveMote;
import com.mercuriusxeno.goo.client.particle.GloveSwing;
import com.mercuriusxeno.goo.client.particle.SprayDripParticle;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.StreamMotesPayload;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.DripArc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.List;
import java.util.Optional;

/**
 * Plays a tick of a glove stream's motes on the client: each flies from the
 * glove along its own heading inside the cone, onto the end of a coat ray
 * for a stream whose spray follows its coat, or along one of the arcs the
 * server's drips fly for a stream throwing drips, carrying the caster's travel;
 * a goo mote swings with its caster's glove over its opening ticks, and a
 * spray drip leaves a source held in its caster's view near the glove, where
 * the tick keeps the caster's lifted palm blob standing
 * (decisions glove-stream-motes-carry-the-players-travel-velocity,
 * glove-stream-motes-swing-with-the-glove-at-launch, goop-and-slick-spray-follows-the-coat,
 * goop-and-slick-spray-emanates-from-the-glove, palm-blob-lifts-as-the-spray-source,
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes).
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
        CoatRay.BlockClip clip = CoatRay.clipping(mc.level);
        List<CoatRay.ConeShare> fan = CoatRay.coneFan();
        boolean threwDrips = false;
        for (int i = 0; i < MOTES_PER_TICK; i++) {
            if (payload.throwSpeed() > Delivery.NO_THROW_SPEED) {
                // goop-and-slick-coat-lands-where-each-drip-arc-strikes: each drawn drip flies one of the server's arcs
                threwDrips |= throwOnItsArc(mc, payload, particle, fan.get(random.nextInt(fan.size())));
            } else {
                CoatRay.ConeShare share = new CoatRay.ConeShare(random.nextDouble(), random.nextDouble());
                threwDrips |= launchMote(mc, payload, particle, share, clip);
            }
        }
        if (threwDrips) {
            // palm-blob-lifts-as-the-spray-source: a tick throwing drips keeps the caster's spray running
            LiftedPalmBlob.sprayed(payload.casterId(), payload.apex(), mc.level.getGameTime());
        }
    }

    /**
     * Throws one drip of the tick along its share of the cone's fan, at the
     * launch the server's drip for that share took: it leaves the lifted palm
     * blob and flies its arc until it strikes.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param mc       the client
     * @param payload  the stream's tick
     * @param particle the drip's particle
     * @param share    the drip's place in the cone's fan
     * @return true where the mote is a spray drip thrown on its arc
     */
    private static boolean throwOnItsArc(Minecraft mc, StreamMotesPayload payload, SimpleParticleType particle,
                                         CoatRay.ConeShare share) {
        Vec3 apex = payload.apex();
        Vec3 launch = DripArc.launch(payload.axis(), payload.coneDegrees(), share, payload.throwSpeed(),
                payload.travel());
        Particle mote = mc.particleEngine.createParticle(particle, apex.x, apex.y, apex.z, launch.x, launch.y,
                launch.z);
        if (mote instanceof SprayDripParticle drip) {
            // palm-blob-lifts-as-the-spray-source: the drip leaves the lifted blob, easing or at the source
            drip.flyArcFrom(partialTick -> LiftedPalmBlob.dripLeavesFrom(payload.casterId(), apex, partialTick),
                    launch);
            return true;
        }
        return false;
    }

    /**
     * Launches one mote of the tick at its share of the cone.
     *
     * @param mc       the client
     * @param payload  the stream's tick
     * @param particle the mote's particle
     * @param share    the mote's place in the cone
     * @param clip     the blocks the coat's ray meets
     * @return true where the mote is a spray drip thrown onto its coat
     */
    private static boolean launchMote(Minecraft mc, StreamMotesPayload payload, SimpleParticleType particle,
                                      CoatRay.ConeShare share, CoatRay.BlockClip clip) {
        Vec3 apex = payload.apex();
        Optional<Vec3> coatEnd = GooStreamHandler.coatEnd(payload, share, clip);
        Vec3 launch = GooStreamHandler.moteLaunch(payload, share, coatEnd);
        Particle mote = mc.particleEngine.createParticle(particle, apex.x, apex.y, apex.z, launch.x, launch.y,
                launch.z);
        if (mote instanceof SprayDripParticle drip && coatEnd.isPresent()) {
            // palm-blob-lifts-as-the-spray-source: the drip leaves the lifted blob, easing or at the source
            drip.leaveFrom(partialTick -> LiftedPalmBlob.dripLeavesFrom(payload.casterId(), apex, partialTick),
                    coatEnd.get(), payload.travel());
            return true;
        }
        if (mote instanceof GloveMote gloveMote) {
            gloveMote.swingWith(new GloveSwing.Tracker(payload.casterId(), CasterPoses::poseOf));
        }
        return false;
    }
}
