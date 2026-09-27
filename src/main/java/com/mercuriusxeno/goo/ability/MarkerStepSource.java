package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.Step;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Answers the step program of the ability a chain marker runs, from the
 * abilities its side holds: the server's registry, or the abilities the
 * client was synced (decision diagnose-then-fix-marker-server-gate).
 */
@FunctionalInterface
public interface MarkerStepSource {

    /**
     * The server's source, reading the registry the ability loader fills.
     */
    MarkerStepSource REGISTRY = abilityId -> {
        Identifier id = Identifier.tryParse(abilityId);
        AbilityDefinition def = id != null ? AbilityRegistry.getAbility(id) : null;
        return def != null ? def.behaviors() : null;
    };

    /**
     * The client's source before client setup installs the synced one: it
     * answers no ability.
     */
    MarkerStepSource NONE = abilityId -> null;

    /**
     * Picks the source a marker's side reads: a dedicated server's client
     * holds an empty registry, so the client reads the synced abilities.
     *
     * @param clientSide  true when the marker sits in a client level
     * @param clientSteps the client's installed source
     * @return the client source on the client, the registry otherwise
     */
    static MarkerStepSource forSide(boolean clientSide, MarkerStepSource clientSteps) {
        return clientSide ? clientSteps : REGISTRY;
    }

    /**
     * Answers the step program of the ability an id names.
     *
     * @param abilityId the ability resource id string
     * @return the ability's steps, or null when this side holds no such ability
     */
    @Nullable List<Step> steps(String abilityId);

    /**
     * Loads the ability's program for the marker host.
     *
     * @param abilityId the ability resource id string
     * @return the program, or null when this side holds no such ability
     */
    default @Nullable ProgramBehavior program(String abilityId) {
        List<Step> steps = steps(abilityId);
        return steps != null ? ProgramBehavior.forHost(steps, HostKind.MARKER) : null;
    }
}
