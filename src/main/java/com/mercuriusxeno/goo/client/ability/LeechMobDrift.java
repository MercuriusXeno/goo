package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import com.mercuriusxeno.goo.block.ability.LeechSouls;
import com.mercuriusxeno.goo.block.ability.LeechStanding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.Optional;

/**
 * What drifts off every living thing standing in Leech goo, for as long as
 * it stands there: hex wisps on the leech goo step's shed interval, and now
 * and then vanilla's soul particle off its body, the goo itself popping
 * none. The client holds the cells and the mobs, so each tick it reads who
 * stands in the goo through the seam the server's drain reads.
 * hex-wisps-drift-off-mobs-standing-in-leech-goo
 * soul-particles-pop-from-leech-goo-now-and-then
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class LeechMobDrift {

    /** How fast a popped soul rises off a mob, blocks a tick, near a soul sand soul's drift. */
    private static final double SOUL_RISE_SPEED = 0.03;

    private LeechMobDrift() {
    }

    /**
     * Sheds the wisps due this tick, and pops the souls drawn, off the living
     * things standing in Leech goo.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living) {
                driftOff(mc, level, living);
            }
        }
    }

    private static void driftOff(Minecraft mc, ClientLevel level, LivingEntity living) {
        if (!living.isAlive() || living.isSpectator()) {
            return;
        }
        Optional<BlockPos> cell = LeechStanding.leechCellUnder(living);
        Optional<LeechGooStep> leech = cell.flatMap(pos -> LeechStanding.stepAt(level, pos));
        if (leech.isEmpty()) {
            return;
        }
        RandomSource random = level.getRandom();
        if (LeechWisps.sheds(true, living.tickCount, leech.get().wispShedTicks())) {
            LeechWisps.shed(mc, random, living);
        }
        if (LeechSouls.pops(true, random.nextDouble(), leech.get().soulPopChance())) {
            Vec3 at = LeechSouls.bodyPoint(living.getBoundingBox(), random.nextDouble(), random.nextDouble(),
                    random.nextDouble());
            level.addParticle(ParticleTypes.SOUL, at.x, at.y, at.z, 0, SOUL_RISE_SPEED, 0);
        }
    }
}
