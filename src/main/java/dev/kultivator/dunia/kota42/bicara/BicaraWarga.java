package dev.kultivator.dunia.kota42.bicara;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.Kenangan;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.api.WargaInfo;
import dev.kultivator.dunia.kota42.cakap.Cakap;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.kota42.cakap.Hasil;
import dev.kultivator.dunia.kota42.cakap.Nada;
import dev.kultivator.dunia.kota42.cakap.Niat;
import dev.kultivator.dunia.kota42.cakap.Predikat;
import dev.kultivator.dunia.kota42.cakap.SesiCakap;
import dev.kultivator.dunia.kota42.cakap.Ujaran;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.cerita.Tokoh;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Talking with every civilian (DESIGN 42.5.1, contract C42.B1): the right-click flow of a resident body or a mapped
 * civilian opens the 38 {@code dialog} screen extended with the 42 tablets of {@link KotaLembar.Dialog} (Bicara, Kabar,
 * Jalan, Hubungan, Tolong, Beri, plus 38's Jasa and Quest), every line assembled by the conversation engine ({@link Cakap},
 * 42K; no LLM).
 * <p>
 * Flow: {@code TempatDialog.interact} asks {@link #boleh} (one line), so every civilian enters the 38 dialog path; 38 data
 * dialogs of characters and quests still win. Without one, {@code TempatDialog} builds its greeting sheet and merges
 * {@link #lembar} into it (the 38 card, Jasa and Quest stay; the 42 keys replace text and choices); the ops this class
 * knows reach {@link #aksi} from {@code TempatDialog.aksi}. The first sheet of a right-click opens a fresh {@link SesiCakap}
 * (the engine plays the opening turn: greeting, small talk or a callback); the body faces the player, gestures and speaks
 * every turn as a bubble; the talk ends with Pamit, an ended engine session, or when the screen closes (checked once a
 * second) and the body returns to its schedule.
 * <p>
 * Excluded on purpose: 34 roles with their own KotaNet screen ({@code NpcRole.tab >= 0}: shops, tea house, guards'
 * mission board) keep the 34 screen until {@code Kota.open} is reachable for a {@code jasa:kota_<tab>} entry, and the
 * fighting/special groups (KULTIVATOR, KHUSUS) keep their own meetings.
 */
public final class BicaraWarga {

	/** Reach for the test helpers. */
	static final double JANGKAU = 6.0D;

	/** The live 42 talk of one player (server memory only). */
	private static final class Bincang {
		final int npcId;
		final int wargaId;
		final long situs;
		@Nullable Component teks;
		@Nullable Component raut;
		@Nullable Component pesan;
		String pesanJenis = LembarKunci.PESAN_OK;
		boolean selesai;
		boolean jalanDibayar;
		int kesanTerakhir;

		Bincang(int npcId, int wargaId, long situs) {
			this.npcId = npcId;
			this.wargaId = wargaId;
			this.situs = situs;
		}
	}

	private static final Map<UUID, Bincang> AKTIF = new HashMap<>();
	/** Players whose next sheet is a fresh right-click (set by {@link #boleh}). */
	private static final Set<UUID> BARU = new HashSet<>();

	static void init() {
		// TempatDialog dispatches here; sessions are cleaned by KotaBicara's tick and disconnect hooks
	}

	static void lupakan(UUID player) {
		AKTIF.remove(player);
		BARU.remove(player);
	}

	static void lupakanSemua() {
		AKTIF.clear();
		BARU.clear();
	}

	// ================================================================== entry (TempatDialog)

	/**
	 * True when 42 talk handles this body ({@code kotaBicara.aktif} and {@code semuaWarga}, a civilian standing for a stored
	 * warga). Also tags a named character's body ({@link TokohKota}) so its 38 data dialogs apply, and marks the next sheet
	 * as a fresh talk.
	 */
	public static boolean boleh(ServerPlayer player, KultivatorNpc npc) {
		if (player == null || npc == null || !npc.isAlive() || !KotaBicara.aktif() || !KotaConfig.bicara().semuaWarga) {
			return false;
		}
		NpcRole role = npc.role();
		if (role.tab >= 0 || role.kelompok == NpcRole.Kelompok.KULTIVATOR || role.kelompok == NpcRole.Kelompok.KHUSUS) {
			return false;
		}
		Warga w = WargaApi.dari(npc);
		if (w == null || !w.hidup()) {
			return false;
		}
		TokohKota.tandaiTubuh(npc, w);
		BARU.add(player.getUUID());
		return true;
	}

	/**
	 * The 42 keys of the talk sheet for {@code npc} ({@link KotaLembar.Dialog}; merged over the 38 greeting sheet), or null
	 * to leave the 38 sheet as it is (42 talk off, not a warga).
	 */
	public static @Nullable CompoundTag lembar(ServerPlayer player, KultivatorNpc npc) {
		if (player == null || npc == null || !KotaBicara.aktif()) {
			return null;
		}
		Warga w = WargaApi.dari(npc);
		if (w == null) {
			return null;
		}
		UUID id = player.getUUID();
		Bincang b = AKTIF.get(id);
		if (BARU.remove(id) || b == null || b.npcId != npc.getId()) {
			b = buka(player, npc, w);
		}
		return bangun(player, npc, w, b);
	}

	/** Starts a talk: a fresh engine session with its opening turn, the greeting memory and the talk events. */
	private static Bincang buka(ServerPlayer player, KultivatorNpc npc, Warga w) {
		Cakap.tutup(player);
		long situs = situs(npc);
		Bincang b = new Bincang(npc.getId(), w.id, situs);
		AKTIF.put(player.getUUID(), b);
		WargaApi.peristiwaPemain(player, situs, "sapa", w);
		SesiCakap sesi = Cakap.sesi(player, npc);
		if (sesi != null) {
			List<Ujaran> turn = sesi.terakhir();
			b.teks = gabung(turn);
			mainkan(npc, turn, false);
			b.selesai = sesi.selesai();
		}
		TujuanKota.bicara(player, w);
		PermintaanWarga.bicara(player, npc, w);
		Pinjaman.bicara(player, npc, w);
		return b;
	}

	// ================================================================== sheet

	private static CompoundTag bangun(ServerPlayer player, KultivatorNpc npc, Warga w, Bincang b) {
		MinecraftServer server = player.level().getServer();
		long now = server.overworld().getGameTime();
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(KotaLembar.Dialog.CAKAP, true);
		Ingatan ing = WargaApi.ingatan(w, player.getUUID());
		tag.put(LembarKunci.NPC, kartu(npc, w, ing, now));
		if (b.teks != null) {
			LembarKunci.teks(tag, LembarKunci.TEKS, b.teks);
		}
		if (b.raut != null) {
			LembarKunci.teks(tag, KotaLembar.Dialog.RAUT, b.raut);
		}
		if (b.pesan != null) {
			LembarKunci.teks(tag, LembarKunci.PESAN, b.pesan);
			tag.putString(LembarKunci.PESAN_JENIS, b.pesanJenis);
			b.pesan = null;
		}
		SesiCakap sesi = b.selesai ? null : Cakap.sesi(player, npc);
		ListTag pilihan = new ListTag();
		if (sesi != null && !sesi.selesai()) {
			List<SesiCakap.Opsi> opsi = sesi.opsi();
			for (int i = 0; i < opsi.size() && i < 9; i++) {
				SesiCakap.Opsi o = opsi.get(i);
				CompoundTag c = new CompoundTag();
				c.putInt(LembarKunci.NO, i + 1);
				LembarKunci.teks(c, LembarKunci.TEKS, o.label());
				c.putString(KotaLembar.Dialog.NIAT, niatId(o.niat()));
				if (o.topik() != null) {
					c.putString(KotaLembar.Dialog.TOPIK, o.topik());
				}
				if (o.niat().equals(Niat.JANJI)) {
					c.putBoolean(KotaLembar.Dialog.JANJI, true);
				}
				if (o.terkunci()) {
					c.putBoolean(LembarKunci.TERKUNCI, true);
					LembarKunci.teks(c, LembarKunci.ALASAN, Component.translatable("kultivator.kota42.bicara.terkunci"));
				}
				pilihan.add(c);
			}
			tag.put(KotaLembar.Dialog.NADA, nada(sesi));
		} else if (sesi == null && !b.selesai) {
			// engine off (mesinLanjut false): the 38 line stays, one way out
			CompoundTag c = new CompoundTag();
			c.putInt(LembarKunci.NO, 1);
			LembarKunci.teks(c, LembarKunci.TEKS, Component.translatable("kultivator.cerita.sapa.pamit"));
			c.putString(KotaLembar.Dialog.NIAT, KotaLembar.Dialog.NIAT_PAMIT);
			pilihan.add(c);
		}
		tag.put(LembarKunci.Dialog.PILIHAN, pilihan);
		if (b.selesai) {
			tag.putBoolean(LembarKunci.Dialog.AKHIR, true);
		}
		ListTag sembunyi = new ListTag();
		ListTag kabar = kabar(server, player, w, b.situs);
		if (kabar.isEmpty()) {
			sembunyi.add(StringTag.valueOf(KotaLembar.Dialog.TAB_KABAR));
		} else {
			tag.put(KotaLembar.Dialog.KABAR, kabar);
		}
		tag.put(KotaLembar.Dialog.SEBAR, sebar(server, player, b.situs));
		tag.put(KotaLembar.Dialog.JALAN, TanyaJalan.lembar(player, npc, w, b.jalanDibayar));
		tag.put(KotaLembar.Dialog.HUBUNGAN, hubungan(player, w, ing));
		ListTag tolong = PermintaanWarga.lembar(player, w);
		if (tolong.isEmpty()) {
			sembunyi.add(StringTag.valueOf(KotaLembar.Dialog.TAB_TOLONG));
		} else {
			tag.put(KotaLembar.Dialog.TOLONG, tolong);
		}
		tag.put(KotaLembar.Dialog.BERI, Selera.lembar(player, w));
		if (!sembunyi.isEmpty()) {
			tag.put(LembarKunci.TAB_SEMBUNYI, sembunyi);
		}
		tag.putLong(LembarKunci.UANG, NilaiBatu.total(player));
		return tag;
	}

	/** The speaker card: the 38 keys the merge keeps plus the warga keys of {@link KotaLembar}. */
	private static CompoundTag kartu(KultivatorNpc npc, Warga w, Ingatan ing, long now) {
		CompoundTag card = new CompoundTag();
		LembarKunci.teks(card, LembarKunci.NAMA, TokohKota.nama(w));
		Component gelar = TokohKota.gelar(w);
		if (gelar != null) {
			LembarKunci.teks(card, LembarKunci.GELAR, gelar);
		}
		card.putInt(KotaLembar.WARGA, w.id);
		int umur = WargaApi.umur(w);
		card.putInt(KotaLembar.UMUR, umur);
		card.putInt(KotaLembar.KELAMIN, w.pria() ? KotaLembar.PRIA : KotaLembar.WANITA);
		card.putString(KotaLembar.TAHAP, tahap(umur));
		LembarKunci.teks(card, KotaLembar.KERJA, kerja(w));
		card.putString(KotaLembar.WATAK, Tokoh.WATAK.get(Math.floorMod(w.watak, Tokoh.WATAK.size())));
		card.putString(KotaLembar.WAJAH, WargaApi.kodeWajah(w, now).name().toLowerCase(Locale.ROOT));
		card.putInt(KotaLembar.KESAN, ing.kesan);
		card.putInt(KotaLembar.KENAL, ing.kenal);
		card.putBoolean(KotaLembar.TAHU_NAMA, ing.nama);
		card.putString(KotaLembar.AKRAB, akrab(ing));
		card.putInt(KotaLembar.RUPA, w.look);
		if (!w.tokoh.isEmpty()) {
			card.putString(LembarKunci.TOKOH_ID, w.tokoh);
		}
		return card;
	}

	/** "Koki" or, for a resident employed by a player, "Pegawai · <tugas>". */
	private static Component kerja(Warga w) {
		Component role = Component.translatable(w.peranNpc().titleKey());
		return WargaApi.majikan(w) != null ? Component.translatable("kultivator.kota42.bicara.kerja.pegawai", role) : role;
	}

	static String tahap(int umur) {
		WargaInfo.Tahap t = WargaInfo.Tahap.dari(umur);
		return t == WargaInfo.Tahap.MUDA ? KotaLembar.TAHAP_MUDA : t.name().toLowerCase(Locale.ROOT);
	}

	/** The heart band of 42.5.2: musuh <= -40, asing (never met), kenal, akrab >= 30, sahabat >= 60. */
	static String akrab(Ingatan ing) {
		if (ing.kesan <= -40) {
			return KotaLembar.AKRAB_MUSUH;
		}
		if (ing.kesan >= 60) {
			return KotaLembar.AKRAB_SAHABAT;
		}
		if (ing.kesan >= 30) {
			return KotaLembar.AKRAB_AKRAB;
		}
		return ing.kenal <= 1 ? KotaLembar.AKRAB_ASING : KotaLembar.AKRAB_KENAL;
	}

	/** Engine intent id -> the sheet's lower-case {@code niat} ({@code BERI_KABAR} is the tablet's {@code kabar}). */
	private static String niatId(Niat niat) {
		if (niat.equals(Niat.BERI_KABAR)) {
			return KotaLembar.Dialog.NIAT_KABAR;
		}
		return niat.id().toLowerCase(Locale.ROOT);
	}

	private static CompoundTag nada(SesiCakap sesi) {
		CompoundTag c = new CompoundTag();
		ListTag daftar = new ListTag();
		Set<Nada> boleh = sesi.nadaBoleh();
		for (Nada n : Nada.values()) {
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, n.id());
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.kota42.cakap.nada." + n.id()));
			boolean bisa = boleh.contains(n);
			row.putBoolean(LembarKunci.BISA, bisa);
			if (!bisa) {
				LembarKunci.teks(row, LembarKunci.ALASAN, Component.translatable("kultivator.kota42.bicara.nada_terkunci." + n.id()));
			}
			daftar.add(row);
		}
		c.put(LembarKunci.DAFTAR, daftar);
		c.putString(KotaLembar.Dialog.BAWAAN, KotaLembar.Dialog.NADA_SOPAN);
		return c;
	}

	// ------------------------------------------------------------------ Kabar

	/** Up to three rumours this warga knows, each told in the warga's own manner (42.5.4 "Pemain mendengar"). */
	private static ListTag kabar(MinecraftServer server, ServerPlayer player, Warga w, long situs) {
		ListTag list = new ListTag();
		for (Fakta f : Cakap.fakta(w)) {
			if (!f.gosip()) {
				continue;
			}
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, Integer.toString(f.gosipId()));
			String topik = Predikat.dari(f.predikat()).topik();
			Ujaran u = Cakap.ujaran(w, Niat.BERI_KABAR, new Cakap.Konteks(player, null, topik, f, situs, Map.of()));
			LembarKunci.teks(row, LembarKunci.TEKS, u.kosong() ? Component.translatable("kultivator.kota42.bicara.kabar.samar") : u.komponen());
			row.putLong(LembarKunci.HARI, f.hariTahu());
			row.putBoolean(KotaLembar.Dialog.TANYA, f.asal().sumber() != Fakta.Sumber.DIRI);
			ListTag jejak = jejakLembar(server, player, situs, f.gosipId());
			if (!jejak.isEmpty()) {
				row.put(KotaLembar.Dialog.JEJAK, jejak);
			}
			list.add(row);
			if (list.size() >= 3) {
				break;
			}
		}
		return list;
	}

	private static ListTag jejakLembar(MinecraftServer server, ServerPlayer player, long situs, int gosip) {
		ListTag out = new ListTag();
		CompoundTag rec = jejak(server, player.getUUID(), situs, gosip, false);
		if (rec == null) {
			return out;
		}
		for (int id : rec.getIntArray("c").orElse(new int[0])) {
			CompoundTag row = new CompoundTag();
			if (id >= 0) {
				Warga t = WargaApi.byId(id);
				LembarKunci.teks(row, LembarKunci.NAMA, t == null ? Component.translatable("kultivator.kota42.bicara.kabar.entah") : TokohKota.nama(t));
				row.putInt(KotaLembar.WARGA, id);
			} else {
				LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable(id == JEJAK_SAKSI
						? "kultivator.kota42.bicara.kabar.jejak_saksi" : "kultivator.kota42.bicara.kabar.jejak_pendekar"));
			}
			out.add(row);
		}
		return out;
	}

	/** Marker of a trace link "the first teller saw it himself". */
	private static final int JEJAK_SAKSI = Integer.MIN_VALUE;

	/** The trace record of a player for one rumour ({@code create} adds it, at most 8 per player, oldest dropped). */
	private static @Nullable CompoundTag jejak(MinecraftServer server, UUID player, long situs, int gosip, boolean create) {
		KotaBicara.Data data = KotaBicara.Data.get(server);
		CompoundTag part = data.bagian(KotaBicara.Data.JEJAK);
		String key = player.toString();
		ListTag list = part.getListOrEmpty(key);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag c = list.getCompoundOrEmpty(i);
			if (c.getLongOr("s", 0L) == situs && c.getIntOr("g", -1) == gosip) {
				return c;
			}
		}
		if (!create) {
			return null;
		}
		CompoundTag c = new CompoundTag();
		c.putLong("s", situs);
		c.putInt("g", gosip);
		c.putIntArray("c", new int[0]);
		list.add(c);
		while (list.size() > 8) {
			list.remove(0);
		}
		part.put(key, list);
		data.ubah();
		return c;
	}

	/**
	 * [Tanya siapa yang bilang]: the warga names who told it (the previous link of the chain), or says he saw it himself. A
	 * trace follows the chain only through the tellers the player actually asks; reaching the first witness fires the quest
	 * event {@code gosip_telusur} ({@link TujuanKota}).
	 */
	private static void tanyaSumber(ServerPlayer player, KultivatorNpc npc, Warga w, Bincang b, int gosip) {
		MinecraftServer server = player.level().getServer();
		List<Integer> rantai = GosipWarga.rantai(server, w, gosip);
		GosipWarga.Gosip g = GosipWarga.cari(server, b.situs, gosip);
		if (g == null) {
			b.teks = Component.translatable("kultivator.kota42.bicara.kabar.lupa");
			return;
		}
		CompoundTag rec = jejak(server, player.getUUID(), b.situs, gosip, true);
		int[] known = rec.getIntArray("c").orElse(new int[0]);
		int next = rantai.isEmpty() ? JEJAK_SAKSI : rantai.get(0);
		boolean baru = true;
		for (int k : known) {
			if (k == next) {
				baru = false;
				break;
			}
		}
		if (baru) {
			int[] grown = java.util.Arrays.copyOf(known, known.length + 1);
			grown[known.length] = next;
			rec.putIntArray("c", grown);
			KotaBicara.Data.get(server).ubah();
		}
		String watak = Tokoh.WATAK.get(Math.floorMod(w.watak, Tokoh.WATAK.size()));
		if (next == JEJAK_SAKSI) {
			b.teks = Component.translatable("kultivator.kota42.bicara.kabar.lihat_sendiri." + watakNada(watak));
			HidupApi.klip(npc, GerakId.TUNJUK, 0, 40);
			TujuanKota.gosipTelusur(player, g.templat(), gosip);
		} else if (next < 0) {
			b.teks = Component.translatable("kultivator.kota42.bicara.kabar.dari_pendekar." + watakNada(watak));
			HidupApi.klip(npc, GerakId.BISIK, 0, 40);
		} else {
			Warga teller = WargaApi.byId(next);
			Component nama = teller == null ? Component.translatable("kultivator.kota42.bicara.kabar.entah") : TokohKota.nama(teller);
			b.teks = Component.translatable("kultivator.kota42.bicara.kabar.dari." + watakNada(watak), nama);
			HidupApi.klip(npc, GerakId.BISIK, 0, 40);
		}
		npc.say(b.teks, 100);
	}

	/** Three manners of answering: bijak/jujur doubt it, penakut/curiga whisper, the rest just tell. */
	private static String watakNada(String watak) {
		return switch (watak) {
			case "bijak", "jujur" -> "ragu";
			case "penakut", "curiga" -> "bisik";
			default -> "biasa";
		};
	}

	// ------------------------------------------------------------------ Sebar

	private static CompoundTag sebar(MinecraftServer server, ServerPlayer player, long situs) {
		CompoundTag c = new CompoundTag();
		c.putLong(LembarKunci.BIAYA, SEBAR_BIAYA);
		Component alasan = sebarAlasan(server, player, situs);
		c.putBoolean(LembarKunci.BISA, alasan == null);
		if (alasan != null) {
			LembarKunci.teks(c, LembarKunci.ALASAN, alasan);
		}
		return c;
	}

	/** NB for the tea of [Sebarkan kabar baik sekteku] (42.5.4). */
	static final long SEBAR_BIAYA = 5L;
	/** Days the recruiting boost lasts. */
	static final int SEBAR_HARI = 3;

	private static @Nullable Component sebarAlasan(MinecraftServer server, ServerPlayer player, long situs) {
		Halls.HallView hall = Halls.hallOf(player.getUUID());
		if (hall == null || !hall.active()) {
			return Component.translatable("kultivator.kota42.bicara.sebar.tanpa_aula");
		}
		CompoundTag t = KotaBicara.Data.get(server).situs(KotaBicara.Data.SEBAR, situs);
		if (t.getLongOr(player.getUUID().toString(), -1L) == KotaHidup.hari(server)) {
			return Component.translatable("kultivator.kota42.bicara.sebar.sudah");
		}
		if (NilaiBatu.total(player) < SEBAR_BIAYA) {
			return Component.translatable("kultivator.kota42.bicara.sebar.kurang", SEBAR_BIAYA);
		}
		return null;
	}

	private static void sebarkan(ServerPlayer player, KultivatorNpc npc, Warga w, Bincang b) {
		MinecraftServer server = player.level().getServer();
		Component alasan = sebarAlasan(server, player, b.situs);
		if (alasan != null) {
			b.pesan = alasan;
			b.pesanJenis = LembarKunci.PESAN_PERINGATAN;
			return;
		}
		if (!NilaiBatu.pay(player, SEBAR_BIAYA)) {
			return;
		}
		KasKota.pajak(server, b.situs, SEBAR_BIAYA, "teh");
		long hari = KotaHidup.hari(server);
		KotaBicara.Data data = KotaBicara.Data.get(server);
		CompoundTag t = data.situs(KotaBicara.Data.SEBAR, b.situs);
		t.putLong(player.getUUID().toString(), hari);
		t.putLong("boost_" + player.getUUID(), hari + SEBAR_HARI);
		data.ubah();
		int idx = WargaData.get(server).indeksPemain(player.getUUID());
		GosipWarga.lahir(player.level(), b.situs, "SEKTE", 0, Fakta.Kebenaran.BENAR, List.of("menerima_murid"), null,
				Fakta.Rujuk.pemain(idx), Fakta.Rujuk.kata("menerima_murid"));
		Halls.HallView hall = Halls.hallOf(player.getUUID());
		Component sekte = hall == null || hall.name().isBlank() ? player.getName() : Component.literal(hall.name());
		b.teks = Component.translatable("kultivator.kota42.bicara.sebar.teks." + Math.floorMod(w.id, 3), sekte);
		npc.say(b.teks, 100);
		HidupApi.klip(npc, GerakId.ANGGUK_DENGAR, 0, 40);
		b.pesan = Component.translatable("kultivator.kota42.bicara.sebar.ok", SEBAR_HARI);
		b.pesanJenis = LembarKunci.PESAN_OK;
	}

	/**
	 * The recruiting boost of [Sebarkan kabar baik sekteku] for the hall of {@code owner} at place {@code situs}: 0.10 while
	 * it lasts (3 days), else 0. For 42E's Kantor Rekrutmen applicant chance (42.5.4).
	 */
	public static double bonusRekrut(MinecraftServer server, long situs, UUID owner) {
		if (server == null || owner == null) {
			return 0.0D;
		}
		long until = KotaBicara.Data.get(server).situs(KotaBicara.Data.SEBAR, situs).getLongOr("boost_" + owner, -1L);
		return until >= KotaHidup.hari(server) ? 0.10D : 0.0D;
	}

	// ------------------------------------------------------------------ Hubungan

	private static CompoundTag hubungan(ServerPlayer player, Warga w, Ingatan ing) {
		CompoundTag c = new CompoundTag();
		c.putInt(KotaLembar.KENAL, ing.kenal);
		c.putInt(KotaLembar.KESAN, ing.kesan);
		c.putLong(KotaLembar.Dialog.UTANG, ing.utang);
		ListTag kenangan = new ListTag();
		List<int[]> slots = new ArrayList<>();
		for (int i = 0; i < Ingatan.PERISTIWA; i++) {
			if (ing.kenangan(i) != null) {
				slots.add(new int[] {i, ing.hariKenangan(i)});
			}
		}
		slots.sort((x, y) -> Integer.compare(y[1], x[1]));
		for (int[] s : slots) {
			Kenangan k = ing.kenangan(s[0]);
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, k.id());
			LembarKunci.teks(row, LembarKunci.NAMA, k.nama());
			row.putLong(LembarKunci.HARI, s[1]);
			row.putBoolean(KotaLembar.Dialog.KUAT, k.kuat);
			kenangan.add(row);
		}
		c.put(KotaLembar.Dialog.KENANGAN, kenangan);
		ListTag aksi = new ListTag();
		aksi.add(tombol(KotaLembar.Dialog.OP_KARTU, Component.translatable("kultivator.kota42.bicara.aksi.kartu"), null));
		long batas = Pinjaman.batasPinjam(player, w);
		Component pinjamAlasan = Pinjaman.alasanPinjam(player, w);
		aksi.add(tombol("pinjam:" + batas, Component.translatable("kultivator.kota42.bicara.aksi.pinjam", batas), pinjamAlasan));
		if (ing.utang < 0) {
			aksi.add(tombol("pinjamkan:" + (-ing.utang), Component.translatable("kultivator.kota42.bicara.aksi.lunasi", -ing.utang),
					NilaiBatu.total(player) < 1L ? Component.translatable("kultivator.kota42.bicara.aksi.tanpa_uang") : null));
		}
		c.put(KotaLembar.AKSI, aksi);
		return c;
	}

	private static CompoundTag tombol(String op, Component nama, @Nullable Component alasan) {
		CompoundTag row = new CompoundTag();
		row.putString(LembarKunci.ID, op);
		LembarKunci.teks(row, LembarKunci.NAMA, nama);
		row.putBoolean(LembarKunci.BISA, alasan == null);
		if (alasan != null) {
			LembarKunci.teks(row, LembarKunci.ALASAN, alasan);
		}
		return row;
	}

	// ================================================================== actions (from TempatDialog.aksi)

	/**
	 * The ops of the 42 tablets ({@link KotaLembar.Dialog}): {@code ucap:NO:NADA}, {@code tanya_sumber:GOSIP}, {@code sebar},
	 * {@code tandai_jalan:ID}, {@code kartu}, {@code terima_tolong:ID}, {@code serahkan_tolong:ID}, {@code beri},
	 * {@code pinjam:NB}, {@code pinjamkan:NB}. Returns true when the op was one of them (TempatLayar refreshes the sheet).
	 */
	public static boolean aksi(ServerPlayer player, KultivatorNpc npc, String op) {
		String[] parts = LembarKunci.urai(op);
		String verb = parts[0];
		Bincang b = AKTIF.get(player.getUUID());
		Warga w = WargaApi.dari(npc);
		if (b == null || b.npcId != npc.getId() || w == null || !KotaBicara.aktif()) {
			return false;
		}
		switch (verb) {
			case KotaLembar.Dialog.OP_UCAP -> ucap(player, npc, w, b, LembarKunci.argInt(parts, 1, -1), LembarKunci.arg(parts, 2));
			case KotaLembar.Dialog.OP_TANYA_SUMBER -> tanyaSumber(player, npc, w, b, LembarKunci.argInt(parts, 1, -1));
			case KotaLembar.Dialog.OP_SEBAR -> sebarkan(player, npc, w, b);
			case KotaLembar.Dialog.OP_TANDAI_JALAN -> {
				TanyaJalan.Tanda h = TanyaJalan.tandai(player, npc, w, LembarKunci.arg(parts, 1), b.jalanDibayar);
				b.jalanDibayar |= h.dibayar();
				if (h.teks() != null) {
					b.teks = h.teks();
				}
				b.pesan = h.pesan();
				b.pesanJenis = h.ok() ? LembarKunci.PESAN_OK : LembarKunci.PESAN_PERINGATAN;
			}
			case KotaLembar.Dialog.OP_KARTU -> {
				if (!TempatLayar.bukaDari(player, "warga_kartu", npc.getId())) {
					b.pesan = Component.translatable("kultivator.kota42.bicara.kartu_tutup");
					b.pesanJenis = LembarKunci.PESAN_PERINGATAN;
				}
			}
			case KotaLembar.Dialog.OP_TERIMA_TOLONG -> hasil(b, PermintaanWarga.terima(player, npc, w, LembarKunci.arg(parts, 1)));
			case KotaLembar.Dialog.OP_SERAHKAN_TOLONG -> hasil(b, PermintaanWarga.serahkan(player, npc, w, LembarKunci.arg(parts, 1)));
			case KotaLembar.Dialog.OP_BERI -> hasil(b, Selera.beri(player, npc, w));
			case "pinjam" -> hasil(b, Pinjaman.pinjam(player, npc, w, LembarKunci.argLong(parts, 1, 0L)));
			case "pinjamkan" -> hasil(b, Pinjaman.pinjamkan(player, npc, w, LembarKunci.argLong(parts, 1, 0L)));
			default -> {
				return false;
			}
		}
		return true;
	}

	/** What an action says back: the warga's line (also a bubble) and a status message. */
	record Balasan(@Nullable Component teks, @Nullable Component pesan, boolean ok) {
		static Balasan gagal(Component pesan) {
			return new Balasan(null, pesan, false);
		}
	}

	private static void hasil(Bincang b, @Nullable Balasan r) {
		if (r == null) {
			return;
		}
		if (r.teks() != null) {
			b.teks = r.teks();
		}
		b.pesan = r.pesan();
		b.pesanJenis = r.ok() ? LembarKunci.PESAN_OK : LembarKunci.PESAN_PERINGATAN;
	}

	/** One choice of tablet Bicara: option {@code no} (1-based) in tone {@code nadaId} (absent = sopan). */
	private static void ucap(ServerPlayer player, KultivatorNpc npc, Warga w, Bincang b, int no, String nadaId) {
		if (b.selesai) {
			TempatDialog.akhiri(player);
			return;
		}
		SesiCakap sesi = Cakap.sesi(player, npc);
		if (sesi == null) {
			b.selesai = true;
			TempatDialog.akhiri(player);
			return;
		}
		List<SesiCakap.Opsi> opsi = sesi.opsi();
		if (no < 1 || no > opsi.size()) {
			return;
		}
		Nada nada = nadaId.isEmpty() ? Nada.SOPAN : Nada.dari(nadaId);
		Hasil hasil = Cakap.pilih(sesi, opsi.get(no - 1), nada == null ? Nada.SOPAN : nada);
		List<Ujaran> turn = hasil.balasan();
		if (!turn.isEmpty()) {
			b.teks = gabung(turn);
			mainkan(npc, turn, true);
		}
		b.raut = hasil.isyarat();
		b.kesanTerakhir = hasil.kesan();
		if (hasil.kesan() != 0) {
			b.pesan = Component.translatable(hasil.kesan() > 0 ? "kultivator.kota42.bicara.kesan_naik" : "kultivator.kota42.bicara.kesan_turun",
					Math.abs(hasil.kesan()));
			b.pesanJenis = hasil.kesan() > 0 ? LembarKunci.PESAN_OK : LembarKunci.PESAN_PERINGATAN;
		}
		if (hasil.selesai()) {
			b.selesai = true;
			HidupApi.klip(npc, GerakId.HORMAT, 0, 40);
		}
	}

	/** Bubble, gesture and voice of an NPC turn (the first gesture and exclamation of the turn). */
	private static void mainkan(KultivatorNpc npc, List<Ujaran> turn, boolean suara) {
		if (turn.isEmpty()) {
			return;
		}
		npc.say(gabung(turn), 60 + 40 * turn.size());
		Ujaran first = turn.get(0);
		int klip = first.gestur() > 0 ? first.gestur() : GerakId.BICARA_TANGAN;
		HidupApi.klip(npc, klip, 0, Math.max(40, GerakId.durasiSekali(klip, 0)));
		if (suara && KotaConfig.bicara().suaraSeru) {
			for (Ujaran u : turn) {
				if (u.seru() != null && VoiceEvents.exists(u.seru())) {
					Voice.say(npc, u.seru(), null);
					break;
				}
			}
		}
	}

	/** 1-3 utterances as one text, one per line. */
	private static @Nullable Component gabung(List<Ujaran> turn) {
		MutableComponent out = null;
		for (Ujaran u : turn) {
			if (u == null || u.kosong()) {
				continue;
			}
			if (out == null) {
				out = Component.empty().append(u.komponen());
			} else {
				out.append("\n").append(u.komponen());
			}
		}
		return out;
	}

	// ================================================================== housekeeping

	/** Once a second: talks whose screen closed or whose body is gone end (engine session closed, body freed). */
	static void tick(MinecraftServer server) {
		if (AKTIF.isEmpty()) {
			return;
		}
		List<UUID> end = new ArrayList<>();
		for (Map.Entry<UUID, Bincang> e : AKTIF.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Bincang b = e.getValue();
			if (player == null || !LembarKunci.Dialog.LAYAR.equals(TempatLayar.layarTerbuka(player)) || TempatLayar.npcTerbuka(player) != b.npcId) {
				end.add(e.getKey());
				if (player != null) {
					Cakap.tutup(player);
					Entity body = player.level().getEntity(b.npcId);
					if (body instanceof KultivatorNpc npc) {
						HidupApi.tatap(npc, null);
					}
				}
			}
		}
		for (UUID id : end) {
			AKTIF.remove(id);
		}
	}

	static long situs(KultivatorNpc npc) {
		return npc.penduduk() ? npc.situsPenduduk() : npc.town();
	}

	/** The nearest living civilian within {@code r} blocks that stands for a warga (test commands). */
	static @Nullable KultivatorNpc terdekat(ServerPlayer player, double r) {
		KultivatorNpc best = null;
		double bestD = Double.MAX_VALUE;
		for (KultivatorNpc npc : player.level().getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(r), KultivatorNpc::isAlive)) {
			double d = npc.distanceToSqr(player);
			if (d < bestD && WargaApi.dari(npc) != null) {
				best = npc;
				bestD = d;
			}
		}
		return best;
	}

	/** Summary lines of what the talk sheet would hold ({@code uji kota_hidup bicara info}). */
	static List<Component> ringkas(ServerPlayer player, KultivatorNpc npc, Warga w) {
		List<Component> out = new ArrayList<>();
		MinecraftServer server = player.level().getServer();
		Ingatan ing = WargaApi.ingatan(w, player.getUUID());
		out.add(Component.translatable("kultivator.kota42.bicara.uji.info", TokohKota.nama(w), w.id, WargaApi.umur(w),
				Component.translatable(w.peranNpc().titleKey()), Tokoh.WATAK.get(Math.floorMod(w.watak, Tokoh.WATAK.size())), ing.kesan, akrab(ing)));
		out.add(Component.translatable("kultivator.kota42.bicara.uji.info_kabar", kabar(server, player, w, situs(npc)).size(),
				PermintaanWarga.lembar(player, w).size(), Selera.petunjuk(w)));
		out.add(Component.translatable("kultivator.kota42.bicara.uji.info_jalan", TanyaJalan.jumlah(player, npc, w)));
		return out;
	}

	private BicaraWarga() {
	}
}
