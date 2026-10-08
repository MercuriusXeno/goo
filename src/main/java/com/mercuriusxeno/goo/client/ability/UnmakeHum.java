package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Unmake's channel hum: a low, warbling destabilizing hum looping from the
 * player for as long as right click holds Unmake, its pitch wavering and
 * jittering, and gone the moment the hold ends.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class UnmakeHum extends AbstractTickableSoundInstance {

    /** The hum's resting pitch, low. */
    static final float BASE_PITCH = 0.55f;
    /** How far the slow warble swings the pitch either side. */
    static final float WARBLE = 0.12f;
    /** Warble cycles a tick. */
    static final float WARBLE_RATE = 0.07f;
    /** How far the fast jitter swings the pitch either side. */
    static final float JITTER = 0.04f;
    private static final float VOLUME = 0.7f;
    private static final double TWO_PI = 2 * Math.PI;
    /** Stretches a 0 to 1 share across -1 to 1. */
    private static final float SIGNED_SPAN = 2f;
    /** The hum now playing, held on the client thread alone. */
    private static final Playing PLAYING = new Playing();

    /** Holds the hum now playing, if any. */
    private static final class Playing {
        private @Nullable UnmakeHum hum;
    }

    private final LocalPlayer player;
    private int age;

    private UnmakeHum(LocalPlayer player, RandomSource random) {
        super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, random);
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = VOLUME;
        this.pitch = BASE_PITCH;
        follow();
    }

    /**
     * Starts the hum when Unmake's hold begins; the hum ends itself when it does.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || UnmakeWaves.heldUnmake(player) == null) {
            return;
        }
        UnmakeHum hum = PLAYING.hum;
        if (hum == null || hum.isStopped()) {
            UnmakeHum started = new UnmakeHum(player, player.getRandom());
            PLAYING.hum = started;
            mc.getSoundManager().play(started);
        }
    }

    @Override
    public void tick() {
        if (player.isRemoved() || UnmakeWaves.heldUnmake(player) == null) {
            stop();
            return;
        }
        age++;
        pitch = pitchAt(age, random.nextFloat());
        follow();
    }

    /**
     * The hum's pitch: its resting pitch, warbling slowly and jittering fast.
     *
     * @param age    ticks the hum has played
     * @param jitter a fresh random share, 0 to 1
     * @return the pitch
     */
    static float pitchAt(int age, float jitter) {
        return BASE_PITCH + WARBLE * (float) Math.sin(age * WARBLE_RATE * TWO_PI)
                + JITTER * (jitter * SIGNED_SPAN - 1);
    }

    private void follow() {
        x = player.getX();
        y = player.getY();
        z = player.getZ();
    }
}
