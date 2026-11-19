package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.crystal.OreSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The fixed synthetic world every held-sweep count and timing reads: stone
 * cut by a lattice of tunnels three blocks wide every twelve blocks along
 * each axis, so every front meets walls on every tick; two-by-two diamond
 * veins every twenty blocks wherever the stone stands; and a mob standing
 * in the tunnels every twenty-four blocks. The caster stands at a tunnel
 * crossing. The world is the assumption every count stands on: a real cave
 * holds fewer walls, a real ore field fewer veins.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
public final class HeldSweepWorld {

    /** Where the caster stands: the foot of a tunnel crossing. */
    public static final Vec3 CASTER = new Vec3(1.5, 0, 1.5);
    /** The ore the veins hold. */
    public static final Identifier DIAMOND = Identifier.withDefaultNamespace("diamond_ore");
    private static final int TUNNEL_EVERY = 12;
    private static final int TUNNEL_WIDTH = 3;
    private static final int OPEN_AXES_FOR_AIR = 2;
    private static final int VEIN_EVERY = 20;
    private static final int VEIN_FROM = 5;
    private static final int VEIN_WIDTH = 2;
    private static final int MOB_EVERY = 24;
    private static final double MOB_OFF_THE_LATTICE = 1.5;
    private static final double MOBS_REACH = 120;
    private static final int SECTION_SIZE = 16;
    private static final List<AABB> CUBE = List.of(new AABB(0, 0, 0, 1, 1, 1));

    private final List<TargetHost> hosts = new ArrayList<>();
    /** Each mob's spot beside its host, so a scan measures distance without a mocked call. */
    private final List<Vec3> spots = new ArrayList<>();

    /** Places the mobs, each a mocked living entity standing at its spot behind a host bound to it. */
    public HeldSweepWorld() {
        int most = (int) (MOBS_REACH / MOB_EVERY);
        for (int x = -most; x <= most; x++) {
            for (int y = -most; y <= most; y++) {
                for (int z = -most; z <= most; z++) {
                    Vec3 at = new Vec3(x * MOB_EVERY + MOB_OFF_THE_LATTICE, y * MOB_EVERY,
                            z * MOB_EVERY + MOB_OFF_THE_LATTICE);
                    if (at.distanceTo(CASTER) <= MOBS_REACH) {
                        LivingEntity mob = mock(LivingEntity.class);
                        when(mob.position()).thenReturn(at);
                        TargetHost host = mock(TargetHost.class);
                        when(host.target()).thenReturn(mob);
                        hosts.add(host);
                        spots.add(at);
                    }
                }
            }
        }
    }

    /**
     * @param pos a block
     * @return true where two of the block's three axes run through a tunnel
     */
    public static boolean isAir(BlockPos pos) {
        int open = tunnel(pos.getX()) + tunnel(pos.getY()) + tunnel(pos.getZ());
        return open >= OPEN_AXES_FOR_AIR;
    }

    private static int tunnel(int coordinate) {
        return Math.floorMod(coordinate, TUNNEL_EVERY) < TUNNEL_WIDTH ? 1 : 0;
    }

    /**
     * @param pos a block
     * @return every block's shape, a whole cube
     */
    public static List<AABB> shapeOf(BlockPos pos) {
        return CUBE;
    }

    /**
     * @param pos a block
     * @return diamond where a vein stands in stone, else null
     */
    public static @Nullable Identifier oreAt(BlockPos pos) {
        boolean inVein = veinRow(pos.getX()) && veinRow(pos.getY()) && veinRow(pos.getZ());
        return inVein && !isAir(pos) ? DIAMOND : null;
    }

    private static boolean veinRow(int coordinate) {
        int along = Math.floorMod(coordinate, VEIN_EVERY);
        return along >= VEIN_FROM && along < VEIN_FROM + VEIN_WIDTH;
    }

    /**
     * The world's ore as a sense's front finds it: a section may hold ore
     * where a vein row crosses it along every axis, as a section's palette
     * answers in a level.
     */
    public static final OreSource ORES = new OreSource() {
        @Override
        public @Nullable Identifier oreAt(BlockPos pos) {
            return HeldSweepWorld.oreAt(pos);
        }

        @Override
        public boolean mayHoldOre(int sectionX, int sectionY, int sectionZ) {
            return veinRowWithin(sectionX) && veinRowWithin(sectionY) && veinRowWithin(sectionZ);
        }
    };

    private static boolean veinRowWithin(int section) {
        for (int coordinate = section * SECTION_SIZE; coordinate < (section + 1) * SECTION_SIZE; coordinate++) {
            if (veinRow(coordinate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The world's ore with each block read counted.
     *
     * @param reads counts each block read, one slot
     * @return the source
     */
    public static OreSource countedOres(int[] reads) {
        return new OreSource() {
            @Override
            public @Nullable Identifier oreAt(BlockPos pos) {
                reads[0]++;
                return ORES.oreAt(pos);
            }

            @Override
            public boolean mayHoldOre(int sectionX, int sectionY, int sectionZ) {
                return ORES.mayHoldOre(sectionX, sectionY, sectionZ);
            }
        };
    }

    /**
     * An entity scan around a caster over the world's mobs, counting the
     * mobs each scan hands its body.
     *
     * @param casterAt where the caster stands
     * @return the scan
     */
    public CountingScan scanAround(Vec3 casterAt) {
        return new CountingScan(casterAt);
    }

    /** A scan around the caster handing its body each mob within the scan's radius, counting each visit. */
    public final class CountingScan implements EntityScanHost {

        private final Vec3 casterAt;
        private int visited;

        private CountingScan(Vec3 casterAt) {
            this.casterAt = casterAt;
        }

        /** @return the mobs every scan so far handed its body */
        public int visited() {
            return visited;
        }

        @Override
        public boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters) {
            return spots.stream().anyMatch(spot -> spot.distanceTo(casterAt) <= radius);
        }

        @Override
        public void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                                        Consumer<TargetHost> body) {
            for (int mob = 0; mob < spots.size(); mob++) {
                if (spots.get(mob).distanceTo(casterAt) <= radius) {
                    visited++;
                    body.accept(hosts.get(mob));
                }
            }
        }

        @Override
        public void forEntity(int entityId, Consumer<TargetHost> body) {
            // the counts strike by scan alone
        }

        @Override
        public HostKind kind() {
            return HostKind.PLAYER;
        }

        @Override
        public BlockPos position() {
            return BlockPos.containing(casterAt);
        }

        @Override
        public void spawnParticles(ParticleBurst burst) {
            // a scan draws nothing
        }

        @Override
        public void playSound(SoundCue cue) {
            // a scan sounds nothing
        }

        @Override
        public OptionalDouble read(String name) {
            return OptionalDouble.empty();
        }
    }
}
