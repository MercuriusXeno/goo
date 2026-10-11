package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;

/**
 * A host standing in a server level at its position, which a step reads
 * and writes around (capability {@link HostCapability#LEVEL}); a prism's
 * reflector links through it (decision reflector-rails-carry-the-brightest-light).
 */
public interface LevelHost extends StepHost {

    /**
     * @return the server level the host stands in
     */
    ServerLevel level();
}
