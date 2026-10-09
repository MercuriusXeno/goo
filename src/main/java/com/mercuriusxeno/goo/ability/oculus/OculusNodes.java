package com.mercuriusxeno.goo.ability.oculus;

import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds the oculus a blink snaps to: among the oculus prisms within the node
 * range of the blinker's eye and inside the cone around their look, the one
 * nearest the look's line by angle. The server's teleport and the client's
 * cursor both ask here, so the cursor shows the node the blink takes.
 * Decision oculus-prism-becomes-a-hovering-eye.
 */
public final class OculusNodes {

    /** The id of the ability whose combo makes a prism an oculus. */
    public static final String OCULUS = "goo:ender_oculus";
    private static final double CELL_CENTER = 0.5;

    private OculusNodes() {
    }

    /**
     * The oculus a blink along the look snaps to.
     *
     * @param level       the level
     * @param eye         the blinker's eye
     * @param look        the unit look vector
     * @param range       the farthest an oculus may stand from the eye
     * @param coneDegrees how far off the look an oculus may stand, in degrees
     * @return the oculus's cell, empty where none stands in the cone
     */
    public static Optional<BlockPos> onLook(Level level, Vec3 eye, Vec3 look, double range, double coneDegrees) {
        return pick(oculiNear(level, eye, range), eye, look, range, coneDegrees);
    }

    /**
     * Picks the cell nearest the look by angle among those inside the range
     * and the cone.
     *
     * @param cells       the oculus cells to choose among
     * @param eye         the blinker's eye
     * @param look        the unit look vector
     * @param range       the farthest a cell's centre may stand from the eye
     * @param coneDegrees how far off the look a cell's centre may stand, in degrees
     * @return the cell picked, empty where none qualifies
     */
    static Optional<BlockPos> pick(List<BlockPos> cells, Vec3 eye, Vec3 look, double range, double coneDegrees) {
        double coneCos = Math.cos(Math.toRadians(coneDegrees));
        return cells.stream()
                .filter(cell -> Vec3.atCenterOf(cell).distanceTo(eye) <= range)
                .filter(cell -> cosOffLook(cell, eye, look) >= coneCos)
                .max(Comparator.comparingDouble(cell -> cosOffLook(cell, eye, look)));
    }

    /**
     * The cosine of the angle between the look and the line to a cell's centre.
     *
     * @param cell the cell
     * @param eye  the blinker's eye
     * @param look the unit look vector
     * @return the cosine, 1 for a cell dead on the look
     */
    private static double cosOffLook(BlockPos cell, Vec3 eye, Vec3 look) {
        Vec3 toCell = Vec3.atCenterOf(cell).subtract(eye);
        double length = toCell.length();
        return length == 0 ? 1 : toCell.scale(1 / length).dot(look);
    }

    /**
     * The oculus prisms in the loaded chunks the range reaches.
     *
     * @param level the level
     * @param eye   the blinker's eye
     * @param range the reach
     * @return the oculus cells
     */
    private static List<BlockPos> oculiNear(Level level, Vec3 eye, double range) {
        List<BlockPos> cells = new ArrayList<>();
        int minX = SectionPos.blockToSectionCoord(eye.x - range);
        int maxX = SectionPos.blockToSectionCoord(eye.x + range);
        int minZ = SectionPos.blockToSectionCoord(eye.z - range);
        int maxZ = SectionPos.blockToSectionCoord(eye.z + range);
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk != null) {
                    chunk.getBlockEntities().values().stream().filter(OculusNodes::isOculus)
                            .forEach(oculus -> cells.add(oculus.getBlockPos()));
                }
            }
        }
        return cells;
    }

    /**
     * Whether a block entity is a prism holding the oculus combo.
     *
     * @param blockEntity the block entity
     * @return true for an oculus
     */
    static boolean isOculus(BlockEntity blockEntity) {
        return blockEntity instanceof PrismBlockEntity prism && OCULUS.equals(prism.getCombo());
    }

    /**
     * The charge an oculus holds.
     *
     * @param level the level
     * @param cell  the oculus's cell
     * @return its charge in mB, zero where no oculus stands
     */
    public static int chargeAt(Level level, BlockPos cell) {
        return level.getBlockEntity(cell) instanceof PrismBlockEntity prism && OCULUS.equals(prism.getCombo())
                ? prism.charge() : 0;
    }

    /**
     * The centre of an oculus's cell, where its eye hovers.
     *
     * @param cell the oculus's cell
     * @return the centre
     */
    public static Vec3 eyeOf(BlockPos cell) {
        return new Vec3(cell.getX() + CELL_CENTER, cell.getY() + CELL_CENTER, cell.getZ() + CELL_CENTER);
    }
}
