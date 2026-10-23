package dev.kultivator.sect.build.api;

import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.build.engine.PreviewData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The construction engine as every other module sees it (DESIGN 21.24): a static facade over the registered
 * implementation. The engine module ({@code sect.build.engine.BuildEngine}) implements this interface and registers itself
 * in its {@code init()}; everyone else calls {@code EngineApi.get().x(...)}. Every method has a safe default (nothing to
 * read, nothing changes, an "unavailable" error), so callers work whether or not the engine is present.
 * <p>
 * All calls happen on the server thread. Templates are 1-9 and tiers 1-9 ({@link SectTemplates}); {@code rot} follows the
 * rotation convention of {@link SectTemplates}. A ticket is a preview handle (0 = refused; the engine tells the player
 * why). Only {@code sect.build.engine.BuildPlacer} behind this API ever places or removes blocks (DESIGN 21.10).
 */
public interface EngineApi {

	/** The error of every default action (defined in {@code lang-src/spine6b.*}). */
	String UNAVAILABLE_KEY = "kultivator.spine6b.unavailable";

	// ================================================================== founding (DESIGN 21.12)

	/**
	 * Compiles a footprint preview for {@code template} at the player's hall stele (async: the snapshot is read over
	 * ticks, the plan emitted off-thread); the client gets a {@code BuildPreview} once {@link #previewData} is ready.
	 */
	default long preview(ServerPlayer player, int template, int rot, Glaze glaze) {
		return 0L;
	}

	/** The compiled preview of a ticket, or null while it is still compiling, expired or unknown. */
	default @Nullable PreviewData previewData(long ticket) {
		return null;
	}

	/** Confirms a preview (or a test or showcase preview): null on success, else why not. */
	default @Nullable Component confirm(ServerPlayer player, long ticket) {
		return Component.translatable(UNAVAILABLE_KEY);
	}

	/** Drops a preview (Batal) and its hologram. */
	default void cancel(long ticket) {
	}

	// ================================================================== reading

	/** The owner's template 1-9, 0 without one. */
	default int template(UUID owner) {
		return 0;
	}

	/** The highest completed tier 0-9 (counts only while the stele stands on its anchor cell). */
	default int tier(UUID owner) {
		return 0;
	}

	default @Nullable SiteView site(UUID owner) {
		return null;
	}

	/** Every component of the owner's plan in placement order (empty without a site). */
	default List<ComponentView> components(UUID owner) {
		return List.of();
	}

	/** What the owner's site still needs: of tier {@code tierOr0}, or (0) of the whole remaining template. */
	default Bill remainingBill(UUID owner, int tierOr0) {
		return Bill.EMPTY;
	}

	/** The bill of one tier of a template from the dry run, before digging credit. */
	default Bill tierBill(int template, int tier, Glaze glaze) {
		return Bill.EMPTY;
	}

	/** The real plan numbers of a template ({@code uji sekte info}). */
	default PlanStats stats(int template) {
		return PlanStats.empty(template);
	}

	/** Dry run of all emissions of one template, or of all nine when {@code template} is null ({@code uji sekte audit}). */
	default AuditReport audit(@Nullable Integer template) {
		return AuditReport.unavailable(template);
	}

	/** True when the block at {@code pos} was placed by the builder and still stands (the sect cell index). */
	default boolean isSectCell(ServerLevel level, BlockPos pos) {
		return false;
	}

	/** True when {@code pos} lies within {@code margin} blocks (horizontally) of any confirmed footprint or showcase. */
	default boolean insideAnySite(Level level, BlockPos pos, int margin) {
		return false;
	}

	/** A named anchor of the owner's built plan ("arrival", "post_water", "main_hall", "tower", "work_*"...), or null. */
	default @Nullable BlockPos anchor(UUID owner, String id) {
		return null;
	}

	/** Where builders stand: a perimeter cell of the open component (changes every 20 s), or null. */
	default @Nullable BlockPos workSpot(UUID owner) {
		return null;
	}

	// ================================================================== control

	/** The flow's rate factor (builders, session speed, FPS hint), multiplied into the rate of DESIGN 21.15. */
	default void setRateMultiplier(UUID owner, double factor) {
	}

	/** Overrides the banked ticks (the engine keeps the bank; debug {@code bank <ticks>}). */
	default void setBank(UUID owner, long ticks) {
	}

	/** Jeda / Lanjut; false without a site. */
	default boolean pause(UUID owner, boolean paused) {
		return false;
	}

	/** Prioritaskan: moves a component of the current tier to the front; false when not found. */
	default boolean prioritize(UUID owner, String componentId) {
		return false;
	}

