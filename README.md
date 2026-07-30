# Skulk Scythe (Fabric mod for Minecraft 1.21.11)

Adds one item: the **Skulk Scythe** — a big skulk-covered scythe with four abilities.

- **`/sculk` command**: run it in-game to get a Skulk Scythe instantly, no
  crafting or `/give` syntax needed.
- **Ambient sculk aura**: while you're holding the scythe (either hand), a
  swirling ring of sculk-soul particles orbits around you continuously, with
  occasional crackling sparks and a faint ambient hum — gets brighter and more
  restless once the warden-summon bar is full.
- **Right-click and hold**: charges up a Warden-style **sonic boom**, fires when you
  release. Longer charge = more damage (6→16), range (10→20 blocks), and knockback.
  Goes through blocks, just like the Warden's real attack. Two counter-rotating
  particle rings swirl while charging, with a rising-pitch sculk-sensor "charging
  click" that speeds up and gets more urgent the longer you hold it, plus heartbeat
  pulses at 25/50/75% and a roar at full charge. Releasing fires a ring burst at
  your feet plus a crackling beam of sonic boom + spark particles.
- **Sneak + Right-click**: **dash** forward in a burst of sculk-soul and spark
  particles. Uses `setVelocity` directly and never calls `addExhaustion`, so it
  costs **zero hunger**. 1.25 second cooldown.
- **Melee hit**: shrieks like a Sculk Shrieker and slaps **Darkness** on whatever
  you hit, plus a burst of sculk-soul particles.
- **Echolocation** (default key **R**, while holding the scythe): pings outward and
  applies **Glowing** to every living thing within 24 blocks — through walls. Sends
  out three expanding particle rings and a vertical pulse, with a "you sense N
  creatures" message. 15s cooldown.
- **Warden-summon bar + cinematic ritual**: hitting things — melee or sonic boom —
  fills a charge bar that renders right under the item icon (bundle-style, fading
  teal → purple). Once full, **Sneak + Right-click starts a ~4 second summoning
  ritual**: a circle grows on the ground, chain-like particles rise and clink
  around its rim, the earth cracks, everything goes quiet for a beat — then a
  flash, a roar, and the Warden itself appears. It fights *for* you: it won't
  target you, and it'll turn on whatever last attacked you, like a bodyguard.
  (Friendliness is a lightweight approximation, not full vanilla taming — see
  `FriendlyWardenTracker.java`. The staged ritual itself lives in
  `WardenSummonSequence.java` if you want to retime or restyle any stage.)
- **Cooldown HUD**: while holding the scythe, three small icons appear on the
  left side of the screen — the scythe (sonic boom), a feather (dash), and a
  sculk sensor (echolocation) — each with a dark overlay that shrinks away and
  a countdown number as the ability comes off cooldown.
