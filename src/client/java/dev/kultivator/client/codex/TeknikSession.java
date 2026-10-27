package dev.kultivator.client.codex;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.technique.spec.run.Loadout;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The session-only technique state the server sends in {@code sessionExtra.teknik} (DESIGN 22.12.10, written by
 * {@code TeknikSync}), read defensively: missing keys mean "nothing active". Server keys (absolute game ticks):
 * <ul>
 * <li>{@code gcd}, {@code lock}: global cooldown and ultimate lockout ends;</li>
 * <li>{@code ch {id: {r ready, m max, n next recharge}}}: charges;</li>
 * <li>{@code act [{k kind, id, e end, r radius, t charge ticks, s stage}]}: kinds form, domain, field, aura, toggle,
 * channel, charge, stage, dharma, dao_domain;</li>
 * <li>{@code sh [pool, max]}: Perisai Qi; {@code kh}: the Kehancuran status; {@code free}: the uji bypass.</li>
 * </ul>
 * The server sends no start ticks, so a running effect's start is the first snapshot that showed it.
 */
public final class TeknikSession {

	public static final String KEY = "teknik";

	/** One lasting effect for the HUD arcs. */
	public record Lasting(String id, String kind, long start, long end) {
	}

	/** A timed flow (charge, channel or stage window). */
	public record Flow(String id, long start, long end, int stage, int count, boolean warn) {
	}

	private static long seen = -1L;
	private static CompoundTag tag = new CompoundTag();
	private static CompoundTag charges = new CompoundTag();
	private static List<Lasting> lasting = List.of();
	private static @Nullable Flow charge;
	private static @Nullable Flow channel;
	private static @Nullable Flow stages;
	/** First snapshot time of each running entry ("kind|id|end"), for the start of HUD arcs. */
	private static final Map<String, Long> FIRST_SEEN = new HashMap<>();

	/** The {@code sessionExtra.teknik} compound of the latest snapshot (never null). */
	public static CompoundTag tag() {
		long version = ClientCultivation.version();
		if (version != seen) {
			seen = version;
			tag = ClientCultivation.data().sessionExtra().getCompoundOrEmpty(KEY);
			parse();
		}
		return tag;
	}

	/** Turns the server's compact keys into the HUD's charges, lasting effects and flows. */
	private static void parse() {
		long now = ClientCultivation.serverGameTime();
		CompoundTag converted = new CompoundTag();
		CompoundTag ch = tag.getCompoundOrEmpty("ch");
		for (String id : ch.keySet()) {
			CompoundTag entry = ch.getCompoundOrEmpty(id);
			CompoundTag out = new CompoundTag();
			out.putInt("n", entry.getIntOr("r", 0));
			out.putInt("max", entry.getIntOr("m", 1));
			out.putLong("next", entry.getLongOr("n", 0L));
			converted.put(id, out);
		}
		charges = converted;
		List<Lasting> list = new ArrayList<>();
		Map<String, Long> alive = new HashMap<>();
		Flow newCharge = null;
		Flow newChannel = null;
		Flow newStages = null;
		for (Tag element : tag.getListOrEmpty("act")) {
			if (!(element instanceof CompoundTag entry)) {
				continue;
			}
			String kind = entry.getStringOr("k", "");
			String id = entry.getStringOr("id", "");
			long end = entry.getLongOr("e", 0L);
			String key = kind + "|" + id + "|" + end;
			long start = FIRST_SEEN.getOrDefault(key, now);
			alive.put(key, start);
			switch (kind) {
				case "form", "domain", "aura", "field", "toggle" -> list.add(new Lasting(id, kind, start, end));
				case "dharma" -> list.add(new Lasting(id, "form", start, end));
				case "dao_domain" -> list.add(new Lasting(id, "domain", start, end));
				case "charge" -> {
					int ticks = entry.getIntOr("t", 0);
					newCharge = new Flow(id, ticks > 0 ? end - ticks : start, end, 0, 0, false);
				}
				case "channel" -> newChannel = new Flow(id, start, end, 0, 0, false);
				case "stage" -> {
					TechniqueSpec spec = TechniqueRegistry.byId(id);
					int count = spec != null && spec.stages() != null ? spec.stages().count() : 0;
					newStages = new Flow(id, start, end, entry.getIntOr("s", 0), count, false);
				}
				default -> {
				}
			}
		}
		FIRST_SEEN.clear();
		FIRST_SEEN.putAll(alive);
		lasting = List.copyOf(list);
		charge = newCharge;
		channel = newChannel;
		stages = newStages;
	}

