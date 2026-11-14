package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Zone pocket's sky: a sea of stars standing far off, each placed by
 * view direction alone, so the field holds still as the camera walks and a
 * room edge view, whose camera stands one room extent away but looks the
 * same way, meets the stars the frame meets. The sphere of directions is cut
 * into cells, a cube's six faces each split evenly by angle, and a cell holds
 * a star where its hash falls within the sky's share. The stars are fainter
 * than the voidstone's (shaders/include/void_stars.glsl), and the sky
 * shader draws them in the voidstone's own star color.
 * outsider-room-draws-a-skybox-of-stars
 * sky-stars-sit-distant-sparser-and-dimmer-than-voidstone
 */
public final class PocketStarSky {

    /** The custom skybox goo:pocket's dimension type names, under NeoForge's custom_skybox attribute. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Goo.MODID, "pocket_stars");
    /** Cells along a cube face's edge. */
    static final int CELLS_PER_EDGE = 96;
    /** The three axes a direction runs along. */
    private static final int AXES = 3;
    /** Each axis's two ends, a cube face through each. */
    private static final int SIDES = 2;
    private static final int FACES = AXES * SIDES;
    private static final int X_AXIS = 0;
    private static final int Y_AXIS = 1;
    private static final int Z_AXIS = 2;
    /** The second axis after a face's own, which runs down its rows. */
    private static final int SECOND_AFTER = 2;
    /** A face's own axis, out through it. */
    private static final double OUTWARD = 1;
    /** Every cell the sky's directions are cut into. */
    public static final int CELLS = FACES * CELLS_PER_EDGE * CELLS_PER_EDGE;
    /** The share of cells holding a star, below the voidstone's bright and dim share together. */
    static final double STAR_SHARE = 0.02;
    /** The faintest star's brightness, a share of the star color. */
    static final float FAINTEST = 0.12F;
    /** The brightest star's brightness, below the voidstone's brightest. */
    static final float BRIGHTEST = 0.5F;
    /** How far off-center within its cell a star may sit, as a share of the cell. */
    private static final double JITTER = 0.7;
    private static final double QUARTER_TURN = Math.PI / 4;
    private static final double HALF = 0.5;
    private static final double TO_UNIT = 4294967296.0;
    private static final long UNSIGNED = 0xFFFFFFFFL;
    /** The voidstone shader's bit mix (shaders/core/voidstone.fsh mixBits), so both fields scatter alike. */
    private static final int MIX_WIDE_SHIFT = 16;
    private static final int MIX_NARROW_SHIFT = 15;
    private static final int MIX_FIRST_FACTOR = 0x7feb352d;
    private static final int MIX_SECOND_FACTOR = 0x846ca68b;
    /** Room for every seed a cell hashes under, so no two faces share a seed. */
    private static final int SEEDS_PER_FACE = 32;
    private static final int PICK_SEED = 0;
    private static final int BRIGHTNESS_SEED = 3;
    private static final int RATE_SEED = 7;
    private static final int PHASE_SEED = 11;
    private static final int ACROSS_SEED = 13;
    private static final int DOWN_SEED = 17;

    private PocketStarSky() {
    }

    /** The sky a dimension draws. */
    public enum Sky {
        /** goo's sea of stars. */
        STARS,
        /** Vanilla's own sky for the dimension's skybox. */
        VANILLA
    }

    /**
     * One star of the sky.
     *
     * @param direction  the unit direction it stands along from the camera
     * @param brightness its share of the star color at the top of its twinkle
     * @param rate       how fast it twinkles, a share from slowest to fastest
     * @param phase      where in its twinkle it starts, a share of its clock
     */
    public record Star(Vec3 direction, float brightness, float rate, float phase) {
    }

    /**
     * One cell of the sky's directions.
     *
     * @param face   the cube face, 0 to 5
     * @param across the cell's column on its face
     * @param down   the cell's row on its face
     */
    private record Cell(int face, int across, int down) {
    }

    /**
     * The sky a dimension draws, by the custom skybox its dimension type names.
     *
     * @param customSkybox the dimension's custom_skybox attribute
     * @return the star sky where it names goo's, else vanilla's own
     */
    public static Sky skyOf(Identifier customSkybox) {
        return ID.equals(customSkybox) ? Sky.STARS : Sky.VANILLA;
    }

    /**
     * The star the sky draws along a view direction: the star of the cell
     * the direction falls in.
     *
     * @param direction the view direction, any length
     * @return the cell's star, empty where the cell holds none
     */
    public static Optional<Star> starAlong(Vec3 direction) {
        return starIn(cellOf(direction));
    }

