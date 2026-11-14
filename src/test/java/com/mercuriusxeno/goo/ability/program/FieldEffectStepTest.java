package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The metal_urchin and crystal_cloud programs, decoded from their JSON,
 * compose the field-effect-with-controller sub-chain. Each strike body's
 * damage acts on the entity itself, which no unit test can build, so the
 * body here swaps its damage for a probe sound at the target and each
 * strike's aim is stubbed per host; the damage itself is proven by the
 * field-effect gametests (decision step-tick-holds-effect). The metal
 * field strikes what is living, alive, no item and not sneaking, and the
 * crystal field what is living, no item, not sneaking and moving; the
 * crystal strike bursts crit at the struck entity (decision
 * razor-shred-bursts-crit); a field effect stands against breaking. On a
 * Mockito marker host whose scan hands over the entities in radius: a
 * metal target spends one charge and is impaled on the strike tick, and
 * spent charges tear down once and end the program; a crystal cloud's
 * eight shreds spend its charges, a sprinting player is shredded twice as
 * often, and the cloud expands, then contracts once its last charge is
 * spent.
 */
class FieldEffectStepTest {

    private static final String METAL_URCHIN = "/data/goo/goo_abilities/metal_urchin.json";
    private static final String CRYSTAL_CLOUD = "/data/goo/goo_abilities/crystal_cloud.json";
    private static final int WALKER_ID = 7;
    private static final int OTHER_WALKER_ID = 9;
    private static final int IDLE_TICKS = 40;
    private static final Identifier OPENING_SOUND = Identifier.parse("goo:test.field_opens");
    private static final Identifier STANDING_SOUND = Identifier.parse("goo:test.field_stands");
    private static final SoundCue OPENING_CUE = new SoundCue(OPENING_SOUND, SoundKind.BLOCKS, 1, 1);
    private static final SoundCue STANDING_CUE = new SoundCue(STANDING_SOUND, SoundKind.BLOCKS, 1, 1);
    private static final double AMBIENT_CHANCE = 0.5;
    /** How far a roll stands either side of the ambient chance. */
    private static final double ROLL_STEP = 0.25;
    private static final Identifier PROBE_SOUND = Identifier.parse("goo:test.strike_landed");
    /** The sound each strike body plays at its target in place of its damage. */
    private static final Step PROBE_STEP = new SoundStep(PROBE_SOUND, FxAnchor.TARGET, SoundKind.HOSTILE,
            Expr.literal(1), Expr.literal(1));
    private static final SoundCue PROBE_CUE = new SoundCue(PROBE_SOUND, SoundKind.HOSTILE, 1, 1);

    /** The entity id each entity host's strike aims at, by host. */
    private final Map<StepHost, Integer> idByHost = new HashMap<>();
    /** The entity host the marker's scan is handing to the strike check, whose entity a new strike aims at. */
    private @Nullable StepHost scanned;
    private MockedStatic<FieldStrike> aims;

