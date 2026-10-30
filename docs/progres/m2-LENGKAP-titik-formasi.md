# m2-LENGKAP-titik-formasi (builder "titik-formasi", DESIGN 24.6.2 A and B)

Not compiled with Gradle (forbidden for this task). Every touched file was compiled with plain `javac` (MC 26.3 jars, `-sourcepath` of the mod) and the plots were checked with dry-run harnesses (`FreeSq`, `TitikCheck`, `PolaCheck`, `AuditCheck`, kept in the scratchpad, not in the repo). Nothing was run in game.

## Checklist

### A. Titik Formasi anchors (`formasi_<n>`) really come out of the sect build engine
- [x] New `sect/build/blueprint/FormasiTitik.java`: a table of plots per template, registered from `SectBuild.init` through `TemplateExtensions` (before the first `Templates.get`), so each plot is a normal template component `t<tier>_formasi_<n>` that the engine plans, bills, schedules, builds, repairs and dismantles. A finished site builds it as "Tambahan" (no rebuild of anything). Part `TitikPart`: light earthwork only in free columns (where no other part stands: boxes of all parts, walls by segment), a paved square (stone field, stone-brick ring, fret border, chiselled-stone-brick centre tile), anchor `formasi_<n>` = the carved centre tile (the Mata Formasi stands one block above, the pattern is read around that cell).
- [x] `EngineApi.formasiAnchors(owner)` is implemented in `BuildEngine`: the finished plots of the owner's site (anchor world position from `Site.anchors`, radius and tier from the table) with the pattern requested on each (spec id and component state). `FormasiAnchor` got `pola` and `state` (old 4-argument constructor kept).
- [x] The Kitab slip needed nothing new to show its anchor talisman: it appears as soon as `HallInfo.anchors` is not empty (it is now, from template 5 up).

### B. Pattern component `FORMASI_PATTERN`
- [x] `EngineApi.requestPattern(owner, anchorId, specId, rot)` implemented in `BuildEngine`. Refused: no standing own site (also showcase, sky, mountain foot, restoring, dismantling), plot not finished, unknown spec or `SectFormationSlots.fits` false (so `NO_6B`, legacy mirrors, wrong radius or dy span), pattern already started (open, funding, waiting, suspended, done), or the owner's store cannot pay the bill right now (checked with a reserve-then-release, nothing is taken up front). A request that is still queued or skipped is replaced. `rot` is the world rotation as the hologram shows it (FIXED_ORIENTATION specs always north); the engine stores `rot - site.rotation` so the placed pattern matches the hologram on every site rotation.
- [x] New `sect/build/blueprint/PolaPart.java` (`Pattern.slots(rot)` = the cells of `Pattern.emit(rot)`): slots under the eye are PLINTH (they replace the plot paving), the rest FRAME. Left out on purpose: the Mata Formasi, every exact-item socket (beacon, diamond, netherite heart: the owner sets those, the pattern still completes), fluid slots, and the blocks no sect build may place (same list as `PlanAudit.bannedBlock`). So the pattern is placed inactive; the owner wakes the eye with fuel.
- [x] Persistence: `Site.pola` (component id -> anchor, spec, local rotation) saved under `pola` in the site tag; the component is rebuilt from it after a restart (resume, repair, dismantle all work). Component bills and op counts of a request are cached in `Site.bills/opCounts` (saved). The 1 s survey also re-runs when a component has no bill yet (this also fixes the missing bills of extension components added to old sites).
- [x] Engine wiring (all use `Site.component(id)` / `Site.components()` instead of `spec()`): Scheduler (requested patterns are always Tambahan, `complete` counts them), resume, reopen, fund/open, complete, dismantle (a dismantled pattern forgets its request), finish-dismantle, components list (`ComponentView` name "Pola Formasi: <spec>"), remaining bill, prioritise, skip, finishWithoutBlocked, repair (Periksa & Perbaiki restores them, later components win), survey.
- [x] Billing and refunds are the engine's own: the bill (`Costs.bill`) is reserved from the Gudang when the component opens; exact items (end rods, amethyst blocks, lodestone ...) 1:1; skipped, blocked or taken-down cells give their share back through the existing release/refund paths. Nothing is held outside the persistent ledger/site data, no new static server state.
- [x] `Costs`: priced the four blocks only patterns use (iron block = its deposit value 9 units Logam, magma block, purpur, nether bricks), so the audit has no unknown blocks.
- [x] `PlanAudit` (`uji sekte audit`): for every plot of a template, every spec that fits it in every rotation is emitted and must stay inside the footprint, use only priced blocks, and plan every slot it can place (completeness).
- [x] Server action `pola:<anchorId>:<specId>:<rot>` (`formasi/integrate/HallBridge`, owner only) -> `SectBuildBridge.request` (now actually called). Test hook `/kultivasi uji sekte titik` (list) and `titik <id> <pola>` (a test site builds it free; rotation = the way the player faces).

