package dev.kultivator.sekte.gabung.ui;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.gabung.GabungCommands;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.langit.npc.LangitNpcPlan;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Module 41U "Layar" server side (DESIGN 41.10): the one door every 41 screen opens and answers through, on top of
 * {@link TempatLayar} (38.U). Ids and keys are in {@link GabungLembar}; 41U-1 builds Direktori, Lamaran, Kartu and Pohon
 * Pangkat ({@code LembarDirektori}, {@code LembarLamaran}, {@code LembarKartu}, {@code LembarPangkat}); 41U-2 and the
 * gameplay modules register theirs with {@link #daftar}.
 *
 * <h2>Personal screens without an NPC</h2>
 * A 41 screen opened from the hub card or a {@code /kultivasi gabung} command has no NPC. {@link TempatLayar} lets such a
 * hub session send read ops only, but the Kartu Anggota and the rank tree act ("Ambil jatah", "Ajukan Kenaikan", 41.13.2:
 * every command runs the same action as its button). So {@link #buka} opens them as an anchor session at the player's
 * feet ({@link TempatLayar#bukaDi}): actions are accepted while the player stays within {@link TempatLayar#JANGKAU}
 * blocks of where the screen opened. A hub card's {@code lihat} reaches the builder wrapped by {@link #daftar}, which
 * re-opens the sheet the same way. Opened by an NPC ({@code npcId >= 0}), the normal NPC session applies.
 *
 * <h2>Actions</h2>
 * Every op of a 41 screen passes {@link #aksi}: first an {@link Aksi} a gameplay module registered with
 * {@link #daftarAksi} (41A Keluar for {@code pamit}, 41B for {@code ajukan} on the Lamaran, 41C for {@code jatah} ...), then
 * the default of the screen's builder ({@link #bawaan}), else "Belum tersedia". {@code tandai:X:Z} is answered here for
 * every screen. The result line of an action is kept per player with {@link #pesan} and shown in the next sheet.
 *
 * <h2>Extra sheet keys</h2>
 * A module may add or override keys of any 41 sheet after it was built with {@link #addPengisi} (41D fills the
 * rank tree's election line, 41C-K the Shifu's next lesson, 41B the candidate traits).
 *
 * <h2>Rank trials</h2>
 * The special requirement of a rank (41.6.1: "Uji Ulang", Ujian Murid Dalam, top 3 of the tournament or one A mission,
 * the heir election, the elder trial, the elders' support, the succession) is one flag per rank in the member's
 * {@code bagian("pangkat")}: the module that runs the trial calls {@link #tandaiLulus}; the rank tree reads
 * {@link #lulus}; a promotion clears it.
 *
 * <p>Server only; never references client classes. Called by {@code Gabung.init()} while 41 is on.
 */
public final class GabungLayar {

	/** An op implementation; {@code op} is split ({@code op[0]} the verb). Show the result with {@link #pesan}. */
	@FunctionalInterface
	public interface Aksi {
		void aksi(ServerPlayer player, int npcId, String[] op);
	}

	/** Adds or overrides keys of a built sheet (runs after the builder, before the sheet is sent). */
	@FunctionalInterface
	public interface Pengisi {
		void isi(ServerPlayer player, int npcId, CompoundTag lembar);
	}

	/** Key of the per-module compound holding the rank trial flags ({@code Keanggotaan.bagian}). */
	public static final String BAGIAN_PANGKAT = "pangkat";
	/** Key of this module's own per-member extras ({@code Keanggotaan.bagian}): gift day, Shifu change day. */
	public static final String BAGIAN_UI = "ui";

	private static final Map<String, TempatLayar.Pembangun> PEMBANGUN = new ConcurrentHashMap<>();
	private static final Map<String, Aksi> MODUL = new ConcurrentHashMap<>();
	private static final Map<String, Aksi> BAWAAN = new ConcurrentHashMap<>();
	private static final Map<String, List<Pengisi>> PENGISI = new ConcurrentHashMap<>();
	private static final Map<UUID, Object[]> PESAN = new HashMap<>();
	/** Players testing {@code gabung skala N}: every sheet built for them is laid out as GUI scale N would (the 38.U.2 test). */
	private static final Map<UUID, Integer> UJI_SKALA = new ConcurrentHashMap<>();
	private static boolean initialised;

	/** Registers the 41 screens and the player command subs. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		LembarDirektori.init();
		LembarLamaran.init();
		LembarKartu.init();
		LembarPangkat.init();
		// 41U-2
		LembarPaviliun.init();
		LembarPapan.init();
		LembarKediaman.init();
		LembarPolitik.init();
		LembarHukum.init();
		GabungCommands.daftar("direktori", () -> Commands.literal("direktori").executes(ctx -> {
			bukaLayar(ctx.getSource().getPlayerOrException(), GabungLembar.Direktori.LAYAR, -1, null);
			return 1;
		}));
		GabungCommands.daftar("kartu", () -> Commands.literal("kartu").executes(ctx -> {
			bukaLayar(ctx.getSource().getPlayerOrException(), GabungLembar.Kartu.LAYAR, -1, null);
			return 1;
		}).then(Commands.argument("tab", StringArgumentType.word()).suggests((ctx, b) -> {
			GabungLembar.Kartu.TABS.forEach(b::suggest);
			return b.buildFuture();
		}).executes(ctx -> {
			bukaLayar(ctx.getSource().getPlayerOrException(), GabungLembar.Kartu.LAYAR, -1, StringArgumentType.getString(ctx, "tab"));
			return 1;
		})));
		GabungCommands.daftar("pangkat", () -> Commands.literal("pangkat").executes(ctx -> {
			bukaLayar(ctx.getSource().getPlayerOrException(), GabungLembar.PohonPangkat.LAYAR, -1, null);
			return 1;
		}));
		GabungCommands.daftar("naik", () -> Commands.literal("naik").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			LembarPangkat.ajukanBerikut(player);
			Object[] p = PESAN.remove(player.getUUID());
			return p == null || !LembarKunci.PESAN_BAHAYA.equals(p[1]) ? 1 : 0;
		}));
		// GUI scale test (D-41U): `gabung skala <0|2|3|4> [layar]`; the old-sheet screens lay out as that scale would (0 = off)
		GabungCommands.daftar("skala", () -> Commands.literal("skala").then(Commands.argument("n", IntegerArgumentType.integer(0, 4))
				.executes(ctx -> ujiSkala(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "n"), null))
				.then(Commands.argument("layar", StringArgumentType.word()).suggests((ctx, b) -> {
					PEMBANGUN.keySet().stream().sorted().forEach(b::suggest);
					return b.buildFuture();
				}).executes(ctx -> ujiSkala(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "n"),
						StringArgumentType.getString(ctx, "layar"))))));
		GabungCommands.daftar("info", () -> Commands.literal("info").then(Commands.argument("sekte", StringArgumentType.word())
				.suggests((ctx, b) -> {
					SekteApi.diplomasi().sects().forEach(b::suggest);
					return b.buildFuture();
				}).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					LembarDirektori.pilih(player, StringArgumentType.getString(ctx, "sekte"));
					bukaLayar(player, GabungLembar.Direktori.LAYAR, -1, null);
					return 1;
				})));
	}

	// ================================================================== registry

	/**
	 * Registers 41 screen {@code layar} with its builder: the generic action handler of this class, the hub open (anchor
	 * session at the player) and {@link #bukaLayar}. Call from the screen's init.
	 */
	public static void daftar(String layar, TempatLayar.Pembangun pembangun) {
		PEMBANGUN.put(layar, pembangun);
		TempatLayar.daftarHub(layar, (player, npcId, op) -> aksi(layar, player, npcId, op), (player, npcId) -> {
			CompoundTag lembar = bangun(layar, player, npcId);
			if (lembar != null && npcId < 0 && !TempatLayar.sedangMelihat(player, layar)) {
				// a hub card's first open: re-open as an anchor session so the screen may act (class javadoc)
				TempatLayar.bukaDi(player, layar, player.blockPosition(), lembar);
				return null;
			}
			return lembar;
		});
	}

	/** A gameplay module implements op {@code verb} of screen {@code layar} (wins over the 41U default). */
	public static void daftarAksi(String layar, String verb, Aksi aksi) {
		if (layar != null && verb != null && aksi != null) {
			MODUL.put(layar + ":" + verb, aksi);
		}
	}

	/** The 41U default of op {@code verb} of {@code layar} (used when no module registered one). */
	static void bawaan(String layar, String verb, Aksi aksi) {
		BAWAAN.put(layar + ":" + verb, aksi);
	}

	/** Adds a sheet filler of screen {@code layar}. */
	public static void addPengisi(String layar, Pengisi pengisi) {
		if (layar != null && pengisi != null) {
			PENGISI.computeIfAbsent(layar, k -> new CopyOnWriteArrayList<>()).add(pengisi);
		}
	}

	/** True when a module registered op {@code verb} of {@code layar}. */
	public static boolean adaAksi(String layar, String verb) {
		return MODUL.containsKey(layar + ":" + verb);
	}

	// ================================================================== opening

	/**
	 * Builds and opens 41 screen {@code layar} for {@code player}, from NPC {@code npcId} or (-1) as the player's own
	 * screen, on tab {@code tab} (null = the sheet's). False when the screen is not registered or the builder refused.
	 */
	public static boolean bukaLayar(ServerPlayer player, String layar, int npcId, @Nullable String tab) {
		CompoundTag lembar = bangun(layar, player, npcId);
		if (lembar == null) {
			if (!PEMBANGUN.containsKey(layar)) {
				pesan(player, Component.translatable("kultivator.gabung.belum_tersedia"), LembarKunci.PESAN_PERINGATAN);
				PESAN.remove(player.getUUID());
			}
			return false;
		}
		if (tab != null && !tab.isEmpty()) {
			lembar.putString(LembarKunci.TAB, tab);
		}
		buka(player, layar, npcId, lembar);
		return true;
	}

	/** Opens a built sheet: an NPC session for {@code npcId >= 0}, else an anchor session at the player's feet. */
	public static void buka(ServerPlayer player, String layar, int npcId, CompoundTag lembar) {
		if (npcId >= 0) {
			TempatLayar.buka(player, layar, npcId, lembar);
		} else {
			TempatLayar.bukaDi(player, layar, player.blockPosition(), lembar);
		}
	}

	/** Builds the sheet of a registered screen with its fillers, or null. */
	public static @Nullable CompoundTag bangun(String layar, ServerPlayer player, int npcId) {
		TempatLayar.Pembangun b = PEMBANGUN.get(layar);
		if (b == null) {
			return null;
		}
		CompoundTag lembar = b.bangun(player, npcId);
		if (lembar == null) {
			return null;
		}
		for (Pengisi p : PENGISI.getOrDefault(layar, List.of())) {
			try {
				p.isi(player, npcId, lembar);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Gabung sheet filler of {} failed", layar, exception);
			}
		}
		Integer uji = UJI_SKALA.get(player.getUUID());
		if (uji != null) {
			lembar.putInt("uji_skala", uji); // key of LayarTempatDasar.UJI_SKALA (the client class is not referenced here)
		}
		Object[] pesan = PESAN.remove(player.getUUID());
		if (pesan != null) {
			LembarKunci.teks(lembar, LembarKunci.PESAN, (Component) pesan[0]);
			lembar.putString(LembarKunci.PESAN_JENIS, (String) pesan[1]);
		}
		return lembar;
	}

	/** {@code gabung skala n [layar]}: sets (n 2-4) or clears (0) the layout test of the player and opens {@code layar}. */
	private static int ujiSkala(ServerPlayer player, int n, @Nullable String layar) {
		if (n > 0) {
			UJI_SKALA.put(player.getUUID(), n);
		} else {
			UJI_SKALA.remove(player.getUUID());
		}
		Msg.chat(player, Component.translatable(n > 0 ? "kultivator.gabung.ui.skala.hidup" : "kultivator.gabung.ui.skala.mati", n)
				.withStyle(ChatFormatting.GRAY));
		if (layar != null && !bukaLayar(player, layar, -1, null)) {
			return 0;
		}
		return 1;
	}

	// ================================================================== actions

	private static void aksi(String layar, ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		String verb = parts[0];
		if (LembarKunci.OP_TAB.equals(verb) || LembarKunci.OP_HALAMAN.equals(verb)) {
			return;
		}
		if (LembarKunci.OP_TANDAI.equals(verb)) {
			tandai(player, LembarKunci.argInt(parts, 1, 0), LembarKunci.argInt(parts, 2, 0));
			return;
		}
		Aksi a = MODUL.get(layar + ":" + verb);
		if (a == null) {
			a = BAWAAN.get(layar + ":" + verb);
		}
		if (a == null) {
			pesan(player, Component.translatable("kultivator.gabung.belum_tersedia"), LembarKunci.PESAN_PERINGATAN);
			return;
		}
		a.aksi(player, npcId, parts);
	}

	/** "Tunjuk di peta": the place and its bearing in chat (the town screens' way). */
	static void tandai(ServerPlayer player, int x, int z) {
		double dx = x + 0.5D - player.getX();
		double dz = z + 0.5D - player.getZ();
		int jarak = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		Component arah = Component.translatable("kultivator.gabung.ui.arah." + arah(dx, dz));
		Msg.chat(player, Component.translatable("kultivator.gabung.ui.tandai", x, z, jarak, arah).withStyle(ChatFormatting.AQUA));
		pesan(player, Component.translatable("kultivator.gabung.ui.tandai.pesan", arah, jarak), LembarKunci.PESAN_OK);
	}

	/** 8 sectors, 0 = north, clockwise, from a world offset. */
	static int arah(double dx, double dz) {
		double sudut = Math.toDegrees(Math.atan2(dx, -dz));
		return Math.floorMod((int) Math.round(sudut / 45.0D), 8);
	}

	/** Remembers the result line for the next sheet and shows it on the action bar ({@code jenis} a PESAN_* id). */
	public static void pesan(ServerPlayer player, Component teks, String jenis) {
		PESAN.put(player.getUUID(), new Object[] {teks, jenis});
		ChatFormatting warna = LembarKunci.PESAN_OK.equals(jenis) ? ChatFormatting.GREEN
				: LembarKunci.PESAN_BAHAYA.equals(jenis) ? ChatFormatting.RED : ChatFormatting.YELLOW;
		Msg.actionBar(player, teks.copy().withStyle(warna));
	}

	// ================================================================== rank trial flags

	/** The module that ran the trial of {@code pangkat} marks it passed (same flag as {@code Promosi.lulus}, cleared by the promotion). */
	public static void tandaiLulus(Keanggotaan k, Pangkat pangkat) {
		k.bagian(BAGIAN_PANGKAT).putBoolean("lulus." + pangkat.id(), true);
	}

	/** True when the trial of {@code pangkat} was passed. */
	public static boolean lulus(Keanggotaan k, Pangkat pangkat) {
		return k.bagian(BAGIAN_PANGKAT).getBooleanOr("lulus." + pangkat.id(), false);
	}

	static void hapusLulus(Keanggotaan k, Pangkat pangkat) {
		k.bagian(BAGIAN_PANGKAT).remove("lulus." + pangkat.id());
	}

	// ================================================================== sheet helpers

	/**
	 * The panel style of a sect screen by alignment (41.10): Lurus {@code giok}, Netral {@code kayu}, Iblis {@code batu}
	 * (tinted {@code #6A1420} by the client); {@code kertas} for an unknown sect ({@code aliran} -1).
	 */
	public static String gaya(int aliran) {
		return switch (aliran) {
			case 0 -> LembarKunci.GAYA_GIOK;
			case 1 -> LembarKunci.GAYA_KAYU;
			case 2 -> LembarKunci.GAYA_BATU;
			default -> LembarKunci.GAYA_KERTAS;
		};
	}

	/**
	 * A new sheet with the title and, when {@code sekte} is a known sect, the sect header of {@link GabungLembar} (name,
	 * ink colour, emblem, alignment and style, class, tier, corak, cloud palace, MEROSOT) plus the player's rank and PK
	 * there. Also runs the member's lazy daily upkeep ({@code Keanggotaan.sentuh}, 41.12.3).
	 */
	public static CompoundTag kepala(ServerPlayer player, @Nullable String sekte, Component judul) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = new CompoundTag();
		LembarKunci.teks(tag, LembarKunci.JUDUL, judul);
		Keanggotaan k = GabungApi.anggota(player);
		if (k.sentuh(player, GabungApi.hari(server))) {
			GabungData.get(server).setDirty();
		}
		DiplomasiService d = SekteApi.diplomasi();
		int aliran = sekte == null ? -1 : d.aliran(sekte);
		tag.putString(LembarKunci.GAYA, gaya(aliran));
		if (sekte == null || d.tier(sekte) <= 0) {
			return tag;
		}
		int tier = d.tier(sekte);
		tag.putString(GabungLembar.SEKTE_ID, sekte);
		LembarKunci.teks(tag, GabungLembar.NAMA_SEKTE, namaSekte(sekte));
		LembarKunci.teks(tag, LembarKunci.SUBJUDUL, namaSekte(sekte));
		tag.putInt(GabungLembar.WARNA, d.color(sekte));
		tag.putInt(GabungLembar.LAMBANG, d.emblem(sekte).pack());
		tag.putInt(GabungLembar.ALIRAN, Math.max(0, aliran));
		tag.putInt(GabungLembar.KELAS, KelasSekte.of(tier));
		tag.putInt(GabungLembar.TINGKAT, tier);
		tag.putString(GabungLembar.CORAK, GabungApi.corak(sekte).id());
		tag.putBoolean(GabungLembar.LANGIT, punyaIstana(server, sekte));
		GabungData.DataSekteGabung ds = GabungData.get(server).peekSekte(sekte);
		tag.putBoolean(GabungLembar.MEROSOT, ds != null && ds.merosot);
		boolean anggota = k.anggotaDi(sekte);
		tag.putInt(GabungLembar.PANGKAT_ANGGOTA, anggota ? k.pangkat.ordinal() : -1);
		if (anggota) {
			tag.putInt(LembarKunci.PK, k.pk);
			tag.putLong(GabungLembar.PK_TOTAL, k.pkTotal);
		}
		return tag;
	}

	/** The sect's shown name ({@code s3} when D does not know it). */
	public static Component namaSekte(@Nullable String sekte) {
		Component name = sekte == null || sekte.isEmpty() ? null : SekteApi.diplomasi().name(sekte);
		return name == null ? Component.literal(sekte == null || sekte.isEmpty() ? "?" : sekte) : name;
	}

	/** True when {@code sekte} owns one of this world's two cloud palaces (37, {@code LangitNpcPlan}). */
	public static boolean punyaIstana(MinecraftServer server, String sekte) {
		for (LangitNpcPlan.Istana istana : LangitNpcPlan.of(server.overworld().getSeed())) {
			if (sekte.equals(LangitNpcPlan.sectId(istana))) {
				return true;
			}
		}
		return false;
	}

	/** The plan entry of {@code sekte}, or null. */
	static @Nullable GerbangPlan.Entry entry(MinecraftServer server, String sekte) {
		return GabungApi.entry(server, sekte);
	}

	/** The name of a player (online, else the name cache) or of a named character / roster id; "?" when unknown. */
	public static Component namaPemegang(MinecraftServer server, String id) {
		if (id == null || id.isEmpty()) {
			return Component.literal("?");
		}
		if (dev.kultivator.sekte.gabung.dunia.TokohSekte.idSah(id)) {
			var t = dev.kultivator.sekte.gabung.dunia.TokohSekte.tokoh(server, id);
			if (t != null) {
				return Component.literal(t.nama());
			}
		}
		if (id.indexOf('#') > 0) {
			var r = dev.kultivator.sekte.mandiri.seteru.Roster.tokoh(server, id);
			return r != null ? r.nama() : Component.literal("?");
		}
		try {
			UUID uuid = UUID.fromString(id);
			ServerPlayer online = server.getPlayerList().getPlayer(uuid);
			if (online != null) {
				return online.getName();
			}
			return Component.literal(server.services().nameToIdCache().get(uuid).map(NameAndId::name).orElse("?"));
		} catch (IllegalArgumentException exception) {
			return Component.literal("?");
		}
	}

	/** {@code [ok, teks, punya?, saran?]} as one list entry. */
	static CompoundTag syarat(boolean ok, Component teks, @Nullable Component punya, @Nullable Component saran) {
		CompoundTag t = new CompoundTag();
		t.putBoolean(LembarKunci.OK, ok);
		LembarKunci.teks(t, LembarKunci.TEKS, teks);
		if (punya != null) {
			LembarKunci.teks(t, GabungLembar.PUNYA, punya);
		}
		if (saran != null) {
			LembarKunci.teks(t, GabungLembar.SARAN, saran);
		}
		return t;
	}

	/** Every registered 41 screen id (for {@code uji}). */
	public static List<String> terdaftar() {
		return new ArrayList<>(PEMBANGUN.keySet());
	}

	private GabungLayar() {
	}
}
