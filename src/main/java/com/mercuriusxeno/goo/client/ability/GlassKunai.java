package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.FlatQuadContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * One of Shards' glass knives as faces: a kunai of clear glass, its blade a
 * flat leaf drawn to a point and its grip a slim darker rod behind it, laid
 * along its heading and rolled about it.
 * decision shards-sling-then-morph-to-flechettes
 */
public final class GlassKunai {

    /** The blade's length from the point to its widest, in blocks at full size. */
    static final float POINT_LENGTH = 0.34f;
    /** The blade's length from its widest back to the grip. */
    static final float HEEL_LENGTH = 0.1f;
    /** The grip's length behind the blade. */
    static final float GRIP_LENGTH = 0.16f;
    /** The blade's half-width at its widest, across its flat. */
    private static final float BLADE_HALF_WIDTH = 0.075f;
    /** The blade's half-thickness at its widest. */
    private static final float BLADE_HALF_THICKNESS = 0.022f;
    /** The grip's half-width. */
    private static final float GRIP_HALF_WIDTH = 0.02f;
    /** The blade's glass: pale, faintly blue, mostly clear. */
    static final int BLADE_COLOR = 0xC0DDF4FF;
    /** The grip's glass: a deeper teal, less clear. */
    static final int GRIP_COLOR = 0xE04FA6C4;
    private static final double LEVEL_EPSILON = 1e-4;

    private GlassKunai() {
    }

    /**
     * Emits one kunai with its point at a place.
     *
     * @param quads   the context the faces emit through
     * @param point   the blade's point, camera-relative
     * @param heading the unit direction it points along
     * @param roll    its roll about its heading, in radians
     * @param scale   its size, 1 at full
     */
    public static void emit(FlatQuadContext quads, Vec3 point, Vec3 heading, float roll, float scale) {
        Vec3[] frame = frame(heading, roll);
        Vec3 side = frame[0];
        Vec3 up = frame[1];
        Vec3 widest = point.subtract(heading.scale(POINT_LENGTH * scale));
        Vec3 heel = widest.subtract(heading.scale(HEEL_LENGTH * scale));
        Vec3[] ring = {
            widest.add(side.scale(BLADE_HALF_WIDTH * scale)),
            widest.add(up.scale(BLADE_HALF_THICKNESS * scale)),
            widest.subtract(side.scale(BLADE_HALF_WIDTH * scale)),
            widest.subtract(up.scale(BLADE_HALF_THICKNESS * scale)),
        };
        for (int corner = 0; corner < ring.length; corner++) {
            Vec3 next = ring[(corner + 1) % ring.length];
            triangle(quads, ring[corner], next, point, BLADE_COLOR);
            triangle(quads, next, ring[corner], heel, BLADE_COLOR);
        }
        emitGrip(quads, heel, heading, side, up, scale);
    }

    private static void emitGrip(FlatQuadContext quads, Vec3 heel, Vec3 heading, Vec3 side, Vec3 up, float scale) {
        Vec3 butt = heel.subtract(heading.scale(GRIP_LENGTH * scale));
        float half = GRIP_HALF_WIDTH * scale;
        Vec3[] offsets = {side.scale(half), up.scale(half), side.scale(-half), up.scale(-half)};
        for (int corner = 0; corner < offsets.length; corner++) {
            Vec3 a = offsets[corner];
            Vec3 b = offsets[(corner + 1) % offsets.length];
            triangle(quads, heel.add(a), heel.add(b), butt.add(b), GRIP_COLOR);
            triangle(quads, heel.add(a), butt.add(b), butt.add(a), GRIP_COLOR);
        }
    }

    /**
     * The blade's side and up about a heading, rolled: the side level with
     * the ground where the heading allows, so an unrolled blade lies flat.
     *
     * @param heading the unit heading
     * @param roll    the roll in radians
     * @return the side then the up, both unit and square to the heading
     */
    static Vec3[] frame(Vec3 heading, float roll) {
        Vec3 level = Math.abs(heading.y) > 1 - LEVEL_EPSILON ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = heading.cross(level).normalize();
        Vec3 up = side.cross(heading).normalize();
        double cos = Math.cos(roll);
        double sin = Math.sin(roll);
        return new Vec3[]{side.scale(cos).add(up.scale(sin)), up.scale(cos).subtract(side.scale(sin))};
    }

    private static void triangle(FlatQuadContext quads, Vec3 a, Vec3 b, Vec3 c, int color) {
        Vector3f normal = new Vector3f((float) (b.x - a.x), (float) (b.y - a.y), (float) (b.z - a.z))
                .cross((float) (c.x - a.x), (float) (c.y - a.y), (float) (c.z - a.z)).normalize();
        ConeGeometry.emitTriangle(corner -> {
            Vec3 at = switch (corner) {
                case ConeGeometry.BASE_START -> a;
                case ConeGeometry.BASE_END -> b;
                default -> c;
            };
            quads.vertex((float) at.x, (float) at.y, (float) at.z, color, normal.x, normal.y, normal.z);
        });
    }
}
