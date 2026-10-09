package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.reap.Reaping;
import com.mercuriusxeno.goo.block.ability.FungalBudBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Mycosis's block step, run each held tick of the stream: Growth's tick for
 * fungi. Every nether wart in the cone takes a random tick so it grows, and
 * every fungal bud ages at wart's pace, one held tick in ten, ripening
 * through its ages into its mushroom on its own rule; each wart standing at its full age is reaped, dropping its
 * loot settled against the wart it is replanted from and standing again at
 * age 0.
 * mycosis-grows-and-reaps-nether-wart
 */
public record TendFungiStep() implements Step {

    private static final String NAME = "tend_fungi";
    /**
     * A held bud ages on one tick in this many, nether wart's own pace, so a
     * bud the spray places ripens over a couple of seconds rather than at once.
     * mycosis-grows-and-reaps-nether-wart
     */
    static final int BUD_ODDS = 10;

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<TendFungiStep> CODEC = MapCodec.unit(TendFungiStep::new);

    /**
     * The registered type.
     */
    public static final StepType<TendFungiStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<TendFungiStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().ifPresent(aim -> {
            ServerLevel level = host.level();
            for (BlockPos pos : CalcifyStep.blocksInCone(host.eye(), aim.aimPoint(), aim.coneDegrees())) {
                tendFungus(level, pos);
            }
        });
        return true;
    }

    /**
     * Grows or reaps the fungus in a cell; a cell holding none is left as it is.
     *
     * @param level the server level
     * @param pos   the cell
     */
    private static void tendFungus(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof NetherWartBlock) {
            if (ripeWart(state.getValue(NetherWartBlock.AGE))) {
                Reaping.replant(level, pos, state, state.setValue(NetherWartBlock.AGE, 0));
            } else {
                state.randomTick(level, pos, level.getRandom());
            }
        } else if (state.getBlock() instanceof FungalBudBlock && budAges(level.getRandom().nextInt(BUD_ODDS))) {
            state.randomTick(level, pos, level.getRandom());
        }
    }

    /**
     * Whether a bud ages this held tick, on the roll of one in {@link #BUD_ODDS}.
     *
     * @param roll a roll from zero below the odds
     * @return true on the one roll in the odds that ages the bud
     */
    static boolean budAges(int roll) {
        return roll == 0;
    }

    /**
     * Whether a nether wart stands at its full age, ripe for the reaping.
     *
     * @param age the wart's age
     * @return true for a ripe wart
     */
    static boolean ripeWart(int age) {
        return age >= NetherWartBlock.MAX_AGE;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}
