package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.ChargeSize;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.network.BlastShardsPayload;
import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Gametests for how each Unstable explosion shows to the players near it:
 * Blast sends vanilla's cloud, poof and smoke in its explode packet beside
 * its burnout (decisions
 * blast-explosion-plays-vanillas-cloud-poof-and-smoke-with-the-fireball,
 * blast-alone-takes-the-new-explosion-look).
 */
@RegistersGameTests
public final class ExplosionLookTests {
    private static final String BLAST_PLAYS_VANILLAS_CLOUD_POOF_AND_SMOKE = "blast_plays_vanillas_cloud_poof_and_smoke";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BLAST_PLAYS_VANILLAS_CLOUD_POOF_AND_SMOKE,
                ExplosionLookTests::blastPlaysVanillasCloudPoofAndSmoke);
    }

    private static final String REMOVAL = "removal";
    private static final Identifier BLAST = Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_blast");
    private static final int FLOOR_SPAN = 16;
    private static final int FLOOR_TOP = 2;
    private static final int BAY_HEIGHT = 5;
    /** The floor block every explosion here goes off on or over, in the middle of the bay. */
    static final BlockPos STRUCK = new BlockPos(8, FLOOR_TOP, 8);
    /** The aim point, the center of the struck block's top face. */
    private static final Vec3 AIM = new Vec3(8.5, FLOOR_TOP + 1.0, 8.5);
    /** Where the viewer stands, on the floor near the bay's edge. */
    private static final BlockPos STAND = new BlockPos(8, FLOOR_TOP + 1, 1);
    /** Below this radius vanilla calls an explosion small and shows its small particle. */
    private static final float SMALL_RADIUS = 2.0F;
    private static final String ABILITY_REQUIRED = "The ability registry must hold %s";
    private static final String ONE_EXPLOSION = "The viewer should receive one explode packet, received %d";
    private static final String VANILLA_PARTICLE = "Blast's explode packet should carry %s, carried %s";
    private static final String VANILLA_BLOCK_PARTICLES = "Blast's block particles should be poof and smoke, were %s";
    private static final String SMOKE_INSIDE_THE_REACH =
            "Blast's explode packet should spread its smoke within its %.1f block reach, carried radius %.2f";
    private static final String GENERIC_EXPLODE ="The explode packet should carry vanilla's generic explode sound";
    private static final String ONE_UNSTABLE_BURNOUT = "The viewer should receive one Unstable burnout, received %s";

    private ExplosionLookTests() {
    }

    /**
     * Blast cast into a stone floor sends the viewer one explode packet
     * carrying vanilla's explosion particle for its radius, poof and smoke as
     * its block particles and vanilla's boom, and one Unstable burnout.
     *
     * @param helper the gametest helper
     */
    public static void blastPlaysVanillasCloudPoofAndSmoke(GameTestHelper helper) {
        stoneFloor(helper);
        PayloadKindsRecorder viewer = standViewer(helper);

        castBlast(helper, ChargeSize.REFERENCE_RADIUS);

        ClientboundExplodePacket explosion = onlyExplosion(helper, viewer);
        ParticleType<?> expected = explosion.radius() < SMALL_RADIUS
                ? ParticleTypes.EXPLOSION : ParticleTypes.EXPLOSION_EMITTER;
        helper.assertTrue(explosion.explosionParticle().getType() == expected,
                String.format(VANILLA_PARTICLE, BuiltInRegistries.PARTICLE_TYPE.getKey(expected),
                        BuiltInRegistries.PARTICLE_TYPE.getKey(explosion.explosionParticle().getType())));
        Set<Identifier> blockParticles = blockParticleTypes(explosion);
        helper.assertTrue(blockParticles.equals(Set.of(BuiltInRegistries.PARTICLE_TYPE.getKey(ParticleTypes.POOF),
                        BuiltInRegistries.PARTICLE_TYPE.getKey(ParticleTypes.SMOKE))),
                String.format(VANILLA_BLOCK_PARTICLES, blockParticles));
        assertGenericExplode(helper, explosion);
        // the client spreads poof and smoke over the packet's radius (ClientExplosionTracker.addParticle)
        helper.assertTrue(explosion.radius() <= ChargeSize.REFERENCE_RADIUS,
                String.format(SMOKE_INSIDE_THE_REACH, ChargeSize.REFERENCE_RADIUS, explosion.radius()));
        assertOneUnstableBurnout(helper, viewer);
        removeViewer(helper, viewer);
        helper.succeed();
    }

    private static void assertOneUnstableBurnout(GameTestHelper helper, PayloadKindsRecorder viewer) {
        AABB bay = helper.getBounds();
        List<ChainBurnoutPayload> burnouts = viewer.receivedOf(ChainBurnoutPayload.class).stream()
                .filter(burnout -> bay.contains(Vec3.atCenterOf(burnout.pos()))).toList();
        helper.assertTrue(burnouts.size() == 1
                        && burnouts.getFirst().gooTypeId().equals(GooTypes.id(GooTypes.UNSTABLE)),
                String.format(ONE_UNSTABLE_BURNOUT, burnouts));
    }

    /**
     * Shards payloads naming the struck block, which this test's explosion
     * breaks; a neighboring test's Blast reaches the viewer and the bay's
     * border too.
     *
     * @param helper the gametest helper
     * @param viewer the viewer's recording connection
     * @return the shards payloads of this test's explosion
     */
    static List<BlastShardsPayload> shardsOfTheStruckBlock(GameTestHelper helper, PayloadKindsRecorder viewer) {
        BlockPos struck = helper.absolutePos(STRUCK);
        return viewer.receivedOf(BlastShardsPayload.class).stream()
                .filter(shards -> shards.cells().stream().anyMatch(cell -> cell.pos().equals(struck)))
                .toList();
    }

    /**
     * Casts Blast at the struck block at a size, as a charged release lands it.
     *
     * @param helper the gametest helper
     * @param size   the cast's size in blocks
     */
    static void castBlast(GameTestHelper helper, double size) {
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(STRUCK), GooTypes.UNSTABLE, Direction.UP,
                ability(helper, BLAST), helper.absoluteVec(AIM), size);
    }

    /**
     * Stands a creative mock player at the bay's edge, its connection
     * recording explode packets and burnouts, tracking the bay's chunks so a
     * burnout sent to the chunk's watchers reaches it.
     *
     * @param helper the gametest helper
     * @return the recorder, the viewer's connection
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    static PayloadKindsRecorder standViewer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(STAND));
        player.setPos(stand.x, stand.y, stand.z);
        // a mock player tracks no chunk, so the burnout's chunk watchers would skip it without this view
        player.setChunkTrackingView(ChunkTrackingView.of(ChunkPos.containing(helper.absolutePos(STRUCK)), 1));
        return PayloadKindsRecorder.attachTo(player, Set.of(ChainBurnoutPayload.class, BlastShardsPayload.class));
    }

    /**
     * Takes the viewer out of the server's player list, so no later test's packets reach it.
     *
     * @param helper the gametest helper
     * @param viewer the viewer's recording connection
     */
    static void removeViewer(GameTestHelper helper, PayloadKindsRecorder viewer) {
        helper.getLevel().getServer().getPlayerList().remove(viewer.player);
    }

    /**
     * The one explode packet centered in this test's bay; a packet reaches 64
     * blocks, so a neighboring test's explosion reaches the viewer too.
     *
     * @param helper the gametest helper
     * @param viewer the viewer's recording connection
     * @return the bay's explode packet
     */
    static ClientboundExplodePacket onlyExplosion(GameTestHelper helper, PayloadKindsRecorder viewer) {
        AABB bay = helper.getBounds();
        List<ClientboundExplodePacket> explosions = viewer.explosions().stream()
                .filter(explosion -> bay.contains(explosion.center())).toList();
        helper.assertTrue(explosions.size() == 1, String.format(ONE_EXPLOSION, explosions.size()));
        return explosions.getFirst();
    }

    private static void assertGenericExplode(GameTestHelper helper, ClientboundExplodePacket explosion) {
        helper.assertTrue(explosion.explosionSound().value() == SoundEvents.GENERIC_EXPLODE.value(), GENERIC_EXPLODE);
    }

    private static Set<Identifier> blockParticleTypes(ClientboundExplodePacket explosion) {
        return explosion.blockParticles().unwrap().stream().map(Weighted::value)
                .map(ExplosionParticleInfo::particle)
                .map(particle -> BuiltInRegistries.PARTICLE_TYPE.getKey(particle.getType()))
                .collect(Collectors.toSet());
    }

    /**
     * Lays a stone floor under open air across the bay.
     *
     * @param helper the gametest helper
     */
    static void stoneFloor(GameTestHelper helper) {
        BlockPos.betweenClosed(0, 0, 0, FLOOR_SPAN - 1, BAY_HEIGHT - 1, FLOOR_SPAN - 1).forEach(cell ->
                helper.setBlock(cell, cell.getY() <= FLOOR_TOP ? Blocks.STONE : Blocks.AIR));
    }

    private static AbilityDefinition ability(GameTestHelper helper, Identifier id) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(id);
        helper.assertTrue(ability != null, String.format(ABILITY_REQUIRED, id));
        return ability;
    }
}
