package dev.kultivator.sekte.gabung;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.tempat.kompleks.Kartu;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.api.SyaratMasuk;
import dev.kultivator.sekte.gabung.politik.Kursi;
import dev.kultivator.sekte.gabung.ui.GabungLayar;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The rank tree of a player member (DESIGN 41.6.1): what the next rank needs, and the promotion itself. One source for the
 * Pohon Pangkat screen (41U {@code LembarPangkat}), the {@code /kultivasi gabung naik} command and its button.
 * <p>
 * Requirements of rank R (from the rank below): the realm of {@code Pangkat.syaratRanah} (Tercatat: Pemurnian Qi
 * <i>or</i> {@link #PK_TUGAS_TERCATAT} PK earned from chores), the days ({@code Pangkat.hariMin}: since the last rank;
 * Pewaris and Tetua count the membership, Wakil the days as elder), the PK cost ({@code base x kf x skalaBiaya}), a free
 * seat ({@code Kursi.pemegang} under {@code Pangkat.kursi}), and the rank's trial, which the module running it marks with
 * {@link #lulus}: Luar the "Uji Ulang" (41B), Dalam the Inner Disciple Trial, Inti a tournament top 3 or an A mission
 * (41C), Pewaris the heir election and Wakil the elders' support (41D), Tetua the Elder Trial. Ketua comes only from the
 * succession (41D) and Tamu only from the guest door, never from here. Other modules may add lines with
 * {@link #addSyarat}.
 * <p>
 * The promotion: [Ajukan Kenaikan] inside the own grounds pays the PK, then a 10 s ceremony (the sect bow, a flash of the
 * new trim colour, the resonating bell) and {@code GabungApi.setPangkat} (advancement {@code sekte/gabung_pangkat_<id>},
 * lencana sync, {@code PANGKAT_NAIK}).
 */
public final class Promosi {

	/** Chore PK that replace the realm for Tercatat (41.6.1). */
	public static final int PK_TUGAS_TERCATAT = 50;
	/** Length of the promotion ceremony (ticks). */
	public static final int UPACARA_TICK = 200;

	/** Extra requirement lines a module adds for a target rank (empty when none). */
	@FunctionalInterface
	public interface Syarat {
		List<SyaratMasuk.Baris> syarat(ServerPlayer player, Keanggotaan k, Pangkat tujuan);
	}

	private record Upacara(Pangkat tujuan, long mulai, int biaya) {
	}

	private static final List<Syarat> SYARAT = new CopyOnWriteArrayList<>();
	private static final Map<UUID, Upacara> UPACARA = new HashMap<>();
	private static boolean registered;

	/** From {@code Gabung.init} (module on). */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		GabungBus.PK_BERUBAH.register((player, sekte, lama, baru, sebab) -> {
			// 41.6.1 Tercatat: PK earned from chores ("gabung.tugas*") count for the realm requirement
			if (baru > lama && sebab != null && sebab.startsWith("gabung.tugas")) {
				Keanggotaan k = GabungApi.anggota(player);
				CompoundTag p = k.bagian("pangkat");
				p.putInt("pkTugas", p.getIntOr("pkTugas", 0) + (baru - lama));
				GabungData.get(player.level().getServer()).setDirty();
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Promosi::tick);
		ServerPlayerEvents.LEAVE.register(player -> batalkan(player, true));
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> UPACARA.clear());
		// The rank tree's [Ajukan Kenaikan]: the ceremony, the own-grounds check and the trials start here, not the 41U default.
		GabungLayar.daftarAksi(GabungLembar.PohonPangkat.LAYAR, GabungLembar.PohonPangkat.OP_AJUKAN,
				(player, npcId, op) -> ajukan(player, Pangkat.byId(LembarKunci.arg(op, 1)) == Pangkat.TETUA));
		GabungCommands.daftar("naik", () -> Commands.literal("naik")
				.executes(ctx -> ajukan(ctx.getSource().getPlayerOrException()) ? 1 : 0)
				.then(Commands.literal("tetua").executes(ctx -> ajukan(ctx.getSource().getPlayerOrException(), true) ? 1 : 0)));
	}

	/** Adds requirement lines (41D seats/trials, 41C tournament, ...). */
	public static void addSyarat(Syarat syarat) {
		if (syarat != null) {
			SYARAT.add(syarat);
		}
	}

	/** Marks the trial of rank {@code tujuan} as passed for the member (the module that runs the trial calls this). */
	public static void lulus(ServerPlayer player, Pangkat tujuan) {
		Keanggotaan k = GabungApi.anggota(player);
		if (!k.status.anggota() || tujuan == null) {
			return;
		}
		k.bagian("pangkat").putBoolean("lulus." + tujuan.id(), true);
		GabungData.get(player.level().getServer()).setDirty();
		Msg.chat(player, Component.translatable("kultivator.gabung.naik.lulus_ujian", tujuan.nama()).withStyle(ChatFormatting.GREEN));
	}

	/** True when the trial of {@code tujuan} was passed (or the rank has none). */
	public static boolean sudahLulus(Keanggotaan k, Pangkat tujuan) {
		return ujian(tujuan) == null || k.bagian("pangkat").getBooleanOr("lulus." + tujuan.id(), false);
	}

	/** True when {@link #ajukan} itself starts the trial of {@code tujuan} (the Luar re-test, the Dalam duel, the Tetua trial). */
	public static boolean mulaiUjian(Pangkat tujuan) {
		return tujuan == Pangkat.LUAR || UjianPangkat.punya(tujuan);
	}

	/** The trial name key of rank {@code tujuan}, or null when it has none. */
	public static @Nullable String ujian(Pangkat tujuan) {
		return switch (tujuan) {
			case LUAR, DALAM, INTI, PEWARIS, TETUA, WAKIL -> "kultivator.gabung.naik.ujian." + tujuan.id();
			default -> null;
		};
	}

	/** The next rank on the member's path (Inti forks: {@code jalurTetua}), or null at the top / for Tamu and Ketua. */
	public static @Nullable Pangkat berikut(Keanggotaan k, boolean jalurTetua) {
		Pangkat next = k.pangkat.berikut(jalurTetua);
		return next == Pangkat.KETUA ? null : next;
	}

	/** Every requirement of rising to {@code tujuan}, in screen order (lang {@code kultivator.gabung.syarat.naik.*}). */
	public static List<SyaratMasuk.Baris> syarat(ServerPlayer player, Pangkat tujuan) {
		List<SyaratMasuk.Baris> out = new ArrayList<>();
		Keanggotaan k = GabungApi.anggota(player);
		MinecraftServer server = player.level().getServer();
		if (!k.status.anggota() || tujuan == null) {
			out.add(new SyaratMasuk.Baris(false, "naik.anggota", new Object[0]));
			return out;
		}
		long hari = GabungApi.hari(server);
		int tier = Math.max(1, GabungApi.tier(k.sekte));
		KelasSekte kelas = GabungApi.kelasSekte(k.sekte);
		// realm
		Realm perlu = tujuan.syaratRanah(kelas, tier);
		Realm punya = Cultivators.get(player).realm();
		if (tujuan == Pangkat.TERCATAT) {
			int pkTugas = k.bagian("pangkat").getIntOr("pkTugas", 0);
			out.add(new SyaratMasuk.Baris(punya.atLeast(perlu) || pkTugas >= PK_TUGAS_TERCATAT, "naik.ranah_atau_tugas",
					new Object[]{perlu.displayName(), pkTugas, PK_TUGAS_TERCATAT}));
		} else {
			out.add(new SyaratMasuk.Baris(punya.atLeast(perlu), "naik.ranah", new Object[]{perlu.displayName(), punya.displayName()}));
		}
		// days
		long sejak = switch (tujuan) {
			case PEWARIS, TETUA -> k.umurAnggota(hari);
			default -> k.hariPangkat < 0L ? k.umurAnggota(hari) : Math.max(0L, hari - k.hariPangkat);
		};
		out.add(new SyaratMasuk.Baris(sejak >= tujuan.hariMin(), tujuan == Pangkat.PEWARIS || tujuan == Pangkat.TETUA ? "naik.hari_anggota"
				: "naik.hari", new Object[]{sejak, tujuan.hariMin()}));
		// PK
		int biaya = GabungApi.biaya(k.sekte, tujuan.biaya());
		out.add(new SyaratMasuk.Baris(k.pk >= biaya, "naik.pk", new Object[]{biaya, k.pk}));
		// seat
		int kursi = tujuan.kursi(kelas);
		if (kursi > 0) {
			int dipakai = Kursi.pemegang(server, k.sekte, tujuan).size();
			out.add(new SyaratMasuk.Baris(dipakai < kursi, "naik.kursi", new Object[]{dipakai, kursi}));
		}
		// trial
		String ujian = ujian(tujuan);
		if (ujian != null) {
			out.add(new SyaratMasuk.Baris(sudahLulus(k, tujuan), "naik.ujian", new Object[]{Component.translatable(ujian)}));
		}
		for (Syarat s : SYARAT) {
			try {
				out.addAll(s.syarat(player, k, tujuan));
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("A Gabung promotion requirement failed", exception);
			}
		}
		return out;
	}

	/** [Ajukan Kenaikan] on the disciple path. */
	public static boolean ajukan(ServerPlayer player) {
		return ajukan(player, false);
	}

	/** [Ajukan Kenaikan]; {@code jalurTetua} picks the elder branch at Inti. False (with the card) when refused. */
	public static boolean ajukan(ServerPlayer player, boolean jalurTetua) {
		Keanggotaan k = GabungApi.anggota(player);
		if (!k.status.anggota()) {
			Msg.chat(player, Component.translatable("kultivator.gabung.uji.bukan_anggota").withStyle(ChatFormatting.GRAY));
			return false;
		}
		Pangkat tujuan = berikut(k, jalurTetua);
		if (tujuan == null) {
			Msg.chat(player, Component.translatable(k.pangkat == Pangkat.TAMU ? "kultivator.gabung.naik.tamu" : "kultivator.gabung.naik.puncak")
					.withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (UPACARA.containsKey(player.getUUID())) {
			return false;
		}
		List<SyaratMasuk.Baris> syarat = syarat(player, tujuan);
		boolean ok = true;
		for (SyaratMasuk.Baris b : syarat) {
			ok &= b.ok();
		}
		if (!ok && tujuan == Pangkat.LUAR && hanyaUjian(syarat) && Pemeriksa.diWilayahSendiri(player)) {
			return ujiUlang(player, k.sekte);
		}
		// D-41AD: the Murid Dalam duel and the Ujian Tetua start the same way (the trial is the only missing line)
		if (!ok && UjianPangkat.punya(tujuan) && hanyaUjian(syarat) && Pemeriksa.diWilayahSendiri(player)) {
			return UjianPangkat.mulai(player, k.sekte, tujuan);
		}
		if (!ok || !Pemeriksa.diWilayahSendiri(player)) {
			kartu(player, tujuan, syarat, ok);
			return false;
		}
		int biaya = GabungApi.biaya(k.sekte, tujuan.biaya());
		if (!GabungApi.bayarPk(player, biaya, "gabung.naik")) {
			kartu(player, tujuan, syarat, false);
			return false;
		}
		UPACARA.put(player.getUUID(), new Upacara(tujuan, player.level().getGameTime(), biaya));
		GabungPayloads.kirimKlip(player, GabungGerakId.SALAM_SEKTE, 0);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
		Msg.chat(player, Component.translatable("kultivator.gabung.naik.upacara", tujuan.nama(), Pemeriksa.nama(k.sekte)).withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** True when the trial line is the only unmet requirement. */
	private static boolean hanyaUjian(List<SyaratMasuk.Baris> syarat) {
		boolean ujian = false;
		for (SyaratMasuk.Baris b : syarat) {
			if (!b.ok()) {
				if (!"naik.ujian".equals(b.kunci())) {
					return false;
				}
				ujian = true;
			}
		}
		return ujian;
	}

	/** 41.7 "Uji Ulang": the sect's corak test again; S2 >= 0.5 passes the trial of Murid Luar. */
	private static boolean ujiUlang(ServerPlayer player, String sekte) {
		Component gagal = dev.kultivator.sekte.gabung.masuk.UjianCorak.mulai(player, sekte, null, (p, hasil) -> {
			GabungApi.lepasSibuk(p, dev.kultivator.sekte.gabung.masuk.UjianCorak.SIBUK);
			if (!hasil.gagal() && hasil.skor() >= 0.5D) {
				lulus(p, Pangkat.LUAR);
			} else {
				Msg.chat(p, Component.translatable("kultivator.gabung.naik.uji_ulang_gagal", (int) Math.round(hasil.skor() * 100.0D))
						.withStyle(ChatFormatting.RED));
			}
		});
		if (gagal != null) {
			Msg.chat(player, gagal.copy().withStyle(ChatFormatting.GRAY));
			return false;
		}
		Msg.chat(player, Component.translatable("kultivator.gabung.naik.uji_ulang_mulai").withStyle(ChatFormatting.GOLD));
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (UPACARA.isEmpty() || server.getTickCount() % 10 != 0) {
			return;
		}
		for (Map.Entry<UUID, Upacara> e : List.copyOf(UPACARA.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				UPACARA.remove(e.getKey());
				continue;
			}
			Upacara u = e.getValue();
			long t = player.level().getGameTime() - u.mulai();
			ServerLevel level = player.level();
			if (t >= UPACARA_TICK / 2 && t < UPACARA_TICK / 2 + 10) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 1.0F);
			}
			if (t >= UPACARA_TICK / 2) {
				// the robe flashes in the new trim colour
				level.sendParticles(new DustParticleOptions(u.tujuan().warna(), 1.4F), player.getX(), player.getY() + 1.0D, player.getZ(), 12,
						0.4D, 0.7D, 0.4D, 0.0D);
			}
			if (t >= UPACARA_TICK) {
				UPACARA.remove(e.getKey());
				Keanggotaan k = GabungApi.anggota(player);
				if (!k.status.anggota()) {
					continue;
				}
				k.bagian("pangkat").remove("lulus." + u.tujuan().id());
				GabungApi.setPangkat(player, u.tujuan(), "gabung.naik");
				Msg.title(player, u.tujuan().nama(), Component.translatable("kultivator.gabung.naik.selesai", Pemeriksa.nama(k.sekte)), 10, 60, 20);
			}
		}
	}

	/** Stops a running ceremony (logout, leaving); {@code kembalikan} refunds its PK. */
	public static void batalkan(ServerPlayer player, boolean kembalikan) {
		Upacara u = UPACARA.remove(player.getUUID());
		if (u != null && kembalikan) {
			// a refund, not income: straight back to the spendable PK (no skalaPk, no monthly count)
			Keanggotaan k = GabungApi.anggota(player);
			if (k.status.anggota()) {
				k.pk += u.biaya();
				GabungData.get(player.level().getServer()).setDirty();
			}
		}
	}

	private static void kartu(ServerPlayer player, Pangkat tujuan, List<SyaratMasuk.Baris> syarat, boolean semuaOk) {
		List<Component> baris = new ArrayList<>();
		for (SyaratMasuk.Baris b : syarat) {
			baris.add(Component.literal(b.ok() ? "✔ " : "✘ ").append(b.teks()));
		}
		if (semuaOk) {
			baris.add(Component.translatable("kultivator.gabung.naik.di_aula").withStyle(ChatFormatting.YELLOW));
		}
		Kartu.kirim(player, Component.translatable("kultivator.gabung.naik.judul", tujuan.nama()), baris, List.of());
	}

	private Promosi() {
	}
}
