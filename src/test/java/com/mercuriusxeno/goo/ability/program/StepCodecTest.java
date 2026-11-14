package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.frost.FrostCurve;
import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.datafixers.util.Unit;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
            Map.entry("watch", new WatchStep(Expr.literal(10), Expr.literal(3),
                    List.of(EntityFilter.LIVING, EntityFilter.MOB))),
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
            Map.entry("cool_lava", new CoolLavaStep()),
            Map.entry("coat", new CoatStep(20)),
            Map.entry("slick", new SlickStep(20, 1.03, 1.08, 40, 1, 0.3)),
            Map.entry("vacuum", new VacuumStep(1.25, 5)),
            Map.entry("vacuum_pull", new VacuumPullStep(1, 0.2, 1.5)),
            Map.entry("wobble", new WobbleStep(Expr.literal(600))),
            Map.entry("detect_ore", new DetectOreStep(TagKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("goo", "gem_ores")), GooTypes.CRYSTAL, 64, 100)),
            Map.entry("wind", new WindStep(true, Optional.of(new WindStep.Tailwind(6, 30)))),
            Map.entry("miasma_vortex", new MiasmaVortexStep()),
            Map.entry("glacial", new GlacialStep(5)),
            Map.entry("traveling", new TravelingStep(3f, List.of(new FreezeBlocksStep(Expr.literal(2.5), false)))),
            Map.entry("break_blocks", new BreakBlocksStep(TagKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("goo", "foliage")))),
            Map.entry("extinguish", new ExtinguishStep()),
            Map.entry("set_ai", LeafSteps.SET_AI.step(false)),
            Map.entry("set_invulnerable", LeafSteps.SET_INVULNERABLE.step(true)),
            Map.entry("spawn_slime", new SpawnSlimeStep(GooTypes.WEIRD, 1)),
            Map.entry("drop_item", new DropItemStep(Identifier.parse("minecraft:cobblestone"),
                    Expr.parse("1 + random(3)").getOrThrow())),
            Map.entry("ignite", LeafSteps.IGNITE.step(Expr.literal(10))),
            Map.entry("light_fire", LeafSteps.LIGHT_FIRE.step(Unit.INSTANCE)),
            Map.entry("detonate_explosive", LeafSteps.DETONATE_EXPLOSIVE.step(Expr.literal(2.5))),
            Map.entry("melt", new MeltStep(TagKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("goo", "meltable")))),
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
            Map.entry("counter", CounterStep.adding(Identifier.parse("goo:ritual"),
                    Expr.parse("100 / pow(max_health, 0.6)").getOrThrow())),
            Map.entry("branch", new BranchStep(Expr.parse("at_least(goo:ritual, 100)").getOrThrow(),
                    List.of(new DropItemStep(DropItemStep.SPAWN_EGG, Expr.literal(1)), LeafSteps.DISCARD.step(Unit.INSTANCE)),
                    List.of(LeafSteps.SET_AI.step(false)))),
            Map.entry("discard", LeafSteps.DISCARD.step(Unit.INSTANCE)),
            Map.entry("rewind_hold", LeafSteps.REWIND_HOLD.step(Unit.INSTANCE)),
            Map.entry("regress", new RegressStep(20)),
            Map.entry("ancient", new AncientStep()),
            Map.entry("grab", new GrabStep(8, 3, 1.5, 1, 10)),
            Map.entry("gouge", new GougeStep(6, 1.5, 2.5, 3.5, 1.5, 1000)),
            Map.entry("telekinesis", new TelekinesisStep(8)),
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
            Map.entry("eldritch", new EldritchStep()),
            Map.entry("wisps", new WispsStep(64, 4000, 1200, 0, true, 0.8, 0.25)),
            Map.entry("reflector", new ReflectorStep(20)),
            Map.entry("ray", new RayStep(32, 10, List.of(EntityFilter.LIVING), new RayStep.Refraction(16, 1.5, 0.85),
                    List.of(new DamageStep(Expr.parse("(4 + 4 * undead) * share").getOrThrow(), DamageKind.MAGIC)),
                    List.of(new SoundStep(Identifier.parse("minecraft:block.beacon.power_select"), FxAnchor.HOST,
                            SoundKind.PLAYERS, Expr.literal(1), Expr.literal(1))))),
            Map.entry("scry", new ScryStep(GooTypes.ASTRAL, 96, List.of(EntityFilter.LIVING),
                    List.of(new AilmentOverlayStep(AilmentKind.ASTRAL, Expr.literal(200))))),
            Map.entry("undead", new UndeadStep(Expr.literal(1))),
            Map.entry("level", new LevelStep(TagKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("goo", "level_breakable")), new BreakPace(5, 1.5))),
            Map.entry("bore", new BoreStep(TagKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("goo", "bore_breakable")), new BreakPace(5, 1.5))),
            Map.entry("spire", new SpireStep()),
            Map.entry("petrify", new PetrifyStep(Expr.literal(2))),
            Map.entry("drain_petrify", new DrainPetrifyStep(Expr.literal(2.5))),
            Map.entry("wake_statue", new WakeStatueStep()),
            Map.entry("calcify", new CalcifyStep(Identifier.fromNamespaceAndPath("goo", "calcify"), 30)),
            Map.entry("degrade", new DegradeStep(Identifier.fromNamespaceAndPath("goo", "decay"), 20)),
            Map.entry("flames", new FlamesStep(Expr.literal(1), 2, 5, 40,
                    TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "flames_burns")))),
            Map.entry("heater", new HeaterStep(6, Expr.literal(1), 2, 5, 20)),
            Map.entry("heal", LeafSteps.HEAL.step(Expr.literal(0.1))),
            Map.entry("court", LeafSteps.COURT.step(Expr.literal(0.25))),
            Map.entry("power_pulse", LeafSteps.POWER_PULSE.step(Unit.INSTANCE)),
            Map.entry("age", LeafSteps.AGE.step(Unit.INSTANCE)),
            Map.entry("metronome", new MetronomeStep()),
            Map.entry("relay", new RelayStep()),
            Map.entry("extender", new ExtenderStep()),
            Map.entry("stun", LeafSteps.STUN.step(Expr.literal(60))),
            Map.entry("signal_wave", new SignalWaveStep(Expr.literal(8), Expr.literal(40))),
            Map.entry("pulser_toggle", new PulserToggleStep(Expr.literal(4))),
            Map.entry("gluttony", new GluttonyStep(Expr.literal(20), Expr.literal(20))),
            Map.entry("reserve_drain", new ReserveDrainStep(Expr.literal(0.05), Expr.literal(0.5), Expr.literal(10),
                    Expr.literal(0.5))),
            Map.entry("feed", new FeedStep(Expr.literal(12), Expr.literal(100))),
            Map.entry("zone", new ZoneStep(Expr.literal(6), Expr.literal(32))),
            Map.entry("shifter", new ShifterStep(Expr.literal(8))),
            Map.entry("shriek", new ShriekStep(Expr.literal(16), Expr.literal(600))),
            Map.entry("sculker", new SculkerStep()),
            Map.entry("vibrate", new VibrateStep(Identifier.withDefaultNamespace("block_activate"))),
            Map.entry("scan_visual", new ScanVisualStep("sound_wave",
                    java.util.Optional.of(Identifier.withDefaultNamespace("block.comparator.click")))),
            Map.entry("end", new EndStep(Expr.literal(1200))),
            Map.entry("hole_open", new HoleOpenStep(Expr.literal(240), Expr.literal(120))),
            Map.entry("outsider", new OutsiderStep(Expr.literal(20), Expr.literal(6), Expr.literal(40),
                    Expr.literal(10), Expr.literal(1))),
            Map.entry("meteor", new MeteorStep(Expr.literal(4), 40)),
            Map.entry("bloom", new BloomStep(Expr.literal(4), Expr.literal(8), Expr.literal(64))),
            Map.entry("tick_plants", new TickPlantsStep()),
            Map.entry("tend_fungi", new TendFungiStep()),
            Map.entry("verdant", new VerdantStep(Identifier.parse("goo:greening"), 5, 40, 8, 4, 1.5f, 6)),
            Map.entry("toxin",new ToxinStep(Identifier.parse("goo:bio_toxin"), Expr.literal(0.06),
                    Expr.literal(100), 2)),
            Map.entry("slash", new SlashStep(Expr.literal(2), Expr.literal(3), Expr.literal(1), Expr.literal(2),
                    Expr.literal(5.3), Expr.literal(5.8), Expr.literal(0.5), Expr.literal(1.1), Expr.literal(3.9),
                    Expr.literal(2.6), Expr.literal(0.45), Expr.literal(0.2), Expr.literal(10), Expr.literal(5),
                    Expr.literal(6), Expr.literal(0.5))),
            Map.entry("hasten_regrow", new HastenRegrowStep(Expr.literal(2))),
            Map.entry("root", new RootStep(Expr.literal(60), Expr.literal(4), Expr.literal(1), Expr.literal(1.5))),
            Map.entry("hit_or_miss", new HitOrMissStep(
                    List.of(new RootStep(Expr.literal(60), Expr.literal(4), Expr.literal(1), Expr.literal(1.5))),
                    List.of(new LingerStep(List.of(LeafSteps.DISCARD.step(Unit.INSTANCE)))))),
            Map.entry("charm", new CharmStep()),
            Map.entry("phase", new PhaseStep(Optional.of(Expr.literal(600)))),
            Map.entry("quantum_portal", new PortalStep()),
            Map.entry("wormhole", new WormholeStep(Expr.literal(16), Expr.literal(200))),
            Map.entry("enchant_book", LeafSteps.ENCHANT_BOOK.step(Unit.INSTANCE)),
            Map.entry("fuse_books", new FuseBooksStep(Optional.of(new SoundCue(
                    Identifier.withDefaultNamespace("block.fire.extinguish"), SoundKind.PLAYERS, 0.4f, 1.6f)))),
            Map.entry("leader", new LeaderStep(12, 4)),
            Map.entry("rally", new RallyStep(Expr.literal(6), Expr.literal(1.5), Expr.literal(0.5))),
            Map.entry("transmute_slime", new SlimeTransmuteStep(GooTypes.ZOO, true, 20,
                    List.of(new AilmentOverlayStep(AilmentKind.HEX, Expr.literal(60))))),
            Map.entry("lifetap", new LifetapStep(Expr.literal(0.3))),
            Map.entry("tome", new TomeStep(TomeKind.FUSE)),
            Map.entry("float", new FloatStep(Expr.literal(100), Expr.literal(1))),
            Map.entry("lift", new LiftStep(Expr.literal(0.3), Expr.literal(0.2), Expr.literal(32))),
            Map.entry("updraft", new UpdraftStep(Expr.literal(1), Expr.literal(8), Expr.literal(0.4), Expr.literal(200))),
            Map.entry("airborn", new AirbornStep(Expr.literal(0.6), Expr.literal(0.4), Expr.literal(0.2),
                    Expr.literal(0.4), Expr.literal(0.5), Expr.literal(1.2), Expr.literal(0.05))),
            Map.entry("pneuma", new PneumaStep(Expr.literal(30), Expr.literal(300), Expr.literal(60))),
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
        // gluttony-overheals-and-overhungers: the step names the caps alone, no clock
        assertEquals(new GluttonyStep(Expr.literal(20), Expr.literal(16)),
                decode("{\"type\": \"gluttony\", \"max_overheal\": 20, \"max_overhunger\": 16}").getOrThrow());
    }

    @Test
    void dartStepsDecode() {
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

    @Test
    void emptyPickRefuses() {
        assertTrue(decode("{\"type\": \"place_block\", \"block\": \"goo:glow_crystal\","
                + " \"state\": {\"size\": {\"by\": 1, \"values\": []}}}").isError());
    }

    // frost-reach-extinguishes-fire-and-burning-mobs
    @Test
    void rockBoresBoreStepCarriesBothPaceValues() {
        // decision bore-and-flatten-json-carry-two-pace-values
        BoreStep bore = AbilityJson.decode("rock_bore").behaviors().stream().filter(BoreStep.class::isInstance)
                .map(BoreStep.class::cast).findFirst().orElseThrow();
        assertNotNull(bore.pace());
    }

    @Test
    void rockLevelStepCarriesBothPaceValues() {
        // decision bore-and-flatten-json-carry-two-pace-values
        LevelStep levelStep = AbilityJson.decode("rock_level").behaviors().stream()
                .filter(LevelStep.class::isInstance).map(LevelStep.class::cast).findFirst().orElseThrow();
        assertNotNull(levelStep.pace());
    }

    @Test
    void extinguishDecodesFromItsTypeAlone() {
        assertEquals(new ExtinguishStep(), decode("{\"type\": \"extinguish\"}").getOrThrow());
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
