package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Where Gloom lies, a hostile mob's natural spawn reads dark: the light part
 * of the spawn check passes there whatever the block or sky light, and the
 * goo's own cell leaves the mob's box free, as a pseudo-liquid does. Every
 * other condition, the space besides the goo, the biome's list, the cap and
 * the distance from players, stays vanilla's, and the goo spends nothing on
 * a spawn. A mob standing on Gloom is out of the daylight: the light its
 * daylight behaviors read is the dark's, and an undead player there is
 * spared the sun. A spot stands on Gloom where its feet are in a Gloom cell
 * or one holds it up.
 * gloom-spawns-regardless-of-light-and-mobs-on-it-ignore-daylight
 * operator ruling 2026-11-18: Gloom's goo is no obstruction to a spawn
 */
public final class GloomDark {

    private GloomDark() {
    }

    /**
     * Whether Gloom lies at a spot, in the block the feet are in or the block under them.
     *
     * @param spot       the block the feet are in
     * @param holdsGloom whether a block holds a Gloom cell
     * @return true where Gloom lies at the spot
     */
    static boolean liesAt(BlockPos spot, Predicate<BlockPos> holdsGloom) {
        return holdsGloom.test(spot) || holdsGloom.test(spot.below());
    }

    /**
     * Whether the light part of a hostile spawn check passes at a spot: on
     * Gloom it does whatever the light, elsewhere vanilla answers.
     *
     * @param spot          the block a spawning mob's feet stand in
     * @param holdsGloom    whether a block holds a Gloom cell
     * @param vanillaPasses vanilla's answer at the spot
     * @return true where the light lets a hostile mob spawn
     */
    static boolean spawnLightPasses(BlockPos spot, Predicate<BlockPos> holdsGloom, boolean vanillaPasses) {
        return vanillaPasses || liesAt(spot, holdsGloom);
    }

    /**
     * The walk target value a hostile mob's spawn check reads at a spot. A
     * monster's value falls below zero as the light rises, which refuses the
     * spawn; on Gloom the value reads no lower than zero, as the dark reads.
     *
     * @param spot         the block a spawning mob's feet stand in
     * @param holdsGloom   whether a block holds a Gloom cell
     * @param vanillaValue vanilla's value at the spot
     * @return the value the spawn check compares against zero
     */
    static float spawnWalkTargetValue(BlockPos spot, Predicate<BlockPos> holdsGloom, float vanillaValue) {
        return liesAt(spot, holdsGloom) ? Math.max(vanillaValue, 0f) : vanillaValue;
    }

    /**
     * Whether a spawning mob's box is free of collisions at a spot: where the
     * feet stand in Gloom, the goo's own cells leave it free.
     *
     * @param spot             the block a spawning mob's feet stand in
     * @param holdsGloom       whether a block holds a Gloom cell
     * @param vanillaFree      vanilla's answer at the spot
     * @param freeBesidesGloom whether the box is free once Gloom's own cells are left out
     * @return true where the box is free for the mob
     */
    static boolean spawnSpaceIsFree(BlockPos spot, Predicate<BlockPos> holdsGloom, boolean vanillaFree,
                                    BooleanSupplier freeBesidesGloom) {
        return vanillaFree || holdsGloom.test(spot) && freeBesidesGloom.getAsBoolean();
    }

    /**
     * Whether a thing stands in daylight: on Gloom it never does.
     *
     * @param feet              the block its feet are in
     * @param holdsGloom        whether a block holds a Gloom cell
     * @param vanillaInDaylight vanilla's answer at the spot
     * @return true where daylight reaches it
     */
    static boolean inDaylight(BlockPos feet, Predicate<BlockPos> holdsGloom, boolean vanillaInDaylight) {
        return vanillaInDaylight && !liesAt(feet, holdsGloom);
    }

