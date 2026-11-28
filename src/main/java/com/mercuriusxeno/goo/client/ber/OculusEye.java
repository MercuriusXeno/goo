package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.model.OculusModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The oculus's ender eye: it hovers just off the face it stands over, bobbing a little, turned toward the camera
 * drawing it, its lids shut unless the viewer looks at it. Purely cosmetic: each client turns and opens the eye for
 * its own camera.
 * decision oculus-prism-becomes-a-hovering-eye
 */
public final class OculusEye {

    /** Blocks the eye bobs either side of where it hovers. */
    static final float HOVER_AMPLITUDE = 0.03f;
    /** Ticks per bob. */
    static final float HOVER_PERIOD = 60f;
    /** The pixel rows the top lid covers when shut, the larger half of the eye's seven. */
    static final int TOP_LID_ROWS = 4;
    /** The pixel rows the bottom lid covers when shut, the smaller half. */
    static final int BOTTOM_LID_ROWS = 3;
    /** One pixel, in blocks: a lid closes a whole row at a time, so it never squeezes a texel. */
    static final float ROW = 1f / 16f;
    /** Where the eye's front starts, in the eye model's blocks. */
    static final float EYE_BOTTOM = 4.5f / 16f;
    /** Where the eye's front ends, in the eye model's blocks. */
    static final float EYE_TOP = 11.5f / 16f;
    private static final float PIXELS_PER_BLOCK = 16f;
    private static final float HALF = 0.5f;
    private static final double TWO_PI = 2 * Math.PI;

    private OculusEye() {
    }

    /**
     * What one frame of an eye draws by.
     *
     * @param eye        the eye's middle in its cell's own blocks
     * @param facing     the side the eye hovers out from, along which it bobs
     * @param sinceStood the ticks since the eye stood, with the partial tick, which its bob reads
     * @param yaw        the yaw in degrees that turns the eye toward the camera
     * @param lidClosure how shut the lids stand, 0 open to 1 shut
     * @param light      the packed light the eye draws in
     */
    public record Frame(Vec3 eye, Direction facing, float sinceStood, float yaw, float lidClosure, int light) {
    }

    /**
     * Draws the hovering eye and its lids, turned toward the camera.
     *
     * @param frame         what the eye draws by
     * @param poseStack     the pose at the cell's corner
     * @param nodeCollector the submit collector
     */
    public static void submit(Frame frame, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        Vec3 eye = frame.eye().add(Vec3.atLowerCornerOf(frame.facing().getUnitVec3i()).scale(hover(frame.sinceStood())));
        poseStack.pushPose();
        poseStack.translate(eye.x, eye.y, eye.z);
        poseStack.rotate(Axis.YP.rotationDegrees(frame.yaw()));
        poseStack.translate(-HALF, -HALF, -HALF);
        GooSubmitter.submitBakedBody(poseStack, nodeCollector, frame.light(), OculusModels.eye());
        int upper = lidRows(frame.lidClosure(), TOP_LID_ROWS);
        for (int drawn = 0; drawn < upper; drawn++) {
            submitLidRow(frame.light(), poseStack, nodeCollector, EYE_TOP - (drawn + 1) * ROW,
                    upperLidRow(upper, drawn));
        }
        int lower = lidRows(frame.lidClosure(), BOTTOM_LID_ROWS);
        for (int drawn = 0; drawn < lower; drawn++) {
            submitLidRow(frame.light(), poseStack, nodeCollector, EYE_BOTTOM + drawn * ROW, lowerLidRow(lower, drawn));
        }
        poseStack.popPose();
    }

    /**
     * The hide row an upper lid row draws: the lid slides down, so its leading edge, the seam, always shows.
     * decision oculus-eyelid-is-shaded-hide-with-a-seam
     *
     * @param shown the rows the upper lid covers
     * @param drawn the row being drawn, 0 at the eye's top
     * @return the hide row, 0 the brow to TOP_LID_ROWS - 1 the seam
     */
    static int upperLidRow(int shown, int drawn) {
        return TOP_LID_ROWS - shown + drawn;
    }

    /**
     * The hide row a lower lid row draws: the lid slides up, so its leading edge, the seam, always shows.
     * decision oculus-eyelid-is-shaded-hide-with-a-seam
     *
     * @param shown the rows the lower lid covers
     * @param drawn the row being drawn, 0 at the eye's bottom
     * @return the hide row, TOP_LID_ROWS the seam to the lid's last row at its bottom
     */
    static int lowerLidRow(int shown, int drawn) {
        return TOP_LID_ROWS + shown - 1 - drawn;
    }

    /**
     * Draws one pixel row of lid flush on the eye's front.
     *
     * @param light         the packed light
     * @param poseStack     the pose in the eye's frame
     * @param nodeCollector the submit collector
     * @param bottom        the row's bottom edge, in blocks
     * @param hideRow       the row of hide it draws
     */
    private static void submitLidRow(int light, PoseStack poseStack, SubmitNodeCollector nodeCollector, float bottom,
                                     int hideRow) {
        poseStack.pushPose();
        poseStack.translate(0f, bottom, 0f);
        GooSubmitter.submitBakedBody(poseStack, nodeCollector, light, OculusModels.lidRow(hideRow));
        poseStack.popPose();
    }

    /**
     * How many pixel rows a lid covers at a closure.
     *
     * @param shut     how shut the lids stand, 0 to 1
     * @param mostRows the rows the lid covers shut
     * @return the rows, 0 open to mostRows shut
     */
    static int lidRows(float shut, int mostRows) {
        return Math.round(Math.clamp(shut, 0f, 1f) * mostRows);
    }

    /**
     * Where the eye's middle hovers in its cell: a lift off the face it stands over.
     *
     * @param facing the side the eye hovers out from, away from the face
     * @param lift   how far off the face the eye's middle hovers, in model pixels
     * @return the eye's middle in the cell's own blocks
     */
    public static Vec3 eyeInCell(Direction facing, double lift) {
        Vec3 out = Vec3.atLowerCornerOf(facing.getUnitVec3i());
        return new Vec3(HALF, HALF, HALF).subtract(out.scale(HALF)).add(out.scale(lift / PIXELS_PER_BLOCK));
    }

    /**
     * The eye's bob off where it hovers, starting still.
     *
     * @param sinceStood the ticks since the eye stood
     * @return the offset in blocks
     */
    static float hover(float sinceStood) {
        return HOVER_AMPLITUDE * (float) Math.sin(TWO_PI * sinceStood / HOVER_PERIOD);
    }
}
