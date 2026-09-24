package com.mercuriusxeno.goo.gametest;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.validation.ContentValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Parses the tracked Goo Lab template at {@code dev/lab/} through the game's
 * own save loader, so the template is proven to open as a superflat, creative,
 * cheats-on world in the running version (decision lab-built-from-code). The
 * gametest run passes the template's path as the {@link #TEMPLATE_PROPERTY}
 * system property; the test reads a temporary copy, since opening a save
 * writes a session lock into it.
 */
public final class LabTemplateTests {

    /**
     * System property the gameTestServer run sets to the template folder.
     */
    public static final String TEMPLATE_PROPERTY = "goo.labTemplate";
    private static final String LEVEL_NAME = "Goo Lab";
    private static final String TEMP_PREFIX = "goo-lab-template";
    private static final String NO_TEMPLATE = "System property goo.labTemplate names no folder";
    private static final String WRONG_NAME = "Template should name its world Goo Lab";
    private static final String NOT_CREATIVE = "Template should open in creative";
    private static final String NO_CHEATS = "Template should allow commands";
    private static final String NEEDS_FIXING = "Template should be at the running data version, needing no upgrade";
    private static final String EXPERIMENTAL = "Template should open without the experimental settings warning";
    private static final String GEN_UNREADABLE = "World gen settings should parse: ";
    private static final String NOT_FLAT = "Overworld should be superflat";
    private static final String UNSTABLE = "Every dimension should read stable";
    private static final String STRUCTURES = "Template should generate no structures";
    private static final String RULES_UNREADABLE = "Game rules should parse: ";
    private static final String TIME_ADVANCES = "Template should lock daylight";
    private static final String MOBS_SPAWN = "Template should hold natural mob spawning off";
    private static final String NO_ERROR = "";
    private static final String IO_FAILED = "Template copy failed: ";

    private LabTemplateTests() {
    }

    /**
     * Opens a copy of the template and asserts its summary, world gen settings and game rules.
     *
     * @param helper the gametest helper
     */
    public static void templateLoads(GameTestHelper helper) {
        String template = System.getProperty(TEMPLATE_PROPERTY);
        helper.assertTrue(template != null && Files.isDirectory(Path.of(template)), NO_TEMPLATE);
        try {
            Path base = Files.createTempDirectory(TEMP_PREFIX);
            copyTree(Path.of(template), base.resolve(LEVEL_NAME));
            try (LevelStorageSource.LevelStorageAccess access =
                         LevelStorageSource.createDefault(base).validateAndCreateAccess(LEVEL_NAME)) {
                assertSummary(helper, access.fixAndGetSummary());
                assertWorldGen(helper, access, helper.getLevel().registryAccess());
                assertGameRules(helper, access, helper.getLevel().registryAccess());
            } finally {
                deleteTree(base);
            }
        } catch (IOException | ContentValidationException e) {
            helper.fail(IO_FAILED + e.getMessage());
        }
        helper.succeed();
    }

    /**
     * Asserts the world list would show the template as a creative, cheats-on, current world.
     *
     * @param helper  the gametest helper
     * @param summary the template's level summary
     */
    private static void assertSummary(GameTestHelper helper, LevelSummary summary) {
        helper.assertTrue(LEVEL_NAME.equals(summary.getLevelName()), WRONG_NAME);
        helper.assertTrue(summary.getGameMode() == GameType.CREATIVE, NOT_CREATIVE);
        helper.assertTrue(summary.hasCommands(), NO_CHEATS);
        helper.assertFalse(summary.requiresFileFixing() || summary.requiresManualConversion(), NEEDS_FIXING);
        helper.assertFalse(summary.isExperimental(), EXPERIMENTAL);
    }

    /**
     * Asserts the world gen settings bake into a stable world with a flat overworld.
     *
     * @param helper   the gametest helper
     * @param access   the open template
     * @param registry the running server's registries
     */
    private static void assertWorldGen(GameTestHelper helper, LevelStorageSource.LevelStorageAccess access,
                                       RegistryAccess registry) {
        DataResult<WorldGenSettings> read = LevelStorageSource.readExistingSavedData(access, registry, WorldGenSettings.TYPE);
        helper.assertTrue(read.isSuccess(), GEN_UNREADABLE + read.error().map(Object::toString).orElse(NO_ERROR));
        WorldGenSettings settings = read.getOrThrow();
        MappedRegistry<LevelStem> noDatapackDimensions = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable());
        WorldDimensions.Complete baked = settings.dimensions().bake(noDatapackDimensions.freeze());
        LevelStem overworld = baked.dimensions().getValue(LevelStem.OVERWORLD);
        helper.assertTrue(overworld != null && overworld.generator() instanceof FlatLevelSource, NOT_FLAT);
        helper.assertTrue(baked.lifecycle().equals(Lifecycle.stable()), UNSTABLE);
        helper.assertFalse(settings.options().generateStructures(), STRUCTURES);
    }

    /**
     * Asserts the game rules lock daylight and hold natural spawning off.
     *
     * @param helper   the gametest helper
     * @param access   the open template
     * @param registry the running server's registries
     */
    private static void assertGameRules(GameTestHelper helper, LevelStorageSource.LevelStorageAccess access,
                                        RegistryAccess registry) {
        DataResult<GameRuleMap> read = LevelStorageSource.readExistingSavedData(access, registry, GameRuleMap.TYPE);
        helper.assertTrue(read.isSuccess(), RULES_UNREADABLE + read.error().map(Object::toString).orElse(NO_ERROR));
        GameRuleMap rules = read.getOrThrow();
        helper.assertTrue(Boolean.FALSE.equals(rules.get(GameRules.ADVANCE_TIME)), TIME_ADVANCES);
        helper.assertTrue(Boolean.FALSE.equals(rules.get(GameRules.SPAWN_MOBS)), MOBS_SPAWN);
    }

    /**
     * Copies a folder tree.
     *
     * @param from the source folder
     * @param to   the destination folder, created
     * @throws IOException when a file cannot be copied
     */
    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path source : paths.toList()) {
                Files.copy(source, to.resolve(from.relativize(source).toString()));
            }
        }
    }

    /**
     * Deletes a folder tree, deepest paths first.
     *
     * @param root the folder to delete
     * @throws IOException when a path cannot be deleted
     */
    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
