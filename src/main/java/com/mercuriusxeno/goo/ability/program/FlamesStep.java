package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.petrify.BlockExposures;
import com.mercuriusxeno.goo.network.HoldMarks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Flames' held pass, run each held tick of the stream: every {@code every}
 * held ticks it casts a spray of {@link FlameRays} from the eye down the look
 * and burns each living thing a ray's line crosses, keeping it alight for the
 * JSON's seconds and dealing the JSON's fire damage, which waits out its
 * invulnerability window. A fire-immune thing is left alone, and no fire
 * block is placed. Each held tick the flame also heats what the player looks
 * at: foliage under the crosshair burns away, and a block under the crosshair
 * that melts or has a furnace result is painted and melts or cooks in place
 * once it has built the JSON's ticks of heat.
 * flames-burns-by-a-spray-of-rays
 * decision flames-absorbs-broil-and-cooks-blocks
 * decision flames-melts-vanilla-meltable-ice-and-snow-over-the-hold
 *
 * @param damage      the fire damage each burn deals, evaluated when it runs
 * @param every       the held ticks between burns
 * @param burnSeconds the seconds each burn keeps the thing alight
 * @param cookTicks   the ticks of heat a block takes to cook
 * @param burns       the blocks the heat burns away
 */
public record FlamesStep(Expr damage, int every, int burnSeconds, int cookTicks, TagKey<Block> burns)
        implements Step {

    private static final String NAME = "flames";
    private static final String FIELD_DAMAGE = "damage";
    private static final String FIELD_EVERY = "every";
    private static final String FIELD_BURN_SECONDS = "burn_seconds";
    private static final String FIELD_COOK_TICKS = "cook_ticks";
    private static final String FIELD_BURNS = "burns";
    private static final double HALF = 0.5;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<FlamesStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_DAMAGE).forGetter(FlamesStep::damage),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_EVERY).forGetter(FlamesStep::every),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf(FIELD_BURN_SECONDS).forGetter(FlamesStep::burnSeconds),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_COOK_TICKS).forGetter(FlamesStep::cookTicks),
            TagKey.codec(Registries.BLOCK).fieldOf(FIELD_BURNS).forGetter(FlamesStep::burns)
    ).apply(inst, FlamesStep::new));

    /**
     * The registered type.
     */
    public static final StepType<FlamesStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<FlamesStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().ifPresent(aim -> {
            cookLookedAt(host, aim);
            if (pulsesOn(aim.held(), every)) {
                burnCrossed(host, aim, damage.evaluateFloat(context));
            }
        });
        return true;
    }

    /**
     * Heats the block under the crosshair, burning foliage away and painting
     * a block that melts or has a furnace result, then builds every block the
     * hold painted toward its melt or result, each finishing through
     * {@link #finishHeated}. Whether water evaporates is read at the eye, the
     * painted blocks standing within the stream's reach of it.
     * decision flames-absorbs-broil-and-cooks-blocks
     * decision flames-melts-vanilla-meltable-ice-and-snow-over-the-hold
     *
     * @param host the channel host
     * @param aim  the stream's aim
     */
    private void cookLookedAt(ChannelHost host, ChannelAim aim) {
        Function<Item, Optional<ItemStack>> results = BlockCooking.resultsIn(host.level());
        boolean waterEvaporates = host.level().environmentAttributes()
                .getValue(EnvironmentAttributes.WATER_EVAPORATES, BlockPos.containing(host.eye()));
        BlockCooking.Meltables meltables = new BlockCooking.Meltables(Blocks.ICE, Blocks.FROSTED_ICE, Blocks.SNOW,
                Blocks.WATER, Blocks.AIR);
        Function<Block, Optional<Block>> melts = raw -> BlockCooking.meltedBlock(raw, meltables, waterEvaporates);
        Function<Block, Optional<Block>> cooks = heatedInto(meltables, waterEvaporates,
                item -> results.apply(item).map(ItemStack::getItem), Blocks.AIR);
        HoldMarks marks = host.holdMarks();
        PaintedHold.lookedAtBlock(host, aim).ifPresent(looked -> {
            if (host.blockIn(looked, burns)) {
                host.level().destroyBlock(looked, false, host.breaker());
            } else {
                PaintedHold.paintSeen(host, List.of(looked), cooks, marks);
            }
        });
        PaintedHold.buildPainted(host, cooks, marks, 1f / cookTicks, exposureMark(melts),
                (pos, raw, into) -> finishHeated(host, pos, raw, into, melts, results,
                        Blocks.AIR.defaultBlockState()));
    }

    /**
     * The block a block in the flame becomes: its melt where it melts, its
     * furnace result otherwise.
     * decision flames-melts-vanilla-meltable-ice-and-snow-over-the-hold
     *
     * @param meltables       the blocks that melt and what they melt into
     * @param waterEvaporates whether the dimension evaporates water
     * @param smelt           the furnace result of an item, empty where no recipe smelts it
     * @param emptied         the block left where a block cooks into an item, air in play
     * @return the block each block becomes, empty where it neither melts nor cooks
     */
    static Function<Block, Optional<Block>> heatedInto(BlockCooking.Meltables meltables, boolean waterEvaporates,
                                                       Function<Item, Optional<Item>> smelt, Block emptied) {
        return raw -> BlockCooking.meltedBlock(raw, meltables, waterEvaporates)
                .or(() -> BlockCooking.cookedBlock(raw, smelt, emptied));
    }

    /**
     * The mark each heating block's exposure carries to the clients: a
     * melting block carries the melt mark, so it shows nothing while it heats
     * and is eaten away once it melts, as Blaze's Molten eats stone; any other is
     * untinted, cooking as it always has.
     * decision flames-melts-vanilla-meltable-ice-and-snow-over-the-hold
     *
     * @param melts the block a block melts into, empty where it does not melt
     * @return the mark of each block, empty for untinted
     */
    static Function<Block, OptionalInt> exposureMark(Function<Block, Optional<Block>> melts) {
        return raw -> melts.apply(raw).isPresent() ? OptionalInt.of(BlockExposures.MELTS_AWAY) : OptionalInt.empty();
    }

    /**
     * Finishes a block the flame filled: a melting block becomes its melt
     * with no item rested, and any other takes the one cooking finish.
     * decision flames-melts-vanilla-meltable-ice-and-snow-over-the-hold
     *
     * @param host    the host the block heats under
     * @param pos     the block
     * @param raw     the block painted there
     * @param into    the block it becomes
     * @param melts   the block a block melts into, empty where it does not melt
     * @param results the furnace result of an item, empty where no recipe smelts it
     * @param emptied the state left where a block cooks into an item, air in play
     */
    static void finishHeated(BlockBreakHost host, BlockPos pos, Block raw, Block into,
                             Function<Block, Optional<Block>> melts, Function<Item, Optional<ItemStack>> results,
                             BlockState emptied) {
        if (melts.apply(raw).isPresent()) {
            host.transformBlock(pos, into.defaultBlockState());
        } else {
            results.apply(raw.asItem()).ifPresent(result ->
                    BlockCooking.finishCooked(host, pos, result.copy(), emptied));
        }
    }

    /**
     * Burns each living thing this pulse's spray of rays crosses.
     *
     * @param host   the channel host
     * @param aim    the stream's aim
     * @param amount the fire damage
     */
    private void burnCrossed(ChannelHost host, ChannelAim aim, float amount) {
        List<LivingEntity> inReach = new ArrayList<>();
        host.forEachLivingIn(cellsAround(host.eye(), aim), Set.of(), target -> inReach.add(target.target()));
        for (LivingEntity target : FlameRays.crossed(sprayOf(host, aim), inReach, LivingEntity::getBoundingBox)) {
            burn(host.level(), target, amount, burnSeconds);
        }
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
     * Whether fire burns an entity: a living thing that is not fire-immune.
     *
     * @param entity the candidate
     * @return true where fire burns it
     */
    static boolean catchesFire(Entity entity) {
        return entity instanceof LivingEntity && !entity.fireImmune();
    }

    /**
     * This pulse's spray: rays from the eye down the look, inside the
     * stream's cone, each reaching as far as the aim does.
     *
     * @param host the channel host
     * @param aim  the stream's aim, its reach along the look
     * @return the rays
     */
    private static List<FlameRays.Ray> sprayOf(ChannelHost host, ChannelAim aim) {
        Vec3 eye = host.eye();
        Vec3 look = aim.aimPoint().subtract(eye);
        double range = look.length();
        Vec3 axis = range > 0 ? look.scale(1 / range) : new Vec3(0, 0, 1);
        return FlameRays.spray(eye, axis, aim.coneDegrees(), range, host.level().getRandom(),
                FlameRays.clipIn(host.level()));
    }

    /**
     * The cells around everything the spray can reach: the reach along the
     * look, widened by the cone's widest and a block, so every living thing
     * a ray may cross stands in one of them.
     *
     * @param eye the eye
     * @param aim the stream's aim
     * @return the cells
     */
    private static List<BlockPos> cellsAround(Vec3 eye, ChannelAim aim) {
        double range = aim.aimPoint().distanceTo(eye);
        double widest = range * Math.tan(Math.toRadians(aim.coneDegrees() * HALF)) + 1;
        AABB reach = new AABB(eye, aim.aimPoint()).inflate(widest);
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(reach.minX, reach.minY, reach.minZ),
                BlockPos.containing(reach.maxX, reach.maxY, reach.maxZ))) {
            cells.add(cell.immutable());
        }
        return cells;
    }

    /**
     * Sets one living thing alight and burns it, alight before the hit lands
     * so a killing burn drops cooked loot. The hit waits out the thing's
     * invulnerability window, so a burn lands at most once per half second.
     * A fire-immune thing is left alone.
     * flames-and-heater-respect-i-frames
     *
     * @param level       the server level
     * @param target      the living thing in the cone
     * @param amount      the fire damage
     * @param burnSeconds the seconds it stays alight
     */
    static void burn(ServerLevel level, LivingEntity target, float amount, int burnSeconds) {
        if (!catchesFire(target)) {
            return;
        }
        target.igniteForSeconds(burnSeconds);
        target.hurtServer(level, target.damageSources().inFire(), amount);
    }

    /**
     * Flames takes a living thing that is not fire-immune, the one it burns.
     * decision steps-declare-the-entities-they-take
     *
     * @param entity the candidate
     * @return true where the step acts on it
     */
    @Override
    public boolean takesEntity(Entity entity) {
        return catchesFire(entity);
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
