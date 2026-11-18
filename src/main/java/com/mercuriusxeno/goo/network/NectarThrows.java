package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.nectar.NectarGuide;
import com.mercuriusxeno.goo.ability.program.NectarGooStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.throwing.ConvergingCone;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;

/**
 * The cone a stream's drips land in when its program guides them, as
 * Nectar's does: an aim passing a mob the caster sees, within the guide
 * radius of the look ray, centers the tick's cone on that mob's feet; any
 * other aim leaves the cone on the look.
 * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob
 */
final class NectarThrows {

    private NectarThrows() {
    }

    /**
     * @param player     the spraying player
     * @param blockSteps the stream's block steps, where a nectar goo step names the guide
     * @return the cone centered on the guided mob's feet, empty where the aim guides nothing
     */
    static Optional<ConvergingCone> guidedCone(ServerPlayer player, List<Step> blockSteps) {
        Optional<NectarGooStep> guide = NectarGooStep.in(blockSteps);
        if (guide.isEmpty()) {
            return Optional.empty();
        }
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        double reach = guide.get().guideReach();
        List<NectarGuide.Candidate> mobs = level.getEntitiesOfClass(Mob.class, new AABB(eye, eye).inflate(reach),
                        mob -> mob.isAlive() && player.hasLineOfSight(mob)).stream()
                .map(mob -> new NectarGuide.Candidate(mob.getId(), mob.getBoundingBox().getCenter())).toList();
        return NectarGuide.guidedTo(eye, player.getLookAngle(), mobs, guide.get().guideRadius(), reach)
                .map(level::getEntity)
                .map(mob -> ConvergingCone.onto(eye, mob.position()));
    }
}
