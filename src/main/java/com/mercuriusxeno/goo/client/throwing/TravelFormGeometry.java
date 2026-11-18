package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.ability.ConeGeometry;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * A travel form's geometry, pointed along a unit direction and grown by the
 * morph: a body of one piece, a shaft from the tail to the base of a pointed
 * front cone, closed at the tail, and flat fins growing out of the body's
 * surface at the tail, each drawn on both its faces. The goo's sprite lies on
 * every face at one sprite a block, each face cut at the block marks along the
 * body so the sprite never wraps within a face, and so stretches nowhere.
 * The flight and the javelin in the hand both draw through {@link #emit}.
 * decision dart-flutes-draw-as-fins
 */
public final class TravelFormGeometry {

    /** The number of sides around the body. */
    static final int FRONT_SIDES = 6;

    private static final float TWO_PI = (float) (2 * Math.PI);
    private static final float HALF = 0.5f;

    /**
     * One flat fin as the quad it draws, its corners in winding order.
     *
     * @param rootLead  the root edge's forward end, on the body's surface
     * @param rootTrail the root edge's rear end, on the body's surface
     * @param tipTrail  the outer edge's rear end
     * @param tipLead   the outer edge's forward end
     */
    public record Fin(Vector3f rootLead, Vector3f rootTrail, Vector3f tipTrail, Vector3f tipLead) {
    }

    /**
     * A form's body at a morph: its front cone's length and base radius, and
     * the shaft behind the cone's base out to the fins' trailing edge.
     *
     * @param length the front cone's length
     * @param radius the shaft's radius and the cone's base radius
     * @param tail   how far the shaft runs behind the cone's base
     */
    private record Body(float length, float radius, float tail) {

        static Body of(TravelForm form, float morphFrac) {
            return new Body(form.frontLength() * morphFrac, form.frontRadius() * morphFrac,
                    form.fins().sweep() * morphFrac);
        }

        /**
         * The body's radius at a place along its axis.
         *
         * @param axial the place along the axis, the cone's base at 0 and the tail behind it
         * @return the radius there
         */
        float radiusAt(float axial) {
            return axial <= 0f || length <= 0f ? radius : radius * Math.max(0f, 1f - axial / length);
        }
    }

    private TravelFormGeometry() {
    }

    /**
     * Emits a travel form about the context's origin, the front cone's base there.
     *
     * @param ctx       the render context
     * @param form      the travel form drawn
     * @param dir       the unit direction the form points along
     * @param morphFrac how far the form has grown, 0 to 1
     * @param uv        the goo fluid sprite's UV rectangle
     */
    public static void emit(RenderContext ctx, TravelForm form, Vector3f dir, float morphFrac,
                            GooRenderUtil.UvRect uv) {
        Body body = Body.of(form, morphFrac);
        if (body.length() <= 0f || body.radius() <= 0f) {
            return;
        }
        float[] basis = ConeGeometry.computeBasis(dir.x, dir.y, dir.z);
        emitBody(ctx, body, dir, basis, uv);
        emitTailCap(ctx, body, dir, basis, uv);
        TravelForm.Fins shape = form.fins();
        for (int i = 0; i < shape.count(); i++) {
            Vector3f radial = radialAt(basis, i, shape.count());
            Vector3f normal = new Vector3f(dir).cross(radial);
            Fin fin = finAlong(shape, body, dir, radial, morphFrac);
            emitFin(ctx, fin, body, dir, normal, uv);
            emitFin(ctx, new Fin(fin.tipLead(), fin.tipTrail(), fin.rootTrail(), fin.rootLead()), body, dir,
                    normal.negate(new Vector3f()), uv);
        }
    }

    /**
     * The fins at a travel form's tail, spaced evenly around the body, the
     * first along the basis the body's first edge stands on.
     *
     * @param form      the travel form
     * @param dir       the unit direction the form points along
     * @param morphFrac how far the form has grown, 0 to 1
     * @return the fins, in order around the body
     */
    static List<Fin> fins(TravelForm form, Vector3f dir, float morphFrac) {
        TravelForm.Fins shape = form.fins();
        Body body = Body.of(form, morphFrac);
        float[] basis = ConeGeometry.computeBasis(dir.x, dir.y, dir.z);
        List<Fin> fins = new ArrayList<>(shape.count());
        for (int i = 0; i < shape.count(); i++) {
            fins.add(finAlong(shape, body, dir, radialAt(basis, i, shape.count()), morphFrac));
        }
        return fins;
    }

    /**
     * The unit direction the given place around the body stands out along, square to the axis.
     *
     * @param basis the body's orthonormal basis
     * @param index the place around the body
     * @param count how many places stand around it
     * @return the radial direction
     */
    private static Vector3f radialAt(float[] basis, int index, int count) {
        return new Vector3f(ConeGeometry.segmentNormal(basis, TWO_PI * index / count));
    }

    /**
     * One fin in the plane the body's axis and a radial direction span, its
     * root edge on the body's surface.
     *
     * @param shape     the fins' whole size
     * @param body      the body at this morph
     * @param dir       the unit body direction
     * @param radial    the unit direction the fin stands out along
     * @param morphFrac how far the form has grown, 0 to 1
     * @return the fin
     */
    private static Fin finAlong(TravelForm.Fins shape, Body body, Vector3f dir, Vector3f radial, float morphFrac) {
        float lead = shape.rootChord() * morphFrac;
        float trail = -shape.sweep() * morphFrac;
        float tipLead = (shape.tipChord() - shape.sweep()) * morphFrac;
        float span = shape.span() * morphFrac;
        return new Fin(
                pointAt(dir, radial, lead, body.radiusAt(lead)),
                pointAt(dir, radial, trail, body.radiusAt(trail)),
                pointAt(dir, radial, trail, body.radiusAt(trail) + span),
                pointAt(dir, radial, tipLead, body.radiusAt(tipLead) + span));
    }

    private static Vector3f pointAt(Vector3f dir, Vector3f radial, float axial, float out) {
        return new Vector3f(dir).mul(axial).fma(out, radial);
    }

    // The body's sides, shaft and cone as one, each side cut at the block marks along the body.
    private static void emitBody(RenderContext ctx, Body body, Vector3f dir, float[] basis,
                                 GooRenderUtil.UvRect uv) {
        TreeSet<Float> cuts = new TreeSet<>(List.of(0f, body.tail(), body.tail() + body.length()));
        for (int mark = 1; mark < body.tail() + body.length(); mark++) {
            cuts.add((float) mark);
        }
        List<Float> along = new ArrayList<>(cuts);
        for (int side = 0; side < FRONT_SIDES; side++) {
            Vector3f out0 = radialAt(basis, side, FRONT_SIDES);
            Vector3f out1 = radialAt(basis, side + 1, FRONT_SIDES);
            float[] normal = ConeGeometry.segmentNormal(basis, TWO_PI * (side + HALF) / FRONT_SIDES);
            for (int cut = 0; cut + 1 < along.size(); cut++) {
                float from = along.get(cut);
                float to = along.get(cut + 1);
                float cell = (float) Math.floor((from + to) * HALF);
                int color = GooRenderUtil.OPAQUE_WHITE;
                for (float[] corner : new float[][]{{from, 0}, {from, 1}, {to, 1}, {to, 0}}) {
                    float axial = corner[0] - body.tail();
                    float radius = body.radiusAt(axial);
                    Vector3f at = pointAt(dir, corner[1] == 0 ? out0 : out1, axial, radius);
                    float around = (side + corner[1]) * chordOf(radius);
                    ctx.vertexColored(color, at.x, at.y, at.z, uOf(uv, corner[0] - cell), vOf(uv, around),
                            normal[ConeGeometry.PERP_X], normal[ConeGeometry.PERP_Y], normal[ConeGeometry.PERP_Z]);
                }
            }
        }
    }

    // The length of one side's edge around the body at a radius.
    private static float chordOf(float radius) {
        return radius * (float) Math.sin(Math.PI / FRONT_SIDES) / HALF;
    }

    // The face closing the body at its tail, facing back, the sprite laid flat across it.
    private static void emitTailCap(RenderContext ctx, Body body, Vector3f dir, float[] basis,
                                    GooRenderUtil.UvRect uv) {
        Vector3f center = new Vector3f(dir).mul(-body.tail());
        Vector3f perp = new Vector3f(basis[ConeGeometry.PERP_X], basis[ConeGeometry.PERP_Y],
                basis[ConeGeometry.PERP_Z]);
        Vector3f cross = new Vector3f(basis[ConeGeometry.CROSS_X], basis[ConeGeometry.CROSS_Y],
                basis[ConeGeometry.CROSS_Z]);
        for (int side = 0; side < FRONT_SIDES; side++) {
            Vector3f edge0 = pointAt(dir, radialAt(basis, side, FRONT_SIDES), -body.tail(), body.radius());
            Vector3f edge1 = pointAt(dir, radialAt(basis, side + 1, FRONT_SIDES), -body.tail(), body.radius());
            ConeGeometry.emitTriangle(corner -> {
                Vector3f at = switch (corner) {
                    case ConeGeometry.BASE_START -> edge1;
                    case ConeGeometry.BASE_END -> edge0;
                    default -> center;
                };
                Vector3f across = new Vector3f(at).sub(center);
                ctx.vertexColored(GooRenderUtil.OPAQUE_WHITE, at.x, at.y, at.z,
                        uOf(uv, body.radius() + across.dot(perp)), vOf(uv, body.radius() + across.dot(cross)),
                        -dir.x, -dir.y, -dir.z);
            });
        }
    }

    // One face of a fin, the sprite laid along the body from the tail and out from the body's surface.
    private static void emitFin(RenderContext ctx, Fin fin, Body body, Vector3f dir, Vector3f normal,
                                GooRenderUtil.UvRect uv) {
        for (Vector3f at : List.of(fin.rootLead(), fin.rootTrail(), fin.tipTrail(), fin.tipLead())) {
            float axial = at.dot(dir);
            float out = new Vector3f(at).sub(new Vector3f(dir).mul(axial)).length();
            ctx.vertexColored(GooRenderUtil.OPAQUE_WHITE, at.x, at.y, at.z, uOf(uv, axial + body.tail()),
                    vOf(uv, out - body.radiusAt(axial)), normal.x, normal.y, normal.z);
        }
    }

    // The sprite's U a distance in blocks into it, one sprite a block.
    private static float uOf(GooRenderUtil.UvRect uv, float blocks) {
        return uv.u0() + (uv.u1() - uv.u0()) * blocks;
    }

    // The sprite's V a distance in blocks into it, one sprite a block.
    private static float vOf(GooRenderUtil.UvRect uv, float blocks) {
        return uv.v0() + (uv.v1() - uv.v0()) * blocks;
    }
}
