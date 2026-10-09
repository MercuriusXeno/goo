package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.SiphonStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Unmake's channel thrum: a low hum like a detuned microwave, its pitch
 * sweeping steadily low to mid and back to low, looping from the player for
 * as long as right click holds Unmake. Two layers a few percent apart in
 * pitch beat against each other for the detuning.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class UnmakeHum extends AbstractTickableSoundInstance {

    /** The thrum's low pitch, where each sweep starts and ends. */
    static final float LOW_PITCH = 0.5f;
    /** The thrum's mid pitch, the top of each sweep. */
    static final float MID_PITCH = 0.8f;
    /** Ticks one sweep takes, low to mid and back to low, two seconds. */
    static final int SWEEP_TICKS = 40;
    /** How far the second layer's pitch sits above the first's. */
    static final float DETUNE = 1.04f;
    private static final float VOLUME = 0.6f;
    private static final float HALF = 0.5f;

    /** The two layers now playing, if any. */
    private static final AtomicReference<@Nullable UnmakeHum[]> PLAYING = new AtomicReference<>();

    private final LocalPlayer player;
    private final float detune;
    private int age;

    private UnmakeHum(LocalPlayer player, RandomSource random, float detune) {
        super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, random);
        this.player = player;
        this.detune = detune;
        this.looping = true;
        this.delay = 0;
        this.volume = VOLUME;
        this.pitch = LOW_PITCH * detune;
        follow();
    }

    /**
     * Starts the thrum's two layers when Unmake's hold begins; each ends
     * itself when the hold does.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || heldUnmake(player) == null) {
            return;
        }
        UnmakeHum[] layers = PLAYING.get();
        if (layers == null || layers[0].isStopped()) {
            UnmakeHum[] started = {new UnmakeHum(player, player.getRandom(), 1f),
                new UnmakeHum(player, player.getRandom(), DETUNE)};
            PLAYING.set(started);
            for (UnmakeHum layer : started) {
                mc.getSoundManager().play(layer);
            }
        }
    }

    @Override
    public void tick() {
        if (player.isRemoved() || heldUnmake(player) == null) {
            stop();
            return;
        }
        age++;
        pitch = pitchAt(age) * detune;
        follow();
    }

    /**
     * The thrum's pitch: a steady sweep from low to mid and back to low.
     *
     * @param age ticks the thrum has played
     * @return the pitch
     */
    static float pitchAt(int age) {
        float phase = (float) (age % SWEEP_TICKS) / SWEEP_TICKS;
        float rise = phase < HALF ? phase / HALF : (1f - phase) / HALF;
        return LOW_PITCH + (MID_PITCH - LOW_PITCH) * rise;
    }

    /**
     * The Unmake the local player's glove holds while right click holds it.
     *
     * @param player the local player
     * @return the ability, or null while no Unmake is held
     */
    static @Nullable ClientAbility heldUnmake(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        boolean unmakes = ability != null && ability.behaviors().stream().anyMatch(SiphonStep.class::isInstance);
        return unmakes && GloveUseTracker.showsArea() ? ability : null;
    }

    private void follow() {
        x = player.getX();
        y = player.getY();
        z = player.getZ();
    }
}
