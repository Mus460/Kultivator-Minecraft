package dev.kultivator.dunia.kota42.bicara;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Cakap;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Kebiasaan;
import dev.kultivator.dunia.kota42.warga.Suasana;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Gifts and tastes (DESIGN 42.5.7 "Beri"): the item in the player's main hand is handed over; the taste is a table of
 * temperament x habit x role x life stage: the drunkard loves wine and hates tea(!), the tea lover loves tea, the
 * bookworm loves manuals and low technique jade, children love sweets and hate medicine, the vain love cloth and
 * flowers, the cat friend loves fish, the sick love medicine, monks hate wine and meat. A liked gift moves the impression
 * +8, a plain one +3, a hated one -5, and only one gift in 3 days counts ({@code Ingatan.hadiahBoleh}); a gift worth at
 * least 50 NB to a poor household (prosperity under 30) counts +15 and births the {@code DERMAWAN} gossip.
 * <p>
 * Data {@code data/kultivator/tempat/selera.json} (optional, 42B-C) extends or replaces the built-in table:
 * <pre>
 * { "ganti": false,                                  true: the data rules replace the built-in rules
 *   "rasa": { "&lt;taste id&gt;": { "id": ["minecraft:cookie"], "item": ["permen", "kue"] (path parts), "tag": ["minecraft:fishes"],
 *                              "kategori": ["obat"] } },               a taste added or redefined
 *   "aturan": [ { "kebiasaan": "pemabuk", "watak": "licik", "peran": "RAHIB", "kelompok": "rahib", "tahap": "anak",
 *                 "sakit": true, "suka": ["arak"], "benci": ["teh"] } ] }   every given key must hold
 * </pre>
 * Taste names are lang keys {@code kultivator.kota42.bicara.rasa.<id>}.
 */
public final class Selera {

	/** Impression of a liked, plain and hated gift (42.4.10 table). */
	public static final int SUKA = 8;
	public static final int BIASA = 3;
	public static final int BENCI = -5;
	/** Generous gift: worth and the prosperity under which the household counts as poor. */
	public static final long DERMAWAN_NB = 50L;
	public static final int MISKIN = 30;
	public static final int DERMAWAN_KESAN = 15;

	/** One taste: what items it covers. */
	private record Rasa(Set<String> ids, List<String> potongan, List<TagKey<Item>> tags, Set<Kategori> kategori) {
		boolean cocok(ItemStack s) {
			String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
			if (ids.contains(id)) {
				return true;
			}
			String path = id.substring(id.indexOf(':') + 1);
			for (String p : potongan) {
				if (path.contains(p)) {
					return true;
				}
			}
			for (TagKey<Item> t : tags) {
				if (s.is(t)) {
					return true;
				}
			}
			return !kategori.isEmpty() && kategori.contains(Kategori.dari(s));
		}
	}

	/** One rule: who (every given condition must hold) likes and hates which tastes. */
	private record Aturan(@Nullable String kebiasaan, @Nullable String watak, @Nullable NpcRole peran, @Nullable NpcRole.Kelompok kelompok,
			@Nullable String tahap, @Nullable Boolean sakit, List<String> suka, List<String> benci) {
		boolean berlaku(Warga w) {
			if (kebiasaan != null && !Kebiasaan.punya(w, kebiasaan)) {
				return false;
			}
			if (watak != null && !watak.equals(PermintaanWarga.watak(w))) {
				return false;
			}
			if (peran != null && w.peranNpc() != peran) {
				return false;
			}
			if (kelompok != null && w.peranNpc().kelompok != kelompok) {
				return false;
			}
			if (tahap != null && !tahap.equals(tahapGolongan(WargaApi.umur(w)))) {
				return false;
			}
			return sakit == null || sakit == (w.kesehatan < 30);
		}
	}

	private static volatile Map<String, Rasa> rasa = Map.of();
	private static volatile List<Aturan> aturan = List.of();

	static {
		muat(new JsonObject());
	}

	/** "anak" (under 13), "remaja", "dewasa", "tua" (55 on). */
	static String tahapGolongan(int umur) {
		if (umur < 13) {
			return "anak";
		}
		if (umur < 16) {
			return "remaja";
		}
		return umur >= 55 ? "tua" : "dewasa";
	}

