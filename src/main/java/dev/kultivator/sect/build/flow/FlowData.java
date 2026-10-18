package dev.kultivator.sect.build.flow;

import dev.kultivator.sect.hall.Halls;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The flow's saved state (DESIGN 21.20 {@code build_flow}): one section of the owner's {@code HallData} entry, reached
 * through {@code Halls.section(owner, "build_flow")}, so it moves with the hall and is saved with it. Holds the ledger, the
 * exact items, the Peti Galian, the frozen scan cap, the guarantee tier, the flags, missions, builders, gathering parties,
 * donors, awards and the template history. Every write is followed by {@link #dirty()}.
 * <p>
 * Without a hall {@code Halls.section} hands back a fresh tag, so writes for an owner without a hall are simply dropped.
 */
public final class FlowData {

	public static final String SECTION = "build_flow";
	public static final int VERSION = 1;

	// ---- flags (DESIGN 21.20 flags {...}; the last three are the flow's own)
	/** "Pakai hasil galian": digging credit goes to the ledger (on, the default) or as items to the Peti Galian. */
	public static final String F_EXCAVATION = "use_excavation";
	/** "Kosongkan ke Peti Galian": containers inside component boxes may be emptied (singleplayer or op only). */
	public static final String F_EMPTY = "empty_containers";
	/** "Bongkar &amp; simpan": player-made blocks in component boxes may be removed to the Peti Galian (singleplayer or op). */
	public static final String F_REMOVE = "remove_player_blocks";
	/** "Bangun Sendiri": the owner keeps the 20.3.1 scan and does not want the template prompt. */
	public static final String F_SELF_BUILD = "self_build";
	/** The one-time "[Pilih Templat Sekte]" chat prompt was shown. */
	public static final String F_PROMPTED = "prompted";
	/** The last switch-on of the two permissions came from an op (they stay valid on LAN, DESIGN 21.19). */
	public static final String F_AUTH_OP = "auth_op";

	/** A guarantee that lasts until the new tier reaches it (template upgrade). */
	public static final long UNTIL_REACHED = Long.MAX_VALUE;

	private static @Nullable MinecraftServer server;

	static void setServer(@Nullable MinecraftServer value) {
		server = value;
	}

	static @Nullable MinecraftServer server() {
		return server;
	}

	/** The owner's live {@code build_flow} section. */
	public static CompoundTag of(UUID owner) {
		CompoundTag section = Halls.section(owner, SECTION);
		if (!section.contains("v")) {
			section.putInt("v", VERSION);
		}
		return section;
	}

	/** A child compound of {@code parent}, created when missing (live). */
	public static CompoundTag sub(CompoundTag parent, String key) {
		CompoundTag child = parent.getCompound(key).orElse(null);
		if (child == null) {
			child = new CompoundTag();
			parent.put(key, child);
		}
		return child;
	}

	/** A child list of {@code parent}, created when missing (live). */
	public static ListTag list(CompoundTag parent, String key) {
		ListTag child = parent.getList(key).orElse(null);
		if (child == null) {
			child = new ListTag();
			parent.put(key, child);
		}
		return child;
	}

	/** Marks the hall data to be saved. */
	public static void dirty() {
		Halls.setDirty();
	}

	/** True when the owner founded a hall (the flow's data has somewhere to live). */
	public static boolean hasHall(UUID owner) {
		return Halls.hallOf(owner) != null;
	}

	/** Game time of the overworld (0 without a server). */
	public static long gameTime() {
		return server == null ? 0L : server.overworld().getGameTime();
	}

	/** Day index {@code floor(overworld day time / 24000)} (DESIGN 20.1). */
	public static long day() {
		return server == null ? 0L : Math.floorDiv(server.overworld().getOverworldClockTime(), 24000L);
	}

	// ================================================================== flags

	public static boolean flag(UUID owner, String key) {
		CompoundTag flags = of(owner).getCompoundOrEmpty("flags");
		return flags.getIntOr(key, F_EXCAVATION.equals(key) ? 1 : 0) != 0;
	}

	public static void setFlag(UUID owner, String key, boolean on) {
		sub(of(owner), "flags").putInt(key, on ? 1 : 0);
		dirty();
	}

	/** Hands the two permissions (and whether an op set them) to the engine: on a switch, before a preview, at founding. */
	public static void applyAuthorization(UUID owner) {
		dev.kultivator.sect.build.api.EngineApi.get().authorize(owner, flag(owner, F_REMOVE), flag(owner, F_EMPTY), flag(owner, F_AUTH_OP));
	}

	// ================================================================== the hall-level coupling (DESIGN 21.17)

	/** {@code frozenScanCap}: the hall's building cap read at confirmation, -1 when none was frozen. */
	public static int frozenScanCap(UUID owner) {
		return of(owner).getIntOr("frozen_scan_cap", -1);
	}

	public static void setFrozenScanCap(UUID owner, int cap) {
		of(owner).putInt("frozen_scan_cap", cap);
		dirty();
	}

	/** The guaranteed tier while it applies (upgrade until reached, relocation for 10 days), else 0. */
	public static int guaranteeTier(UUID owner) {
		CompoundTag guarantee = of(owner).getCompoundOrEmpty("guarantee");
		int tier = guarantee.getIntOr("tier", 0);
		if (tier <= 0) {
			return 0;
		}
		long until = guarantee.getLongOr("until_day", 0L);
		return until == UNTIL_REACHED || day() <= until ? tier : 0;
	}

	/** "upgrade" or "move" (empty without a guarantee). */
	public static String guaranteeKind(UUID owner) {
		return of(owner).getCompoundOrEmpty("guarantee").getStringOr("kind", "");
	}

	public static long guaranteeUntil(UUID owner) {
		return of(owner).getCompoundOrEmpty("guarantee").getLongOr("until_day", 0L);
	}

	public static void setGuarantee(UUID owner, int tier, long untilDay, String kind) {
		CompoundTag guarantee = new CompoundTag();
		guarantee.putInt("tier", Math.max(0, tier));
		guarantee.putLong("until_day", untilDay);
		guarantee.putString("kind", kind == null ? "" : kind);
		of(owner).put("guarantee", guarantee);
		dirty();
	}

	public static void clearGuarantee(UUID owner) {
		of(owner).remove("guarantee");
		dirty();
	}

	// ================================================================== awards and history

	/** True when the once-only award {@code id} was already given (mission lines, server lines, ceremonies). */
	public static boolean awarded(UUID owner, String id) {
		for (Tag tag : of(owner).getListOrEmpty("awards")) {
			if (id.equals(tag.asString().orElse(""))) {
				return true;
			}
		}
		return false;
	}

	/** Marks {@code id} as given; false when it already was. */
	public static boolean award(UUID owner, String id) {
		if (awarded(owner, id)) {
			return false;
		}
		ListTag awards = list(of(owner), "awards");
		awards.add(net.minecraft.nbt.StringTag.valueOf(id));
		while (awards.size() > 512) {
			awards.remove(0);
		}
		dirty();
		return true;
	}

	/** A history entry {template, from_day, to_day}: a new template opens one, release or upgrade closes it. */
	public static void openHistory(UUID owner, int template) {
		closeHistory(owner);
		CompoundTag entry = new CompoundTag();
		entry.putInt("t", template);
		entry.putLong("from", day());
		entry.putLong("to", -1L);
		ListTag history = list(of(owner), "history");
		history.add(entry);
		while (history.size() > 16) {
			history.remove(0);
		}
		dirty();
	}

	public static void closeHistory(UUID owner) {
		ListTag history = of(owner).getListOrEmpty("history");
		if (!history.isEmpty() && history.get(history.size() - 1) instanceof CompoundTag last && last.getLongOr("to", -1L) < 0L) {
			last.putLong("to", day());
			dirty();
		}
	}

	private FlowData() {
	}
}
