package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.overlay.OreIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Each gem vein Glitter's front reaches bursts into sparkles the tick its
 * icon begins to show, white glints and a few rising motes around the vein.
 * decision glitter-sphere-icons-gem-ore-groups
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class VeinSparkles {

    /** White glints one burst throws. */
    private static final int GLINTS = 14;
    /** Rising motes one burst throws. */
    private static final int MOTES = 4;
    /** Blocks about the vein's centroid the sparkles scatter over. */
    private static final double SCATTER = 0.9;
    /** The motes' rise, in blocks a tick. */
    private static final double MOTE_RISE = 0.04;
    private static final double HALF = 0.5;
    /** Both sides of the centroid. */
    private static final double BOTH_SIDES = 2;

    private static long lastTick = Long.MIN_VALUE;

    private VeinSparkles() {
    }

    /**
     * Bursts every vein whose icon began to show since the last tick.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            lastTick = Long.MIN_VALUE;
            return;
        }
        long now = level.getGameTime();
        if (lastTick != Long.MIN_VALUE && now > lastTick) {
            OreIcons.CLIENT.burstsBetween(lastTick, now).forEach(centroid -> burst(level, centroid));
        }
        lastTick = now;
    }

    private static void burst(ClientLevel level, Vec3 centroid) {
        RandomSource random = level.getRandom();
        for (int index = 0; index < GLINTS; index++) {
            level.addParticle(ParticleTypes.WAX_OFF, centroid.x + scatter(random), centroid.y + scatter(random),
                    centroid.z + scatter(random), 0, 0, 0);
        }
        for (int index = 0; index < MOTES; index++) {
            level.addParticle(ParticleTypes.END_ROD, centroid.x + scatter(random), centroid.y + scatter(random),
                    centroid.z + scatter(random), 0, MOTE_RISE, 0);
        }
    }

    private static double scatter(RandomSource random) {
        return (random.nextDouble() - HALF) * BOTH_SIDES * SCATTER;
    }
}