	/** Lewati bagian ini: skips a (blocked) component, refunding its unused reservation; false when not found. */
	default boolean skip(UUID owner, String componentId) {
		return false;
	}

	/** Selesaikan tanpa blok terhalang: finishes the component leaving its blocked cells out; false when not found. */
	default boolean finishWithoutBlocked(UUID owner, String componentId) {
		return false;
	}

	/** Periksa ulang: re-checks the blocked cells now. */
	default void recheck(UUID owner) {
	}

	/** The site's permissions "Bongkar &amp; simpan" (player-made blocks) and "Kosongkan" (containers), DESIGN 21.10. */
	default void authorize(UUID owner, boolean removePlayerBlocks, boolean emptyContainers) {
	}

	/**
	 * {@link #authorize(UUID, boolean, boolean)} remembering whether an op switched them on: set in singleplayer by a
	 * non-op, they stop counting once the world is opened to LAN (DESIGN 21.19). Kept for the owner also before founding.
	 */
	default void authorize(UUID owner, boolean removePlayerBlocks, boolean emptyContainers, boolean byOp) {
		authorize(owner, removePlayerBlocks, emptyContainers);
	}

	/**
	 * Periksa &amp; Perbaiki: inspects the DONE components off the server thread, then queues their air cells as a paid
	 * repair (the owner is told the result). The number of finished parts being inspected, 0 when there are none, -1
	 * without a site, -2 while the site is busy (building, deconstructing or already inspecting).
	 */
	default int repair(UUID owner) {
		return -1;
	}

	/** Bongkar bangunan ({@code total} false keeps the landform) or Bongkar total; false without a site. */
	default boolean deconstruct(UUID owner, boolean total) {
		return false;
	}

	/** Naik Templat with a preview ticket of the larger template: null when it started, else why not. */
	default @Nullable Component beginUpgrade(UUID owner, int newTemplate, long ticket) {
		return Component.translatable(UNAVAILABLE_KEY);
	}

	/** Lepaskan Templat ({@code keepBlocks} true: the buildings stay as ordinary blocks); false without a site. */
	default boolean release(UUID owner, boolean keepBlocks) {
		return false;
	}

	/** Relocation (Pindah &amp; bongkar / Pindah &amp; tinggalkan) to a new stele: null when it started, else why not. */
	default @Nullable Component relocate(UUID owner, BlockPos newStele, boolean deconstructOld) {
		return Component.translatable(UNAVAILABLE_KEY);
	}

	// ================================================================== debug and test (DESIGN 21.22)

	/** Debug {@code tingkat-set}: sets only the tier counter (hall-level coupling tests). */
	default void setTierCounter(UUID owner, int tier) {
	}

	/** Debug {@code komponen selesai}: finishes the open component now; false when none is open. */
	default boolean finishOpen(UUID owner) {
		return false;
	}

	/** Debug {@code rencana ulang}: recompiles the plan to the current plan version. */
	default void replan(UUID owner) {
	}

	/** {@code uji sekte templat}: a free test preview (undo-logged); {@code force} skips only relief, earthwork and water. */
	default long previewTest(ServerPlayer player, int template, int rot, Glaze glaze, boolean force) {
		return 0L;
	}

	/** {@code uji sekte tingkat}: builds every component up to {@code tier} at no cost; speedMode 0 normal, 1 instan, 2 cepat. */
	default @Nullable Component testBuild(ServerPlayer player, int tier, int speedMode) {
		return Component.translatable(UNAVAILABLE_KEY);
	}

	/** {@code uji sekte naik}: an instant test upgrade. */
	default @Nullable Component testUpgrade(ServerPlayer player, int newTemplate) {
		return Component.translatable(UNAVAILABLE_KEY);
	}

	/** {@code uji sekte bongkar}: an instant test deconstruction. */
	default @Nullable Component testDeconstruct(ServerPlayer player) {
		return Component.translatable(UNAVAILABLE_KEY);
	}

	/** {@code uji sekte pamer}: a showcase preview at {@code origin} (not a hall, at most 2); confirm it with {@link #confirm}. */
	default long showcase(ServerPlayer player, int template, int tier, int rot, BlockPos origin) {
		return 0L;
	}

	/** {@code uji sekte pamer hapus [semua]}: restores the world from the undo log; the number of showcases removed. */
	default int showcaseRemove(ServerPlayer player, boolean all) {
		return 0;
	}

	/** {@code uji sekte pamer daftar}. */
	default List<SiteView> showcases() {
		return List.of();
	}

