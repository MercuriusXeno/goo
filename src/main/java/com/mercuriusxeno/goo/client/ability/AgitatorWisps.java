package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.particle.HexWispParticle;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The cloud around an agitator prism: hex wisps circling it slowly at
 * their own radius, pace and height, and on each spawn attempt a surge of
 * wisps bursting outward before curling back. The prism renderer reports
 * each agitator it draws, with its countdown; a prism unreported for a few
 * ticks, gone or out of sight, is dropped.
 * agitator-prism-quickens-until-a-spawn
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class AgitatorWisps {

    /** Ticks a prism stays tracked after the renderer last reported it. */
    private static final int FORGET_AFTER = 5;
    /** Ticks between one circling wisp and the next. */
    private static final int CIRCLE_EVERY = 3;
    private static final double BASE_RADIUS = 0.45;
    private static final double RADIUS_VARIANCE = 0.4;
    private static final double BASE_TURN = 0.04;
    private static final double TURN_VARIANCE = 0.05;
    private static final double BOB_REACH = 0.12;
    private static final double HEIGHT_SPAN = 0.9;
    private static final int BASE_CIRCLE_LIFE = 50;
    private static final int CIRCLE_LIFE_VARIANCE = 40;
    private static final int SURGE_WISPS = 14;
    private static final double SURGE_SPEED = 0.22;
    private static final int SURGE_TICKS = 6;
    private static final int SURGE_FLIGHT = 16;
    private static final double FULL_TURN = Math.PI * 2;
    private static final double CLOCKWISE = 1;
    private static final double COUNTERCLOCKWISE = -1;
    private static final double CELL_CENTER = 0.5;

    private static final Map<BlockPos, Tracked> TRACKED = new HashMap<>();

    private AgitatorWisps() {
    }

    /**
     * An agitator the renderer reported: its last countdown and when it was seen.
     *
     * @param countdown the countdown last seen
     * @param seenAt    the game time it was last seen
     */
    private record Tracked(int countdown, long seenAt) {
    }

    /**
     * Whether the countdown restarted since it was last seen, which it does
     * only as an attempt fires.
     *
     * @param before the countdown last seen
     * @param now    the countdown seen now
     * @return true when an attempt fired between the two
     */
    static boolean attempted(int before, int now) {
        return now > before;
    }

    /**
     * Reports an agitator prism drawn this frame, surging its wisps when its
     * countdown has restarted since it was last seen.
     *
     * @param pos       the prism's cell
     * @param countdown its countdown now
     */
    public static void report(BlockPos pos, int countdown) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Tracked before = TRACKED.put(pos.immutable(), new Tracked(countdown, mc.level.getGameTime()));
        if (before != null && attempted(before.countdown(), countdown)) {
            surge(mc, mc.level.getRandom(), center(pos));
        }
    }

    /**
     * Keeps every tracked prism's cloud circling, and forgets the prisms the
     * renderer has stopped reporting.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        long now = mc.level.getGameTime();
        Iterator<Map.Entry<BlockPos, Tracked>> tracked = TRACKED.entrySet().iterator();
        while (tracked.hasNext()) {
            Map.Entry<BlockPos, Tracked> prism = tracked.next();
            if (now - prism.getValue().seenAt() > FORGET_AFTER) {
                tracked.remove();
            } else if (now % CIRCLE_EVERY == 0) {
                circle(mc, mc.level.getRandom(), center(prism.getKey()));
            }
        }
    }

    private static Vec3 center(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos).add(0, CELL_CENTER, 0);
    }

    private static void circle(Minecraft mc, RandomSource random, Vec3 center) {
        Vec3 ring = center.add(0, (random.nextDouble() - CELL_CENTER) * HEIGHT_SPAN, 0);
        if (mc.particleEngine.createParticle(GooParticles.HEX_WISP.get(), ring.x, ring.y, ring.z, 0, 0, 0)
                instanceof HexWispParticle wisp) {
            double turn = (BASE_TURN + random.nextDouble() * TURN_VARIANCE) * (random.nextBoolean() ? CLOCKWISE : COUNTERCLOCKWISE);
            wisp.circle(ring, BASE_RADIUS + random.nextDouble() * RADIUS_VARIANCE, random.nextDouble() * FULL_TURN,
                    turn, BOB_REACH, BASE_CIRCLE_LIFE + random.nextInt(CIRCLE_LIFE_VARIANCE));
        }
    }

    private static void surge(Minecraft mc, RandomSource random, Vec3 center) {
        for (int i = 0; i < SURGE_WISPS; i++) {
            Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian() * CELL_CENTER, random.nextGaussian())
                    .normalize().scale(SURGE_SPEED);
            if (mc.particleEngine.createParticle(GooParticles.HEX_WISP.get(), center.x, center.y, center.z, 0, 0, 0)
                    instanceof HexWispParticle wisp) {
                wisp.launch(0, out, SURGE_TICKS, SURGE_FLIGHT, () -> center);
            }
        }
    }
}
