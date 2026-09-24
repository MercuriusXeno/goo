package com.mercuriusxeno.goo.lab;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Executes a {@link LabPlan} against a server level: every placement set at
 * the origin plus its offset, every sign given its text, every bay filled by
 * {@link LabRigs}, every supply station stocked by {@link LabStock} and every
 * pen's mobs spawned (decision lab-built-from-code).
 */
public final class LabBuilder {

    /**
     * Block update flags: notify neighbours and send the change to clients.
     */
    private static final int UPDATE_FLAGS = Block.UPDATE_ALL;
    /**
     * Clear flags: tell clients, drop nothing, skip the side effects that spill a block entity's contents.
     */
    private static final int CLEAR_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
    /**
     * The sign line that carries the name.
     */
    private static final int NAME_LINE = 1;
    /**
     * Offset from a block corner to its centre, where a mob spawns.
     */
    private static final double BLOCK_CENTRE = 0.5;

    private LabBuilder() {
    }

    /**
     * Builds the whole plan with its origin at the given position.
     *
     * @param level  the level to build in
     * @param origin the world position of the plan's zero offset
     * @param plan   the plan to build
     * @return the number of placements set
     * @throws CommandSyntaxException when a placement's block state does not parse
     */
    public static int build(ServerLevel level, BlockPos origin, LabPlan plan) throws CommandSyntaxException {
        return buildWithin(level, origin, plan, plan.bounds());
    }

    /**
     * Builds the part of the plan inside a region: its placements, the rigs of
     * the plots it holds whole and the spawns it holds. A test builds one bay or zone this way.
     *
     * @param level  the level to build in
     * @param origin the world position of the plan's zero offset
     * @param plan   the plan to build from
     * @param region the part of the plan to build, in plan offsets
     * @return the number of placements set
     * @throws CommandSyntaxException when a placement's block state does not parse
     */
    public static int buildWithin(ServerLevel level, BlockPos origin, LabPlan plan, LabBox region)
            throws CommandSyntaxException {
        HolderLookup<Block> blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        int placed = 0;
        for (LabPlacement placement : plan.placements()) {
            if (region.contains(placement.offset())) {
                place(level, origin, blocks, placement);
                placed++;
            }
        }
        plan.plots().stream().filter(plot -> holdsWhole(region, plot.bounds()))
                .forEach(plot -> LabRigs.rig(level, origin, plot));
        plan.supply().stations().stream().filter(station -> region.contains(station.chestOffset()))
                .forEach(station -> LabStock.stock(level, origin, station));
        plan.spawns().stream().filter(spawn -> region.contains(spawn.offset()))
                .forEach(spawn -> spawn(level, origin, spawn));
        return placed;
    }

    /**
     * Clears the plan's bounds and builds the plan again in place, so an
     * altered lab returns to the plan without a restart (decision lab-save-is-disposable).
     *
     * @param level  the level to rebuild in
     * @param origin the world position of the plan's zero offset
     * @param plan   the plan to rebuild
     * @return the number of placements set
     * @throws CommandSyntaxException when a placement's block state does not parse
     */
    public static int rebuild(ServerLevel level, BlockPos origin, LabPlan plan) throws CommandSyntaxException {
        clear(level, origin, plan.bounds());
        return build(level, origin, plan);
    }

    /**
     * Clears a region: every container emptied and every block set to air
     * with drops and block entity side effects suppressed, then every
     * non-player entity inside removed, the items a removal let fall included.
     *
     * @param level  the level to clear in
     * @param origin the world position of the plan's zero offset
     * @param region the region, in plan offsets
     */
    public static void clear(ServerLevel level, BlockPos origin, LabBox region) {
        BlockPos low = worldPos(origin, region.min());
        BlockPos high = worldPos(origin, region.max());
        for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                container.clearContent();
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), CLEAR_FLAGS);
        }
        AABB inside = new AABB(low.getX(), low.getY(), low.getZ(), high.getX() + 1, high.getY() + 1, high.getZ() + 1);
        level.getEntities((Entity) null, inside, entity -> !(entity instanceof Player)).forEach(Entity::discard);
    }

    /**
     * Answers the lab plan laid for a level: one supply station per goo type its registry holds.
     *
     * @param level the level whose registries to read
     * @return the plan
     */
    public static LabPlan planFor(ServerLevel level) {
        return LabLayout.plan(LabStock.gooTypeIds(level));
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
     * Answers whether a region holds a box whole.
     *
     * @param region the region
     * @param box    the box
     * @return true when both corners of the box lie in the region
     */
    private static boolean holdsWhole(LabBox region, LabBox box) {
        return region.contains(box.min()) && region.contains(box.max());
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
     * Spawns one planned mob at its block's centre and keeps it from despawning.
     *
     * @param level  the level to spawn in
     * @param origin the world position of the plan's zero offset
     * @param spawn  the planned spawn
     */
    private static void spawn(ServerLevel level, BlockPos origin, LabSpawn spawn) {
        Entity entity = EntityType.byString(spawn.entityId())
                .map(type -> type.create(level, EntitySpawnReason.COMMAND)).orElse(null);
        if (entity == null) {
            return;
        }
        BlockPos pos = worldPos(origin, spawn.offset());
        entity.snapTo(pos.getX() + BLOCK_CENTRE, pos.getY(), pos.getZ() + BLOCK_CENTRE);
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        level.addFreshEntity(entity);
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
