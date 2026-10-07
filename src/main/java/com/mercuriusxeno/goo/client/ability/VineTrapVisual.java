package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.root.Rooted;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.ber.AbilityBlockRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Vines trap on the ground: the blob flattens into a small knot of vine
 * on the face it landed on while tendrils curl out across that face from
 * it and hook over the cell's edges, swaying until a mob steps in and the
 * trap roots it.
 * vines-unpack-root-and-thorn
 */
public final class VineTrapVisual {

    /** The ability whose marker this draws. */
    static final String VINES_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "leaf_vines").toString();

    /** Tendrils one trap latches with. */
    static final int TENDRILS = 5;
    /** How far across the face a tendril runs before it hooks over the edge. */
    private static final double REACH = 0.5;
    /** How far down past the edge a tendril hooks. */
    private static final double HOOK = 0.2;
    /** The points a tendril's hook adds past the edge, and how far down the hook its bend sits. */
    private static final int HOOK_POINTS = 2;
    private static final double HOOK_BEND = 0.5;
    /** How far a tendril lies off the face it lies on, clear of the block beneath. */
    private static final double LIFT = 0.01;
    private static final double HALF_WIDTH = 0.07;
    /** The knot's half-width at full size. */
    private static final double KNOT_HALF = 0.16;
    /** How far a tendril's end sways either side, and how fast. */
    private static final double SWAY = 0.04;
    private static final double SWAY_RATE = 0.12;
    private static final float CENTER = 0.5f;

    /** The game time each trap was first drawn at, which its unpack runs from. */
    private static final Map<AbilityBlockEntity, Float> FIRST_DRAWN = new WeakHashMap<>();

    private VineTrapVisual() {
    }

    /**
     * Populates the render state with whether the marker is a Vines trap and
     * how far its blob has unpacked.
     *
     * @param be    the ability block entity
     * @param state the render state to populate
     */
    public static void extract(AbilityBlockEntity be, AbilityBlockRenderState state) {
        state.vinesTrap = state.behaviorActive && VINES_ABILITY.equals(be.getAbilityId());
        if (!state.vinesTrap) {
            return;
        }
        float first = FIRST_DRAWN.computeIfAbsent(be, drawn -> state.gameTime);
        state.vinesUnpacked = unpackedAt(state.gameTime - first);
    }

    /**
     * How far the trap's blob has unpacked at an age.
     *
     * @param age the ticks since the trap was first drawn, with the partial tick
     * @return 0 at the landing, 1 once unpacked
     */
    static float unpackedAt(float age) {
        return Math.clamp(age / Rooted.UNPACK_TICKS, 0f, 1f);
    }

    /**
     * Submits the shrinking orb, the knot and the tendrils.
     *
     * @param state         the render state
     * @param poseStack     the pose stack at the marker's cell
     * @param nodeCollector the node collector
     */
    public static void submit(AbilityBlockRenderState state, PoseStack poseStack,
                              SubmitNodeCollector nodeCollector) {
        float unpacked = state.vinesUnpacked;
        Direction face = state.placedFace;
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 surface = new Vec3(CENTER, CENTER, CENTER).subtract(normal.scale(CENTER));
        if (unpacked < 1f) {
            submitFlatteningOrb(state, poseStack, nodeCollector, surface, 1f - unpacked);
        }
        GooRenderUtil.UvRect uv = VineRibbon.spriteUv();
        int color = ARGB.opaque(VineTangleLayer.VINE_GREEN);
        Basis basis = Basis.of(face);
        nodeCollector.submitCustomGeometry(poseStack, GooSubmitter.renderType(), (pose, consumer) -> {
            RenderContext ctx = new RenderContext(pose, consumer, state.lightCoords, color);
            VineRibbon.emitKnot(ctx, surface, basis.normal(), basis.across(), basis.along(), KNOT_HALF * unpacked,
                    uv);
            for (int i = 0; i < TENDRILS; i++) {
                List<Vec3> spine = tendrilSpine(surface, basis, i, unpacked, state.gameTime);
                VineRibbon.emit(ctx, spine, flatSides(spine, basis.normal()), basis.normal(), uv);
            }
        });
    }

    /**
     * The orb flattening onto the face as the vines unpack out of it.
     *
     * @param state         the render state
     * @param poseStack     the pose stack at the marker's cell
     * @param nodeCollector the node collector
     * @param surface       the center of the face the trap lies on
     * @param left          how much of the orb is left, 0 to 1
     */
    private static void submitFlatteningOrb(AbilityBlockRenderState state, PoseStack poseStack,
                                            SubmitNodeCollector nodeCollector, Vec3 surface, float left) {
        poseStack.pushPose();
        poseStack.translate(surface.x, surface.y, surface.z);
        poseStack.scale(left, left, left);
        poseStack.translate(-surface.x, -surface.y, -surface.z);
        MarkerOrbVisual.submit(state, poseStack, nodeCollector);
        poseStack.popPose();
    }

    /**
     * The face a trap lies on, as the unit normal out of it and two unit
     * directions across it.
     *
     * @param normal out of the face, into the marker's cell
     * @param across one direction across the face
     * @param along  the other direction across the face
     */
    record Basis(Vec3 normal, Vec3 across, Vec3 along) {
        static Basis of(Direction face) {
            Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            Vec3 across = face.getAxis() == Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
            return new Basis(normal, across, normal.cross(across));
        }
    }

    /**
     * The points one tendril runs through: out from the knot across the face,
     * swaying a little at its end, then over the cell's edge and down past
     * it, reached out as far as the vines have unpacked.
     *
     * @param surface  the center of the face the trap lies on
     * @param basis    the face's directions
     * @param index    which tendril
     * @param unpacked how far the vines have unpacked, 0 to 1
     * @param gameTime the game time with the partial tick
     * @return the tendril's points, the first at the knot
     */
    static List<Vec3> tendrilSpine(Vec3 surface, Basis basis, int index, float unpacked, float gameTime) {
        double angle = index * Math.TAU / TENDRILS;
        Vec3 out = basis.across().scale(Math.cos(angle)).add(basis.along().scale(Math.sin(angle)));
        Vec3 sideways = basis.normal().cross(out);
        double sway = SWAY * Math.sin(gameTime * SWAY_RATE + index);
        Vec3 lift = basis.normal().scale(LIFT);
        Vec3 from = surface.add(lift);
        Vec3 edge = surface.add(out.scale(REACH)).add(sideways.scale(sway)).add(lift);
        Vec3 hooked = edge.subtract(basis.normal().scale(HOOK + LIFT)).add(out.scale(LIFT));
        List<Vec3> spine = new ArrayList<>();
        int across = VineRibbon.SEGMENTS - HOOK_POINTS;
        for (int k = 0; k <= across; k++) {
            spine.add(from.lerp(edge, unpacked * k / (double) across));
        }
        if (unpacked >= 1f) {
            spine.add(edge.lerp(hooked, HOOK_BEND));
            spine.add(hooked);
        }
        return spine;
    }

    /**
     * Each point's half-width offset, lying in the face square to the tendril.
     *
     * @param spine  the tendril's points
     * @param normal the face's normal
     * @return the offsets
     */
    static List<Vec3> flatSides(List<Vec3> spine, Vec3 normal) {
        List<Vec3> sides = new ArrayList<>(spine.size());
        for (int k = 0; k < spine.size(); k++) {
            Vec3 along = spine.get(Math.min(k + 1, spine.size() - 1)).subtract(spine.get(Math.max(k - 1, 0)));
            Vec3 side = normal.cross(along);
            sides.add(side.lengthSqr() == 0 ? Vec3.ZERO : side.normalize().scale(HALF_WIDTH));
        }
        return sides;
    }
}
