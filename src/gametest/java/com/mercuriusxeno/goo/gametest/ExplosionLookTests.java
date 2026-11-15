package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.ChargeSize;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooParticles;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
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
 * its burnout, while Lurker's mine and the Lurker Ignite sets off send the
 * silent look their burnout draws over (decisions
 * blast-explosion-plays-vanillas-cloud-poof-and-smoke-with-the-fireball,
 * blast-alone-takes-the-new-explosion-look).
 */
@RegistersGameTests
public final class ExplosionLookTests {
    private static final String BLAST_PLAYS_VANILLAS_CLOUD_POOF_AND_SMOKE = "blast_plays_vanillas_cloud_poof_and_smoke";
    private static final String LURKER_MINE_PLAYS_THE_SILENT_LOOK = "lurker_mine_plays_the_silent_look";
    private static final String IGNITE_SET_OFF_PLAYS_THE_SILENT_LOOK = "ignite_set_off_plays_the_silent_look";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BLAST_PLAYS_VANILLAS_CLOUD_POOF_AND_SMOKE,
                ExplosionLookTests::blastPlaysVanillasCloudPoofAndSmoke);
        GooTestFunctions.registerTest(registrar, LURKER_MINE_PLAYS_THE_SILENT_LOOK,
                ExplosionLookTests::lurkerMinePlaysTheSilentLook);
        GooTestFunctions.registerTest(registrar, IGNITE_SET_OFF_PLAYS_THE_SILENT_LOOK,
                ExplosionLookTests::igniteSetOffPlaysTheSilentLook);
    }

    private static final String REMOVAL = "removal";
    private static final Identifier BLAST = Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_blast");
    private static final Identifier LURKER = Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_lurker");
    private static final Identifier IGNITE = Identifier.fromNamespaceAndPath(Goo.MODID, "blaze_ignite");
    private static final int FLOOR_SPAN = 16;
    private static final int FLOOR_TOP = 2;
    private static final int BAY_HEIGHT = 5;
    /** The floor block every explosion here goes off on or over, in the middle of the bay. */
    static final BlockPos STRUCK = new BlockPos(8, FLOOR_TOP, 8);
    /** The cell over the struck block, where a blob landing on it lands. */
    private static final BlockPos OVER_THE_STRUCK = STRUCK.above();
    /** The aim point, the center of the struck block's top face. */
    private static final Vec3 AIM = new Vec3(8.5, FLOOR_TOP + 1.0, 8.5);
    /** Where the viewer stands, on the floor near the bay's edge. */
    private static final BlockPos STAND = new BlockPos(8, FLOOR_TOP + 1, 1);
    /** Below this radius vanilla calls an explosion small and shows its small particle. */
    private static final float SMALL_RADIUS = 2.0F;
    /** The ticks after the landing by which the Ignite set-off has gone off. */
    private static final int SETTLE_TICKS = 1;
    private static final String ABILITY_REQUIRED = "The ability registry must hold %s";
    private static final String ONE_EXPLOSION = "The viewer should receive one explode packet, received %d";
    private static final String VANILLA_PARTICLE = "Blast's explode packet should carry %s, carried %s";
    private static final String VANILLA_BLOCK_PARTICLES = "Blast's block particles should be poof and smoke, were %s";
    private static final String GENERIC_EXPLODE = "The explode packet should carry vanilla's generic explode sound";
    private static final String ONE_UNSTABLE_BURNOUT = "The viewer should receive one Unstable burnout, received %s";
    private static final String SILENT_PARTICLE = "The explode packet should carry the silent blast, carried %s";
    private static final String NO_BLOCK_PARTICLES = "The explode packet should carry no block particles, carried %s";
    private static final String SHOULD_STAND_LURKER = "The lurker should stand on the floor before it goes off";
    private static final String SHOULD_GO_OFF = "The lurker should go off with the zombie beside it";

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
        AABB bay = helper.getBounds();
        List<ChainBurnoutPayload> burnouts = viewer.receivedOf(ChainBurnoutPayload.class).stream()
                .filter(burnout -> bay.contains(Vec3.atCenterOf(burnout.pos()))).toList();
        helper.assertTrue(burnouts.size() == 1
                        && burnouts.getFirst().gooTypeId().equals(GooTypes.id(GooTypes.UNSTABLE)),
                String.format(ONE_UNSTABLE_BURNOUT, burnouts));
        removeViewer(helper, viewer);
        helper.succeed();
    }

    /**
     * Lurker standing on the floor, set off by a zombie standing beside it,
     * sends the viewer one explode packet with the silent look.
     *
     * @param helper the gametest helper
     */
    public static void lurkerMinePlaysTheSilentLook(GameTestHelper helper) {
        stoneFloor(helper);
        discardLeftoverEntities(helper);
        PayloadKindsRecorder viewer = standViewer(helper);
        standLurker(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, OVER_THE_STRUCK.east());
        zombie.setInvulnerable(true);
        helper.succeedWhen(() -> {
            helper.assertFalse(helper.getBlockState(OVER_THE_STRUCK).is(GooBlocks.ABILITY_BLOCK.get()),
                    SHOULD_GO_OFF);
            assertSilentLook(helper, onlyExplosion(helper, viewer));
            zombie.discard();
            removeViewer(helper, viewer);
        });
    }

    /**
     * Ignite landing on a Lurker standing on the floor sets it off, and the
     * fiery explosion sends the viewer one explode packet with the silent look.
     *
     * @param helper the gametest helper
     */
    public static void igniteSetOffPlaysTheSilentLook(GameTestHelper helper) {
        stoneFloor(helper);
        PayloadKindsRecorder viewer = standViewer(helper);
        standLurker(helper);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(OVER_THE_STRUCK), GooTypes.BLAZE, Direction.UP,
                ability(helper, IGNITE));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            assertSilentLook(helper, onlyExplosion(helper, viewer));
            removeViewer(helper, viewer);
            helper.succeed();
        });
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

    private static void standLurker(GameTestHelper helper) {
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(STRUCK), GooTypes.UNSTABLE, Direction.UP,
                ability(helper, LURKER));
        helper.assertTrue(helper.getBlockState(OVER_THE_STRUCK).is(GooBlocks.ABILITY_BLOCK.get()),
                SHOULD_STAND_LURKER);
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
        return PayloadKindsRecorder.attachTo(player, Set.of(ChainBurnoutPayload.class));
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

    private static void assertSilentLook(GameTestHelper helper, ClientboundExplodePacket explosion) {
        helper.assertTrue(explosion.explosionParticle().getType() == GooParticles.SILENT_BLAST.get(),
                String.format(SILENT_PARTICLE, BuiltInRegistries.PARTICLE_TYPE.getKey(
                        explosion.explosionParticle().getType())));
        Set<Identifier> blockParticles = blockParticleTypes(explosion);
        helper.assertTrue(blockParticles.isEmpty(), String.format(NO_BLOCK_PARTICLES, blockParticles));
        assertGenericExplode(helper, explosion);
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

    private static void discardLeftoverEntities(GameTestHelper helper) {
        AABB bay = new AABB(helper.absolutePos(STRUCK)).inflate(FLOOR_SPAN);
        helper.getLevel().getEntities((Entity) null, bay, entity -> !(entity instanceof Player))
                .forEach(Entity::discard);
    }

    private static AbilityDefinition ability(GameTestHelper helper, Identifier id) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(id);
        helper.assertTrue(ability != null, String.format(ABILITY_REQUIRED, id));
        return ability;
    }
}
