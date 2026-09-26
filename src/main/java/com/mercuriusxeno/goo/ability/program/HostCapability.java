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
     * A stack count that can be read and spent, and the marker variables.
     */
    STACKS(StacksHost.class),
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
     * Blocks around the anchor the host strikes layer by layer, with the
     * layer fx and the mined-layer count its renderer reads.
     */
    LAYER_WALK(LayerWalkHost.class),
    /**
     * A field-effect state the host keeps across ticks and its renderer
     * reads: the strikes in flight, the strike cooldown and the charges
     * spent on the current stack.
     */
    FIELD_EFFECT(FieldEffectHost.class),
    /**
     * A phased-step cursor the host keeps across ticks and its renderer
     * reads: the phase running, its progress and the declared reach.
     */
    PHASED(PhasedHost.class),
    /**
     * A goo total the host fills by consuming the valued blocks around its
     * anchor and drops as blobs.
     */
    CONSUMED_GOO(ConsumedGooHost.class);

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
