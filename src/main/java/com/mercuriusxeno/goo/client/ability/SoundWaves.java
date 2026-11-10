package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.ScanWavePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Deep Click's sound wave: a ring of sculk motes leaving the glove and
 * travelling the straight line to the landing over the server's delay, so
 * it arrives on the tick the click sounds there, then widening and thinning
 * out at the landing while it fades
 * (decision click-is-a-hit-scan-drawn-as-a-delayed-wave).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SoundWaves {

    /** The ring's radius as it travels, in blocks. */
    static final double RING_RADIUS = 0.35;
    /** How far the ring widens past its radius while it fades at the landing, in blocks. */
    static final double FADE_WIDENING = 0.9;
    /** The motes around the ring as it travels. */
    static final int RING_MOTES = 10;
    private static final double TURN = Math.PI * 2;
    private static final double MOSTLY_VERTICAL = 0.99;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private static final List<Travelling> TRAVELLING = new ArrayList<>();

    private SoundWaves() {
    }

    /**
     * One wave on its way to its landing.
     *
     * @param wave  the wave the server sent
     * @param start the client game tick it left the glove
     */
    record Travelling(ScanWavePayload wave, long start) {
    }

    /**
     * Starts a wave leaving the glove.
     *
     * @param wave     the wave the server sent
     * @param gameTime the client game tick it arrived on
     */
    static void start(ScanWavePayload wave, long gameTime) {
        TRAVELLING.add(new Travelling(wave, gameTime));
    }

    /**
     * Draws each travelling wave's ring this tick and lets the faded ones go.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            TRAVELLING.clear();
            return;
        }
        if (TRAVELLING.isEmpty() || Minecraft.getInstance().isPaused()) {
            return;
        }
        long now = level.getGameTime();
        TRAVELLING.removeIf(travelling -> now - travelling.start() > travelling.wave().ticks() + travelling.wave().fade());
        for (Travelling travelling : TRAVELLING) {
            drawRing(level, travelling.wave(), now - travelling.start());
        }
    }

    private static void drawRing(ClientLevel level, ScanWavePayload wave, long age) {
        Vec3 direction = wave.to().subtract(wave.from());
        if (direction.lengthSqr() == 0) {
            return;
        }
        Vec3 along = direction.normalize();
        Vec3 across = along.cross(Math.abs(along.y) < MOSTLY_VERTICAL ? UP : EAST).normalize();
        Vec3 around = along.cross(across);
        Vec3 center = ScanWaveProgress.at(wave.from(), wave.to(), wave.ticks(), age);
        double radius = RING_RADIUS + FADE_WIDENING * fadeShare(wave, age);
        int motes = Math.max(1, (int) Math.round(RING_MOTES * (1 - fadeShare(wave, age))));
        for (int mote = 0; mote < motes; mote++) {
            double angle = TURN * mote / motes;
            Vec3 at = center.add(across.scale(Math.cos(angle) * radius)).add(around.scale(Math.sin(angle) * radius));
            level.addParticle(ParticleTypes.SCULK_CHARGE_POP, at.x, at.y, at.z, 0, 0, 0);
        }
    }

    /**
     * How far a wave has faded at its landing.
     *
     * @param wave the wave
     * @param age  the ticks since it left the glove
     * @return 0 while it travels, rising to 1 as its fade runs out
     */
    static double fadeShare(ScanWavePayload wave, long age) {
        long faded = age - wave.ticks();
        return faded <= 0 || wave.fade() <= 0 ? 0 : Math.min(1.0, (double) faded / wave.fade());
    }
}
