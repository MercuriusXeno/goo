package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.program.ColonizeStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import java.util.List;

/**
 * Shroom's held ghost: a shell of drifting mauve spore motes at Colonize's
 * reach, turning slowly and bobbing as a cloud does, with rings born at the
 * aim point travelling outward to the same reach.
 * held-visual-ghosts-the-landing-in-two-passes
 * colonize-blob-grows-the-network
 */
public final class ShroomHeldGhost implements HeldGhostVisual {

    /** The one instance the held dome renderer holds. */
    public static final ShroomHeldGhost INSTANCE = new ShroomHeldGhost();

    /** Motes on the shell, spread evenly by the golden angle. */
    static final int MOTES = 480;
    /** A mote's half width, in blocks. */
    private static final float MOTE_HALF = 0.035f;
    /** The shell's mauve. */
    private static final int MOTE_RGB = 0xB57FC0;
    /** A mote's alpha at full held opacity. */
    private static final float MOTE_ALPHA = 0.7f;
    /** How fast the shell turns, in radians per second. */
    private static final double TURN_PER_SECOND = 0.25;
    /** How far a mote bobs off the shell, as a share of the radius. */
    private static final double BOB_SHARE = 0.04;
    private static final double BOB_PER_SECOND = 1.3;
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    private static final double HALF = 0.5;
    /** A unit shell spans two from its top to its bottom. */
    private static final double TOP_TO_BOTTOM = 2;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;
    private static final int OPAQUE = 255;

    private ShroomHeldGhost() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.SHROOM;
    }

    /**
     * The dome and the outward rings at the colonize step's radius, falling
     * back to the area's size where the program holds none.
     *
     * @param area      the ability's synced area
     * @param behaviors the ability's synced program
     * @return the ghost
     */
    @Override
    public HeldGhost ghost(AbilityArea area, List<Step> behaviors) {
        float reach = SyncedSteps.first(behaviors, ColonizeStep.class).map(ColonizeStep::radius)
                .filter(radius -> radius.variables().isEmpty()).map(radius -> radius.evaluateFloat(Variables.NONE))
                .filter(radius -> radius > 0).orElse((float) area.size());
        return HeldGhost.outwardTo(reach);
    }

    @Override
    public List<HeldLayer> heldLayers() {
        return List.of(new HeldLayer(GooRenderTypes.SPORE_SHELL_TYPE, GooRenderTypes.SPORE_SHELL_THROUGH_BLOCKS_TYPE,
                ShroomHeldGhost::emitMotes));
    }

    /**
     * Where a mote sits on a unit shell: an even spiral down the sphere,
     * turned about the vertical by the clock.
     *
     * @param index the mote's index
     * @param turn  the shell's turn about the vertical, in radians
     * @return the mote's unit offset: x, y and z
     */
    static double[] moteOnUnitShell(int index, double turn) {
        double y = 1 - TOP_TO_BOTTOM * (index + HALF) / MOTES;
        double ring = Math.sqrt(Math.max(0, 1 - y * y));
        double around = index * GOLDEN_ANGLE + turn;
        return new double[] {Math.cos(around) * ring, y, Math.sin(around) * ring};
    }

    private static void emitMotes(PoseStack.Pose pose, VertexConsumer c, HeldGhost ghost, Direction face,
                                  float opacity, double nowSeconds) {
        int color = ARGB.color(Math.round(Mth.clamp(MOTE_ALPHA * opacity, 0f, 1f) * OPAQUE), MOTE_RGB);
        double turn = nowSeconds * TURN_PER_SECOND;
        for (int i = 0; i < MOTES; i++) {
            double[] unit = moteOnUnitShell(i, turn);
            double radius = ghost.domeRadius() * (1 + BOB_SHARE * Math.sin(nowSeconds * BOB_PER_SECOND + i));
            emitMote(pose, c, (float) (unit[X] * radius), (float) (unit[Y] * radius), (float) (unit[Z] * radius),
                    color);
        }
    }

    /**
     * A mote as two crossed quads, so it reads from any side.
     *
     * @param pose  the pose entry
     * @param c     the vertex consumer
     * @param x     the mote's x about the dome's center
     * @param y     the mote's y about the dome's center
     * @param z     the mote's z about the dome's center
     * @param color the mote's color
     */
    private static void emitMote(PoseStack.Pose pose, VertexConsumer c, float x, float y, float z, int color) {
        c.addVertex(pose, x - MOTE_HALF, y - MOTE_HALF, z).setColor(color);
        c.addVertex(pose, x + MOTE_HALF, y - MOTE_HALF, z).setColor(color);
        c.addVertex(pose, x + MOTE_HALF, y + MOTE_HALF, z).setColor(color);
        c.addVertex(pose, x - MOTE_HALF, y + MOTE_HALF, z).setColor(color);
        c.addVertex(pose, x, y - MOTE_HALF, z - MOTE_HALF).setColor(color);
        c.addVertex(pose, x, y - MOTE_HALF, z + MOTE_HALF).setColor(color);
        c.addVertex(pose, x, y + MOTE_HALF, z + MOTE_HALF).setColor(color);
        c.addVertex(pose, x, y + MOTE_HALF, z - MOTE_HALF).setColor(color);
    }
}
