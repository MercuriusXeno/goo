package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.program.ReserveDrainStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.particle.VitalMoteParticle;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.stream.Stream;

/**
 * Reserve's effect in the world: each held tick, pink motes of vital goo
 * pull out of the player's chest into the glove; each half heart the drain
 * takes thumps a heartbeat; each reserve half banked rings the player in a
 * soft pink pulse. The client reads the drained and banked halves from its
 * own health and overlay, so no packet carries them.
 * reserve-hearts-sit-behind-the-bar
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ReserveVisual {

    /** Motes drawn out of the chest each held tick. */
    static final int MOTES_PER_TICK = 2;
    /** Share of the player's height the chest sits at. */
    static final double CHEST_HEIGHT = 0.7;
    /** Blocks around the chest a mote may leave from. */
    private static final double CHEST_SPREAD = 0.25;
    /** Blocks a mote's flight bows out from the straight line, sideways and up. */
    static final double BOW = 0.4;
    private static final float HEARTBEAT_VOLUME = 0.7f;
    /** Pitched up from the warden's, so the thump reads as the player's own heart. */
    private static final float HEARTBEAT_PITCH = 1.4f;
    /** Motes in the pulse ring, and their outward speed in blocks per tick. */
    private static final int PULSE_MOTES = 16;
    private static final double PULSE_SPEED = 0.12;
    /** Share of the player's height the pulse ring sits at. */
    private static final double PULSE_HEIGHT = 0.5;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF = 0.5;
    private static final int NO_READING = -1;

    private static int lastHealthHalves = NO_READING;
    private static int lastReserveHalves = NO_READING;

    private ReserveVisual() {
    }

    /**
     * What one client tick of the bar cues: the half hearts the drain took
     * and the reserve halves banked since the tick before.
     *
     * @param drained the health halves the drain took
     * @param banked  the reserve halves banked
     */
    record Cues(int drained, int banked) {
    }

    /**
     * The cues a tick's change in the bar plays. Health lost counts as
     * drained only while a reserve stands and the player is not flinching,
     * since a hit flinches and the silent drain never does.
     *
     * @param lastHealth  the health halves the tick before
     * @param health      the health halves now
     * @param lastReserve the reserve halves the tick before
     * @param reserve     the reserve halves now
     * @param draining    whether a reserve stands and the player is not flinching
     * @return the halves drained and banked
     */
    static Cues cuesFor(int lastHealth, int health, int lastReserve, int reserve, boolean draining) {
        if (!draining) {
            return new Cues(0, 0);
        }
        return new Cues(Math.max(0, lastHealth - health), Math.max(0, reserve - lastReserve));
    }

    /**
     * The chest a mote leaves from.
     *
     * @param feet   the player's position
     * @param height the player's height
     * @return the chest point
     */
    static Vec3 chestOf(Vec3 feet, double height) {
        return feet.add(0, height * CHEST_HEIGHT, 0);
    }

    /**
     * The point a mote's flight bows out through: the middle of the line from
     * chest to glove, pushed out by the bow along a direction.
     *
     * @param chest the mote's start
     * @param glove the glove it lands in
     * @param bowed the unit direction this mote bows toward
     * @return the control point
     */
    static Vec3 bowPoint(Vec3 chest, Vec3 glove, Vec3 bowed) {
        return chest.add(glove).scale(HALF).add(bowed.scale(BOW));
    }

    /**
     * Draws this held tick's motes from the chest into the glove when the
     * streamed ability drains into a reserve.
     *
     * @param player    the local player holding the stream
     * @param abilityId the streamed ability
     */
    public static void drawDrain(Player player, String abilityId) {
        if (!drains(abilityId)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RandomSource random = player.getRandom();
        Vec3 chest = chestOf(player.position(), player.getBbHeight());
        Vec3 glove = gloveNow(mc);
        for (int mote = 0; mote < MOTES_PER_TICK; mote++) {
            Vec3 from = chest.add(spread(random, CHEST_SPREAD));
            Vec3 bowed = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()), random.nextGaussian())
                    .normalize();
            if (mc.particleEngine.createParticle(GooParticles.VITAL_MOTE.get(), from.x, from.y, from.z, 0, 0, 0)
                    instanceof VitalMoteParticle homing) {
                homing.homeTo(bowPoint(from, glove, bowed), () -> gloveNow(mc));
            }
        }
    }

    /**
     * Reads the local player's bar each client tick, thumping a heartbeat
     * when the drain takes a half heart and pulsing when a reserve half banks.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            lastHealthHalves = NO_READING;
            return;
        }
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        int health = Mth.ceil(player.getHealth());
        int reserve = overlay.shieldHalves();
        if (lastHealthHalves != NO_READING) {
            Cues cues = cuesFor(lastHealthHalves, health, lastReserveHalves, reserve,
                    overlay.reserves() && player.hurtTime == 0);
            play(player, cues);
        }
        lastHealthHalves = health;
        lastReserveHalves = reserve;
    }

    private static void play(LocalPlayer player, Cues cues) {
        Level level = player.level();
        if (cues.drained() > 0) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT,
                    SoundSource.PLAYERS, HEARTBEAT_VOLUME, HEARTBEAT_PITCH, false);
        }
        if (cues.banked() > 0) {
            Vec3 ring = player.position().add(0, player.getBbHeight() * PULSE_HEIGHT, 0);
            for (int mote = 0; mote < PULSE_MOTES; mote++) {
                double angle = TWO_PI * mote / PULSE_MOTES;
                level.addParticle(GooParticles.RESTORE_MOTE.get(), ring.x, ring.y, ring.z,
                        Math.cos(angle) * PULSE_SPEED, 0, Math.sin(angle) * PULSE_SPEED);
            }
        }
    }

    private static Vec3 gloveNow(Minecraft mc) {
        return GloveAim.handPosition(mc.gameRenderer.getMainCamera());
    }

    private static Vec3 spread(RandomSource random, double radius) {
        return new Vec3(random.nextDouble() - HALF, random.nextDouble() - HALF, random.nextDouble() - HALF)
                .scale(radius / HALF);
    }

    private static boolean drains(String abilityId) {
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        return ability != null && ability.behaviors().stream().flatMap(ReserveVisual::withDescendants)
                .anyMatch(ReserveDrainStep.class::isInstance);
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(ReserveVisual::withDescendants));
    }
}
