package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import java.util.OptionalDouble;

/**
 * The variables a chain marker binds, read off its block entity: the
 * server's {@link MarkerHost} reads them when a step evaluates, and the
 * client reads them when a renderer evaluates a step param off the synced
 * ability definition (decision capability-interfaces-derive-host-kind).
 *
 * @param be the marker block entity
 */
public record MarkerVariables(ChainMarkerBlockEntity be) implements Variables {

    @Override
    public OptionalDouble read(String name) {
        return switch (name) {
            case HostVariables.STACKS -> OptionalDouble.of(be.getStackCount());
            case HostVariables.MAX_STACKS -> OptionalDouble.of(be.getMaxStacks());
            case HostVariables.FLAT -> OptionalDouble.of(be.isFlatBlob() ? 1 : 0);
            default -> OptionalDouble.empty();
        };
    }
}