    /**
     * Every star the sky draws.
     *
     * @return the stars, cell by cell
     */
    public static List<Star> stars() {
        List<Star> stars = new ArrayList<>();
        for (int face = 0; face < FACES; face++) {
            for (int across = 0; across < CELLS_PER_EDGE; across++) {
                for (int down = 0; down < CELLS_PER_EDGE; down++) {
                    starIn(new Cell(face, across, down)).ifPresent(stars::add);
                }
            }
        }
        return stars;
    }

    private static Optional<Star> starIn(Cell cell) {
        if (hash(cell, PICK_SEED) >= STAR_SHARE) {
            return Optional.empty();
        }
        double faintToBright = hash(cell, BRIGHTNESS_SEED);
        // Most stars sit faint and a few bright, as a far field reads.
        float brightness = (float) (FAINTEST + (BRIGHTEST - FAINTEST) * faintToBright * faintToBright);
        Vec3 direction = directionOf(cell.face(),
                cell.across() + HALF + (hash(cell, ACROSS_SEED) - HALF) * JITTER,
                cell.down() + HALF + (hash(cell, DOWN_SEED) - HALF) * JITTER);
        return Optional.of(new Star(direction, brightness, (float) hash(cell, RATE_SEED),
                (float) hash(cell, PHASE_SEED)));
    }

    /**
     * The cell a direction falls in: the cube face its largest axis points
     * through, and the column and row of the two axes after it, each spread
     * by angle so every cell spans near the same piece of sky.
     *
     * @param direction the direction
     * @return its cell
     */
    private static Cell cellOf(Vec3 direction) {
        double[] axes = {direction.x(), direction.y(), direction.z()};
        int major = majorAxisOf(axes);
        double reach = Math.abs(axes[major]);
        return new Cell(major * SIDES + (axes[major] >= 0 ? 0 : 1), cellAlong(axes[(major + 1) % AXES] / reach),
                cellAlong(axes[(major + SECOND_AFTER) % AXES] / reach));
    }

    private static int majorAxisOf(double[] axes) {
        int major = 0;
        for (int axis = 1; axis < AXES; axis++) {
            if (Math.abs(axes[axis]) > Math.abs(axes[major])) {
                major = axis;
            }
        }
        return major;
    }

    private static int cellAlong(double onFace) {
        double share = (Math.atan(onFace) / QUARTER_TURN + 1) * HALF;
        return Math.clamp((int) Math.floor(share * CELLS_PER_EDGE), 0, CELLS_PER_EDGE - 1);
    }

    /**
     * The unit direction through a point of a cube face, the point given in
     * cells along the face's column and row.
     *
     * @param face   the cube face
     * @param across the column, in cells
     * @param down   the row, in cells
     * @return the direction
     */
    private static Vec3 directionOf(int face, double across, double down) {
        int major = face / SIDES;
        double[] axes = new double[AXES];
        axes[major] = face % SIDES == 0 ? OUTWARD : -OUTWARD;
        axes[(major + 1) % AXES] = onFaceOf(across);
        axes[(major + SECOND_AFTER) % AXES] = onFaceOf(down);
        return new Vec3(axes[X_AXIS], axes[Y_AXIS], axes[Z_AXIS]).normalize();
    }

    private static double onFaceOf(double cells) {
        return Math.tan((cells / CELLS_PER_EDGE / HALF - 1) * QUARTER_TURN);
    }

    /**
     * A cell's hash under a seed, evenly spread over zero to one, mixed as
     * the voidstone shader mixes its texels.
     *
     * @param cell the cell
     * @param seed which of the cell's values the hash picks
     * @return the hash, from zero up to one
     */
    private static double hash(Cell cell, int seed) {
        int mixed = mixBits(cell.face() * SEEDS_PER_FACE + seed ^ mixBits(cell.across() ^ mixBits(cell.down())));
        return (mixed & UNSIGNED) / TO_UNIT;
    }

    private static int mixBits(int value) {
        int mixed = value;
        mixed ^= mixed >>> MIX_WIDE_SHIFT;
        mixed *= MIX_FIRST_FACTOR;
        mixed ^= mixed >>> MIX_NARROW_SHIFT;
        mixed *= MIX_SECOND_FACTOR;
        mixed ^= mixed >>> MIX_WIDE_SHIFT;
        return mixed;
    }
}
