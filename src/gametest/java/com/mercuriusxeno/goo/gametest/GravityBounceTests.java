package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for the slime coat's bounce under a dimension's gravity: a pig holding jump on a Goop pad in lunar and
 * one in the overworld each bounce through the coat's own landing, and in lunar the held bounces peak higher than a
 * plain jump there and, after four of them, some six times as high as the same four bounces in the overworld.
 * decision goop-bounce-height-is-proportionate-to-gravity
 */
@RegistersGameTests
public final class GravityBounceTests {
    private static final String HELD_BOUNCES_PEAK_PROPORTIONATELY_TO_GRAVITY =
            "held_bounces_peak_proportionately_to_gravity";

    /** Where each dimension's site stands, far from every other test's ground and high over any terrain. */
    private static final int SITE_X = 2184;
    private static final int SITE_Z = 8;
    private static final int SITE_Y = 90;
    /** The chunks each way of the site that stand loaded before its own chunk's entities tick. */
    private static final int ENTITY_TICKING_REACH = 2;
    /** How far east of the pad the plain jumper stands on stone. */
    private static final int PLAIN_JUMPER_OFFSET = 4;
    /** The plain jump off the pad, then the four held bounces it lands into. */
    private static final int PEAKS_READ = 5;
    private static final int FOURTH_HELD_BOUNCE = 4;
    private static final double AT_LEAST_FIVE_TIMES = 5;
    private static final int BREATH_TICKS = 6000;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_BETTER_THAN_A_JUMP = "A held bounce in lunar should peak higher than a plain jump"
            + " there, %s: bounce peaks %s";
    private static final String NOT_PROPORTIONATE = "After four held bounces a pig in lunar should peak at least five"
            + " times as high as in the overworld: lunar peaks %s, overworld peaks %s";

    private GravityBounceTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, HELD_BOUNCES_PEAK_PROPORTIONATELY_TO_GRAVITY,
                GravityBounceTests::heldBouncesPeakProportionatelyToGravity);
    }

    /**
     * A pig holding jump on a slime coat in lunar and one in the overworld, each on its own stone pad built at the
     * same height, and a pig holding jump on bare stone in lunar: each pig's peaks are read from the tick its site's
     * entities tick, and once each pad pig has peaked five times, its plain jump off the pad and four held bounces,
     * the lunar bounces peak over the plain jump and the fourth peaks at least five times the overworld's fourth.
     * decision goop-bounce-height-is-proportionate-to-gravity
     *
     * @param helper the gametest helper
     */
    public static void heldBouncesPeakProportionatelyToGravity(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel lunar = overworld.getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        BlockPos pad = new BlockPos(SITE_X, SITE_Y, SITE_Z);
        BlockPos stone = pad.east(PLAIN_JUMPER_OFFSET);
        PeakWatch lunarBounce = new PeakWatch(lunar, pad, pigOn(lunar, pad, true));
        PeakWatch overworldBounce = new PeakWatch(overworld, pad, pigOn(overworld, pad, true));
        PeakWatch lunarJump = new PeakWatch(lunar, stone, pigOn(lunar, stone, false));
        List<PeakWatch> watches = List.of(lunarBounce, overworldBounce, lunarJump);
        boolean[] read = {false};
        helper.onEachTick(() -> {
            if (read[0]) {
                return;
            }
            watches.forEach(PeakWatch::tick);
            if (lunarBounce.peaks.size() < PEAKS_READ || overworldBounce.peaks.size() < PEAKS_READ
                    || lunarJump.peaks.isEmpty()) {
                return;
            }
            read[0] = true;
            watches.forEach(PeakWatch::release);
            double plainJump = lunarJump.peaks.getFirst();
            List<Double> heldBounces = lunarBounce.peaks.subList(1, PEAKS_READ);
            helper.assertTrue(heldBounces.stream().allMatch(peak -> peak > plainJump),
                    String.format(NO_BETTER_THAN_A_JUMP, plainJump, heldBounces));
            helper.assertTrue(lunarBounce.peaks.get(FOURTH_HELD_BOUNCE)
                            >= AT_LEAST_FIVE_TIMES * overworldBounce.peaks.get(FOURTH_HELD_BOUNCE),
                    String.format(NOT_PROPORTIONATE, lunarBounce.peaks, overworldBounce.peaks));
            helper.succeed();
        });
    }

    /**
     * Forces a site's chunk and loads those around it, lays a stone floor at the site with a slime coat on it or
     * bare, and adds a pig standing on it that breathes, so lunar's airlessness leaves it bouncing.
     *
     * @param level  the level the site stands in
     * @param site   the cell the pig stands in
     * @param coated whether the floor carries a slime coat
     * @return the pig
     */
    private static Pig pigOn(ServerLevel level, BlockPos site, boolean coated) {
        int chunkX = site.getX() >> 4;
        int chunkZ = site.getZ() >> 4;
        level.setChunkForced(chunkX, chunkZ, true);
        for (int dx = -ENTITY_TICKING_REACH; dx <= ENTITY_TICKING_REACH; dx++) {
            for (int dz = -ENTITY_TICKING_REACH; dz <= ENTITY_TICKING_REACH; dz++) {
                level.getChunk(chunkX + dx, chunkZ + dz);
            }
        }
        level.setBlockAndUpdate(site.below(), Blocks.STONE.defaultBlockState());
        double floor = 0;
        if (coated) {
            BlockState coat = GooBlocks.SLIME_COAT.get().defaultBlockState();
            level.setBlockAndUpdate(site, coat);
            floor = (double) VolumeCellBlock.layersOf(coat) / VolumeCellBlock.MAX_LAYERS;
        }
        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
        pig.setPos(Vec3.atBottomCenterOf(site).add(0, floor, 0));
        pig.removeFreeWill();
        pig.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, BREATH_TICKS));
        level.addFreshEntity(pig);
        return pig;
    }

    /** A pig held jumping at a site, and the peaks it has climbed to over where it first stood. */
    private static final class PeakWatch {
        private final ServerLevel level;
        private final BlockPos site;
        private final Pig pig;
        private final List<Double> peaks = new ArrayList<>();
        private double standing = Double.NaN;
        private double rising = Double.NEGATIVE_INFINITY;

        PeakWatch(ServerLevel level, BlockPos site, Pig pig) {
            this.level = level;
            this.site = site;
            this.pig = pig;
        }

        /** Holds the pig's jump and, once its site's entities tick, reads where it climbed to this tick. */
        void tick() {
            pig.getJumpControl().jump();
            if (!level.isPositionEntityTicking(site)) {
                return;
            }
            if (Double.isNaN(standing)) {
                standing = pig.getY();
            }
            double above = pig.getY() - standing;
            if (pig.getDeltaMovement().y > 0) {
                rising = Math.max(rising, above);
            } else if (rising > Double.NEGATIVE_INFINITY) {
                peaks.add(Math.max(rising, above));
                rising = Double.NEGATIVE_INFINITY;
            }
        }

        /** Removes the pig and frees its site's chunk. */
        void release() {
            pig.discard();
            level.setChunkForced(site.getX() >> 4, site.getZ() >> 4, false);
        }
    }
}
