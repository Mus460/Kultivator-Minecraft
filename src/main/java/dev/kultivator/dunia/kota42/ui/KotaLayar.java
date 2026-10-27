package dev.kultivator.dunia.kota42.ui;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaHidupCommands;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.NamaWarga;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server entry of module 42U "Layar" (DESIGN 42.11): registers the 42U-1 screens ({@code warga_kartu},
 * {@code warga_pohon}, {@code kota_buku}, {@code kota_milikku}, {@code rumah}, {@code toko_pemain}, {@code investasi},
 * {@code gudang}, {@code lapak_sewa}) and calls 42U-2's {@link LembarKotaDasar#init()}. The shared pieces of
 * {@link LembarKotaDasar} (place, selection, header, warga rows, result line) are reused; this class adds what the
 * 42U-1 screens need on top:
 * <ul>
 * <li><b>Sessions</b>: an NPC session (the NPC serves the screen, see {@link #JASA}), an anchor session ({@link #bukaDi}:
 * a counter, a sign, the player inside his own shop) or a remote one (hub card, command, a jump from a remote screen):
 * remote sheets carry {@link KotaLembar#JAUH} and {@link KotaLembar#DI_MANA}, the client disables every write and the
 * server would drop it anyway ({@code TempatLayar}).</li>
 * <li><b>Tab jumps</b> ("No GUI twice", jadwal 0.1): {@link #daftarTab} lets a tab op open another screen (Buku Kota's
 * Acara opens the 38 {@code acara} board, Harga the trade ledger, Milikku the asset list). Other {@code tab:TAB:ARG}
 * ops store the selection ({@link LembarKotaDasar#setPilih}).</li>
 * <li><b>Services</b>: one {@code TempatDialog} service provider lists the screens an NPC serves in the dialog's Jasa
 * tablet (Yamen judge and village head: houses, stalls, town book; warehouse keeper and porters: warehouse; broker and
 * banker: investments).</li>
 * <li><b>Commands</b>: {@code /kultivasi kotaku buku|milikku|harga|kartu [nama]|pohon [nama]} (remote, read only).</li>
 * </ul>
 * Server only; never references client classes.
 */
public final class KotaLayar {

	/** Lang prefix of every 42U-1 text ({@code lang-src/kota_hidup_ui}). */
	public static final String K = "kultivator.kota42.ui.";

	/** One op verb of a 42U-1 screen ({@code op[0]} the verb). */
	@FunctionalInterface
	public interface Aksi {
		void aksi(ServerPlayer player, int npcId, long situs, String[] op);
	}

	/** A tab op that opens another screen instead; true when it handled the op. */
	@FunctionalInterface
	public interface Lompat {
		boolean lompat(ServerPlayer player, int npcId, long situs, String[] op);
	}

	/** Roles serving each screen in the dialog's Jasa tablet (and accepted as the NPC of a session). */
	static final Map<String, Set<NpcRole>> JASA = Map.of(
			KotaLembar.Rumah.LAYAR, Set.of(NpcRole.HAKIM, NpcRole.KEPALA_DESA, NpcRole.TETUA_DESA, NpcRole.MENTERI, NpcRole.TUAN_BENTENG),
			KotaLembar.LapakSewa.LAYAR, Set.of(NpcRole.HAKIM, NpcRole.KEPALA_DESA, NpcRole.TETUA_DESA, NpcRole.PEDAGANG_BAZAR, NpcRole.PETUGAS_BEA),
			KotaLembar.Gudang.LAYAR, Set.of(NpcRole.PENJAGA_GUDANG, NpcRole.KULI, NpcRole.SYAHBANDAR, NpcRole.KAFILAH_BASHI, NpcRole.KULI_GUNUNG),
			KotaLembar.Investasi.LAYAR, Set.of(NpcRole.PIALANG, NpcRole.BANKIR),
			KotaLembar.Buku.LAYAR, Set.of(NpcRole.HAKIM, NpcRole.KEPALA_DESA, NpcRole.TETUA_DESA, NpcRole.MENTERI, NpcRole.PUSTAKAWAN,
					NpcRole.SASTRAWAN));
	/** Order of the Jasa rows. */
	private static final List<String> JASA_URUT = List.of(KotaLembar.Buku.LAYAR, KotaLembar.Rumah.LAYAR, KotaLembar.LapakSewa.LAYAR,
			KotaLembar.Gudang.LAYAR, KotaLembar.Investasi.LAYAR);

	private static final Map<String, LembarKotaDasar.Bangun> PEMBANGUN = new ConcurrentHashMap<>();
	private static final Map<String, Aksi> BAWAAN = new ConcurrentHashMap<>();
	private static final Map<String, Aksi> MODUL = new ConcurrentHashMap<>();
	private static final Map<String, Lompat> LOMPAT = new ConcurrentHashMap<>();
	private static final Map<String, List<LembarKotaDasar.Pengisi>> PENGISI = new ConcurrentHashMap<>();
	/** Player -> screens the player opened at an anchor (writes allowed there). */
	private static final Map<UUID, Set<String>> JANGKAR = new HashMap<>();
	private static final Map<UUID, Object[]> PESAN = new HashMap<>();
	private static boolean initialised;

	/** Registers every 42 screen (42U-1 and 42U-2). Called once from {@code KotaHidup.init}. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		LembarKotaDasar.init();
		LembarWarga.init();
		LembarPohon.init();
		LembarBuku.init();
		LembarRumah.init();
		LembarToko.init();
		LembarInvestasi.init();
		LembarGudang.init();
		CeritaApi.daftarJasa(KotaLayar::jasa);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			JANGKAR.remove(handler.getPlayer().getUUID());
			PESAN.remove(handler.getPlayer().getUUID());
		});
		perintah();
	}

	// ================================================================== registry

	/** A 42U-1 screen with its builder (the place already resolved, {@link LembarKotaDasar#situs}). */
	static void daftar(String layar, LembarKotaDasar.Bangun bangun) {
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

	/** Tab {@code tab} of {@code layar} opens another screen ({@code op} is the whole split tab op). */
	static void daftarTab(String layar, String tab, Lompat lompat) {
		LOMPAT.put(layar + ":" + tab, lompat);
	}

	/** A gameplay module (42D/42E/42F...) handles {@code verb} of a 42U-1 screen itself; wins over the default. */
	public static void daftarAksi(String layar, String verb, Aksi aksi) {
		if (layar != null && verb != null && aksi != null) {
			MODUL.put(layar + ":" + verb, aksi);
		}
	}

	/** True when a module registered {@code verb} of {@code layar} (buttons without a default appear only then). */
	public static boolean adaModul(String layar, String verb) {
		return MODUL.containsKey(layar + ":" + verb);
	}

	/** A module adds or replaces blocks of a 42U-1 sheet after the builder ran (keys of {@link KotaLembar}). */
	public static void addPengisi(String layar, LembarKotaDasar.Pengisi pengisi) {
		if (layar != null && pengisi != null) {
			PENGISI.computeIfAbsent(layar, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(pengisi);
		}
	}

	// ================================================================== opening

	/** Opens {@code layar} for NPC {@code npcId} (the NPC must serve it, see {@link #JASA}); false when refused. */
	public static boolean buka(ServerPlayer player, String layar, int npcId) {
		if (npcId < 0) {
			return bukaJauh(player, layar, null);
		}
		CompoundTag tag = bangun(layar, player, npcId);
		if (tag == null) {
			return false;
		}
		TempatLayar.buka(player, layar, npcId, tag);
		return true;
	}

	/**
	 * Opens {@code layar} at a service anchor (a cash counter, a "dijual" sign, the shop the player stands in): writes are
	 * allowed within {@link TempatLayar#JANGKAU} blocks of {@code jangkar}.
	 */
	public static boolean bukaDi(ServerPlayer player, String layar, BlockPos jangkar, @Nullable String tab) {
		JANGKAR.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(layar);
		CompoundTag tag = bangun(layar, player, -1);
		if (tag == null) {
			return false;
		}
		if (tab != null && !tab.isEmpty()) {
			tag.putString(LembarKunci.TAB, tab);
		}
		TempatLayar.bukaDi(player, layar, jangkar, tag);
		return true;
	}

	/** Opens {@code layar} as a remote screen (read only), on tab {@code tab} (null = the sheet's). */
	public static boolean bukaJauh(ServerPlayer player, String layar, @Nullable String tab) {
		Set<String> j = JANGKAR.get(player.getUUID());
		if (j != null) {
			j.remove(layar);
		}
		CompoundTag tag = PEMBANGUN.containsKey(layar) ? bangun(layar, player, -1) : LembarKotaDasar.bangun(layar, player, -1);
		if (tag == null) {
			return false;
		}
		if (tab != null && !tab.isEmpty()) {
			tag.putString(LembarKunci.TAB, tab);
		}
		TempatLayar.buka(player, layar, -1, tag);
		return true;
	}

	/** True when the open {@code layar} of this player has no NPC and no anchor (read only). */
	static boolean jauh(ServerPlayer player, int npcId, String layar) {
		if (npcId >= 0) {
			return false;
		}
		Set<String> j = JANGKAR.get(player.getUUID());
		return j == null || !j.contains(layar);
	}

	/** The NPC of a session when it is a town NPC of one of {@code peran}, else null. */
	static @Nullable KultivatorNpc npc(ServerPlayer player, int npcId) {
		return npcId >= 0 && player.level().getEntity(npcId) instanceof KultivatorNpc npc ? npc : null;
	}

	static @Nullable CompoundTag bangun(String layar, ServerPlayer player, int npcId) {
		LembarKotaDasar.Bangun b = PEMBANGUN.get(layar);
		if (b == null) {
			return null;
		}
		long situs = LembarKotaDasar.situs(player, npcId);
		CompoundTag tag;
		try {
			tag = b.bangun(player, npcId, situs);
		} catch (RuntimeException ex) {
			Kultivator.LOGGER.warn("42 sheet {} failed to build", layar, ex);
			return null;
		}
		if (tag == null) {
			return null;
		}
		for (LembarKotaDasar.Pengisi p : PENGISI.getOrDefault(layar, List.of())) {
			try {
				p.isi(player, npcId, situs, tag);
			} catch (RuntimeException ex) {
				Kultivator.LOGGER.warn("42 sheet filler of {} failed", layar, ex);
			}
		}
		if (jauh(player, npcId, layar)) {
			tag.putBoolean(KotaLembar.JAUH, true);
			LembarKunci.teks(tag, KotaLembar.DI_MANA, Component.translatable(K + "di_mana." + layar));
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
		long situs = LembarKotaDasar.situs(player, npcId);
		if (LembarKunci.OP_TAB.equals(verb)) {
			Lompat l = LOMPAT.get(layar + ":" + LembarKunci.arg(parts, 1));
			if (l != null && l.lompat(player, npcId, situs, parts)) {
				return;
			}
			if (parts.length > 2 && !parts[2].isEmpty()) {
				LembarKotaDasar.setPilih(player, layar, parts[2]);
			}
			return;
		}
		if (LembarKunci.OP_TANDAI.equals(verb)) {
			tandai(player, LembarKunci.argInt(parts, 1, 0), LembarKunci.argInt(parts, 2, 0));
			return;
		}
		if (LembarKunci.OP_HALAMAN.equals(verb) || LembarKunci.OP_LIHAT.equals(verb)) {
			Aksi a = BAWAAN.get(layar + ":" + verb);
			if (a != null) {
				a.aksi(player, npcId, situs, parts);
			}
			return;
		}
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

	// ================================================================== result line

	/** Remembers the result line for the next 42U-1 sheet and shows it on the action bar ({@code jenis} a PESAN_* id). */
	public static void pesan(ServerPlayer player, Component teks, String jenis) {
		PESAN.put(player.getUUID(), new Object[] {teks, jenis});
		ChatFormatting warna = LembarKunci.PESAN_OK.equals(jenis) ? ChatFormatting.GREEN
				: LembarKunci.PESAN_BAHAYA.equals(jenis) ? ChatFormatting.RED : ChatFormatting.YELLOW;
		Msg.actionBar(player, teks.copy().withStyle(warna));
	}

	/** The usual answer of an API returning null on success or the refusal. */
	static void hasil(ServerPlayer player, @Nullable Component tolak, Component ok) {
		if (tolak != null) {
			pesan(player, tolak, LembarKunci.PESAN_BAHAYA);
		} else {
			pesan(player, ok, LembarKunci.PESAN_OK);
		}
	}

	/** "Tunjuk": the place and its bearing in chat, plus the result line. */
	static void tandai(ServerPlayer player, int x, int z) {
		double dx = x + 0.5D - player.getX();
		double dz = z + 0.5D - player.getZ();
		int jarak = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		Component arah = Component.translatable(K + "arah." + LembarKotaDasar.arah(dx, dz));
		Msg.chat(player, Component.translatable(K + "tandai", x, z, jarak, arah).withStyle(ChatFormatting.AQUA));
		pesan(player, Component.translatable(K + "tandai.pesan", arah, jarak), LembarKunci.PESAN_OK);
	}

	// ================================================================== services in the dialog

	private static List<TempatDialog.JasaEntri> jasa(ServerPlayer player, KultivatorNpc npc) {
		NpcRole role = npc.role();
		if (role == null || !WargaApi.aktif()) {
			return List.of();
		}
		List<TempatDialog.JasaEntri> out = new ArrayList<>();
		for (String layar : JASA_URUT) {
			if (JASA.getOrDefault(layar, Set.of()).contains(role)) {
				out.add(new TempatDialog.JasaEntri(layar, Component.translatable(K + "jasa." + layar), ikonJasa(layar),
						Component.translatable(K + "jasa." + layar + ".catatan"), false, null));
			}
		}
		// 42E: the protector of a held town offers the pact, the clerk of the player's own recruitment office the office sheet
		if (role == NpcRole.JUARA_SEKTE) {
			out.add(jasaSekte(KotaLembar.Pakta.LAYAR, LembarKunci.Ikon.TIMBANGAN));
		} else if (role == NpcRole.PETUGAS_GILDA
				&& dev.kultivator.dunia.kota42.sekte.KantorRekrut.kantor(TempatDialog.situs(player), player.getUUID()) != null) {
			out.add(jasaSekte(KotaLembar.Kantor.LAYAR, IKON_BUKU));
		}
		jasaBalai(out, player, role);
		return out;
	}

	/** Roles that sit in a town's hall and serve the office sheet, the court sheet and the lots sheet. */
	private static final Set<NpcRole> BALAI_JABATAN = Set.of(NpcRole.HAKIM, NpcRole.KEPALA_DESA, NpcRole.TETUA_DESA, NpcRole.MENTERI);
	private static final Set<NpcRole> BALAI_KAVLING = Set.of(NpcRole.KEPALA_DESA, NpcRole.TETUA_DESA, NpcRole.MENTERI, NpcRole.TUAN_BENTENG);

	/**
	 * The hall's own sheets (D-42U; they opened only from the read-only hub before): the office of the player in this town
	 * (every official), the cases of the court (the judge; locked below the deputy judge's rank, with the reason) and the
	 * town's growth lots (the heads and the lord of the keep, when the town has lots).
	 */
	private static void jasaBalai(List<TempatDialog.JasaEntri> out, ServerPlayer player, NpcRole role) {
		long situs = TempatDialog.situs(player);
		if (BALAI_JABATAN.contains(role) && dev.kultivator.dunia.kota42.ekonomi.Jabatan.aktif()) {
			out.add(new TempatDialog.JasaEntri(KotaLembar.Jabatan.LAYAR, Component.translatable(K + "jasa." + KotaLembar.Jabatan.LAYAR),
					LembarKunci.Ikon.CAP_MERAH, Component.translatable(K + "jasa." + KotaLembar.Jabatan.LAYAR + ".catatan"), false, null));
		}
		if (role == NpcRole.HAKIM && dev.kultivator.dunia.kota42.ekonomi.Jabatan.aktif()) {
			boolean hakim = situs != 0L && dev.kultivator.dunia.kota42.ekonomi.Jabatan.pangkat(player.getUUID(), situs) >= KotaLembar.Jabatan.HAKIM_PEMBANTU;
			out.add(new TempatDialog.JasaEntri(KotaLembar.Perkara.LAYAR, Component.translatable(K + "jasa." + KotaLembar.Perkara.LAYAR),
					LembarKunci.Ikon.YAMEN, Component.translatable(K + "jasa." + KotaLembar.Perkara.LAYAR + ".catatan"), !hakim,
					hakim ? null : Component.translatable(K + "jasa." + KotaLembar.Perkara.LAYAR + ".kunci")));
		}
		if (BALAI_KAVLING.contains(role) && situs != 0L && !dev.kultivator.dunia.kota42.tumbuh.Kavling.daftar(situs).isEmpty()) {
			out.add(new TempatDialog.JasaEntri(KotaLembar.Kavling.LAYAR, Component.translatable(K + "jasa." + KotaLembar.Kavling.LAYAR),
					LembarKunci.Ikon.BEBAS_PERTAMA + 14, Component.translatable(K + "jasa." + KotaLembar.Kavling.LAYAR + ".catatan"), false, null));
		}
	}

	private static TempatDialog.JasaEntri jasaSekte(String layar, int ikon) {
		return new TempatDialog.JasaEntri(layar, Component.translatable(K + "jasa." + layar), ikon,
				Component.translatable(K + "jasa." + layar + ".catatan"), false, null);
	}

	/** Tempat atlas slots of the 38UI additions: 121 book, 123 key (client {@code IkonTempat.BUKU}/{@code KUNCI}). */
	private static final int IKON_BUKU = LembarKunci.Ikon.BEBAS_PERTAMA + 9;
	private static final int IKON_KUNCI = LembarKunci.Ikon.BEBAS_PERTAMA + 11;

	private static int ikonJasa(String layar) {
		return switch (layar) {
			case KotaLembar.Rumah.LAYAR -> IKON_KUNCI;
			case KotaLembar.LapakSewa.LAYAR -> LembarKunci.Ikon.LAPAK_SEKTE;
			case KotaLembar.Gudang.LAYAR -> LembarKunci.Ikon.KAFILAH;
			case KotaLembar.Investasi.LAYAR -> LembarKunci.Ikon.TIMBANGAN;
			default -> IKON_BUKU;
		};
	}

	/** True when NPC {@code npcId} serves {@code layar} (builders refuse other NPCs). */
	static boolean melayani(ServerPlayer player, int npcId, String layar) {
		KultivatorNpc npc = npc(player, npcId);
		return npc != null && npc.role() != null && JASA.getOrDefault(layar, Set.of()).contains(npc.role());
	}

	// ================================================================== commands

	private static void perintah() {
		KotaHidupCommands.daftar("buku", () -> Commands.literal("buku").executes(ctx -> {
			bukaJauh(ctx.getSource().getPlayerOrException(), KotaLembar.Buku.LAYAR, null);
			return 1;
		}).then(Commands.argument("tab", StringArgumentType.word()).suggests((ctx, b) -> {
			KotaLembar.Buku.TABS.forEach(b::suggest);
			return b.buildFuture();
		}).executes(ctx -> {
			bukaJauh(ctx.getSource().getPlayerOrException(), KotaLembar.Buku.LAYAR, StringArgumentType.getString(ctx, "tab"));
			return 1;
		})));
		KotaHidupCommands.daftar("milikku", () -> Commands.literal("milikku").executes(ctx -> {
			bukaJauh(ctx.getSource().getPlayerOrException(), KotaLembar.Milikku.LAYAR, null);
			return 1;
		}));
		KotaHidupCommands.daftar("acara", () -> Commands.literal("acara").executes(ctx -> {
			bukaJauh(ctx.getSource().getPlayerOrException(), KotaLembar.Buku.LAYAR, KotaLembar.Buku.TAB_ACARA);
			return 1;
		}));
		KotaHidupCommands.daftar("harga", () -> Commands.literal("harga").executes(ctx -> {
			bukaJauh(ctx.getSource().getPlayerOrException(), KotaLembar.Gudang.LAYAR, KotaLembar.Gudang.TAB_HARGA);
			return 1;
		}));
		KotaHidupCommands.daftar("kartu", () -> Commands.literal("kartu").executes(ctx -> kartu(ctx.getSource().getPlayerOrException(), null, false))
				.then(Commands.argument("nama", StringArgumentType.greedyString())
						.executes(ctx -> kartu(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "nama"), false))));
		KotaHidupCommands.daftar("pohon", () -> Commands.literal("pohon").executes(ctx -> kartu(ctx.getSource().getPlayerOrException(), null, true))
				.then(Commands.argument("nama", StringArgumentType.greedyString())
						.executes(ctx -> kartu(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "nama"), true))));
	}

	private static int kartu(ServerPlayer player, @Nullable String nama, boolean pohon) {
		Warga w = cari(player, nama);
		if (w == null) {
			pesan(player, Component.translatable(K + "cmd.tidak_ketemu"), LembarKunci.PESAN_BAHAYA);
			return 0;
		}
		String layar = pohon ? KotaLembar.Pohon.LAYAR : KotaLembar.Kartu.LAYAR;
		LembarKotaDasar.setPilih(player, layar, Integer.toString(w.id));
		return bukaJauh(player, layar, null) ? 1 : 0;
	}

	/** By id, by full or given name in the player's place, else the nearest civilian within 8 blocks. */
	static @Nullable Warga cari(ServerPlayer player, @Nullable String siapa) {
		if (siapa != null && !siapa.isBlank()) {
			try {
				return WargaApi.byId(Integer.parseInt(siapa.trim()));
			} catch (NumberFormatException ignored) {
				// a name
			}
			TempatInfo tempat = KotaHidupCommands.tempatPemain(player);
			if (tempat == null) {
				return null;
			}
			String q = siapa.trim().toLowerCase(Locale.ROOT);
			Warga kira = null;
			for (Warga w : WargaApi.wargaTempat(tempat.situsId())) {
				String full = NamaWarga.lengkap(w).toLowerCase(Locale.ROOT);
				if (full.equals(q)) {
					return w;
				}
				if (kira == null && (full.contains(q) || NamaWarga.diri(w).toLowerCase(Locale.ROOT).equals(q))) {
					kira = w;
				}
			}
			return kira;
		}
		KultivatorNpc best = null;
		double bestSq = 64.0D;
		for (KultivatorNpc npc : player.level().getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(8.0D), KultivatorNpc::isAlive)) {
			double d = npc.distanceToSqr(player);
			if (d < bestSq && WargaApi.dari(npc) != null) {
				bestSq = d;
				best = npc;
			}
		}
		return best == null ? null : WargaApi.dari(best);
	}

	private KotaLayar() {
	}
}
