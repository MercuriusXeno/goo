package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.MycosisStep;
import com.mercuriusxeno.goo.ability.program.UndeadInversion;
import com.mercuriusxeno.goo.ability.spray.SprayPrograms;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jspecify.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Shroom's Mycosis cell: the volume cell bound to Mycosis' goo, which
 * infects a living thing touching it, standing on its top or wading in its
 * space, as the spore stream infected what it reached: the laying ability's
 * entity steps run on it, giving it the mycosis effect and the spores that
 * burst Mycosis from its corpse. A thing still carrying the effect is left
 * as it is, so the goo infects it anew once its infection lapses. The cell
 * spares the player who placed it. On each tick its cadence falls on, the
 * cell tends the nether wart and fungal buds in its reach and grows a
 * goologged bud in its own space, ripening there into a mushroom, then a bud
 * on each open spot in its reach, one a growing; lying on a shroom network,
 * it also spreads that network a block a growing. Each bud and each network
 * block spends layers, and once nothing remains to grow or take the goo
 * idles with its layers kept.
 * mycosis-goo-infects-what-touches-it-as-the-spray-did
 * mycosis-is-the-residual-goo-that-infests-with-mushrooms
 * mushrooms-and-buds-are-goo-logged-with-shroom-goo
 * mycosis-goo-disperses-spores-and-grows-shrooms-nearby-depleting
 * mycosis-grows-and-reaps-nether-wart
 * colonize-blob-grows-the-network
 */
public class MycosisCellBlock extends VolumeCellBlock {

    /**
     * Creates a Mycosis cell.
     *
     * @param properties the block properties
     * @param binding    the Mycosis binding
     */
    public MycosisCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
        registerDefaultState(defaultBlockState().setValue(GoologgedFungus.FUNGUS, GoologgedFungus.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GoologgedFungus.FUNGUS);
    }

    /**
     * Pours as any cell does, then, where the cell still stands, tends and
     * grows fungus on a tick its cadence falls on and keeps a tick coming, a
     * pending pour's or the next growing's, so saturated goo still tends.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        super.tick(state, level, pos, random);
        if (!level.getBlockState(pos).is(this)) {
            return;
        }
        MycosisStep.in(layingProgram(level)).ifPresent(mycosis -> {
            if (InfestGnaw.gnawsAt(level.getGameTime(), mycosis.growTicks())) {
                MycosisTending.tendAround(level, pos, mycosis.reach());
                growFungus(level, pos, mycosis);
                spreadNetwork(level, pos, mycosis);
            }
            if (level.getBlockState(pos).is(this) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.scheduleTick(pos, this, InfestGnaw.ticksToNextGnaw(level.getGameTime(), mycosis.growTicks()));
            }
        });
    }

    /**
     * Ripens the cell's own goologged fungus one age, then grows the next
     * bud where MycosisGrowth answers, in the cell's own space or on a
     * valid spot in its reach, spending its layers; spent to nothing, the
     * cell leaves its fungus standing alone. Saturated goo changes nothing.
     * mushrooms-and-buds-are-goo-logged-with-shroom-goo
     * mycosis-goo-disperses-spores-and-grows-shrooms-nearby-depleting
     * mycosis-pays-per-mob-infected-and-fungus-tended
     *
     * @param level   the server level
     * @param pos     the cell
     * @param mycosis Mycosis' step
     */
    private void growFungus(ServerLevel level, BlockPos pos, MycosisStep mycosis) {
        BlockState standing = level.getBlockState(pos);
        GoologgedFungus fungus = GoologgedFungus.in(standing);
        BlockState ripened = standing.setValue(GoologgedFungus.FUNGUS,
                fungus.tended(level.getBlockState(pos.below()), level.getRandom()));
        BlockState bud = mycosis.budState();
        MycosisGrowth.Growth growth = MycosisGrowth.nextGrowth(validSpots(level, pos, mycosis.reach(), bud),
                fungus == GoologgedFungus.NONE ? Set.of() : Set.of(pos), ripened.getValue(LAYERS),
                mycosis.layersPerBud());
        BlockState grown = growth.spot().filter(pos::equals)
                .map(own -> ripened.setValue(GoologgedFungus.FUNGUS, GoologgedFungus.BUD_0)).orElse(ripened);
        growth.spot().filter(spot -> !spot.equals(pos)).ifPresent(spot ->
                level.setBlock(spot, bud, UPDATE_ALL));
        BlockState after = growth.layersLeft() > 0 ? grown.setValue(LAYERS, growth.layersLeft())
                : GoologgedFungus.in(grown).standingAlone(bud);
        if (after != standing) {
            level.setBlock(pos, after, UPDATE_ALL);
        }
    }

