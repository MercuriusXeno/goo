package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.HeartOverlayStep;
import com.mercuriusxeno.goo.ability.program.GluttonyStep;
import com.mercuriusxeno.goo.ability.program.LeaderStep;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Held durative effects: each pays its upkeep per tick after its start tick, ends when it cannot pay or is ended, and a heart-changing start ends any other heart-changing effect (decision self-effects-trickle-until-ended). */
class HeldEffectsTest {

    private static final long STARTED = 100L;
    private static final int UPKEEP = 1;
    private static final Identifier KINDLE = Identifier.fromNamespaceAndPath("goo", "blaze_kindle");
    private static final Identifier BARKSKIN = Identifier.fromNamespaceAndPath("goo", "leaf_barkskin");
    private static final Identifier GLUTTONY = Identifier.fromNamespaceAndPath("goo", "jelly_gluttony");

    private static HeldEffects.Held hearts(Identifier ability, ResourceKey<GooTypeDefinition> type) {
        return new HeldEffects.Held(ability, type, UPKEEP, Set.of(LaidState.HEART_OVERLAY), STARTED);
    }

    private static HeldEffects.Held gluttony() {
        return new HeldEffects.Held(GLUTTONY, GooTypes.JELLY, UPKEEP, Set.of(LaidState.GLUTTONY), STARTED);
    }

    private static HeldEffects holding(HeldEffects.Held... held) {
        HeldEffects effects = HeldEffects.NONE;
        for (HeldEffects.Held effect : held) {
            effects = effects.start(effect).after();
        }
        return effects;
    }

    @Nested
    class Tick {

        @Test
        void anEffectPaysNothingOnTheTickItStarted() {
            HeldEffects.Ticked ticked = holding(hearts(KINDLE, GooTypes.BLAZE)).tick(type -> 0, STARTED);
            assertEquals(Map.of(), ticked.drawn());
            assertTrue(ticked.ended().isEmpty());
        }

        @Test
        void anEffectPaysItsUpkeepEachTickAfter() {
            HeldEffects held = holding(hearts(KINDLE, GooTypes.BLAZE));
            HeldEffects.Ticked ticked = held.tick(type -> 10, STARTED + 1);
            assertEquals(Map.of(GooTypes.BLAZE, UPKEEP), ticked.drawn());
            assertSame(held, ticked.after());
        }

        @Test
        void anEffectEndsWhenTheInventoryCannotPay() {
            HeldEffects.Ticked ticked = holding(hearts(KINDLE, GooTypes.BLAZE)).tick(type -> UPKEEP - 1, STARTED + 1);
            assertEquals(List.of(KINDLE), ticked.ended().stream().map(HeldEffects.Held::ability).toList());
            assertTrue(ticked.after().isEmpty());
            assertEquals(Map.of(), ticked.drawn());
        }

        @Test
        void effectsSharingATypeDrawInTurnAndTheLastUnpaidEnds() {
            HeldEffects.Held first = new HeldEffects.Held(KINDLE, GooTypes.BLAZE, UPKEEP, Set.of(), STARTED);
            HeldEffects.Held second = new HeldEffects.Held(GLUTTONY, GooTypes.BLAZE, UPKEEP, Set.of(), STARTED);
            HeldEffects.Ticked ticked = holding(first, second).tick(type -> UPKEEP, STARTED + 1);
            assertEquals(Map.of(GooTypes.BLAZE, UPKEEP), ticked.drawn());
            assertEquals(List.of(second), ticked.ended());
            assertTrue(ticked.after().holds(KINDLE));
        }

        @Test
        void aHalfUpkeepDrawsOneEveryOtherTickItsRemainderPayingBetween() {
            HeldEffects held = holding(new HeldEffects.Held(KINDLE, GooTypes.BLAZE, GooFraction.of(1, 2), Set.of(),
                    STARTED));
            GooRemainders remainders = GooRemainders.EMPTY;
            List<Integer> draws = new ArrayList<>();
            for (long now = STARTED + 1; now <= STARTED + 4; now++) {
                HeldEffects.Ticked ticked = held.tick(type -> 10, remainders, now);
                draws.add(ticked.drawn().getOrDefault(GooTypes.BLAZE, 0));
                remainders = ticked.remainders();
                assertTrue(ticked.ended().isEmpty());
            }

            assertEquals(List.of(1, 0, 1, 0), draws);
            assertEquals(GooFraction.ZERO, remainders.of(GooTypes.BLAZE));
        }
    }

    @Nested
    class StartAndEnd {

        @Test
        void aHeartChangingStartEndsTheOtherHeartChangingEffect() {
            HeldEffects.Changed changed = holding(hearts(KINDLE, GooTypes.BLAZE), gluttony())
                    .start(hearts(BARKSKIN, GooTypes.LEAF));
            assertEquals(List.of(KINDLE), changed.ended().stream().map(HeldEffects.Held::ability).toList());
            assertTrue(changed.after().holds(BARKSKIN));
            assertTrue(changed.after().holds(GLUTTONY));
            assertFalse(changed.after().holds(KINDLE));
        }

