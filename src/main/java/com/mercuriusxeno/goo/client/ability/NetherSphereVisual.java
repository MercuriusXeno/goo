package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ber.ChainMarkerBlockEntityRenderer;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * Nether black-hole render path: the three-pass sphere/corona/disk
 * submission triggered from {@link ChainMarkerBlockEntityRenderer} when a nether
 * {@code ProgramBehavior} is active on the chain marker BE. All nether-
 * specific geometry, mesh caches, color packing, and render-state
 * extraction for the black-hole visual lives here so the generic BER
 * only has to know about the orb visual and the thin dispatch check.
 *
 * <p>Three passes per frame while the nether behavior is active:
 * <ol>
 *   <li>Solid occluder sphere via {@link GooRenderTypes#NETHER_BLACKHOLE_TYPE}
 *       - writes depth so everything behind the visible face is hidden.</li>
 *   <li>Additive corona halo via {@link GooRenderTypes#NETHER_CORONA_TYPE}
 *       - same sphere mesh at {@link #CORONA_SCALE} the main radius,
 *       fragment shader does a geometric ray-sphere test against the
 *       main radius (decoded from {@code Color.b}) to carve out the
 *       annular ring.</li>
 *   <li>Additive accretion disk via {@link GooRenderTypes#NETHER_DISK_TYPE}
 *       - flat annulus ring in the XZ plane. Inner radius floats just
 *       past the sphere silhouette, outer radius is driven by a
 *       separate expansion curve in {@link BlackHolePhases} so the disk
 *       sweeps outward independent of the sphere's growth (not in
 *       lockstep). Brightness is strictly radial in the shader, so the
 *       ring reads identically from any viewing angle.</li>
 * </ol>
 */
public final class NetherSphereVisual {

    /**
     * Offset to get block center from integer position.
     */
    private static final float BLOCK_CENTER = 0.5f;
    /**
     * Solid alpha (0xFF) for the blackhole sphere vertices.
     */
    private static final int BLACKHOLE_ALPHA = 0xFF;
    /**
     * Maximum encodable radius for the {@code Color.b} channel (in world blocks).
     * Must match the {@code MAX_ENCODED_RADIUS} constants in
     * {@code nether_corona.vsh} and {@code nether_disk.vsh}. 16 sits
     * safely above the max actual visible radius (~11 for stack-4 nether).
     */
    private static final float MAX_ENCODED_RADIUS = 16f;

    /**
     * Number of latitude bands on the sphere mesh (excluding poles).
     */
    private static final int SPHERE_LAT_SEGMENTS = 32;
    /**
     * Number of longitude segments around the sphere mesh.
     */
    private static final int SPHERE_LON_SEGMENTS = 64;
    /**
     * Vertices per quad (matches {@code VertexFormat.Mode.QUADS}).
     */
    private static final int VERTICES_PER_QUAD = 4;
    /**
     * Radius multiplier for the corona pass. Must match
     * {@code CORONA_SCALE} in {@code nether_corona.vsh}. Good values
     * are 1.08 for a thin corona, 1.15 for a thicker one.
     */
    private static final float CORONA_SCALE = 1.08f;
    /**
     * Disk's inner edge, as a multiple of the current sphere radius.
     * Sits just past the sphere surface so the inner rim hugs the
     * silhouette without z-fighting the sphere's equator.
     */
    private static final float DISK_INNER_SPHERE_MULT = 1.06f;
    /**
     * Latitude offset subtracted from {@code lat / latSegments} to center phi on zero.
     */
    private static final double LATITUDE_HALF_OFFSET = 0.5;
    /**
     * Full circle in radians.
     */
    private static final double TWO_PI = 2.0 * Math.PI;

    /**
     * Pre-generated unit sphere mesh. Every 4 consecutive entries form
     * one quad. Each vertex's XYZ doubles as the unit outward normal.
     */
    private static final List<Vector3f> SPHERE_MESH = buildSphereMesh();

    private NetherSphereVisual() {
    }

    /**
     * Populates the render state's nether fields from the marker's phase
     * cursor and marks the lens around the sphere's current visible radius.
     *
     * @param be    the chain marker block entity
     * @param state the render state to populate
     */
    public static void extract(ChainMarkerBlockEntity be, ChainMarkerRenderState state) {
        if (BlackHolePhases.populateRenderState(be, state)) {
            NetherLensEffect.markHoleActive(BlackHolePhases.holeCenter(be), BlackHolePhases.visibleRadius(state));
        }
    }

    /**
     * Submits the three render passes for the black-hole visual:
     * occluding sphere, additive corona halo, and accretion disk. Call
     * from the BER's {@code submit} when {@code state.netherActive} is
     * true.
     *
     * @param state         the render state snapshot
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     */
    public static void submit(ChainMarkerRenderState state, PoseStack poseStack,
                              SubmitNodeCollector nodeCollector) {
        float fullRadius = BlackHolePhases.fullRadius(state);
        float visibleRadius = BlackHolePhases.visibleRadius(state);
        int color = packBlackholeColor(state.visibleScale, state.animationTime, visibleRadius);
        float coronaRadius = visibleRadius * CORONA_SCALE;
        float innerR = visibleRadius * DISK_INNER_SPHERE_MULT;
        float outerR = NetherDiscMesh.outerRadius(innerR, visibleRadius, fullRadius, state.diskExpansionScale);
        float animPhase = state.animationTime;

        // Main sphere: solid-black occluder with depth write on.
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_BLACKHOLE_TYPE,
                (pose, c) -> emitSphereMesh(pose, c, visibleRadius, color));
        // Corona halo: the fragment shader's ray-sphere test against the
        // main radius (Color.b) carves the annular ring.
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_CORONA_TYPE,
                (pose, c) -> emitSphereMesh(pose, c, coronaRadius, color));
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_DISK_TYPE,
                (pose, c) -> NetherDiscMesh.emitDisc(pose, c, innerR, outerR, animPhase));
    }

    /**
     * Emits the pre-generated unit sphere mesh with each vertex scaled to
     * {@code radius} and translated to the block center.
     *
     * @param pose   the current pose entry
     * @param c      the vertex consumer
     * @param radius world-space sphere radius in blocks
     * @param color  packed ARGB vertex color
     */
    private static void emitSphereMesh(PoseStack.Pose pose, VertexConsumer c,
                                       float radius, int color) {
        for (Vector3f v : SPHERE_MESH) {
            c.addVertex(pose,
                            BLOCK_CENTER + v.x() * radius,
                            BLOCK_CENTER + v.y() * radius,
                            BLOCK_CENTER + v.z() * radius)
                    .setColor(color)
                    .setNormal(pose, v.x(), v.y(), v.z());
        }
    }

    /**
     * Packs per-frame state for the sphere and corona shaders into the
     * vertex ARGB color. Channels: R = visible scale, G = animation time,
     * B = main radius normalized by {@link #MAX_ENCODED_RADIUS},
     * A = fixed opaque.
     *
     * @param scale         implosion visible scale in [0, 1]
     * @param animationTime swirl animation phase in [0, 1]
     * @param visibleRadius main sphere's current visible radius in world blocks
     * @return the packed ARGB color
     */
    private static int packBlackholeColor(float scale, float animationTime, float visibleRadius) {
        return ARGB.color(BLACKHOLE_ALPHA, NetherDiscMesh.toByte(scale),
                NetherDiscMesh.toByte(animationTime), NetherDiscMesh.toByte(visibleRadius / MAX_ENCODED_RADIUS));
    }

    /**
     * Builds a UV sphere mesh as a list of {@link Vector3f} unit vectors.
     * Every four consecutive entries form one quad matching
     * {@code VertexFormat.Mode.QUADS}. Called once at class init.
     *
     * @return the unit sphere vertex list
     */
    private static List<Vector3f> buildSphereMesh() {
        int capacity = SPHERE_LAT_SEGMENTS * SPHERE_LON_SEGMENTS * VERTICES_PER_QUAD;
        List<Vector3f> out = new ArrayList<>(capacity);
        for (int lat = 0; lat < SPHERE_LAT_SEGMENTS; lat++) {
            double phi0 = Math.PI * ((double) lat / SPHERE_LAT_SEGMENTS - LATITUDE_HALF_OFFSET);
            double phi1 = Math.PI * ((double) (lat + 1) / SPHERE_LAT_SEGMENTS - LATITUDE_HALF_OFFSET);
            for (int lon = 0; lon < SPHERE_LON_SEGMENTS; lon++) {
                double theta0 = TWO_PI * lon / SPHERE_LON_SEGMENTS;
                double theta1 = TWO_PI * (lon + 1) / SPHERE_LON_SEGMENTS;
                out.add(sphereVertex(phi0, theta0));
                out.add(sphereVertex(phi1, theta0));
                out.add(sphereVertex(phi1, theta1));
                out.add(sphereVertex(phi0, theta1));
            }
        }
        return out;
    }

    /**
     * Builds one unit-sphere vertex at spherical coordinates (phi, theta).
     *
     * @param phi   latitude in radians, {@code [-PI/2, PI/2]}
     * @param theta longitude in radians, {@code [0, 2*PI]}
     * @return the unit-sphere vertex as a {@link Vector3f}
     */
    private static Vector3f sphereVertex(double phi, double theta) {
        double cosPhi = Math.cos(phi);
        return new Vector3f(
                (float) (cosPhi * Math.cos(theta)),
                (float) Math.sin(phi),
                (float) (cosPhi * Math.sin(theta)));
    }
}
