package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.HeartOverlayStep;
import com.mercuriusxeno.goo.ability.program.NourishStep;
import com.mercuriusxeno.goo.ability.program.SoundCue;
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

/** Held self + brew effects: each pays its upkeep per tick after its start tick, ends when it cannot pay or is ended, and a heart-changing start ends any other heart-changing effect (decision self-effects-trickle-until-ended). */
class HeldEffectsTest {

    private static final long STARTED = 100L;
    private static final int UPKEEP = 1;
    private static final Identifier KINDLE = Identifier.fromNamespaceAndPath("goo", "blaze_kindle");
    private static final Identifier BARKSKIN = Identifier.fromNamespaceAndPath("goo", "leaf_barkskin");
    private static final Identifier NOURISH = Identifier.fromNamespaceAndPath("goo", "vital_nourish");

    private static HeldEffects.Held hearts(Identifier ability, ResourceKey<GooTypeDefinition> type) {
        return new HeldEffects.Held(ability, type, UPKEEP, Set.of(LaidState.HEART_OVERLAY), STARTED);
    }

    private static HeldEffects.Held nourish() {
        return new HeldEffects.Held(NOURISH, GooTypes.VITAL, UPKEEP, Set.of(LaidState.NOURISH), STARTED);
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
        void aPrepaidEffectPaysNothingAndEndsAtItsExpiry() {
            // brew-runs-the-crawl-prepaid-on-a-shown-clock
            long expiresAt = STARTED + 100;
            HeldEffects.Held brewed = new HeldEffects.Held(KINDLE, GooTypes.BLAZE, UPKEEP,
                    Set.of(LaidState.HEART_OVERLAY), STARTED, expiresAt);
            HeldEffects held = holding(brewed);
            HeldEffects.Ticked before = held.tick(type -> 0, expiresAt - 1);
            assertEquals(Map.of(), before.drawn());
            assertSame(held, before.after());
            HeldEffects.Ticked atEnd = held.tick(type -> 0, expiresAt);
            assertEquals(List.of(brewed), atEnd.ended());
            assertTrue(brewed.prepaid());
            assertFalse(hearts(KINDLE, GooTypes.BLAZE).prepaid());
        }

        @Test
        void effectsSharingATypeDrawInTurnAndTheLastUnpaidEnds() {
            HeldEffects.Held first = new HeldEffects.Held(KINDLE, GooTypes.BLAZE, UPKEEP, Set.of(), STARTED);
            HeldEffects.Held second = new HeldEffects.Held(NOURISH, GooTypes.BLAZE, UPKEEP, Set.of(), STARTED);
            HeldEffects.Ticked ticked = holding(first, second).tick(type -> UPKEEP, STARTED + 1);
            assertEquals(Map.of(GooTypes.BLAZE, UPKEEP), ticked.drawn());
            assertEquals(List.of(second), ticked.ended());
            assertTrue(ticked.after().holds(KINDLE));
        }
    }

    @Nested
    class StartAndEnd {

        @Test
        void aHeartChangingStartEndsTheOtherHeartChangingEffect() {
            HeldEffects.Changed changed = holding(hearts(KINDLE, GooTypes.BLAZE), nourish())
                    .start(hearts(BARKSKIN, GooTypes.LEAF));
            assertEquals(List.of(KINDLE), changed.ended().stream().map(HeldEffects.Held::ability).toList());
            assertTrue(changed.after().holds(BARKSKIN));
            assertTrue(changed.after().holds(NOURISH));
            assertFalse(changed.after().holds(KINDLE));
        }

        @Test
        void aStartChangingNoHeartsEndsNothing() {
            HeldEffects.Changed changed = holding(hearts(KINDLE, GooTypes.BLAZE)).start(nourish());
            assertTrue(changed.ended().isEmpty());
            assertTrue(changed.after().holds(KINDLE));
        }

        @Test
        void endingAHeldEffectNamesIt() {
            HeldEffects.Changed changed = holding(hearts(KINDLE, GooTypes.BLAZE)).end(KINDLE);
            assertEquals(List.of(hearts(KINDLE, GooTypes.BLAZE)), changed.ended());
            assertTrue(changed.after().isEmpty());
        }

        @Test
        void endingAnEffectNotHeldChangesNothing() {
            HeldEffects held = holding(nourish());
            HeldEffects.Changed changed = held.end(KINDLE);
            assertSame(held, changed.after());
            assertTrue(changed.ended().isEmpty());
        }
    }

    /** The effect list shows each glove effect's time left: its type's goo over the upkeep drawn a tick (decision brew-runs-the-crawl-prepaid-on-a-shown-clock). */
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
            HeldEffects.Held second = new HeldEffects.Held(NOURISH, GooTypes.BLAZE, UPKEEP, Set.of(), STARTED);
            assertEquals(Map.of(GooTypes.BLAZE, 1000), holding(first, second).ticksLeft(type -> 2000));
        }

        @Test
        void aPrepaidBrewCountsNoUpkeep() {
            HeldEffects.Held brewed = new HeldEffects.Held(KINDLE, GooTypes.BLAZE, UPKEEP,
                    Set.of(LaidState.HEART_OVERLAY), STARTED, STARTED + 100);
            assertEquals(Map.of(), holding(brewed).ticksLeft(type -> 2000));
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
        void aPrepaidExpiryPlaysTheAbilitysOwnCueOnce() {
            SoundCue own = SoundCue.of(Identifier.withDefaultNamespace("block.fire.extinguish"));
            HeldEffects.Held brewed = new HeldEffects.Held(KINDLE, GooTypes.BLAZE, UPKEEP,
                    Set.of(LaidState.HEART_OVERLAY), STARTED, STARTED + 10, own);
            List<HeldEffects.Held> ended = holding(brewed).tick(type -> 0, STARTED + 10).ended();
            assertEquals(List.of(own.sound()), playedIds(ended));
        }
    }

    @Test
    void laidStateIsReadFromTheProgram() {
        assertEquals(Set.of(LaidState.HEART_OVERLAY), LaidState.laidBy(List.of(new HeartOverlayStep(HeartKind.KINDLE))));
        assertEquals(Set.of(LaidState.NOURISH), LaidState.laidBy(List.of(new NourishStep(Expr.literal(80)))));
        assertEquals(Set.of(), LaidState.laidBy(List.of()));
    }
}
