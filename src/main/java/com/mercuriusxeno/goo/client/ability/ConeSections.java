package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.FlatQuadContext;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import java.util.function.DoubleUnaryOperator;

/**
 * Stacked cross-sections square to a look, the volume a held ability's
 * shader fills: Petrify's fog in its cone and Bore's vortex down its tunnel
 * (decisions petrify-stone-encasement-and-calcify-map,
 * bore-vortex-with-a-worldspace-shake). Each vertex's color carries how far
 * along the look its section stands in red and where on the section it sits
 * in green and blue, since a core pipeline takes no per-draw uniforms.
 */
final class ConeSections {

    /** Where the first section stands by default, in blocks past the apex, clear of the view. */
    static final double NEAR = 0.6;
    private static final int SEGMENTS = 32;
    private static final int OPAQUE = 0xFF;
    private static final float SIGNED_TO_UNIT = 0.5f;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF = 0.5;

    private ConeSections() {
    }

    /**
     * The volume the sections fill.
     *
     * @param apex     where the volume starts, camera relative
     * @param axis     the look's unit vector
     * @param yaw      the look's yaw in degrees, which turns the sections' frame
     * @param near     where the first section stands past the apex
     * @param range    how far the volume reaches past the apex
     * @param sections how many sections stack along it
     * @param radiusAt each section's radius by its distance past the apex
     */
    record Volume(Vec3 apex, Vec3 axis, float yaw, double near, double range, int sections,
                  DoubleUnaryOperator radiusAt) {
    }

    /**
     * Emits every section of a volume.
     *
     * @param quads  the quad emitter
     * @param volume the volume
     */
    static void emit(FlatQuadContext quads, Volume volume) {
        for (int section = 0; section < volume.sections(); section++) {
            double distance = sectionDistance(section, volume.near(), volume.range(), volume.sections());
            emitSection(quads, volume.apex().add(volume.axis().scale(distance)), volume,
                    volume.radiusAt().applyAsDouble(distance), (float) (distance / volume.range()));
        }
    }

    /**
     * How far past the apex a section stands: the sections spread evenly
     * from the nearest to the reach.
     *
     * @param section  the section's index
     * @param near     where the first section stands
     * @param range    the reach
     * @param sections how many sections stack along it
     * @return the section's distance in blocks
     */
    static double sectionDistance(int section, double near, double range, int sections) {
        return near + (range - near) * (section + HALF) / sections;
    }

    /**
     * Emits one section: a disc square to the axis.
     *
     * @param quads  the quad emitter
     * @param center the section's center, camera relative
     * @param volume the volume, its look and yaw
     * @param radius the section's radius
     * @param along  how far along the volume the section stands, 0 to 1
     */
    private static void emitSection(FlatQuadContext quads, Vec3 center, Volume volume, double radius, float along) {
        Vec3 axis = volume.axis();
        Vec3 side = sectionSide(axis, volume.yaw());
        Vec3 up = side.cross(axis);
        int middle = sectionColor(along, 0, 0);
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double a0 = TWO_PI * segment / SEGMENTS;
            double a1 = TWO_PI * (segment + 1) / SEGMENTS;
            vertex(quads, center, axis, middle);
            rim(quads, new Frame(center, side, up, axis), radius, a0, along);
            rim(quads, new Frame(center, side, up, axis), radius, a1, along);
            vertex(quads, center, axis, middle);
        }
    }

    /**
     * The unit direction across a section to its side: level, square to the
     * way the look's yaw faces, so it holds steady however the look pitches,
     * straight up and straight down among them, where the look's own cross
     * with up vanishes.
     *
     * @param axis the look's unit vector, which lies in the yaw's upright plane
     * @param yaw  the look's yaw in degrees
     * @return the side direction
     */
    static Vec3 sectionSide(Vec3 axis, float yaw) {
        Vec3 facing = Vec3.directionFromRotation(0f, yaw);
        return new Vec3(-facing.z, 0, facing.x).normalize();
    }

    /**
     * Emits a rim vertex of a section.
     *
     * @param quads  the quad emitter
     * @param frame  the section's frame
     * @param radius the section's radius
     * @param angle  the vertex's angle about the axis
     * @param along  how far along the volume the section stands
     */
    private static void rim(FlatQuadContext quads, Frame frame, double radius, double angle, float along) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vec3 at = frame.center().add(frame.side().scale(cos * radius)).add(frame.up().scale(sin * radius));
        vertex(quads, at, frame.axis(), sectionColor(along, (float) cos, (float) sin));
    }

    /**
     * A section's frame: its center and the directions across it and along the look.
     *
     * @param center the section's center, camera relative
     * @param side   the unit direction across the section to its side
     * @param up     the unit direction across the section upward
     * @param axis   the look's unit vector
     */
    private record Frame(Vec3 center, Vec3 side, Vec3 up, Vec3 axis) {
    }

    private static void vertex(FlatQuadContext quads, Vec3 at, Vec3 axis, int color) {
        quads.vertex((float) at.x, (float) at.y, (float) at.z, color, (float) axis.x, (float) axis.y,
                (float) axis.z);
    }

    /**
     * A vertex's color: how far along the volume in red, its place on the
     * section in green and blue.
     *
     * @param along how far along, 0 to 1
     * @param u     the section-local x, -1 to 1
     * @param v     the section-local y, -1 to 1
     * @return the packed color
     */
    static int sectionColor(float along, float u, float v) {
        return ARGB.color(OPAQUE, NetherDiscMesh.toByte(along), NetherDiscMesh.toByte((u + 1f) * SIGNED_TO_UNIT),
                NetherDiscMesh.toByte((v + 1f) * SIGNED_TO_UNIT));
    }
}
