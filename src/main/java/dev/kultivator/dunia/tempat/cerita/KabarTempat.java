package dev.kultivator.dunia.tempat.cerita;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.sekte.api.SekteBus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rumours that follow the world (DESIGN 38.S.5). Templates come from {@code data/kultivator/tempat/kabar/*.json}
 * ({@link Templat}); a world event, a place event, a story chapter or a deed of a player spreads one ({@link #kabar}),
 * which then lives in {@link CeritaData} for {@code umur_hari} days: at most {@code cerita.rumorMaks} per place and half
 * that (at least 12) world-wide. NPC talk ({@link TempatDialog} nodes {@code teks: "kabar"}, 38NL chatter through
 * {@link #pilih}) tells the freshest rumour a player has not heard today, preferring the speaker's role; when nothing
 * lives, the background templates ({@code pemicu "umum"}) keep towns talking.
 *
 * <p>Built-in triggers: {@code harta_lahir}, {@code harta_diklaim}, {@code langit_<sky kind>}, {@code segel_pecah},
 * {@code binatang_evolusi} ({@code DuniaEvents}); {@code tagihan_dibayar}, {@code tagihan_ditolak}, {@code tempat_rusak},
 * {@code kejahatan}, {@code acara_<id>}, {@code quest_<id>} ({@code TempatEvents}); {@code alur_bab<n>} ({@link AlurTakhta});
 * {@code sekte_<aksi>} (war, peace, alliance, growth, raid of NPC sects) and {@code sekte_turnamen} ({@code SekteBus}).
 * Other modules spread their own through {@code CeritaApi.kabar(level, situsId, templat, isian...)}.
 */
public final class KabarTempat {

	/**
	 * One template: {@code {id, pemicu, bobot?, umur_hari?, peran_penyebar?[], teks?, isian?[]}}; text key defaults to
	 * {@code kultivator.cerita.kabar.<id>}; {@code isian} names fixed arguments resolved at telling time ({@link Isian});
	 * arguments given when spreading come first.
	 */
	public record Templat(String id, String pemicu, int bobot, int umurHari, List<String> penyebar, String teks, List<String> isian) {

		static List<Templat> parseFile(JsonObject json) {
			List<Templat> out = new ArrayList<>();
			JsonElement list = json.get("templat");
			if (list == null || !list.isJsonArray()) {
				return out;
			}
			for (JsonElement e : list.getAsJsonArray()) {
				if (!e.isJsonObject()) {
					continue;
				}
				JsonObject o = e.getAsJsonObject();
				String id = DialogNode.str(o, "id", "");
				if (id.isEmpty()) {
					continue;
				}
				List<String> roles = new ArrayList<>();
				for (String r : DialogNode.list(o, "peran_penyebar")) {
					roles.add(r.toLowerCase(Locale.ROOT));
				}
				out.add(new Templat(id, DialogNode.str(o, "pemicu", "umum"), o.has("bobot") ? Math.max(1, o.get("bobot").getAsInt()) : 10,
						o.has("umur_hari") ? Math.max(1, o.get("umur_hari").getAsInt()) : 3, List.copyOf(roles),
						DialogNode.str(o, "teks", "kultivator.cerita.kabar." + id), DialogNode.list(o, "isian")));
			}
			return out;
		}

		boolean dariPeran(@Nullable NpcRole role) {
			return penyebar.isEmpty() || role == null || penyebar.contains(role.id());
		}
	}

	/** Hooks the built-in triggers (from {@code Cerita.init}). */
	static void init() {
		DuniaEvents.TREASURE_BORN.register((level, birth) -> pemicu(level, CeritaData.GLOBAL, "harta_lahir"));
		DuniaEvents.TREASURE_CLAIMED.register((level, birth, winner, rival) -> {
			if (winner != null) {
				pemicu(level, CeritaData.GLOBAL, "harta_diklaim", winner.getName().getString());
			}
		});
		DuniaEvents.SKY_EVENT.register((level, kind, started) -> {
			if (started) {
				pemicu(level, CeritaData.GLOBAL, "langit_" + kind.id());
			}
		});
		DuniaEvents.SEAL_CHANGED.register((level, site, integrity, broken) -> {
			if (broken) {
				pemicu(level, CeritaData.GLOBAL, "segel_pecah");
			}
		});
		DuniaEvents.BEAST_EVOLVED.register((owner, beast, tier, line) -> {
			if (owner.level() instanceof ServerLevel level && tier >= 5) {
				pemicu(level, CeritaData.GLOBAL, "binatang_evolusi", owner.getName().getString());
			}
		});
		TempatEvents.TAGIHAN.register((player, situs, jumlah, dibayar) -> {
			if (player.level() instanceof ServerLevel level) {
				pemicu(level, situs, dibayar ? "tagihan_dibayar" : "tagihan_ditolak", player.getName().getString());
			}
		});
		TempatEvents.TEMPAT_RUSAK.register((level, situs, gugus, blok, pelaku) -> {
			if (blok >= 12) {
				pemicu(level, situs, "tempat_rusak");
			}
		});
		TempatEvents.KEJAHATAN.register((player, town, jenis) -> {
			if (player.level() instanceof ServerLevel level) {
				pemicu(level, town, "kejahatan", player.getName().getString());
			}
		});
		TempatEvents.ACARA.register((level, situs, acara, mulai) -> {
			if (mulai) {
				pemicu(level, situs, "acara_" + acara.replace('.', '_'));
				SumberLayanan.acara(level, situs, acara);
			}
		});
		// sect news (38.S.5 "SekteBus: perang, pakta"): wars, peace, alliances, growth and raids of the NPC sects
		SekteBus.RIVAL_SECT_ACTION.register((server, owner, sectId, action, magnitude) -> {
			switch (action) {
				case PERANG, DAMAI, SEKUTU, PUTUS, TUMBUH, SERANGAN, PERANG_NPC -> {
					// the sect is stored as "sekte:<id>" and named at telling time (Isian), so the client translates it
					if (sectId != null) {
						pemicu(server.overworld(), CeritaData.GLOBAL, "sekte_" + action.id(), "sekte:" + sectId);
					}
				}
				default -> {
				}
			}
		});
		SekteBus.TOURNAMENT_ENDED.register((server, owner, result) -> pemicu(server.overworld(), CeritaData.GLOBAL, "sekte_turnamen"));
		TempatEvents.QUEST.register((player, id, tahap) -> {
			if (tahap == TempatEvents.QUEST_SELESAI && player.level() instanceof ServerLevel level) {
				pemicu(level, QuestTracker.situs(player, id), "quest_" + id, player.getName().getString());
			}
		});
	}

	/** Spreads one template of trigger {@code pemicu} (weighted pick) at {@code situs} ({@link CeritaData#GLOBAL} world-wide). */
	public static void pemicu(ServerLevel level, long situs, String pemicu, String... isian) {
		if (!CeritaConfig.jalan()) {
			return;
		}
		List<Templat> pool = new ArrayList<>();
		int total = 0;
		for (Templat t : CeritaReload.kabar().values()) {
			if (t.pemicu.equals(pemicu)) {
				pool.add(t);
				total += t.bobot;
			}
		}
		if (pool.isEmpty()) {
			return;
		}
		int roll = level.getRandom().nextInt(total);
		for (Templat t : pool) {
			roll -= t.bobot;
			if (roll < 0) {
				simpan(level.getServer(), situs, t, isian);
				return;
			}
		}
	}

	/** C38.ST1 {@code kabar}: spreads template {@code templat} (by id) with arguments {@code isian}. */
	public static void kabar(ServerLevel level, @Nullable Long situs, String templat, Object... isian) {
		if (!CeritaConfig.jalan()) {
			return;
		}
		Templat t = CeritaReload.kabar().get(templat);
		if (t == null) {
			pemicu(level, situs == null ? CeritaData.GLOBAL : situs, templat, strings(isian));
			return;
		}
		simpan(level.getServer(), situs == null ? CeritaData.GLOBAL : situs, t, strings(isian));
	}

	private static String[] strings(Object[] isian) {
		String[] out = new String[isian == null ? 0 : isian.length];
		for (int i = 0; i < out.length; i++) {
			Object o = isian[i];
			out[i] = o instanceof Component c ? c.getString() : String.valueOf(o);
		}
		return out;
	}

	private static void simpan(MinecraftServer server, long situs, Templat t, String[] isian) {
		CeritaData data = CeritaData.get(server);
		List<CompoundTag> list = data.rumours(situs);
		long day = Kota.day(server);
		// one live copy per template and place: a newer one refreshes it
		list.removeIf(r -> r.getStringOr("t", "").equals(t.id));
		CompoundTag r = new CompoundTag();
		r.putString("t", t.id);
		ListTag args = new ListTag();
		for (String a : isian) {
			args.add(StringTag.valueOf(a == null ? "" : a));
		}
		r.put("i", args);
		r.putLong("h", day);
		r.putLong("s", situs);
		list.add(r);
		int cap = situs == CeritaData.GLOBAL ? Math.max(12, CeritaConfig.get().rumorMaks / 2) : CeritaConfig.get().rumorMaks;
		while (list.size() > cap) {
			list.remove(0);
		}
		data.touch();
	}

	/** Drops rumours older than their template's age (once per day from the story tick). */
	static void bersihkan(MinecraftServer server) {
		CeritaData data = CeritaData.get(server);
		long day = Kota.day(server);
		Map<String, Templat> templates = CeritaReload.kabar();
		boolean changed = false;
		for (long site : data.rumourSites()) {
			List<CompoundTag> list = data.rumours(site);
			int before = list.size();
			list.removeIf(r -> {
				Templat t = templates.get(r.getStringOr("t", ""));
				return t == null || day - r.getLongOr("h", day) > t.umurHari;
			});
			changed |= before != list.size();
		}
		if (changed) {
			data.touch();
		}
	}

	/**
	 * The rumour {@code player} hears from a speaker of {@code role} at {@code situs}: the freshest live one not told today
	 * (place first, then world-wide), else a background line; null when there is nothing at all. Marks it heard.
	 */
	public static @Nullable Component pilih(ServerPlayer player, long situs, @Nullable NpcRole role) {
		MinecraftServer server = player.level().getServer();
		CeritaData data = CeritaData.get(server);
		long day = Kota.day(server);
		Map<String, Templat> templates = CeritaReload.kabar();
		CompoundTag best = null;
		Templat bestT = null;
		long[] order = situs == CeritaData.GLOBAL ? new long[] {CeritaData.GLOBAL} : new long[] {situs, CeritaData.GLOBAL};
		for (long site : order) {
			if (best != null) {
				break;
			}
			List<CompoundTag> list = data.rumours(site);
			for (int i = list.size() - 1; i >= 0; i--) {
				CompoundTag r = list.get(i);
				Templat t = templates.get(r.getStringOr("t", ""));
				if (t == null || !t.dariPeran(role) || data.heard(player.getUUID(), key(r), day)) {
					continue;
				}
				best = r;
				bestT = t;
				break;
			}
		}
		if (best != null) {
			data.markHeard(player.getUUID(), key(best), day);
			return teks(player, situs, bestT, best);
		}
		List<Templat> pool = new ArrayList<>();
		for (Templat t : templates.values()) {
			if ("umum".equals(t.pemicu) && t.dariPeran(role)) {
				pool.add(t);
			}
		}
		if (pool.isEmpty()) {
			return null;
		}
		RandomSource random = player.getRandom();
		Templat t = pool.get(random.nextInt(pool.size()));
		return Component.translatable(t.teks, Isian.resolve(player, situs, t.isian));
	}

	/** Every live rumour of {@code situs} and the world-wide list as text (commands, the town notice board). */
	public static List<Component> semua(ServerPlayer player, long situs) {
		List<Component> out = new ArrayList<>();
		CeritaData data = CeritaData.get(player.level().getServer());
		Map<String, Templat> templates = CeritaReload.kabar();
		for (long site : new long[] {situs, CeritaData.GLOBAL}) {
			for (CompoundTag r : data.rumours(site)) {
				Templat t = templates.get(r.getStringOr("t", ""));
				if (t != null) {
					out.add(teks(player, situs, t, r));
				}
			}
			if (situs == CeritaData.GLOBAL) {
				break;
			}
		}
		return out;
	}

	private static String key(CompoundTag r) {
		return r.getStringOr("t", "") + "@" + r.getLongOr("s", 0L);
	}

	private static Component teks(ServerPlayer player, long situs, Templat t, CompoundTag r) {
		ListTag stored = r.getListOrEmpty("i");
		List<String> names = new ArrayList<>();
		for (int i = 0; i < stored.size(); i++) {
			names.add(stored.getStringOr(i, ""));
		}
		names.addAll(t.isian);
		return Component.translatable(t.teks, Isian.resolve(player, situs, names));
	}

	/** Spread by a site discovery: nothing for now except the story goals; kept for symmetry with 38.S.5. */
	static void ditemukan(ServerPlayer player, DuniaSites.Site site) {
		if (player.level() instanceof ServerLevel level && site.kind().startsWith("gua/")) {
			pemicu(level, CeritaData.GLOBAL, "situs_gua", player.getName().getString());
		}
	}

	private KabarTempat() {
	}
}
