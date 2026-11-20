package com.mercuriusxeno.goo.ability.yarn;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.SlimeTransmuteStep;
import com.mercuriusxeno.goo.ability.program.StitchStep;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Runs Stitch on the mobs in it: a passive passes through the goo as if it
 * were not there; a hostile is tangled in its strands, slowed as a cobweb
 * slows, fire on it lands at the multiple Vines' root gives unless it is
 * fire immune, and fire ends the tangle.
 * passives-move-through-stitch-and-hostiles-tangle-in-it
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class StitchEvents {

    /** The share of its motion a tangled mob keeps each tick, a cobweb's. */
    private static final Vec3 COBWEB_SLOW = new Vec3(0.25, 0.05F, 0.25);

    private StitchEvents() {
    }

    /**
     * Runs a Stitch cell on a thing in it or on it, on the server: a slime
     * becomes a passive mob rolled from the table, the cell spending that
     * mob's layers; a hostile is tangled in strands that slow it as a cobweb
     * does; a passive, and a hostile whose strands fire has ended, moves on
     * untouched.
     * stitch-is-yarns-residual-that-turns-slimes-into-passive-mobs
     *
     * @param entity the thing in or on the cell
     * @param pos    the cell
     * @param cell   the cell's state
     * @param stitch the cell's laying step
     */
    public static void touch(Entity entity, BlockPos pos, BlockState cell, StitchStep stitch) {
        if (!(entity.level() instanceof ServerLevel level) || !(entity instanceof LivingEntity living)
                || living.isRemoved()) {
            return;
        }
        if (living instanceof Slime slime && turnSlime(level, slime, pos, cell)) {
            return;
        }
        if (StitchPassage.of(living.getType().getCategory(), living instanceof Enemy) == StitchPassage.TANGLE) {
            tangle(living, cell, stitch);
        }
    }

    /**
     * Turns a slime on a Stitch cell into the passive mob the table rolls for
     * the cell's layers, through the slime transmutation Shape runs, and
     * leaves the cell its layers less the mob's, or air where none are left.
     * stitch-is-yarns-residual-that-turns-slimes-into-passive-mobs
     *
     * @param level the level
     * @param slime the slime
     * @param pos   the cell
     * @param cell  the cell's state
     * @return true where the slime turned
     */
    private static boolean turnSlime(ServerLevel level, Slime slime, BlockPos pos, BlockState cell) {
        List<StitchMob> table = level.registryAccess().lookupOrThrow(StitchMob.REGISTRY).stream()
                .sorted(Comparator.comparing(mob -> mob.entity().toString())).toList();
        Optional<StitchRoll> rolled = StitchRoll.roll(cell.getValue(VolumeCellBlock.LAYERS), table,
                level.getRandom().nextDouble());
        Optional<EntityType<?>> type = rolled.flatMap(roll -> BuiltInRegistries.ENTITY_TYPE.getOptional(
                roll.mob().entity()));
        if (rolled.isEmpty() || type.isEmpty() || SlimeTransmuteStep.transmute(level, slime, type.get(),
                GooTypes.YARN, SlimeTransmuteStep.DEFAULT_MORPH_TICKS).isEmpty()) {
            return false;
        }
        int left = rolled.get().layersLeft();
        level.setBlock(pos, left > 0 ? cell.setValue(VolumeCellBlock.LAYERS, left) : Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);
        return true;
    }

    /**
     * Tangles a hostile in strands that slow it as a cobweb does, unless fire
     * has ended its strands.
     *
     * @param living the hostile
     * @param cell   the cell's state
     * @param stitch the cell's laying step
     */
    private static void tangle(LivingEntity living, BlockState cell, StitchStep stitch) {
        long now = living.level().getGameTime();
        Tangled standing = living.getData(GooAttachments.TANGLED);
        Tangled after = standing.touched(stitch.fireFactor(), now);
        if (after != standing) {
            living.setData(GooAttachments.TANGLED, after);
        }
        if (after.holds(now)) {
            living.makeStuckInBlock(cell, COBWEB_SLOW);
        }
    }

    /**
     * Multiplies fire on a tangled mob that is not fire immune, and lets the
     * fire end its tangle.
     *
     * @param event the incoming damage event
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide() || !living.hasData(GooAttachments.TANGLED)) {
            return;
        }
        Tangled tangled = living.getData(GooAttachments.TANGLED);
        long now = living.level().getGameTime();
        boolean fire = event.getSource().is(DamageTypeTags.IS_FIRE);
        if (!tangled.holds(now)) {
            return;
        }
        event.setAmount(tangled.damageAt(now, event.getAmount(), fire, living.fireImmune()));
        if (fire) {
            living.setData(GooAttachments.TANGLED, tangled.burn());
        }
    }
}
