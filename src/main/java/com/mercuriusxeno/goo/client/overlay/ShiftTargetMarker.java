package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.FungusAim;
import com.mercuriusxeno.goo.ability.program.ShiftStep;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ability.SporeMotes;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * Fungal Shift's target marker: while the press is held, the fungus the
 * release would land on flares, its own shape swollen and quick-pulsing in
 * magenta, and a slim column of spore motes rises from where the player
 * would stand, both drawn through walls so the mark reads behind them.
 * fungal-shift-blinks-to-the-aimed-fungus
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ShiftTargetMarker {

    /** How far the flare swells past the block. */
    private static final float FLARE_SWELL = 1.22f;
    /** The flare's alpha at full pulse. */
    private static final float FLARE_ALPHA = 0.9f;
    /** The flare's quick pulse, radians per second. */
    private static final float FLARE_PULSE_PER_SECOND = 9f;
    /** Motes in the column. */
    private static final int COLUMN_MOTES = 24;
    /** The column's height, in blocks. */
    private static final double COLUMN_HEIGHT = 2.2;
    /** How fast a mote climbs the column, in column heights per second. */
    private static final double CLIMB_PER_SECOND = 0.45;
    /** How far a mote wanders from the column's axis, in blocks. */
    private static final double COLUMN_WANDER = 0.12;
    private static final float MOTE_HALF = 0.04f;
    private static final int MOTE_RGB = 0xE070F0;
    private static final float MOTE_ALPHA = 0.85f;
    private static final float MILLIS_PER_SECOND = 1000f;
    private static final double GOLDEN_TURN = 2.399963;
    private static final int OPAQUE = 255;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    /** The last fungal target resolved, kept for the tick and look it was resolved at. */
    private static Optional<BlockPos> target = Optional.empty();
    private static long targetAt = Long.MIN_VALUE;
    private static float targetYaw;
    private static float targetPitch;

    private ShiftTargetMarker() {
    }

    /**
     * Draws the marker after the translucent world while a held Fungal Shift aims at fungus.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        ClientAbility ability = selectedAbility(player);
        if (!showsMarker(ability, GloveUseTracker.showsArea())) {
            return;
        }
        aimedFungus(player, ability).ifPresent(pos -> draw(mc, event.getPoseStack(), pos));
    }

    /**
     * Whether the marker shows for the selected ability: it shifts to fungus,
     * and its indicator's rule holds for the press.
     *
     * @param ability the selected ability's synced copy, or null when none
     * @param useHeld whether right click holds a live press
     * @return true while the marker shows
     */
    static boolean showsMarker(@Nullable ClientAbility ability, boolean useHeld) {
        return ability != null && ShiftStep.fungusRange(ability.behaviors()).isPresent()
                && ability.indicator().shows(useHeld);
    }

    private static @Nullable ClientAbility selectedAbility(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        return abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
    }

    /**
     * The fungus the release would land on, resolved through the aim the
     * server's shift takes, once per tick and look.
     *
     * @param player  the local player
     * @param ability the selected Fungal Shift
     * @return the fungus, or empty when the look names none
     */
    private static Optional<BlockPos> aimedFungus(LocalPlayer player, ClientAbility ability) {
        long tick = player.level().getGameTime();
        if (tick != targetAt || player.getYRot() != targetYaw || player.getXRot() != targetPitch) {
            target = FungusAim.aimedFungus(player.level(), player,
                    ShiftStep.reachOf(player, ShiftStep.fungusRange(ability.behaviors()).orElseThrow()));
            targetAt = tick;
            targetYaw = player.getYRot();
            targetPitch = player.getXRot();
        }
        return target;
    }

    private static void draw(Minecraft mc, PoseStack poseStack, BlockPos pos) {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        float seconds = Util.getMillis() / MILLIS_PER_SECOND;
        RenderType glow = GooRenderTypes.fungusGlow(mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location());
        FungusXray.drawBlock(mc, poseStack, buffers.getBuffer(glow), camera, pos,
                FungusXray.instance(FungusXray.glowColor(FungusXray.pulse(seconds, FLARE_PULSE_PER_SECOND),
                        FLARE_ALPHA)), FLARE_SWELL);
        buffers.endBatch(glow);
        RenderType motes = GooRenderTypes.SPORE_SHELL_THROUGH_BLOCKS_TYPE;
        drawColumn(poseStack.last(), buffers.getBuffer(motes), ShiftStep.standingOn(mc.level, pos).subtract(camera),
                seconds);
        buffers.endBatch(motes);
    }

    /**
     * Where a column mote sits above the stand point: climbing at its own
     * phase, wrapping to the bottom at the top, wandering a little about the axis.
     *
     * @param index   the mote's index
     * @param seconds seconds on the real-time clock
     * @return the mote's offset from the stand point: x, y and z
     */
    static double[] columnMote(int index, double seconds) {
        double climb = (seconds * CLIMB_PER_SECOND + (double) index / COLUMN_MOTES) % 1.0;
        double around = index * GOLDEN_TURN + seconds;
        return new double[] {Math.cos(around) * COLUMN_WANDER, climb * COLUMN_HEIGHT, Math.sin(around) * COLUMN_WANDER};
    }

    private static void drawColumn(PoseStack.Pose pose, VertexConsumer c, Vec3 stand, double seconds) {
        for (int i = 0; i < COLUMN_MOTES; i++) {
            double[] at = columnMote(i, seconds);
            double climb = at[Y] / COLUMN_HEIGHT;
            float fade = (float) Math.sin(climb * Math.PI);
            int color = ARGB.color(Math.round(Mth.clamp(MOTE_ALPHA * fade, 0f, 1f) * OPAQUE), MOTE_RGB);
            SporeMotes.emit(pose, c, (float) (stand.x + at[X]), (float) (stand.y + at[Y]),
                    (float) (stand.z + at[Z]), MOTE_HALF, color);
        }
    }
}
