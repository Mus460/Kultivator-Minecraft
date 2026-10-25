package dev.kultivator.dunia.kota42.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.ekonomi.Jabatan;
import dev.kultivator.dunia.kota42.ekonomi.KotaEkonomi;
import dev.kultivator.dunia.kota42.tumbuh.BahanKota;
import dev.kultivator.dunia.kota42.tumbuh.Kavling;
import dev.kultivator.dunia.kota42.tumbuh.Kemakmuran;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.kultivator.dunia.kota42.ui.KotaLembar.Kavling.*;

/**
 * {@code kavling} (DESIGN 42.11.13, keys {@link KotaLembar.Kavling}) — one growth lot of 42F, drawn as the client's
 * {@code LayarKavling} (style giok). From the frozen contract C42.F1 ({@code Kavling.daftar}, {@code BahanKota.stok},
 * {@code Kemakmuran.nilai}) plus the 42W-G plan sidecar {@code structure/kota_tumbuh/rencana.json} (stage names,
 * materials per stage, needs) read once per server: the lot card with stage x/5 and its name, this stage's materials
 * against the town stock and the bag, a top-down silhouette of the finished stage 5 (column heights and map colours of
 * the plan's last template, &le; 32 x 32), the requirements (prosperity &ge; 55, Kas Kota, blocked), tab Pilih (rank 4+:
 * every plan of the lot's style with the need it answers; picks through 42C-2 {@code Jabatan.pilihRencana}) and tab Dana.
 * Progress inside a stage, crews, donors and selling or donating materials belong to 42F: it adds them with
 * {@link LembarKotaDasar#addPengisi} / {@link LembarKotaDasar#daftarAksi} (verbs {@code jual}, {@code sumbang},
 * {@code sumbang_nb}).
 */
public final class LembarKavling {

	private static final String K = LembarKotaDasar.K + "kavling.";
	private static final Identifier RENCANA_JSON = Kultivator.id("structure/kota_tumbuh/rencana.json");
	/** Prosperity a lot needs before its next stage (42.9.4). */
	public static final int MAKMUR_MIN = 55;
	private static final int SILUET_MAKS = 32;

	private static @Nullable JsonObject rencana;
	private static @Nullable MinecraftServer rencanaServer;
	private static final Map<String, CompoundTag> SILUET = new HashMap<>();

	static void init() {
		LembarKotaDasar.daftar(LAYAR, LembarKavling::bangun);
		LembarKotaDasar.bawaan(LAYAR, OP_RENCANA, (p, npc, situs, op) -> LembarKotaDasar.hasil(p,
				Jabatan.pilihRencana(p, situs, LembarKunci.arg(op, 1)), Component.translatable(K + "rencana_ok")));
	}

	// ================================================================== plans (rencana.json of 42W-G)

	private static @Nullable JsonObject rencana(MinecraftServer server) {
		if (rencana != null && rencanaServer == server) {
			return rencana;
		}
		rencanaServer = server;
		rencana = null;
		SILUET.clear();
		Optional<Resource> res = server.getResourceManager().getResource(RENCANA_JSON);
		if (res.isPresent()) {
			try (Reader reader = res.get().openAsReader()) {
				JsonElement root = JsonParser.parseReader(reader);
				if (root.isJsonObject()) {
					rencana = root.getAsJsonObject();
				}
			} catch (Exception ex) {
				Kultivator.LOGGER.warn("kota_tumbuh/rencana.json unreadable for the kavling screen", ex);
			}
		}
		return rencana;
	}

	private static @Nullable JsonObject plan(MinecraftServer server, String id) {
		JsonObject root = rencana(server);
		JsonObject all = root == null || !root.has("rencana") ? null : root.getAsJsonObject("rencana");
		return all == null || !all.has(id) ? null : all.getAsJsonObject(id);
	}

	private static List<String> plansOfStyle(MinecraftServer server, String gaya) {
		List<String> out = new ArrayList<>();
		JsonObject root = rencana(server);
		JsonObject all = root == null || !root.has("rencana") ? null : root.getAsJsonObject("rencana");
		if (all != null) {
			for (String id : all.keySet()) {
				if (id.startsWith(gaya + "/")) {
					out.add(id);
				}
			}
		}
		return out;
	}

