package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.block.alienstructure.AlienMaterial;
import com.mercuriusxeno.goo.block.alienstructure.PillarStyle;
import com.mercuriusxeno.goo.lunar.WarrenRuinShapes.Part;
import com.mercuriusxeno.goo.lunar.WarrenRuinShapes.Placement;
import com.mercuriusxeno.goo.lunar.WarrenRuinShapes.Shape;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What one warrens ruin is cut from, fixed for the whole ruin and never drawn per block: the material each part takes,
 * the style its pillars are carved in, and how it is turned; a glyph bears its material's own rune. A ruin is cut from
 * one material throughout, or by a design giving each kind of part its own: pillars from one, walls and glyphs from
 * another, floor tiles from a third. A test lays shapes through it without the game's registries.
 * decision buildings-place-material-families-by-design-never-by-roll
 *
 * @param materials the material each part takes
 * @param style     the style every pillar of the ruin is carved in
 * @param rotation  how the ruin is turned about its spot
 */
public record RuinLook(Map<Part, AlienMaterial> materials, PillarStyle style, Rotation rotation) {

    /**
     * One block a ruin lays, with the look it is laid in.
     *
     * @param placement where it goes and which part it is
     * @param material  the material it is cut from, or null for a part no material cuts
     * @param style     the pillar style, for a pillar
     */
    public record LaidBlock(Placement placement, AlienMaterial material, PillarStyle style) {
    }

    /**
     * Picks a ruin's look: one material throughout, or a design of one material for pillars, one for walls and glyphs
     * and one for floor tiles, each even odds; then one pillar style and one turn.
     *
     * @param random the feature's random
     * @return the look
     */
    public static RuinLook pick(RandomSource random) {
        AlienMaterial walls = pickOf(AlienMaterial.values(), random);
        boolean designed = random.nextBoolean();
        AlienMaterial pillars = designed ? pickOf(AlienMaterial.values(), random) : walls;
        AlienMaterial floors = designed ? pickOf(AlienMaterial.values(), random) : walls;
        Map<Part, AlienMaterial> materials = new EnumMap<>(Part.class);
        materials.put(Part.PLAIN, walls);
        materials.put(Part.GLYPH, walls);
        materials.put(Part.PILLAR, pillars);
        materials.put(Part.TILE, floors);
        return new RuinLook(materials, pickOf(PillarStyle.values(), random), pickOf(Rotation.values(), random));
    }

    /**
     * Lays a shape in this look.
     *
     * @param shape the shape
     * @return each block with its look
     */
    public List<LaidBlock> lay(Shape shape) {
        List<LaidBlock> laid = new ArrayList<>();
        for (Placement placement : shape.blocks()) {
            Part member = placement.part().member();
            laid.add(new LaidBlock(placement, member == null ? null : materials.get(member), style));
        }
        return laid;
    }

    static <T> T pickOf(T[] values, RandomSource random) {
        return values[random.nextInt(values.length)];
    }
}
