package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.LevelStep;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Level's cursor: while right click holds a Level channel, rock's dust
 * disc lies flat on the plane of the face the hold began on, in line with
 * the block the crosshair rests on, never turning with the face the
 * crosshair later rests on.
 * decision flatten-disc-cursor-breaks-above-the-plane
 */
public final class LevelCursor {

    private LevelCursor() {
    }

    /**
     * Hands the shared path the disc while a Level hold runs on a face,
     * sorted by the cell it lies on.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the disc's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ChannelAim.FacePlane plane = GloveUseTracker.pressPlane();
        if (player == null || mc.level == null || plane == null
                || !showsCursor(selectedAbility(player), GloveUseTracker.runsHeld(player))) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        BlockPos cell = cellUnder(CameraEye.of(mc, player, partialTick).eye(),
                GloveThrowSender.drawnCursorPoint(player, partialTick), plane);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        BurnoutFrame burnout = new BurnoutFrame(frame.poseStack(), mc.renderBuffers().bufferSource(), camera,
                mc.level.getGameTime() + partialTick);
        draws.accept(new SortedDraw(Vec3.atCenterOf(cell).distanceToSqr(camera),
                () -> RockExplosionVisual.INSTANCE.renderCursor(burnout, cell, plane.face())));
    }

    /**
     * The cell the disc lies on: out from the press plane, in line with the
     * block the cursor's point rests on, read along the line from the eye.
     * decision diagnose-and-correct-eye-aimed-visuals-jumping-on-sneak
     *
     * @param eye         the eye the cursor's ray leaves from, {@link CameraEye#of}'s
     * @param cursorPoint the drawn cursor's world point
     * @param plane       the face the hold began on
     * @return the cell
     */
    static BlockPos cellUnder(Vec3 eye, Vec3 cursorPoint, ChannelAim.FacePlane plane) {
        return plane.cellOutFrom(new ChannelAim(cursorPoint, plane).aimedBlock(eye));
    }

    /**
     * The synced copy of the ability the player's glove has selected.
     *
     * @param player the local player
     * @return the ability, or null with no glove or no synced copy
     */
    private static @Nullable ClientAbility selectedAbility(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        return abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
    }

    /**
     * Whether the cursor shows: the selected ability levels and right click holds it.
     *
     * @param ability the selected ability's synced copy, or null when none
     * @param useHeld whether right click holds a live press
     * @return true while the cursor shows
     */
    static boolean showsCursor(@Nullable ClientAbility ability, boolean useHeld) {
        return useHeld && ability != null && ability.behaviors().stream().anyMatch(LevelStep.class::isInstance);
    }
}
