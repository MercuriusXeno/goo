package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.ArmPoseKind;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Knives' hold, seen and heard while right click holds it: the glove's
 * blob hops off the palm as the hold begins and floats ahead of and above
 * the hand as it eases left across the chest, swelling as the hold grows,
 * with a chime climbing in pitch at each knife formed and a ring of
 * resonance at the last. Letting go settles the blob back onto the palm.
 * The glove press poses the arm, so the players watching see it wound up
 * with the blob afloat.
 * decision knives-sling-then-morph-to-flechettes
 * decision knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
 * decision knives-charge-one-per-three-ticks-up-to-ten
 * decision charge-swells-the-palm-blob
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class KnivesCharge {

    /** A remote player's blob fills over this many ticks, the charge it cannot read. */
    private static final float REMOTE_FULL_TICKS = 60f;
    /** The hand's place in the world: chest height, ahead of the body, eased across from the glove side. */
    private static final double CHEST_HEIGHT = 1.05;
    private static final double CROUCH_DROP = 0.3;
    private static final double AHEAD = 0.42;
    private static final double GLOVE_SIDE = 0.35;
    private static final double ACROSS = 0.45;
    /**
     * The glove's palm blob against its own rest size, as the charge begins
     * and once it is full; at its fullest the shell stays inside the hand's
     * six-pixel width.
     */
    static final float PALM_REST = 1f;
    static final float PALM_FULLEST = 1.2f;
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
     * The glove's palm blob at a share of a full charge: its rest size as
     * the charge begins, swelling to its fullest at full charge.
     * decision charge-swells-the-palm-blob
     *
     * @param share the share of a full charge, 0 to 1
     * @return its size against its rest size
     */
    public static float palmScaleAt(float share) {
        return PALM_REST + (PALM_FULLEST - PALM_REST) * Math.clamp(share, 0f, 1f);
    }

    /**
     * The share of a full charge the palm blob shows: none unless a slinging
     * charge is held with the arm wound up.
     *
     * @param slinging    the held selection's knife-forming charge, or null where it forms none
     * @param woundUp     whether the glove arm holds its wind-up
     * @param chargeShare the share the held ticks reach, 0 to 1
     * @return the share, 0 to 1
     */
    static float palmShareOf(@Nullable Charge slinging, boolean woundUp, float chargeShare) {
        return slinging == null || !woundUp ? 0f : Math.clamp(chargeShare, 0f, 1f);
    }

    /**
     * The share of a full charge the local player's glove shows in its palm,
     * for the glove the local player holds; any other glove shows none.
     *
     * @param glove the glove being drawn
     * @return the share, 0 to 1
     */
    public static float localPalmShare(ItemStack glove) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || glove != player.getMainHandItem() && glove != player.getOffhandItem()) {
            return 0f;
        }
        AbilityPoses.Posed posed = AbilityPoses.posedAt(player.getId());
        Charge charge = chargeOf(player);
        return palmShareOf(charge, posed != null && posed.holding(ArmPoseKind.WIND_UP),
                charge == null ? 0f : localChargeShare(player));
    }

    /**
     * Client tick: holds every Knives charge's blob afloat and chimes each
     * step of the local hold in.
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
        holdTheFloatingBlobs(mc.level.getGameTime());
        Charge charge = chargeOf(player);
        boolean holding = charge != null && GloveUseTracker.heldTicks() > 0;
        if (!holding) {
            shownLocally = 0;
            return;
        }
        chime(mc.level, player, charge, GloveThrowSender.selectedDelivery(selectedAbility(player)),
                GloveUseTracker.heldTicks());
    }

    /**
     * Holds the blob of every player whose Knives hold stands afloat this
     * tick, through the wind-up.
     * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
     *
     * @param now the level's game time
     */
    static void holdTheFloatingBlobs(long now) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (Player player : level.players()) {
            AbilityPoses.Posed posed = AbilityPoses.posedAt(player.getId());
            if (posed != null) {
                holdTheFloatingBlob(player, posed, now);
            }
        }
    }

    private static void holdTheFloatingBlob(Player player, AbilityPoses.Posed posed, long now) {
        if (floatsAt(posed)) {
            LiftedPalmBlob.floated(player.getId(), player.getEyePosition(), (long) posed.since(), now);
        }
    }

    /**
     * Whether a pose holds the blob afloat: through the wind-up the held
     * right click keeps, the blob settling once it is let go.
     * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
     *
     * @param posed the pose
     * @return true while the blob floats
     */
    static boolean floatsAt(AbilityPoses.Posed posed) {
        return posed.holding(ArmPoseKind.WIND_UP);
    }

    /**
     * How far a caster's hand has eased across the chest now.
     *
     * @param casterId the caster's entity id
     * @param now      the game time, with the frame's partial tick
     * @return the share across, 0 at the glove side to 1 at the wind-up's furthest
     */
    static float acrossOf(int casterId, double now) {
        AbilityPoses.Posed posed = AbilityPoses.posedAt(casterId);
        return posed == null ? 0f : AbilityPoses.acrossAt(posed, now - posed.since());
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

    private static float localChargeShare(Player local) {
        return GloveThrowSender.selectedDelivery(selectedAbility(local)).chargeShare(GloveUseTracker.heldTicks());
    }

    /**
     * How far a caster's hold swells the floating blob: by the share of the
     * charge it holds while its arm is wound up, and not at all otherwise.
     * decision charge-swells-the-palm-blob
     *
     * @param caster the caster
     * @param now    the game time, with the frame's partial tick
     * @return the swell against the blob's size, the palm's rest size with no wind-up
     */
    public static float liftedSwellOf(Player caster, double now) {
        AbilityPoses.Posed posed = AbilityPoses.posedAt(caster.getId());
        LocalPlayer local = Minecraft.getInstance().player;
        if (posed == null || local == null) {
            return PALM_REST;
        }
        return posed.holding(ArmPoseKind.WIND_UP) ? palmScaleAt(shareFor(caster, local, now - posed.since()))
                : PALM_REST;
    }

    /**
     * Where a player's glove hand is: before the chest, eased from the glove
     * side across toward the other.
     *
     * @param player     the player
     * @param gloveRight whether the glove is in the right hand
     * @param eased      how far the wind-up has eased the hand across, 0 to 1
     * @param partial    the partial tick
     * @return the hand's place in the world
     */
    static Vec3 handAt(Player player, boolean gloveRight, float eased, float partial) {
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        Vec3 ahead = Vec3.directionFromRotation(0f, bodyYaw);
        Vec3 glove = Vec3.directionFromRotation(0f, bodyYaw + (gloveRight ? RIGHT_ANGLE : -RIGHT_ANGLE));
        return player.getPosition(partial).add(0, CHEST_HEIGHT - (player.isCrouching() ? CROUCH_DROP : 0), 0)
                .add(ahead.scale(AHEAD)).add(glove.scale(GLOVE_SIDE - ACROSS * eased));
    }

    /**
     * The share of a full charge a player's blob shows: the local player's
     * from the charge it holds, another's from how long its arm has been wound up.
     *
     * @param player the player whose blob it is
     * @param local  the local player
     * @param age    ticks the player's arm has been wound up
     * @return the share, 0 to 1
     */
    private static float shareFor(Player player, LocalPlayer local, double age) {
        if (player == local) {
            return chargeOf(local) == null ? 0f : localChargeShare(local);
        }
        return Math.min(1f, (float) age / REMOTE_FULL_TICKS);
    }
}
