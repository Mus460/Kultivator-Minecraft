package dev.kultivator.dunia.kota42.ekonomi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaTitik;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Kepribadian;
import dev.kultivator.dunia.kota42.warga.Suasana;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Court cases of a Hakim Pembantu (DESIGN 42.6.6): a template engine over {@code data/kultivator/tempat/perkara/*.json}
 * filled with real residents of the place. 42C-2 writes the engine and three templates; 42B-C writes the rest (>= 24).
 * <p>
 * <b>Template</b> (all texts are lang keys; {@code %1$s} plaintiff, {@code %2$s} defendant, {@code %3$s} witness or the
 * gossip's first spreader):
 * <pre>
 * { "nama": key, "teks": key,
 *   "penggugat": {"peran": ["petani", ...], "klaim": key},          // peran = NpcRole ids, optional (any adult)
 *   "tergugat":  {"peran": [...], "klaim": key},
 *   "benar": "acak" | "penggugat" | "tergugat",                      // which side is in the right (acak: seeded per case)
 *   "saksi": 2,                                                      // 2-3 witnesses
 *   "kesaksian": {"penggugat": [key, ...], "tergugat": [key, ...]},  // what a truthful witness says when that side is right
 *   "dusta": key,                                                    // what a sly witness says instead (seeded 1 in 3)
 *   "bukti": [{"jenis": "barang|gosip|catatan", "barang": "minecraft:feather",
 *              "teks": {"penggugat": key, "tergugat": key}}],
 *   "putusan": [{"untuk": "penggugat|tergugat|damai", "teks": key, "akibat": key}],   // exactly 3
 *   "suap": 0.5 }                                                    // chance the side in the wrong offers an envelope
 * </pre>
 * <b>Flow</b>: every day a place with a judge (rank >= 3) opens new cases up to two (at most three open, each open seven
 * days). The judge calls witnesses ({@link #panggil}: their bodies come to the {@code sidang} titik of the hall when he
 * is near it and say their testimony), traces evidence ({@link #telusuri}: an item, a ledger entry, or a gossip with its
 * chain of spreaders) and decides ({@link #putuskan}). Right: the party in the right +10 goodwill, the place's mood +3
 * for three days; reconciliation half of that; wrong: the wronged party -12, mood -3. <b>Bribe</b>: a sly or greedy party
 * in the wrong may put an envelope on the table ({@code 15 x (b+1)} NB). Taking it pays the NB and Infami +2; with 20 %
 * the higher judge (Hakim Bao Qing of the capital) audits: one rank down and a fine of twice the bribe.
 * <p>
 * State in {@code KotaEkonomiData.situs("perkara", situs)}: {@code n} next id, {@code md}/{@code mds} mood bonus and
 * until day, {@code L} cases ({@code i} id, {@code t} template, {@code h} day opened, {@code a}/{@code b} plaintiff and
 * defendant warga, {@code s} witnesses int[], {@code sp} called mask, {@code bk} traced mask, {@code benar} 0 plaintiff /
 * 1 defendant, {@code amp} envelope NB, {@code ampd} 0 open / 1 taken / 2 refused, {@code put} verdict 1-3 (0 open),
 * {@code oleh} judge UUID, {@code dusta} lying witness mask).
 */
public final class Perkara {

	public static final int BUKA_HARI = 7;
	public static final int BUKA_MAKS = 3;
	public static final int BARU_PER_HARI = 2;
	public static final double AUDIT = 0.20D;
	public static final int INFAMI_SUAP = 2;
	public static final long SUAP_DASAR = 15L;
	public static final int KESAN_BENAR = 10;
	public static final int KESAN_SALAH = -12;
	public static final int SUASANA = 3;
	public static final int SUASANA_HARI = 3;
	/** Ticks a witness body stays at the hall. */
	public static final int SAKSI_TICK = 2400;
	/** Cases judged without a bribe for the advancement "hakim jujur". */
	public static final int JUJUR_SEPULUH = 10;

	public static final String P_PENGGUGAT = "penggugat";
	public static final String P_TERGUGAT = "tergugat";
	public static final String P_DAMAI = "damai";

	private static final FileToIdConverter LISTER = FileToIdConverter.json("tempat/perkara");
	private static volatile Map<String, JsonObject> templat = Map.of();
	/** Witness bodies at a hall: case key "situs:id" -> bodies with their despawn game time. */
	private static final Map<String, List<KultivatorNpc>> SAKSI = new HashMap<>();
	private static final Map<String, Long> SAKSI_SAMPAI = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Kultivator.id("kota42_perkara"), new Muat());
		Suasana.addPengaruhTempat(Perkara::pengaruhSuasana);
	}

	static void lupakan() {
		SAKSI.clear();
		SAKSI_SAMPAI.clear();
	}

	/** The loaded templates by id (file name). */
	public static Map<String, JsonObject> templat() {
		return templat;
	}

	// ================================================================== cases

	/** One case for the screens. */
	public record Kasus(long situs, int id, String templat, long hari, int penggugat, int tergugat, int[] saksi, int dipanggil,
			int ditelusuri, long amplop, int amplopKeadaan, int putusan) {

		public boolean selesai() {
			return putusan > 0;
		}
	}

	private static ListTag daftar(MinecraftServer server, long situs) {
		CompoundTag site = KotaEkonomiData.get(server).situs(KotaEkonomiData.PERKARA, situs);
		ListTag list = site.getListOrEmpty("L");
		if (!site.contains("L")) {
			site.put("L", list);
		}
		return list;
	}

	private static @Nullable CompoundTag mentah(MinecraftServer server, long situs, int id) {
		for (Tag tag : daftar(server, situs)) {
			if (tag instanceof CompoundTag c && c.getIntOr("i", -1) == id) {
				return c;
			}
		}
		return null;
	}

	private static Kasus dari(long situs, CompoundTag c) {
		return new Kasus(situs, c.getIntOr("i", 0), c.getStringOr("t", ""), c.getLongOr("h", 0L), c.getIntOr("a", 0), c.getIntOr("b", 0),
				c.getIntArray("s").orElse(new int[0]), c.getIntOr("sp", 0), c.getIntOr("bk", 0), c.getLongOr("amp", 0L), c.getIntOr("ampd", 0),
				c.getIntOr("put", 0));
	}

	/** The cases of a place (open and decided in the last week). */
	public static List<Kasus> kasus(MinecraftServer server, long situs) {
		List<Kasus> out = new ArrayList<>();
		for (Tag tag : daftar(server, situs)) {
			if (tag instanceof CompoundTag c) {
				out.add(dari(situs, c));
			}
		}
		return out;
	}

	/** One case, or null. */
	public static @Nullable Kasus kasus(MinecraftServer server, long situs, int id) {
		CompoundTag c = mentah(server, situs, id);
		return c == null ? null : dari(situs, c);
	}

	/** The case's title with the parties' names ("Ayam Bibi Wang hilang"). */
	public static Component nama(Kasus k) {
		JsonObject t = templat.get(k.templat());
		return t == null ? Component.literal(k.templat()) : teks(str(t, "nama"), k, -1);
	}

	/** The summary line. */
	public static Component ringkas(Kasus k) {
		JsonObject t = templat.get(k.templat());
		return t == null ? Component.empty() : teks(str(t, "teks"), k, -1);
	}

	/** What party {@code pihak} ("penggugat" / "tergugat") claims. */
	public static Component klaim(Kasus k, String pihak) {
		JsonObject t = templat.get(k.templat());
		JsonObject side = t == null ? null : obj(t, pihak);
		return side == null ? Component.empty() : teks(str(side, "klaim"), k, -1);
	}

	/** The testimony of witness index {@code i} (only meaningful once called). */
	public static Component kesaksian(MinecraftServer server, Kasus k, int i) {
		JsonObject t = templat.get(k.templat());
		CompoundTag c = mentah(server, k.situs(), k.id());
		if (t == null || c == null || i < 0 || i >= k.saksi().length) {
			return Component.empty();
		}
		if ((c.getIntOr("dusta", 0) & (1 << i)) != 0 && t.has("dusta")) {
			return teks(str(t, "dusta"), k, k.saksi()[i]);
		}
		String side = c.getIntOr("benar", 0) == 0 ? P_PENGGUGAT : P_TERGUGAT;
		JsonArray lines = obj(t, "kesaksian") == null ? null : arr(obj(t, "kesaksian"), side);
		if (lines == null || lines.isEmpty()) {
			return Component.empty();
		}
		return teks(lines.get(i % lines.size()).getAsString(), k, k.saksi()[i]);
	}

	/** The evidence of a case: kind, the item (or empty), the text once traced, the gossip chain once traced. */
	public record Bukti(int indeks, String jenis, ItemStack barang, Component teks, List<Component> rantai, boolean ditelusuri) {
	}

	public static List<Bukti> bukti(MinecraftServer server, Kasus k) {
		List<Bukti> out = new ArrayList<>();
		JsonObject t = templat.get(k.templat());
		CompoundTag c = mentah(server, k.situs(), k.id());
		JsonArray list = t == null ? null : arr(t, "bukti");
		if (list == null || c == null) {
			return out;
		}
		String side = c.getIntOr("benar", 0) == 0 ? P_PENGGUGAT : P_TERGUGAT;
		for (int i = 0; i < list.size(); i++) {
			JsonObject b = list.get(i).isJsonObject() ? list.get(i).getAsJsonObject() : new JsonObject();
			boolean traced = (k.ditelusuri() & (1 << i)) != 0;
			String kind = str(b, "jenis");
			ItemStack item = ItemStack.EMPTY;
			if (b.has("barang")) {
				Identifier id = Identifier.tryParse(str(b, "barang"));
				Item it = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
				item = it == null ? ItemStack.EMPTY : new ItemStack(it);
			}
			JsonObject texts = obj(b, "teks");
			Component text = traced && texts != null ? teks(str(texts, side), k, rantai(k, i).isEmpty() ? -1 : rantai(k, i).get(0))
					: Component.translatable("kultivator.kota42.perkara.bukti_belum");
			List<Component> chain = new ArrayList<>();
			if (traced && "gosip".equals(kind)) {
				for (int id : rantai(k, i)) {
					Warga w = WargaApi.byId(id);
					if (w != null) {
						chain.add(WargaApi.nama(w));
					}
				}
			}
			out.add(new Bukti(i, kind, item, text, chain, traced));
		}
		return out;
	}

	/** The spreaders of gossip evidence {@code i} (seeded from the place's residents; the last one is a witness). */
	private static List<Integer> rantai(Kasus k, int i) {
		List<Warga> all = WargaApi.wargaTempat(k.situs());
		List<Integer> out = new ArrayList<>();
		if (all.isEmpty()) {
			return out;
		}
		RandomSource random = RandomSource.create(k.situs() * 31L + k.id() * 977L + i);
		for (int n = 0; n < 2; n++) {
			Warga w = all.get(random.nextInt(all.size()));
			if (w.id != k.penggugat() && w.id != k.tergugat() && !out.contains(w.id)) {
				out.add(w.id);
			}
		}
		if (k.saksi().length > 0) {
			out.add(k.saksi()[0]);
		}
		return out;
	}

	/** Verdict {@code no} (1-3): its text and its estimated consequence. */
	public static Component[] putusanTeks(Kasus k, int no) {
		JsonObject t = templat.get(k.templat());
		JsonArray list = t == null ? null : arr(t, "putusan");
		if (list == null || no < 1 || no > list.size()) {
			return new Component[] {Component.empty(), Component.empty()};
		}
		JsonObject v = list.get(no - 1).getAsJsonObject();
		return new Component[] {teks(str(v, "teks"), k, -1), teks(str(v, "akibat"), k, -1)};
	}

	private static Component teks(String key, Kasus k, int saksi) {
		Warga a = WargaApi.byId(k.penggugat());
		Warga b = WargaApi.byId(k.tergugat());
		Warga s = saksi < 0 ? null : WargaApi.byId(saksi);
		return Component.translatable(key, a == null ? Component.literal("?") : WargaApi.nama(a), b == null ? Component.literal("?") : WargaApi.nama(b),
				s == null ? Component.literal("?") : WargaApi.nama(s));
	}

	// ================================================================== opening

	/** Daily: opens new cases in places with a judge, closes stale ones. */
	static void harian(MinecraftServer server, long situs, long hari) {
		if (!Jabatan.aktif() || templat.isEmpty()) {
			return;
		}
		ListTag list = daftar(server, situs);
		int open = 0;
		for (int i = list.size() - 1; i >= 0; i--) {
			CompoundTag c = list.getCompoundOrEmpty(i);
			long age = hari - c.getLongOr("h", hari);
			if (c.getIntOr("put", 0) > 0 ? age > BUKA_HARI * 2L : age > BUKA_HARI) {
				list.remove(i);
			} else if (c.getIntOr("put", 0) == 0) {
				open++;
			}
		}
		if (!adaHakim(server, situs)) {
			return;
		}
		RandomSource random = RandomSource.create(situs * 524287L + hari * 6151L);
		for (int n = 0; n < BARU_PER_HARI && open < BUKA_MAKS; n++) {
			List<String> ids = new ArrayList<>(templat.keySet());
			if (buat(server, situs, ids.get(random.nextInt(ids.size())), random) != null) {
				open++;
			}
		}
	}

	private static boolean adaHakim(MinecraftServer server, long situs) {
		CompoundTag p = KotaEkonomiData.get(server).situs(KotaEkonomiData.JABATAN, situs).getCompoundOrEmpty("p");
		for (String key : p.keySet()) {
			if (p.getCompoundOrEmpty(key).getIntOr("r", 0) >= Jabatan.HAKIM_PEMBANTU) {
				return true;
			}
		}
		return false;
	}

	/** Opens a case of template {@code id} at {@code situs} with real residents; returns it, or null (no fitting people). */
	public static @Nullable Kasus buat(MinecraftServer server, long situs, String id, RandomSource random) {
		JsonObject t = templat.get(id);
		if (t == null) {
			return null;
		}
		List<Warga> adults = new ArrayList<>();
		for (Warga w : WargaApi.wargaTempat(situs)) {
			if (w.hidup() && WargaApi.umur(w) >= 16) {
				adults.add(w);
			}
		}
		Warga a = pilih(adults, obj(t, P_PENGGUGAT), random, -1);
		Warga b = a == null ? null : pilih(adults, obj(t, P_TERGUGAT), random, a.id);
		if (a == null || b == null) {
			return null;
		}
		int want = Math.max(2, Math.min(3, t.has("saksi") ? t.get("saksi").getAsInt() : 2));
		List<Integer> witnesses = new ArrayList<>();
		List<Warga> pool = new ArrayList<>(adults);
		Collections.shuffle(pool, new java.util.Random(random.nextLong()));
		int lies = 0;
		for (Warga w : pool) {
			if (witnesses.size() >= want) {
				break;
			}
			if (w.id != a.id && w.id != b.id) {
				if ("licik".equals(Kepribadian.watak(w)) && random.nextInt(3) == 0) {
					lies |= 1 << witnesses.size();
				}
				witnesses.add(w.id);
			}
		}
		if (witnesses.size() < 2) {
			return null;
		}
		CompoundTag site = KotaEkonomiData.get(server).situs(KotaEkonomiData.PERKARA, situs);
		int caseId = site.getIntOr("n", 1);
		site.putInt("n", caseId + 1);
		String right = t.has("benar") ? t.get("benar").getAsString() : "acak";
		int benar = P_PENGGUGAT.equals(right) ? 0 : P_TERGUGAT.equals(right) ? 1 : random.nextInt(2);
		CompoundTag c = new CompoundTag();
		c.putInt("i", caseId);
		c.putString("t", id);
		c.putLong("h", KotaHidup.hari(server));
		c.putInt("a", a.id);
		c.putInt("b", b.id);
		c.putIntArray("s", witnesses.stream().mapToInt(Integer::intValue).toArray());
		c.putInt("benar", benar);
		c.putInt("dusta", lies);
		Warga wrong = benar == 0 ? b : a;
		double bribe = t.has("suap") ? t.get("suap").getAsDouble() : 0.3D;
		String temper = Kepribadian.watak(wrong);
		if ("licik".equals(temper) || "serakah".equals(temper)) {
			bribe *= 2.0D;
		}
		c.putBoolean("suapMau", random.nextDouble() < bribe);
		daftar(server, situs).add(c);
		KotaEkonomiData.get(server).ubah();
		return dari(situs, c);
	}

	private static @Nullable Warga pilih(List<Warga> adults, @Nullable JsonObject side, RandomSource random, int bukan) {
		List<String> roles = new ArrayList<>();
		JsonArray peran = side == null ? null : arr(side, "peran");
		if (peran != null) {
			for (JsonElement e : peran) {
				roles.add(e.getAsString());
			}
		}
		List<Warga> fit = new ArrayList<>();
		for (Warga w : adults) {
			if (w.id != bukan && (roles.isEmpty() || roles.contains(w.peranNpc().id()))) {
				fit.add(w);
			}
		}
		if (fit.isEmpty()) {
			for (Warga w : adults) {
				if (w.id != bukan) {
					fit.add(w);
				}
			}
		}
		return fit.isEmpty() ? null : fit.get(random.nextInt(fit.size()));
	}

	// ================================================================== hearing

	private static @Nullable Component bolehSidang(ServerPlayer player, long situs, @Nullable CompoundTag c) {
		if (Jabatan.pangkat(player.getUUID(), situs) < Jabatan.HAKIM_PEMBANTU) {
			return Component.translatable("kultivator.kota42.perkara.bukan_hakim");
		}
		if (c == null) {
			return Component.translatable("kultivator.kota42.perkara.tiada");
		}
		if (c.getIntOr("put", 0) > 0) {
			return Component.translatable("kultivator.kota42.perkara.sudah");
		}
		return null;
	}

	/**
	 * Calls witness {@code wargaId} of case {@code id}: the testimony is heard; when the judge is within 32 blocks of the
	 * hall's {@code sidang} titik the witness's body walks in and stands there for two minutes. An envelope may appear.
	 */
	public static @Nullable Component panggil(ServerPlayer player, long situs, int id, int wargaId) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = mentah(server, situs, id);
		Component why = bolehSidang(player, situs, c);
		if (why != null) {
			return why;
		}
		int[] witnesses = c.getIntArray("s").orElse(new int[0]);
		int index = -1;
		for (int i = 0; i < witnesses.length; i++) {
			if (witnesses[i] == wargaId) {
				index = i;
			}
		}
		if (index < 0) {
			return Component.translatable("kultivator.kota42.perkara.bukan_saksi");
		}
		c.putInt("sp", c.getIntOr("sp", 0) | (1 << index));
		KotaHidup.tandaiSibuk(player, KotaHidup.SIBUK_SIDANG, 1200);
		Kasus k = dari(situs, c);
		Warga w = WargaApi.byId(wargaId);
		tubuhSaksi(player, situs, k, w);
		Msg.chat(player, Component.translatable("kultivator.kota42.perkara.kesaksian", w == null ? Component.literal("?") : WargaApi.nama(w),
				kesaksian(server, k, index)).withStyle(ChatFormatting.AQUA));
		tawarAmplop(player, situs, c);
		KotaEkonomiData.get(server).ubah();
		return null;
	}

	private static void tubuhSaksi(ServerPlayer player, long situs, Kasus k, @Nullable Warga w) {
		if (w == null || !(player.level() instanceof ServerLevel level) || !KotaHidup.duniaKota(level)) {
			return;
		}
		List<Titik> halls = KotaTitik.titik(level, situs, KotaTitik.SIDANG);
		Titik hall = null;
		for (Titik t : halls) {
			if (t.pos().distSqr(player.blockPosition()) < 32 * 32) {
				hall = t;
			}
		}
		if (hall == null) {
			return;
		}
		String key = situs + ":" + k.id();
		List<KultivatorNpc> bodies = SAKSI.computeIfAbsent(key, x -> new ArrayList<>());
		BlockPos at = hall.pos().relative(hall.hadap().getClockWise(), bodies.size() * 2 - 2);
		KultivatorNpc body = HidupApi.tubuh(level, situs, w.peranNpc(), at, hall.hadap().getOpposite().toYRot(), w.look);
		if (body != null) {
			body.setLonely(64.0D, SAKSI_TICK);
			body.memory().putBoolean("saksi", true);
			HidupApi.tatap(body, player);
			bodies.add(body);
			SAKSI_SAMPAI.put(key, level.getGameTime() + SAKSI_TICK);
		}
	}

	private static void tawarAmplop(ServerPlayer player, long situs, CompoundTag c) {
		if (!c.getBooleanOr("suapMau", false) || c.getLongOr("amp", 0L) > 0L) {
			return;
		}
		long nb = SUAP_DASAR * (Barang.pita(player) + 1L);
		c.putLong("amp", nb);
		Warga wrong = WargaApi.byId(c.getIntOr("benar", 0) == 0 ? c.getIntOr("b", 0) : c.getIntOr("a", 0));
		Msg.chat(player, Component.translatable("kultivator.kota42.perkara.amplop", wrong == null ? Component.literal("?") : WargaApi.nama(wrong),
				NilaiBatu.text(nb)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
	}

	/** Traces evidence {@code indeks} of case {@code id}: its text (and a gossip's chain of spreaders) is revealed. */
	public static @Nullable Component telusuri(ServerPlayer player, long situs, int id, int indeks) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = mentah(server, situs, id);
		Component why = bolehSidang(player, situs, c);
		if (why != null) {
			return why;
		}
		Kasus k = dari(situs, c);
		List<Bukti> list = bukti(server, k);
		if (indeks < 0 || indeks >= list.size()) {
			return Component.translatable("kultivator.kota42.perkara.bukti_tiada");
		}
		c.putInt("bk", c.getIntOr("bk", 0) | (1 << indeks));
		KotaEkonomiData.get(server).ubah();
		Bukti b = bukti(server, dari(situs, c)).get(indeks);
		MutableComponent line = Component.translatable("kultivator.kota42.perkara.bukti_temu", b.teks());
		if (!b.rantai().isEmpty()) {
			MutableComponent chain = Component.empty();
			for (int i = 0; i < b.rantai().size(); i++) {
				chain.append(i == 0 ? Component.empty() : Component.literal(" → ")).append(b.rantai().get(i));
			}
			line.append(Component.literal(" ")).append(Component.translatable("kultivator.kota42.perkara.rantai", chain));
		}
		Msg.chat(player, line.withStyle(ChatFormatting.GRAY));
		tawarAmplop(player, situs, c);
		return null;
	}

	/** Takes ({@code terima}) or refuses the envelope on the table. */
	public static @Nullable Component amplop(ServerPlayer player, long situs, int id, boolean terima) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = mentah(server, situs, id);
		Component why = bolehSidang(player, situs, c);
		if (why != null) {
			return why;
		}
		long nb = c.getLongOr("amp", 0L);
		if (nb <= 0L || c.getIntOr("ampd", 0) != 0) {
			return Component.translatable("kultivator.kota42.perkara.tanpa_amplop");
		}
		c.putInt("ampd", terima ? 1 : 2);
		if (terima) {
			EkonomiApi.beri(player, nb, "kota42.suap");
			TemaApi.tambahInfami(player, INFAMI_SUAP, "suap");
			Msg.chat(player, Component.translatable("kultivator.kota42.perkara.amplop_terima", NilaiBatu.text(nb)).withStyle(ChatFormatting.DARK_RED));
		} else {
			Warga wrong = WargaApi.byId(c.getIntOr("benar", 0) == 0 ? c.getIntOr("b", 0) : c.getIntOr("a", 0));
			if (wrong != null) {
				WargaApi.ubahKesan(wrong, player, -4, "tolak_suap", null);
			}
			Msg.chat(player, Component.translatable("kultivator.kota42.perkara.amplop_tolak").withStyle(ChatFormatting.GREEN));
		}
		KotaEkonomiData.get(server).ubah();
		return null;
	}

	/** Decides case {@code id} with verdict {@code no} (1-3); returns null on success or the refusal. */
	public static @Nullable Component putuskan(ServerPlayer player, long situs, int id, int no) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = mentah(server, situs, id);
		Component why = bolehSidang(player, situs, c);
		if (why != null) {
			return why;
		}
		Kasus k = dari(situs, c);
		JsonObject t = templat.get(k.templat());
		JsonArray verdicts = t == null ? null : arr(t, "putusan");
		if (verdicts == null || no < 1 || no > verdicts.size()) {
			return Component.translatable("kultivator.kota42.perkara.putusan_tiada");
		}
		String untuk = str(verdicts.get(no - 1).getAsJsonObject(), "untuk");
		boolean plaintiffRight = c.getIntOr("benar", 0) == 0;
		Warga right = WargaApi.byId(plaintiffRight ? k.penggugat() : k.tergugat());
		Warga wrong = WargaApi.byId(plaintiffRight ? k.tergugat() : k.penggugat());
		String rightSide = plaintiffRight ? P_PENGGUGAT : P_TERGUGAT;
		int mood;
		Component result;
		if (untuk.equals(rightSide)) {
			mood = SUASANA;
			kesan(right, player, KESAN_BENAR);
			kesan(wrong, player, -3);
			result = Component.translatable("kultivator.kota42.perkara.hasil_benar");
		} else if (P_DAMAI.equals(untuk)) {
			mood = 1;
			kesan(right, player, KESAN_BENAR / 2);
			kesan(wrong, player, 2);
			result = Component.translatable("kultivator.kota42.perkara.hasil_damai");
		} else {
			mood = -SUASANA;
			kesan(right, player, KESAN_SALAH);
			kesan(wrong, player, 6);
			result = Component.translatable("kultivator.kota42.perkara.hasil_salah");
		}
		c.putInt("put", no);
		c.putString("oleh", player.getUUID().toString());
		CompoundTag site = KotaEkonomiData.get(server).situs(KotaEkonomiData.PERKARA, situs);
		long today = KotaHidup.hari(server);
		int now = site.getLongOr("mds", -1L) >= today ? site.getIntOr("md", 0) : 0;
		site.putInt("md", Math.max(-6, Math.min(6, now + mood)));
		site.putLong("mds", today + SUASANA_HARI);
		boolean bribed = c.getIntOr("ampd", 0) == 1;
		Jabatan.catatPutusan(server, player.getUUID(), bribed);
		Jabatan.catatTugasMinggu(server, situs, player.getUUID(), "perkara");
		Jabatan.catatTugas(player, situs);
		KotaHidup.lepasSibuk(player, KotaHidup.SIBUK_SIDANG);
		bubarkanSaksi(situs + ":" + id);
		Msg.chat(player, Component.translatable("kultivator.kota42.perkara.putus", nama(k), putusanTeks(k, no)[0]).withStyle(ChatFormatting.GOLD));
		Msg.chat(player, result.copy().withStyle(mood > 0 ? ChatFormatting.GREEN : mood < 0 ? ChatFormatting.RED : ChatFormatting.YELLOW));
		if (bribed && RandomSource.create(situs ^ id * 0x9E3779B97F4A7C15L).nextDouble() < AUDIT) {
			audit(player, situs, c.getLongOr("amp", 0L));
		}
		if (Jabatan.catatanSidang(server, player.getUUID())[3] >= JUJUR_SEPULUH) {
			Advancements.grant(player, "tempat/kota42_hakim_jujur");
		}
		KotaEkonomiData.get(server).ubah();
		return null;
	}

	private static void kesan(@Nullable Warga w, ServerPlayer player, int delta) {
		if (w != null && delta != 0) {
			WargaApi.ubahKesan(w, player, delta, "perkara", null);
		}
	}

	/** Hakim Bao Qing's audit: one rank down and a fine of twice the bribe (from the purse, then the drawer). */
	private static void audit(ServerPlayer player, long situs, long bribe) {
		MinecraftServer server = player.level().getServer();
		int rank = Jabatan.pangkat(player.getUUID(), situs);
		Jabatan.setPangkat(server, situs, player.getUUID(), rank - 1, null);
		Jabatan.catatAudit(server, player.getUUID());
		long fine = bribe * 2L;
		long paid = fine;
		if (!EkonomiApi.bayar(player, fine, "kota42.denda_audit")) {
			paid = Investasi.dariLaci(server, player.getUUID(), fine);
			if (paid < fine) {
				dev.kultivator.dunia.kota.KotaData.get(server).addReputation(situs, player.getUUID(), -10);
			}
		}
		dev.kultivator.dunia.tempat.ekonomi.KasKota.pajak(server, situs, paid, "denda");
		Msg.title(player, Component.translatable("kultivator.kota42.perkara.audit_judul").withStyle(ChatFormatting.DARK_RED),
				Component.translatable("kultivator.kota42.perkara.audit", NilaiBatu.text(fine)), 10, 70, 20);
	}

	/** Place-mood layer: recent verdicts (+-3 for three days). */
	private static int pengaruhSuasana(long situs) {
		MinecraftServer server = KotaHidup.server();
		if (server == null) {
			return 0;
		}
		CompoundTag site = KotaEkonomiData.get(server).situs(KotaEkonomiData.PERKARA, situs);
		return site.getLongOr("mds", -1L) >= KotaHidup.hari(server) ? site.getIntOr("md", 0) : 0;
	}

	/** Despawns witness bodies whose time ran out (every second). */
	static void tick(MinecraftServer server, int tick) {
		if (SAKSI_SAMPAI.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (String key : List.copyOf(SAKSI_SAMPAI.keySet())) {
			if (SAKSI_SAMPAI.get(key) <= now) {
				bubarkanSaksi(key);
			}
		}
	}

	private static void bubarkanSaksi(String key) {
		List<KultivatorNpc> bodies = SAKSI.remove(key);
		SAKSI_SAMPAI.remove(key);
		if (bodies != null) {
			for (KultivatorNpc npc : bodies) {
				if (!npc.isRemoved()) {
					npc.vanish();
				}
			}
		}
	}

	// ================================================================== json helpers

	private static String str(@Nullable JsonObject o, String key) {
		return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}

	private static @Nullable JsonObject obj(@Nullable JsonObject o, String key) {
		return o != null && o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
	}

	private static @Nullable JsonArray arr(@Nullable JsonObject o, String key) {
		return o != null && o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : null;
	}

	/** A template is usable: names, two sides, three verdicts. */
	static boolean sah(JsonObject t) {
		JsonArray verdicts = arr(t, "putusan");
		return !str(t, "nama").isEmpty() && obj(t, P_PENGGUGAT) != null && obj(t, P_TERGUGAT) != null && verdicts != null && verdicts.size() == 3;
	}

	/** Reloads {@code tempat/perkara/*.json} with the data packs. */
	private static final class Muat extends SimplePreparableReloadListener<Map<String, JsonObject>> {

		@Override
		protected Map<String, JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
			Map<String, JsonObject> out = new LinkedHashMap<>();
			List<Map.Entry<Identifier, Resource>> entries = new ArrayList<>(LISTER.listMatchingResources(manager).entrySet());
			entries.sort(Map.Entry.comparingByKey());
			for (Map.Entry<Identifier, Resource> entry : entries) {
				Identifier id = LISTER.fileToId(entry.getKey());
				try (Reader reader = entry.getValue().openAsReader()) {
					JsonElement json = JsonParser.parseReader(reader);
					if (json.isJsonObject() && sah(json.getAsJsonObject())) {
						out.put(id.getPath(), json.getAsJsonObject());
					} else {
						Kultivator.LOGGER.warn("Court case template {} is incomplete and was skipped", entry.getKey());
					}
				} catch (Exception exception) {
					Kultivator.LOGGER.warn("Court case template {} could not be read", entry.getKey(), exception);
				}
			}
			return Collections.unmodifiableMap(out);
		}

		@Override
		protected void apply(Map<String, JsonObject> loaded, ResourceManager manager, ProfilerFiller profiler) {
			templat = loaded;
			Kultivator.LOGGER.info("Kota hidup: {} court case templates", loaded.size());
		}
	}

	// ================================================================== commands

	/**
	 * {@code /kultivasi kotaku perkara [<id> | panggil <id> <warga> | telusuri <id> <bukti> | putus <id> <no> |
	 * amplop <id> terima|tolak]}.
	 */
	static LiteralArgumentBuilder<CommandSourceStack> perintah() {
		return Commands.literal("perkara").executes(ctx -> daftarCmd(ctx.getSource()))
				.then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> lihat(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id"))))
				.then(Commands.literal("panggil").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.then(Commands.argument("warga", IntegerArgumentType.integer(0)).executes(ctx -> hasil(ctx, (p, s) ->
								panggil(p, s, IntegerArgumentType.getInteger(ctx, "id"), IntegerArgumentType.getInteger(ctx, "warga")))))))
				.then(Commands.literal("telusuri").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.then(Commands.argument("bukti", IntegerArgumentType.integer(1)).executes(ctx -> hasil(ctx, (p, s) ->
								telusuri(p, s, IntegerArgumentType.getInteger(ctx, "id"), IntegerArgumentType.getInteger(ctx, "bukti") - 1))))))
				.then(Commands.literal("putus").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.then(Commands.argument("no", IntegerArgumentType.integer(1, 3)).executes(ctx -> hasil(ctx, (p, s) ->
								putuskan(p, s, IntegerArgumentType.getInteger(ctx, "id"), IntegerArgumentType.getInteger(ctx, "no")))))))
				.then(Commands.literal("amplop").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.then(Commands.literal("terima").executes(ctx -> hasil(ctx, (p, s) -> amplop(p, s, IntegerArgumentType.getInteger(ctx, "id"), true))))
						.then(Commands.literal("tolak").executes(ctx -> hasil(ctx, (p, s) -> amplop(p, s, IntegerArgumentType.getInteger(ctx, "id"), false))))));
	}

	private interface Aksi {
		@Nullable Component jalan(ServerPlayer player, long situs);
	}

	private static int hasil(CommandContext<CommandSourceStack> ctx, Aksi aksi) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatInfo tempat = PajakKota.tempat(player);
		Component why = tempat == null ? Component.translatable("kultivator.kota42.cmd.tanpa_tempat") : aksi.jalan(player, tempat.situsId());
		if (why != null) {
			ctx.getSource().sendFailure(why);
			return 0;
		}
		return 1;
	}

	private static int daftarCmd(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		TempatInfo tempat = PajakKota.tempat(player);
		if (tempat == null) {
			source.sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return 0;
		}
		MinecraftServer server = source.getServer();
		List<Kasus> all = kasus(server, tempat.situsId());
		long today = KotaHidup.hari(server);
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.perkara.judul", PajakKota.nama(server, tempat.situsId()), all.size())
				.withStyle(ChatFormatting.GOLD), false);
		for (Kasus k : all) {
			MutableComponent line = Component.translatable("kultivator.kota42.perkara.baris", k.id(), nama(k), k.saksi().length,
					bukti(server, k).size(), Math.max(0L, BUKA_HARI - (today - k.hari())));
			if (k.selesai()) {
				line.append(Component.translatable("kultivator.kota42.perkara.baris_putus").withStyle(ChatFormatting.DARK_GRAY));
			} else {
				line.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/kultivasi kotaku perkara " + k.id())));
			}
			source.sendSuccess(() -> line, false);
		}
		return all.size();
	}

	private static int lihat(CommandSourceStack source, int id) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		TempatInfo tempat = PajakKota.tempat(player);
		MinecraftServer server = source.getServer();
		Kasus k = tempat == null ? null : kasus(server, tempat.situsId(), id);
		if (k == null) {
			source.sendFailure(Component.translatable("kultivator.kota42.perkara.tiada"));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.perkara.kepala", nama(k)).withStyle(ChatFormatting.GOLD), false);
		source.sendSuccess(() -> ringkas(k).copy().withStyle(ChatFormatting.GRAY), false);
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.perkara.pihak_a", klaim(k, P_PENGGUGAT)), false);
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.perkara.pihak_b", klaim(k, P_TERGUGAT)), false);
		for (int i = 0; i < k.saksi().length; i++) {
			Warga w = WargaApi.byId(k.saksi()[i]);
			boolean called = (k.dipanggil() & (1 << i)) != 0;
			int wid = k.saksi()[i];
			MutableComponent line = Component.translatable("kultivator.kota42.perkara.saksi", w == null ? Component.literal("?") : WargaApi.nama(w));
			if (called) {
				line.append(Component.literal(": ")).append(kesaksian(server, k, i).copy().withStyle(ChatFormatting.AQUA));
			} else {
				line.append(Component.translatable("kultivator.kota42.perkara.tombol_panggil").withStyle(style -> style.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/kultivasi kotaku perkara panggil " + k.id() + " " + wid))));
			}
			source.sendSuccess(() -> line, false);
		}
		for (Bukti b : bukti(server, k)) {
			MutableComponent line = Component.translatable("kultivator.kota42.perkara.bukti", Component.translatable("kultivator.kota42.perkara.jenis." + b.jenis()),
					b.barang().isEmpty() ? Component.empty() : b.barang().getHoverName(), b.teks());
			if (!b.ditelusuri()) {
				int no = b.indeks() + 1;
				line.append(Component.translatable("kultivator.kota42.perkara.tombol_telusuri").withStyle(style -> style.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/kultivasi kotaku perkara telusuri " + k.id() + " " + no))));
			}
			source.sendSuccess(() -> line, false);
		}
		if (k.amplop() > 0L && k.amplopKeadaan() == 0) {
			source.sendSuccess(() -> Component.translatable("kultivator.kota42.perkara.amplop_meja", NilaiBatu.text(k.amplop()))
					.withStyle(ChatFormatting.DARK_RED)
					.append(Component.translatable("kultivator.kota42.perkara.tombol_terima").withStyle(style -> style.withColor(ChatFormatting.RED)
							.withClickEvent(new ClickEvent.RunCommand("/kultivasi kotaku perkara amplop " + k.id() + " terima"))))
					.append(Component.translatable("kultivator.kota42.perkara.tombol_tolak").withStyle(style -> style.withColor(ChatFormatting.GREEN)
							.withClickEvent(new ClickEvent.RunCommand("/kultivasi kotaku perkara amplop " + k.id() + " tolak")))), false);
		}
		if (!k.selesai()) {
			for (int no = 1; no <= 3; no++) {
				Component[] v = putusanTeks(k, no);
				int n = no;
				source.sendSuccess(() -> Component.translatable("kultivator.kota42.perkara.pilihan", n, v[0], v[1])
						.withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand("/kultivasi kotaku perkara putus " + k.id() + " " + n))), false);
			}
		}
		return 1;
	}

	// ================================================================== uji

	/** {@code uji kota_hidup ekonomi perkara [buat <templat> | putus <n>]}. */
	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("perkara").executes(ctx -> daftarCmd(ctx.getSource()))
				.then(Commands.literal("buat").then(Commands.argument("templat", StringArgumentType.word()).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TempatInfo tempat = PajakKota.tempat(player);
					String id = StringArgumentType.getString(ctx, "templat");
					Kasus k = tempat == null ? null : buat(ctx.getSource().getServer(), tempat.situsId(), id, RandomSource.create());
					if (k == null) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.uji.perkara_gagal", id, String.join(", ", templat.keySet())));
						return 0;
					}
					return lihat(ctx.getSource(), k.id());
				})))
				.then(Commands.literal("putus").then(Commands.argument("n", IntegerArgumentType.integer(1)).then(Commands.argument("no", IntegerArgumentType.integer(1, 3))
						.executes(ctx -> hasil(ctx, (p, s) -> putuskan(p, s, IntegerArgumentType.getInteger(ctx, "n"), IntegerArgumentType.getInteger(ctx, "no")))))));
	}

	private Perkara() {
	}
}
