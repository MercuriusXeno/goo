package com.mercuriusxeno.goo.ability.frost;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The blocks Iceborn has frozen in a level, kept in the level's saved data so
 * none is lost over a logout or a restart: each still water it turned to ice
 * and each still lava it turned to obsidian, with the player whose Iceborn
 * froze it. Once that player is out of range, gone, or no longer Iceborn, the
 * block goes back to water or lava (decision iceborn-frozen-hearts-thaw-on-fire).
 */
public final class IcebornFrozenBlocks extends SavedData {

    /**
     * Blocks from its player within which a frozen block holds: a little past
     * the leech's reach, so a block the leech froze does not flicker back at
     * its edge.
     */
    static final double HOLDS_WITHIN = IcebornEvents.LEECH_RADIUS + 1.5;

    private static final String FIELD_POS = "pos";
    private static final String FIELD_LAVA = "lava";
    private static final String FIELD_OWNER = "owner";
    private static final String FIELD_FROZEN = "frozen";

    /**
     * One block Iceborn froze.
     *
     * @param pos   where it stands
     * @param lava  true for lava frozen to obsidian, false for water frozen to ice
     * @param owner the player whose Iceborn froze it
     */
    record Frozen(BlockPos pos, boolean lava, UUID owner) {

        static final Codec<Frozen> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                BlockPos.CODEC.fieldOf(FIELD_POS).forGetter(Frozen::pos),
                Codec.BOOL.fieldOf(FIELD_LAVA).forGetter(Frozen::lava),
                UUIDUtil.CODEC.fieldOf(FIELD_OWNER).forGetter(Frozen::owner)
        ).apply(inst, Frozen::new));
    }

    /** Codec for the level's record. */
    static final Codec<IcebornFrozenBlocks> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Frozen.CODEC.listOf().fieldOf(FIELD_FROZEN).forGetter(record -> List.copyOf(record.frozen))
    ).apply(inst, IcebornFrozenBlocks::new));

    /** Saved data type, stored as goo/iceborn_frozen_blocks.dat in each level's data storage. */
    static final SavedDataType<IcebornFrozenBlocks> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "iceborn_frozen_blocks"), IcebornFrozenBlocks::new, CODEC);

    private final List<Frozen> frozen;

    /** An empty record. */
    IcebornFrozenBlocks() {
        this(List.of());
    }

    /**
     * A record of the blocks given.
     *
     * @param frozen the blocks Iceborn froze
     */
    IcebornFrozenBlocks(List<Frozen> frozen) {
        this.frozen = new ArrayList<>(frozen);
    }

    /**
     * The record of a level.
     *
     * @param level the server level
     * @return its record
     */
    public static IcebornFrozenBlocks of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Records a block a player's Iceborn froze.
     *
     * @param pos   where it stands
     * @param lava  true for lava frozen to obsidian, false for water frozen to ice
     * @param owner the player whose Iceborn froze it
     */
    void record(BlockPos pos, boolean lava, UUID owner) {
        frozen.add(new Frozen(pos.immutable(), lava, owner));
        setDirty();
    }

    /**
     * The blocks the record holds.
     *
     * @return them, in the order they froze
     */
    List<Frozen> frozen() {
        return List.copyOf(frozen);
    }

    /**
     * Whether a frozen block holds where its player stands: within
     * HOLDS_WITHIN of an Iceborn player in its level.
     *
     * @param owner where its Iceborn player stands, or null where the player is gone or no longer Iceborn
     * @param pos   the block
     * @return true while it holds
     */
    static boolean holds(@Nullable Vec3 owner, BlockPos pos) {
        return owner != null && owner.closerThan(pos.getCenter(), HOLDS_WITHIN);
    }

    /**
     * Sends every block whose player no longer holds it back to water or
     * lava, where it still stands frozen, and drops it from the record.
     *
     * @param level the server level
     */
    void revertUnheld(ServerLevel level) {
        boolean changed = frozen.removeIf(block -> {
            if (holds(icebornAt(level, block.owner()), block.pos())) {
                return false;
            }
            revert(level, block);
            return true;
        });
        if (changed) {
            setDirty();
        }
    }

    private static @Nullable Vec3 icebornAt(ServerLevel level, UUID owner) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        return player != null && player.level() == level && IcebornEvents.isIceborn(player) ? player.position()
                : null;
    }

    private static void revert(ServerLevel level, Frozen block) {
        BlockState now = level.getBlockState(block.pos());
        boolean stillFrozen = block.lava() ? now.is(Blocks.OBSIDIAN) : now.is(IcebornEvents.icebornIce());
        if (stillFrozen) {
            level.setBlock(block.pos(), (block.lava() ? Blocks.LAVA : Blocks.WATER).defaultBlockState(),
                    Block.UPDATE_ALL);
        }
    }
}
