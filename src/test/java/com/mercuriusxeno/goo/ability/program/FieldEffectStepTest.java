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
 * The metal_spikes and crystal_cloud programs, decoded from their JSON,
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

    private static final String METAL_SPIKES = "/data/goo/goo_abilities/metal_spikes.json";
    private static final String CRYSTAL_CLOUD = "/data/goo/goo_abilities/crystal_cloud.json";
    private static final int WALKER_ID = 7;
    private static final int OTHER_WALKER_ID = 9;
    private static final int STRIKE_TICK = 6;
    private static final int STRIKE_TICKS = 12;
    /** The ticks after a strike lands that its spear's retract shinks, a tick into the retract. */
    private static final int RETRACT_FRAME = 1;
    private static final int COOLDOWN = 10;
    private static final int IDLE_TICKS = 40;
    private static final int METAL_CHARGES = 4;
    private static final int CRYSTAL_CHARGES = 8;
    private static final double METAL_SPEND_CHANCE = 0.25;
    /** A walker is shredded every second tick, so a throw's eight charges last sixteen ticks. */
    private static final int WALKING_TICKS_FOR_A_THROW = 16;
    /** Ticks a sprinter and a walker share the cloud while its charges outlast them both. */
    private static final int SPRINT_WINDOW = 4;
    /** The tick after the cloud opens that its second chime rings. */
    private static final int EXPAND_CHIME_FRAME = 4;
    /** Rolls either side of the cloud's ambient chance. */
    private static final double ROLL_MISSES_THE_TINKLE = 0.5;
    private static final double ROLL_PASSES_THE_TINKLE = 0.05;
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

    private static List<Step> metalProgram() throws IOException {
        return withProbedStrikes(program(METAL_SPIKES));
    }

    private static List<Step> crystalProgram() throws IOException {
        return withProbedStrikes(program(CRYSTAL_CLOUD));
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
        tick(program, host, STRIKE_TICK);
        verify(walker).playSound(PROBE_CUE);
        verify(walker).spawnParticles(argThat(burst -> "crit".equals(burst.particle().getPath())));
        assertEquals(1, host.fieldEffect().chargesSpent());
        assertTrue(program.isActive());
    }

    /**
     * A strike's framed sound plays at the struck mob on its frame, held by the
     * trap's program past the strike body's one tick
     * (decision urchin-spikes-shink-out-and-shink-back).
     */
    @Test
    void aStrikesFramedShinkPlaysAtTheStruckMobOnItsFrame() throws IOException {
        EntityHost walker = walker(WALKER_ID);
        MarkerHost host = marker(List.of(walker));
        ProgramBehavior program = ProgramBehavior.forHost(metalProgram(), HostKind.MARKER);
        SoundCue out = new SoundCue(Identifier.parse("minecraft:item.axe.scrape"), SoundKind.BLOCKS, 0.8f, 1.5f);
        SoundCue back = new SoundCue(Identifier.parse("minecraft:item.axe.scrape"), SoundKind.BLOCKS, 0.8f, 1.0f);

        program.tick(host);
        tick(program, host, STRIKE_TICK);
        verify(walker, times(1)).playSound(out);
        verify(walker, never()).playSound(back);

        tick(program, host, RETRACT_FRAME - 1);
        verify(walker, never()).playSound(back);

        program.tick(host);
        verify(walker, times(1)).playSound(back);
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
        tick(program, host, STRIKE_TICK);
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

    // decision metal-spends-charge-by-chance
    @Test
    void metalSpendsOnAQuarterRollAndCrystalOnEveryStrike() {
        FieldEffectStep metal = fieldEffectOf(program(METAL_SPIKES));
        FieldEffectStep crystal = fieldEffectOf(program(CRYSTAL_CLOUD));
        assertEquals(METAL_SPEND_CHANCE, metal.spendChance().evaluate(Variables.NONE));
        assertEquals(1, crystal.spendChance().evaluate(Variables.NONE));
    }

    // decision splat-runs-the-program-no-fuse
    @Test
    void theMetalProgramBuysFourChargesAThrow() {
        assertEquals(METAL_CHARGES, fieldEffectOf(program(METAL_SPIKES)).charges().evaluate(Variables.NONE));
    }

    // decision splat-runs-the-program-no-fuse
    @Test
    void theCrystalProgramBuysEightShredsAThrow() {
        assertEquals(CRYSTAL_CHARGES, fieldEffectOf(program(CRYSTAL_CLOUD)).charges().evaluate(Variables.NONE));
    }

    @Test
    void theMetalFieldStrikesWhatIsLivingAliveNoItemAndNotSneaking() {
        assertEquals(List.of(EntityFilter.LIVING, EntityFilter.ALIVE, EntityFilter.NOT_ITEM, EntityFilter.NOT_SNEAKING),
                fieldEffectOf(program(METAL_SPIKES)).where());
    }

    @Test
    void theCrystalFieldStrikesWhatIsLivingNoItemNotSneakingAndMoving() {
        assertEquals(List.of(EntityFilter.LIVING, EntityFilter.NOT_ITEM, EntityFilter.NOT_SNEAKING, EntityFilter.MOVING),
                fieldEffectOf(program(CRYSTAL_CLOUD)).where());
    }

    @Test
    void theCooldownHoldsTheNextStrikeTenTicks() throws IOException {
        EntityHost first = walker(WALKER_ID);
        EntityHost second = walker(OTHER_WALKER_ID);
        MarkerHost host = marker(List.of(first, second));
        ProgramBehavior program = ProgramBehavior.forHost(metalProgram(), HostKind.MARKER);

        tick(program, host, COOLDOWN);
        assertEquals(1, host.fieldEffect().chargesSpent());

        program.tick(host);
        assertEquals(2, host.fieldEffect().chargesSpent());
    }

    @Test
    void spentChargesTearDownOnceWhenTheLastStrikeRetractsAndEndTheProgram() throws IOException {
        EntityHost walker = walker(WALKER_ID);
        MarkerHost host = marker(List.of(walker));
        ProgramBehavior program = ProgramBehavior.forHost(metalProgram(), HostKind.MARKER);

        tick(program, host, (METAL_CHARGES - 1) * COOLDOWN + STRIKE_TICKS - 1);
        assertEquals(METAL_CHARGES, host.fieldEffect().chargesSpent());
        assertTrue(program.isActive());
        verify(host, never()).playSound(argThat(cue -> "block.fire.extinguish".equals(cue.sound().getPath())));

        tick(program, host, IDLE_TICKS);

        assertFalse(program.isActive());
        verify(host, times(1)).spawnParticles(argThat(burst -> "poof".equals(burst.particle().getPath())));
        verify(host, times(1)).playSound(argThat(cue -> "block.fire.extinguish".equals(cue.sound().getPath())));
    }

    // razor-keeps-its-look-gated-on-glass: the cloud sounds as it opens and tinkles as it stands
    @Test
    void theCrystalFieldChimesAsItOpensAndTinklesOnARollWhileItStands() {
        MarkerHost host = marker(List.of());
        when(host.rollFraction()).thenReturn(ROLL_MISSES_THE_TINKLE);
        ProgramBehavior program = ProgramBehavior.forHost(program(CRYSTAL_CLOUD), HostKind.MARKER);

        program.tick(host);
        verify(host, times(1)).playSound(argThat(cue -> "block.amethyst_cluster.place".equals(cue.sound().getPath())));
        tick(program, host, EXPAND_CHIME_FRAME + 1);
        verify(host, times(1)).playSound(argThat(cue -> isChime(cue) && cue.volume() == 1f));

        when(host.rollFraction()).thenReturn(ROLL_PASSES_THE_TINKLE);
        program.tick(host);
        verify(host, times(1)).playSound(argThat(cue -> isChime(cue) && cue.volume() < 1f));
    }

    private static boolean isChime(SoundCue cue) {
        return "block.amethyst_block.chime".equals(cue.sound().getPath());
    }

    @Test
    void eightShredsOnAMovingEntitySpendTheThrowsCharges() throws IOException {
        EntityHost walker = walker(WALKER_ID);
        MarkerHost host = marker(List.of(walker));
        ProgramBehavior program = ProgramBehavior.forHost(crystalProgram(), HostKind.MARKER);

        tick(program, host, WALKING_TICKS_FOR_A_THROW - 1);
        verify(walker, times(CRYSTAL_CHARGES - 1)).playSound(PROBE_CUE);

        tick(program, host, IDLE_TICKS);

        verify(walker, times(CRYSTAL_CHARGES)).playSound(PROBE_CUE);
        assertEquals(CRYSTAL_CHARGES, host.fieldEffect().chargesSpent());
    }

    @Test
    void aSprintingPlayerIsShreddedEveryTickTwiceAsOftenAsAWalker() throws IOException {
        EntityHost walker = walker(WALKER_ID);
        EntityHost sprinter = entityInRadius(OTHER_WALKER_ID, true);
        MarkerHost host = marker(List.of(walker, sprinter));
        ProgramBehavior program = ProgramBehavior.forHost(crystalProgram(), HostKind.MARKER);

        tick(program, host, SPRINT_WINDOW);

        verify(sprinter, times(SPRINT_WINDOW)).playSound(PROBE_CUE);
        verify(walker, times(SPRINT_WINDOW / 2)).playSound(PROBE_CUE);
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
        assertTrue(fieldEffectOf(program(METAL_SPIKES)).standsAgainstBreaking());
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