        @Test
        void aStartChangingNoHeartsEndsNothing() {
            HeldEffects.Changed changed = holding(hearts(KINDLE, GooTypes.BLAZE)).start(gluttony());
            assertTrue(changed.ended().isEmpty());
            assertTrue(changed.after().holds(KINDLE));
        }

        @Test
        void anEffectWhoseAbilityIsNoLongerKnownEndsAndTheRestStand() {
            HeldEffects.Changed changed = holding(hearts(KINDLE, GooTypes.BLAZE), gluttony())
                    .endUnknown(ability -> !ability.equals(KINDLE));
            assertEquals(List.of(KINDLE), changed.ended().stream().map(HeldEffects.Held::ability).toList());
            assertFalse(changed.after().holds(KINDLE));
            assertTrue(changed.after().holds(GLUTTONY));
        }

        @Test
        void endingAHeldEffectNamesIt() {
            HeldEffects.Changed changed = holding(hearts(KINDLE, GooTypes.BLAZE)).end(KINDLE);
            assertEquals(List.of(hearts(KINDLE, GooTypes.BLAZE)), changed.ended());
            assertTrue(changed.after().isEmpty());
        }

        @Test
        void endingAnEffectNotHeldChangesNothing() {
            HeldEffects held = holding(gluttony());
            HeldEffects.Changed changed = held.end(KINDLE);
            assertSame(held, changed.after());
            assertTrue(changed.ended().isEmpty());
        }
    }

    /** The effect list shows each glove effect's time left: its type's goo over the upkeep drawn a tick (decision self-effects-trickle-until-ended). */
    @Nested
    class TimeLeft {

        @Test
        void aGloveEffectsTimeIsItsGooOverItsUpkeep() {
            HeldEffects held = holding(hearts(KINDLE, GooTypes.BLAZE));
            assertEquals(Map.of(GooTypes.BLAZE, 2000), held.ticksLeft(type -> 2000));
        }

        @Test
        void effectsSharingATypeSplitItsGoo() {
            HeldEffects.Held first = new HeldEffects.Held(KINDLE, GooTypes.BLAZE, UPKEEP, Set.of(), STARTED);
            HeldEffects.Held second = new HeldEffects.Held(GLUTTONY, GooTypes.BLAZE, UPKEEP, Set.of(), STARTED);
            assertEquals(Map.of(GooTypes.BLAZE, 1000), holding(first, second).ticksLeft(type -> 2000));
        }

        /** The per-type upkeep a tick, which ticksLeft divides by and the wheel's counts subtract (decision time-counts-net-standing-upkeep). */
        @Test
        void standingUpkeepsOnATypeSumPerTick() {
            HeldEffects.Held first = new HeldEffects.Held(KINDLE, GooTypes.BLAZE, 1, Set.of(), STARTED);
            HeldEffects.Held second = new HeldEffects.Held(GLUTTONY, GooTypes.BLAZE, 2, Set.of(), STARTED);
            HeldEffects held = holding(first, second);

            assertEquals(Map.of(GooTypes.BLAZE, 3.0), held.upkeepPerTick());
            assertEquals(Map.of(GooTypes.BLAZE, (int) (3000 / 3.0)), held.ticksLeft(type -> 3000));
        }

        @Test
        void anExtenderHalvesTheOthersAndPaysItsOwn() {
            HeldEffects.Held first = new HeldEffects.Held(KINDLE, GooTypes.BLAZE, 1, Set.of(), STARTED);
            HeldEffects.Held second = new HeldEffects.Held(GLUTTONY, GooTypes.BLAZE, 2, Set.of(), STARTED);
            HeldEffects.Held extender = new HeldEffects.Held(Identifier.fromNamespaceAndPath("goo", "pulse_extender"),
                    GooTypes.PULSE, 1, Set.of(LaidState.EXTENDER), STARTED);
            HeldEffects held = holding(first, second, extender);

            assertEquals(Map.of(GooTypes.BLAZE, 1.5, GooTypes.PULSE, 1.0), held.upkeepPerTick());
            assertTrue(held.extended());
            assertEquals(Map.of(GooTypes.BLAZE, (int) (3000 / 1.5), GooTypes.PULSE, 3000),
                    held.ticksLeft(type -> 3000));
        }
    }

    /** Every end plays the ended effect's down cue once (decision held-effects-sound-up-and-down). */
    @Nested
    class DownSound {

        private final List<SoundCue> played = new ArrayList<>();

        private List<Identifier> playedIds(List<HeldEffects.Held> ended) {
            HeldEffects.soundEnds(ended, played::add);
            return played.stream().map(SoundCue::sound).toList();
        }