    /**
     * The light a mob's daylight behaviors read at its spot: on Gloom, the
     * dimension's value at no light, so the mob behaves as it does at night.
     *
     * @param feet         the block its feet are in
     * @param holdsGloom   whether a block holds a Gloom cell
     * @param vanillaValue vanilla's light value at the spot
     * @param darkValue    the value the dimension reads at no light
     * @return the light value the mob's behaviors read
     */
    static float daylightValue(BlockPos feet, Predicate<BlockPos> holdsGloom, float vanillaValue, float darkValue) {
        return liesAt(feet, holdsGloom) ? darkValue : vanillaValue;
    }

    /**
     * Whether a thing stands in daylight in a level.
     *
     * @param level             the level
     * @param feet              the block its feet are in
     * @param vanillaInDaylight vanilla's answer at the spot
     * @return true where daylight reaches it
     */
    public static boolean inDaylight(BlockGetter level, BlockPos feet, boolean vanillaInDaylight) {
        return inDaylight(feet, gloomIn(level), vanillaInDaylight);
    }

    /**
     * The light a mob's daylight behaviors read at its spot in a level.
     *
     * @param level        the level
     * @param feet         the block its feet are in
     * @param vanillaValue vanilla's light value at the spot
     * @return the light value the mob's behaviors read
     */
    public static float daylightValue(Level level, BlockPos feet, float vanillaValue) {
        return daylightValue(feet, gloomIn(level), vanillaValue, level.dimensionType().ambientLight());
    }

    /**
     * Whether a block of a level holds a Gloom cell.
     *
     * @param level the level
     * @return the test, true where the block holds a cell that reads dark
     */
    public static Predicate<BlockPos> gloomIn(BlockGetter level) {
        return pos -> VolumeCellBlock.hasSurface(level.getBlockState(pos).getBlock(), CellSurface.DARKEN);
    }

    /**
     * Whether the light part of a hostile spawn check passes at a spot of a level.
     *
     * @param level         the level
     * @param spot          the block a spawning mob's feet stand in
     * @param vanillaPasses vanilla's answer at the spot
     * @return true where the light lets a hostile mob spawn
     */
    public static boolean spawnLightPasses(BlockGetter level, BlockPos spot, boolean vanillaPasses) {
        return spawnLightPasses(spot, gloomIn(level), vanillaPasses);
    }

    /**
     * The walk target value a hostile mob's spawn check reads at a spot of a level.
     *
     * @param level        the level
     * @param spot         the block a spawning mob's feet stand in
     * @param vanillaValue vanilla's value at the spot
     * @return the value the spawn check compares against zero
     */
    public static float spawnWalkTargetValue(BlockGetter level, BlockPos spot, float vanillaValue) {
        return spawnWalkTargetValue(spot, gloomIn(level), vanillaValue);
    }

    /**
     * Whether a spawning mob's box is free of collisions in a level.
     *
     * @param level       the level
     * @param box         the mob's spawn box
     * @param vanillaFree vanilla's answer for the box
     * @return true where the box is free for the mob
     */
    public static boolean spawnSpaceIsFree(CollisionGetter level, AABB box, boolean vanillaFree) {
        Predicate<BlockPos> holdsGloom = gloomIn(level);
        BlockPos spot = BlockPos.containing(box.getCenter().x, box.minY, box.getCenter().z);
        return spawnSpaceIsFree(spot, holdsGloom, vanillaFree, () -> freeBesides(level, box, holdsGloom));
    }

    /**
     * Whether a box meets no block collision but Gloom's, no entity's and no
     * border, vanilla's collision test with the goo's cells left out.
     *
     * @param level      the level
     * @param box        the mob's spawn box
     * @param holdsGloom whether a block holds a Gloom cell
     * @return true where only Gloom's cells, if any, meet the box
     */
    private static boolean freeBesides(CollisionGetter level, AABB box, Predicate<BlockPos> holdsGloom) {
        if (!level.getWorldBorder().isWithinBounds(box) || !level.getEntityCollisions(null, box).isEmpty()) {
            return false;
        }
        VoxelShape boxShape = Shapes.create(box);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (!holdsGloom.test(pos) && Shapes.joinIsNotEmpty(state.getCollisionShape(level, pos).move(pos),
                    boxShape, BooleanOp.AND)) {
                return false;
            }
        }
        return true;
    }
}
