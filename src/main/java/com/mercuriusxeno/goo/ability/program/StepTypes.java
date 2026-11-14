package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonElement;
import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link StepType}s and the dispatch codec that reads a step
 * tree: {@link #CODEC} reads {@code "type"}, finds the type by name and
 * hands the rest of the object to that type's codec. A name no type
 * carries refuses at load, save inside a step list: {@link #LIST_CODEC}
 * drops a step of a type no longer registered, as a save or a pack from
 * before a step's rename carries, and loads the rest
 * (decision shriek-is-a-sculk-call-that-draws-every-mob-it-reaches).
 */
public final class StepTypes {

    private static final Map<String, StepType<?>> TYPES = new LinkedHashMap<>();
    private static final String ERR_UNKNOWN = "Unknown step type: ";
    private static final String TYPE_FIELD = "type";
    private static final String WARN_DROPPED = "Dropping a step of the type {}, which no step type carries any more";

    /**
     * Codec for one step, dispatching on the {@code "type"} field.
     */
    public static final Codec<Step> CODEC = Codec.STRING
            .comapFlatMap(StepTypes::byName, StepType::name)
            .dispatch(TYPE_FIELD, Step::type, StepType::codec);

    /**
     * Codec for a step list, the body of a program or a container step: a
     * step of a type no longer registered is dropped with a warning and the
     * rest load; a known step that does not read refuses the list.
     */
    public static final Codec<List<Step>> LIST_CODEC = Codec.PASSTHROUGH.listOf()
            .flatXmap(StepTypes::knownSteps, StepTypes::encodedSteps);

    static {
        register(LeafSteps.WAIT.type());
        register(AwaitEntityStep.TYPE);
        register(WatchStep.TYPE);
        register(BusterStep.TYPE);
        register(ExplodeStep.TYPE);
        register(DamageStep.TYPE);
        register(PotionStep.TYPE);
        register(TargetStep.TYPE);
        register(FreezeStep.TYPE);
        register(NovaStep.TYPE);
        register(FreezeBlocksStep.TYPE);
        register(CoolLavaStep.TYPE);
        register(CoatStep.TYPE);
        register(SlickStep.TYPE);
        register(VacuumStep.TYPE);
        register(VacuumPullStep.TYPE);
        register(WobbleStep.TYPE);
        register(DetectOreStep.TYPE);
        register(WindStep.TYPE);
        register(MiasmaVortexStep.TYPE);
        register(BreakBlocksStep.TYPE);
        register(ExtinguishStep.TYPE);
        register(TravelingStep.TYPE);
        register(GlacialStep.TYPE);
        register(LeafSteps.SET_AI.type());
        register(LeafSteps.SET_INVULNERABLE.type());
        register(LeafSteps.REWIND_HOLD.type());
        register(RegressStep.TYPE);
        register(SpawnSlimeStep.TYPE);
        register(AncientStep.TYPE);
        register(DropItemStep.TYPE);
        register(LeafSteps.IGNITE.type());
        register(LeafSteps.LIGHT_FIRE.type());
        register(LeafSteps.DETONATE_EXPLOSIVE.type());
        register(MeltStep.TYPE);
        register(EntitiesStep.TYPE);
        register(ParticlesStep.TYPE);
        register(SoundStep.TYPE);
        register(TeleportStep.TYPE);
        register(PushStep.TYPE);
        register(PlaceBlockStep.TYPE);
        register(SetStateStep.TYPE);
        register(FieldEffectStep.TYPE);
        register(LingerStep.TYPE);
        register(PhasedStep.TYPE);
        register(PullStep.TYPE);
        register(LeafSteps.CONSUME_BLOCKS.type());
        register(LeafSteps.DROP_SPHERE.type());
        register(CounterStep.TYPE);
        register(BranchStep.TYPE);
        register(LeafSteps.DISCARD.type());
        register(LeafSteps.SET_BABY.type());
        register(AilmentOverlayStep.TYPE);
        register(AfterimageStep.TYPE);
        register(GhostTrailStep.TYPE);
        register(HeartOverlayStep.TYPE);
        register(SporeHostStep.TYPE);
        register(ColonizeStep.TYPE);
        register(FloorsStep.TYPE);
        register(ShiftStep.TYPE);
        register(SightStep.TYPE);
        register(LuxStep.TYPE);
        register(EldritchStep.TYPE);
        register(WispsStep.TYPE);
        register(ReflectorStep.TYPE);
        register(ScryStep.TYPE);
        register(RayStep.TYPE);
        register(UndeadStep.TYPE);
        register(FlattenStep.TYPE);
        register(BoreStep.TYPE);
        register(SpireStep.TYPE);
        register(PetrifyStep.TYPE);
        register(DrainPetrifyStep.TYPE);
        register(WakeStatueStep.TYPE);
        register(CalcifyStep.TYPE);
        register(DegradeStep.TYPE);
        register(FlamesStep.TYPE);
        register(HeaterStep.TYPE);
        register(LeafSteps.HEAL.type());
        register(LeafSteps.COURT.type());
        register(LeafSteps.POWER_PULSE.type());
        register(LeafSteps.AGE.type());
        register(MetronomeStep.TYPE);
        register(RelayStep.TYPE);
        register(ExtenderStep.TYPE);
        register(LeafSteps.STUN.type());
        register(SignalWaveStep.TYPE);
        register(PulserToggleStep.TYPE);
        register(GluttonyStep.TYPE);
        register(ReserveDrainStep.TYPE);
        register(FeedStep.TYPE);
        register(ZoneStep.TYPE);
        register(ShifterStep.TYPE);
        register(ShriekStep.TYPE);
        register(SculkerStep.TYPE);
        register(LeaderStep.TYPE);
        register(VibrateStep.TYPE);
        register(ScanVisualStep.TYPE);
        register(EndStep.TYPE);
        register(HoleOpenStep.TYPE);
        register(OutsiderStep.TYPE);
        register(MeteorStep.TYPE);
        register(WormholeStep.TYPE);
        register(RootStep.TYPE);
        register(HitOrMissStep.TYPE);
        register(BloomStep.TYPE);
        register(TickPlantsStep.TYPE);
        register(TendFungiStep.TYPE);
        register(ToxinStep.TYPE);
        register(VerdantStep.TYPE);
        register(HastenRegrowStep.TYPE);
        register(SlashStep.TYPE);
        register(CharmStep.TYPE);
        register(PhaseStep.TYPE);
        register(PortalStep.TYPE);
        register(QuantumSwapStep.TYPE);
        register(LeafSteps.ENCHANT_BOOK.type());
        register(FuseBooksStep.TYPE);
        register(SlimeTransmuteStep.TYPE);
        register(RallyStep.TYPE);
        register(LifetapStep.TYPE);
        register(LeechStep.TYPE);
        register(TomeStep.TYPE);
        register(FloatStep.TYPE);
        register(AirbornStep.TYPE);
        register(PneumaStep.TYPE);
        register(UpdraftStep.TYPE);
        register(LiftStep.TYPE);
        register(GrabStep.TYPE);
        register(TerrakinesisStep.TYPE);
        register(TelekinesisStep.TYPE);
    }

    private StepTypes() {
    }

    /**
     * Registers a step type under its name.
     *
     * @param type the type to register
     */
    public static void register(StepType<?> type) {
        TYPES.put(type.name(), type);
    }

    /**
     * Returns every registered type, in registration order.
     *
     * @return the types
     */
    public static Collection<StepType<?>> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    /**
     * Finds a type by the name the JSON carries.
     *
     * @param name the type name
     * @return the type, or an error naming the unknown type
     */
    /**
     * Reads a step list, dropping each step whose type no step type carries.
     *
     * @param entries the list's entries, unread
     * @return the steps read, or the first known step's refusal
     */
    private static DataResult<List<Step>> knownSteps(List<Dynamic<?>> entries) {
        List<Step> steps = new ArrayList<>();
        for (Dynamic<?> entry : entries) {
            Optional<String> name = entry.get(TYPE_FIELD).asString().result();
            if (name.isPresent() && !TYPES.containsKey(name.get())) {
                Goo.LOGGER.warn(WARN_DROPPED, name.get());
                continue;
            }
            DataResult<Step> step = CODEC.parse(entry);
            Optional<DataResult.Error<Step>> refused = step.error();
            if (refused.isPresent()) {
                return DataResult.error(refused.get().messageSupplier());
            }
            steps.add(step.getOrThrow());
        }
        return DataResult.success(steps);
    }

    /**
     * Writes a step list, each step as its type's codec writes it.
     *
     * @param steps the steps
     * @return the list's entries, or the first step's refusal
     */
    private static DataResult<List<Dynamic<?>>> encodedSteps(List<Step> steps) {
        List<Dynamic<?>> entries = new ArrayList<>();
        for (Step step : steps) {
            DataResult<JsonElement> written = CODEC.encodeStart(JsonOps.INSTANCE, step);
            Optional<DataResult.Error<JsonElement>> refused = written.error();
            if (refused.isPresent()) {
                return DataResult.error(refused.get().messageSupplier());
            }
            entries.add(new Dynamic<>(JsonOps.INSTANCE, written.getOrThrow()));
        }
        return DataResult.success(entries);
    }

    private static DataResult<StepType<?>> byName(String name) {
        StepType<?> type = TYPES.get(name);
        if (type == null) {
            return DataResult.error(() -> ERR_UNKNOWN + name);
        }
        return DataResult.success(type);
    }
}
