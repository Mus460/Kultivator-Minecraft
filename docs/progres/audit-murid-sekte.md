# Audit murid + sekte (2026-10-25)

Rules kept: no Gradle, no commit, no `.minecraft`, no edits in files owned by 41/42 (`docs/progres/jadwal-41-42.md`).
Every changed Java file was compiled with javac 25 against `build/classes` + Loom jars (68 files in one run, then
HubunganTab, exit 0). Lang fragments: JSON parses, id_id/en_us key sets identical, EOL kept (sectbuild CRLF, rest LF).
**Finish agent: these are the files to include in the next Gradle build (list in "Edits").**

## Checklist

- [x] P1 pilih jalan murid (user report)
- [x] P2 pembangunan sekte terlalu sering terhalang (user report)
- [x] P3 ganti nama sekte (user report)
- [x] DiscipleScreen: ICONS[tab] crash (fixed before this audit), key 0 -> tab 10, distinct Hati/Hubungan glyphs
- [x] Scan of all screens for `ICONS[...]`-style unbounded glyph arrays: none left
- [x] Murid server core, murid client (all 10 tabs), mandiri 39, small sekte modules, sect build + langit, hall/raids/diplomacy
- [x] Wiring: 124 payloads, 38 action ids, lang keys, client classes on server, config-gated registration, keybinds
- [x] Fixes applied and compiled; bugs in 41/42-owned files listed below

## Priority user reports

| Report | Cause | Fix | File |
|---|---|---|---|
| P1 "fitur pilih jalan murid tidak jalan" | [Dorong ke jalan...] only added +0.8 to the path score; the current path has +0.7 inertia and a change needs +0.5 margin, so the push almost never changed anything, while the action bar said "Kau mendorong ...". | The master's push now switches the path at once (story/diary/notice via `tetapkan`); the push stays (+0.8) so the 10-day check keeps it; pushing the current path clears pushes; IBLIS overlay: push stored, redemption first. Answer in chat: `dorong.ganti` / `dorong.cabut` / `dorong.iblis`. Tooltip text updated. | `sekte/mandiri/PilihJalan.java` (`dorong`), `sekte/mandiri/cerita/AksiMandiri.java` (case "dorong"), `lang-src/sekte_mandiri_cerita.*` |
| P2 "banyak blok yang menghalangi pembangunan" | No player-placed tracker: `Classifier` treated every block outside `#kultivator:sect_natural` as PLAYER_MADE (blocked). False positives: ruins/towers of Structory, Dungeons and Taverns, vanilla ruins/mineshafts; branch logs (column without leaves above), fallen logs/stumps; modded grass/plants; modded stone/ores; obsidian (in `indestructible`, never overridable); basalt, blackstone, amethyst, corals, fossils, cave vines...; empty worldgen barrels/chests; unloaded chunk cells in the retry pass counted as blocked. Founding refused any foreign structure id containing "temple", "desert", "jungle"... | `Classifier`: blocks inside a non-`kultivator` structure piece = natural (`worldgen()`), empty containers there too; logs natural when a scan-time tree column is within 3 blocks, or touching decaying leaves, or lying on natural ground (fallen/stump); any `canBeReplaced()` block = plant; loose obsidian/crying obsidian/lodestone no longer protected (lit portal frames and formation lodestones still are). `sect_natural.json` +31 optional entries (`#c:ores/stones/sands/gravels/obsidians`, `#minecraft:replaceable_by_trees`, corals, cave vines, basalt, blackstone, amethyst, bone block, suspicious sand/gravel, sculk blocks...). `BuildEngine.retryDeferred`: UNLOADED is retried, not blocked. Blocked chat line now lists up to 3 cells (block, x y z, why) + hint (`Bongkar & simpan`, Periksa ulang). `Validator`: foreign structure ids only refuse a site for "village". Build page upgrade button works for sky templates (was always disabled). Guide c5.blocked text updated. Still protected: filled containers, unopened loot chests, spawners/vaults/budding amethyst, modded block entities, this mod's own places, real player builds outside structures. | `sect/build/engine/{Classifier,BuildEngine,Validator}.java`, `sect/build/flow/Ceremonies.java`, `client/sectbuild/BuildPage.java`, `data/kultivator/tags/block/sect_natural.json`, `lang-src/sectbuild.*`, `lang-src/guide.*` |
| P3 "tidak bisa ganti nama sekte" | Hall page: pressing [Ganti Nama] the second time (as its tooltip says) first dropped the text field's focus on mouse-down (`AulaPage.mouseClicked`), so on release the button saw "not focused", reset the text to the old name and re-focused: the new name was never sent. Only Enter worked. Server validation (3-24 letters/digits/space/-/_, any script) and the refusal message were fine. | Click on the rename button no longer unfocuses the field. | `client/hall/AulaPage.java` |