### UI (the single Kitab slip, Formasi tablet of the hall scroll)
- [x] `client/formasi/FormationSlip`: the existing hologram talisman at a spot no longer advances on click; new row "Titik berikutnya" (cycles the selected spot) and "Bangun di Titik k" (sends `pola:`; disabled with a reason line when not owner, locked, does not fit the radius, or already started), plus a status line ("Titik 2 · jari-jari 6 · tahap 7 · Pola X (dalam antrean)"). The hologram anchor is the plot tile + 1 (the eye cell).
- [x] `client/sectbuild/SiteDiagram`: plots draw as the disc shape.
- [x] Lang (id + en together): `blueprint` (component names, 5 error keys), `formasi` (2), `formasiui` (slip texts; the anchor tooltip reworded), `sectbuild` (2 test-hook lines).

## Design decisions (design silent or impossible as written)
- The table asks for 13 x 13 .. 23 x 23 squares "inside an existing component", but the nine templates have no reserved plots and are packed. A dry run of every component of every tier and of all module extensions (5 modules) leaves no free square of radius 6+ in templates 3-6 and only small ones elsewhere. So the plots live in the free squares the harness found: nothing above the floor except lanterns, flowers and low plants (ornament, ground cover), one zone level under the whole square, off the central axis (|x| <= 4), the Bagua disc and every named anchor. Radii are the largest of 11/8/6/3 that fit; `n` keeps the table row (0 gate tower, 1 Bagua court, 2 alchemy hall, 3 meditation court, 4-6 nodes, 7 Altar Gabungan) and the preferred radius, but the position is not "beside" the named building.
- Tier = the table's tier raised to the highest tier of any component that paves a cell of the square (so no later paving replaces the carved tile); plots sort last in their tier.
- Result (templates / plots: radius): T3, T4: none; T5: 1 (r3, tier 7); T6: 1 (r3, tier 7); T7: 5 (r3); T8: 8 (2 x r6, rest r3); T9: 8 (r8, 3 x r6, rest r3). Templates 1-4 and the sky templates have no anchors, the slip hides "Di Titik Formasi" there as before. A site gets the missing plots the normal Tambahan way.
- Decorations (lanterns, banner poles) that stand inside a plot are not removed; the pattern's own cells replace them like any light swap (refunded), repair puts the pattern cell back.
- Hall level lock is not enforced when building a pattern (like `setTarget`), only shown in the slip; activation still checks it.
- Patterns are bought only when the Gudang can pay the whole bill at request time; otherwise "setor bahan dulu". A queued request that later cannot be funded waits like any component and can be skipped.
- `FormasiAnchor.centre` = the carved tile (Seragam's decal code already assumed this with `centre().above()`); the slip adds 1 for the hologram.

## Files
Server: `sect/build/blueprint/{FormasiTitik (new), PolaPart (new), Costs (CRLF kept)}`, `sect/build/engine/{BuildEngine, Site (CRLF kept), Scheduler, PlanAudit (CRLF kept)}`, `sect/build/api/{EngineApi, FormasiAnchor}`, `sect/build/SectBuild`, `sect/build/flow/BuildCommands`, `formasi/integrate/{HallBridge, SectBuildBridge}`.
Client: `client/formasi/FormationSlip` (CRLF kept), `client/sectbuild/SiteDiagram`.
Lang: `lang-src/{blueprint,formasi,formasiui,sectbuild}.{id_id,en_us}.json`.

## Not done / left
- Anchors for templates 3-4 and a full set for 5-6 need reserved plots in the template art (needs below).
- Not built: removing a finished pattern on request (only deconstruction takes it down), a second pattern on a plot that already has one.
- The pattern's special blocks the engine will not place (exact-item hearts, water, redstone, lightning rods) must be set by the owner by hand; Perbaiki leaves those cells alone.
