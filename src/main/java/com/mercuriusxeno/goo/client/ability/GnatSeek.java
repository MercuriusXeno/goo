package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.particle.GloveSwing;
import com.mercuriusxeno.goo.client.particle.GnatParticle;
import com.mercuriusxeno.goo.client.particle.GnatSteering;
import com.mercuriusxeno.goo.network.GnatSeekPayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.function.Supplier;

/**
 * Plays a tick of Decay's swarm on the client: gnats fan out from the glove
 * across the stream's cone around the line the player fires, and every tick
 * their target pulls them, their momentum carrying them on, so the swarm
 * converges on the mob it chases, following it as it moves, or with none on
 * the block under the crosshair (decision decay-gnats-degrade-each-block-once).
 */
public final class GnatSeek {

    /** Gnats launched each tick of the swarm. */
    private static final int GNATS_PER_TICK = 6;

    /** The ability the swarm is registered under in {@link ConeVisuals}. */
    static final String DECAY_ID = "goo:zombie_decay";

    private GnatSeek() {
    }

    /**
     * Handles a tick of the swarm on the client thread.
     *
     * @param payload the swarm's tick
     * @param context the network context
     */
    public static void handle(GnatSeekPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> launch(payload));
    }

    private static void launch(GnatSeekPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Supplier<Vec3> prey = preyOf(level, payload);
        Vec3 glove = CastOrigin.of(payload.casterId(), CasterPoses.gloveOf(payload.casterId(), payload.glove()), 0f);
        for (GnatSteering.Launch launch : GnatSteering.launchesOf(payload, glove, GNATS_PER_TICK, level.getRandom())) {
            Vec3 velocity = launch.velocity();
            if (mc.particleEngine.createParticle(GooParticles.GNAT.get(), glove.x, glove.y, glove.z, velocity.x,
                    velocity.y, velocity.z) instanceof GnatParticle gnat) {
                // cone-style-abilities-are-reviewed-for-consistency: the chased mob is sought no farther than the reach
                gnat.seek(prey != null ? () -> GnatSteering.seekWithin(glove, prey.get(), payload.reach())
                        : launch::rest);
                // every-ability-casts-from-the-lifted-palm-blob: the gnat leaves the lifted blob where it stands then
                gnat.leaveFrom(() -> CastOrigin.of(payload.casterId(), CasterPoses.gloveOf(payload.casterId(),
                        payload.glove()), 0f));
                // glove-stream-motes-swing-with-the-glove-at-launch
                gnat.swingWith(new GloveSwing.Tracker(payload.casterId(), CasterPoses::poseOf));
            }
        }
    }

    /**
     * Launches a tick of the swarm through a cone with no crosshair to converge on, as a nozzle pours it: each gnat
     * fanned inside the cone's shape, seeking its own point at the cone's reach along the way it left.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param cone the cone poured through
     */
    static void pour(SprayCone cone) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        RandomSource random = level.getRandom();
        Vec3 apex = cone.apex();
        for (int i = 0; i < GNATS_PER_TICK; i++) {
            Vec3 velocity = GnatSteering.launch(cone.axis(), cone.shape(), Math.sqrt(random.nextDouble()),
                    random.nextDouble());
            Vec3 rest = apex.add(velocity.normalize().scale(cone.reach()));
            if (mc.particleEngine.createParticle(GooParticles.GNAT.get(), apex.x, apex.y, apex.z, velocity.x,
                    velocity.y, velocity.z) instanceof GnatParticle gnat) {
                gnat.seek(() -> rest);
            }
        }
    }

    /**
     * Where the chased mob stands, read every tick, or null where the swarm chases none.
     *
     * @param level   the client level
     * @param payload the swarm's tick
     * @return the mob's middle, or null
     */
    private static @Nullable Supplier<Vec3> preyOf(ClientLevel level, GnatSeekPayload payload) {
        if (payload.preyId() == GnatSeekPayload.NO_PREY || !(level.getEntity(payload.preyId()) instanceof Entity mob)) {
            return null;
        }
        return () -> mob.getBoundingBox().getCenter();
    }
}
