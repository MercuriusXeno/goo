package com.mercuriusxeno.goo.ability.program;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * What a step asks of its host. Each capability is the interface a host
 * implements to provide it, a {@link HostKind} provides the ones its host
 * type implements, a {@link Step} names the ones it needs, and
 * {@link ProgramBehavior#forHost} refuses a program at load when a step
 * needs one the host lacks (decisions host-agnostic-runtime and
 * capability-interfaces-derive-host-kind).
 */
public enum HostCapability {
    /**
     * A placed face on a block.
     */
    PLACED_FACE(PlacedFaceHost.class),
    /**
     * A driver that ticks the program past its first tick.
     */
    TICKING(TickingHost.class),
    /**
     * An explosion at the host anchor.
     */
    EXPLODE(ExplodeHost.class),
    /**
     * A scan for entities around the anchor.
     */
    ENTITY_SCAN(EntityScanHost.class),
    /**
     * A struck entity the effect steps act on.
     */
    TARGET(TargetHost.class),
    /**
     * A block position the host can write a block state into.
     */
    PLACE_BLOCK(PlaceBlockHost.class),
    /**
     * A field-effect state the host keeps across ticks and its renderer
     * reads: the strikes in flight, the strike cooldown and the charges
     * spent.
     */
    FIELD_EFFECT(FieldEffectHost.class),
    /**
     * A phased-step cursor the host keeps across ticks and its renderer
     * reads: the phase running, its progress and the declared reach.
     */
    PHASED(PhasedHost.class),
    /**
     * A hoard of stacks the host fills from the blocks and items around its
     * anchor and leaves as a compression sphere.
     */
    HOARD(HoardHost.class),
    /**
     * A landing where the ability can stand its own block, which runs the
     * steps handed to it (decision lingering-abilities-place-their-own-thing).
     */
    LINGER(LingerHost.class),
    /**
     * A landing where a blob can grow a shroom network
     * (decision colonize-blob-grows-the-network).
     */
    COLONIZE(ColonizeHost.class),
    /**
     * A one-tick host anchored at a point, which can run steps on each open
     * floor around it (decision colonize-blob-grows-the-network).
     */
    FLOOR_SCAN(FloorScanHost.class),
    /**
     * A held channel's aim this tick and the player's hand to break blocks
     * with (decision flatten-disc-cursor-breaks-above-the-plane).
     */
    CHANNEL(ChannelHost.class),
    /**
     * The blocks around the host to read and break (decision
     * crush-blob-breaks-along-its-strike).
     */
    BREAK_BLOCKS(BlockBreakHost.class),
    /**
     * A tap drip's landing: the drips its block has taken, and dripstone to
     * grow down from it (decision petrify-drip-calcifies-and-grows-dripstone).
     */
    DRIP(DripHost.class),
    /**
     * The host's own block, whose properties can change in place (decision
     * bulb-one-model-max-light-beacon-combo).
     */
    STATE_WRITE(StateWriteHost.class),
    /**
     * The server level the host stands in, read and written around its
     * position (decision reflector-rails-carry-the-brightest-light).
     */
    LEVEL(LevelHost.class),
    /**
     * A spot a mob from the host's chunk can be pulled to (decision
     * convoke-blob-throbs-until-a-mob-arrives).
     */
    CONVOKE(ConvokeHost.class),
    /**
     * A struck surface a Dragon Gate can open over (decision
     * dragon-gate-banishes-blocks-and-opens-a-portal).
     */
    DRAGON_GATE(GateHost.class),
    /**
     * A struck surface Astral's gate can open over (decision
     * astral-visits-lunar-and-solar-dimensions).
     */
    ASTRAL_GATE(GateHost.class),
    /**
     * A landing a meteor can be called down on (decision
     * meteo-needs-a-clear-sky).
     */
    METEOR(MeteorHost.class),
    /**
     * The world around a lasting host to green tick after tick (decision
     * verdant-prism-greens-blocks-slowly).
     */
    GREENING(GreeningHost.class),
    /**
     * A landing that can tick the redstone device it landed on (decision
     * zap-ticks-the-device-and-stuns).
     */
    POWER_PULSE(PowerPulseHost.class),
    /**
     * A host that can toggle the redstone device where it acts, as a hand
     * would (decision pulser-drip-toggles-the-block-below).
     */
    TOGGLE_DEVICE(DeviceToggleHost.class),
    /**
     * A host whose own block can give redstone power to its neighbors
     * (decision thumper-blob-pulses-periodically-then-fades).
     */
    EMIT_POWER(PowerEmitHost.class),
    /**
     * A host that hears the redstone signals reaching its block and keeps
     * their beat (decision metronome-prism-pulses-at-the-learned-rate).
     */
    BEAT(BeatHost.class),
    /**
     * A host that carries redstone signals between relays linked through air
     * (decision relay-prism-carries-the-signal-through-air).
     */
    RELAY(RelayHost.class),
    /**
     * A host that can lengthen the timed effects standing on it (decision
     * extender-multiplies-the-next-self-duration).
     */
    EXTEND_EFFECTS(EffectExtendHost.class),
    /**
     * A cell a conjured mob stands in, and the point the goo morphs into it
     * from (decision spawn-goo-morphs-into-the-mob-it-births).
     */
    SPAWN_MOB(MobSpawnHost.class),
    /**
     * An agitator's countdown kept across ticks (decision
     * agitator-prism-quickens-until-a-spawn).
     */
    AGITATE(AgitateHost.class),
    /**
     * The level and the point frost spreads out of (decisions
     * nova-ring-grows-with-the-hold, nova-drip-pulses-a-short-lasting-freeze).
     */
    FROST(FrostHost.class),
    /**
     * A block to tick faster, the aimed machine a held stream ends on
     * (decision tick-channel-marches-squares-on-the-face).
     */
    TICK_BLOCK(TickBlockHost.class),
    /**
     * An anchor that banks ticks, the timekeeper prism
     * (decision timekeeper-prism-banks-ticks-forward-only).
     */
    TICK_BANK(TickBankHost.class),
    /**
     * A sphere around the anchor to slow time in, the chronosphere's marker
     * (decision chronosphere-hastes-players-slows-mobs).
     */
    TIME_VEIL(TimeVeilHost.class);

    private final Class<? extends StepHost> hostType;

    HostCapability(Class<? extends StepHost> hostType) {
        this.hostType = hostType;
    }

    /**
     * Returns the lower-case name a refusal message uses.
     *
     * @return the key
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the interface a host implements to provide this capability.
     *
     * @return the capability interface
     */
    public Class<? extends StepHost> hostType() {
        return hostType;
    }

    /**
     * Collects the capabilities a host type provides, one for each
     * capability interface it implements.
     *
     * @param host the host type
     * @return the provided capabilities
     */
    public static Set<HostCapability> providedBy(Class<? extends StepHost> host) {
        Set<HostCapability> provided = EnumSet.noneOf(HostCapability.class);
        for (HostCapability capability : values()) {
            if (capability.hostType.isAssignableFrom(host)) {
                provided.add(capability);
            }
        }
        return Collections.unmodifiableSet(provided);
    }
}
