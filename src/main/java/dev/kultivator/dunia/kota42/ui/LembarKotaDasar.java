package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.api.WargaInfo;
import dev.kultivator.dunia.kota42.ekonomi.KotaEkonomi;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.ragam.IramaMingguan;
import dev.kultivator.dunia.kota42.sekte.PaktaLindung;
import dev.kultivator.dunia.kota42.tumbuh.Kemakmuran;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.cerita.Tokoh;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The shared server half of the 42U-2 screens (DESIGN 42.11.9-42.11.14: {@code jabatan}, {@code perkara},
 * {@code pasar_hantu}, {@code guanpu}, {@code kantor_rekrut}, {@code pakta}, {@code cabang}, {@code kavling},
 * {@code upacara}, {@code rombongan}; keys {@link KotaLembar}). One registry per screen, the pattern of 41's
 * {@code GabungLayar}:
 * <ul>
 * <li><b>Builder</b> ({@link Bangun}) of the screen's {@code Lembar*} class, with the place already resolved
 * ({@link #situs}); then every registered {@link Pengisi} of another module may add or replace blocks (42D/42E/42F fill
 * what only they know); then the pending result line ({@link #pesan}).</li>
 * <li><b>Ops</b>: the read verbs {@code tab:TAB:ARG} (selection per player and screen, {@link #pilih}) and
 * {@code tandai:X:Z} are handled here; every other verb goes first to a module's {@link #daftarAksi} handler, else to the
 * builder class's own handler ({@link #bawaan}); an unknown verb answers "belum tersedia".</li>
 * <li><b>Place</b>: the entity's place (mechanism entity {@code situsId}, a resident's home, a town NPC's town), else the
 * selection of a remote screen, else where the player stands (160 blocks, {@code PajakKota.tempat}).</li>
 * </ul>
 * Server only; client screens are registered by {@code client/dunia/kota42/ui/layar/KotaUi2Client}.
 */
public final class LembarKotaDasar {

	/** Lang prefix of every 42U-2 text ({@code lang-src/kota_hidup_ui2}). */
	public static final String K = "kultivator.kota42.ui2.";

	/** Builds the whole sheet for a resolved place ({@code situs} 0 = none found); null refuses the open. */
	@FunctionalInterface
	public interface Bangun {
		@Nullable
		CompoundTag bangun(ServerPlayer player, int npcId, long situs);
	}

	/** Another module adds or replaces blocks of a built sheet (keys of {@link KotaLembar}). */
	@FunctionalInterface
	public interface Pengisi {
		void isi(ServerPlayer player, int npcId, long situs, CompoundTag lembar);
	}

	/** One op verb: {@code op} is the split op ({@code op[0]} the verb). */
	@FunctionalInterface
	public interface Aksi {
		void aksi(ServerPlayer player, int npcId, long situs, String[] op);
	}

	private static final Map<String, Bangun> PEMBANGUN = new ConcurrentHashMap<>();
	private static final Map<String, Aksi> MODUL = new ConcurrentHashMap<>();
	private static final Map<String, Aksi> BAWAAN = new ConcurrentHashMap<>();
	private static final Map<String, List<Pengisi>> PENGISI = new ConcurrentHashMap<>();
	private static final Map<UUID, Object[]> PESAN = new HashMap<>();
	private static final Map<String, String> PILIH = new HashMap<>();
	private static final Map<UUID, Long> SITUS_PILIH = new HashMap<>();
	private static boolean initialised;

	/** Registers every 42U-2 screen. Called once from {@code KotaHidup} (via 42U-1's ui init, see the progress file). */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		LembarJabatan.init();
		LembarPerkara.init();
		LembarPasarHantu.init();
		LembarGuanpu.init();
		LembarKantor.init();
		LembarPakta.init();
		LembarCabang.init();
		LembarKavling.init();
		LembarUpacara.init();
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> lupakan(handler.getPlayer().getUUID()));
	}

	// ================================================================== registry

	/** A screen of this module; {@code hub} true when it may also open remotely ({@link KotaLembar#LAYAR_HUB}). */
	static void daftar(String layar, Bangun bangun) {
		PEMBANGUN.put(layar, bangun);
		TempatLayar.Penangan penangan = (p, npcId, op) -> aksi(layar, p, npcId, op);
		TempatLayar.Pembangun pembangun = (p, npcId) -> bangun(layar, p, npcId);
		if (KotaLembar.LAYAR_HUB.contains(layar)) {
			TempatLayar.daftarHub(layar, penangan, pembangun);
		} else {
			TempatLayar.daftar(layar, penangan, pembangun);
		}
	}

	/** The builder class's own handler of {@code verb}. */
	static void bawaan(String layar, String verb, Aksi aksi) {
		BAWAAN.put(layar + ":" + verb, aksi);
	}

	/** A module (42D, 42E, 42F) handles {@code verb} of {@code layar} itself; wins over the builder's own handler. */
	public static void daftarAksi(String layar, String verb, Aksi aksi) {
		MODUL.put(layar + ":" + verb, aksi);
	}

	/** A module fills blocks of {@code layar} after the builder ran. */
	public static void addPengisi(String layar, Pengisi pengisi) {
		PENGISI.computeIfAbsent(layar, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(pengisi);
	}

	public static boolean adaAksi(String layar, String verb) {
		return MODUL.containsKey(layar + ":" + verb) || BAWAAN.containsKey(layar + ":" + verb);
	}

	/** Opens {@code layar} for the player (same NPC as the current screen); false when the builder refused. */
	public static boolean buka(ServerPlayer player, String layar, int npcId) {
		CompoundTag tag = bangun(layar, player, npcId);
		if (tag == null) {
			return false;
		}
		TempatLayar.buka(player, layar, npcId, tag);
		return true;
	}

	static @Nullable CompoundTag bangun(String layar, ServerPlayer player, int npcId) {
		Bangun b = PEMBANGUN.get(layar);
		if (b == null) {
			return null;
		}
		long situs = situs(player, npcId);
		CompoundTag tag = b.bangun(player, npcId, situs);
		if (tag == null) {
			return null;
		}
		for (Pengisi p : PENGISI.getOrDefault(layar, List.of())) {
			try {
				p.isi(player, npcId, situs, tag);
			} catch (RuntimeException ex) {
				dev.kultivator.Kultivator.LOGGER.warn("42 sheet filler of {} failed", layar, ex);
			}
		}
		Object[] pesan = PESAN.remove(player.getUUID());
		if (pesan != null) {
			LembarKunci.teks(tag, LembarKunci.PESAN, (Component) pesan[0]);
			tag.putString(LembarKunci.PESAN_JENIS, (String) pesan[1]);
		}
		return tag;
	}

	private static void aksi(String layar, ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		String verb = parts.length == 0 ? "" : parts[0];
		if (LembarKunci.OP_TAB.equals(verb)) {
			if (parts.length > 2 && !parts[2].isEmpty()) {
				setPilih(player, layar, parts[2]);
			}
			return;
		}
		if (LembarKunci.OP_TANDAI.equals(verb)) {
			tandai(player, LembarKunci.argInt(parts, 1, 0), LembarKunci.argInt(parts, 2, 0));
			return;
		}
		if (LembarKunci.OP_HALAMAN.equals(verb) || LembarKunci.OP_LIHAT.equals(verb)) {
			return;
		}
		long situs = situs(player, npcId);
		Aksi a = MODUL.get(layar + ":" + verb);
		if (a == null) {
			a = BAWAAN.get(layar + ":" + verb);
		}
		if (a == null) {
			pesan(player, Component.translatable(K + "belum_tersedia"), LembarKunci.PESAN_PERINGATAN);
			return;
		}
		a.aksi(player, npcId, situs, parts);
	}

	// ================================================================== selection, place, messages

	/** The player's selected row of {@code layar} ("" none). */
	public static String pilih(ServerPlayer player, String layar) {
		return PILIH.getOrDefault(player.getUUID() + "|" + layar, "");
	}

	public static void setPilih(ServerPlayer player, String layar, String id) {
		PILIH.put(player.getUUID() + "|" + layar, id);
	}

	/** A remote screen shows this place from now on (kota_buku, kota_milikku jump to a town). */
	public static void pilihSitus(ServerPlayer player, long situs) {
		SITUS_PILIH.put(player.getUUID(), situs);
	}

	private static void lupakan(UUID id) {
		PESAN.remove(id);
		SITUS_PILIH.remove(id);
		String awal = id + "|";
		PILIH.keySet().removeIf(k -> k.startsWith(awal));
	}

	/** The place a screen talks about (0 none), see the class javadoc. */
	public static long situs(ServerPlayer player, int npcId) {
		if (npcId >= 0) {
			Entity e = player.level().getEntity(npcId);
			if (e instanceof TemaMekanismeEntity m && m.situsId() != 0L) {
				return m.situsId();
			}
			if (e instanceof KultivatorNpc npc) {
				if (npc.situsPenduduk() != 0L) {
					return npc.situsPenduduk();
				}
				if (npc.town() != 0L) {
					return npc.town();
				}
			}
			if (e != null && player.level() instanceof ServerLevel level) {
				TempatInfo t = TempatApi.tempatDi(level, e.blockPosition());
				if (t != null) {
					return t.situsId();
				}
			}
		}
		TempatInfo here = PajakKota.tempat(player);
		if (here != null) {
			return here.situsId();
		}
		Long dipilih = SITUS_PILIH.get(player.getUUID());
		return dipilih == null ? 0L : dipilih;
	}

	/** Remembers the result line for the next sheet and shows it on the action bar ({@code jenis} a PESAN_* id). */
	public static void pesan(ServerPlayer player, Component teks, String jenis) {
		PESAN.put(player.getUUID(), new Object[] {teks, jenis});
		ChatFormatting warna = LembarKunci.PESAN_OK.equals(jenis) ? ChatFormatting.GREEN
				: LembarKunci.PESAN_BAHAYA.equals(jenis) ? ChatFormatting.RED : ChatFormatting.YELLOW;
		Msg.actionBar(player, teks.copy().withStyle(warna));
	}

	/** The usual answer of an API that returns null on success or the refusal. */
	public static void hasil(ServerPlayer player, @Nullable Component tolak, @Nullable Component ok) {
		if (tolak != null) {
			pesan(player, tolak, LembarKunci.PESAN_BAHAYA);
		} else if (ok != null) {
			pesan(player, ok, LembarKunci.PESAN_OK);
		}
	}

	/** "Tunjuk": the place and its bearing in chat (the town screens' way). */
	static void tandai(ServerPlayer player, int x, int z) {
		double dx = x + 0.5D - player.getX();
		double dz = z + 0.5D - player.getZ();
		int jarak = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		Component arah = Component.translatable(K + "arah." + arah(dx, dz));
		Msg.chat(player, Component.translatable(K + "tandai", x, z, jarak, arah).withStyle(ChatFormatting.AQUA));
		pesan(player, Component.translatable(K + "tandai.pesan", arah, jarak), LembarKunci.PESAN_OK);
	}

	/** 8 sectors, 0 = north, clockwise, from a world offset. */
	static int arah(double dx, double dz) {
		double sudut = Math.toDegrees(Math.atan2(dx, -dz));
		return Math.floorMod((int) Math.round(sudut / 45.0D), 8);
	}

	// ================================================================== sheet pieces

	/** A new sheet with title, style and money; {@code judul} null keeps the client's default title. */
	static CompoundTag lembar(ServerPlayer player, @Nullable Component judul, String gaya) {
		CompoundTag tag = new CompoundTag();
		if (judul != null) {
			LembarKunci.teks(tag, LembarKunci.JUDUL, judul);
		}
		tag.putString(LembarKunci.GAYA, gaya);
		tag.putLong(LembarKunci.UANG, dev.kultivator.dunia.kota.NilaiBatu.total(player));
		return tag;
	}

	/** The town header of {@link KotaLembar} (place name as {@code tempat}). Nothing when {@code situs} is 0. */
	static void kepala(CompoundTag tag, ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		if (situs == 0L || server == null) {
			return;
		}
		tag.putLong(LembarKunci.SITUS, situs);
		LembarKunci.teks(tag, LembarKunci.TEMPAT, PajakKota.nama(server, situs));
		String jenis = PajakKota.jenisTempat(server, situs);
		tag.putString(KotaLembar.JENIS_TEMPAT, switch (jenis) {
			case PajakKota.JT_IBU_KOTA -> KotaLembar.JT_IBUKOTA;
			case PajakKota.JT_TEMA -> KotaLembar.JT_TEMA;
			case PajakKota.JT_KOTA -> KotaLembar.JT_KOTA;
			case PajakKota.JT_POS -> KotaLembar.JT_POS;
			default -> KotaLembar.JT_KAMPUNG;
		});
		tag.putInt(KotaLembar.KELAS_KOTA, PajakKota.kelas(server, situs));
		long hari = KotaHidup.hari(server);
		tag.putInt(KotaLembar.MUSIM, IramaMingguan.musim(hari));
		tag.putInt(KotaLembar.HARI_MUSIM, (int) Math.floorMod(hari, (long) Jadwal.HARI_MUSIM));
		tag.putInt(KotaLembar.KEMAKMURAN, Math.max(0, Math.min(100, Kemakmuran.nilai(situs))));
		tag.putInt(KotaLembar.SUASANA_KOTA, WargaApi.suasanaTempat(situs));
		tag.putInt(KotaLembar.PAJAK, PajakKota.tarif(server, situs));
		tag.putLong(KotaLembar.KAS_KOTA, KotaEkonomi.kasKota(server, situs));
		UUID pelindung = PaktaLindung.pelindung(situs);
		if (pelindung != null) {
			CompoundTag p = new CompoundTag();
			Halls.HallView hall = Halls.hallOf(pelindung);
			LembarKunci.teks(p, LembarKunci.NAMA, hall == null || hall.name().isEmpty() ? Component.translatable(K + "pelindung.tanpa_nama")
					: Component.literal(hall.name()));
			p.putBoolean(KotaLembar.PEMAIN, true);
			p.putBoolean(LembarKunci.ANDA, pelindung.equals(player.getUUID()));
			tag.put(KotaLembar.PELINDUNG, p);
		}
	}

	/** One requirement row: ok B, teks T, punya T. */
	static CompoundTag syarat(boolean ok, Component teks, @Nullable Component punya) {
		CompoundTag s = new CompoundTag();
		s.putBoolean(LembarKunci.OK, ok);
		LembarKunci.teks(s, LembarKunci.TEKS, teks);
		if (punya != null) {
			LembarKunci.teks(s, KotaLembar.Cabang.PUNYA, punya);
		}
		return s;
	}

	/** One button row of {@link KotaLembar#AKSI}: id S, nama T, bisa B, alasan T. */
	static CompoundTag tombol(String id, Component nama, @Nullable Component alasan) {
		CompoundTag a = new CompoundTag();
		a.putString(LembarKunci.ID, id);
		LembarKunci.teks(a, LembarKunci.NAMA, nama);
		a.putBoolean(LembarKunci.BISA, alasan == null);
		if (alasan != null) {
			LembarKunci.teks(a, LembarKunci.ALASAN, alasan);
		}
		return a;
	}

	/** A text line row: teks T. */
	static CompoundTag baris(Component teks) {
		CompoundTag t = new CompoundTag();
		LembarKunci.teks(t, LembarKunci.TEKS, teks);
		return t;
	}

	/** The KotaLembar {@code tahap} id of an age. */
	static String tahap(int umur) {
		return switch (WargaInfo.Tahap.dari(umur)) {
			case BAYI -> KotaLembar.TAHAP_BAYI;
			case ANAK -> KotaLembar.TAHAP_ANAK;
			case REMAJA -> KotaLembar.TAHAP_REMAJA;
			case MUDA -> KotaLembar.TAHAP_MUDA;
			case DEWASA -> KotaLembar.TAHAP_DEWASA;
			case TUA -> KotaLembar.TAHAP_TUA;
			case PENSIUN -> KotaLembar.TAHAP_PENSIUN;
		};
	}

	/**
	 * The warga keys of a list row (42.11 "Sizes": warga, nama, umur, tahap, peran, status, rupa) plus job, mood face,
	 * watak and what the resident thinks of the player; {@code kartu} adds the card keys (kelamin, ahli, akar).
	 */
	static CompoundTag warga(Warga w, @Nullable ServerPlayer player, boolean kartu) {
		CompoundTag t = new CompoundTag();
		t.putInt(KotaLembar.WARGA, w.id);
		LembarKunci.teks(t, LembarKunci.NAMA, WargaApi.nama(w));
		int umur = WargaApi.umur(w);
		t.putInt(KotaLembar.UMUR, umur);
		t.putString(KotaLembar.TAHAP, tahap(umur));
		NpcRole role = w.peranNpc();
		t.putString(LembarKunci.PERAN, role.name());
		LembarKunci.teks(t, KotaLembar.KERJA, Component.translatable(role.titleKey()));
		t.putString(KotaLembar.STATUS, switch (w.status) {
			case HIDUP -> KotaLembar.ST_HIDUP;
			default -> w.status.name().toLowerCase(Locale.ROOT);
		});
		t.putInt(KotaLembar.RUPA, w.look);
		t.putInt(LembarKunci.POTRET, -1);
		t.putString(KotaLembar.WAJAH, WargaApi.kodeWajah(w, 0L).name().toLowerCase(Locale.ROOT));
		if (w.watak >= 0 && w.watak < Tokoh.WATAK.size()) {
			t.putString(KotaLembar.WATAK, Tokoh.WATAK.get(w.watak));
		}
		if (!w.tokoh.isEmpty()) {
			t.putString(LembarKunci.TOKOH_ID, w.tokoh);
		}
		if (player != null) {
			Ingatan ing = WargaApi.ingatan(w, player.getUUID());
			int kesan = ing == null ? 0 : ing.kesan;
			int kenal = ing == null ? 0 : ing.kenal;
			t.putInt(KotaLembar.KESAN, kesan);
			t.putInt(KotaLembar.KENAL, kenal);
			t.putString(KotaLembar.AKRAB, kesan <= -40 ? KotaLembar.AKRAB_MUSUH : kenal <= 0 ? KotaLembar.AKRAB_ASING
					: kesan >= 60 ? KotaLembar.AKRAB_SAHABAT : kesan >= 30 ? KotaLembar.AKRAB_AKRAB : KotaLembar.AKRAB_KENAL);
		}
		if (kartu) {
			t.putInt(KotaLembar.KELAMIN, w.pria() ? KotaLembar.PRIA : KotaLembar.WANITA);
			t.putInt(KotaLembar.AHLI, w.ahli);
			t.putInt(KotaLembar.AKAR, w.akar);
		}
		return t;
	}

	static ListTag daftar(List<CompoundTag> rows) {
		ListTag list = new ListTag();
		for (int i = 0; i < rows.size() && i < LembarKunci.DAFTAR_MAKS; i++) {
			list.add(rows.get(i));
		}
		return list;
	}

	/** {@code kunci_layar}: the whole screen locked with a reason. */
	static CompoundTag kunci(CompoundTag tag, Component alasan) {
		CompoundTag k = new CompoundTag();
		LembarKunci.teks(k, LembarKunci.ALASAN, alasan);
		tag.put(LembarKunci.KUNCI_LAYAR, k);
		return tag;
	}

	private LembarKotaDasar() {
	}
}