	/** {@code uji sekte hologram}: the layout hologram only, no validation. */
	default void hologram(ServerPlayer player, int template, int tier) {
	}

	/** {@code uji sekte bandingkan}: all nine footprints nested around the player as a hologram. */
	default void compareAll(ServerPlayer player) {
	}

	/** {@code uji sekte kamera}: the art QA views 1-3 (DESIGN 21.8). */
	default void camera(ServerPlayer player, int view) {
	}

	/** {@code uji sekte reset}: removes every plan block, restores the undo-logged originals, clears the template. */
	default boolean reset(UUID owner) {
		return false;
	}

	// ================================================================== phase 7c Titik Formasi (DESIGN 24.6.2, 24.13 item 8)

	/**
	 * The owner's built Titik Formasi anchors ({@code formasi_<n>}), empty until P6b-cetak fills this (slip allowed). No
	 * sect template emits a {@code formasi_<n>} anchor yet (only the legacy {@code formation} disc), so nothing can be read
	 * here without the engine; the Kitab slip hides its "Di Titik Formasi" talisman while this list is empty.
	 */
	default List<FormasiAnchor> formasiAnchors(UUID owner) {
		return List.of();
	}

	/**
	 * Optional: asks the engine to build {@code specId}'s pattern ({@code Pattern.emit(rot)}) at anchor {@code anchorId} as
	 * a {@code FORMASI_PATTERN} component, inside the confirmed footprint only and placed inactive. Null when queued, else
	 * why not (the default: unavailable).
	 */
	default @Nullable Component requestPattern(UUID owner, String anchorId, String specId, int rot) {
		return Component.translatable(UNAVAILABLE_KEY);
	}

	// ================================================================== Sekte Langit (DESIGN 37.9.1)

	/**
	 * A sky preview (Rancang Langit) of sky template {@code sky} (11-19) with its plaza at {@code yP} and the throne
	 * shifted {@code dx, dz} from the owner's stele (path A) or from the player (path B / {@code test}). Async like
	 * {@link #preview}; {@link #confirm} founds the tethered sky site. 0 when refused (the player is told why).
	 */
	default long previewSky(ServerPlayer player, int sky, int rot, Glaze glaze, int yP, int dx, int dz, boolean test) {
		return 0L;
	}

	/** {@code uji langit_sekte pamer}: a sky showcase preview whose throne is at {@code origin} (undo-logged). */
	default long showcaseSky(ServerPlayer player, int sky, int tier, int rot, BlockPos origin) {
		return 0L;
	}

	/**
	 * The Ascension (Upacara Naik, DESIGN 37.3.5 item 4): moves the hall onto the throne of the owner's tethered sky site,
	 * makes the sky site the owner's main site and turns the ground site into the Mountain Foot ({@code mode}). Null on
	 * success, else why not.
	 */
	default @Nullable Component ascend(UUID owner, FootMode mode) {
		return Component.translatable(UNAVAILABLE_KEY);
	}

	/** The owner's sky site (tethered or standing), or null. */
	default @Nullable SiteView skySite(UUID owner) {
		return null;
	}

	/** True when {@code pos} lies in a sky island's footprint column below its lowest block (DESIGN 37.8.3). */
	default boolean underSkyIsland(Level level, BlockPos pos) {
		return false;
	}

	/** {@code uji langit_sekte bangun}: builds the owner's sky site (tethered or not) up to {@code tier} at no cost. */
	default @Nullable Component testBuildSky(ServerPlayer player, int tier, int speedMode) {
		return Component.translatable(UNAVAILABLE_KEY);
	}

	/** {@code uji langit_sekte naik [paksa]}: the Ascension now; {@code force} skips the tier-1 condition (never the stele). */
	default @Nullable Component testAscend(ServerPlayer player, boolean force) {
		return Component.translatable(UNAVAILABLE_KEY);
	}

	// ================================================================== registration

	/** The registered engine, or the safe defaults. */
	static EngineApi get() {
		return Holder.engine;
	}

	/** Called once by the engine module's {@code init()}; null restores the defaults. */
	static void register(@Nullable EngineApi engine) {
		Holder.engine = engine == null ? Holder.NONE : engine;
	}

	/** True once an engine registered itself. */
	static boolean registered() {
		return Holder.engine != Holder.NONE;
	}

	/** Keeps the registered engine (not part of the API). */
	final class Holder {
		private static final EngineApi NONE = new EngineApi() {
		};
		private static volatile EngineApi engine = NONE;

		private Holder() {
		}
	}
}
