# Goo

A Minecraft mod for NeoForge 26.1. Melt items down into typed goo, store it, move it around, and throw it at things.

## What it does

Every item in the game has a goo breakdown across 15 types: Metal, Crystal, Leaf, Vital, Shroom, Rock, Blaze, Frost, Typhoon, Glow, Hex, Pulse, Nether, Ender, and Aeon.

Toss items into a **Crucible** to melt them into goo. Store it in **Canisters** and **Vats**. Move it between machines with **Gasket** networks. Feed goo back into a **Plexer** to reconstitute items.

Each goo type has **three effects**: World, Mob, and Brew. Equip a **Glove**, pick a type, and throw blobs at blocks and mobs to trigger them. Brew goo potions at a vanilla brewing stand.

## Building

Requires JDK 25.

```bash
./gradlew build
```

## Authors

MercuriusXeno and gabb.png.

## License

Goo is All Rights Reserved. Copyright (c) 2026 MercuriusXeno. See [LICENSE](LICENSE).

### Third-party

The shipped mod jar bundles none of the dependencies below: NeoForge is provided by the loader at runtime, and every other entry is used only to test or analyze the build.

| Dependency | Version | Used for | License |
| --- | --- | --- | --- |
| NeoForge | 26.1.0.1-beta | Mod loader, provided at runtime | LGPL-2.1 |
| JUnit Jupiter | 5.11.4 | Tests | EPL-2.0 |
| JUnit Platform Launcher | 1.13.4 | Tests | EPL-2.0 |
| Mockito (mockito-core, mockito-junit-jupiter) | 5.23.0 | Tests | MIT |
| Instancio (instancio-junit) | 5.4.0 | Tests | Apache-2.0 |
| ArchUnit (archunit-junit5) | 1.4.1 | Tests; bundles ASM under BSD-3-Clause | Apache-2.0 |
| sb-contrib | 7.6.8 | SpotBugs plugin, static analysis | LGPL-2.1 |
| Find Security Bugs (findsecbugs-plugin) | 1.13.0 | SpotBugs plugin, static analysis | LGPL |

