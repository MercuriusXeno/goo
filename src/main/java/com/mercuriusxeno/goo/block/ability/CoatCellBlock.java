package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.durable.CoatRepair;
import com.mercuriusxeno.goo.ability.program.CoatStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import java.util.EnumSet;
import java.util.Set;

/**
 * Durable's Coat cell: the volume cell bound to Durable's goo, resisting a
 * blast and a pick as obsidian does at any fill, never broken by a mob, and
 * keeping the block it stands against.
 * durable-goo-is-a-protective-coating-that-blocks-damage
 */
public class CoatCellBlock extends VolumeCellBlock {

    /**
     * Obsidian's hardness, which a Coat cell takes at any fill.
     */
    public static final float HARDNESS = 50.0F;
    /**
     * Obsidian's blast resistance, which a Coat cell takes at any fill.
     */
    public static final float BLAST_RESISTANCE = 1200.0F;

    /**
     * Creates a Coat cell.
     *
     * @param properties the block properties
     * @param binding    the Coat binding
     */
    public CoatCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * Refuses every mob the cell, the wither and the ender dragon among them.
     *
     * @return false
     */
    @Override
    public boolean canEntityDestroy(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos,
                                    @NonNull Entity entity) {
        return false;
    }

    /**
     * Repairs a damaged item lying in the cell once each period its laying
     * step names: the cell spends a layer and the item regains what
     * CoatRepair answers for that layer's worth on the item's.
     * items-dropped-in-durable-goo-are-repaired-by-their-worth
     */
    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                @NonNull Entity entity, @NonNull InsideBlockEffectApplier effects,
                                boolean pastEdges) {
        super.entityInside(state, level, pos, entity, effects, pastEdges);
        if (level instanceof ServerLevel server && entity instanceof ItemEntity item && item.blockPosition().equals(pos)
                && item.tickCount > 0) {
            CoatStep.in(layingProgram(server))
                    .filter(coat -> item.tickCount % coat.repairTicks() == 0)
                    .ifPresent(coat -> repair(server, pos, state, item));
        }
    }

    private void repair(ServerLevel level, BlockPos pos, BlockState state, ItemEntity item) {
        ItemStack stack = item.getItem();
        int restored = CoatRepair.layerRestores(level, binding().layingAbility(), stack);
        if (restored > 0) {
            ItemStack repaired = stack.copy();
            repaired.setDamageValue(stack.getDamageValue() - restored);
            item.setItem(repaired);
            spendLayers(level, pos, state, 1);
        }
    }

    /**
     * The faces of a block a Coat cell stands against.
     *
     * @param level the level
     * @param pos   the block
     * @return each side whose neighbor is a Coat cell
     */
    public static Set<Direction> coatedFaces(BlockGetter level, BlockPos pos) {
        Set<Direction> faces = EnumSet.noneOf(Direction.class);
        for (Direction side : Direction.values()) {
            if (level.getBlockState(pos.relative(side)).getBlock() instanceof CoatCellBlock) {
                faces.add(side);
            }
        }
        return faces;
    }

    /**
     * Whether a blast keeps off a block: one of the faces the blast stands
     * beyond is coated, so the blast comes from the Coat's side.
     *
     * @param block  the block's center
     * @param blast  the blast's center
     * @param coated the block's coated faces
     * @return true where a coated face looks toward the blast
     */
    public static boolean shieldsFrom(Vec3 block, Vec3 blast, Set<Direction> coated) {
        Vec3 toward = blast.subtract(block);
        return coated.stream().anyMatch(face -> toward.dot(Vec3.atLowerCornerOf(face.getUnitVec3i())) > 0);
    }
}