	// ================================================================== table

	private static Map<String, Rasa> rasaBawaan() {
		Map<String, Rasa> m = new LinkedHashMap<>();
		m.put("arak", rasa(List.of(), List.of("arak", "wine", "jiu_", "anggur"), List.of(), List.of()));
		m.put("teh", rasa(List.of(), List.of("teh_", "_teh", "tea"), List.of(), List.of()));
		m.put("kitab", rasa(List.of("minecraft:book", "minecraft:written_book", "minecraft:writable_book", "minecraft:enchanted_book"),
				List.of("kitab", "manual", "gulungan", "giok_teknik", "scroll"), List.of(), List.of()));
		m.put("manis", rasa(List.of("minecraft:cookie", "minecraft:cake", "minecraft:pumpkin_pie", "minecraft:honey_bottle", "minecraft:sweet_berries"),
				List.of("permen", "tanghulu", "kue_", "bakpao", "gula", "manisan"), List.of(), List.of()));
		m.put("kain", rasa(List.of(), List.of("kain", "sutra", "silk", "jubah"), List.of("minecraft:wool"), List.of("kain")));
		m.put("bunga", rasa(List.of(), List.of("bunga", "flower"), List.of("minecraft:flowers"), List.of()));
		m.put("ikan", rasa(List.of(), List.of("ikan", "fish"), List.of("minecraft:fishes"), List.of()));
		m.put("obat", rasa(List.of(), List.of(), List.of(), List.of("obat")));
		m.put("herba", rasa(List.of(), List.of(), List.of(), List.of("herba")));
		m.put("jimat", rasa(List.of(), List.of("jimat", "talisman"), List.of(), List.of("jimat")));
		m.put("senjata", rasa(List.of(), List.of(), List.of(), List.of("senjata")));
		m.put("mewah", rasa(List.of("minecraft:gold_ingot", "minecraft:emerald", "minecraft:diamond", "minecraft:amethyst_shard"),
				List.of("giok", "mutiara", "pearl"), List.of(), List.of("mewah")));
		m.put("daging", rasa(List.of("minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken", "minecraft:cooked_mutton",
				"minecraft:cooked_rabbit", "minecraft:beef", "minecraft:porkchop", "minecraft:chicken", "minecraft:mutton", "minecraft:rabbit"),
				List.of("daging", "meat"), List.of(), List.of()));
		m.put("biji", rasa(List.of("minecraft:wheat_seeds", "minecraft:melon_seeds", "minecraft:pumpkin_seeds", "minecraft:beetroot_seeds"),
				List.of(), List.of(), List.of()));
		return m;
	}

	private static Rasa rasa(List<String> ids, List<String> potongan, List<String> tags, List<String> kategori) {
		List<TagKey<Item>> t = new ArrayList<>();
		for (String tag : tags) {
			Identifier id = Identifier.tryParse(tag);
			if (id != null) {
				t.add(TagKey.create(Registries.ITEM, id));
			}
		}
		Set<Kategori> k = new LinkedHashSet<>();
		for (String kat : kategori) {
			Kategori x = Kategori.byId(kat);
			if (x != null) {
				k.add(x);
			}
		}
		return new Rasa(Set.copyOf(ids), List.copyOf(potongan), List.copyOf(t), Set.copyOf(k));
	}

