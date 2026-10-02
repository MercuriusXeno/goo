package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mercuriusxeno.goo.type.GooColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Drips falling from a goo splat the whole time it stands: from the impact
 * until it has dissolved, splat-drips hang from the splat's lower half and
 * then fall, thickest just after the hit and thinning as the splat dissolves.
 * Decision splat-holds-then-dissolves-dripping.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SplatDrips {

    /** Drips per tick the tick after the hit, beyond the floor: about three a second in all when fresh. */
    static final float FRESH_DRIPS_PER_TICK = 0.125f;

    /** Drips per tick a splat sheds until it is gone, however far it has dissolved: about one every two seconds. */
    static final float FLOOR_DRIPS_PER_TICK = 0.025f;

    /** Share of the splat's radius drips leave from, inside its noise-broken edge. */
    static final double DRIP_REACH = 0.8;

    /** Radius of the splat in blocks; must match SPLAT_RADIUS in goo_mob_coat.fsh. */
    static final double SPLAT_RADIUS = 0.25;

    /** Fully opaque alpha for particle colors. */
    private static final int OPAQUE_BLACK = 0xFF000000;

    private SplatDrips() {
    }

    /**
     * Where a mob stands now.
     *
     * @param entity the mob
     * @return its feet and its body's yaw
     */
    public static MobCoats.Stance stanceOf(Entity entity) {
        float bodyYaw = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
        return new MobCoats.Stance(entity.position(), bodyYaw);
    }

    /**
     * Drips a splat sheds in one tick of its life: the rate falls from the
     * fresh rate after the hit to the floor as the splat ages, the fraction
     * past a whole drip settled by the random, and none once it is gone.
     *
     * @param ageTicks game ticks since its hit
     * @param random   the source settling a fractional drip
     * @return the drips this tick
     */
    static int dripsAt(int ageTicks, RandomGenerator random) {
        float rate = dripRate(ageTicks);
        int whole = (int) rate;
        return whole + (random.nextFloat() < rate - whole ? 1 : 0);
    }

    /**
     * Drips a splat sheds a tick on average at an age: the fresh rate on top
     * of the floor just after the hit, falling to the floor as it ages, and
     * none once it is gone.
     *
     * @param ageTicks game ticks since its hit
     * @return the mean drips per tick
     */
    static float dripRate(int ageTicks) {
        if (ageTicks < 0 || ageTicks >= MobCoats.COAT_LIFE_TICKS) {
            return 0f;
        }
        float fresh = 1f - (float) ageTicks / MobCoats.COAT_LIFE_TICKS;
        return FLOOR_DRIPS_PER_TICK + FRESH_DRIPS_PER_TICK * fresh;
    }

    /**
     * The points drips leave a splat from this tick: within the splat's reach
     * of the hit point and no higher than it, so they run off its lower half.
     *
     * @param hitPoint the splat's world point now
     * @param ageTicks game ticks since its hit
     * @param random   the scatter source
     * @return the points to spawn drips at
     */
    static List<Vec3> shed(Vec3 hitPoint, int ageTicks, RandomGenerator random) {
        int count = dripsAt(ageTicks, random);
        List<Vec3> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(hitPoint.add(lowerHalfOffset(random)));
        }
        return points;
    }

    /**
     * A random point in the lower half of a ball of the drip reach.
     *
     * @param random the scatter source
     * @return the offset from the hit point
     */
    private static Vec3 lowerHalfOffset(RandomGenerator random) {
        Vec3 direction = new Vec3(random.nextGaussian(), -Math.abs(random.nextGaussian()), random.nextGaussian());
        double length = direction.length();
        if (length == 0) {
            return Vec3.ZERO;
        }
        double reach = SPLAT_RADIUS * DRIP_REACH * Math.cbrt(random.nextDouble());
        return direction.scale(reach / length);
    }

    /**
     * Sheds the drips of every standing splat on a mob the client holds.
     *
     * @param event the client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long tick = level.getGameTime();
        RandomGenerator random = RandomGenerator.getDefault();
        MobCoats.CLIENT.forEachStanding(tick, (entityId, coat) -> {
            Entity mob = level.getEntity(entityId);
            if (mob == null) {
                return;
            }
            Vec3 hitPoint = coat.hitPointOn(stanceOf(mob));
            int argb = GooColors.get(level.registryAccess(), coat.gooType()) | OPAQUE_BLACK;
            for (Vec3 point : shed(hitPoint, (int) (tick - coat.hitTick()), random)) {
                level.addParticle(ColorParticleOption.create(GooParticles.SPLAT_DRIP.get(), argb),
                        point.x, point.y, point.z, 0, 0, 0);
            }
        });
    }
}