        @Test
        void endingByPressPlaysTheSharedCueOnce() {
            List<HeldEffects.Held> ended = holding(hearts(KINDLE, GooTypes.BLAZE)).end(KINDLE).ended();
            assertEquals(List.of(GooSoundIds.ABILITY_DOWN), playedIds(ended));
        }

        @Test
        void runningDryPlaysTheCueOnce() {
            List<HeldEffects.Held> ended = holding(hearts(KINDLE, GooTypes.BLAZE)).tick(type -> 0, STARTED + 1).ended();
            assertEquals(List.of(GooSoundIds.ABILITY_DOWN), playedIds(ended));
        }

        @Test
        void aReplacingHeartEffectPlaysTheReplacedCueOnce() {
            List<HeldEffects.Held> ended = holding(hearts(KINDLE, GooTypes.BLAZE))
                    .start(hearts(BARKSKIN, GooTypes.LEAF)).ended();
            assertEquals(List.of(GooSoundIds.ABILITY_DOWN), playedIds(ended));
        }

        @Test
        void runningDryPlaysTheAbilitysOwnCueOnce() {
            SoundCue own = SoundCue.of(Identifier.withDefaultNamespace("block.fire.extinguish"));
            HeldEffects.Held kindle = new HeldEffects.Held(KINDLE, GooTypes.BLAZE, GooFraction.of(UPKEEP),
                    Set.of(LaidState.HEART_OVERLAY), STARTED, own);
            List<HeldEffects.Held> ended = holding(kindle).tick(type -> 0, STARTED + 1).ended();
            assertEquals(List.of(own.sound()), playedIds(ended));
        }
    }

    @Test
    void laidStateIsReadFromTheProgram() {
        assertEquals(Set.of(LaidState.HEART_OVERLAY), LaidState.laidBy(List.of(new HeartOverlayStep(HeartKind.KINDLE))));
        assertEquals(Set.of(LaidState.GLUTTONY), LaidState.laidBy(List.of(new GluttonyStep(Expr.literal(20),
                Expr.literal(20)))));
        assertEquals(Set.of(), LaidState.laidBy(List.of()));
    }

    /** decision leader-is-zoos-brew-ability */
    @Test
    void leaderStepLaysLeader() {
        assertEquals(Set.of(LaidState.LEADER), LaidState.laidBy(List.of(new LeaderStep(12, 4))));
    }

    /** The Extender held beside other effects (decision extender-multiplies-the-next-self-duration). */
    @Nested
    class Extender {

        private static final Identifier EXTENDER = Identifier.fromNamespaceAndPath("goo", "pulse_extender");
        private static final int TICKS = 20;
        private static final int PLENTY = 1000;

        private HeldEffects.Held gloveExtender() {
            return new HeldEffects.Held(EXTENDER, GooTypes.PULSE, UPKEEP, Set.of(LaidState.EXTENDER), STARTED);
        }

        @Test
        void extenderBesideKindlePaysPulseEveryTickAndCoversEveryOtherBlazeTick() {
            HeldEffects held = holding(gloveExtender(), hearts(KINDLE, GooTypes.BLAZE));
            int pulse = 0;
            int blaze = 0;
            for (long now = STARTED + 1; now <= STARTED + TICKS; now++) {
                HeldEffects.Ticked ticked = held.tick(type -> PLENTY, now);
                pulse += ticked.drawn().getOrDefault(GooTypes.PULSE, 0);
                blaze += ticked.drawn().getOrDefault(GooTypes.BLAZE, 0);
                held = ticked.after();
            }
            assertEquals(TICKS * UPKEEP, pulse);
            assertEquals(TICKS / 2 * UPKEEP, blaze);
        }

        @Test
        void pulseRunningDryEndsTheExtenderAloneAndTheOthersPayEveryTick() {
            HeldEffects.Ticked ticked = holding(gloveExtender(), hearts(KINDLE, GooTypes.BLAZE))
                    .tick(type -> type == GooTypes.PULSE ? 0 : PLENTY, STARTED + 2);
            assertEquals(List.of(gloveExtender()), ticked.ended());
            assertEquals(UPKEEP, ticked.drawn().get(GooTypes.BLAZE));
        }

        @Test
        void aGloveExtenderDoublesEachOtherEffectsTimeLeftUntilItEnds() {
            HeldEffects held = holding(gloveExtender(), hearts(KINDLE, GooTypes.BLAZE));
            assertEquals(2 * PLENTY / UPKEEP, held.ticksLeft(type -> PLENTY).get(GooTypes.BLAZE));
            assertEquals(PLENTY / UPKEEP, held.ticksLeft(type -> PLENTY).get(GooTypes.PULSE));
            HeldEffects ended = held.end(EXTENDER).after();
            assertEquals(PLENTY / UPKEEP, ended.ticksLeft(type -> PLENTY).get(GooTypes.BLAZE));
        }
    }
}
