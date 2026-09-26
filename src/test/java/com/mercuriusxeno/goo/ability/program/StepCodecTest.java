package com.mercuriusxeno.goo.ability.program;

import com.mojang.datafixers.util.Unit;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
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
            Map.entry("set_health", LeafSteps.SET_HEALTH.step(Expr.parse("0.5 * health / max_health").getOrThrow())),
            Map.entry("freeze_ticks",
                    LeafSteps.FREEZE_TICKS.step(Expr.parse("140 * 25 / pow(health, 0.2) / 100").getOrThrow())),
            Map.entry("set_ai", LeafSteps.SET_AI.step(false)),
            Map.entry("set_invulnerable", LeafSteps.SET_INVULNERABLE.step(true)),
            Map.entry("clone_entity", new CloneEntityStep(Expr.parse("100 / pow(max_health, 0.6)").getOrThrow())),
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
            Map.entry("place_block", new PlaceBlockStep(Identifier.parse("goo:glow_crystal"), Map.of(
                    "facing", new StateValue.PlacedFace(),
                    "shape", new StateValue.Named("flat"),
                    "size", new StateValue.Pick(Expr.parse("stacks - 1").getOrThrow(), List.of("tiny", "large"))))),
            Map.entry("progressive_area", new ProgressiveAreaStep(AreaShape.FLAT_CIRCLE, "fortune_smelt_break",
                    "blaze_flame", "generic_explode", Expr.literal(8))),
            Map.entry("field_effect", new FieldEffectStep(Expr.literal(3.75),
                    List.of(EntityFilter.LIVING, EntityFilter.NOT_ITEM, EntityFilter.NOT_SNEAKING),
                    Expr.literal(10), Expr.parse("2 - sprinting").getOrThrow(), Expr.literal(1),
                    Expr.literal(0.5), Expr.literal(6), Expr.literal(13), new FieldTiming(Expr.literal(10), Expr.literal(10)),
                    List.of(new DamageStep(Expr.literal(1), DamageKind.CACTUS, false, Optional.of(Expr.literal(1)))),
                    List.of(new SoundStep(Identifier.parse("minecraft:block.fire.extinguish"), FxAnchor.HOST,
                            SoundKind.BLOCKS, Expr.literal(0.5), Expr.literal(1.2))))),
            Map.entry("phased", new PhasedStep(Expr.parse("1 + 2 * stacks").getOrThrow(), List.of(
                    new StepPhase("expand", Expr.literal(15), List.of(new SoundStep(
                            Identifier.parse("goo:effects.black_hole"), FxAnchor.HOST, SoundKind.BLOCKS,
                            Expr.literal(6), Expr.literal(1))),
                            List.of(new PullStep(Expr.literal(9), Expr.literal(0.15))),
                            List.of(LeafSteps.CONSUME_BLOCKS.step(Expr.literal(3)))),
                    new StepPhase("popping", Expr.literal(0), List.of(LeafSteps.DROP_CONSUMED.step(Unit.INSTANCE)), List.of(),
                            List.of())))),
            Map.entry("pull", new PullStep(Expr.parse("3 * (1 + 2 * stacks)").getOrThrow(), Expr.literal(0.15))),
            Map.entry("consume_blocks", LeafSteps.CONSUME_BLOCKS.step(Expr.parse("1 + 2 * stacks").getOrThrow())),
            Map.entry("drop_consumed", LeafSteps.DROP_CONSUMED.step(Unit.INSTANCE)),
            Map.entry("counter", CounterStep.adding(Identifier.parse("goo:ritual"),
                    Expr.parse("100 / pow(max_health, 0.6)").getOrThrow())),
            Map.entry("branch", new BranchStep(Expr.parse("at_least(goo:ritual, 100)").getOrThrow(),
                    List.of(new DropItemStep(DropItemStep.SPAWN_EGG, Expr.literal(1)), LeafSteps.DISCARD.step(Unit.INSTANCE)),
                    List.of(LeafSteps.SET_AI.step(false)))),
            Map.entry("discard", LeafSteps.DISCARD.step(Unit.INSTANCE)),
            Map.entry("set_baby", LeafSteps.SET_BABY.step(true))
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
                + "\"facing\": \"face\", \"shape\": {\"by\": \"flat\", \"values\": [\"bump\", \"flat\"]},"
                + " \"size\": {\"by\": \"stacks - 1\", \"values\": [\"tiny\", \"small\", \"medium\", \"large\"]}}}";
        PlaceBlockStep step = assertInstanceOf(PlaceBlockStep.class, decode(json).getOrThrow());
        assertEquals(Identifier.parse("goo:glow_crystal"), step.block());
        assertEquals(new StateValue.PlacedFace(), step.state().get("facing"));
        StateValue.Pick shape = assertInstanceOf(StateValue.Pick.class, step.state().get("shape"));
        assertEquals(List.of("bump", "flat"), shape.values());
        assertEquals(Set.of("flat", "stacks"),
                step.expressions().flatMap(expr -> expr.variables().stream()).collect(Collectors.toSet()));
    }

    @Test
    void rockTunnelStepDecodes() {
        String json = "{\"type\": \"progressive_area\", \"shape\": \"tunnel\", \"effect\": \"silk_break\","
                + " \"visuals\": \"rock_dust\", \"audio\": \"stone_break\", \"preview_delay\": 8}";
        ProgressiveAreaStep step = assertInstanceOf(ProgressiveAreaStep.class, decode(json).getOrThrow());
        assertEquals(new ProgressiveAreaStep(AreaShape.TUNNEL, "silk_break", "rock_dust", "stone_break",
                Expr.literal(8)), step);
    }

    @Test
    void progressiveAreaRefusesADelegateNoRegistryHolds() {
        String prefix = "{\"type\": \"progressive_area\", \"shape\": \"sphere\", \"preview_delay\": 8,";
        assertTrue(decode(prefix + " \"effect\": \"melt\", \"visuals\": \"none\", \"audio\": \"none\"}").isError());
        assertTrue(decode(prefix + " \"effect\": \"freeze\", \"visuals\": \"sparks\", \"audio\": \"none\"}").isError());
        assertTrue(decode(prefix + " \"effect\": \"freeze\", \"visuals\": \"none\", \"audio\": \"thunder\"}").isError());
        assertTrue(decode(prefix.replace("sphere", "cone")
                + " \"effect\": \"freeze\", \"visuals\": \"none\", \"audio\": \"none\"}").isError());
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
    void unknownFilterRefuses() {
        assertTrue(decode("{\"type\": \"await_entity\", \"radius\": 3, \"where\": [\"friendly\"]}").isError());
    }

    @Test
    void badExpressionRefuses() {
        assertTrue(decode("{\"type\": \"explode\", \"power\": \"2 +\"}").isError());
    }
}