	/** The material of a block id per the plan palette ("" when it costs nothing). */
	private static String bahanBlok(MinecraftServer server, String blockId) {
		JsonObject root = rencana(server);
		if (root == null) {
			return "";
		}
		JsonObject palet = root.has("palet_bahan") ? root.getAsJsonObject("palet_bahan") : null;
		if (palet != null && palet.has(blockId)) {
			return palet.get(blockId).getAsString();
		}
		JsonObject genteng = root.has("atap_genteng") ? root.getAsJsonObject("atap_genteng") : null;
		if (genteng != null) {
			for (String g : genteng.keySet()) {
				JsonElement e = genteng.get(g);
				if (e.isJsonArray()) {
					for (JsonElement b : e.getAsJsonArray()) {
						if (b.isJsonPrimitive() && blockId.equals(b.getAsString())) {
							return BH_GENTENG;
						}
					}
				} else if (e.isJsonPrimitive() && blockId.equals(e.getAsString())) {
					return BH_GENTENG;
				}
			}
		}
		return "";
	}

	// ================================================================== rows for other screens

	/** The {@code kavling} rows of {@code kota_buku}/{@code jabatan}: id I, nama T, rencana T, tahap I, tahap_total I, keadaan S, x I, z I. */
	public static ListTag barisKota(long situs) {
		List<CompoundTag> rows = new ArrayList<>();
		MinecraftServer server = dev.kultivator.dunia.kota42.KotaHidup.server();
		for (Kavling.Info k : Kavling.daftar(situs)) {
			CompoundTag t = new CompoundTag();
			t.putInt(LembarKunci.ID, k.id());
			LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(K + "nama", k.id()));
			LembarKunci.teks(t, RENCANA, namaRencana(k.gaya(), k.jenis()));
			t.putInt(KotaLembar.Kavling.TAHAP_TOTAL, Kavling.SELESAI);
			t.putInt("tahap", Math.max(0, k.tahap()));
			t.putString(LembarKunci.KEADAAN, keadaan(k));
			t.putInt(LembarKunci.X, k.pos().getX());
			t.putInt(LembarKunci.Z, k.pos().getZ());
			rows.add(t);
		}
		return LembarKotaDasar.daftar(rows);
	}

	private static String keadaan(Kavling.Info k) {
		if (!k.layak()) {
			return KV_TIDAK_LAYAK;
		}
		if (k.selesai()) {
			return KV_SELESAI;
		}
		if (k.jenis().isEmpty()) {
			return KV_KOSONG;
		}
		return k.tahap() > 0 ? KV_DIBANGUN : KV_MENUNGGU;
	}

	static Component namaRencana(String gaya, String jenis) {
		if (jenis.isEmpty()) {
			return Component.translatable(K + "rencana_belum");
		}
		return Component.translatable(K + "rencana_gaya", Component.translatable(K + "jenis." + jenis), Component.translatable(K + "gaya." + gaya));
	}

	// ================================================================== the sheet

	private static @Nullable Kavling.Info pilihan(ServerPlayer p, long situs, List<Kavling.Info> semua) {
		String pilih = LembarKotaDasar.pilih(p, LAYAR);
		for (Kavling.Info k : semua) {
			if (Integer.toString(k.id()).equals(pilih)) {
				return k;
			}
		}
		Kavling.Info dekat = null;
		double best = Double.MAX_VALUE;
		for (Kavling.Info k : semua) {
			double d = k.pos().distSqr(p.blockPosition());
			if (d < best) {
				best = d;
				dekat = k;
			}
		}
		return dekat;
	}

	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId, long situs) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = LembarKotaDasar.lembar(player, Component.translatable(K + "judul"), LembarKunci.GAYA_GIOK);
		LembarKotaDasar.kepala(tag, player, situs);
		if (situs == 0L) {
			return LembarKotaDasar.kunci(tag, Component.translatable(LembarKotaDasar.K + "jabatan.tanpa_tempat"));
		}
		List<Kavling.Info> semua = Kavling.daftar(situs);
		Kavling.Info k = pilihan(player, situs, semua);
		if (k == null) {
			CompoundTag kosong = new CompoundTag();
			LembarKunci.teks(kosong, LembarKunci.TEKS, Component.translatable(K + "kosong"));
			tag.put(LembarKunci.KOSONG, kosong);
			return tag;
		}
		String rencanaId = k.jenis().isEmpty() ? "" : k.gaya() + "/" + k.jenis();
		JsonObject plan = rencanaId.isEmpty() ? null : plan(server, rencanaId);
		int tahap = Math.max(0, k.tahap());
		int berikut = Math.min(Kavling.SELESAI, tahap + 1);

		CompoundTag c = new CompoundTag();
		c.putInt(LembarKunci.ID, k.id());
		LembarKunci.teks(c, LembarKunci.NAMA, Component.translatable(K + "nama", k.id()));
		LembarKunci.teks(c, RENCANA, namaRencana(k.gaya(), k.jenis()));
		c.putString(RENCANA_ID, rencanaId);
		c.putString(GAYA_KAVLING, k.gaya());
		c.putString(JENIS, k.jenis());
		c.putInt("tahap", tahap);
		c.putInt(TAHAP_TOTAL, Kavling.SELESAI);
		LembarKunci.teks(c, TAHAP_NAMA, Component.translatable(K + "tahap." + (k.selesai() ? Kavling.SELESAI : berikut)));
		c.putInt(KEMAJUAN, 0);
		c.putInt(REGU, 0);
		c.putInt(LembarKunci.SISA_HARI, -1);
		String keadaan = keadaan(k);
		c.putString(LembarKunci.KEADAAN, keadaan);
		c.putInt(LembarKunci.X, k.pos().getX());
		c.putInt(LembarKunci.Z, k.pos().getZ());
		int makmur = Kemakmuran.nilai(situs);
		long kas = KotaEkonomi.kasKota(server, situs);
		Component halang = null;
		if (KV_TIDAK_LAYAK.equals(keadaan)) {
			halang = Component.translatable(K + "halang.tidak_layak");
		} else if (!k.selesai() && makmur < MAKMUR_MIN) {
			halang = Component.translatable(K + "halang.makmur", makmur, MAKMUR_MIN);
		}
		if (halang != null) {
			LembarKunci.teks(c, TERHALANG, halang);
		}
		tag.put(KAVLING, c);

		if (plan != null) {
			CompoundTag s = siluet(server, rencanaId, plan);
			if (s != null) {
				tag.put(KotaLembar.Kavling.SILUET, s); // the cache map SILUET shadows the static-imported key
			}
		}

		// materials of the next stage against the town stock and the bag
		int[] stok = BahanKota.stok(situs);
		int[] tas = tasBahan(server, player);
		JsonObject bahanTahap = bahanTahap(plan, berikut);
		List<CompoundTag> bahan = new ArrayList<>();
		for (int i = 0; i < SEMUA_BAHAN.size(); i++) {
			String id = SEMUA_BAHAN.get(i);
			int perlu = bahanTahap != null && bahanTahap.has(id) ? bahanTahap.get(id).getAsInt() : 0;
			if (perlu <= 0 && (i >= stok.length || stok[i] <= 0)) {
				continue;
			}
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, id);
			LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(K + "bahan." + id));
			int ada = i < stok.length ? stok[i] : 0;
			t.putInt(TERKUMPUL, Math.min(ada, Math.max(perlu, ada)));
			t.putInt(PERLU, perlu);
			t.putInt(TAS, tas[i]);
			t.putLong(LembarKunci.HARGA, hargaBahan(id));
			t.putBoolean(LembarKunci.OK, ada >= perlu);
			bahan.add(t);
		}
		tag.put(BAHAN, LembarKotaDasar.daftar(bahan));

		List<CompoundTag> syarat = new ArrayList<>();
		syarat.add(LembarKotaDasar.syarat(makmur >= MAKMUR_MIN, Component.translatable(K + "syarat.makmur", MAKMUR_MIN),
				Component.literal(Integer.toString(makmur))));
		long biayaTahap = biayaTahap(plan, berikut);
		syarat.add(LembarKotaDasar.syarat(kas >= biayaTahap, Component.translatable(K + "syarat.kas", biayaTahap),
				Component.literal(Long.toString(kas))));
		syarat.add(LembarKotaDasar.syarat(halang == null, Component.translatable(K + "syarat.halang"),
				halang == null ? Component.translatable(K + "syarat.halang_tidak") : halang));
		tag.put(LembarKunci.SYARAT, LembarKotaDasar.daftar(syarat));

		int pangkat = Jabatan.pangkat(player.getUUID(), situs);
		if (pangkat >= Jabatan.WAKIL && !k.selesai()) {
			String dipilih = Jabatan.pilihanRencana(server, situs);
			List<CompoundTag> pilihan = new ArrayList<>();
			for (String id : plansOfStyle(server, k.gaya())) {
				JsonObject p = plan(server, id);
				if (p == null) {
					continue;
				}
				String jenis = p.has("jenis") ? p.get("jenis").getAsString() : id.substring(id.indexOf('/') + 1);
				CompoundTag t = new CompoundTag();
				t.putString(LembarKunci.ID, id);
				LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(K + "jenis." + jenis));
				LembarKunci.teks(t, LembarKunci.TEKS, kebutuhan(p));
				t.putLong(LembarKunci.HARGA, biayaTotal(p));
				t.putBoolean(KotaLembar.PILIH, id.equals(dipilih));
				t.putBoolean(LembarKunci.BISA, true);
				pilihan.add(t);
			}
			tag.put(PILIHAN, LembarKotaDasar.daftar(pilihan));
		} else {
			CompoundTag kunciTab = new CompoundTag();
			LembarKunci.teks(kunciTab, TAB_PILIH, k.selesai() ? Component.translatable(K + "kunci_selesai")
					: Component.translatable(K + "kunci_pilih", Jabatan.gelar(server, situs, Jabatan.WAKIL)));
			tag.put(LembarKunci.TAB_KUNCI, kunciTab);
		}
		tag.put(DANA, new ListTag());
		tag.putLong(DANA_TOTAL, 0L);
		tag.putLong(BIAYA_TOTAL, plan == null ? 0L : biayaTotal(plan));
		return tag;
	}

	private static @Nullable JsonObject bahanTahap(@Nullable JsonObject plan, int tahap) {
		if (plan == null || !plan.has("tahap")) {
			return null;
		}
		for (JsonElement e : plan.getAsJsonArray("tahap")) {
			JsonObject t = e.getAsJsonObject();
			if (t.has("tahap") && t.get("tahap").getAsInt() == tahap && t.has("bahan")) {
				return t.getAsJsonObject("bahan");
			}
		}
		return null;
	}

	/** Material units are bought at the town price 1.1 x (42.9.4); 2 NB per unit is the plan's base, so the cost of a stage. */
	private static long biayaTahap(@Nullable JsonObject plan, int tahap) {
		JsonObject b = bahanTahap(plan, tahap);
		long n = 0L;
		if (b != null) {
			for (String id : b.keySet()) {
				n += b.get(id).getAsLong() * hargaBahan(id);
			}
		}
		return n;
	}

	private static long biayaTotal(JsonObject plan) {
		long n = 0L;
		for (int t = 1; t <= Kavling.SELESAI; t++) {
			n += biayaTahap(plan, t);
		}
		return n;
	}

	/** Price per material unit in NB (stone and wood 2, tiles and paper 3, cloth 4; x1.1 town markup, rounded up). */
	private static long hargaBahan(String id) {
		return switch (id) {
			case BH_KAIN -> 5L;
			case BH_GENTENG, BH_KACA_KERTAS -> 4L;
			default -> 3L;
		};
	}

	private static Component kebutuhan(JsonObject plan) {
		JsonArray need = plan.has("kebutuhan") ? plan.getAsJsonArray("kebutuhan") : new JsonArray();
		Component out = null;
		for (JsonElement e : need) {
			Component c = Component.translatable(K + "butuh." + e.getAsString());
			out = out == null ? c : out.copy().append(" · ").append(c);
		}
		return out == null ? Component.translatable(K + "butuh.umum") : out;
	}

	/** Matching blocks in the bag per material, by the plan palette (block items only). */
	private static int[] tasBahan(MinecraftServer server, ServerPlayer player) {
		int[] out = new int[SEMUA_BAHAN.size()];
		for (int slot = 0; slot < 36; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) {
				continue;
			}
			String bahan = bahanBlok(server, BuiltInRegistries.BLOCK.getKey(bi.getBlock()).toString());
			int i = SEMUA_BAHAN.indexOf(bahan);
			if (i >= 0) {
				out[i] += stack.getCount();
			}
		}
		return out;
	}

	/** Column heights and top map colours of the plan's last stage (read once per plan). */
	private static @Nullable CompoundTag siluet(MinecraftServer server, String id, JsonObject plan) {
		CompoundTag cached = SILUET.get(id);
		if (cached != null) {
			return cached;
		}
		String tpl = null;
		if (plan.has("tahap")) {
			for (JsonElement e : plan.getAsJsonArray("tahap")) {
				JsonObject t = e.getAsJsonObject();
				if (t.has("template")) {
					tpl = t.get("template").getAsString();
				}
			}
		}
		Identifier tid = tpl == null ? null : Identifier.tryParse(tpl);
		Optional<StructureTemplate> st = tid == null ? Optional.empty() : server.getStructureTemplateManager().get(tid);
		if (st.isEmpty()) {
			return null;
		}
		CompoundTag nbt = st.get().save(new CompoundTag());
		ListTag palette = nbt.getListOrEmpty(StructureTemplate.PALETTE_TAG);
		ListTag blocks = nbt.getListOrEmpty(StructureTemplate.BLOCKS_TAG);
		int w = Math.min(SILUET_MAKS, st.get().getSize().getX());
		int d = Math.min(SILUET_MAKS, st.get().getSize().getZ());
		if (w <= 0 || d <= 0) {
			return null;
		}
		int[] colour = new int[palette.size()];
		for (int i = 0; i < palette.size(); i++) {
			String name = palette.getCompoundOrEmpty(i).getStringOr("Name", "minecraft:air");
			Identifier bid = Identifier.tryParse(name);
			Block b = bid == null ? null : BuiltInRegistries.BLOCK.getValue(bid);
			colour[i] = b == null || name.endsWith(":air") || name.endsWith("structure_void") || name.endsWith("jigsaw") ? 0
					: b.defaultMapColor().col;
		}
		int[] tinggi = new int[w * d];
		int[] warna = new int[w * d];
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag b = blocks.getCompoundOrEmpty(i);
			ListTag pos = b.getListOrEmpty(StructureTemplate.BLOCK_TAG_POS);
			int x = pos.getIntOr(0, -1);
			int y = pos.getIntOr(1, 0);
			int z = pos.getIntOr(2, -1);
			int state = b.getIntOr(StructureTemplate.BLOCK_TAG_STATE, -1);
			if (x < 0 || z < 0 || x >= w || z >= d || state < 0 || state >= colour.length || colour[state] == 0) {
				continue;
			}
			int idx = z * w + x;
			if (y + 1 >= tinggi[idx]) {
				tinggi[idx] = y + 1;
				warna[idx] = 0xFF000000 | colour[state];
			}
		}
		CompoundTag s = new CompoundTag();
		s.putInt(KotaLembar.LEBAR, w);
		s.putInt(KotaLembar.PANJANG, d);
		s.putIntArray(TINGGI, tinggi);
		s.putIntArray(KotaLembar.WARNA, warna);
		SILUET.put(id, s);
		return s;
	}

	private LembarKavling() {
	}
}
