# CLAUDE.md — Fog of Paris

Context file for Claude Code. Read it at the start of every session.

---

## 1. How to work with the owner (READ FIRST)

The owner is **not an Android developer and not an app designer**. He knows
some Python (data analysis). He supervises this project; he does not write
the code. Communication rules:

- **Explain in plain language.** Before any non-trivial change, say in a few
  simple sentences *what* you are going to do and *why*, as you would to a
  smart non-developer. No unexplained jargon: if you must use a technical term
  (e.g. "foreground service", "ViewModel", "migration"), define it in one line
  the first time.
- **Ask before big decisions.** Anything structural (new library, database
  schema, architecture change, new permission, anything affecting battery or
  privacy) → present the options with pros/cons in simple terms, give your
  recommendation, and wait for approval.
- **Summarize after each task**: what changed, in 2–5 plain sentences, and
  **exactly how to test it** on the phone (which button to press, where to walk,
  what he should see).
- **Guide him through Android Studio** when he has to do something himself
  (run the app, grant a permission, use the emulator's fake location, read an
  error). Give step-by-step instructions; don't assume he knows where things are.
- Keep explanations short. Clarity over completeness.
- The owner writes in French or English; answer in the language he uses.

---

## 2. Vision

An Android app (Kotlin) that applies the video-game "fog of war" to Paris.
The city starts covered in dark fog. **Walking** through the streets reveals
the areas you pass through, permanently. Later: a game layer with points per
neighborhood and progress stats. All data stays on the phone.

Target device: Google Pixel 9a. Dev environment: Windows + Android Studio.
Personal project, not published on the Play Store for now.

---

## 3. Game rules (decided — do not reinvent)

1. **On foot only.** Walking *and running* reveal cells. Car, bike, metro,
   RER, bus must NOT reveal anything.
   - The main filter is Google's **Activity Recognition Transition API**:
     reveal only while it *positively* reports walking/running (on foot).
     "Still" or "in vehicle" never reveals. Speed alone is NOT reliable
     (slow buses/trams in traffic), as the owner pointed out.
   - Safety nets: a **speed filter** (reject fixes above ~12 km/h, so running
     still counts; mainly covers the detection lag when boarding a vehicle)
     and an **accuracy filter** (ignore fixes worse than ~30 m).
   - Walking detection arrives in Phase 5; Phase 4 ships with the speed and
     accuracy filters only (owner's decision, 2026-09-26).
   - **Never interpolate across GPS gaps.** In the metro the GPS goes silent
     and reappears elsewhere; the jump must not reveal the path in between.
     Only reveal the cell of each accepted fix (optionally fill straight lines
     between two *close, recent, walking* fixes, never across a gap).
2. **Playable area = the administrative limits of the city of Paris.**
   - This includes Bois de Boulogne and Bois de Vincennes (they are part of
     the commune, even though they lie outside the périphérique).
   - Use the official commune boundary from Paris OpenData (or the union of
     the 20 arrondissements). Store it as a bundled GeoJSON asset. Use a
     point-in-polygon test.
   - Rendering: inside the city = fog that clears when visited. Outside =
     solid black, not playable, never revealed.
3. **Works in the background, app closed.** The phone stays in the pocket.
   - Implement a **foreground service** (persistent notification) for tracking.
   - Android 14+ requirements: declare `foregroundServiceType="location"` and
     the `FOREGROUND_SERVICE_LOCATION` permission.
   - **No `ACCESS_BACKGROUND_LOCATION`** (owner's decision, 2026-09-26, for
     privacy): the service is always started from the visible app, so it keeps
     "while in use" location access with the app closed. Consequence: tracking
     can't auto-restart after a reboot; the user taps the switch again.
   - Add a clear on/off switch for tracking in the UI.
   - **Battery matters**: high-accuracy GPS only while on foot; otherwise
     `PRIORITY_PASSIVE` (no GPS of our own). This lets tracking stay on
     permanently — the owner chose "leave it on for good" (2026-09-27) over a
     fully automatic start that would need "Allow all the time".
4. **Gamification (Phase 7, rules approved by the owner 2026-09-26).**
   - Unit: Paris's **80 "quartiers administratifs"** (Paris OpenData
     `quartier_paris`, bundled GeoJSON).
   - Quartier % = revealed cells whose centre is in the quartier / all cells
     of the quartier (same method as the Paris-wide %).
   - Medals: Bronze 10 %, Silver 25 %, Gold 50 %, Maîtrisé 75 %. Not 100 %:
     building interiors can't be walked, so 100 % is practically unreachable.
   - Points = 1 per revealed cell in Paris + 100 per medal (cumulative: a
     Gold quartier gives 3 medals = 300).
   - History is derived from `firstVisitedAt` (cells per week, medal dates);
     nothing extra stored.
   - Quiet notification (no sound) when a medal is won while tracking.
5. **Collections & levels (approved by the owner 2026-09-27).**
   - 7 sets, 145 places (`tools/places/places.tsv` → `assets/places.geojson`,
     coordinates from OSM): 34 walkable Seine bridges, 18 town halls (Hôtel de
     Ville + Paris Centre + 5e–20e), 16 passages couverts, 30 monuments,
     25 parks, 15 squares, 7 main stations.
   - A place counts when a revealed cell's centre is within the place's radius
     (60 m bridges … 150 m big roundabouts; the cell containing the place always
     counts). Retroactive. +50 pts per place, +500 per completed set.
   - Unvisited places are shown on the map as grey dots (destinations),
     visited ones in gold.
   - Levels by points, owner's order: Badaud 0, Promeneur 500, Flâneur 1 500,
     Touriste 3 500, Arpenteur 7 000, Explorateur 12 000, Parisien 20 000,
     Cartographe 30 000, Baron Haussmann 45 000.
   - Quiet notifications for new place, completed set, new title.
   - **Points are fixed** (owner, 2026-09-27): never multipliers, "double
     points" events or time-limited bonuses. No proximity vibrations either.
   - Phase 8 (approved 2026-09-27): "Paris la nuit" redesign following the
     mockup https://claude.ai/artifact/72UCGjYbbrcT5PaRvgRUxE (tabs Carte ·
     Progrès · Quartiers · Collections, quartier mosaic, collection cards,
     new-title celebration), plus: nearest-unvisited-place compass card;
     nearby-place alert behind a settings switch (off by default; when on it
     may vibrate; only fires while walking on familiar ground, i.e. few new
     cells revealed recently, never while exploring new streets); hidden
     treasures (secret places not shown on the map); arrondissement badges
     (all 4 quartiers at bronze); a darker map style.

