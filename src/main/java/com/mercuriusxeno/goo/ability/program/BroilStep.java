package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.HoldMarks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Broil's held pass, run each held tick of the stream at only what the
 * player looks at: foliage under the crosshair burns away, a block under the
 * crosshair with a furnace result is painted and cooks in place once it has
 * built the JSON's ticks of heat, into that result where it is a block, or
 * popping that result out where it is an item, and each pulse burns every
 * living thing in the cells the stream's rays cross before they meet a
 * block. No fire block is placed.
 * decision broil-heat-wave-ring-cooks-ahead
 *
 * @param cookTicks the ticks of heat a block takes to cook
 * @param burns     the blocks the heat burns away
 * @param damage    the fire damage each pulse deals, evaluated when the pulse runs
 * @param every     the held ticks between pulses
 */
public record BroilStep(int cookTicks, TagKey<Block> burns, Expr damage, int every) implements Step {

    private static final String NAME = "broil";
    private static final String FIELD_COOK_TICKS = "cook_ticks";
    private static final String FIELD_BURNS = "burns";
    private static final String FIELD_DAMAGE = "damage";
    private static final String FIELD_EVERY = "every";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<BroilStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.fieldOf(FIELD_COOK_TICKS).forGetter(BroilStep::cookTicks),
            TagKey.codec(Registries.BLOCK).fieldOf(FIELD_BURNS).forGetter(BroilStep::burns),
            Expr.CODEC.fieldOf(FIELD_DAMAGE).forGetter(BroilStep::damage),
            Codec.INT.fieldOf(FIELD_EVERY).forGetter(BroilStep::every)
    ).apply(inst, BroilStep::new));

    /**
     * The registered type.
     */
    public static final StepType<BroilStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<BroilStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().ifPresent(aim -> {
            Function<Block, Optional<Block>> cooks = cookingIn(host.level());
            HoldMarks marks = host.holdMarks();
            // the operator's ruling on Broil: blocks are hit only under the crosshair, never the cone around it
            PaintedHold.lookedAtBlock(host, aim).ifPresent(looked -> {
                if (host.blockIn(looked, burns)) {
                    host.level().destroyBlock(looked, false, host.breaker());
                } else {
                    PaintedHold.paintSeen(host, List.of(looked), cooks, marks);
                }
            });
            PaintedHold.buildPainted(host, cooks, marks, 1f / cookTicks, OptionalInt.empty(),
                    (pos, raw) -> popSmeltedItem(host.level(), pos, raw));
            if (pulsesOn(aim.held(), every)) {
                float amount = damage.evaluateFloat(context);
                host.forEachLivingIn(List.copyOf(PaintedHold.seenReach(host, aim)), Set.of(),
                        target -> scald(host.level(), target.target(), amount));
            }
        });
        return true;
    }

    /**
     * Whether a held tick pulses: the hold's first tick and every
     * {@code every} ticks after it, as a stream's sound beats.
     *
     * @param held  the hold's tick count, 1 on its first tick
     * @param every the held ticks between pulses
     * @return true on a pulse
     */
    static boolean pulsesOn(int held, int every) {
        return every <= 1 || (held - 1) % every == 0;
    }

    /**
     * The block each block cooks into under a level's live recipes, each
     * block's answer looked up once for the tick.
     *
     * @param level the server level
     * @return the cooked block of a block, empty where it cooks into none
     */
    private static Function<Block, Optional<Block>> cookingIn(ServerLevel level) {
        Map<Block, Optional<Block>> cooked = new HashMap<>();
        return raw -> cooked.computeIfAbsent(raw,
                block -> cookedBlock(block, item -> smelted(level, item).map(ItemStack::getItem), Blocks.AIR));
    }

    /**
     * The block a block cooks into: its item's furnace result where that
     * result places a block other than the one it came from, or the empty
     * block it leaves where the result is an item that places none.
     * decision broil-heat-wave-ring-cooks-ahead
     *
     * @param raw    the block standing in the heat
     * @param smelt  the furnace result of an item, empty where no recipe smelts it
     * @param popped the block left where the raw block cooks into an item, air in play
     * @return the cooked block, or empty where the block does not cook
     */
    static Optional<Block> cookedBlock(Block raw, Function<Item, Optional<Item>> smelt, Block popped) {
        return smelt.apply(raw.asItem())
                .map(result -> result instanceof BlockItem placed ? placed.getBlock() : popped)
                .filter(block -> block != raw);
    }

    /**
     * Drops a cooked block's furnace result where the block stood, when it
     * cooked into an item rather than a block: a log pops out its charcoal.
     * decision broil-heat-wave-ring-cooks-ahead
     *
     * @param level the server level
     * @param pos   the block's position
     * @param raw   the block that cooked
     */
    private static void popSmeltedItem(ServerLevel level, BlockPos pos, Block raw) {
        smelted(level, raw.asItem())
                .filter(result -> !(result.getItem() instanceof BlockItem))
                .ifPresent(result -> Block.popResource(level, pos, result));
    }

    /**
     * An item's smelting result under the server's live recipes.
     *
     * @param level the server level
     * @param item  the item a furnace would take
     * @return the result, or empty where no smelting recipe takes it
     */
    private static Optional<ItemStack> smelted(ServerLevel level, Item item) {
        SingleRecipeInput input = new SingleRecipeInput(new ItemStack(item));
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
                .map(holder -> holder.value().assemble(input))
                .filter(result -> !result.isEmpty());
    }

    /**
     * Burns one living thing with fire through its damage immunity; a hit
     * that would kill sets it alight first, so its loot drops cooked as a
     * burning mob's does. A fire-immune thing is left alone.
     *
     * @param level  the server level
     * @param target the living thing in the cone
     * @param amount the fire damage
     */
    static void scald(ServerLevel level, LivingEntity target, float amount) {
        if (target.fireImmune()) {
            return;
        }
        if (amount >= target.getHealth() + target.getAbsorptionAmount()) {
            target.setRemainingFireTicks(1);
        }
        target.invulnerableTime = 0;
        target.hurtServer(level, target.damageSources().inFire(), amount);
        target.invulnerableTime = 0;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(damage);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}
