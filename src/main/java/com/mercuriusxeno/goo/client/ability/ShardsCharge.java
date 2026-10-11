package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.ArmPoseKind;
import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.network.ChargeHoldPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Shards' charge, seen and heard while right click holds it: the glove arm
 * winds up to the opposite shoulder, where glass knives gather in a fan,
 * one more as the charge grows, each arriving with a chime that climbs in
 * pitch, and a ring of resonance once the charge is full. The hold goes to
 * the server, so the players watching see the arm wound up and the fan.
 * decision shards-sling-then-morph-to-flechettes
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ShardsCharge {

    /** The most knives the fan at the shoulder shows, however many the release throws. */
    static final int MOST_SHOWN = 9;
    /** Degrees the fan opens across, edge to edge, at its fullest. */
    static final float FAN_DEGREES = 110f;
    /** The fan's knives against a thrown knife's size. */
    private static final float FAN_SCALE = 0.55f;
    /** Ticks a knife takes to grow into the fan. */
    private static final float GROW_TICKS = 3f;
    /** A remote player's fan fills over this many ticks, the charge it cannot read. */
    private static final float REMOTE_FULL_TICKS = 30f;
    /** The fan's place: shoulder height, ahead of the chest and toward the opposite shoulder. */
    private static final double SHOULDER_HEIGHT = 1.38;
    private static final double CROUCH_DROP = 0.3;
    private static final double AHEAD = 0.42;
    private static final double ACROSS = 0.3;
    private static final float CHIME_VOLUME = 0.55f;
    private static final float CHIME_PITCH_LOW = 0.8f;
    private static final float CHIME_PITCH_SPAN = 1.2f;
    private static final float FULL_VOLUME = 0.8f;
    private static final float FULL_PITCH = 1.5f;
    private static final float RIGHT_ANGLE = 90f;
    private static final double TO_RADIANS = Math.PI / 180.0;
    private static final float HALF = 0.5f;
    /** How far a fanned knife leans ahead of the chest, against its upright. */
    private static final double LEAN_AHEAD = 0.25;
    /** Ticks after one knife each next knife starts to grow into the fan. */
    private static final float GROW_STAGGER = 0.5f;

    /** The knives the local fan shows now, so a new one chimes. */
    private static int shownLocally;
    /** Whether the server has been told the local hold began. */
    private static boolean holdSent;

    private ShardsCharge() {
    }

    /**
     * The knives a fan shows at a share of a full charge: one more as the
     * charge grows, the release's count capped at what the fan shows.
     *
     * @param charge the charge block
     * @param share  the share of a full charge, 0 to 1
     * @return the knives shown, at least one
     */
    static int shownAt(Charge charge, float share) {
        return Math.min(MOST_SHOWN, charge.fleckCount(share));
    }

    /**
     * Each fanned knife's heading in the fan's own plane: up and fanned out
     * evenly, the first toward the glove side.
     *
     * @param index the knife's place in the fan
     * @param count the knives shown
     * @return the knife's angle from upright, in degrees, positive toward the glove side
     */
    static float fanAngle(int index, int count) {
        if (count <= 1) {
            return 0f;
        }
        return FAN_DEGREES * (HALF - (float) index / (count - 1));
    }

    /**
     * Client tick: follows the local hold, posing the arm, telling the
     * server as the hold begins and ends, and chiming each knife in.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            shownLocally = 0;
            holdSent = false;
            return;
        }
        Charge charge = localCharge(player);
        boolean holding = charge != null && GloveUseTracker.heldTicks() > 0;
        if (holding != holdSent) {
            holdSent = holding;
            followHold(mc, player, holding);
        }
        if (!holding) {
            shownLocally = 0;
            return;
        }
        chime(mc.level, player, charge, GloveThrowSender.selectedDelivery(selectedAbility(player)));
    }

    /**
     * Tells the server the local hold began or ended, and winds the local
     * arm up, or lets a wound arm fall where no fling has taken over.
     *
     * @param mc      the client
     * @param player  the local player
     * @param holding whether the hold began
     */
    private static void followHold(Minecraft mc, LocalPlayer player, boolean holding) {
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundCustomPayloadPacket(new ChargeHoldPayload(holding)));
        }
        AbilityPoses.Posed posed = AbilityPoses.posedAt(player.getId());
        if (holding) {
            AbilityPoses.pose(player.getId(), ArmPoseKind.WIND_UP, gloveOnTheRight(player));
        } else if (posed != null && posed.kind() == ArmPoseKind.WIND_UP) {
            AbilityPoses.pose(player.getId(), ArmPoseKind.NONE, false);
        }
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

    private static boolean gloveOnTheRight(Player player) {
        boolean inMain = player.getMainHandItem().getItem() instanceof GooGloveItem;
        HumanoidArm gloveArm = inMain ? player.getMainArm() : player.getMainArm().getOpposite();
        return gloveArm == HumanoidArm.RIGHT;
    }

    /**
     * Draws the fan of gathering knives at the opposite shoulder of every
     * player whose arm is wound up.
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
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.CRYSTAL_SHARD_TYPE));
        for (Player player : mc.level.players()) {
            AbilityPoses.Posed posed = AbilityPoses.posedAt(player.getId());
            if (posed != null && posed.kind() == ArmPoseKind.WIND_UP) {
                double age = now - posed.since();
                emitFan(quads, player, posed.gloveRight(), shownFor(player, mc.player, age), age, partial, camera);
            }
        }
        buffers.endBatch(GooRenderTypes.CRYSTAL_SHARD_TYPE);
    }

    /**
     * The knives a player's fan shows: the local player's from the charge
     * it holds, another's from how long its arm has been wound up.
     *
     * @param player the player whose fan it is
     * @param local  the local player
     * @param age    ticks the player's arm has been wound up
     * @return the knives shown
     */
    private static float shownFor(Player player, LocalPlayer local, double age) {
        if (player == local) {
            Charge charge = localCharge(local);
            String ability = selectedAbility(local);
            return charge == null ? 0 : shownAt(charge,
                    GloveThrowSender.selectedDelivery(ability).chargeShare(GloveUseTracker.heldTicks()));
        }
        return Math.max(1, Math.round(MOST_SHOWN * Math.min(1f, (float) age / REMOTE_FULL_TICKS)));
    }

    private static void emitFan(FlatQuadContext quads, Player player, boolean gloveRight, float shown, double age,
                                float partial, Vec3 camera) {
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        Vec3 ahead = Vec3.directionFromRotation(0f, bodyYaw);
        Vec3 glove = Vec3.directionFromRotation(0f, bodyYaw + (gloveRight ? RIGHT_ANGLE : -RIGHT_ANGLE));
        Vec3 base = player.getPosition(partial).add(0, SHOULDER_HEIGHT - (player.isCrouching() ? CROUCH_DROP : 0), 0)
                .add(ahead.scale(AHEAD)).subtract(glove.scale(ACROSS));
        int count = Math.max(1, (int) shown);
        for (int index = 0; index < count; index++) {
            double angle = fanAngle(index, count) * TO_RADIANS;
            Vec3 heading = new Vec3(0, Math.cos(angle), 0).add(glove.scale(Math.sin(angle)))
                    .add(ahead.scale(LEAN_AHEAD)).normalize();
            float grow = Math.clamp((float) age / GROW_TICKS - index * GROW_STAGGER, 0f, 1f);
            float scale = FAN_SCALE * grow;
            if (scale > 0f) {
                Vec3 point = base.add(heading.scale((GlassKunai.POINT_LENGTH + GlassKunai.HEEL_LENGTH) * scale));
                GlassKunai.emit(quads, point.subtract(camera), heading, 0f, scale);
            }
        }
    }
}
