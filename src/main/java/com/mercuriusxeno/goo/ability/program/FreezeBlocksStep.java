package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.frost.FrostFire;
import com.mercuriusxeno.goo.data.GooGroups;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Freezes the still water and lava types within a sphere around the host's frost
 * center: water becomes ice, magicked ice that never thaws where the step
 * lasts, each lava type becomes what the molten groups pair it with, lava
 * obsidian and molten iron iron core, and the fire in the sphere goes out. The
 * Orb freezes what it passes with ice that may melt
 * (decision orb-carries-a-swirling-nova), and Nova's ring and Cold's cone
 * freeze what they reach the same way (decision nova-ring-and-cold-cone-freeze-water-and-lava).
 *
 * @param radius  the sphere's radius in blocks, evaluated on the host
 * @param lasting whether water freezes to magicked ice that never thaws, rather than vanilla ice
 */
public record FreezeBlocksStep(Expr radius, boolean lasting) implements TakesNoEntity {

    private static final String NAME = "freeze_blocks";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_LASTING = "lasting";
    /** The base values group holding each lava type frost cools. */
    private static final String MOLTEN_BLOCKS = "molten_blocks";
    /** The base values group holding what each lava type cools into, at the lava type's position. */
    private static final String MOLTEN_COOLED_BLOCKS = "molten_cooled_blocks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<FreezeBlocksStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(FreezeBlocksStep::radius),
            Codec.BOOL.optionalFieldOf(FIELD_LASTING, true).forGetter(FreezeBlocksStep::lasting)
    ).apply(inst, FreezeBlocksStep::new));

    /**
     * The registered type.
     */
    public static final StepType<FreezeBlocksStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * A freeze leaving lasting ice.
     *
     * @param radius the sphere's radius in blocks
     */
    public FreezeBlocksStep(Expr radius) {
        this(radius, true);
    }

    @Override
    public StepType<FreezeBlocksStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        FrostHost host = context.hostAs(FrostHost.class);
        freezeWithin(host.level(), host.frostCenter(), radius.evaluate(context), lasting);
        return true;
    }

    /**
     * Freezes the still water and lava whose block centers stand within a
     * sphere, and puts out the fire blocks and burning entities in it.
     *
     * @param level  the level
     * @param center the sphere's center
     * @param radius  the sphere's radius in blocks
     * @param lasting whether water freezes to magicked ice that never thaws
     */
    public static void freezeWithin(ServerLevel level, Vec3 center, double radius, boolean lasting) {
        // frost-reach-extinguishes-fire-and-burning-mobs
        FrostFire.putOutWithin(level, center, radius);
        BlockPos from = BlockPos.containing(center.subtract(radius, radius, radius));
        BlockPos to = BlockPos.containing(center.add(radius, radius, radius));
        double reachSquared = radius * radius;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (Vec3.atCenterOf(pos).distanceToSqr(center) <= reachSquared) {
                frozenForm(level.getBlockState(pos), lasting).ifPresent(frozen -> level.setBlock(pos, frozen,
                        Block.UPDATE_ALL));
            }
        }
    }

    /**
     * What a block freezes into: still water to ice, magicked where it lasts,
     * a still lava type to the block the molten groups pair it with, anything
     * else nothing.
     *
     * @param state   the block
     * @param lasting whether water freezes to magicked ice
     * @return its frozen form, or empty where it does not freeze
     */
    static Optional<BlockState> frozenForm(BlockState state, boolean lasting) {
        if (!state.getFluidState().isSource()) {
            return Optional.empty();
        }
        if (state.is(Blocks.WATER)) {
            return Optional.of(lasting ? GooBlocks.MAGICKED_ICE.get().defaultBlockState()
                    : Blocks.ICE.defaultBlockState());
        }
        return cooledFormOf(GooGroups.current(), BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                .flatMap(BuiltInRegistries.BLOCK::getOptional).map(Block::defaultBlockState);
    }

    /**
     * The block a lava type cools into: the member of the molten cooled group standing at the position the lava type
     * stands at in the molten group.
     * frost-cools-molten-blocks-into-molten-cooled-blocks
     * every-frost-freeze-turns-a-molten-iron-source-to-iron-core
     *
     * @param groups each group's members by group name
     * @param liquid the lava type's block id
     * @return the cooled block's id, empty where the molten group holds no such liquid or no partner stands for it
     */
    static Optional<Identifier> cooledFormOf(Map<String, List<Identifier>> groups, Identifier liquid) {
        return GooGroups.pairedMember(groups, MOLTEN_BLOCKS, MOLTEN_COOLED_BLOCKS, liquid);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.FROST);
    }
}
