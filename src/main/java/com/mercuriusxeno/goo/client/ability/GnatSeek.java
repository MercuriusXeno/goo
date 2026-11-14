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
        Vec3 glove = CasterPoses.gloveOf(payload.casterId(), payload.glove());
        RandomSource random = level.getRandom();
        for (int i = 0; i < GNATS_PER_TICK; i++) {
            Vec3 launch = GnatSteering.launch(payload.aim(), payload.sprayDegrees(), random.nextDouble(),
                    random.nextDouble());
            if (mc.particleEngine.createParticle(GooParticles.GNAT.get(), glove.x, glove.y, glove.z, launch.x,
                    launch.y, launch.z) instanceof GnatParticle gnat) {
                Vec3 rest = pointIn(payload.targetMin(), payload.targetMax(), random);
                gnat.seek(prey != null ? prey : () -> rest);
                gnat.leaveFrom(() -> CasterPoses.gloveOf(payload.casterId(), payload.glove()));
                // glove-stream-motes-swing-with-the-glove-at-launch
                gnat.swingWith(new GloveSwing.Tracker(payload.casterId(), CasterPoses::poseOf));
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

    private static Vec3 pointIn(Vec3 min, Vec3 max, RandomSource random) {
        return new Vec3(min.x + random.nextDouble() * (max.x - min.x), min.y + random.nextDouble() * (max.y - min.y),
                min.z + random.nextDouble() * (max.z - min.z));
    }
}
