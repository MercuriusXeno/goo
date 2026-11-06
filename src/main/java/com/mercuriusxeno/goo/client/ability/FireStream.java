package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.FlamesStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Flames' fire stream: while right click holds Flames, the glove pours
 * particle fire, licks and embers, down its cone through {@link FireSpray}
 * each tick, aimed at what the crosshair points at.
 * flames-draws-as-particle-style-fire
 * <p>
 * A spout pinned by {@code /goodebug flames} pours in place, held and released
 * on a loop, so the operator can walk around it and watch it from any side.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class FireStream {

    /** A pinned spout's loop: it pours this many ticks, then rests until the loop's end. */
    static final long PINNED_HELD_TICKS = 40;
    static final long PINNED_LOOP_TICKS = 60;
    private static final String FLAMES_ID = "goo:blaze_flames";

    /** The spout {@code /goodebug flames} pinned, or null. */
    private static @Nullable PinnedSpout pinned;

    private FireStream() {
    }

    /**
     * A spout pinned in the world, and the game time its loop began.
     *
     * @param spout    the spout, as Flames would pour it when pinned
     * @param pinnedAt the game time the loop began
     */
    private record PinnedSpout(FireSpray.Spout spout, long pinnedAt) {
    }

    /**
     * Pins a looping spout at the glove, down the look, as Flames would pour it now.
     *
     * @return whether a spout was pinned: false with no player in a level
     */
    public static boolean pinAtGlove() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return false;
        }
        ClientAbility flames = AbilitySyncHandler.findAbility(FLAMES_ID);
        double range = flames == null ? FireSpray.FLAMES_RANGE : flames.delivery().range();
        double cone = flames == null ? FireSpray.FLAMES_CONE_DEGREES : flames.delivery().coneDegrees();
        Vec3 hand = GloveAim.handPosition(mc.gameRenderer.getMainCamera());
        Vec3 axis = aimAtCrosshair(player, hand, range);
        pinned = new PinnedSpout(new FireSpray.Spout(hand, axis, range, cone), player.level().getGameTime());
        return true;
    }

    /** Takes the pinned spout away. */
    public static void unpin() {
        pinned = null;
    }

    /**
     * Pours the held stream, and the pinned spout while its loop pours.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }
        pourHeld(mc, player, level);
        pourPinned(level);
    }

    private static void pourHeld(Minecraft mc, LocalPlayer player, ClientLevel level) {
        ClientAbility flames = selectedFlames(player);
        if (flames == null || !GloveUseTracker.runsHeld(player)) {
            return;
        }
        Vec3 hand = GloveAim.handPosition(mc.gameRenderer.getMainCamera());
        Vec3 axis = aimAtCrosshair(player, hand, flames.delivery().range());
        FireSpray.pour(level, spoutOf(flames.delivery(), hand, axis));
    }

    private static void pourPinned(ClientLevel level) {
        PinnedSpout spout = pinned;
        if (spout != null && (level.getGameTime() - spout.pinnedAt()) % PINNED_LOOP_TICKS < PINNED_HELD_TICKS) {
            FireSpray.pour(level, spout.spout());
        }
    }

    /**
     * The spout held Flames pours from: the glove, down its aim, at its delivery's reach and cone.
     *
     * @param delivery Flames' delivery
     * @param hand     the glove's world position
     * @param axis     the aim's unit vector from the glove
     * @return the spout
     */
    static FireSpray.Spout spoutOf(Delivery delivery, Vec3 hand, Vec3 axis) {
        return new FireSpray.Spout(hand, axis, delivery.range(), delivery.coneDegrees());
    }

    /**
     * Aims the stream from the glove at what the crosshair points at: the
     * look from the eye finds the block within the reach, as the server's
     * FlamesStep does, and the stream pours from the glove toward that point.
     *
     * @param player the local player
     * @param hand   the glove's world position
     * @param range  the stream's reach
     * @return the aim's unit vector from the glove
     */
    private static Vec3 aimAtCrosshair(LocalPlayer player, Vec3 hand, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 target = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : eye.add(look.scale(range));
        return target.subtract(hand).normalize();
    }

    /**
     * The Flames the local player's glove has selected.
     *
     * @param player the local player
     * @return the ability, or null while the glove selects no Flames
     */
    private static @Nullable ClientAbility selectedFlames(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        boolean burns = ability != null && ability.behaviors().stream().anyMatch(FlamesStep.class::isInstance);
        return burns ? ability : null;
    }
}