    @BeforeEach
    void stubTheAimOfEachStrike() {
        aims = mockStatic(FieldStrike.class);
        aims.when(() -> FieldStrike.aimedAt(any())).thenAnswer(inv ->
                new FieldStrike(idByHost.get(scanned), 1.5f, 1.0f, 0.5f, 0));
        aims.when(() -> FieldStrike.followed(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void releaseTheAimStub() {
        aims.close();
    }

    private static List<Step> program(String resource) {
        String fileName = resource.substring(resource.lastIndexOf('/') + 1);
        return LingerStep.bodyOf(AbilityJson.decode(fileName.substring(0, fileName.length() - ".json".length()))
                .behaviors()).orElseThrow();
    }

    /** The shipped metal field, whose timings the tests below run on rather than copy. */
    private static FieldEffectStep metalField() {
        return fieldEffectOf(program(METAL_URCHIN));
    }

    private static int strikeTick() {
        return metalField().strikeTick().evaluateInt(Variables.NONE);
    }

    private static int strikeTicks() {
        return metalField().strikeTicks().evaluateInt(Variables.NONE);
    }

    private static int cooldown() {
        return metalField().cooldown().evaluateInt(Variables.NONE);
    }

    private static int metalCharges() {
        return metalField().charges().evaluateInt(Variables.NONE);
    }

    private static List<Step> metalProgram() throws IOException {
        return withProbedStrikes(program(METAL_URCHIN));
    }

    /**
     * Swaps each field effect's strike damage for the probe sound, keeping
     * the body's other steps and every timing the JSON names.
     *
     * @param steps the decoded program
     * @return the program with probed strike bodies
     */
    private static List<Step> withProbedStrikes(List<Step> steps) {
        return steps.stream().map(step -> step instanceof FieldEffectStep field
                ? new FieldEffectStep(field.radius(), field.where(), field.cooldown(), field.interval(),
                        field.charges(), field.spendChance(), field.strikeTick(), field.strikeTicks(), field.timing(),
                        probed(field.strike()), field.teardown())
                : step).toList();
    }

    private static List<Step> probed(List<Step> strike) {
        return Stream.concat(strike.stream().filter(step -> !(step instanceof DamageStep)),
                Stream.of(PROBE_STEP)).toList();
    }

    /**
     * A walking living entity in the field's radius, bound as a target host.
     *
     * @param id its entity id
     * @return the target host
     */
    private EntityHost walker(int id) {
        return entityInRadius(id, false);
    }

    /**
     * A living entity in the field's radius, bound as a target host.
     *
     * @param id         its entity id
     * @param sprinting  whether it is a sprinting player
     * @return the target host
     */
    private EntityHost entityInRadius(int id, boolean sprinting) {
        EntityHost target = mock(EntityHost.class);
        when(target.kind()).thenReturn(HostKind.ENTITY);
        when(target.read(anyString())).thenReturn(OptionalDouble.empty());
        when(target.read(HostVariables.SPRINTING)).thenReturn(OptionalDouble.of(sprinting ? 1 : 0));
        idByHost.put(target, id);
        return target;
    }

    /**
     * A marker host keeping a real field-effect state and standing in a
     * world whose scan hands the body each entity in radius.
     *
     * @param inRadius the entities within the trap's radius
     * @return the marker host
     */
    private MarkerHost marker(List<EntityHost> inRadius) {
        MarkerHost host = mock(MarkerHost.class);
        FieldEffectState state = new FieldEffectState();
        when(host.kind()).thenReturn(HostKind.MARKER);
        when(host.fieldEffect()).thenReturn(state);
        when(host.read(anyString())).thenReturn(OptionalDouble.empty());
        doAnswer(inv -> {
            Consumer<TargetHost> body = inv.getArgument(3);
            inRadius.forEach(entity -> {
                scanned = entity;
                body.accept(entity);
            });
            return null;
        }).when(host).forEachEntityWithin(any(), anyDouble(), anySet(), any());
        doAnswer(inv -> {
            int id = inv.getArgument(0);
            Consumer<TargetHost> body = inv.getArgument(1);
            inRadius.stream().filter(entity -> idByHost.get(entity) == id).forEach(body);
            return null;
        }).when(host).forEntity(anyInt(), any());
        return host;
    }

    private static void tick(ProgramBehavior program, MarkerHost host, int ticks) {
        for (int i = 0; i < ticks; i++) {
            program.tick(host);
        }
    }

    private static FieldEffectStep fieldEffectOf(List<Step> steps) {
        return steps.stream().filter(FieldEffectStep.class::isInstance).map(FieldEffectStep.class::cast)
                .findFirst().orElseThrow();
    }

    /**
     * Each tick a strike in flight takes the aim FieldStrike.followed answers for its entity, so the spear stands
     * where the mob stands when the stab lands (decision urchin-rests-bristled-and-spears).
     */
    @Test
    void aStrikeInFlightTakesTheAimFollowedAnswersEachTick() throws IOException {
        Vec3 whereTheMobWalked = new Vec3(9, 9, 9);
        aims.when(() -> FieldStrike.followed(any(), any())).thenAnswer(inv ->
                inv.<FieldStrike>getArgument(0).reaimedAt(whereTheMobWalked));
        MarkerHost host = marker(List.of(walker(WALKER_ID)));
        ProgramBehavior program = ProgramBehavior.forHost(metalProgram(), HostKind.MARKER);

        tick(program, host, 2);

        assertEquals(List.of(new FieldStrike(WALKER_ID, 9f, 9f, 9f, 1)), host.fieldEffect().strikes());
    }
    @Test
    void aTargetInRadiusSpendsOneChargeAndIsImpaledOnTheStrikeTick() throws IOException {
        EntityHost walker = walker(WALKER_ID);
        MarkerHost host = marker(List.of(walker));
        ProgramBehavior program = ProgramBehavior.forHost(metalProgram(), HostKind.MARKER);

        program.tick(host);

        assertEquals(1, host.fieldEffect().chargesSpent());
        verify(walker, never()).playSound(PROBE_CUE);
        tick(program, host, strikeTick());
        verify(walker).playSound(PROBE_CUE);
        verify(walker).spawnParticles(argThat(burst -> "crit".equals(burst.particle().getPath())));
        assertEquals(1, host.fieldEffect().chargesSpent());
        assertTrue(program.isActive());
    }

    /**
     * The program with its field effect's spend chance replaced.
     *
     * @param steps       the program
     * @param spendChance the spend chance to set
     * @return the program with the field effect's spend chance set
     */
    private static List<Step> withSpendChance(List<Step> steps, double spendChance) {
        return steps.stream().map(step -> step instanceof FieldEffectStep field
                ? new FieldEffectStep(field.radius(), field.where(), field.cooldown(), field.interval(),
                        field.charges(), Expr.literal(spendChance), field.strikeTick(), field.strikeTicks(),
                        field.timing(), field.strike(), field.teardown())
                : step).toList();
    }

    /**
     * Drives one strike against a walker under a spend chance and a fixed roll.
     *
     * @param spendChance the field's spend chance
     * @param roll        the fraction every roll answers
     * @return the walker, struck, and the marker it was struck from
     */
    private Map.Entry<EntityHost, MarkerHost> strikeOnceUnder(double spendChance, double roll) throws IOException {
        EntityHost walker = walker(WALKER_ID);
        MarkerHost host = marker(List.of(walker));
        when(host.rollFraction()).thenReturn(roll);
        ProgramBehavior program = ProgramBehavior.forHost(withSpendChance(metalProgram(), spendChance),
                HostKind.MARKER);
        program.tick(host);
        tick(program, host, strikeTick());
        return Map.entry(walker, host);
    }

    // decision metal-spends-charge-by-chance
    @Test
    void aStrikeWhoseRollMissesLandsAndSpendsNoCharge() throws IOException {
        Map.Entry<EntityHost, MarkerHost> struck = strikeOnceUnder(0, 0);
        verify(struck.getKey()).playSound(PROBE_CUE);
        assertEquals(0, struck.getValue().fieldEffect().chargesSpent());
    }

    // decision metal-spends-charge-by-chance
    @Test
    void aFieldNamingNoSpendChanceSpendsOnEveryStrike() {
        FieldEffectStep unnamed = (FieldEffectStep) StepTypes.LIST_CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("[{\"type\": \"field_effect\", \"radius\": 3, \"strike\": []}]"))
                .getOrThrow().getFirst();
        assertEquals(1, unnamed.spendChance().evaluate(Variables.NONE));
    }

    @Test
    void theMetalFieldStrikesWhatIsLivingAliveNoItemAndNotSneaking() {
        assertEquals(List.of(EntityFilter.LIVING, EntityFilter.ALIVE, EntityFilter.NOT_ITEM, EntityFilter.NOT_SNEAKING),
                fieldEffectOf(program(METAL_URCHIN)).where());
    }

    @Test
    void theCrystalFieldStrikesWhatIsLivingNoItemNotSneakingAndMoving() {
        assertEquals(List.of(EntityFilter.LIVING, EntityFilter.NOT_ITEM, EntityFilter.NOT_SNEAKING, EntityFilter.MOVING),
                fieldEffectOf(program(CRYSTAL_CLOUD)).where());
    }

    @Test
    void theCooldownHoldsTheNextStrikeForItsTicks() throws IOException {
        EntityHost first = walker(WALKER_ID);
        EntityHost second = walker(OTHER_WALKER_ID);
        MarkerHost host = marker(List.of(first, second));
        ProgramBehavior program = ProgramBehavior.forHost(metalProgram(), HostKind.MARKER);

        tick(program, host, cooldown());
        assertEquals(1, host.fieldEffect().chargesSpent());

        program.tick(host);
        assertEquals(2, host.fieldEffect().chargesSpent());
    }

    @Test
    void spentChargesTearDownOnceWhenTheLastStrikeRetractsAndEndTheProgram() throws IOException {
        EntityHost walker = walker(WALKER_ID);
        MarkerHost host = marker(List.of(walker));
        ProgramBehavior program = ProgramBehavior.forHost(metalProgram(), HostKind.MARKER);

        tick(program, host, (metalCharges() - 1) * cooldown() + strikeTicks() - 1);
        assertEquals(metalCharges(), host.fieldEffect().chargesSpent());
        assertTrue(program.isActive());
        verify(host, never()).playSound(argThat(cue -> "block.fire.extinguish".equals(cue.sound().getPath())));

        tick(program, host, IDLE_TICKS);

        assertFalse(program.isActive());
        verify(host, times(1)).spawnParticles(argThat(burst -> "poof".equals(burst.particle().getPath())));
        verify(host, times(1)).playSound(argThat(cue -> "block.fire.extinguish".equals(cue.sound().getPath())));
    }

    /**
     * A field runs its expand steps on the tick it opens and its ambient steps on a tick whose roll falls under
     * its ambient chance, so the crystal cloud chimes as it opens and tinkles as it stands
     * (decision razor-keeps-its-look-gated-on-glass). The sounds and the chance are built here, not read from the
     * shipped cloud.
     */
    @Test
    void aFieldSoundsItsExpandAsItOpensAndItsAmbientOnARollUnderItsChance() {
        MarkerHost host = marker(List.of());
        ProgramBehavior program = ProgramBehavior.forHost(withSounds(program(CRYSTAL_CLOUD)), HostKind.MARKER);

        when(host.rollFraction()).thenReturn(AMBIENT_CHANCE + ROLL_STEP);
        program.tick(host);
        when(host.rollFraction()).thenReturn(AMBIENT_CHANCE - ROLL_STEP);
        program.tick(host);

        verify(host, times(1)).playSound(OPENING_CUE);
        verify(host, times(1)).playSound(STANDING_CUE);
    }

    /** Swaps each field effect's expand and ambient steps and chance for the literal ones built here. */
    private static List<Step> withSounds(List<Step> steps) {
        return steps.stream().map(step -> step instanceof FieldEffectStep field
                ? new FieldEffectStep(field.radius(), field.where(), field.cooldown(), field.interval(),
                        field.charges(), field.spendChance(), field.strikeTick(), field.strikeTicks(), field.timing(),
                        field.strike(), field.teardown(), List.of(soundOf(OPENING_SOUND)),
                        List.of(soundOf(STANDING_SOUND)), Expr.literal(AMBIENT_CHANCE))
                : step).toList();
    }

    private static Step soundOf(Identifier sound) {
        return new SoundStep(sound, FxAnchor.HOST, SoundKind.BLOCKS, Expr.literal(1), Expr.literal(1));
    }

    // decision razor-shred-bursts-crit
    @Test
    void theCrystalStrikeBurstsCritAtTheStruckEntity() {
        List<Map.Entry<Identifier, FxAnchor>> bursts = fieldEffectOf(program(CRYSTAL_CLOUD)).strike().stream()
                .filter(ParticlesStep.class::isInstance).map(ParticlesStep.class::cast)
                .map(particles -> Map.entry(particles.particle(), particles.at())).toList();

        assertEquals(List.of(Map.entry(Identifier.withDefaultNamespace("crit"), FxAnchor.TARGET)), bursts);
    }

    @Test
    void aFieldEffectStandsAgainstBreaking() {
        assertTrue(fieldEffectOf(program(METAL_URCHIN)).standsAgainstBreaking());
    }

    @Test
    void theStrikeBodyLoadsAgainstTheEntityHostAndTheFieldEffectRefusesTheEntityHost() throws IOException {
        List<Step> metal = metalProgram();

        assertDoesNotThrow(() -> ProgramBehavior.forHost(metal, HostKind.MARKER));
        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(metal, HostKind.ENTITY));
        assertTrue(refusal.getMessage().contains("field_effect"), refusal.getMessage());
    }

    @Test
    void theIntervalIsReadOnTheSelectedEntitySoAMarkerVariableThereRefusesAtLoad() {
        List<Step> steps = List.of(new FieldEffectStep(Expr.literal(3), List.of(), Expr.literal(0),
                Expr.parse("stacks").getOrThrow(), Expr.literal(1), Expr.literal(0), Expr.literal(1),
                FieldTiming.INSTANT, List.of(new DamageStep(Expr.literal(1), DamageKind.CACTUS)), List.of()));

        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(steps, HostKind.MARKER));

        assertTrue(refusal.getMessage().contains("stacks"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("struck entity"), refusal.getMessage());
    }

    @Test
    void aMarkerOnlyStepInTheStrikeBodyRefusesAtLoadSinceItRunsOnTheStruckEntity() {
        List<Step> steps = List.of(new FieldEffectStep(Expr.literal(3), List.of(), Expr.literal(0),
                Expr.literal(1), Expr.literal(1), Expr.literal(0), Expr.literal(1), FieldTiming.INSTANT,
                List.of(new ExplodeStep(Expr.literal(2), ExplosionMode.NONE),
                        LeafSteps.WAIT.step(Expr.literal(2))),
                List.of()));

        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(steps, HostKind.MARKER));

        assertTrue(refusal.getMessage().contains("wait"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("struck entity"), refusal.getMessage());
    }
}