	private static List<Aturan> aturanBawaan() {
		List<Aturan> a = new ArrayList<>();
		a.add(new Aturan("pemabuk", null, null, null, null, null, List.of("arak"), List.of("teh")));
		a.add(new Aturan("pecinta_teh", null, null, null, null, null, List.of("teh"), List.of()));
		a.add(new Aturan("kutu_buku", null, null, null, null, null, List.of("kitab"), List.of()));
		a.add(new Aturan(null, null, null, null, "anak", null, List.of("manis"), List.of("obat")));
		a.add(new Aturan("pesolek", null, null, null, null, null, List.of("kain", "bunga"), List.of()));
		a.add(new Aturan("teman_kucing", null, null, null, null, null, List.of("ikan"), List.of()));
		a.add(new Aturan(null, null, null, null, null, Boolean.TRUE, List.of("obat"), List.of()));
		a.add(new Aturan(null, null, null, NpcRole.Kelompok.RAHIB, null, null, List.of("teh"), List.of("arak", "daging")));
		a.add(new Aturan("pendoa_kuil", null, null, null, null, null, List.of("jimat"), List.of("arak")));
		a.add(new Aturan("takhayul", null, null, null, null, null, List.of("jimat"), List.of()));
		a.add(new Aturan("pemuja_kultivator", null, null, null, null, null, List.of("senjata", "jimat"), List.of()));
		a.add(new Aturan("penyair", null, null, null, null, null, List.of("arak", "kitab"), List.of()));
		a.add(new Aturan("pemancing", null, null, null, null, null, List.of("ikan"), List.of()));
		a.add(new Aturan("penyulam", null, null, null, null, null, List.of("kain"), List.of()));
		a.add(new Aturan("burung_sangkar", null, null, null, null, null, List.of("biji"), List.of()));
		a.add(new Aturan("pelukis_gula", null, null, null, null, null, List.of("manis"), List.of()));
		a.add(new Aturan("tari_kipas", null, null, null, null, null, List.of("kain", "bunga"), List.of()));
		a.add(new Aturan("pelit", null, null, null, null, null, List.of("mewah"), List.of()));
		a.add(new Aturan(null, "serakah", null, null, null, null, List.of("mewah"), List.of()));
		a.add(new Aturan(null, "sombong", null, null, null, null, List.of("mewah"), List.of("biji")));
		a.add(new Aturan(null, "bijak", null, null, null, null, List.of("kitab", "teh"), List.of()));
		a.add(new Aturan(null, "penakut", null, null, null, null, List.of("jimat"), List.of("senjata")));
		a.add(new Aturan(null, null, null, NpcRole.Kelompok.CENDEKIA, null, null, List.of("kitab"), List.of()));
		a.add(new Aturan(null, null, NpcRole.TABIB, null, null, null, List.of("herba"), List.of()));
		a.add(new Aturan(null, null, NpcRole.ALKEMIS, null, null, null, List.of("herba"), List.of()));
		a.add(new Aturan(null, null, NpcRole.KOKI, null, null, null, List.of("daging", "ikan"), List.of()));
		a.add(new Aturan(null, null, null, NpcRole.Kelompok.PEKERJA, "dewasa", null, List.of("daging"), List.of()));
		a.add(new Aturan(null, null, null, null, "tua", null, List.of("teh"), List.of()));
		return a;
	}

