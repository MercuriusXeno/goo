package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Aeon's Haste on a player: speed and haste with their particles off, and
 * the golden haste overlay on the player's model in their place, refreshed
 * for every watcher while the held effect stands and cleared when it ends.
 * haste-stacks-speed-under-the-golden-overlay
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class Haste {

    /** The duration a glove's Haste lays its effects for: until the held effect ends and clears them. */
    public static final int HELD = MobEffectInstance.INFINITE_DURATION;
    /** Ticks between one overlay refresh and the next, so a player who starts watching sees it within a second. */
    static final int OVERLAY_REFRESH_TICKS = 20;
    /** How long each refresh lasts: past the next refresh and its fade, so the overlay never dips. */
    static final int OVERLAY_TICKS = OVERLAY_REFRESH_TICKS * 3;
    private static final int OVERLAY_CLEARED = 0;
    private static final boolean NOT_AMBIENT = false;
    private static final boolean NO_PARTICLES = false;
    private static final boolean SHOWS_ICON = true;

    private Haste() {
    }

    /**
     * Lays Haste's effects on a target, their particles off, and starts its overlay.
     *
     * @param target   the hasted entity
     * @param speed    the speed amplifier
     * @param haste    the haste amplifier
     * @param duration the ticks they last, or {@link #HELD} until the held effect ends
     */
    public static void lay(LivingEntity target, int speed, int haste, int duration) {
        target.addEffect(new MobEffectInstance(MobEffects.SPEED, duration, speed, NOT_AMBIENT, NO_PARTICLES, SHOWS_ICON));
        target.addEffect(new MobEffectInstance(MobEffects.HASTE, duration, haste, NOT_AMBIENT, NO_PARTICLES, SHOWS_ICON));
        sendOverlay(target, OVERLAY_TICKS);
    }

    /**
     * Clears Haste from a player as its held effect ends: the effects go and
     * the overlay drops on every client.
     *
     * @param player the player
     */
    static void clear(ServerPlayer player) {
        player.removeEffect(MobEffects.SPEED);
        player.removeEffect(MobEffects.HASTE);
        sendOverlay(player, OVERLAY_CLEARED);
    }

    /**
     * Refreshes the overlay of each player whose held effects lay Haste.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % OVERLAY_REFRESH_TICKS != 0
                || !player.hasData(GooAttachments.HELD_EFFECTS)) {
            return;
        }
        boolean hasted = player.getData(GooAttachments.HELD_EFFECTS).held().stream()
                .anyMatch(held -> held.lays().contains(LaidState.HASTE));
        if (hasted) {
            sendOverlay(player, OVERLAY_TICKS);
        }
    }

    private static void sendOverlay(LivingEntity target, int ticks) {
        EntityVisuals.sendToWatchers(target, new AilmentPayload(target.getId(), AilmentKind.HASTE, ticks));
    }
}
