package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.nectar.NectarGuide;
import com.mercuriusxeno.goo.ability.program.NectarGooStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.throwing.DripArc;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;

/**
 * The axis a stream's drips leave along when its program guides them, as
 * Nectar's does: an aim passing a mob the caster sees, within the guide
 * radius of the look ray, throws the tick's drips onto that mob's feet; any
 * other aim leaves the look as it stands.
 * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob
 */
final class NectarThrows {

    private NectarThrows() {
    }

    /**
     * @param player     the spraying player
     * @param apex       the cone's apex, the glove hand
     * @param look       the player's look vector
     * @param blockSteps the stream's block steps, where a nectar goo step names the guide
     * @param exitSpeed  a drip's exit speed, blocks per tick
     * @return the axis the tick's drips leave along
     */
    static Vec3 axisFor(ServerPlayer player, Vec3 apex, Vec3 look, List<Step> blockSteps, double exitSpeed) {
        Optional<NectarGooStep> guide = NectarGooStep.in(blockSteps);
        if (guide.isEmpty()) {
            return look;
        }
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        double reach = guide.get().guideReach();
        List<NectarGuide.Candidate> mobs = level.getEntitiesOfClass(Mob.class, new AABB(eye, eye).inflate(reach),
                        mob -> mob.isAlive() && player.hasLineOfSight(mob)).stream()
                .map(mob -> new NectarGuide.Candidate(mob.getId(), mob.getBoundingBox().getCenter())).toList();
        return NectarGuide.guidedTo(eye, look, mobs, guide.get().guideRadius(), reach)
                .map(level::getEntity)
                .map(mob -> NectarGuide.throwOnto(apex, mob.position(), exitSpeed,
                        DripArc.gravityIn(level.dimension())))
                .orElse(look);
    }
}
