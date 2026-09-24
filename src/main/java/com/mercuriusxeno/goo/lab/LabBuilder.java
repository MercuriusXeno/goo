package com.mercuriusxeno.goo.lab;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Executes a {@link LabPlan} against a server level: every placement set at
 * the origin plus its offset, and every sign given its text
 * (decision lab-built-from-code).
 */
public final class LabBuilder {

    /**
     * Block update flags: notify neighbours and send the change to clients.
     */
    private static final int UPDATE_FLAGS = Block.UPDATE_ALL;
    /**
     * The sign line that carries the machine's name.
     */
    private static final int NAME_LINE = 1;

    private LabBuilder() {
    }

    /**
     * Builds the plan with its origin at the given position.
     *
     * @param level  the level to build in
     * @param origin the world position of the plan's zero offset
     * @param plan   the plan to build
     * @return the number of placements set
     * @throws CommandSyntaxException when a placement's block state does not parse
     */
    public static int build(ServerLevel level, BlockPos origin, LabPlan plan) throws CommandSyntaxException {
        HolderLookup<Block> blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        for (LabPlacement placement : plan.placements()) {
            place(level, origin, blocks, placement);
        }
        return plan.placements().size();
    }

    /**
     * Answers the world position of a plan offset.
     *
     * @param origin the world position of the plan's zero offset
     * @param offset the offset relative to the origin
     * @return the world position
     */
    public static BlockPos worldPos(BlockPos origin, LabOffset offset) {
        return origin.offset(offset.x(), offset.y(), offset.z());
    }

    /**
     * Answers the world position of the lab origin in the Goo Lab world.
     *
     * @return the lab origin position
     */
    public static BlockPos worldOrigin() {
        LabOffset origin = LabLayout.WORLD_ORIGIN;
        return new BlockPos(origin.x(), origin.y(), origin.z());
    }

    /**
     * Sets one placement's block and, for a sign, its text.
     *
     * @param level     the level to build in
     * @param origin    the world position of the plan's zero offset
     * @param blocks    the block lookup the state string resolves against
     * @param placement the placement to set
     * @throws CommandSyntaxException when the block state does not parse
     */
    private static void place(ServerLevel level, BlockPos origin, HolderLookup<Block> blocks, LabPlacement placement)
            throws CommandSyntaxException {
        BlockPos pos = worldPos(origin, placement.offset());
        level.setBlock(pos, parseState(blocks, placement.blockState()), UPDATE_FLAGS);
        if (placement.isSign() && level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            sign.updateText(text -> text.setMessage(NAME_LINE, Component.literal(placement.signText())), true);
        }
    }

    /**
     * Resolves a block state string from the plan.
     *
     * @param blocks     the block lookup
     * @param blockState the state in command syntax
     * @return the resolved block state
     * @throws CommandSyntaxException when the state does not parse
     */
    static BlockState parseState(HolderLookup<Block> blocks, String blockState) throws CommandSyntaxException {
        return BlockStateParser.parseForBlock(blocks, blockState, false).blockState();
    }
}
