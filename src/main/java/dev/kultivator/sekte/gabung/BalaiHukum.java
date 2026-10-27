package dev.kultivator.sekte.gabung;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.tempat.kompleks.Kartu;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Law Hall (Balai Hukum, DESIGN 41.6.11; Baizhang Qinggui: the monastery's penalties up to expulsion). Registered as
 * the {@link GabungApi.Penegak} of every recorded violation and as a lazy {@link Keanggotaan.Penyentuh}.
 * <p>
 * The violation record ({@code Keanggotaan.pelanggaran}, 0-100) decays 1 point per 2 world days. Crossing a threshold of
 * {@link Aturan} opens a case: {@link Aturan#TEGURAN} a reprimand by the Discipline Elder the next time the member is in the
 * grounds; {@link Aturan#DENDA} and above a <b>trial</b> ({@link #sidang}) with three choices: accept the sentence, defend
 * oneself (one question of the Ujian Hati; right = record -10) or ask the Shifu to plead (bond >= 50: record -15, bond -10).
 * The sentence follows the record after those reductions: a fine of {@code 20 x class} PK, confinement in the Gua Renungan
 * ({@code 4 x points} real seconds, at most 20 minutes), demotion by one rank (with confinement), or expulsion. A summons
 * left unanswered for {@link #BATAS_HARI} world days is carried out anyway. A2 (killing a fellow member) is judged at once:
 * appear and be expelled, or flee and become a deserter.
 * <p>
 * Confinement: the member is brought to the cave ({@code TitikGabung.GUA_RENUNGAN}, else the Law Hall table, else the main
 * hall, else where he stands) and held by a formation seal (particles, no block): leaving {@link #KURUNGAN_RADIUS} blocks,
 * another dimension or an ender pearl brings him back (teleport + fall distance reset), at most {@link #KABUR_MAKS} times;
 * the next escape is desertion. Meditation keeps working inside. The rest may be bought off at {@link #TEBUS_PK} PK per
 * {@link #TEBUS_DETIK} seconds. Busy state {@code gabung.kurungan} (41.0 item 6) reads {@code kurunganSampai}.
 */
public final class BalaiHukum {

	/** World days a summons waits before the sentence is carried out without the member. */
	public static final int BATAS_HARI = 3;
	/** World days an A2 summons waits before the member counts as fled (deserter). */
	public static final int BATAS_A2_HARI = 1;
	/** Blocks around the confinement spot the seal allows. */
	public static final double KURUNGAN_RADIUS = 7.0D;
	/** Returns before an escape counts as desertion. */
	public static final int KABUR_MAKS = 3;
	/** Ransom: PK per this many remaining seconds. */
	public static final int TEBUS_PK = 5;
	public static final int TEBUS_DETIK = 10;
	/** Defence and the Shifu's plea (41.6.11). */
	public static final int BELA_POIN = 10;
	public static final int SHIFU_POIN = 15;
	public static final int SHIFU_IKATAN_MIN = 50;
	public static final int SHIFU_IKATAN_BIAYA = 10;
	/** Days absent before "Lalai" (A9). */
	public static final int LALAI_HARI = 30;
	/** Sentence levels. */
	public static final int TK_BERSIH = 0;
	public static final int TK_TEGURAN = 1;
	public static final int TK_DENDA = 2;
	public static final int TK_KURUNGAN = 3;
	public static final int TK_TURUN = 4;
	public static final int TK_USIR = 5;

	/** What a trial shows (41U {@code LembarHukum} and the chat card read the same record). */
	public record Sidang(String sekte, Component tetuaHukum, int catatan, int tingkat, Component hukuman, String aturan,
			String saksi, boolean a2, boolean bolehBela, boolean bolehShifu, @Nullable Component shifu, int ikatan) {
	}

	/** One Ujian Hati question: the lang key of the question and the shown answers; {@code benar} is the right one's index. */
	public record Soal(String kunci, List<String> jawaban, int benar) {
	}

	private record Kurungan(ResourceKey<Level> dim, BlockPos pos) {
	}

	/** Confined online members -> where. */
	private static final Map<UUID, Kurungan> KURUNGAN = new HashMap<>();
	private static boolean registered;

	/** From {@code Gabung.init} (module on). */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		GabungApi.addPenegak(BalaiHukum::langgar);
		// 41.6.11: meditation keeps running in the Gua Renungan, at x0.8
		dev.kultivator.cultivation.Meditation.addGainFactor(p -> KURUNGAN.containsKey(p.getUUID()) ? 0.8D : 1.0D);
		Keanggotaan.addPenyentuh(BalaiHukum::sentuh);
		ServerTickEvents.END_SERVER_TICK.register(BalaiHukum::tick);
		ServerPlayerEvents.JOIN.register(BalaiHukum::masuk);
		ServerPlayerEvents.LEAVE.register(player -> KURUNGAN.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> KURUNGAN.clear());
		GabungCommands.daftar("hukum", BalaiHukum::perintah);
		GabungCommands.daftar("tebus", () -> Commands.literal("tebus").executes(ctx -> tebus(ctx.getSource().getPlayerOrException()) ? 1 : 0));
		GabungUji.daftar("langgar", BalaiHukum::ujiLanggar);
		GabungUji.daftar("kurung", () -> Commands.literal("kurung")
				.then(Commands.argument("detik", IntegerArgumentType.integer(5, Aturan.KURUNGAN_MAKS_DETIK))
						.executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							if (GabungApi.sekte(p.getUUID()) == null) {
								ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.uji.bukan_anggota"));
								return 0;
							}
							kurung(p, IntegerArgumentType.getInteger(ctx, "detik"));
							return 1;
						})));
		GabungUji.daftar("sidang", () -> Commands.literal("sidang").executes(ctx -> {
			ServerPlayer p = ctx.getSource().getPlayerOrException();
			Keanggotaan k = GabungApi.anggota(p);
			if (!k.status.anggota()) {
				ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.uji.bukan_anggota"));
				return 0;
			}
			CompoundTag h = k.bagian("hukum");
			int tk = Math.max(TK_DENDA, tingkat(k.pelanggaran));
			h.putInt("sidang", tk);
			h.putLong("batas", GabungApi.hari(ctx.getSource().getServer()) + BATAS_HARI);
			h.putBoolean("bela", false);
			h.putBoolean("dibela", false);
			GabungData.get(ctx.getSource().getServer()).setDirty();
			kartuSidang(p);
			return 1;
		}));
	}

	// ================================================================== levels

	/** The sentence level of a record value. */
	public static int tingkat(int catatan) {
		if (catatan >= Aturan.USIR) {
			return TK_USIR;
		}
		if (catatan >= Aturan.TURUN) {
			return TK_TURUN;
		}
		if (catatan >= Aturan.KURUNGAN) {
			return TK_KURUNGAN;
		}
		if (catatan >= Aturan.DENDA) {
			return TK_DENDA;
		}
		return catatan >= Aturan.TEGURAN ? TK_TEGURAN : TK_BERSIH;
	}

	/** The fine of 41.6.11: {@code 20 x class} PK. */
	public static int denda(String sekte) {
		return 20 * GabungApi.kelas(sekte);
	}

	/** Confinement seconds for a record value ({@code 4 x points}, at most 20 minutes). */
	public static int detikKurungan(int catatan) {
		return Math.min(Aturan.KURUNGAN_MAKS_DETIK, Math.max(Aturan.DETIK_PER_POIN * 5, Aturan.DETIK_PER_POIN * catatan));
	}

	/** The sentence line of level {@code tk} for {@code k}. */
	public static Component hukuman(Keanggotaan k, int tk) {
		return switch (tk) {
			case TK_USIR -> Component.translatable("kultivator.gabung.hukum.vonis.usir");
			case TK_TURUN -> Component.translatable("kultivator.gabung.hukum.vonis.turun", k.pangkat.turun().nama(),
					detikKurungan(k.pelanggaran) / 60);
			case TK_KURUNGAN -> Component.translatable("kultivator.gabung.hukum.vonis.kurungan", Math.max(1, detikKurungan(k.pelanggaran) / 60));
			case TK_DENDA -> Component.translatable("kultivator.gabung.hukum.vonis.denda", denda(k.sekte));
			case TK_TEGURAN -> Component.translatable("kultivator.gabung.hukum.vonis.teguran");
			default -> Component.translatable("kultivator.gabung.hukum.vonis.bebas");
		};
	}

	// ================================================================== the enforcer (GabungApi.langgar)

	private static void langgar(ServerPlayer player, Keanggotaan k, Aturan aturan, BlockPos at, int sebelum, int sesudah) {
		MinecraftServer server = player.level().getServer();
		long hari = GabungApi.hari(server);
		CompoundTag h = k.bagian("hukum");
		h.putString("aturan", aturan.id());
		h.putLong("hari", hari);
		if (!h.contains("saksi")) {
			h.putString("saksi", "");
		}
		int aliran = SekteApi.diplomasi().aliran(k.sekte);
		Msg.chat(player, Component.translatable("kultivator.gabung.hukum.tercatat", aturan.id().toUpperCase(java.util.Locale.ROOT),
				aturan.teks(aliran), aturan.poin(), sesudah).withStyle(ChatFormatting.RED));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
		if (aturan == Aturan.A2) {
			h.putLong("a2Batas", hari + BATAS_A2_HARI);
			GabungData.get(server).setDirty();
			kartuA2(player, k);
			return;
		}
		int lama = tingkat(sebelum);
		int baru = tingkat(sesudah);
		if (baru > lama && baru > h.getIntOr("tingkat", TK_BERSIH)) {
			if (baru == TK_TEGURAN) {
				h.putBoolean("teguran", true);
				if (Pemeriksa.diWilayahSendiri(player)) {
					teguran(player, k);
				}
			} else {
				h.putInt("sidang", baru);
				h.putLong("batas", hari + BATAS_HARI);
				h.putBoolean("bela", false);
				h.putBoolean("dibela", false);
				h.remove("soal");
				kartuPanggilan(player, k, baru);
			}
		}
		GabungData.get(server).setDirty();
	}

	/** The member walked into the own grounds: the reprimand waiting for him, and a reminder of an open trial. */
	static void tibaDiWilayah(ServerPlayer player, Keanggotaan k) {
		CompoundTag h = k.bagian("hukum");
		if (h.getBooleanOr("teguran", false)) {
			teguran(player, k);
		}
		if (h.getIntOr("sidang", 0) > 0 || h.getLongOr("a2Batas", 0L) > 0L) {
			Msg.chat(player, Component.translatable("kultivator.gabung.hukum.ingat", namaTetuaHukum(player.level().getServer(), k.sekte))
					.withStyle(ChatFormatting.GOLD));
		}
	}

	private static void teguran(ServerPlayer player, Keanggotaan k) {
		MinecraftServer server = player.level().getServer();
		k.bagian("hukum").putBoolean("teguran", false);
		k.bagian("hukum").putInt("tingkat", Math.max(TK_TEGURAN, k.bagian("hukum").getIntOr("tingkat", 0)));
		GabungData.get(server).setDirty();
		Kartu.kirim(player, Component.translatable("kultivator.gabung.hukum.teguran.judul", namaTetuaHukum(server, k.sekte)),
				List.of(Component.translatable("kultivator.gabung.hukum.teguran." + ragam(player, k.sekte, 3)),
						Component.translatable("kultivator.gabung.hukum.catatan", k.pelanggaran, Aturan.DENDA)), List.of());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.6F, 0.7F);
	}

	// ================================================================== lazy upkeep (decay, Lalai, deadlines)

	private static void sentuh(ServerPlayer player, Keanggotaan k, long hari) {
		if (!k.status.anggota()) {
			return;
		}
		// decay: 1 point per SUSUT_HARI world days
		if (k.hariSusut < 0L) {
			k.hariSusut = hari;
		} else if (hari - k.hariSusut >= Aturan.SUSUT_HARI) {
			long langkah = (hari - k.hariSusut) / Aturan.SUSUT_HARI;
			k.pelanggaran = (int) Math.max(0L, k.pelanggaran - langkah);
			k.hariSusut += langkah * Aturan.SUSUT_HARI;
		}
		CompoundTag h = k.bagian("hukum");
		if (h.getIntOr("tingkat", 0) > tingkat(k.pelanggaran)) {
			// the record fell below a threshold: crossing it again opens a new case
			h.putInt("tingkat", tingkat(k.pelanggaran));
		}
		// A9 Lalai: gone 30 world days from the grounds without leave
		if (k.pangkat != Pangkat.TAMU && k.pangkat != Pangkat.KETUA && Pemeriksa.hariAbsen(k, hari) > LALAI_HARI) {
			Pangkat turun = k.pangkat.turun();
			k.bagian("aturan").putLong("hadir", hari);
			k.catat(hari, "kultivator.gabung.riwayat.lalai", k.pangkat.id());
			if (turun != k.pangkat) {
				GabungApi.setPangkat(player, turun, "gabung.lalai");
			}
			Msg.chat(player, Component.translatable("kultivator.gabung.hukum.lalai", LALAI_HARI, turun.nama()).withStyle(ChatFormatting.RED));
		}
		// an unanswered A2 summons: he fled the trial
		long a2 = h.getLongOr("a2Batas", 0L);
		if (a2 > 0L && hari > a2) {
			h.remove("a2Batas");
			Keluar.belot(player, "kultivator.gabung.keluar.sebab.lari_sidang");
			return;
		}
		// an unanswered summons is carried out (unless another event holds the member: asked again next time)
		long batas = h.getLongOr("batas", 0L);
		if (h.getIntOr("sidang", 0) > 0 && batas > 0L && hari > batas && Sibuk.why(player) == null) {
			Msg.chat(player, Component.translatable("kultivator.gabung.hukum.in_absentia", namaTetuaHukum(player.level().getServer(), k.sekte))
					.withStyle(ChatFormatting.RED));
			jatuhkan(player, k);
		}
	}

	// ================================================================== trial

	/** The trial of {@code player} as the screen shows it, or null when no case is open. */
	public static @Nullable Sidang sidang(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		if (!k.status.anggota()) {
			return null;
		}
		CompoundTag h = k.bagian("hukum");
		boolean a2 = h.getLongOr("a2Batas", 0L) > 0L;
		int tk = h.getIntOr("sidang", 0);
		if (!a2 && tk <= 0) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		int vonis = a2 ? TK_USIR : Math.max(TK_DENDA, tingkat(k.pelanggaran));
		TokohSekte.Tokoh shifu = k.shifu.isEmpty() ? null : TokohSekte.tokoh(server, k.shifu);
		boolean bolehShifu = !a2 && shifu != null && !h.getBooleanOr("dibela", false) && k.ikatanShifu >= SHIFU_IKATAN_MIN;
		return new Sidang(k.sekte, namaTetuaHukum(server, k.sekte), k.pelanggaran, vonis,
				a2 ? Component.translatable("kultivator.gabung.hukum.vonis.a2") : hukuman(k, vonis), h.getStringOr("aturan", ""),
				h.getStringOr("saksi", ""), a2, !a2 && !h.getBooleanOr("bela", false), bolehShifu,
				shifu == null ? null : Component.literal(shifu.nama()), k.ikatanShifu);
	}

	/** [Terima hukuman]: the sentence of the current record is carried out (A2: expulsion). False when nothing is open. */
	public static boolean terimaHukuman(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		Sidang s = sidang(player);
		if (s == null) {
			Msg.chat(player, Component.translatable("kultivator.gabung.hukum.tidak_ada").withStyle(ChatFormatting.GRAY));
			return false;
		}
		String busy = Sibuk.whyExcept(player, Gabung.SIBUK_KURUNGAN);
		if (busy != null) {
			Msg.chat(player, Sibuk.text(busy));
			return false;
		}
		if (s.a2()) {
			k.bagian("hukum").remove("a2Batas");
			GabungData.get(player.level().getServer()).setDirty();
			Keluar.usir(player, "kultivator.gabung.keluar.sebab.a2");
			return true;
		}
		jatuhkan(player, k);
		return true;
	}

	/** [Lari] from an A2 trial: desertion. */
	public static boolean lari(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		if (!k.status.anggota() || k.bagian("hukum").getLongOr("a2Batas", 0L) <= 0L) {
			return false;
		}
		k.bagian("hukum").remove("a2Batas");
		GabungData.get(player.level().getServer()).setDirty();
		Keluar.belot(player, "kultivator.gabung.keluar.sebab.lari_sidang");
		return true;
	}

	/** [Bela diri]: the question of the Ujian Hati for this case (the same until answered), or null when not allowed. */
	public static @Nullable Soal belaDiri(ServerPlayer player) {
		Sidang s = sidang(player);
		if (s == null || !s.bolehBela()) {
			return null;
		}
		Keanggotaan k = GabungApi.anggota(player);
		CompoundTag h = k.bagian("hukum");
		String aturan = s.aturan().isEmpty() ? "a1" : s.aturan();
		if (aturan.equals("a9") || aturan.equals("a4") || aturan.equals("a2")) {
			aturan = "a1";
		}
		int aliran = SekteApi.diplomasi().aliran(k.sekte);
		String dasar = "kultivator.gabung.hukum.soal." + aturan + (aliran == 2 && Aturan.byId(aturan) != null && Aturan.byId(aturan).iblisLonggar() ? ".iblis" : "");
		// the right answer is "<dasar>.0"; the shown order is seeded per case so the place of the right one changes
		int geser = Math.floorMod(player.getUUID().hashCode() ^ (int) h.getLongOr("hari", 0L) ^ k.pelanggaran, 3);
		List<String> jawaban = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			jawaban.add(dasar + "." + Math.floorMod(i - geser, 3));
		}
		int benar = geser;
		h.putString("soal", dasar);
		h.putInt("benar", benar);
		GabungData.get(player.level().getServer()).setDirty();
		return new Soal(dasar, List.copyOf(jawaban), benar);
	}

	/** An answer to the defence question; true when right (record -10). Either way the defence is used up. */
	public static boolean jawab(ServerPlayer player, int pilihan) {
		Keanggotaan k = GabungApi.anggota(player);
		CompoundTag h = k.bagian("hukum");
		if (!k.status.anggota() || h.getStringOr("soal", "").isEmpty() || h.getBooleanOr("bela", false)) {
			return false;
		}
		boolean benar = pilihan == h.getIntOr("benar", -1);
		h.putBoolean("bela", true);
		h.remove("soal");
		if (benar) {
			k.pelanggaran = Math.max(0, k.pelanggaran - BELA_POIN);
			Msg.chat(player, Component.translatable("kultivator.gabung.hukum.bela.benar", BELA_POIN, k.pelanggaran).withStyle(ChatFormatting.GREEN));
		} else {
			Msg.chat(player, Component.translatable("kultivator.gabung.hukum.bela.salah").withStyle(ChatFormatting.RED));
		}
		GabungData.get(player.level().getServer()).setDirty();
		return benar;
	}

	/** [Minta Shifu membela]: bond >= 50, record -15, bond -10; false when not allowed. */
	public static boolean mintaShifu(ServerPlayer player) {
		Sidang s = sidang(player);
		if (s == null || !s.bolehShifu()) {
			Msg.chat(player, Component.translatable("kultivator.gabung.hukum.shifu.tidak", SHIFU_IKATAN_MIN).withStyle(ChatFormatting.GRAY));
			return false;
		}
		Keanggotaan k = GabungApi.anggota(player);
		k.bagian("hukum").putBoolean("dibela", true);
		k.pelanggaran = Math.max(0, k.pelanggaran - SHIFU_POIN);
		k.ikatanShifu = Math.max(0, k.ikatanShifu - SHIFU_IKATAN_BIAYA);
		GabungData.get(player.level().getServer()).setDirty();
		Msg.chat(player, Component.translatable("kultivator.gabung.hukum.shifu.bela", s.shifu(), SHIFU_POIN, k.pelanggaran)
				.withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** Carries out the sentence of the current record and closes the case. */
	private static void jatuhkan(ServerPlayer player, Keanggotaan k) {
		MinecraftServer server = player.level().getServer();
		CompoundTag h = k.bagian("hukum");
		int tk = tingkat(k.pelanggaran);
		h.remove("sidang");
		h.remove("batas");
		h.remove("soal");
		h.putInt("tingkat", Math.max(h.getIntOr("tingkat", 0), tk));
		long hari = GabungApi.hari(server);
		k.catat(hari, "kultivator.gabung.riwayat.vonis", Integer.toString(tk), Integer.toString(k.pelanggaran));
		GabungData.get(server).setDirty();
		switch (tk) {
			case TK_USIR -> Keluar.usir(player, "kultivator.gabung.keluar.sebab.catatan");
			case TK_TURUN -> {
				Pangkat turun = k.pangkat.turun();
				if (turun != k.pangkat) {
					GabungApi.setPangkat(player, turun, "gabung.hukum");
				}
				kurung(player, detikKurungan(k.pelanggaran));
			}
			case TK_KURUNGAN -> kurung(player, detikKurungan(k.pelanggaran));
			case TK_DENDA -> {
				int denda = Math.min(k.pk, denda(k.sekte));
				if (denda > 0) {
					GabungApi.bayarPk(player, denda, "gabung.denda");
				}
				Msg.chat(player, Component.translatable("kultivator.gabung.hukum.didenda", denda).withStyle(ChatFormatting.GOLD));
			}
			default -> Msg.chat(player, Component.translatable("kultivator.gabung.hukum.dibebaskan").withStyle(ChatFormatting.GREEN));
		}
	}

	// ================================================================== confinement (Gua Renungan)

	/** Confines the member for {@code detik} real seconds in the Gua Renungan of his sect (41.6.11). */
	public static void kurung(ServerPlayer player, int detik) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || detik <= 0) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		BlockPos spot = GabungApi.titik(sekte, TitikGabung.GUA_RENUNGAN);
		ServerLevel level = server.overworld();
		if (spot == null || KompleksSpot.gerbangSaja(sekte, spot)) {
			BlockPos meja = GabungApi.titik(sekte, TitikGabung.HUKUM);
			spot = meja != null ? meja : spot;
		}
		if (spot == null) {
			spot = player.blockPosition();
			level = player.level();
		}
		long now = player.level().getGameTime();
		k.kurunganSampai = now + detik * 20L;
		CompoundTag h = k.bagian("hukum");
		h.putIntArray("kurunganPos", new int[]{spot.getX(), spot.getY(), spot.getZ()});
		h.putString("kurunganDim", level.dimension().identifier().toString());
		h.putInt("kabur", 0);
		GabungData.get(server).setDirty();
		KURUNGAN.put(player.getUUID(), new Kurungan(level.dimension(), spot.immutable()));
		bawaKe(player, level, spot);
		GabungPayloads.kirimKlip(player, GabungGerakId.RENUNGAN, 0);
		Kartu.kirim(player, Component.translatable("kultivator.gabung.hukum.kurungan.judul"),
				List.of(Component.translatable("kultivator.gabung.hukum.kurungan.isi", Math.max(1, detik / 60), detik % 60),
						Component.translatable("kultivator.gabung.hukum.kurungan.tebus", TEBUS_PK, TEBUS_DETIK, biayaTebus(detik))),
				List.of(Kartu.chip(Component.translatable("kultivator.gabung.hukum.tombol.tebus"), "/kultivasi gabung tebus",
						Component.translatable("kultivator.gabung.hukum.tombol.tebus.hover", biayaTebus(detik)), ChatFormatting.AQUA)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	/** Remaining confinement seconds (0 when free). */
	public static int sisaDetik(ServerPlayer player) {
		Keanggotaan k = GabungData.get(player.level().getServer()).peek(player.getUUID());
		long now = player.level().getGameTime();
		return k == null || k.kurunganSampai <= now ? 0 : (int) ((k.kurunganSampai - now + 19L) / 20L);
	}

	public static int biayaTebus(int detik) {
		return (detik + TEBUS_DETIK - 1) / TEBUS_DETIK * TEBUS_PK;
	}

	/** Buys off the rest of the confinement; false when not confined or short of PK. */
	public static boolean tebus(ServerPlayer player) {
		int sisa = sisaDetik(player);
		if (sisa <= 0) {
			Msg.chat(player, Component.translatable("kultivator.gabung.hukum.tebus.bebas").withStyle(ChatFormatting.GRAY));
			return false;
		}
		int biaya = biayaTebus(sisa);
		if (!GabungApi.bayarPk(player, biaya, "gabung.tebus")) {
			Msg.chat(player, Component.translatable("kultivator.gabung.hukum.tebus.kurang", biaya).withStyle(ChatFormatting.RED));
			return false;
		}
		lepas(player, "kultivator.gabung.hukum.tebus.lunas");
		return true;
	}

	/** Ends a confinement silently (the member left the sect: desertion, expulsion). */
	public static void lepasKurungan(ServerPlayer player) {
		KURUNGAN.remove(player.getUUID());
		GabungPayloads.kirimKlip(player, 0, 0); // the RENUNGAN loop does not end by itself
		Keanggotaan k = GabungData.get(player.level().getServer()).peek(player.getUUID());
		if (k != null && k.kurunganSampai > 0L) {
			k.kurunganSampai = 0L;
			GabungData.get(player.level().getServer()).setDirty();
		}
	}

	private static void lepas(ServerPlayer player, String kunci) {
		KURUNGAN.remove(player.getUUID());
		GabungPayloads.kirimKlip(player, 0, 0);
		Keanggotaan k = GabungData.get(player.level().getServer()).peek(player.getUUID());
		if (k != null) {
			k.kurunganSampai = 0L;
			k.bagian("hukum").remove("kurunganPos");
			k.bagian("hukum").remove("kurunganDim");
			GabungData.get(player.level().getServer()).setDirty();
		}
		Msg.chat(player, Component.translatable(kunci).withStyle(ChatFormatting.GREEN));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0F, 0.9F);
	}

	private static void masuk(ServerPlayer player) {
		if (!Gabung.aktif()) {
			return;
		}
		Keanggotaan k = GabungData.get(player.level().getServer()).peek(player.getUUID());
		if (k == null || k.kurunganSampai <= player.level().getGameTime()) {
			return;
		}
		CompoundTag h = k.bagian("hukum");
		int[] p = h.getIntArray("kurunganPos").orElse(null);
		Identifier dim = Identifier.tryParse(h.getStringOr("kurunganDim", ""));
		if (p != null && p.length == 3 && dim != null) {
			KURUNGAN.put(player.getUUID(), new Kurungan(ResourceKey.create(Registries.DIMENSION, dim), new BlockPos(p[0], p[1], p[2])));
		}
	}

	private static void tick(MinecraftServer server) {
		if (KURUNGAN.isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		for (Map.Entry<UUID, Kurungan> e : List.copyOf(KURUNGAN.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				KURUNGAN.remove(e.getKey());
				continue;
			}
			Keanggotaan k = GabungData.get(server).peek(player.getUUID());
			if (k == null || !k.status.anggota() || k.kurunganSampai <= 0L) {
				KURUNGAN.remove(e.getKey());
				continue;
			}
			if (player.level().getGameTime() >= k.kurunganSampai) {
				lepas(player, "kultivator.gabung.hukum.kurungan.selesai");
				continue;
			}
			Kurungan kur = e.getValue();
			ServerLevel level = server.getLevel(kur.dim());
			if (level == null) {
				KURUNGAN.remove(e.getKey());
				continue;
			}
			boolean keluar = player.level() != level || player.blockPosition().distSqr(kur.pos()) > KURUNGAN_RADIUS * KURUNGAN_RADIUS;
			if (keluar) {
				CompoundTag h = k.bagian("hukum");
				int kabur = h.getIntOr("kabur", 0) + 1;
				h.putInt("kabur", kabur);
				GabungData.get(server).setDirty();
				if (kabur > KABUR_MAKS) {
					KURUNGAN.remove(e.getKey());
					k.kurunganSampai = 0L;
					Keluar.belot(player, "kultivator.gabung.keluar.sebab.kabur");
					continue;
				}
				bawaKe(player, level, kur.pos());
				Msg.chat(player, Component.translatable("kultivator.gabung.hukum.kurungan.ditarik", kabur, KABUR_MAKS).withStyle(ChatFormatting.DARK_RED));
			}
			if (server.getTickCount() % 40 == 0) {
				segel(level, kur.pos());
			}
		}
	}

	/** The formation seal of the cave: a ring of glyph particles at the edge of what it allows (no block). */
	private static void segel(ServerLevel level, BlockPos pos) {
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI / 8.0D;
			double x = pos.getX() + 0.5D + Math.cos(a) * (KURUNGAN_RADIUS - 0.5D);
			double z = pos.getZ() + 0.5D + Math.sin(a) * (KURUNGAN_RADIUS - 0.5D);
			level.sendParticles(ParticleTypes.ENCHANT, x, pos.getY() + 1.0D, z, 2, 0.05D, 0.4D, 0.05D, 0.0D);
		}
	}

	private static void bawaKe(ServerPlayer player, ServerLevel level, BlockPos pos) {
		double x = pos.getX() + 0.5D;
		double y = pos.getY();
		double z = pos.getZ() + 0.5D;
		if (player.level() == level) {
			player.teleportTo(x, y, z);
		} else {
			player.teleportTo(level, x, y, z, Set.of(), player.getYRot(), player.getXRot(), false);
		}
		player.resetFallDistance();
	}

	// ================================================================== chat cards and commands

	private static void kartuPanggilan(ServerPlayer player, Keanggotaan k, int tk) {
		MinecraftServer server = player.level().getServer();
		Kartu.kirim(player, Component.translatable("kultivator.gabung.hukum.panggilan.judul", Pemeriksa.nama(k.sekte)),
				List.of(Component.translatable("kultivator.gabung.hukum.panggilan.isi", namaTetuaHukum(server, k.sekte), BATAS_HARI),
						Component.translatable("kultivator.gabung.hukum.vonis", hukuman(k, tk))),
				List.of(Kartu.chip(Component.translatable("kultivator.gabung.hukum.tombol.sidang"), "/kultivasi gabung hukum",
						Component.translatable("kultivator.gabung.hukum.tombol.sidang.hover"), ChatFormatting.GOLD)));
	}

	private static void kartuA2(ServerPlayer player, Keanggotaan k) {
		MinecraftServer server = player.level().getServer();
		Kartu.kirim(player, Component.translatable("kultivator.gabung.hukum.a2.judul", Pemeriksa.nama(k.sekte)),
				List.of(Component.translatable("kultivator.gabung.hukum.a2.isi", namaTetuaHukum(server, k.sekte)),
						Component.translatable("kultivator.gabung.hukum.a2.pilihan")),
				List.of(Kartu.chip(Component.translatable("kultivator.gabung.hukum.tombol.hadir"), "/kultivasi gabung hukum terima",
								Component.translatable("kultivator.gabung.hukum.tombol.hadir.hover"), ChatFormatting.GOLD),
						Kartu.chip(Component.translatable("kultivator.gabung.hukum.tombol.lari"), "/kultivasi gabung hukum lari",
								Component.translatable("kultivator.gabung.hukum.tombol.lari.hover"), ChatFormatting.DARK_RED)));
	}

	/** The trial card in chat (the same record and actions as 41U's {@code gabung_hukum} screen). */
	public static void kartuSidang(ServerPlayer player) {
		Sidang s = sidang(player);
		if (s == null) {
			Msg.chat(player, Component.translatable("kultivator.gabung.hukum.tidak_ada").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (s.a2()) {
			kartuA2(player, GabungApi.anggota(player));
			return;
		}
		List<Component> baris = new ArrayList<>();
		baris.add(Component.translatable("kultivator.gabung.hukum.sidang.ketua", s.tetuaHukum()));
		baris.add(Component.translatable("kultivator.gabung.hukum.catatan", s.catatan(), Aturan.USIR));
		Aturan a = Aturan.byId(s.aturan());
		if (a != null) {
			baris.add(Component.translatable("kultivator.gabung.hukum.sidang.perkara", a.id().toUpperCase(java.util.Locale.ROOT),
					a.teks(SekteApi.diplomasi().aliran(s.sekte()))));
		}
		baris.add(s.saksi().isEmpty() ? Component.translatable("kultivator.gabung.hukum.sidang.tanpa_saksi")
				: Component.translatable("kultivator.gabung.hukum.sidang.saksi", s.saksi()));
		baris.add(Component.translatable("kultivator.gabung.hukum.vonis", s.hukuman()));
		List<Component> chips = new ArrayList<>();
		chips.add(Kartu.chip(Component.translatable("kultivator.gabung.hukum.tombol.terima"), "/kultivasi gabung hukum terima",
				Component.translatable("kultivator.gabung.hukum.tombol.terima.hover"), ChatFormatting.GOLD));
		if (s.bolehBela()) {
			chips.add(Kartu.chip(Component.translatable("kultivator.gabung.hukum.tombol.bela"), "/kultivasi gabung hukum bela",
					Component.translatable("kultivator.gabung.hukum.tombol.bela.hover", BELA_POIN), ChatFormatting.AQUA));
		}
		if (s.bolehShifu()) {
			chips.add(Kartu.chip(Component.translatable("kultivator.gabung.hukum.tombol.shifu"), "/kultivasi gabung hukum shifu",
					Component.translatable("kultivator.gabung.hukum.tombol.shifu.hover", s.shifu(), SHIFU_POIN, SHIFU_IKATAN_BIAYA),
					ChatFormatting.GREEN));
		}
		Kartu.kirim(player, Component.translatable("kultivator.gabung.hukum.sidang.judul", Pemeriksa.nama(s.sekte())), baris, chips);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> perintah() {
		return Commands.literal("hukum")
				.executes(ctx -> {
					kartuSidang(ctx.getSource().getPlayerOrException());
					return 1;
				})
				.then(Commands.literal("terima").executes(ctx -> terimaHukuman(ctx.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("lari").executes(ctx -> lari(ctx.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("shifu").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					boolean ok = mintaShifu(p);
					if (ok) {
						kartuSidang(p);
					}
					return ok ? 1 : 0;
				}))
				.then(Commands.literal("bela").executes(BalaiHukum::kartuSoal))
				.then(Commands.literal("jawab").then(Commands.argument("n", IntegerArgumentType.integer(0, 2)).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					boolean ok = jawab(p, IntegerArgumentType.getInteger(ctx, "n"));
					kartuSidang(p);
					return ok ? 1 : 0;
				})));
	}

	private static int kartuSoal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Soal soal = belaDiri(player);
		if (soal == null) {
			Msg.chat(player, Component.translatable("kultivator.gabung.hukum.bela.habis").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		List<Component> chips = new ArrayList<>();
		for (int i = 0; i < soal.jawaban().size(); i++) {
			chips.add(Kartu.chip(Component.literal(String.valueOf((char) ('A' + i))), "/kultivasi gabung hukum jawab " + i,
					Component.translatable(soal.jawaban().get(i)), ChatFormatting.AQUA));
		}
		List<Component> baris = new ArrayList<>();
		baris.add(Component.translatable(soal.kunci()));
		for (int i = 0; i < soal.jawaban().size(); i++) {
			baris.add(Component.literal((char) ('A' + i) + ". ").append(Component.translatable(soal.jawaban().get(i))));
		}
		Kartu.kirim(player, Component.translatable("kultivator.gabung.hukum.bela.judul"), baris, chips);
		return 1;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> ujiLanggar() {
		LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("langgar");
		for (Aturan a : Aturan.values()) {
			node.then(Commands.literal(a.id()).executes(ctx -> {
				ServerPlayer p = ctx.getSource().getPlayerOrException();
				if (GabungApi.sekte(p.getUUID()) == null) {
					ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.uji.bukan_anggota"));
					return 0;
				}
				if (a == Aturan.A9) {
					// A9 has no points: pretend the member has been away for the whole window
					Keanggotaan k = GabungApi.anggota(p);
					k.bagian("aturan").putLong("hadir", GabungApi.hari(ctx.getSource().getServer()) - LALAI_HARI - 1L);
					k.hariSentuh = -1L;
					if (k.sentuh(p, GabungApi.hari(ctx.getSource().getServer()))) {
						GabungData.get(ctx.getSource().getServer()).setDirty();
					}
					return 1;
				}
				GabungApi.langgar(p, a, p.blockPosition(), false);
				return 1;
			}));
		}
		return node;
	}

	// ================================================================== names and small helpers

	/** "Tetua Hukum Qin Zhenyuan" of the sect, or the generic title before 41W names the elders. */
	public static Component namaTetuaHukum(MinecraftServer server, String sekte) {
		TokohSekte.Tokoh t = server == null || sekte == null ? null : TokohSekte.jabatan(server, sekte, Jabatan.TETUA_HUKUM);
		return t == null ? Component.translatable("kultivator.gabung.hukum.tetua_umum")
				: Component.translatable("kultivator.gabung.hukum.tetua_nama", t.nama());
	}

	/** A seeded variant 0..n-1 per member and sect (so the elder's lines differ between players). */
	private static int ragam(ServerPlayer player, String sekte, int n) {
		return Math.floorMod(player.getUUID().hashCode() * 31 + (sekte == null ? 0 : sekte.hashCode()) + (int) (player.level().getGameTime() / 24000L), n);
	}

	/** Whether a resolved spot is only the gate stand-in (no compound cave): then the Law Hall table is tried first. */
	private static final class KompleksSpot {
		static boolean gerbangSaja(String sekte, BlockPos spot) {
			BlockPos gate = SekteApi.diplomasi().gate(sekte);
			BlockPos planned = SekteApi.diplomasi().plannedGate(sekte);
			return spot.equals(gate) || spot.equals(planned);
		}
	}

	private BalaiHukum() {
	}
}
