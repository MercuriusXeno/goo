package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.particle.GloveMote;
import com.mercuriusxeno.goo.client.particle.GloveSwing;
import com.mercuriusxeno.goo.client.particle.SprayDripParticle;
import com.mercuriusxeno.goo.client.throwing.SprayerUseTracker;
import com.mercuriusxeno.goo.item.SprayerItem;
import com.mercuriusxeno.goo.network.DripFlights;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.StreamMotesPayload;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.DripArc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Plays a tick of a glove stream's motes on the client: each flies from the
 * glove along its own heading inside the cone, or along one of the arcs the
 * server's drips fly for a stream throwing drips, so the drips land where
 * the coat lands, each carrying none of the caster's movement;
 * a goo mote swings with its caster's glove over its opening ticks, and a
 * spray drip leaves a source held in its caster's view near the glove, where
 * the tick keeps the caster's lifted palm blob standing
 * (decisions glove-stream-motes-carry-the-players-travel-velocity,
 * glove-stream-motes-swing-with-the-glove-at-launch, goop-and-slick-spray-follows-the-coat,
 * goop-and-slick-spray-emanates-from-the-glove, palm-blob-lifts-as-the-spray-source,
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes,
 * goop-and-slick-drips-add-none-of-the-players-movement).
 */
public final class StreamMotes {

    /** Motes off the arc launched each tick of a stream throwing no drips; a spray draws the drips its server threw. */
    static final int MOTES_PER_TICK = 6;

    /** The ability whose spores are registered in {@link ConeVisuals}. */
    static final String MYCOSIS_ID = "goo:sculk_mycosis";

    private StreamMotes() {
    }

    /**
     * Launches a tick of Mycosis' spores through a cone a nozzle pours, as the server's tick launches the glove's:
     * each inside the cone's shape, coming to rest at its reach.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param cone the cone poured through
     */
    static void pour(SprayCone cone) {
        Minecraft mc = Minecraft.getInstance();
        AbilitySyncHandler.ClientAbility mycosis = AbilitySyncHandler.findAbility(MYCOSIS_ID);
        if (mc.player == null || mycosis == null) {
            return;
        }
        int casterId = mc.player.getId();
        mycosis.delivery().particle().ifPresent(id -> SimpleParticles.resolve(id).ifPresent(particle -> launch(
                GooStreamHandler.motesOf(casterId, cone.apex(), cone.axis(), id,
                        new GooStreamHandler.SprayedCone(cone.shape(), cone.reach()), Delivery.NO_THROW_SPEED),
                particle)));
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
        if (payload.throwSpeed() <= Delivery.NO_THROW_SPEED) {
            for (int i = 0; i < MOTES_PER_TICK; i++) {
                launchMote(mc, payload, particle, new CoatRay.ConeShare(random.nextDouble(), random.nextDouble()));
            }
            return;
        }
        boolean threwDrips = false;
        // operator UAT 2026-11-17: every drawn drip lands; the client draws the very drips the server threw
        List<CoatRay.ConeShare> shares = CoatRay.scatteredShares(payload.held(), payload.drips());
        for (int i = 0; i < shares.size(); i++) {
            // goop-and-slick-coat-lands-where-each-drip-arc-strikes: each drawn drip flies one of the server's arcs
            // operator ruling 2026-11-13: a tick's drips stand apart, each a share of a tick further along its arc
            threwDrips |= throwOnItsArc(mc, payload, particle, shares.get(i), (double) i / shares.size());
        }
        if (threwDrips) {
            liftThePalmBlob(mc, payload);
        }
    }

    /**
     * Keeps a glove caster's spray running from their lifted palm blob; a sprayer lifts none.
     * palm-blob-lifts-as-the-spray-source: a tick throwing drips keeps the caster's spray running
     *
     * @param mc      the client
     * @param payload the stream's tick
     */
    private static void liftThePalmBlob(Minecraft mc, StreamMotesPayload payload) {
        if (sprayerOf(mc, payload.casterId()) == null) {
            LiftedPalmBlob.held(payload.casterId(), payload.apex(), mc.level.getGameTime());
        }
    }