	/** Loads {@code selera.json} over the built-in table (called by the datapack loader; an empty object = built-in only). */
	static void muat(JsonObject json) {
		Map<String, Rasa> r = rasaBawaan();
		List<Aturan> a = json.has("ganti") && json.get("ganti").getAsBoolean() ? new ArrayList<>() : aturanBawaan();
		try {
			if (json.has("rasa") && json.get("rasa").isJsonObject()) {
				for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("rasa").entrySet()) {
					if (e.getKey().startsWith("_") || !e.getValue().isJsonObject()) {
						continue;
					}
					JsonObject o = e.getValue().getAsJsonObject();
					r.put(e.getKey(), rasa(daftar(o, "id"), daftar(o, "item"), daftar(o, "tag"), daftar(o, "kategori")));
				}
			}
			if (json.has("aturan") && json.get("aturan").isJsonArray()) {
				for (JsonElement e : json.getAsJsonArray("aturan")) {
					if (!e.isJsonObject()) {
						continue;
					}
					JsonObject o = e.getAsJsonObject();
					NpcRole peran = o.has("peran") ? NpcRole.byId(o.get("peran").getAsString().toLowerCase(Locale.ROOT)) : null;
					if (peran == null && o.has("peran")) {
						try {
							peran = NpcRole.valueOf(o.get("peran").getAsString().toUpperCase(Locale.ROOT));
						} catch (IllegalArgumentException ignored) {
							Kultivator.LOGGER.warn("selera.json: unknown role {}", o.get("peran").getAsString());
							continue;
						}
					}
					NpcRole.Kelompok kelompok = null;
					if (o.has("kelompok")) {
						try {
							kelompok = NpcRole.Kelompok.valueOf(o.get("kelompok").getAsString().toUpperCase(Locale.ROOT));
						} catch (IllegalArgumentException ignored) {
							Kultivator.LOGGER.warn("selera.json: unknown group {}", o.get("kelompok").getAsString());
							continue;
						}
					}
					a.add(new Aturan(teks(o, "kebiasaan"), teks(o, "watak"), peran, kelompok, teks(o, "tahap"),
							o.has("sakit") ? o.get("sakit").getAsBoolean() : null, daftar(o, "suka"), daftar(o, "benci")));
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("selera.json could not be read fully; the built-in tastes stay", exception);
		}
		rasa = Map.copyOf(r);
		aturan = List.copyOf(a);
	}

	private static List<String> daftar(JsonObject o, String key) {
		List<String> out = new ArrayList<>();
		if (o.has(key) && o.get(key).isJsonArray()) {
			for (JsonElement e : o.getAsJsonArray(key)) {
				out.add(e.getAsString());
			}
		}
		return out;
	}

	private static @Nullable String teks(JsonObject o, String key) {
		return o.has(key) ? o.get(key).getAsString() : null;
	}

	// ================================================================== reading tastes

	/** +1 liked, 0 plain, -1 hated: a hate always beats a like (the drunkard and his tea). */
	public static int nilai(Warga w, ItemStack s) {
		if (w == null || s == null || s.isEmpty()) {
			return 0;
		}
		boolean suka = false;
		for (Aturan a : aturan) {
			if (!a.berlaku(w)) {
				continue;
			}
			for (String id : a.benci()) {
				Rasa r = rasa.get(id);
				if (r != null && r.cocok(s)) {
					return -1;
				}
			}
			for (String id : a.suka()) {
				Rasa r = rasa.get(id);
				if (r != null && r.cocok(s)) {
					suka = true;
				}
			}
		}
		return suka ? 1 : 0;
	}

	/** The first liked taste of {@code w} the item belongs to, or null (quest tokens {@code rasa:<id>}). */
	static @Nullable String rasaSuka(Warga w, ItemStack s) {
		for (String id : suka(w)) {
			Rasa r = rasa.get(id);
			if (r != null && r.cocok(s)) {
				return id;
			}
		}
		return null;
	}

	/** The tastes a warga likes, rule order, without duplicates. */
	static List<String> suka(Warga w) {
		Set<String> out = new LinkedHashSet<>();
		for (Aturan a : aturan) {
			if (a.berlaku(w)) {
				out.addAll(a.suka());
			}
		}
		return List.copyOf(out);
	}

	/** "Sepertinya dia suka teh" (the first liked taste), or the plain hint when nothing special. */
	public static Component petunjuk(Warga w) {
		List<String> s = suka(w);
		return s.isEmpty() ? Component.translatable("kultivator.kota42.bicara.beri.petunjuk_umum")
				: Component.translatable("kultivator.kota42.bicara.beri.petunjuk", Component.translatable("kultivator.kota42.bicara.rasa." + s.get(0)));
	}

	// ================================================================== tablet Beri

	/** {@link KotaLembar.Dialog#BERI}: the item in hand, the taste hint, and whether a gift counts today. */
	static CompoundTag lembar(ServerPlayer player, Warga w) {
		CompoundTag c = new CompoundTag();
		ItemStack hand = player.getMainHandItem();
		if (!hand.isEmpty()) {
			CompoundTag tangan = new CompoundTag();
			LembarKunci.barang(tangan, hand.copyWithCount(1), player.registryAccess());
			c.put(LembarKunci.TANGAN, tangan);
		}
		Ingatan ing = WargaApi.ingatan(w, player.getUUID());
		LembarKunci.teks(c, KotaLembar.Dialog.PETUNJUK, ing.kenal >= 2 || Kebiasaan.punya(w, "pemabuk") ? petunjuk(w)
				: Component.translatable("kultivator.kota42.bicara.beri.belum_kenal"));
		long hari = KotaHidup.hari(player.level().getServer());
		long berikut = ing.hadiahHari + Ingatan.HADIAH_JEDA;
		c.putLong(KotaLembar.Dialog.BERIKUT, Math.max(hari, berikut));
		c.putBoolean(LembarKunci.BISA, !hand.isEmpty());
		if (hand.isEmpty()) {
			LembarKunci.teks(c, LembarKunci.ALASAN, Component.translatable("kultivator.kota42.bicara.beri.tangan_kosong"));
		} else if (hari < berikut) {
			LembarKunci.teks(c, LembarKunci.ALASAN, Component.translatable("kultivator.kota42.bicara.beri.tiga_hari", berikut - hari));
		}
		return c;
	}

	/** [Beri]: one item of the main hand goes to the warga; impression, memory, feeling and maybe the generous gossip. */
	static BicaraWarga.Balasan beri(ServerPlayer player, KultivatorNpc npc, Warga w) {
		ItemStack hand = player.getMainHandItem();
		if (hand.isEmpty()) {
			return BicaraWarga.Balasan.gagal(Component.translatable("kultivator.kota42.bicara.beri.tangan_kosong"));
		}
		MinecraftServer server = player.level().getServer();
		long situs = BicaraWarga.situs(npc);
		ItemStack one = hand.copyWithCount(1);
		int taste = nilai(w, one);
		long worth = EkonomiApi.harga(one);
		Ingatan ing = WargaApi.ingatan(w, player.getUUID());
		long hari = KotaHidup.hari(server);
		boolean counts = hari - ing.hadiahHari >= Ingatan.HADIAH_JEDA;
		hand.shrink(1);
		player.setItemInHand(InteractionHand.MAIN_HAND, hand);
		boolean dermawan = counts && taste >= 0 && worth >= DERMAWAN_NB && Suasana.kemakmuranRumahTangga(server, situs, w) < MISKIN;
		String jenis = taste > 0 ? "hadiah_suka" : taste < 0 ? "hadiah_benci" : "hadiah_biasa";
		WargaApi.peristiwaPemain(player, situs, jenis, w);
		WargaData data = WargaData.get(server);
		int idx = data.indeksPemain(player.getUUID());
		if (counts) {
			Cakap.peristiwa(w, taste < 0 ? "hadiah_dibenci" : "hadiah_disukai", Fakta.Rujuk.pemain(idx), taste == 0 ? 0.5F : 1.0F);
		}
		if (dermawan) {
			// the gift itself already counted +8/+3 above; a generous gift tops it up to +15
			ing.ubah(DERMAWAN_KESAN - (taste > 0 ? SUKA : BIASA), null, false, hari);
			data.ubah();
			GosipWarga.lahir(player.level(), situs, "DERMAWAN", Fakta.Rujuk.pemain(idx), Fakta.Rujuk.warga(w.id));
		}
		String reaksi = dermawan ? "dermawan" : taste > 0 ? "suka" : taste < 0 ? "benci" : "biasa";
		Component line = Component.translatable("kultivator.kota42.bicara.beri.reaksi." + reaksi + "." + Math.floorMod(w.id + (int) hari, 3),
				one.getHoverName());
		npc.say(line, 100);
		HidupApi.klip(npc, taste < 0 ? GerakId.MARAH_HENTAK : taste > 0 ? GerakId.TERTAWA : GerakId.HORMAT, 0, 40);
		if (taste >= 0 && VoiceEvents.exists("kota42.terima_kasih")) {
			Voice.say(npc, "kota42.terima_kasih", null);
		}
		if (one.has(DataComponents.FOOD) && w.kesehatan < 100) {
			w.kesehatan = Math.min(100, w.kesehatan + 2);
			data.ubah();
		}
		TujuanKota.beri(player, w, one);
		Component pesan = !counts ? Component.translatable("kultivator.kota42.bicara.beri.tidak_dihitung")
				: Component.translatable("kultivator.kota42.bicara.beri.pesan." + reaksi);
		return new BicaraWarga.Balasan(line, pesan, counts && taste >= 0);
	}

	private Selera() {
	}
}