    /**
     * Spreads the shroom network the cell lies on by one block, the nearest
     * in its reach it can take over, spending the layers a network block
     * costs; goo short of them, on no network or with nothing left to take
     * spreads nothing and keeps its layers.
     * colonize-blob-grows-the-network
     * colonize-is-dropped-and-its-power-moves-into-mycosis-goo
     * mycosis-pays-per-mob-infected-and-fungus-tended
     *
     * @param level   the server level
     * @param pos     the cell
     * @param mycosis Mycosis' step
     */
    private void spreadNetwork(ServerLevel level, BlockPos pos, MycosisStep mycosis) {
        BlockState standing = level.getBlockState(pos);
        if (!standing.is(this) || standing.getValue(LAYERS) < mycosis.layersPerNetwork()) {
            return;
        }
        BlockPos floor = pos.below();
        ShroomNetwork.of(level.getBlockState(floor)).ifPresent(network ->
                network.nextTaken(level, floor, mycosis.reach()).ifPresent(taken -> {
                    network.take(level, taken);
                    int left = standing.getValue(LAYERS) - mycosis.layersPerNetwork();
                    level.setBlock(pos, left > 0 ? standing.setValue(LAYERS, left)
                            : GoologgedFungus.in(standing).standingAlone(mycosis.budState()), UPDATE_ALL);
                }));
    }

    /**
     * The spots a bud could stand in, in growing order: the cell's own space
     * where it lies on a floor with room for a bud, then each open block in
     * its reach a fungal bud could stand in, shuffled so the spores scatter.
     * mycosis-goo-disperses-spores-and-grows-shrooms-nearby-depleting
     *
     * @param level the server level
     * @param pos   the cell
     * @param reach the blocks across it reaches
     * @param bud   the fungal bud's state, whose rule says where a bud can stand
     * @return the spots
     */
    private static List<BlockPos> validSpots(ServerLevel level, BlockPos pos, int reach, BlockState bud) {
        List<BlockPos> valid = new ArrayList<>();
        if (FungalBudBlock.standsOnAFloor(level, pos) && FungalBudBlock.hasRoomBeside(level, pos)) {
            valid.add(pos);
        }
        List<BlockPos> around = new ArrayList<>(MycosisTending.inReach(pos, reach).stream()
                .filter(spot -> level.getBlockState(spot).isAir() && bud.canSurvive(level, spot)).toList());
        Util.shuffle(around, level.getRandom());
        valid.addAll(around);
        return valid;
    }

    /**
     * Infects a thing standing on the goo's top, as every cell's surface then acts on it.
     */
    @Override
    public void stepOn(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                       @NonNull Entity entity) {
        infect(level, pos, entity);
        super.stepOn(level, pos, state, entity);
    }

    /**
     * Infects a thing in the goo's space, wading in it or standing on a cell short of a whole block.
     */
    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                @NonNull Entity entity, @NonNull InsideBlockEffectApplier effects,
                                boolean pastEdges) {
        infect(level, pos, entity);
        super.entityInside(state, level, pos, entity, effects, pastEdges);
    }

    /**
     * Runs the laying ability's entity steps on a living thing touching the
     * goo, on the server, the cell's placer as their source; the placer, and
     * a thing whose infection still stands, are left as they are.
     * spared as Vines spares its caster (operator ruling 2026-11-17 on Vines: the caster is spared their own goo)
     *
     * @param level  the level the cell stands in
     * @param pos    the cell
     * @param entity the entity touching the cell
     */
    private void infect(Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel server && entity instanceof LivingEntity living && infectable(living)) {
            ServerPlayer placer = creditedPlayerAt(server, pos);
            AbilityDefinition mycosis = AbilityRegistry.of(server).getAbility(binding().layingAbility());
            if (mycosis != null && !UndeadInversion.sparesCaster(living, placer)) {
                SprayPrograms.runOnLiving(server, living, placer, mycosis);
            }
        }
    }

    /**
     * Whether the goo infects a living thing touching it: a live one, no
     * spectator, whose last infection has lapsed.
     *
     * @param living the living thing touching the cell
     * @return true where the goo infects it
     */
    private static boolean infectable(LivingEntity living) {
        return living.isAlive() && !living.isSpectator() && !living.hasEffect(GooMobEffects.MYCOSIS);
    }

    /**
     * A Mycosis cell spares its placer, so the cell remembers who placed it.
     * standing-goo-thing-credits-whoever-placed-it
     */
    @Override
    protected boolean creditsItsPlacer() {
        return true;
    }
}