	public static long lockoutLeft() {
		return Math.max(0L, tag().getLongOr("lock", 0L) - ClientCultivation.serverGameTime());
	}

	/** The Perisai Qi pool ({@code sh[0]}). */
	public static double shield() {
		return Math.max(0.0D, tag().getListOrEmpty("sh").getFloatOr(0, 0.0F));
	}

	/** The Perisai Qi maximum ({@code sh[1]}). */
	public static double shieldMax() {
		return Math.max(0.0D, tag().getListOrEmpty("sh").getFloatOr(1, 0.0F));
	}

	/** Today's in-game day index: the client's overworld clock / 24000 (the server's {@code Mastery.day}). */
	public static long today() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? 0L : minecraft.level.getOverworldClockTime() / 24000L;
	}

	/** Charges of {@code id}: {n ready, max, next recharge tick} or null when the spec has none in flight. */
	public static @Nullable CompoundTag charges(String id) {
		tag();
		return charges.get(id) instanceof CompoundTag entry ? entry : null;
	}

	/** Lasting forms, domains, auras, toggles and fields still running (end 0 = until toggled off). */
	public static List<Lasting> lasting() {
		tag();
		long now = ClientCultivation.serverGameTime();
		List<Lasting> list = new ArrayList<>();
		for (Lasting entry : lasting) {
			if (entry.end() == 0L || entry.end() > now) {
				list.add(entry);
			}
		}
		return list;
	}

	public static @Nullable Flow charge() {
		tag();
		return running(charge);
	}

	public static @Nullable Flow channel() {
		tag();
		return running(channel);
	}

	public static @Nullable Flow stages() {
		tag();
		return running(stages);
	}

	private static @Nullable Flow running(@Nullable Flow flow) {
		return flow == null || flow.end() <= ClientCultivation.serverGameTime() ? null : flow;
	}

	/** The Kehancuran status compound ({@code kh}). */
	public static CompoundTag kh() {
		return tag().getCompoundOrEmpty("kh");
	}

	/** The player's stored switch ({@code extra.path.breaker}). */
	public static boolean breakerStored() {
		return ClientCultivation.data().extra().getCompoundOrEmpty("path").getBooleanOr("breaker", false);
	}

	/**
	 * Mode Kehancuran is on for the player's own techniques: the stored switch and still allowed (the server's
	 * {@code kh.allowed}; without it, the singleplayer owner counts as allowed).
	 */
	public static boolean breakerOn() {
		if (!breakerStored()) {
			return false;
		}
		CompoundTag kh = kh();
		if (kh.get("allowed") != null) {
			return kh.getBooleanOr("allowed", false);
		}
		return Minecraft.getInstance().hasSingleplayerServer();
	}

	/** The 12 loadout ids ("" = empty). */
	public static String[] slots() {
		return Loadout.slots(ClientCultivation.data());
	}

	/** The loadout's specs in slot order (empty slots skipped), or the castable specs by grade when it is empty. */
	public static List<TechniqueSpec> ring() {
		CultivationData data = ClientCultivation.data();
		List<TechniqueSpec> ring = new ArrayList<>();
		for (String id : slots()) {
			TechniqueSpec spec = id.isEmpty() ? null : TechniqueRegistry.byId(id);
			if (spec != null && data.knows(spec.id())) {
				ring.add(spec);
			}
		}
		if (!ring.isEmpty()) {
			return ring;
		}
		return SpecInfo.castables(data);
	}

	private TeknikSession() {
	}
}
