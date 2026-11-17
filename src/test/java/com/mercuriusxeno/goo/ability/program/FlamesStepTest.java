package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.petrify.BlockExposures;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Each Flames burn sets a living thing alight before its fire hit lands,
 * so a killing burn drops cooked loot, and spares the fire-immune
 * (decision flames-pixelated-fire-stream), the hit landing through vanilla's
 * hurt, which waits out the mob's invulnerability window
 * (decision flames-and-heater-respect-i-frames), and the
 * shipped Flames carries the ticks a block takes to cook under it
 * (decision flames-absorbs-broil-and-cooks-blocks).
 */
class FlamesStepTest {

    private static final float DAMAGE = 1f;
    private static final int BURN_SECONDS = 5;

    private final ServerLevel level = mock(ServerLevel.class);
    private final LivingEntity mob = mock(LivingEntity.class);
    private final DamageSource inFire = mock(DamageSource.class);

    FlamesStepTest() {
        DamageSources sources = mock(DamageSources.class);
        when(sources.inFire()).thenReturn(inFire);
        when(mob.damageSources()).thenReturn(sources);
        when(mob.getHealth()).thenReturn(20f);
    }

    @Test
    void aBurnSetsTheMobAlightThenHurtsItWithFire() {
        FlamesStep.burn(level, mob, DAMAGE, BURN_SECONDS, null);

        InOrder order = inOrder(mob);
        order.verify(mob).igniteForSeconds(BURN_SECONDS);
        order.verify(mob).hurtServer(level, inFire, DAMAGE);
    }

    @Test
    void aLivingMobCatchesFire() {
        assertEquals(true, FlamesStep.catchesFire(mob));
    }

    @Test
    void aFireImmuneMobCatchesNoFire() {
        when(mob.fireImmune()).thenReturn(true);

        assertEquals(false, FlamesStep.catchesFire(mob));
    }

    @Test
    void theFirstTickAndEveryFourthAfterPulse() {
        assertTrue(FlamesStep.pulsesOn(1, 4));
        assertFalse(FlamesStep.pulsesOn(2, 4));
        assertFalse(FlamesStep.pulsesOn(4, 4));
        assertTrue(FlamesStep.pulsesOn(5, 4));
        assertTrue(FlamesStep.pulsesOn(9, 4));
    }

    @Test
    void aPulseEveryTickPulsesEachTick() {
        assertTrue(FlamesStep.pulsesOn(2, 1));
    }

    /**
     * decision flames-melts-vanilla-meltable-ice-and-snow-over-the-hold
     */
    @Nested
    class HeatedBlock {

        private final Block ice = mock(Block.class);
        private final Block water = mock(Block.class);
        private final Block air = mock(Block.class);
        private final Block sand = mock(Block.class);
        private final Item sandItem = mock(Item.class);
        private final Block glass = mock(Block.class);
        private final BlockItem glassItem = mock(BlockItem.class);
        private final BlockCooking.Meltables meltables =
                new BlockCooking.Meltables(ice, mock(Block.class), mock(Block.class), water, air);
        private final Function<Block, Optional<Block>> heats;

        HeatedBlock() {
            when(ice.asItem()).thenReturn(mock(Item.class));
            when(sand.asItem()).thenReturn(sandItem);
            when(glassItem.getBlock()).thenReturn(glass);
            heats = BlockCooking.heatedInto(raw -> Optional.empty(), meltables, false,
                    item -> item == sandItem ? Optional.of(glassItem) : Optional.empty(), air);
        }

        @Test
        void iceInTheFlameBecomesWater() {
            assertEquals(Optional.of(water), heats.apply(ice));
        }

        @Test
        void sandInTheFlameBecomesItsFurnaceResult() {
            assertEquals(Optional.of(glass), heats.apply(sand));
        }

        @Test
        void heatingIceCarriesTheMeltMarkAndSandNone() {
            Function<Block, OptionalInt> marks = FlamesStep.exposureMark(
                    raw -> BlockCooking.meltedBlock(raw, meltables, false));

            assertEquals(OptionalInt.of(BlockExposures.MELTS_AWAY), marks.apply(ice));
            assertEquals(OptionalInt.empty(), marks.apply(sand));
        }

        @Test
        void aMeltedBlockBecomesItsMelt() {
            BlockBreakHost host = mock(BlockBreakHost.class);
            BlockPos pos = new BlockPos(3, 1, 2);
            BlockState waterState = mock(BlockState.class);
            when(water.defaultBlockState()).thenReturn(waterState);

            BlockCooking.finishHeated(host, pos, ice, water, raw -> BlockCooking.meltedBlock(raw, meltables, false),
                    item -> Optional.empty(), mock(BlockState.class));

            verify(host).transformBlock(pos, waterState);
        }
    }

    /**
     * Flames heats every block Molten's melts_into names into the block it names as Molten melts it, read from the
     * map itself, so a map with another pair melts that pair too
     * (decision flames-melts-obsidian-magma-and-iron-core-as-molten-does).
     */
    @Nested
    class MeltsAsMoltenDoes {

        private final Map<Identifier, Block> blocks = new HashMap<>();
        private final BlockCooking.Meltables meltables = new BlockCooking.Meltables(mock(Block.class),
                mock(Block.class), mock(Block.class), mock(Block.class), mock(Block.class));

        private Block blockOf(Identifier id) {
            return blocks.computeIfAbsent(id, unused -> {
                Block block = mock(Block.class);
                when(block.asItem()).thenReturn(mock(Item.class));
                return block;
            });
        }

        private Identifier idOf(Block block) {
            return blocks.entrySet().stream().filter(entry -> entry.getValue() == block).map(Map.Entry::getKey)
                    .findFirst().orElseThrow();
        }

        private Function<Block, Optional<Block>> flamesHeatsUnder(Map<Identifier, List<String>> meltsInto) {
            Function<Block, Optional<Block>> moltenMelts = MoltenStep.meltsOf(meltsInto, this::idOf,
                    (block, tag) -> false, Map.of(), id -> Optional.of(blockOf(id)));
            return BlockCooking.heatedInto(moltenMelts, meltables, false, item -> Optional.empty(),
                    meltables.air());
        }

        private void assertHeatsEveryPair(Map<Identifier, List<String>> meltsInto) {
            Function<Block, Optional<Block>> heats = flamesHeatsUnder(meltsInto);
            meltsInto.forEach((key, named) -> named.forEach(raw -> assertEquals(
                    Optional.of(blockOf(key)), heats.apply(blockOf(Identifier.parse(raw))), raw)));
        }

        @Test
        void flamesMeltsEveryBlockTheShippedMoltenMeltsIntoTheSameBlock() {
            Map<Identifier, List<String>> shipped =
                    MoltenStep.in(AbilityJson.decode("blaze_molten").behaviors()).orElseThrow().meltsInto();

            assertHeatsEveryPair(shipped);
        }

        @Test
        void flamesMeltsAPairAddedToMoltensMap() {
            Map<Identifier, List<String>> withAPair = new HashMap<>(
                    MoltenStep.in(AbilityJson.decode("blaze_molten").behaviors()).orElseThrow().meltsInto());
            withAPair.put(Identifier.parse("pack:hot_slag"), List.of("pack:slag"));

            assertHeatsEveryPair(withAPair);
        }
    }
}
