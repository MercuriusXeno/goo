package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where each caster's current held-sweep pulse began: where the caster
 * stood as it started, kept through the pulse so its front never moves
 * with its caster, and taken afresh as the next pulse starts.
 * decision held-sweeps-share-one-fast-shell-visual
 */
public final class HeldSweepOrigins {

    private static final Map<UUID, Vec3> PULSE_ORIGINS = new ConcurrentHashMap<>();

    private HeldSweepOrigins() {
    }

    /**
     * The origin of a caster's pulse on one of its ticks.
     *
     * @param caster    the caster
     * @param pulseTick the pulse's tick, 1 on its first
     * @return the pulse's origin
     */
    public static Vec3 originOf(LivingEntity caster, int pulseTick) {
        Vec3 origin = pulseTick == 1 ? caster.position()
                : PULSE_ORIGINS.getOrDefault(caster.getUUID(), caster.position());
        PULSE_ORIGINS.put(caster.getUUID(), origin);
        return origin;
    }
}