---

## 4. Technical direction

- **Kotlin + Jetpack Compose** (Material 3).
- **Architecture**: simple MVVM — UI (Compose) → ViewModel → Repository →
  data sources (database, location). Keep it as simple as possible; explain it
  to the owner once, in plain words.
- **Location**: Google Play Services `FusedLocationProviderClient` (replaces the
  old `LocationManager` approach — better battery, better background behavior).
- **Walking detection**: Activity Recognition Transition API
  (`ACTIVITY_RECOGNITION` permission).
- **Storage**: **Room** (SQLite) from the start, because gamification needs
  richer data than a simple set (visit timestamps, per-quartier aggregates).
  Suggested tables: visited cells (cellX, cellY, firstVisitedAt), and later
  derived stats. No cloud, no network sync.
- **Map**: currently osmdroid 6.1.20 with standard OpenStreetMap tiles.
  At the start of the rebuild, **check osmdroid's maintenance status and
  compare with MapLibre Android**; recommend one to the owner in plain terms.
  Note: OSM's public tile servers have a usage policy — fine for personal use,
  but a different tile provider would be needed if the app is ever published.
- **Grid**: square cells of **50 m**, equirectangular approximation (fine at the
  scale of Paris). Cells identified by a `data class CellId(x, y)`.
- **Fog rendering** (since Phase 6): a small alpha image over Paris's grid
  extent (4 px per cell, `FogMask.kt`) shown as a MapLibre ImageSource with
  linear resampling. Each visited cell clears a soft disc (not a square) so
  neighbouring cells blend into a continuous trail — owner found squares too
  blocky. Rendering only: scoring still counts whole cells. Black outside
  Paris is a polygon layer on top. The map must stay edge-to-edge (it goes under the status and
  navigation bars; UI controls must respect system insets). UI texts are in
  French (owner's choice); map labels use `name:fr`.
- Dependency versions centralized in `gradle/libs.versions.toml`.
- Use current, stable library versions; verify them rather than guessing.

---

## 5. Existing code (first prototype)

A working prototype exists (map + position marker + fog + persistence), built
while the owner was learning. **A clean rebuild is acceptable and expected**,
because the target architecture (background service, Room, walking detection)
differs a lot. Reuse what is good:

- `Grid.kt` — `CellId`, `latLonToCell`, `cellToBounds`. Correct and tested;
  keep the logic (constants: 50 m cells, meters per degree computed at
  latitude 48.85°).
- `GridTest.kt` — unit tests (origin, inside-cell, round-trip). Keep them and
  keep them green.
- `FogOverlay.kt` — osmdroid overlay using `saveLayer` + `PorterDuff.Mode.CLEAR`.
  Lessons learned: use `projection.getIntrinsicScreenRect()`, recompute pixel
  positions on every draw (the projection changes on scroll/zoom).
- Known bug from the prototype: the Scaffold's `innerPadding` shrank the map,
  so the fog didn't cover the top/bottom of the screen. The map must be
  edge-to-edge.
- Persistence was a DataStore `Set<String>` ("x,y") — to be replaced by Room.

Package name: `com.glazrak.fogofparis`.

---

## 6. Product backlog (not yet scheduled)

- Better name, in French (ideas: "Paris Dévoilé", "Terra Incognita") — owner
  will decide later.
- Export/share the revealed map as an image.
- Manual export/import of progress (cloud backup is disabled).
- Done in Phase 6: recenter button, center on user at launch, GPS status
  label, % of Paris revealed, soft fog edges.

---

## 7. Code conventions

- Dedicated types for domain concepts (data classes like `CellId`) rather than
  generic tuples/pairs.
- Named arguments when calling Kotlin functions with several same-typed
  parameters. (Not available for Java constructors, e.g. osmdroid's
  `BoundingBox` → positional args + a comment giving the order.)
- Comments explain *why*, not *what*. Keep them in sync with the code.
- Naming: `camelCase` for variables/functions, `PascalCase` for types,
  `SCREAMING_SNAKE_CASE` for constants.
- Log errors (`Log.e`) instead of swallowing exceptions silently.
- Pure logic (grid, filters, scoring, point-in-polygon) lives in plain Kotlin
  files with unit tests.

---

## 8. Workflow

- **Claude Code makes the commits.** Small, coherent commits; messages in
  English, imperative mood, with prefixes (`feat:`, `fix:`, `refactor:`,
  `chore:`, `test:`). Commit before any large multi-file change so it can be
  rolled back. Push to GitHub when a step is stable and the app builds.
- Work in **phases**; at the end of each phase the app must build and run.
- Run the unit tests before committing; keep them green.
- After each phase: plain-language summary + how to test it on the Pixel
  (and in the emulator using fake locations / routes when relevant).
- Privacy: location data never leaves the device. Cloud backup is disabled
  (`allowBackup="false"` + exclude-all rules, owner's decision 2026-09-26);
  direct phone-to-phone transfer is still allowed.
