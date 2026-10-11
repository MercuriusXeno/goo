package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.frost.FrostCurve;
import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.datafixers.util.Unit;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every registered step type round-trips through the dispatch codec, and
 * the codec refuses a type name or a filter it does not know.
 */
class StepCodecTest {

    /**
     * One sample per registered type, keyed by type name; the registry
     * check below fails when a type is registered without a sample here.
     */
    private static final Map<String, Step> SAMPLES = Map.ofEntries(
            Map.entry("wait", LeafSteps.WAIT.step(Expr.parse("4 + stacks").getOrThrow())),
            Map.entry("await_entity", new AwaitEntityStep(SelectionShape.CUBE, Expr.literal(3),
                    List.of(EntityFilter.LIVING, EntityFilter.NOT_ITEM))),
            Map.entry("explode", new ExplodeStep(Expr.parse("2.5 + 1.0 * (stacks - 1)").getOrThrow(),
                    ExplosionMode.NONE)),
            Map.entry("damage", new DamageStep(Expr.parse("health / 2").getOrThrow(), DamageKind.CACTUS)),
            Map.entry("potion", new PotionStep(Identifier.parse("minecraft:levitation"), Expr.literal(100),
                    Expr.parse("1 + stacks").getOrThrow(), false)),
            Map.entry("target", new TargetStep(List.of(EntityFilter.NOT_BOSS),
                    List.of(new DamageStep(Expr.literal(4), DamageKind.FREEZE)))),
            Map.entry("freeze", new FreezeStep(Expr.parse("10 * stacks").getOrThrow(),
                    new FrostCurve(300, 0.005f, 0.5f))),
            Map.entry("nova", new NovaStep(Expr.parse("2 + 6 * charge").getOrThrow(),
                    Expr.parse("4 + 12 * charge").getOrThrow(), 0.05f, 0.4f, new FrostCurve(300, 0.005f, 0.5f, 3f))),
            Map.entry("freeze_blocks", new FreezeBlocksStep(Expr.literal(2))),
            Map.entry("drips", new DripsStep(6, List.of(new FreezeBlocksStep(Expr.literal(2))))),
            Map.entry("wind", new WindStep(true, Optional.of(new WindStep.Tailwind(6, 30)))),
            Map.entry("glacial", new GlacialStep(5)),
            Map.entry("traveling", new TravelingStep(3f, List.of(new FreezeBlocksStep(Expr.literal(2.5), false)))),
            Map.entry("break_blocks", new BreakBlocksStep(TagKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("goo", "foliage")))),
            Map.entry("set_ai", LeafSteps.SET_AI.step(false)),
            Map.entry("set_invulnerable", LeafSteps.SET_INVULNERABLE.step(true)),
            Map.entry("clone_entity", new CloneEntityStep(Expr.parse("100 / pow(max_health, 0.6)").getOrThrow(),
                    GooTypes.VITAL)),
            Map.entry("drop_item", new DropItemStep(Identifier.parse("minecraft:cobblestone"),
                    Expr.parse("1 + random(3)").getOrThrow())),
            Map.entry("ignite", LeafSteps.IGNITE.step(Expr.literal(10))),
            Map.entry("entities", new EntitiesStep(SelectionShape.SPHERE, Expr.literal(2.5),
                    List.of(EntityFilter.LIVING, EntityFilter.NOT_FIRE_IMMUNE),
                    List.of(LeafSteps.IGNITE.step(Expr.literal(5))))),
            Map.entry("particles", new ParticlesStep(Identifier.parse("minecraft:damage_indicator"), FxAnchor.TARGET,
                    Expr.literal(15), Expr.literal(0), Optional.of(Expr.literal(0.5)), Optional.of(Expr.literal(1.5)),
                    Expr.literal(0), Expr.literal(1))),
            Map.entry("sound", new SoundStep(Identifier.parse("minecraft:entity.enderman.teleport"), FxAnchor.TARGET,
                    SoundKind.HOSTILE, Expr.parse("0.55 + 0.08 * stacks").getOrThrow(), Expr.literal(1))),
            Map.entry("teleport", new TeleportStep(TeleportMode.RANDOM_OFFSET, Expr.literal(32))),
            Map.entry("push", new PushStep(Expr.literal(1.5))),
            Map.entry("place_block", new PlaceBlockStep(Identifier.parse("goo:glow_crystal"), Map.of(
                    "facing", new StateValue.PlacedFace(),
                    "shape", new StateValue.Named("bump"),
                    "size", new StateValue.Pick(Expr.parse("stacks - 1").getOrThrow(), List.of("tiny", "large"))))),
            Map.entry("set_state", new SetStateStep(Map.of("lit", new StateValue.Named("true")))),
            Map.entry("linger", new LingerStep(List.of(new ExplodeStep(Expr.literal(2.5), ExplosionMode.TNT)))),
            Map.entry("field_effect", new FieldEffectStep(Expr.literal(3.75),
                    List.of(EntityFilter.LIVING, EntityFilter.NOT_ITEM, EntityFilter.NOT_SNEAKING),
                    Expr.literal(10), Expr.parse("2 - sprinting").getOrThrow(), Expr.literal(2),
                    Expr.literal(6), Expr.literal(13), new FieldTiming(Expr.literal(10), Expr.literal(10)),
                    List.of(new DamageStep(Expr.literal(1), DamageKind.CACTUS, false, Optional.of(Expr.literal(1)))),
                    List.of(new SoundStep(Identifier.parse("minecraft:block.fire.extinguish"), FxAnchor.HOST,
                            SoundKind.BLOCKS, Expr.literal(0.5), Expr.literal(1.2))))),
            Map.entry("phased", new PhasedStep(Expr.parse("1 + 2 * stacks").getOrThrow(), List.of(
                    new StepPhase("expand", Expr.literal(15), List.of(new SoundStep(
                            Identifier.parse("goo:effects.black_hole"), FxAnchor.HOST, SoundKind.BLOCKS,
                            Expr.literal(6), Expr.literal(1))),
                            List.of(new PullStep(Expr.literal(9), Expr.literal(0.15))),
                            List.of(LeafSteps.CONSUME_BLOCKS.step(Expr.literal(3)))),
                    new StepPhase("popping", Expr.literal(0), List.of(LeafSteps.DROP_SPHERE.step(Unit.INSTANCE)), List.of(),
                            List.of())))),
            Map.entry("pull", new PullStep(Expr.parse("3 * (1 + 2 * stacks)").getOrThrow(), Expr.literal(0.15), true)),
            Map.entry("consume_blocks", LeafSteps.CONSUME_BLOCKS.step(Expr.parse("1 + 2 * stacks").getOrThrow())),
            Map.entry("drop_sphere", LeafSteps.DROP_SPHERE.step(Unit.INSTANCE)),
            Map.entry("counter", CounterStep.adding(Identifier.parse("goo:ritual"),
                    Expr.parse("100 / pow(max_health, 0.6)").getOrThrow())),
            Map.entry("branch", new BranchStep(Expr.parse("at_least(goo:ritual, 100)").getOrThrow(),
                    List.of(new DropItemStep(DropItemStep.SPAWN_EGG, Expr.literal(1)), LeafSteps.DISCARD.step(Unit.INSTANCE)),
                    List.of(LeafSteps.SET_AI.step(false)))),
            Map.entry("discard", LeafSteps.DISCARD.step(Unit.INSTANCE)),
            Map.entry("stasis", LeafSteps.STASIS.step(Unit.INSTANCE)),
            Map.entry("rewind_hold", LeafSteps.REWIND_HOLD.step(Unit.INSTANCE)),
            Map.entry("regress", new RegressStep(20)),
            Map.entry("tick_block", new TickBlockStep(4)),
            Map.entry("bank_ticks", new BankTicksStep(1, 40)),
            Map.entry("withdraw_bank", new WithdrawBankStep(50, 20)),
            Map.entry("slow_time", new SlowTimeStep(Expr.literal(5), 10, 200, 0.1)),
            Map.entry("haste", new HasteStep()),
            Map.entry("set_baby", LeafSteps.SET_BABY.step(true)),
            Map.entry("ailment_overlay", new AilmentOverlayStep(AilmentKind.HEX,
                    Expr.parse("20 * 60 / pow(health, 0.4)").getOrThrow())),
            Map.entry("afterimage", new AfterimageStep(GooTypes.HEX, Expr.literal(20))),
            Map.entry("ghost_trail", new GhostTrailStep(GooTypes.ENDER, Expr.literal(30))),
            Map.entry("heart_overlay", new HeartOverlayStep(HeartKind.KINDLE)),
            Map.entry("spore_host", new SporeHostStep(Identifier.parse("goo:shroom_mycosis"), Expr.literal(3),
                    Expr.literal(600))),
            Map.entry("colonize", new ColonizeStep(Expr.literal(3), List.of(LeafSteps.DISCARD.step(Unit.INSTANCE)))),
            Map.entry("floors", new FloorsStep(Expr.literal(2), List.of())),
            Map.entry("shift", new ShiftStep(Expr.literal(16))),
            Map.entry("sight", new SightStep(Expr.literal(3))),
            Map.entry("lux", new LuxStep()),
            Map.entry("wisps", new WispsStep(64, 4000, 1200, 0, true, 0.8)),
            Map.entry("reflector", new ReflectorStep(20)),
            Map.entry("ray", new RayStep(32, 10, List.of(EntityFilter.LIVING), new RayStep.Refraction(16, 1.5, 0.85),
                    List.of(new DamageStep(Expr.parse("(4 + 4 * undead) * share").getOrThrow(), DamageKind.MAGIC)),
                    List.of(new SoundStep(Identifier.parse("minecraft:block.beacon.power_select"), FxAnchor.HOST,
                            SoundKind.PLAYERS, Expr.literal(1), Expr.literal(1))))),
            Map.entry("scry", new ScryStep(1, 96, 20, List.of(EntityFilter.LIVING),
                    List.of(new AilmentOverlayStep(AilmentKind.GLOW, Expr.literal(200))))),
            Map.entry("undead", new UndeadStep(Expr.literal(1))),
            Map.entry("flatten", new FlattenStep(TagKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("goo", "flatten_breakable")))),
            Map.entry("bore", new BoreStep(TagKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("goo", "bore_breakable")), 1)),
            Map.entry("spire", new SpireStep(TagKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("goo", "spire_liftable")))),
            Map.entry("petrify", new PetrifyStep(Expr.literal(2))),
            Map.entry("calcify", new CalcifyStep(Identifier.fromNamespaceAndPath("goo", "calcify"), 30)),
            Map.entry("degrade", new DegradeStep(Identifier.fromNamespaceAndPath("goo", "decay"), 20)),
            Map.entry("degrade_drip", new DegradeDripStep(Identifier.fromNamespaceAndPath("goo", "decay"), 8)),
            Map.entry("petrify_drip", new PetrifyDripStep(Identifier.fromNamespaceAndPath("goo", "calcify"), 8,
                    TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "grows_dripstone")))),
            Map.entry("heal", LeafSteps.HEAL.step(Expr.literal(0.1))),
            Map.entry("court", LeafSteps.COURT.step(Expr.literal(0.25))),
            Map.entry("power_pulse", LeafSteps.POWER_PULSE.step(Unit.INSTANCE)),
            Map.entry("toggle_device", LeafSteps.TOGGLE_DEVICE.step(Unit.INSTANCE)),
            Map.entry("metronome", new MetronomeStep()),
            Map.entry("relay", new RelayStep()),
            Map.entry("extender", new ExtenderStep()),
            Map.entry("stun", LeafSteps.STUN.step(Expr.literal(60))),
            Map.entry("signal_wave", new SignalWaveStep(Expr.literal(8), Expr.literal(40))),
            Map.entry("pulser_toggle", new PulserToggleStep(Expr.literal(4))),
            Map.entry("nourish", new NourishStep(Expr.literal(80))),
            Map.entry("reserve_drain", new ReserveDrainStep(Expr.literal(0.05), Expr.literal(0.5), Expr.literal(10),
                    Expr.literal(0.5))),
            Map.entry("zone", new ZoneStep(Expr.literal(6), Expr.literal(32))),
            Map.entry("shifter", new ShifterStep(Expr.literal(8))),
            Map.entry("convoke", new ConvokeStep(Expr.literal(20))),
            Map.entry("end", new EndStep(Expr.literal(1200))),
            Map.entry("bloom", new BloomStep(Expr.literal(4), Expr.literal(8), Expr.literal(64))),
            Map.entry("tick_plants", new TickPlantsStep()),
            Map.entry("tend_fungi", new TendFungiStep()),
            Map.entry("pulse_plants", new PulsePlantsStep(4, 1, 1.5f, 6)),
            Map.entry("verdant", new VerdantStep(Identifier.parse("goo:greening"), 5, 40, 8, 4, 1.5f, 6)),
            Map.entry("toxin",new ToxinStep(Identifier.parse("goo:bio_toxin"), Expr.literal(0.06),
                    Expr.literal(100), 2)),
            Map.entry("reap", new ReapStep(Expr.literal(4), Expr.literal(10))),
            Map.entry("hasten_regrow", new HastenRegrowStep(Expr.literal(2))),
            Map.entry("root", new RootStep(Expr.literal(60), Expr.literal(4), Expr.literal(1), Expr.literal(1.5))),
            Map.entry("hit_or_miss", new HitOrMissStep(
                    List.of(new RootStep(Expr.literal(60), Expr.literal(4), Expr.literal(1), Expr.literal(1.5))),
                    List.of(new LingerStep(List.of(LeafSteps.DISCARD.step(Unit.INSTANCE)))))),
            Map.entry("charm", new CharmStep()),
            Map.entry("enchant_book", LeafSteps.ENCHANT_BOOK.step(Unit.INSTANCE)),
            Map.entry("fuse_books", new FuseBooksStep(Optional.of(new SoundCue(
                    Identifier.withDefaultNamespace("block.fire.extinguish"), SoundKind.PLAYERS, 0.4f, 1.6f)))),
            Map.entry("spawn_random", new SpawnRandomStep(GooTypes.HEX, 20,
                    List.of(new AilmentOverlayStep(AilmentKind.HEX, Expr.literal(60))), Expr.literal(5))),
            Map.entry("lifetap", new LifetapStep(Expr.literal(0.3))),
            Map.entry("tome", new TomeStep(TomeKind.FUSE)),
            Map.entry("float", new FloatStep(Expr.literal(100), Expr.literal(1))),
            Map.entry("lift", new LiftStep(Expr.literal(0.3), Expr.literal(0.2), Expr.literal(32))),
            Map.entry("updraft", new UpdraftStep(Expr.literal(1), Expr.literal(8), Expr.literal(0.4), Expr.literal(200))),
            Map.entry("airborn", new AirbornStep(Expr.literal(0.35), Expr.literal(0.15), Expr.literal(0.4),
                    Expr.literal(0.5), Expr.literal(1.5), Expr.literal(1.2), Expr.literal(0.05))),
            Map.entry("leech", new LeechStep(Expr.literal(0.5),
                    List.of(new DamageStep(Expr.literal(2), DamageKind.ATTACK))))
    );

    private static Step roundTrip(Step step) {
        JsonElement json = StepTypes.CODEC.encodeStart(JsonOps.INSTANCE, step).getOrThrow();
        return StepTypes.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static DataResult<Step> decode(String json) {
        return StepTypes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @Test
    void everyRegisteredTypeHasASample() {
        assertEquals(SAMPLES.keySet(), StepTypes.all().stream().map(StepType::name).collect(Collectors.toSet()));
    }

    @Test
    void everyRegisteredTypeRoundTrips() {
        for (Step sample : SAMPLES.values()) {
            assertEquals(sample, roundTrip(sample), sample.type().name());
        }
    }

    @Test
    void typeFieldNamesTheStep() {
        JsonElement json = StepTypes.CODEC.encodeStart(JsonOps.INSTANCE, SAMPLES.get("explode")).getOrThrow();
        assertEquals("explode", json.getAsJsonObject().get("type").getAsString());
    }

    @Test
    void optionalParamsDefault() {
        Step step = decode("{\"type\": \"await_entity\", \"radius\": 3}").getOrThrow();
        AwaitEntityStep await = assertInstanceOf(AwaitEntityStep.class, step);
        assertEquals(SelectionShape.SPHERE, await.shape());
        assertEquals(List.of(), await.where());
        ExplodeStep explode = assertInstanceOf(ExplodeStep.class,
                decode("{\"type\": \"explode\", \"power\": 2}").getOrThrow());
        assertEquals(ExplosionMode.TNT, explode.mode());
        DamageStep damage = assertInstanceOf(DamageStep.class,
                decode("{\"type\": \"damage\", \"amount\": 8}").getOrThrow());
        assertEquals(DamageKind.MAGIC, damage.source());
        PotionStep potion = assertInstanceOf(PotionStep.class,
                decode("{\"type\": \"potion\", \"effect\": \"minecraft:poison\", \"duration\": 60}").getOrThrow());
        assertEquals(0, potion.amplifier().evaluate(Variables.NONE));
        assertTrue(potion.visible());
        ParticlesStep particles = assertInstanceOf(ParticlesStep.class,
                decode("{\"type\": \"particles\", \"id\": \"minecraft:crit\", \"spread\": 0.5}").getOrThrow());
        assertEquals(FxAnchor.HOST, particles.at());
        assertEquals(1, particles.count().evaluate(Variables.NONE));
        assertEquals(Optional.empty(), particles.spreadAlong());
        assertEquals(0, particles.lift().evaluate(Variables.NONE));
        SoundStep sound = assertInstanceOf(SoundStep.class,
                decode("{\"type\": \"sound\", \"id\": \"minecraft:block.glass.break\"}").getOrThrow());
        assertEquals(FxAnchor.HOST, sound.at());
        assertEquals(SoundKind.BLOCKS, sound.source());
        assertEquals(1, sound.volume().evaluate(Variables.NONE));
        assertEquals(1, sound.pitch().evaluate(Variables.NONE));
    }

    @Test
    void heldEffectStepsDecodeWithNoDuration() {
        // self-effects-trickle-until-ended: the brew alone names a duration
        assertEquals(new HeartOverlayStep(HeartKind.KINDLE),
                decode("{\"type\": \"heart_overlay\", \"kind\": \"kindle\"}").getOrThrow());
        assertEquals(new NourishStep(Expr.literal(80)),
                decode("{\"type\": \"nourish\", \"interval\": 80}").getOrThrow());
    }

    @Test
    void javelinStepsDecode() {
        Step step = decode("{\"type\": \"damage\", \"amount\": 8.0, \"source\": \"magic\"}").getOrThrow();
        DamageStep damage = assertInstanceOf(DamageStep.class, step);
        assertEquals(8.0, damage.amount().evaluate(Variables.NONE));
        assertEquals(DamageKind.MAGIC, damage.source());
    }

    @Test
    void proximityMineStepsDecode() {
        String json = "[{\"type\": \"await_entity\", \"shape\": \"sphere\", \"radius\": 3.0, \"where\": [\"living\"]},"
                + " {\"type\": \"explode\", \"power\": \"2.5 + 1.0 * (stacks - 1)\"}]";
        List<Step> steps = StepTypes.LIST_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        assertEquals(2, steps.size());
        assertInstanceOf(AwaitEntityStep.class, steps.get(0));
        assertInstanceOf(ExplodeStep.class, steps.get(1));
    }

    // decision push-direction-enum-deleted
    @Test
    void pushDecodesFromStrengthAlone() {
        assertEquals(new PushStep(Expr.literal(1.5)), decode("{\"type\": \"push\", \"strength\": 1.5}").getOrThrow());
    }

    @Test
    void targetStepDecodesItsChildren() {
        String json = "{\"type\": \"target\", \"where\": [\"not_boss\"], \"steps\": ["
                + "{\"type\": \"potion\", \"effect\": \"minecraft:poison\", \"duration\": 200}]}";
        TargetStep target = assertInstanceOf(TargetStep.class, decode(json).getOrThrow());
        assertEquals(List.of(EntityFilter.NOT_BOSS), target.where());
        assertInstanceOf(PotionStep.class, target.steps().get(0));
    }

    @Test
    void glowCrystalStepDecodes() {
        String json = "{\"type\": \"place_block\", \"block\": \"goo:glow_crystal\", \"state\": {"
                + "\"facing\": \"face\", \"shape\": \"bump\","
                + " \"size\": {\"by\": \"stacks - 1\", \"values\": [\"tiny\", \"small\", \"medium\", \"large\"]}}}";
        PlaceBlockStep step = assertInstanceOf(PlaceBlockStep.class, decode(json).getOrThrow());
        assertEquals(Identifier.parse("goo:glow_crystal"), step.block());
        assertEquals(new StateValue.PlacedFace(), step.state().get("facing"));
        assertEquals(new StateValue.Named("bump"), step.state().get("shape"));
        assertEquals(Set.of("stacks"),
                step.expressions().flatMap(expr -> expr.variables().stream()).collect(Collectors.toSet()));
    }

    // decision splat-runs-the-program-no-fuse
    @Test
    void progressiveAreaIsNoLongerAStepType() {
        assertTrue(StepTypes.all().stream().noneMatch(type -> type.name().equals("progressive_area")));
        String json = "{\"type\": \"progressive_area\", \"shape\": \"tunnel\", \"effect\": \"silk_break\","
                + " \"visuals\": \"rock_dust\", \"audio\": \"stone_break\", \"preview_delay\": 8}";
        assertTrue(decode(json).isError());
    }

    @Test
    void emptyPickRefuses() {
        assertTrue(decode("{\"type\": \"place_block\", \"block\": \"goo:glow_crystal\","
                + " \"state\": {\"size\": {\"by\": 1, \"values\": []}}}").isError());
    }

    @Test
    void phaseOptionalFieldsDefaultAndAPhasedStepNeedsAPhase() {
        PhasedStep phased = assertInstanceOf(PhasedStep.class,
                decode("{\"type\": \"phased\", \"phases\": [{\"name\": \"popping\"}]}").getOrThrow());
        assertEquals(0, phased.radius().evaluate(Variables.NONE));
        assertEquals(new StepPhase("popping", Expr.literal(0), List.of(), List.of(), List.of()),
                phased.phases().get(0));
        assertTrue(decode("{\"type\": \"phased\", \"phases\": []}").isError());
    }

    @Test
    void unknownTypeRefuses() {
        assertTrue(decode("{\"type\": \"teleport_everyone\"}").isError());
    }

    @Test
    void ailmentOverlayRefusesAKindNoAilmentCarries() {
        assertTrue(decode("{\"type\": \"ailment_overlay\", \"kind\": \"sunburn\", \"duration\": 60}").isError());
    }

    @Test
    void afterimageSilhouettesLiveTwelveTicksUnlessTheJsonSaysOtherwise() {
        AfterimageStep afterimage = assertInstanceOf(AfterimageStep.class,
                decode("{\"type\": \"afterimage\", \"goo\": \"ender\"}").getOrThrow());

        assertEquals(new AfterimageStep(GooTypes.ENDER, Expr.literal(12)), afterimage);
        assertTrue(decode("{\"type\": \"afterimage\"}").isError(), "an afterimage names no goo type");
    }

    @Test
    void unknownFilterRefuses() {
        assertTrue(decode("{\"type\": \"await_entity\", \"radius\": 3, \"where\": [\"friendly\"]}").isError());
    }

    @Test
    void badExpressionRefuses() {
        assertTrue(decode("{\"type\": \"explode\", \"power\": \"2 +\"}").isError());
    }
}
