# JVDiablo 2

A Diablo II port for Android written **from scratch in Java**: its own engine, its own file-format decoders, its own software renderer. No third-party game engine and no external libraries for Diablo II formats.

> **Status: beta.** A lot is still missing, but you can already create a hero, enter the Rogue Encampment and walk around the map.

- App package: `com.jv.diablo2`
- Language: Java (100 source files, ~420 KB)
- Platform: Android (a single `Activity`)

---

## What is this

JVDiablo 2 reads the **original game data** (`.mpq` archives), parses it with its own decoders and draws the game in software, pixel by pixel, into its own frame buffer. The whole pipeline lives in this project:

```
MPQ archives → decompress/decrypt → DC6 / DCC / DS1 / DT1 / COF / TXT → game logic → frame buffer → screen
```

> ⚠️ Game files are **not included**. You need your own copy of Diablo II / Lord of Destruction.

## What works

- Mounting MPQ archives from the APK `assets`, from a user-selected folder (SAF) or from a regular folder
- Trademark screen, main menu, loading screen (door animation from `loadingscreen.dc6`)
- Character select (up to 8 slots) and character creation ("Select Hero Class" with all seven classes)
- 640×480 game frame: the **Rogue Encampment** built from DS1 + DT1, the hero as a layered sprite (COF/DCC), NPCs and objects from DS1
- **Walking around the map** by tap/click: A\* pathfinding, walk/run, camera
- Bottom panel built from the original DC6 files: life and mana orbs, run button, stamina bar, belt, mini panel
- Monsters with AI (idle, chase via A\*, melee), combat, experience, loot drops
- Items: generation (quality, affixes, uniques), inventory, equipment (the hero's look changes with equipment)
- Skills: loaded from `Skills.txt`, formula evaluator, skill tree
- Saves in a custom `.jvd2` format
- Utility tools: resource viewer, map viewer, test arena, diagnostics

## What is missing

- In-game panels (inventory, skills, character, etc.) are not ported to the new engine yet; tapping them shows a notice
- Only the starting location is wired up; other levels and acts are not
- NPC dialogs; quests (a quest log exists, but quests only complete by killing the boss)
- Sound: only uncompressed PCM WAV plays; WAV files compressed inside MPQ (Huffman/ADPCM) cannot be read yet
- Set items, crafted items, sockets, class-specific item bonuses
- Monster scaling for difficulties (Normal only for now)
- Importing real `.d2s` saves
- Battle.net, multiplayer, cinematics, credits (buttons are in the menu but inactive)

---

## Project structure

Sources are in `diablo2/` (package `com.jv.diablo2`).

### Package root: Android and screens

| File | Purpose |
|---|---|
| `MainActivity` | The only `Activity`: game surface, MPQ mounting, dialogs, report sharing |
| `GameView` | Game loop: fixed **25 ticks/s** (like the original) plus rendering |
| `Scene`, `Nav` | Screen interface and navigation between screens |
| `Screen800`, `FbScene` | Scene bases: 800×600 virtual screen (menus) and software-rendered 640×480 frame (game) |
| `DataMounter` | MPQ mounting: APK assets → SAF folder → regular folder |
| `Session` | Shared app state: mounted game data and loaded tables |
| `Res` | Resource lookup and cache in MPQ, with a report of what was found for diagnostics |
| `Saves` | Saved heroes: `files/save/<name>.jvd2` |
| `Ui`, `AudioPlayer` | UI drawing with the game's own fonts; WAV playback |
| `LevelLoader` | Level loading: DS1 + DT1 → game frame |

### Screen flow

```
BootScene → TrademarkScene → MainMenuScene → CharSelectScene → CharCreateScene
                                  │                 │
                                  │                 └→ LoadingScene → GameScene
                                  └→ ToolsScene → ViewerScene / MapScene / SandboxScene / DiagScene
```

### Subpackages

| Package | Contents |
|---|---|
| `mpq/` | MPQ support: `MpqArchive` (format v0), `MpqCrypto` (encryption and name hashing), `Pkware` (PKWARE DCL "implode" decompression, rewritten in Java), `MpqFileSystem` (virtual file system over all archives; original priority: patch > expansion (LoD) > base archives) |
| `io/` | Positional reading so 250+ MB archives are never loaded into memory: `RandomAccessSource`, `ChannelSource`, `MemorySource` |
| `gfx/` | Format decoders: `Dc6` (interface, items), `Dcc` (hero/monster animations), `Ds1` (level layout), `Dt1` (tiles), `Cof` (composite animation descriptions), `AnimData`, `Palette`, `D2Font`, `StringTbl` |
| `data/` | Access to game tables (`data\global\excel\*.txt`): `TxtTable`, `GameData`, `LevelDb` |
| `game/` | Game logic: world, hero, monster AI, A\*, combat, experience, quests, saves, treasure classes, an original-style random number generator |
| `game/item/` | Items: bases, types, affixes, properties, uniques, generator, inventory |
| `game/skill/` | Skills: `SkillDb`, `SkillTree`, `Calc` (evaluator for formulas from Skills.txt) |
| `render/` | Software renderer: `Fb` (frame buffer), `GameFrame` (game frame), `LevelRenderer`, `Hud`, `Paperdoll`, `LookBuilder`, `MonsterActor`, `Text`, `Assets`, `DirCalib` |
| `tools/` | `SamplePack` bundles a small zip of sample files for format verification instead of sending whole archives |

### Key ideas

- **All rendering code is pure Java with no Android dependency.** The game composes the picture in `Fb` (an ARGB array); Android only copies the buffer into a `Bitmap` and scales it to the screen without smoothing, keeping the 4:3 aspect ratio. That is why `render/` and `game/` can run on a plain JVM and be tested by saving frames as PNG.
- **All Diablo II sprites are palette indices**, converted to colors only at draw time.
- **As in the original:** 25 ticks per second, a sub-tile is 32×16 pixels, a tile of 5×5 sub-tiles is a 160×80 diamond.
- **Data comes from the original tables** (`MonStats`, `Skills`, `TreasureClass`, `Armor`/`Weapons`, `Experience`, and others) rather than being hard-coded, which leaves room for mods.
- **Level draw order:** floors → shadows → walls/pillars/trees interleaved with units by depth (x + y) → roofs.

---

## Game data

The app looks for `.mpq` files in this order:

1. The APK `assets` (folders `/`, `d2`, `data`, `mpq`), read without copying
2. A previously selected folder (Storage Access Framework)
3. `/Android/data/com.jv.diablo2/files`

Archives used: `d2data`, `d2char`, `d2sfx`, `d2music`, `d2speech`, `d2video`, `d2exp`, `d2xmusic`, `d2xtalk`, `d2xvideo`, `patch_d2`.

The folder can be chosen from the tools menu (bottom-left corner of the main menu).

## Utility tools

- **Resource viewer**: DC6 images (tap the preview to flip frames) and WAV sounds (tap to play)
- **Map viewer**: list of DS1 files → DT1 floor in isometric view *(experimental)*
- **Sandbox**: test arena with combat, experience, drops, item pickup, inventory and skill tree
- **Diagnostics**: which resources were found and where; the report and the MPQ file list are sent with one button

## Dependencies

Besides the Android SDK, the project uses `androidx.core` (`FileProvider`) and `androidx.documentfile`. The archive contains sources only, with no Gradle files.

## Controls

Tap/click the ground to walk there. Tap an item to pick it up. The run button on the panel toggles walk/run. The back button returns to the main menu.

---

## Acknowledgements

- The DCC decoder is based on the algorithm by Paul Siramy
- PKWARE DCL decompression is equivalent to the public `blast.c` by Mark Adler, reimplemented in Java

## Legal

Diablo II and related materials belong to Blizzard Entertainment. This is a fan project; it contains no original game files and requires a legally obtained copy of the game.