## Feature table

| Feature | Status | File:line | Note |
|---|---|---|---|
| Disciple scroll header icon tabs 8-10 | fixed (before) | client/disciple/DiscipleScreen.java:379 | `iconOf(tab)` |
| Disciple scroll key 0 / tooltip label | fixed | client/disciple/DiscipleScreen.java (keyPressed, init tooltip) | KEY_0 -> tablet 10, label "0" |
| Hati vs Hubungan glyph | fixed | client/screen/ui/Glyphs.java (`HATI`, `HUBUNGAN`), KepribadianClient:37/39, HubunganClient:53/54 | tab + hub card |
| Hidden tablets clickable on short windows | fixed | client/disciple/DiscipleScreen.java (tooltip + click loops) | |
| Technique sockets on narrow screens | fixed | client/disciple/TechniqueTab.java (`socketStep`) | |
| Hati tab advancement/A_VIEW sent on every scroll open | fixed | client/sekte/kepribadian/HatiTab.java | sent on first draw |
| Hati tab buttons over empty message | fixed | HatiTab.java (`visible`) | |
| Jalan Hidup tab diary request at open | fixed | client/sekte/mandiri/JalanHidupTab.java | `onView` removed, drawBehind requests |
| Jaring screen reopening after close | fixed | client/sekte/hubungan/JaringScreen.java | refresh reply dropped when closed |
| Hubungan tab "memuat" forever with module off | fixed | client/sekte/hubungan/HubunganTab.java:~238 | `canSend` |
| Tournament / emblem cards "memuat" with module off | fixed | client/sekte/turnamen/TurnamenScreen.java, client/sekte/seragam/SeragamClient.java | new keys `*.ui.mati` |
| Elder picker stays open | fixed | client/sekte/tetua/BalaiTetuaScreen.java:~160 | |
| Return from errand paused Mandiri planner a day | fixed | disciple/DiscipleEntity.java:439-466, :984 | `applyMode` |
| Dao protectors left after calamity cut | fixed | disciple/path/Calamity.java:246 | `DaoProtectors.clearFor` |
| Disciple list not refreshed on unload | fixed | disciple/Disciples.java:~1036 | removal reason |
| Depart during release bow | fixed | disciple/Disciples.java:~746 | |
| `uji murid` debug target role | fixed | disciple/Disciples.java:~1576 | |
| Soul Seal jade lost on late refusal | fixed | disciple/path/SoulSeal.java | `giveJadeBack` |
| Disciple core: actions, orders, AI, recruitment, breakthrough, pills, Dao, release, powers, paths, save/load, info | OK | disciple/** | traced end to end |
| Life path push (P1) | fixed | sekte/mandiri/PilihJalan.java, cerita/AksiMandiri.java | see top |
| Tebus ritual cancelled at once | fixed | sekte/mandiri/Perencana.java:312 | |
| Honour duel started twice | fixed | sekte/mandiri/seteru/DuelSeteru.java | `MULAI` set |
| Gifts given twice | fixed | sekte/mandiri/jalan/PelaksanaJalan.java:636,641 | |
| Seclusion always "terganggu" | fixed | sekte/mandiri/tumbuh/TutupDiri.java:122 | |
| Busy disciple pulled into duel/brawl | fixed | seteru/Adegan.java, DuelSeteru, Tawuran | `asliSibuk` |
| Feud action on lover record | fixed | seteru/CampurTangan.java:61 | |
| Town scene at 0,0,0 | fixed | jalan/KontrakKota.java:61 | |
| Natal work while following | fixed | kerja/NatalSendiri.java:81 | |
| Mandiri minors (salute level, gate watch, party records, Tanding, Antrean dirty, reset cmd, roster entry from client) | fixed | Perencana, PelaksanaDasar:194, Perjalanan, Tanding, Antrean, MandiriUji, SeteruPayloads | |
| Kebun index from client (server hang/OOM) | fixed | sekte/kebun/Pertumbuhan.java:174, KebunPayloads.java:409 | bounds check |
| Rebel duel winner by health (surrender = win) | fixed | sekte/kepribadian/KepergianMurid.java, Kepribadian.init | duel end listener |
| Leaving pose stuck after logoff | fixed | KepergianMurid.java:~366, leave() | |
| Kepribadian reset cmd mode | fixed | KepribadianCommands.java:~396 | |
| Apologise without active hall | fixed | Kepribadian.java:~814 | |
| Mission recall wounded | fixed | sekte/misi/Kepulangan.java:~463 | |
| Kebun self-job flag | fixed | Pertumbuhan.java:331, GiliranTambang.java:198 | |
| Emblem designer stale cost | fixed | sekte/seragam/Seragam.java:~205 | |
| Tangga announcement skip | fixed | sekte/tangga/Pendakian.java:~646 | |
| Incense cmd shrinks ItemStack.EMPTY | fixed | sekte/paviliun/PaviliunCommands.java:308 | |
| Entrance test opening effect | fixed | sect/hall/Candidates.java:1056 | |
| Offer cap / glow berries | fixed | sect/hall/Contribution.java:~164, ~232 | |
| Disciple slips skip daily limit | fixed | sect/hall/KontribusiMurid.java:~143 | |
| Sky template upgrade refused (too_large) | fixed | sect/build/flow/Founding.java:158 | rank |
| Deposit with no hall deletes items | fixed | sect/build/flow/Deposits.java:342 | |
| Envoy rewards with no envoy (overflow) | fixed | sekte/langit/main/LangitUtusan.java:215 | |
| Free materials after recheck+skip | fixed | sect/build/engine/BuildEngine.java (received, `dropPending`) | |
| Upgrade during deconstruction wipes site | fixed | BuildEngine.java (beginUpgrade) | new key `blueprint.error.busy` |
| Deconstruction skips unloaded / changed-state cells | fixed | BuildEngine.java (dismantleRun), BuildPlacer.java (dismantle) | |
| Legacy cells after restart | fixed | BuildEngine.java (received resume) | |
| Sky ranks vs ground thresholds | fixed | flow/Ceremonies.java, GatherExpeditions.java, BuildMissions.java | |
| A1 offering for bigger island | fixed | flow/LangitRancang.java:117, Founding.confirm, LangitSesaji.java | |
| Upgrade guarantee kept after Ganti | fixed | flow/Founding.java (change) | |
| Sky graves two per cell | fixed | blueprint/awan/PulauSatelit.java:367 | |
| Sky safety net on short drops | fixed | sekte/langit/gerak/LangitJatuh.java | foot list, not fallDistance |
| Sky build order (mine, paifang), duplicate history, Langit screen with module off, dive trail, sky sketch, ferry riders | fixed | AwanTemplates, Upgrade, BuildCommands, LangitMain/LangitUi, TerjunAwan, BuildPage:167, FeriAwanEntity | |
| Payloads (124), action ids (38), lang keys (3736 literal), client refs on server, gated registration, keybinds | OK | | G = CAST also vanilla quickActions (intentional, SettingsRows note) |
| Raids, diplomacy, player sects (/sect), sparring, territory/outposts, sect events, hub cards | OK | | previous audit-sekte bugs confirmed fixed |

## Bugs left for 41/42 owners (not edited: owned files)

| Owner | File | Finding |
|---|---|---|
| 41A | `sect/hall/Halls.java` ~1836 `relocateHall` | unsure: skips the `KultivatorConfig.sectHall` and `Abadi.isImmortalLevel` checks founding does (try moving a stele into Alam Abadi). |
| 41A | `sect/hall/Halls.java` 510-515 `onStelePlaced` wake | unsure: waking a dormant hall may skip the new found veto (check `beforeMove` with `wake=true`). |
| 41A | `sect/hall/Halls.java` ~956 + `sekte/tetua/Tetua.java:299` | minor: elder auto-accept goes through `handleAction`, which uses the viewer's open scroll; if the master views a mate's hall, nothing is accepted but the day is marked done. Needs a `Halls.accept(master, id)` -> `Candidates.accept(master, master.getUUID(), id)`. |

## Left as is (design calls / minor / unsure) — needs a decision or in-game test

- Disciple death: `MandiriConfig.bolehGugur` is never read; DESIGN 39 systems never kill (verified). Disciples can still die by design in normal combat, outside raid territory (`musuh/SectRaids.java:1144`), in the Forbidden Ground on Hardcore, and possibly from wild monsters during a tournament bout (`duel/DuelHooks.java` ~140, unsure). A global guard would be a new feature.
- Founding validator (`sect/build/engine/Validator.java`): "cliffs" red (>15% columns >17 below plaza, not forceable, :201/:241), relief caps per template, any villager within 16 blocks, modded block entity in a building volume = red; sky band top includes tree leaves (`Snapshot.java:298-306`) so Tectonic peaks + trees can empty the band. Not changed (design).
- Classifier trade-offs (P2): a player's build inside another mod's structure piece and a lone log on natural ground are now cleared (drops go to the sect ledger / Dig Chest).
- Build page blocked count shows the sample size (max 32), `BuildEngine.java` `view()`; deconstruction retry pass still drops a cell whose chunk unloads; seal stone/retaining walls stay "own" (changing it would block builds); `Ledger.richStacks` vs `takeRich` index with undecodable items (unsure); `Previews` recompute per tick (unsure); `LangitSyncServer` no forget on dimension change (unsure).
- Small minors not fixed: `sekte_kebun` info never shown on the client; `YangPergi` queue never expires; forced test betrayal with no NPC sect; PaviliunScreen scroll unclamped (harmless); `Musim.reigning` null for guest winner (no callers); `kebun giliran` test (skipped on purpose: the change would break the command); SoulSeal refusal always says `seal_max`; Mandiri sword-flight cancel fall damage (unsure); `Perencana.paksa` bypass (unsure); `/sect` player group has no rename command (only the hall has a name to change).

## Edits made (compile these)

Java (69): client `disciple/DiscipleScreen`, `disciple/TechniqueTab`, `hall/AulaPage`, `screen/ui/Glyphs`, `sectbuild/BuildPage`,
`sekte/hubungan/{HubunganClient,HubunganTab,JaringScreen}`, `sekte/kepribadian/{HatiTab,KepribadianClient}`,
`sekte/mandiri/JalanHidupTab`, `sekte/seragam/SeragamClient`, `sekte/tetua/BalaiTetuaScreen`, `sekte/turnamen/TurnamenScreen`;
server `disciple/{DiscipleEntity,Disciples}`, `disciple/path/{Calamity,SoulSeal}`,
`sect/build/blueprint/awan/{AwanTemplates,PulauSatelit}`, `sect/build/engine/{BuildEngine,BuildPlacer,Classifier,Validator}`,
`sect/build/flow/{BuildCommands,BuildMissions,Ceremonies,Deposits,Founding,GatherExpeditions,LangitRancang,LangitSesaji,Upgrade}`,
`sect/hall/{Candidates,Contribution,KontribusiMurid}`, `sekte/kebun/{GiliranTambang,KebunPayloads,Pertumbuhan}`,
`sekte/kepribadian/{KepergianMurid,Kepribadian,KepribadianCommands}`, `sekte/langit/gerak/{FeriAwanEntity,LangitJatuh,TerjunAwan}`,
`sekte/langit/main/{LangitMain,LangitUi,LangitUtusan}`, `sekte/mandiri/{MandiriUji,PelaksanaDasar,Perencana,PilihJalan}`,
`sekte/mandiri/cerita/AksiMandiri`, `sekte/mandiri/jalan/{KontrakKota,PelaksanaJalan,Perjalanan}`,
`sekte/mandiri/kerja/{NatalSendiri,Tanding}`, `sekte/mandiri/seteru/{Adegan,Antrean,CampurTangan,DuelSeteru,SeteruPayloads,Tawuran}`,
`sekte/mandiri/tumbuh/TutupDiri`, `sekte/misi/Kepulangan`, `sekte/paviliun/PaviliunCommands`, `sekte/seragam/Seragam`,
`sekte/tangga/Pendakian`.
Data/lang: `data/kultivator/tags/block/sect_natural.json`; `lang-src/{blueprint,guide,sectbuild,sekte_mandiri_cerita,sekte_seragam,sekte_turnamen}.{id_id,en_us}.json`.

## Needs in-game test

Pilih jalan (push switches + chat line); hall rename by clicking [Ganti Nama] twice; sect build in Terralith/Tectonic with a
Structory/D&T ruin, fallen logs, big trees, obsidian in the footprint (should clear; real player builds and filled chests
should still block, chat names them); sky upgrade 11->12 from the Build page; disciple scroll keys 1-0 with 10 tabs and on a
short window; Tebus completes; one result per honour duel; journey gifts once; 1-2 day seclusion ends normally; rebel duel
surrender; deconstruction with part unloaded; Langit screen with module off; envoy commands with no envoy refused.