    /**
     * Throws one drip of the tick onto its share's spot of the cone, at the
     * launch the server's drip for that share took: it leaves the lifted palm
     * blob and flies its arc until it strikes.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     * residual-spray-cone-centers-on-the-look-from-the-blob
     *
     * @param mc       the client
     * @param payload  the stream's tick
     * @param particle the drip's particle
     * @param share    the drip's place in the cone
     * @param lead     the share of a tick of flight the drip has flown when thrown
     * @return true where the mote is a spray drip thrown on its arc
     */
    private static boolean throwOnItsArc(Minecraft mc, StreamMotesPayload payload, SimpleParticleType particle,
                                         CoatRay.ConeShare share, double lead) {
        Vec3 apex = payload.apex();
        Vec3 launch = payload.cone().launchFrom(apex, payload.coneShape(), share, payload.throwSpeed(),
                DripArc.gravityIn(mc.level.dimension()));
        Particle mote = mc.particleEngine.createParticle(particle, apex.x, apex.y, apex.z, launch.x, launch.y,
                launch.z);
        if (!(mote instanceof SprayDripParticle drip)) {
            return false;
        }
        // palm-blob-lifts-as-the-spray-source: the drip leaves the lifted blob, easing or at the source
        // operator report 2026-11-18: a sprayer's spray came from an invisible thing below the sprayer; a sprayer's
        // drip leaves its nozzle, as the view carries it
        Player sprayer = sprayerOf(mc, payload.casterId());
        // goop-wobbles-a-mob-its-drip-strikes: a drawn drip splats on a mob it meets, as the server's strikes it
        drip.flyArcFrom(partialTick -> sprayer != null ? SprayerUseTracker.nozzleEnd(sprayer, partialTick)
                        : LiftedPalmBlob.dripLeavesFrom(payload.casterId(), apex, partialTick),
                launch, payload.strikesLiving() ? DripFlights.livingNear(mc.level, payload.casterId())
                        : DripArc.NO_LIVING, lead);
        return true;
    }

    /**
     * The caster spraying from a sprayer, its use held, whose drips leave the sprayer's nozzle in place of a palm
     * blob, which a sprayer never lifts.
     *
     * @param mc       the client
     * @param casterId the caster's entity id
     * @return the caster, or null where the caster sprays from a glove
     */
    private static @Nullable Player sprayerOf(Minecraft mc, int casterId) {
        return mc.level != null && mc.level.getEntity(casterId) instanceof Player caster && caster.isUsingItem()
                && caster.getUseItem().getItem() instanceof SprayerItem ? caster : null;
    }

    /**
     * Launches one mote of the tick at its share of the cone.
     *
     * @param mc       the client
     * @param payload  the stream's tick
     * @param particle the mote's particle
     * @param share    the mote's place in the cone
     */
    private static void launchMote(Minecraft mc, StreamMotesPayload payload, SimpleParticleType particle,
                                   CoatRay.ConeShare share) {
        // every-ability-casts-from-the-lifted-palm-blob: Revert's and Mycosis' motes leave the lifted blob
        Vec3 apex = CastOrigin.of(payload.casterId(), payload.apex(), 0f);
        Vec3 launch = GooStreamHandler.moteLaunch(payload, share);
        Particle mote = mc.particleEngine.createParticle(particle, apex.x, apex.y, apex.z, launch.x, launch.y,
                launch.z);
        if (mote instanceof GloveMote gloveMote) {
            // every-ability-casts-from-the-lifted-palm-blob: the mote leaves the lifted blob where it stands then
            gloveMote.leaveFrom(() -> CastOrigin.of(payload.casterId(),
                    CasterPoses.gloveOf(payload.casterId(), payload.apex()), 0f));
            gloveMote.swingWith(new GloveSwing.Tracker(payload.casterId(), CasterPoses::poseOf));
        }
    }
}
