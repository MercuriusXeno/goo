package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of {@link StepType}s and the dispatch codec that reads a step
 * tree: {@link #CODEC} reads {@code "type"}, finds the type by name and
 * hands the rest of the object to that type's codec. A name no type
 * carries refuses at load.
 */
public final class StepTypes {

    private static final Map<String, StepType<?>> TYPES = new LinkedHashMap<>();
    private static final String ERR_UNKNOWN = "Unknown step type: ";
    private static final String TYPE_FIELD = "type";

    /**
     * Codec for one step, dispatching on the {@code "type"} field.
     */
    public static final Codec<Step> CODEC = Codec.STRING
            .comapFlatMap(StepTypes::byName, StepType::name)
            .dispatch(TYPE_FIELD, Step::type, StepType::codec);

    /**
     * Codec for a step list, the body of a program or a container step.
     */
    public static final Codec<List<Step>> LIST_CODEC = CODEC.listOf();

    static {
        register(LeafSteps.WAIT.type());
        register(AwaitEntityStep.TYPE);
        register(ExplodeStep.TYPE);
        register(DamageStep.TYPE);
        register(PotionStep.TYPE);
        register(TargetStep.TYPE);
        register(FreezeStep.TYPE);
        register(NovaStep.TYPE);
        register(FreezeBlocksStep.TYPE);
        register(DripsStep.TYPE);
        register(WindStep.TYPE);
        register(BreakBlocksStep.TYPE);
        register(TravelingStep.TYPE);
        register(GlacialStep.TYPE);
        register(LeafSteps.SET_AI.type());
        register(LeafSteps.SET_INVULNERABLE.type());
        register(LeafSteps.STASIS.type());
        register(LeafSteps.REWIND_HOLD.type());
        register(RegressStep.TYPE);
        register(TickBlockStep.TYPE);
        register(DripsStep.TYPE);
        register(BankTicksStep.TYPE);
        register(WithdrawBankStep.TYPE);
        register(SlowTimeStep.TYPE);
        register(HasteStep.TYPE);
        register(DropItemStep.TYPE);
        register(LeafSteps.IGNITE.type());
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
        register(WispsStep.TYPE);
        register(ReflectorStep.TYPE);
        register(ScryStep.TYPE);
        register(RayStep.TYPE);
        register(UndeadStep.TYPE);
        register(FlattenStep.TYPE);
        register(BoreStep.TYPE);
        register(CrushStep.TYPE);
        register(PetrifyStep.TYPE);
        register(CalcifyStep.TYPE);
        register(DegradeStep.TYPE);
        register(PetrifyDripStep.TYPE);
        register(DegradeDripStep.TYPE);
        register(LeafSteps.HEAL.type());
        register(LeafSteps.COURT.type());
        register(LeafSteps.POWER_PULSE.type());
        register(LeafSteps.TOGGLE_DEVICE.type());
        register(EmitPowerStep.TYPE);
        register(MetronomeStep.TYPE);
        register(RelayStep.TYPE);
        register(ExtenderStep.TYPE);
        register(LeafSteps.STUN.type());
        register(SignalWaveStep.TYPE);
        register(PulserToggleStep.TYPE);
        register(NourishStep.TYPE);
        register(ReserveDrainStep.TYPE);
        register(BanishStep.TYPE);
        register(TeleportitisStep.TYPE);
        register(ConvokeStep.TYPE);
        register(DragonGateStep.TYPE);
        register(RootStep.TYPE);
        register(HitOrMissStep.TYPE);
        register(BloomStep.TYPE);
        register(TickPlantsStep.TYPE);
        register(TendFungiStep.TYPE);
        register(PulsePlantsStep.TYPE);
        register(ToxinStep.TYPE);
        register(VerdantStep.TYPE);
        register(HastenRegrowStep.TYPE);
        register(ReapStep.TYPE);
        register(CharmStep.TYPE);
        register(LeafSteps.ENCHANT_BOOK.type());
        register(FuseBooksStep.TYPE);
        register(SlimeTransmuteStep.TYPE);
        register(RallyStep.TYPE);
        register(AgitateStep.TYPE);
        register(LifetapStep.TYPE);
        register(LeechStep.TYPE);
        register(TomeStep.TYPE);
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
    private static DataResult<StepType<?>> byName(String name) {
        StepType<?> type = TYPES.get(name);
        if (type == null) {
            return DataResult.error(() -> ERR_UNKNOWN + name);
        }
        return DataResult.success(type);
    }
}
