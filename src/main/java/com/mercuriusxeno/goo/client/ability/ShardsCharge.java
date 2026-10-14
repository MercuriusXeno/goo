package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.ArmPoseKind;
import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Shards' charge, seen and heard while right click holds it: the glove
 * hand eases left across the chest with the goo in its palm swelling as
 * the charge grows, a chime climbing in pitch at each step of the charge and a
 * ring of resonance once it is full. The glove press poses the arm, so the
 * players watching see it wound up with the goo in the hand.
 * decision shards-sling-then-morph-to-flechettes
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ShardsCharge {

    /** The steps a charge chimes at, however many flecks the release throws. */
    static final int MOST_SHOWN = 9;
    /** The hand's blob against a flying blob's size, as the charge begins and once it is full. */
    static final float BLOB_SMALLEST = 0.25f;
    static final float BLOB_FULLEST = 0.8f;
    /** A remote player's blob fills over this many ticks, the charge it cannot read. */
    private static final float REMOTE_FULL_TICKS = 30f;
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

    /** The charge steps the local hold has chimed, so a new one chimes. */
    private static int shownLocally;

    private ShardsCharge() {
    }

    /**
     * The step a charge has reached at a share of a full charge, one more as
     * the release's fleck count grows, capped at the steps it chimes.
     *
     * @param charge the charge block
     * @param share  the share of a full charge, 0 to 1
     * @return the step, at least one
     */
    static int shownAt(Charge charge, float share) {
        return Math.min(MOST_SHOWN, charge.fleckCount(share));
    }

    /**
     * The hand's blob at a share of a full charge: small as the charge
     * begins, swelling to its fullest at full charge.
     *
     * @param share the share of a full charge, 0 to 1
     * @return its size against a flying blob's
     */
    static float blobScaleAt(float share) {
        return BLOB_SMALLEST + (BLOB_FULLEST - BLOB_SMALLEST) * Math.clamp(share, 0f, 1f);
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
     * @param slinging    the held selection's slinging charge, or null where it does not sling
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
        Charge charge = localCharge(player);
        return palmShareOf(charge, posed != null && posed.holding(ArmPoseKind.WIND_UP),
                charge == null ? 0f
                        : GloveThrowSender.selectedDelivery(selectedAbility(player))
                        .chargeShare(GloveUseTracker.heldTicks()));
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
        Charge charge = localCharge(player);
        boolean holding = charge != null && GloveUseTracker.heldTicks() > 0;
        if (!holding) {
            shownLocally = 0;
            return;
        }
        chime(mc.level, player, charge, GloveThrowSender.selectedDelivery(selectedAbility(player)));
    }

    private static void chime(ClientLevel level, LocalPlayer player, Charge charge, Delivery delivery) {
        float share = delivery.chargeShare(GloveUseTracker.heldTicks());
        int shown = shownAt(charge, share);
        if (shown > shownLocally) {
            level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, CHIME_VOLUME, CHIME_PITCH_LOW + share * CHIME_PITCH_SPAN, false);
            if (share >= 1f) {
                level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                        SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, FULL_VOLUME, FULL_PITCH, false);
            }
            shownLocally = shown;
        }
    }

    private static @Nullable String selectedAbility(LocalPlayer player) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        return selection == null ? null : selection.abilityId();
    }

    /**
     * The charge block of the local player's slinging selection.
     *
     * @param player the local player
     * @return the charge, or null where the selection does not sling
     */
    private static @Nullable Charge localCharge(LocalPlayer player) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        if (selection == null) {
            return null;
        }
        Charge charge = GloveThrowSender.selectedDelivery(selection.abilityId()).charge();
        return charge.slings() ? charge : null;
    }

    /**
     * Draws the goo swelling in the hand of every player whose arm is wound
     * up, but the local player's in first person, whose glove's palm blob
     * swells instead.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partial;
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        for (Player player : mc.level.players()) {
            AbilityPoses.Posed posed = AbilityPoses.posedAt(player.getId());
            if (woundInTheWorld(posed, firstPerson && player == mc.player)) {
                drawWorldBlob(pose, buffers, new WoundHand(player, mc.player, posed), now, partial, camera);
            }
        }
        buffers.endBatch();
    }

    private static boolean woundInTheWorld(AbilityPoses.Posed posed, boolean ownFirstPerson) {
        return posed != null && posed.holding(ArmPoseKind.WIND_UP) && !ownFirstPerson;
    }

    /**
     * A wound-up player's hand to draw the goo in.
     *
     * @param player the player
     * @param local  the local player
     * @param posed  the player's wind-up
     */
    private record WoundHand(Player player, LocalPlayer local, AbilityPoses.Posed posed) {
    }

    private static void drawWorldBlob(PoseStack pose, MultiBufferSource buffers, WoundHand wound, double now,
                                      float partial, Vec3 camera) {
        double age = now - wound.posed().since();
        float eased = AbilityPoses.heldAt(ArmPoseKind.WIND_UP, age, 0f).weight();
        Vec3 hand = handAt(wound.player(), wound.posed().gloveRight(), eased, partial);
        pose.pushPose();
        pose.translate(hand.x - camera.x, hand.y - camera.y, hand.z - camera.z);
        GooFlightRenderer.renderBlob(pose, buffers, GooTypes.CRYSTAL, (float) now,
                blobScaleAt(shareFor(wound.player(), wound.local(), age)));
        pose.popPose();
    }

    /**
     * Where a wound-up player's glove hand is: before the chest, eased from
     * the glove side across toward the other.
     *
     * @param player     the player
     * @param gloveRight whether the glove is in the right hand
     * @param eased      how far the wind-up has eased the hand across, 0 to 1
     * @param partial    the partial tick
     * @return the hand's place in the world
     */
    private static Vec3 handAt(Player player, boolean gloveRight, float eased, float partial) {
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        Vec3 ahead = Vec3.directionFromRotation(0f, bodyYaw);
        Vec3 glove = Vec3.directionFromRotation(0f, bodyYaw + (gloveRight ? RIGHT_ANGLE : -RIGHT_ANGLE));
        return player.getPosition(partial).add(0, CHEST_HEIGHT - (player.isCrouching() ? CROUCH_DROP : 0), 0)
                .add(ahead.scale(AHEAD)).add(glove.scale(GLOVE_SIDE - ACROSS * eased));
    }

    /**
     * The share of a full charge a player's hand shows: the local player's
     * from the charge it holds, another's from how long its arm has been wound up.
     *
     * @param player the player whose hand it is
     * @param local  the local player
     * @param age    ticks the player's arm has been wound up
     * @return the share, 0 to 1
     */
    private static float shareFor(Player player, LocalPlayer local, double age) {
        if (player == local) {
            String ability = selectedAbility(local);
            return localCharge(local) == null ? 0f
                    : GloveThrowSender.selectedDelivery(ability).chargeShare(GloveUseTracker.heldTicks());
        }
        return Math.min(1f, (float) age / REMOTE_FULL_TICKS);
    }
}