- Renders noticeably **larger than a normal tool** in your hand (2–2.4x scale via
  the item model's display transforms).
- Craftable with an echo shard, netherite ingot, and sculk (recipe included), or
  give it to yourself with `/sculk` or `/give @s skulkscythe:skulk_scythe`.

## Version notes

This targets **Minecraft 1.21.11** — the last version before Mojang removed
code obfuscation entirely (starting at 26.1) and Fabric retired Yarn mappings.
1.21.11 is a normal, standard Fabric setup: Yarn `1.21.11+build.4`, Fabric
Loader `0.18.4`, Fabric API `0.141.4+1.21.11`, Fabric Loom `1.14-SNAPSHOT`,
and Java 21.

Getting this building took a few rounds against real compile errors from an
actual CI run, so — unlike earlier drafts of this README — the API names below
are **confirmed against the live yarn-1.21.11+build.4 javadocs**, not guesses:

- `Entity#getWorld()` → `Entity#getEntityWorld()` (renamed in 1.21.9)
- `Entity#getPos()` → `Entity#getEntityPos()` (renamed for 1.21.11)
- `TypedActionResult<ItemStack>` → plain `ActionResult` (merged in 1.21.2 —
  `Item#use()` now returns `ActionResult` directly, no generic; values are
  `ActionResult.SUCCESS` / `.CONSUME` / `.FAIL` / `.PASS`)
- `UseAction` lives in `net.minecraft.util`, not `net.minecraft.item`
- `LivingEntity#damage(...)` now takes a leading `ServerWorld` param:
  `damage(ServerWorld, DamageSource, float)`, and returns `boolean`
- `Entity#velocityModified` → `Entity#velocityDirty`
- `World#isClient` is a **method** (`world.isClient()`), not a public field
- `Item#postHit` now returns `void` (not `boolean`); `Item#onStoppedUsing` now
  returns `boolean` (not `void`) — opposite of older tutorials, easy to get
  backwards
- `KeyBinding` now needs a `KeyBinding.Category` object (created via
  `KeyBinding.Category.create(Identifier)`) as its 4th constructor arg,
  instead of a raw translation-key string
- `World#addParticle` only exists on `ClientWorld`/`ServerWorld`, not the base
  `World` class — this is why all client-only particle effects (charge swirl,
  ambient aura) live in `SkulkScytheModClient.java` rather than in the shared
  `SkulkScytheItem.java`. Referencing a client-only type like `ClientWorld`
  from a class that's also loaded on a dedicated server will crash that
  server at class-verification time, even inside an `isClient()` check — so
  keep client-only visuals in the client entrypoint.

I deliberately avoided vanilla's per-item `ItemCooldownManager` for the three
abilities (it keys cooldowns by Item, so sonic boom/dash/echolocation would've
all shared one cooldown) and rolled a tiny custom cooldown tracker in
`AbilityCooldowns.java` instead.

## Project layout

```
skulkscythe/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── src/main/java/com/skulkscythe/mod/
│   ├── SkulkScytheMod.java          <- registers item, data component, networking
│   ├── SkulkScytheModClient.java    <- echolocation keybind (client only)
│   ├── item/SkulkScytheItem.java    <- charging, dash/summon, shriek, bar rendering
│   ├── ability/SkulkAbilities.java  <- shared ability logic (called from item + network)
│   ├── ability/FriendlyWardenTracker.java  <- keeps summoned Wardens off their owner
│   ├── ability/WardenSummonSequence.java   <- staged cinematic ritual (circle/chains/flash/reveal)
│   ├── network/EcholocationPayload.java
│   └── util/AbilityCooldowns.java   <- independent per-ability cooldowns, also feeds the HUD
├── .github/workflows/build.yml      <- builds the jar on GitHub's servers
└── src/main/resources/
    ├── fabric.mod.json
    ├── assets/skulkscythe/
    │   ├── icon.png
    │   ├── lang/en_us.json
    │   ├── models/item/skulk_scythe.json   <- the "hold it big" transforms
    │   └── textures/item/skulk_scythe.png  <- placeholder pixel art texture
    └── data/skulkscythe/recipe/skulk_scythe.json
```

## Building

There are two ways to get a jar:

### Option A — GitHub Actions (no local install needed)

1. Create a new repo on GitHub and push everything in this folder to it, e.g.:
   ```
   cd skulkscythe
   git init
   git add .
   git commit -m "Skulk Scythe mod"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
   git push -u origin main
   ```
2. GitHub will automatically run `.github/workflows/build.yml`, which builds the
   mod on GitHub's servers (Java 21 + Gradle 9.5.1, no wrapper needed).
3. Go to the repo's **Actions** tab → the latest run → scroll down to
   **Artifacts** → download `skulkscythe-mod-jar`. That zip contains the built
   `.jar`.
4. If a build ever fails, click into the run and read the log — paste it back
   to me and I'll patch whatever needs it.

### Option B — Build it locally

1. Install **Java 21** (required by 1.21.11) and **Gradle 9.5.1+**
   (via [gradle.org](https://gradle.org/install/), SDKMAN, or Homebrew —
   `sdk install gradle 9.5.1` or `brew install gradle`).

   Note: this project doesn't ship a `gradlew` wrapper jar (it's a binary
   normally downloaded from Gradle's servers), so use your system-installed
   `gradle` command instead of `./gradlew`.

2. From the `skulkscythe/` folder:
   ```
   gradle build
   ```
   (First run downloads Minecraft, mappings, Loom, etc. — needs internet
   and takes a few minutes.)
3. The finished mod jar shows up in `build/libs/skulkscythe-1.0.0.jar`.
4. Install **Fabric Loader 0.18.4+** and **Fabric API 0.141.4+1.21.11 or newer**
   for Minecraft 1.21.11, then drop the built jar into your `.minecraft/mods`
   folder alongside Fabric API.

## Customizing

- **Texture**: `textures/item/skulk_scythe.png` is a 16×16 placeholder I drew
  in code (dark blade, cyan skulk-sensor speckles, gray handle). Open it in
  any pixel editor (or Blockbench) to make it look how you want — the model
  will pick up whatever you save there.
- **Size**: tweak the `scale` arrays in `models/item/skulk_scythe.json`
  (currently ~2x normal tool size in third person, ~2.4x in first person).
- **Sonic boom damage/range/cooldown** and **dash strength/cooldown**: all are
  named constants at the top of `SkulkScytheItem.java`.
- **Recipe**: edit `data/skulkscythe/recipe/skulk_scythe.json`, or delete it
  and just `/give` yourself the item instead.
