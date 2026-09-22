# Evergarden2

Isolated experiment for new Evergarden structures. Contains only the Evergarden and Advance Magic plugins, their configs, source art, and bundled resource packs. This repository does not deploy to a server when built.

## Build

Requires Java 25+ and Maven. Run `./build.sh` or `mvn -pl advance-magic,evergarden -am package -DskipTests` from this repository. JARs are produced in each module's `target/` directory. Pack sources are checked in; regenerate them deliberately with each module's `tools/build_packs.py`, verify the packs, and publish the changed ZIPs before updating pack URLs and SHA-1 values.

## Test isolation

Install `advance-magic/target/advance-magic.jar` and `evergarden/target/evergarden.jar` only on a separate test server. These JARs use the existing plugin names and commands, so do not install alongside their release versions. The default world name is `evergarden2`. The first experimental landmark is a block-only sky whale centered near `(0, -400)`; it can be disabled with `structures.sky-whale.enabled: false` before generating those chunks. Existing server configs are not overwritten by a JAR update; use fresh plugin data folders or review the two `src/main/resources/config.yml` files.

Java packs use pinned GitHub URLs from this repository. Bedrock packs and Geyser mappings are embedded in the JARs and installed locally when Geyser-Spigot is present. To publish new Java packs, commit the ZIPs first, update the URLs to that commit, set the matching SHA-1 hashes, and build again. Never point these configs at the moving `Afterdeath/DEV` branch.
