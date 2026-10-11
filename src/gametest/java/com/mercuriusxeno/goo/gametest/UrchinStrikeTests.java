package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.FieldEffectState;
import com.mercuriusxeno.goo.ability.program.FieldStrike;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for Metal's Urchin: every spear the trap shoots at a walking
 * mob hurts it and stands in it when the hurt lands. Before the fix the
 * spear aimed at the point the mob stood when picked while the damage
 * landed six ticks later, so a walking zombie stood 0.48 to 1.25 blocks
 * off the spear on every shot.
 * decision urchin-rests-bristled-and-spears
 */
@RegistersGameTests
public final class UrchinStrikeTests {

    private static final String URCHIN_HITS_EVERY_SHOT = "urchin_hits_every_shot";

    private static final Identifier METAL_SPIKES = Identifier.parse("goo:metal_spikes");
    private static final BlockPos MARKER_POS = new BlockPos(3, 1, 3);
    private static final BlockPos ZOMBIE_POS = MARKER_POS.east(2);
    private static final int FLOOR_SPAN = 6;
    /** The two ends of the zombie's walk, each inside the trap's radius, south of the marker. */
    private static final BlockPos WALK_EAST = MARKER_POS.south(2).east(2);
    private static final BlockPos WALK_WEST = MARKER_POS.south(2).west(2);
    /** Ticks the zombie walks toward one end before turning to the other. */
    private static final int PACE_TICKS = 20;
    private static final int SHOTS = 10;
    /** The strike age metal_spikes.json's strike_tick lands the stab at. */
    private static final int LANDING_AGE = 6;
    /** Health enough that ten six-damage stabs leave the zombie standing. */
    private static final double DURABLE_HEALTH = 200;
    /** Ticks by which ten shots, each at most a strike's length and a cooldown apart, have landed. */
    private static final int DEADLINE = 380;
    private static final String ABILITY_REQUIRED = "Ability registry must hold metal_spikes";
    private static final String MISSED = "Urchin missed %d of %d shots at the walking zombie,"
            + " health before and after and the spear's distance off the zombie on each: %s";
    private static final String TOO_FEW_SHOTS = "Urchin shot %d of ten times at the walking zombie: %s";

    private UrchinStrikeTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, URCHIN_HITS_EVERY_SHOT, UrchinStrikeTests::urchinHitsEveryShot);
    }

    /**
     * An urchin beside a zombie walking back and forth through it shoots it
     * ten times: each stab hurts it, and the spear's aim is the zombie's
     * body center when the stab lands, this tick's or the last, since the
     * trap and the zombie tick in an order the test does not fix. The aim
     * is compared as the strike stores it, in floats. The test re-arms the
     * trap each tick so its charges last ten shots.
     *
     * @param helper the gametest helper
     */
    public static void urchinHitsEveryShot(GameTestHelper helper) {
        AbilityBlockEntity trap = landUrchin(helper);
        Zombie zombie = durableZombie(helper);
        List<String> shots = new ArrayList<>();
        int[] misses = new int[1];
        float[] healthAtShot = new float[1];
        int[] lastAge = {Integer.MAX_VALUE};
        boolean[] judged = {true};
        Vec3[] centerBefore = {zombie.getBoundingBox().getCenter()};
        helper.onEachTick(() -> {
            Vec3 center = zombie.getBoundingBox().getCenter();
            BlockPos goal = (helper.getTick() / PACE_TICKS) % 2 == 0 ? WALK_EAST : WALK_WEST;
            Vec3 wanted = helper.absoluteVec(Vec3.atBottomCenterOf(goal));
            zombie.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, 1.0);
            FieldEffectState field = trap.getFieldEffect();
            field.setChargesSpent(0);
            field.strikes().stream().filter(strike -> strike.entityId() == zombie.getId()).findFirst()
                    .ifPresent(strike -> {
                        if (strike.age() < lastAge[0]) {
                            healthAtShot[0] = zombie.getHealth();
                            judged[0] = false;
                        }
                        lastAge[0] = strike.age();
                        if (!judged[0] && strike.age() > LANDING_AGE) {
                            judged[0] = true;
                            boolean onTarget = strike.equals(strike.reaimedAt(center))
                                    || strike.equals(strike.reaimedAt(centerBefore[0]));
                            shots.add(String.format("%.0f->%.0f %s", healthAtShot[0], zombie.getHealth(),
                                    onTarget ? "on" : "off by " + center.distanceTo(
                                            new Vec3(strike.x(), strike.y(), strike.z()))));
                            if (zombie.getHealth() >= healthAtShot[0] || !onTarget) {
                                misses[0]++;
                            }
                        }
                    });
            centerBefore[0] = center;
            if (shots.size() >= SHOTS) {
                helper.assertTrue(misses[0] == 0, String.format(MISSED, misses[0], shots.size(), shots));
                zombie.discard();
                helper.setBlock(MARKER_POS, Blocks.AIR);
                helper.succeed();
            }
        });
        helper.runAfterDelay(DEADLINE, () -> helper.fail(String.format(TOO_FEW_SHOTS, shots.size(), shots)));
    }

    /**
     * Floors the bay under the trap's radius and lands an Urchin on the
     * stone north of the marker position.
     *
     * @param helper the gametest helper
     * @return the trap's block entity
     */
    private static AbilityBlockEntity landUrchin(GameTestHelper helper) {
        for (int x = 0; x <= FLOOR_SPAN; x++) {
            for (int z = 0; z <= FLOOR_SPAN; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(MARKER_POS.north(), Blocks.STONE);
        AbilityDefinition urchin = AbilityRegistry.of(helper.getLevel()).getAbility(METAL_SPIKES);
        helper.assertTrue(urchin != null, ABILITY_REQUIRED);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(MARKER_POS.north()), GooTypes.METAL,
                Direction.SOUTH, urchin, null, 0);
        return helper.getBlockEntity(MARKER_POS, AbilityBlockEntity.class);
    }

    /**
     * Spawns a zombie beside the trap that ten stabs and the daylight leave standing.
     *
     * @param helper the gametest helper
     * @return the zombie
     */
    private static Zombie durableZombie(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(DURABLE_HEALTH);
        zombie.setHealth((float) DURABLE_HEALTH);
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
        return zombie;
    }
}
