package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Knives' hold, heard while right click holds it: a chime climbing in pitch
 * at each knife formed and a ring of resonance at the last.
 * decision knives-charge-one-per-three-ticks-up-to-ten
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class KnivesCharge {

    /** The hand's place in the world: chest height, ahead of the body, toward the glove side. */
    private static final double CHEST_HEIGHT = 1.05;
    private static final double CROUCH_DROP = 0.3;
    private static final double AHEAD = 0.42;
    private static final double GLOVE_SIDE = 0.35;
    private static final float CHIME_VOLUME = 0.55f;
    private static final float CHIME_PITCH_LOW = 0.8f;
    private static final float CHIME_PITCH_SPAN = 1.2f;
    private static final float FULL_VOLUME = 0.8f;
    private static final float FULL_PITCH = 1.5f;
    private static final float RIGHT_ANGLE = 90f;

    /** The knives the local hold has chimed, so a new one chimes. */
    private static int shownLocally;

    private KnivesCharge() {
    }

    /**
     * Client tick: chimes each step of the local hold in.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            shownLocally = 0;
            return;
        }
        Charge charge = chargeOf(player);
        boolean holding = charge != null && GloveUseTracker.heldTicks() > 0;
        if (!holding) {
            shownLocally = 0;
            return;
        }
        chime(mc.level, player, charge, GloveThrowSender.selectedDelivery(selectedAbility(player)),
                GloveUseTracker.heldTicks());
    }

    private static void chime(ClientLevel level, LocalPlayer player, Charge charge, Delivery delivery,
                              int heldTicks) {
        float share = delivery.chargeShare(heldTicks);
        int shown = charge.stackedAt(heldTicks);
        if (shown > shownLocally) {
            level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, CHIME_VOLUME, CHIME_PITCH_LOW + share * CHIME_PITCH_SPAN, false);
            if (shown >= charge.stack()) {
                level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                        SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, FULL_VOLUME, FULL_PITCH, false);
            }
            shownLocally = shown;
        }
    }

    private static @Nullable String selectedAbility(Player player) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        return selection == null ? null : selection.abilityId();
    }

    /**
     * The charge block of a player's knife-forming selection.
     *
     * @param player the player
     * @return the charge, or null where the selection forms no knives
     */
    private static @Nullable Charge chargeOf(Player player) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        if (selection == null) {
            return null;
        }
        Delivery delivery = GloveThrowSender.selectedDelivery(selection.abilityId());
        return delivery.formsKnives() ? delivery.charge() : null;
    }

    /**
     * Where a player's glove hand is: before the chest, on the glove side.
     *
     * @param player     the player
     * @param gloveRight whether the glove is in the right hand
     * @param partial    the partial tick
     * @return the hand's place in the world
     */
    static Vec3 handAt(Player player, boolean gloveRight, float partial) {
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        Vec3 ahead = Vec3.directionFromRotation(0f, bodyYaw);
        Vec3 glove = Vec3.directionFromRotation(0f, bodyYaw + (gloveRight ? RIGHT_ANGLE : -RIGHT_ANGLE));
        return player.getPosition(partial).add(0, CHEST_HEIGHT - (player.isCrouching() ? CROUCH_DROP : 0), 0)
                .add(ahead.scale(AHEAD)).add(glove.scale(GLOVE_SIDE));
    }
}
