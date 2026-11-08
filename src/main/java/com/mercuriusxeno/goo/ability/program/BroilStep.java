package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.HoldMarks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Broil's held pass, run each held tick of the stream at only what the
 * player looks at: foliage under the crosshair burns away, a block under the
 * crosshair with a furnace result is painted and cooks in place once it has
 * built the JSON's ticks of heat, through the one finish every cooked block
 * takes, and each pulse burns every living thing in the cells the stream's
 * rays cross before they meet a block. No fire block is placed.
 * decision broil-heat-wave-ring-cooks-ahead
 * decision blocks-that-cook-into-items-cook-like-blocks
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
            Function<Item, Optional<ItemStack>> results = BlockCooking.resultsIn(host.level());
            Function<Block, Optional<Block>> cooks = raw -> BlockCooking.cookedBlock(raw,
                    item -> results.apply(item).map(ItemStack::getItem), Blocks.AIR);
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
                    (pos, raw, into) -> results.apply(raw.asItem()).ifPresent(result -> BlockCooking.finishCooked(host,
                            pos, result.copy(), Blocks.AIR.defaultBlockState())));
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
     * Burns one living thing with fire through its damage immunity; a hit
     * that would kill sets it alight first, so its loot drops cooked as a
     * burning mob's does. A fire-immune thing is left alone.
     *
     * @param level  the server level
     * @param target the living thing in the cone
     * @param amount the fire damage
     */
    static void scald(ServerLevel level, LivingEntity target, float amount) {
        if (!catchesFire(target)) {
            return;
        }
        if (amount >= target.getHealth() + target.getAbsorptionAmount()) {
            target.setRemainingFireTicks(1);
        }
        target.invulnerableTime = 0;
        target.hurtServer(level, target.damageSources().inFire(), amount);
        target.invulnerableTime = 0;
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
     * Broil takes a living thing that is not fire-immune, the one it burns.
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
