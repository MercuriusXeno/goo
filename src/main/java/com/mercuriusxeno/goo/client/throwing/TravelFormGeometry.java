package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.ability.ConeGeometry;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * A travel form's geometry about its middle, pointed along a unit direction
 * and grown by the morph: a pointed front cone and flat fins at its tail.
 * The flight and the javelin in the hand both draw through {@link #emit}.
 * decision dart-flutes-draw-as-fins
 */
public final class TravelFormGeometry {

    /** The number of triangular sides on the front cone. */
    static final int FRONT_SIDES = 3;

    private static final float TWO_PI = (float) (2 * Math.PI);

    /**
     * One flat fin as the quad it draws, its corners in winding order.
     *
     * @param rootLead  the root edge's forward end, on the shaft
     * @param rootTrail the root edge's rear end, on the shaft
     * @param tipTrail  the outer edge's rear end
     * @param tipLead   the outer edge's forward end
     */
    public record Fin(Vector3f rootLead, Vector3f rootTrail, Vector3f tipTrail, Vector3f tipLead) {
    }

    private TravelFormGeometry() {
    }

    /**
     * Emits a travel form about the context's origin.
     *
     * @param ctx       the render context
     * @param form      the travel form drawn
     * @param dir       the unit direction the form points along
     * @param morphFrac how far the form has grown, 0 to 1
     * @param uv        the goo fluid sprite's UV rectangle
     */
    public static void emit(RenderContext ctx, TravelForm form, Vector3f dir, float morphFrac,
                            GooRenderUtil.UvRect uv) {
        float[] basis = ConeGeometry.computeBasis(dir.x, dir.y, dir.z);
        ConeGeometry.emitCone(ctx, new ConeGeometry.Cone(0f, 0f, 0f, dir.x, dir.y, dir.z,
                form.frontLength() * morphFrac, form.frontRadius() * morphFrac),
                basis, FRONT_SIDES, GooRenderUtil.OPAQUE_WHITE, uv);
        TravelForm.Fins shape = form.fins();
        for (int i = 0; i < shape.count(); i++) {
            Vector3f radial = radialAt(basis, i, shape.count());
            Vector3f normal = new Vector3f(dir).cross(radial);
            emitFin(ctx, finAlong(shape, dir, radial, morphFrac), normal, uv);
        }
    }

    /**
     * The fins at a travel form's tail, spaced evenly around the shaft, the
     * first along the basis the front cone's first edge stands on.
     *
     * @param form      the travel form
     * @param dir       the unit direction the form points along
     * @param morphFrac how far the form has grown, 0 to 1
     * @return the fins, in order around the shaft
     */
    static List<Fin> fins(TravelForm form, Vector3f dir, float morphFrac) {
        TravelForm.Fins shape = form.fins();
        float[] basis = ConeGeometry.computeBasis(dir.x, dir.y, dir.z);
        List<Fin> fins = new ArrayList<>(shape.count());
        for (int i = 0; i < shape.count(); i++) {
            fins.add(finAlong(shape, dir, radialAt(basis, i, shape.count()), morphFrac));
        }
        return fins;
    }

    /**
     * The unit direction the given fin stands out along, perpendicular to the shaft.
     *
     * @param basis the shaft's orthonormal basis
     * @param index the fin's place around the shaft
     * @param count how many fins stand around the shaft
     * @return the radial direction
     */
    private static Vector3f radialAt(float[] basis, int index, int count) {
        return new Vector3f(ConeGeometry.segmentNormal(basis, TWO_PI * index / count));
    }

    /**
     * One fin in the plane the shaft's axis and a radial direction span.
     *
     * @param shape     the fins' whole size
     * @param dir       the unit shaft direction
     * @param radial    the unit direction the fin stands out along
     * @param morphFrac how far the form has grown, 0 to 1
     * @return the fin
     */
    private static Fin finAlong(TravelForm.Fins shape, Vector3f dir, Vector3f radial, float morphFrac) {
        float tipLeadAxial = shape.tipChord() - shape.sweep();
        return new Fin(
                pointAt(dir, radial, shape.rootChord() * morphFrac, 0f),
                pointAt(dir, radial, 0f, 0f),
                pointAt(dir, radial, -shape.sweep() * morphFrac, shape.span() * morphFrac),
                pointAt(dir, radial, tipLeadAxial * morphFrac, shape.span() * morphFrac));
    }

    private static Vector3f pointAt(Vector3f dir, Vector3f radial, float axial, float out) {
        return new Vector3f(dir).mul(axial).fma(out, radial);
    }

    private static void emitFin(RenderContext ctx, Fin fin, Vector3f normal, GooRenderUtil.UvRect uv) {
        int white = GooRenderUtil.OPAQUE_WHITE;
        ctx.vertexColored(white, fin.rootLead().x, fin.rootLead().y, fin.rootLead().z,
                uv.u0(), uv.v0(), normal.x, normal.y, normal.z);
        ctx.vertexColored(white, fin.rootTrail().x, fin.rootTrail().y, fin.rootTrail().z,
                uv.u1(), uv.v0(), normal.x, normal.y, normal.z);
        ctx.vertexColored(white, fin.tipTrail().x, fin.tipTrail().y, fin.tipTrail().z,
                uv.u1(), uv.v1(), normal.x, normal.y, normal.z);
        ctx.vertexColored(white, fin.tipLead().x, fin.tipLead().y, fin.tipLead().z,
                uv.u0(), uv.v1(), normal.x, normal.y, normal.z);
    }
}
