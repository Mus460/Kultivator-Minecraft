package dev.kultivator.dunia.kota42.sekte;

import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.tempat.kotabesar.GelombangBinatang;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The defence calls of a protected town (DESIGN 42.8.2 "Panggilan Bela Kota", contract C42.E1 via {@link PaktaLindung}).
 * Every {@code kotaSekte.serbuanHari} days (+-5, so 10-20 by default) beasts, bandits or demonic cultivators gather outside a
 * protected town. The protector has one world day to answer ({@link #jawab}):
 * <ul>
 * <li><b>Come</b> ({@code datang}): when he reaches the town the raid runs live ({@link GelombangBinatang#mulai}: three waves
 * at one edge of the place, nothing breaks, nobody of the town dies);</li>
 * <li><b>Send disciples</b> ({@code kirim}): at least two of Foundation or above (those on guard duty in the town count, others
 * within 16 blocks go for a day); the fight is decided at once from their strength against the raiders' (a disciple never
 * dies; one may come back wounded, 39's wound).</li>
 * </ul>
 * Silence for a day is a loss. A won call: Nama Baik +3, renown +10, the gossip of the victory, {@code kota42_bela_kota}; a
 * lost one: the week's tribute halved, three days of fear (-20 mood), three lost in a row end the pact. While a call is
 * open, players in the town are in the enemy pressure {@code kota.kepung} and the answering protector in {@code kota.bela}.
 * State: {@code pakta[situs].sb}: {@code k} state, {@code j} kind, {@code f} strength, {@code b} deadline (game time),
 * {@code berikut} the next call's day, {@code w} calls won, {@code t} the last result day and {@code m} won.
 */
public final class SerbuanKota {

	/** AwayStore reason of disciples sent to a defence. */
	public static final String ALASAN_BELA = "kota42.bela";
	public static final String TENANG = "tenang";
	public static final String PANGGILAN = "panggilan";
	public static final String BERLANGSUNG = "berlangsung";
	public static final String CARA_DATANG = "datang";
	public static final String CARA_KIRIM = "kirim";
	/** Ticks to answer a call: one world day. */
	public static final long BATAS_JAWAB = 24000L;
	public static final int MURID_MIN = 2;
	static final double JARAK_KIRIM = 16.0D;

	/** What a screen shows of the defence of one town. */
	public record Bela(String keadaan, String jenis, long sisaTick, int kekuatan, int kekuatanKita, long terakhirHari, boolean terakhirMenang,
			@Nullable BlockPos depan, long berikut) {
	}

	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		GelombangBinatang.initTempat();
	}

	static void lupakan() {
	}

	// ================================================================== schedule

	/** Sets the day of the next call: {@code serbuanHari} +- 5 from {@code hari}, stable per place. */
	static void jadwalkan(MinecraftServer server, long situs, CompoundTag pakta, long hari) {
		CompoundTag sb = sb(pakta);
		int dasar = KotaConfig.sekte().serbuanHari;
		int geser = (int) Math.floorMod((situs * 0x9E3779B97F4A7C15L) ^ (hari * 0x632BE59BD9B4E019L), 11L) - 5;
		sb.putLong("berikut", hari + Math.max(3, dasar + geser));
		sb.putString("k", TENANG);
		pakta.put("sb", sb);
	}

	/** Day step of one pact: the call comes when its day is here. */
	static void harian(MinecraftServer server, long situs, CompoundTag pakta, long hari) {
		CompoundTag sb = sb(pakta);
		if (!sb.contains("berikut")) {
			jadwalkan(server, situs, pakta, hari);
			return;
		}
		if (TENANG.equals(sb.getStringOr("k", TENANG)) && hari >= sb.getLongOr("berikut", Long.MAX_VALUE)) {
			panggil(server, situs, pakta);
		}
	}

	/** Opens a defence call now ({@code uji kota_hidup sekte pakta serbuan} too). */
	static void panggil(MinecraftServer server, long situs, CompoundTag pakta) {
		UUID owner = pakta.read("o", UUIDUtil.CODEC).orElse(null);
		if (owner == null) {
			return;
		}
		CompoundTag sb = sb(pakta);
		long hari = KotaSekte.hari(server);
		RandomSource r = RandomSource.create(situs ^ hari * 0x9E3779B97F4A7C15L ^ 0x5E4B0AL);
		String jenis = pilihJenis(server, situs, r);
		int kelas = Math.min(3, PajakKota.kelas(server, situs));
		double kuat = 60.0D * kelas * (1.0D + 0.15D * sb.getIntOr("w", 0)) * (0.8D + 0.4D * r.nextDouble());
		sb.putString("k", PANGGILAN);
		sb.putString("j", jenis);
		sb.putInt("f", (int) Math.round(kuat));
		sb.putLong("b", server.overworld().getGameTime() + BATAS_JAWAB);
		sb.remove("cara");
		pakta.put("sb", sb);
		KotaSekteData.get(server).ubah();
		KotaBus.SERBUAN.invoker().onSerbuan(server, situs, true, false);
		ServerPlayer p = server.getPlayerList().getPlayer(owner);
		if (p != null) {
			Msg.title(p, Component.translatable("kultivator.kota42.sekte.bela.judul").withStyle(ChatFormatting.RED),
					Component.translatable("kultivator.kota42.sekte.bela.sub." + jenis, KotaSekte.namaTempat(server, situs)), 10, 80, 20);
			Msg.chat(p, Component.translatable("kultivator.kota42.sekte.bela.panggil", KotaSekte.namaTempat(server, situs),
					Component.translatable("kultivator.kota42.sekte.bela.jenis." + jenis), sb.getIntOr("f", 0)).withStyle(ChatFormatting.GOLD));
		}
		Halls.log(owner, Component.translatable("kultivator.kota42.sekte.bela.log_panggil", KotaSekte.namaTempat(server, situs)));
	}

	private static String pilihJenis(MinecraftServer server, long situs, RandomSource r) {
		if (PajakKota.kotaIblis(server, situs)) {
			return GelombangBinatang.JENIS_BEGAL;
		}
		int roll = r.nextInt(100);
		return switch (PajakKota.jenisTempat(server, situs)) {
			case PajakKota.JT_KAMPUNG -> roll < 60 ? GelombangBinatang.JENIS_BINATANG : GelombangBinatang.JENIS_BEGAL;
			case PajakKota.JT_TEMA -> roll < 30 ? GelombangBinatang.JENIS_BINATANG : roll < 60 ? GelombangBinatang.JENIS_BEGAL : GelombangBinatang.JENIS_IBLIS;
			default -> roll < 40 ? GelombangBinatang.JENIS_BINATANG : roll < 75 ? GelombangBinatang.JENIS_BEGAL : GelombangBinatang.JENIS_IBLIS;
		};
	}

	// ================================================================== reading

	/** The defence state of a protected town (a calm one when none). */
	public static Bela bela(MinecraftServer server, long situs) {
		CompoundTag pakta = PaktaLindung.peek(server, situs);
		CompoundTag sb = pakta == null ? new CompoundTag() : pakta.getCompoundOrEmpty("sb");
		UUID owner = pakta == null ? null : pakta.read("o", UUIDUtil.CODEC).orElse(null);
		long now = server.overworld().getGameTime();
		String k = sb.getStringOr("k", TENANG);
		long sisa = PANGGILAN.equals(k) || BERLANGSUNG.equals(k) ? Math.max(0L, sb.getLongOr("b", now) - now) : 0L;
		return new Bela(k, sb.getStringOr("j", GelombangBinatang.JENIS_BINATANG), sisa, sb.getIntOr("f", 0),
				owner == null ? 0 : kekuatanKita(server, situs, owner, null), sb.getLongOr("t", -1L), sb.getBooleanOr("m", false),
				GelombangBinatang.depanTempat(situs), sb.getLongOr("berikut", -1L));
	}

	/**
	 * The defence strength {@code owner} can send to {@code situs}: 20 per power step of the disciples on guard duty there
	 * and (with {@code player}) of his Foundation+ disciples within 16 blocks, +10 % with a branch training hall.
	 */
	public static int kekuatanKita(MinecraftServer server, long situs, UUID owner, @Nullable ServerPlayer player) {
		double kuat = 0.0D;
		for (TugasKotaMurid.Tugas t : TugasKotaMurid.penjagaKota(server, situs, owner)) {
			kuat += 20.0D * Math.max(1, t.kekuatan());
		}
		for (DiscipleEntity d : player == null ? List.<DiscipleEntity>of() : bisaDikirim(player)) {
			kuat += 20.0D * Math.max(1, DisciplePaths.power(d.data()));
		}
		return (int) Math.round(kuat * CabangKota.bonusBela(server, situs, owner));
	}

	/** Disciples of {@code player} within 16 blocks who may go to a defence: Foundation or above, not busy, not wounded. */
	public static List<DiscipleEntity> bisaDikirim(ServerPlayer player) {
		List<DiscipleEntity> out = new ArrayList<>();
		long now = player.level().getGameTime();
		for (DiscipleEntity d : Disciples.loaded(player.getUUID())) {
			if (d.role() == DiscipleEntity.Role.DISCIPLE && d.level() == player.level() && d.distanceToSqr(player) <= JARAK_KIRIM * JARAK_KIRIM
					&& d.data().realm().ordinal() >= Realm.FOUNDATION.ordinal() && !d.data().breakingThrough()
					&& MandiriApi.luka(d.data(), now) == JenisLuka.TIDAK) {
				out.add(d);
			}
		}
		return out;
	}

	// ================================================================== answering

	/** {@code bela:BD}: answers the open call by coming ({@code datang}) or sending disciples ({@code kirim}). */
	public static KotaSekte.Hasil jawab(ServerPlayer player, long situs, String cara) {
		MinecraftServer server = player.level().getServer();
		CompoundTag pakta = PaktaLindung.peek(server, situs);
		if (pakta == null || !player.getUUID().equals(pakta.read("o", UUIDUtil.CODEC).orElse(null))) {
			return KotaSekte.Hasil.gagal("pakta.bukan_milik").kirim(player);
		}
		CompoundTag sb = sb(pakta);
		if (!PANGGILAN.equals(sb.getStringOr("k", TENANG))) {
			return KotaSekte.Hasil.gagal("bela.tidak_ada").kirim(player);
		}
		if (CARA_DATANG.equals(cara)) {
			sb.putString("cara", CARA_DATANG);
			pakta.put("sb", sb);
			KotaSekteData.get(server).ubah();
			long sisa = Math.max(20L, sb.getLongOr("b", 0L) - player.level().getGameTime());
			KotaHidup.tandaiSibuk(player, KotaHidup.SIBUK_BELA, (int) Math.min(Integer.MAX_VALUE, sisa));
			return KotaSekte.Hasil.berhasil("bela.datang", KotaSekte.namaTempat(server, situs)).kirim(player);
		}
		if (!CARA_KIRIM.equals(cara)) {
			return KotaSekte.Hasil.gagal("bela.cara").kirim(player);
		}
		List<TugasKotaMurid.Tugas> jaga = TugasKotaMurid.penjagaKota(server, situs, player.getUUID());
		List<DiscipleEntity> pergi = bisaDikirim(player);
		int fondasi = pergi.size();
		for (TugasKotaMurid.Tugas t : jaga) {
			if (t.ranah() >= Realm.FOUNDATION.ordinal()) {
				fondasi++;
			}
		}
		if (fondasi < MURID_MIN) {
			return KotaSekte.Hasil.gagal("bela.murid_kurang", MURID_MIN).kirim(player);
		}
		int kita = kekuatanKita(server, situs, player.getUUID(), player);
		int musuh = Math.max(1, sb.getIntOr("f", 1));
		long hari = KotaSekte.hari(server);
		RandomSource r = RandomSource.create(situs ^ hari * 31L ^ player.getUUID().getLeastSignificantBits());
		double peluang = Mth.clamp(0.5D + (kita - musuh) / (2.0D * musuh), 0.1D, 0.95D);
		boolean menang = r.nextDouble() < peluang;
		long kembali = player.level().getGameTime() + 24000L;
		List<Component> nama = new ArrayList<>();
		for (DiscipleEntity d : pergi) {
			CompoundTag info = new CompoundTag();
			info.putLong("situs", situs);
			info.putBoolean("menang", menang);
			info.putBoolean("luka", r.nextDouble() < (menang ? 0.10D : 0.35D));
			nama.add(d.getDisplayName());
			Halls.storeAway(player.getUUID(), d, kembali, ALASAN_BELA, info);
		}
		for (TugasKotaMurid.Tugas t : jaga) {
			if (r.nextDouble() < (menang ? 0.10D : 0.35D)) {
				TugasKotaMurid.tandaiLuka(server, t.soul());
			}
			nama.add(Component.literal(t.nama()));
		}
		selesai(server, situs, menang, false, player.getUUID());
		net.minecraft.network.chat.MutableComponent daftar = Component.empty();
		for (int i = 0; i < nama.size(); i++) {
			if (i > 0) {
				daftar.append(", ");
			}
			daftar.append(nama.get(i));
		}
		return KotaSekte.Hasil.berhasil(menang ? "bela.kirim_menang" : "bela.kirim_kalah", daftar, kita, musuh,
				(int) Math.round(peluang * 100.0D)).kirim(player);
	}

	// ================================================================== clock (every second)

	static void tick(MinecraftServer server) {
		GelombangBinatang.tickTempat(server);
		if (!KotaConfig.sekte().pakta) {
			return;
		}
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		java.util.Set<UUID> dalam = new java.util.HashSet<>();
		for (long situs : PaktaLindung.semua(server)) {
			CompoundTag pakta = PaktaLindung.peek(server, situs);
			if (pakta == null) {
				continue;
			}
			CompoundTag sb = pakta.getCompoundOrEmpty("sb");
			String k = sb.getStringOr("k", TENANG);
			if (TENANG.equals(k)) {
				continue;
			}
			UUID owner = pakta.read("o", UUIDUtil.CODEC).orElse(null);
			BoundingBox box = GelombangBinatang.kotakTempat(level, situs);
			if (box != null) {
				BoundingBox area = box.inflatedBy(16, 32, 16);
				for (ServerPlayer p : level.players()) {
					if (area.isInside(p.blockPosition())) {
						dalam.add(p.getUUID());
					}
				}
			}
			if (PANGGILAN.equals(k)) {
				if (now >= sb.getLongOr("b", 0L)) {
					selesai(server, situs, false, false, owner);
					continue;
				}
				ServerPlayer p = owner == null ? null : server.getPlayerList().getPlayer(owner);
				if (CARA_DATANG.equals(sb.getStringOr("cara", "")) && p != null && box != null && p.level() == level
						&& box.inflatedBy(24, 32, 24).isInside(p.blockPosition())) {
					mulaiHidup(level, situs, pakta, sb, p);
				}
			}
		}
		// one pass for every town: inside any besieged town = under the siege pressure
		for (ServerPlayer p : level.players()) {
			KotaHidup.kepung(p, dalam.contains(p.getUUID()));
		}
	}

	private static void mulaiHidup(ServerLevel level, long situs, CompoundTag pakta, CompoundTag sb, ServerPlayer p) {
		MinecraftServer server = level.getServer();
		String jenis = sb.getStringOr("j", GelombangBinatang.JENIS_BINATANG);
		int kuat = sb.getIntOr("f", 60);
		UUID owner = p.getUUID();
		boolean jalan = GelombangBinatang.mulai(level, situs, kuat, jenis, menang -> selesai(server, situs, menang, true, owner));
		if (!jalan) {
			return;
		}
		sb.putString("k", BERLANGSUNG);
		sb.putLong("b", level.getGameTime() + GelombangBinatang.TEMPAT_PERTANDA + GelombangBinatang.TEMPAT_BATAS + 200L);
		pakta.put("sb", sb);
		KotaSekteData.get(server).ubah();
		KotaHidup.tandaiSibuk(p, KotaHidup.SIBUK_BELA, (int) (GelombangBinatang.TEMPAT_PERTANDA + GelombangBinatang.TEMPAT_BATAS));
		Component nama = KotaSekte.namaTempat(server, situs);
		for (ServerPlayer q : level.players()) {
			if (q.blockPosition().closerThan(p.blockPosition(), 160.0D)) {
				Msg.title(q, Component.translatable("kultivator.kota42.sekte.bela.mulai").withStyle(ChatFormatting.RED),
						Component.translatable("kultivator.kota42.sekte.bela.mulai_sub." + jenis, nama), 10, 70, 20);
			}
		}
	}

	/** The call ends: rewards or losses, the bus, the next call's day. */
	static void selesai(MinecraftServer server, long situs, boolean menang, boolean hidup, @Nullable UUID owner) {
		CompoundTag pakta = PaktaLindung.peek(server, situs);
		if (pakta == null || !pakta.contains("o")) {
			return;
		}
		CompoundTag sb = sb(pakta);
		long hari = KotaSekte.hari(server);
		sb.putLong("t", hari);
		sb.putBoolean("m", menang);
		sb.putString("k", TENANG);
		sb.remove("cara");
		if (menang) {
			sb.putInt("w", sb.getIntOr("w", 0) + 1);
		}
		pakta.put("sb", sb);
		jadwalkan(server, situs, pakta, hari);
		ServerLevel level = server.overworld();
		Component nama = KotaSekte.namaTempat(server, situs);
		ServerPlayer p = owner == null ? null : server.getPlayerList().getPlayer(owner);
		if (menang) {
			PaktaLindung.menang(server, situs, pakta);
			if (owner != null) {
				KotaSekte.tambahNamaBaik(server, situs, owner, 3);
				Halls.addRenown(owner, 10, "kultivator.kota42.sekte.renown.bela");
				Halls.log(owner, Component.translatable("kultivator.kota42.sekte.bela.log_menang", nama));
			}
			if (p != null) {
				Advancements.grant(p, "tempat/kota42_bela_kota");
				KotaHidup.lepasSibuk(p, KotaHidup.SIBUK_BELA);
				if (hidup) {
					Msg.title(p, Component.translatable("kultivator.kota42.sekte.bela.menang").withStyle(ChatFormatting.GOLD),
							Component.translatable("kultivator.kota42.sekte.bela.menang_sub", nama), 10, 70, 20);
				}
			}
			for (ServerPlayer q : level.players()) {
				if (hidup && q != p && level.dimension() == q.level().dimension() && GelombangBinatang.terlibat(q)) {
					KotaSekte.tambahNamaBaik(server, situs, q.getUUID(), 2);
				}
			}
		} else {
			PaktaLindung.kalah(server, situs, pakta, hari);
			if (owner != null) {
				Halls.log(owner, Component.translatable("kultivator.kota42.sekte.bela.log_kalah", nama));
			}
			if (p != null) {
				KotaHidup.lepasSibuk(p, KotaHidup.SIBUK_BELA);
				Msg.title(p, Component.translatable("kultivator.kota42.sekte.bela.kalah").withStyle(ChatFormatting.DARK_RED),
						Component.translatable("kultivator.kota42.sekte.bela.kalah_sub", nama, PaktaLindung.gagal(server, situs),
								PaktaLindung.GAGAL_MAKS), 10, 70, 20);
			}
		}
		KotaSekteData.get(server).ubah();
		KotaBus.SERBUAN.invoker().onSerbuan(server, situs, false, menang);
	}

	/** The pact ended: a live raid there stops (the raiders scatter), nothing else is owed. */
	static void lupakanTempat(MinecraftServer server, long situs) {
		if (GelombangBinatang.serbuTempat(situs)) {
			GelombangBinatang.hentikanTempat(server.overworld(), situs, false);
		}
	}

	/** A disciple sent to a defence comes home: the diary line, maybe a wound (never death), a small share of a bar. */
	static @Nullable Vec3 kembali(ServerLevel level, UUID owner, CompoundTag stored, CompoundTag info) {
		CompoundTag disciple = stored.getCompoundOrEmpty("disciple");
		if (disciple.isEmpty()) {
			return null;
		}
		MinecraftServer server = level.getServer();
		DiscipleData data = DiscipleData.load(disciple);
		long now = level.getGameTime();
		boolean menang = info.getBooleanOr("menang", false);
		if (info.getBooleanOr("luka", false)) {
			MandiriApi.lukai(data, JenisLuka.TERLUKA, now, now + 2L * 24000L, "kota42.bela");
		}
		DisciplePaths.grantBarShare(data, menang ? 0.08D : 0.04D);
		stored.put("disciple", data.save());
		UUID soul = stored.read("soul", UUIDUtil.CODEC).orElse(null);
		if (soul != null) {
			long situs = info.getLongOr("situs", 0L);
			long hari = KotaSekte.hari(server);
			int jam = (int) ((dev.kultivator.dunia.kota.Kota.timeOfDay(server) / 1000L + 6L) % 24L);
			MandiriApi.simpanHarian(server, owner, soul, new EntriHarian(hari, jam, "kota.bela",
					menang ? "kultivator.kota42.sekte.harian.bela_menang" : "kultivator.kota42.sekte.harian.bela_kalah",
					List.of(KotaSekte.namaTempat(server, situs).getString()), menang ? EntriHarian.Suasana.BANGGA : EntriHarian.Suasana.SEDIH,
					false));
		}
		return null;
	}

	/** Test: opens a call at {@code situs} now; false without a pact. */
	public static boolean paksa(MinecraftServer server, long situs) {
		CompoundTag pakta = PaktaLindung.peek(server, situs);
		if (pakta == null || !pakta.contains("o")) {
			return false;
		}
		panggil(server, situs, pakta);
		return true;
	}

	/** Test: ends the open call at {@code situs} as lost. */
	public static boolean paksaGagal(MinecraftServer server, long situs) {
		CompoundTag pakta = PaktaLindung.peek(server, situs);
		if (pakta == null || !pakta.contains("o")) {
			return false;
		}
		if (GelombangBinatang.serbuTempat(situs)) {
			return GelombangBinatang.hentikanTempat(server.overworld(), situs, false);
		}
		selesai(server, situs, false, false, pakta.read("o", UUIDUtil.CODEC).orElse(null));
		return true;
	}

	private static CompoundTag sb(CompoundTag pakta) {
		CompoundTag sb = pakta.getCompoundOrEmpty("sb");
		if (!pakta.contains("sb")) {
			pakta.put("sb", sb);
		}
		return sb;
	}

	private SerbuanKota() {
	}
}
