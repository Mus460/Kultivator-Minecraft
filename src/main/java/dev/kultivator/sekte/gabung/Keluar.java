package dev.kultivator.sekte.gabung;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.cultivation.CaveAbode;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.dunia.tempat.kompleks.Kartu;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.api.StatusKeluar;
import dev.kultivator.sekte.gabung.api.SyaratMasuk;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.gabung.masuk.GabungItems;
import dev.kultivator.sekte.mandiri.seteru.Roster;
import dev.kultivator.technique.spec.run.Loadout;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Leaving a sect (DESIGN 41.7) and the founding veto of rule R2 (41.3.2-41.3.3). The data door is
 * {@code GabungApi.keluar}; this class runs what comes before it: the requirements, the ceremony, the master's blessing,
 * the badge, the residence, the stolen inheritance, and afterwards the followers of 41.7.4.
 * <ul>
 * <li><b>Pamit</b> (41.7.1): member, Tercatat or higher, 5 days of membership, record below 30, no binding War Call, rent
 * paid, not the master. A short bow ({@code pamit_bungkuk}) where he stands, the badge handed back, the residence
 * released; a Shifu bond of 50+ gives the <i>Surat Restu</i> (and Restu Mendirikan from Inti up). Forcing it without the
 * requirements is <b>Pamit Tanpa Izin</b>: rank and PK lost, no blessing, not hunted.</li>
 * <li><b>Diusir</b> (41.7.2): the Discipline Elder reads the expulsion; in the grounds the badge is seized, elsewhere it
 * cracks into a decorative jade and a letter tells him. Learned techniques stay; the Warisan may never be taught.
 * Righteous sects add Infami +5.</li>
 * <li><b>Membelot</b> (41.7.3): status deserter, Infami +10 (righteous) or +5; the hunters of 41D read the hunt window
 * that {@code GabungApi.keluar} opens; the held Warisan becomes stolen ({@link #hapusWarisan} wipes it on capture).</li>
 * </ul>
 * What a former member may do with the Warisan is kept in {@code bagian("keluar")} (the per-module compound survives
 * {@code Keanggotaan.lepas}): {@code warisan.<s>} the spec, {@code restu.<s>} blessed, {@code restuDirikan.<s>} the day of a
 * founding blessing (41D: a hall founded within 30 days starts at standing +40), {@code ajar.<s>} unblessed teachings.
 * <p>
 * R2: a member from Pelayan to Dalam (and Inti+ without a branch) who places a new Hall Stele founds it <b>dormant</b>
 * ({@code Halls.FoundVeto}) and gets the choices [Pamit dulu] [Ajukan Cabang] (Inti+) [Dirikan tanpa izin] [Batal]; waking
 * the stele while the veto holds brings the choices back ({@code Halls.SteleMoveListener}).
 */
public final class Keluar {

	/** 41.7.1 requirements. */
	public static final int PAMIT_HARI_MIN = 5;
	public static final int PAMIT_CATATAN_MAKS = 30;
	public static final int RESTU_IKATAN_MIN = 50;
	/** 41.7.4: roster friends who follow (attitude), and the standing cost per follower. */
	public static final int IKUT_SIKAP_RESTU = 80;
	public static final int IKUT_SIKAP_PEMBELOT = 90;
	public static final int IKUT_STANDING_RESTU = -5;
	public static final int IKUT_STANDING_PEMBELOT = -15;
	/** 41.7.4: standing lost per unblessed teaching of the Warisan. */
	public static final int AJAR_STANDING = -10;
	/** Infami of an expulsion from a righteous sect and of desertion (41.7.2-41.7.3). */
	public static final int INFAMI_USIR_LURUS = 5;
	public static final int INFAMI_BELOT_LURUS = 10;
	public static final int INFAMI_BELOT_LAIN = 5;

	/** Branch request handler of 41D (R2 [Ajukan Cabang]); null until 41D registers it. */
	@FunctionalInterface
	public interface PenanganCabang {
		/** Starts the branch request for the dormant hall at {@code stele}; false (with its own message) when refused. */
		boolean ajukan(ServerPlayer player, String sekte, @Nullable BlockPos stele);
	}

	private static volatile @Nullable PenanganCabang cabang;
	private static boolean registered;

	/** From {@code Gabung.init} (module on). */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		Halls.addFoundVeto(Keluar::vetoPendirian);
		Halls.addSteleMoveListener(new Halls.SteleMoveListener() {
			@Override
			public boolean beforeMove(ServerPlayer player, UUID owner, ServerLevel level, @Nullable BlockPos from, BlockPos to, boolean wake) {
				if (!wake || !Gabung.aktif() || vetoAlasan(player) == null) {
					return false;
				}
				kartuAula(player);
				return true;
			}
		});
		GabungCommands.daftar("pamit", () -> Commands.literal("pamit")
				.executes(ctx -> pamit(ctx.getSource().getPlayerOrException(), false) ? 1 : 0)
				.then(Commands.literal("paksa").executes(ctx -> pamit(ctx.getSource().getPlayerOrException(), true) ? 1 : 0))
				.then(Commands.literal("tinggalkan").executes(ctx -> tinggalkan(ctx.getSource().getPlayerOrException()) ? 1 : 0)));
		GabungCommands.daftar("aula", Keluar::perintahAula);
		GabungUji.daftar("pamit", () -> Commands.literal("pamit")
				.executes(ctx -> ujiPamit(ctx.getSource().getPlayerOrException(), false))
				.then(Commands.literal("restu").executes(ctx -> ujiPamit(ctx.getSource().getPlayerOrException(), true))));
		GabungUji.daftar("usir", () -> Commands.literal("usir").executes(ctx -> usir(ctx.getSource().getPlayerOrException(),
				"kultivator.gabung.keluar.sebab.uji") ? 1 : 0));
		GabungUji.daftar("belot", () -> Commands.literal("belot").executes(ctx -> belot(ctx.getSource().getPlayerOrException(),
				"kultivator.gabung.keluar.sebab.uji") ? 1 : 0));
	}

	/** 41D registers the branch request of R2 here. */
	public static void setPenanganCabang(@Nullable PenanganCabang penangan) {
		cabang = penangan;
	}

	// ================================================================== Pamit (41.7.1)

	/** The requirements of an honourable leave, in screen order (lang {@code kultivator.gabung.syarat.pamit.*}). */
	public static List<SyaratMasuk.Baris> syaratPamit(ServerPlayer player) {
		List<SyaratMasuk.Baris> out = new ArrayList<>();
		Keanggotaan k = GabungApi.anggota(player);
		MinecraftServer server = player.level().getServer();
		long hari = GabungApi.hari(server);
		out.add(new SyaratMasuk.Baris(k.status == dev.kultivator.sekte.gabung.api.StatusAnggota.ANGGOTA, "pamit.anggota", new Object[0]));
		out.add(new SyaratMasuk.Baris(k.pangkat.tingkat() >= Pangkat.TERCATAT.tingkat(), "pamit.pangkat",
				new Object[]{Pangkat.TERCATAT.nama()}));
		out.add(new SyaratMasuk.Baris(k.umurAnggota(hari) >= PAMIT_HARI_MIN, "pamit.hari", new Object[]{k.umurAnggota(hari), PAMIT_HARI_MIN}));
		out.add(new SyaratMasuk.Baris(k.pelanggaran < PAMIT_CATATAN_MAKS, "pamit.catatan", new Object[]{k.pelanggaran, PAMIT_CATATAN_MAKS}));
		out.add(new SyaratMasuk.Baris(!perangMengikat(server, k) && !dev.kultivator.sekte.gabung.politik.PanggilanPerang.wajib(player), "pamit.perang",
				new Object[0]));
		out.add(new SyaratMasuk.Baris(k.kediaman.isEmpty() || k.sewaSampai >= hari, "pamit.sewa", new Object[0]));
		out.add(new SyaratMasuk.Baris(k.pangkat != Pangkat.KETUA, "pamit.ketua", new Object[0]));
		return out;
	}

	/** True while a War Call binds this member (Dalam and up while the sect's war runs; 41D keeps {@code perang.lawan}). */
	public static boolean perangMengikat(MinecraftServer server, Keanggotaan k) {
		if (k.sekte.isEmpty() || k.pangkat.tingkat() < Pangkat.DALAM.tingkat() || k.pangkat == Pangkat.TAMU) {
			return false;
		}
		GabungData.DataSekteGabung d = GabungData.get(server).peekSekte(k.sekte);
		return d != null && !d.perang.getStringOr("lawan", "").isEmpty();
	}

	/**
	 * [Pamit]: leaves honourably when every requirement holds; otherwise shows them with [Pamit tanpa izin] (and, while a
	 * War Call binds a Dalam+, [Tinggalkan sekte] = desertion). {@code paksa}: Pamit Tanpa Izin right away.
	 */
	public static boolean pamit(ServerPlayer player, boolean paksa) {
		Keanggotaan k = GabungApi.anggota(player);
		if (!k.status.anggota() || k.sekte.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.gabung.uji.bukan_anggota").withStyle(ChatFormatting.GRAY));
			return false;
		}
		MinecraftServer server = player.level().getServer();
		if (paksa) {
			if (k.pangkat == Pangkat.KETUA) {
				Msg.chat(player, Component.translatable("kultivator.gabung.keluar.ketua_dulu").withStyle(ChatFormatting.RED));
				return false;
			}
			if (perangMengikat(server, k)) {
				// leaving by force while the War Call binds a Dalam+ is desertion (41.7.3)
				kartuPamit(player, syaratPamit(player));
				return false;
			}
			pamitPaksa(player, "kultivator.gabung.keluar.sebab.paksa");
			return true;
		}
		List<SyaratMasuk.Baris> syarat = syaratPamit(player);
		for (SyaratMasuk.Baris b : syarat) {
			if (!b.ok()) {
				kartuPamit(player, syarat);
				return false;
			}
		}
		pamitHormat(player, k);
		return true;
	}

	/** [Tinggalkan sekte] while a War Call binds: desertion. */
	public static boolean tinggalkan(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		if (!k.status.anggota() || !perangMengikat(player.level().getServer(), k)) {
			return false;
		}
		return belot(player, "kultivator.gabung.keluar.sebab.perang");
	}

	private static void pamitHormat(ServerPlayer player, Keanggotaan k) {
		MinecraftServer server = player.level().getServer();
		String sekte = k.sekte;
		long hari = GabungApi.hari(server);
		boolean restu = !k.shifu.isEmpty() && k.ikatanShifu >= RESTU_IKATAN_MIN;
		Pangkat pangkat = k.pangkat;
		TokohSekte.Tokoh shifu = k.shifu.isEmpty() ? null : TokohSekte.tokoh(server, k.shifu);
		simpanWarisan(k, sekte, restu);
		CompoundTag kel = k.bagian("keluar");
		if (restu && pangkat.tingkat() >= Pangkat.INTI.tingkat()) {
			kel.putLong("restuDirikan." + sekte, hari);
		}
		upacara(player, GabungGerakId.PAMIT_BUNGKUK);
		Component namaSekte = Pemeriksa.nama(sekte);
		List<Component> baris = new ArrayList<>();
		baris.add(Component.translatable("kultivator.gabung.keluar.pamit.isi", namaSekte));
		if (shifu != null) {
			baris.add(Component.translatable("kultivator.gabung.keluar.pamit.shifu." + (restu ? "restu" : "dingin"), shifu.nama()));
		}
		ambilLencana(player, sekte, false);
		lepasKediaman(player, k);
		GabungApi.keluar(player, StatusKeluar.PAMIT);
		if (restu) {
			beriRestu(player, sekte, pangkat, hari, namaSekte, shifu == null ? "" : shifu.nama());
			baris.add(Component.translatable("kultivator.gabung.keluar.pamit.restu"));
			Advancements.grant(player, "sekte/gabung_pamit");
		}
		Keanggotaan.Riwayat r = GabungApi.anggota(player).riwayatSekte.get(sekte);
		if (r != null && r.pkBeku() > 0) {
			baris.add(Component.translatable("kultivator.gabung.keluar.pamit.beku", r.pkBeku()));
		}
		Kartu.kirim(player, Component.translatable("kultivator.gabung.keluar.pamit.judul", namaSekte), baris, List.of());
		if (restu) {
			pengikut(player, sekte, IKUT_SIKAP_RESTU, IKUT_STANDING_RESTU);
		}
	}

	/** Pamit Tanpa Izin (41.7.1, and R3 "found without leave"): rank and PK lost, no blessing, not hunted. */
	public static void pamitPaksa(ServerPlayer player, String sebab) {
		Keanggotaan k = GabungApi.anggota(player);
		if (!k.status.anggota()) {
			return;
		}
		String sekte = k.sekte;
		simpanWarisan(k, sekte, false);
		k.pk = 0;
		GabungData.get(player.level().getServer()).setDirty();
		upacara(player, GabungGerakId.PAMIT_BUNGKUK);
		ambilLencana(player, sekte, false);
		lepasKediaman(player, k);
		GabungApi.keluar(player, StatusKeluar.PAMIT_PAKSA);
		Kartu.kirim(player, Component.translatable("kultivator.gabung.keluar.paksa.judul", Pemeriksa.nama(sekte)),
				List.of(Component.translatable(sebab), Component.translatable("kultivator.gabung.keluar.paksa.isi")), List.of());
	}

	// ================================================================== Diusir (41.7.2)

	/** Expels the member ({@code sebab}: a lang key shown in the reading). False when he is no member. */
	public static boolean usir(ServerPlayer player, String sebab) {
		Keanggotaan k = GabungApi.anggota(player);
		if (!k.status.anggota() || k.sekte.isEmpty()) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		String sekte = k.sekte;
		boolean diWilayah = Pemeriksa.diWilayahSendiri(player);
		simpanWarisan(k, sekte, false);
		upacara(player, GabungGerakId.DIUSIR_TUNDUK);
		ambilLencana(player, sekte, !diWilayah);
		lepasKediaman(player, k);
		boolean lurus = SekteApi.diplomasi().aliran(sekte) == 0;
		GabungApi.keluar(player, StatusKeluar.DIUSIR);
		if (lurus) {
			TemaApi.tambahInfami(player, INFAMI_USIR_LURUS, "gabung.diusir");
		}
		Component tetua = BalaiHukum.namaTetuaHukum(server, sekte);
		List<Component> baris = new ArrayList<>();
		baris.add(Component.translatable(diWilayah ? "kultivator.gabung.keluar.usir.baca" : "kultivator.gabung.keluar.usir.surat", tetua,
				Pemeriksa.nama(sekte)));
		baris.add(Component.translatable(sebab));
		baris.add(Component.translatable("kultivator.gabung.keluar.usir.isi"));
		Kartu.kirim(player, Component.translatable("kultivator.gabung.keluar.usir.judul", Pemeriksa.nama(sekte)), baris, List.of());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.5F);
		return true;
	}

	// ================================================================== Membelot (41.7.3)

	/** The member deserts ({@code sebab}: a lang key). False when he is no member. */
	public static boolean belot(ServerPlayer player, String sebab) {
		Keanggotaan k = GabungApi.anggota(player);
		if (!k.status.anggota() || k.sekte.isEmpty()) {
			return false;
		}
		String sekte = k.sekte;
		simpanWarisan(k, sekte, false);
		if (!k.warisan.isEmpty()) {
			// 41D-P: the hunters wipe the stolen technique on capture (Keanggotaan.lepas clears warisan before KELUAR)
			k.bagian("perang").putString(dev.kultivator.sekte.gabung.politik.PemburuSekte.WARISAN_CURI, k.warisan);
		}
		BalaiHukum.lepasKurungan(player);
		ambilLencana(player, sekte, true);
		lepasKediaman(player, k);
		int aliran = SekteApi.diplomasi().aliran(sekte);
		GabungApi.keluar(player, StatusKeluar.PEMBELOT);
		TemaApi.tambahInfami(player, aliran == 0 ? INFAMI_BELOT_LURUS : INFAMI_BELOT_LAIN, "gabung.pembelot");
		Kartu.kirim(player, Component.translatable("kultivator.gabung.keluar.belot.judul", Pemeriksa.nama(sekte)),
				List.of(Component.translatable(sebab), Component.translatable("kultivator.gabung.keluar.belot.isi"),
						Component.translatable("kultivator.gabung.keluar.belot.buru", GabungConfig.get().pemburuHari)), List.of());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4F, 1.4F);
		pengikut(player, sekte, IKUT_SIKAP_PEMBELOT, IKUT_STANDING_PEMBELOT);
		return true;
	}

	// ================================================================== the Warisan (41.7.4)

	private static void simpanWarisan(Keanggotaan k, String sekte, boolean restu) {
		CompoundTag kel = k.bagian("keluar");
		if (!k.warisan.isEmpty()) {
			kel.putString("warisan." + sekte, k.warisan);
		}
		kel.putBoolean("restu." + sekte, restu);
	}

	/**
	 * Wipes the Teknik Warisan of {@code sekte} the player learned (only that spec; 41.7.4: a captured or surrendering
	 * deserter, when {@code gabungSekte.hapusWarisanTertangkap}): forgotten, taken off the loadout and the selection.
	 * True when something was wiped.
	 */
	public static boolean hapusWarisan(ServerPlayer player, String sekte) {
		Keanggotaan k = GabungApi.anggota(player);
		String spec = k.sekteAktif() != null && k.sekte.equals(sekte) ? k.warisan : k.bagian("keluar").getStringOr("warisan." + sekte, "");
		if (spec.isEmpty() || !GabungConfig.get().hapusWarisanTertangkap) {
			return false;
		}
		CultivationData data = Cultivators.get(player);
		if (!data.knows(spec)) {
			return false;
		}
		String[] slots = Loadout.slots(data);
		for (int i = 0; i < slots.length; i++) {
			if (spec.equals(slots[i])) {
				Loadout.set(player, i, "");
			}
		}
		if (spec.equals(data.selectedId())) {
			data.setSelectedId(null);
		}
		data.forget(spec);
		Cultivators.dirty(player);
		Cultivators.syncNow(player);
		k.bagian("keluar").remove("warisan." + sekte);
		GabungData.get(player.level().getServer()).setDirty();
		Msg.chat(player, Component.translatable("kultivator.gabung.keluar.warisan_dihapus", Pemeriksa.nama(sekte)).withStyle(ChatFormatting.DARK_RED));
		return true;
	}

	/** A player taught technique {@code spec} to an own disciple (from {@code Pemeriksa}): A6 or the 41.7.4 costs. */
	static void ajarWarisan(ServerPlayer master, String spec) {
		Keanggotaan k = GabungData.get(master.level().getServer()).peek(master.getUUID());
		if (k == null || spec == null || spec.isEmpty()) {
			return;
		}
		if (k.status.anggota() && spec.equals(k.warisan)) {
			GabungApi.langgar(master, Aturan.A6, master.blockPosition(), false);
			return;
		}
		CompoundTag kel = k.bagian("keluar");
		for (String key : List.copyOf(kel.keySet())) {
			if (!key.startsWith("warisan.") || !spec.equals(kel.getStringOr(key, ""))) {
				continue;
			}
			String sekte = key.substring("warisan.".length());
			if (kel.getBooleanOr("restu." + sekte, false)) {
				return;
			}
			kel.putInt("ajar." + sekte, kel.getIntOr("ajar." + sekte, 0) + 1);
			GabungData.get(master.level().getServer()).setDirty();
			SekteApi.diplomasi().adjust(master.getUUID(), sekte, AJAR_STANDING, "gabung.ajar_warisan");
			// 41.7.4 / D-41AD: every unblessed lesson sends one more hunter with the next squad
			dev.kultivator.sekte.gabung.politik.PemburuSekte.tambahRegu(master);
			Msg.chat(master, Component.translatable("kultivator.gabung.keluar.ajar_warisan", Pemeriksa.nama(sekte), AJAR_STANDING)
					.withStyle(ChatFormatting.RED));
			return;
		}
	}

	// ================================================================== ceremony, badge, residence, followers

	private static void upacara(ServerPlayer player, int klip) {
		GabungPayloads.kirimKlip(player, klip, 0);
		GabungPayloads.lepasKlipNanti(player, 80);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.7F, 0.8F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH, player.getX(), player.getY() + 1.0D, player.getZ(), 24,
				0.5D, 0.6D, 0.5D, 0.01D);
	}

	/**
	 * Takes back the sect's membership badges from the inventory: handed back ({@code retak} false) or, for an expulsion
	 * away from the grounds or a desertion, cracked: the membership component is removed and the badge is renamed
	 * "Giok Retak" (a keepsake).
	 */
	private static void ambilLencana(ServerPlayer player, String sekte, boolean retak) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			GabungItems.Anggota a = stack.isEmpty() ? null : stack.get(GabungItems.ANGGOTA);
			if (a == null || !sekte.equals(a.sekte()) || !player.getUUID().equals(a.pemilik())) {
				continue;
			}
			if (retak) {
				stack.remove(GabungItems.ANGGOTA);
				stack.set(DataComponents.CUSTOM_NAME, Component.translatable("kultivator.gabung.keluar.giok_retak", a.namaSekte())
						.withStyle(ChatFormatting.GRAY));
			} else {
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		inv.setChanged();
	}

	private static void lepasKediaman(ServerPlayer player, Keanggotaan k) {
		if (!k.kediaman.isEmpty() && CaveAbode.of(player) != null) {
			CaveAbode.clear(player);
		}
	}

	private static void beriRestu(ServerPlayer player, String sekte, Pangkat pangkat, long hari, Component namaSekte, String shifu) {
		ItemStack surat = new ItemStack(GabungItems.SURAT_RESTU);
		surat.set(GabungItems.RESTU, new GabungItems.Restu(sekte, pangkat.ordinal(), hari, namaSekte, shifu));
		if (!player.getInventory().add(surat)) {
			player.spawnAtLocation(player.level(), surat);
		}
	}

	/**
	 * 41.7.4: one roster friend of the old sect whose attitude reaches {@code sikapMin} follows the player as a candidate to
	 * his own hall (roster status MEMBELOT, "ikut kakaknya"); the old sect's standing drops by {@code standing}.
	 */
	private static void pengikut(ServerPlayer player, String sekte, int sikapMin, int standing) {
		MinecraftServer server = player.level().getServer();
		Halls.HallView hall = Halls.hallOf(player.getUUID());
		if (hall == null || !hall.active()) {
			return;
		}
		Roster.Tokoh best = null;
		for (Roster.Tokoh t : Roster.daftar(server, sekte)) {
			int sikap = Roster.sikap(server, t.id(), player.getUUID());
			if (t.aktif() && sikap >= sikapMin && (best == null || sikap > Roster.sikap(server, best.id(), player.getUUID()))) {
				best = t;
			}
		}
		if (best == null || !Roster.ikutPergi(server, best.id())) {
			return;
		}
		RandomSource random = player.getRandom();
		DiscipleData data = DiscipleData.fresh(random);
		data.setRank(best.rank());
		for (Realm realm : Realm.values()) {
			if (!realm.isMortal() && realm.ordinal() <= data.realm().ordinal()) {
				data.setQuality(realm, BreakthroughQuality.NORMAL);
			}
		}
		if (!best.dao().isEmpty()) {
			data.setDao(Dao.byId(best.dao()));
		}
		data.setComprehension(30 + random.nextInt(30));
		data.setQi(DiscipleCultivation.maxQi(data));
		Halls.addCandidates(player.getUUID(), List.of(new Halls.CandidateSpec(data, 85, -1.0D, -1.0D, true, "gabung_ikut",
				"kultivator.gabung.keluar.ikut.asal", 10)));
		SekteApi.diplomasi().adjust(player.getUUID(), sekte, standing, "gabung.ikut");
		Msg.chat(player, Component.translatable("kultivator.gabung.keluar.ikut", best.nama(), Pemeriksa.nama(sekte)).withStyle(ChatFormatting.GOLD));
	}

	private static void kartuPamit(ServerPlayer player, List<SyaratMasuk.Baris> syarat) {
		Keanggotaan k = GabungApi.anggota(player);
		List<Component> baris = new ArrayList<>();
		for (SyaratMasuk.Baris b : syarat) {
			baris.add(Component.literal(b.ok() ? "✔ " : "✘ ").append(b.teks()));
		}
		List<Component> chips = new ArrayList<>();
		if (perangMengikat(player.level().getServer(), k)) {
			baris.add(Component.translatable("kultivator.gabung.keluar.perang_mengikat"));
			chips.add(Kartu.chip(Component.translatable("kultivator.gabung.keluar.tombol.tinggalkan"), "/kultivasi gabung pamit tinggalkan",
					Component.translatable("kultivator.gabung.keluar.tombol.tinggalkan.hover"), ChatFormatting.DARK_RED));
		} else if (k.pangkat != Pangkat.KETUA) {
			chips.add(Kartu.chip(Component.translatable("kultivator.gabung.keluar.tombol.paksa"), "/kultivasi gabung pamit paksa",
					Component.translatable("kultivator.gabung.keluar.tombol.paksa.hover"), ChatFormatting.RED));
		}
		Kartu.kirim(player, Component.translatable("kultivator.gabung.keluar.syarat.judul"), baris, chips);
	}

	private static int ujiPamit(ServerPlayer player, boolean restu) {
		Keanggotaan k = GabungApi.anggota(player);
		if (!k.status.anggota()) {
			Msg.chat(player, Component.translatable("kultivator.gabung.uji.bukan_anggota"));
			return 0;
		}
		if (restu && k.ikatanShifu < RESTU_IKATAN_MIN) {
			k.ikatanShifu = RESTU_IKATAN_MIN;
		}
		if (restu && k.shifu.isEmpty()) {
			// the test needs a teacher to bless: the sect's first named elder stands in
			List<TokohSekte.Tokoh> tetua = TokohSekte.tetua(player.level().getServer(), k.sekte);
			if (!tetua.isEmpty()) {
				k.shifu = tetua.getFirst().id();
			}
		}
		GabungData.get(player.level().getServer()).setDirty();
		pamitHormat(player, k);
		return 1;
	}

	// ================================================================== R2: the founding veto (41.3.2-41.3.3)

	/**
	 * Why a new hall of {@code player} must start dormant, or null: a member (not a Tetua Tamu, not the master) whose hall
	 * is not a recognised branch of the sect (41D's {@code DataSekteGabung.cabang}).
	 */
	public static @Nullable Component vetoAlasan(ServerPlayer player) {
		if (!Gabung.aktif()) {
			return null;
		}
		Keanggotaan k = GabungData.get(player.level().getServer()).peek(player.getUUID());
		String sekte = k == null ? null : k.sekteAktif();
		if (sekte == null || k.pangkat == Pangkat.TAMU || k.pangkat == Pangkat.KETUA || Keanggotaan.JALUR_TAMU.equals(k.jalur)) {
			return null;
		}
		GabungData.DataSekteGabung d = GabungData.get(player.level().getServer()).peekSekte(sekte);
		if (d != null && d.cabang.contains(player.getUUID())) {
			return null;
		}
		return Component.translatable("kultivator.gabung.aula.veto", Pemeriksa.nama(sekte), k.pangkat.nama());
	}

	private static @Nullable Component vetoPendirian(ServerPlayer founder, ServerLevel level, BlockPos pos) {
		Component alasan = vetoAlasan(founder);
		if (alasan != null) {
			Keanggotaan k = GabungApi.anggota(founder);
			k.bagian("aula").putIntArray("stele", new int[]{pos.getX(), pos.getY(), pos.getZ()});
			GabungData.get(level.getServer()).setDirty();
			// the dialog comes after the hall exists (dormant), one tick later
			level.getServer().execute(() -> kartuAula(founder));
		}
		return alasan;
	}

	/** The R2 choices card. */
	public static void kartuAula(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		if (!k.status.anggota()) {
			return;
		}
		boolean bolehCabang = k.pangkat.tingkat() >= Pangkat.INTI.tingkat() && GabungConfig.get().bolehCabang;
		List<Component> chips = new ArrayList<>();
		chips.add(Kartu.chip(Component.translatable("kultivator.gabung.aula.tombol.pamit"), "/kultivasi gabung aula pamit",
				Component.translatable("kultivator.gabung.aula.tombol.pamit.hover"), ChatFormatting.GOLD));
		chips.add(Kartu.chip(Component.translatable("kultivator.gabung.aula.tombol.cabang"), "/kultivasi gabung aula cabang",
				Component.translatable(bolehCabang ? "kultivator.gabung.aula.tombol.cabang.hover" : "kultivator.gabung.aula.tombol.cabang.kunci",
						Pangkat.INTI.nama()), bolehCabang ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
		chips.add(Kartu.chip(Component.translatable("kultivator.gabung.aula.tombol.paksa"), "/kultivasi gabung aula paksa",
				Component.translatable("kultivator.gabung.aula.tombol.paksa.hover"), ChatFormatting.RED));
		chips.add(Kartu.chip(Component.translatable("kultivator.gabung.aula.tombol.batal"), "/kultivasi gabung aula batal",
				Component.translatable("kultivator.gabung.aula.tombol.batal.hover"), ChatFormatting.GRAY));
		Kartu.kirim(player, Component.translatable("kultivator.gabung.aula.judul"),
				List.of(Component.translatable("kultivator.gabung.aula.isi", Pemeriksa.nama(k.sekte), k.pangkat.nama())), chips);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> perintahAula() {
		return Commands.literal("aula")
				.executes(ctx -> {
					kartuAula(ctx.getSource().getPlayerOrException());
					return 1;
				})
				.then(Commands.literal("pamit").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					if (!pamit(p, false)) {
						return 0;
					}
					return bangunkanAula(p) ? 1 : 0;
				}))
				.then(Commands.literal("paksa").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					Keanggotaan k = GabungApi.anggota(p);
					if (!k.status.anggota() || k.pangkat == Pangkat.KETUA) {
						return 0;
					}
					pamitPaksa(p, "kultivator.gabung.keluar.sebab.dirikan");
					return bangunkanAula(p) ? 1 : 0;
				}))
				.then(Commands.literal("cabang").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					Keanggotaan k = GabungApi.anggota(p);
					if (!k.status.anggota()) {
						return 0;
					}
					if (k.pangkat.tingkat() < Pangkat.INTI.tingkat() || !GabungConfig.get().bolehCabang) {
						Msg.chat(p, Component.translatable("kultivator.gabung.aula.cabang_kunci", Pangkat.INTI.nama()).withStyle(ChatFormatting.GRAY));
						return 0;
					}
					PenanganCabang h = cabang;
					if (h == null) {
						Msg.chat(p, Component.translatable("kultivator.gabung.belum_tersedia").withStyle(ChatFormatting.GRAY));
						return 0;
					}
					return h.ajukan(p, k.sekte, steleAula(k)) ? 1 : 0;
				}))
				.then(Commands.literal("batal").executes(ctx -> {
					Msg.chat(ctx.getSource().getPlayerOrException(), Component.translatable("kultivator.gabung.aula.batal").withStyle(ChatFormatting.GRAY));
					return 1;
				}));
	}

	private static @Nullable BlockPos steleAula(Keanggotaan k) {
		int[] p = k.bagian("aula").getIntArray("stele").orElse(null);
		return p == null || p.length != 3 ? null : new BlockPos(p[0], p[1], p[2]);
	}

	/** Wakes the player's dormant hall at its stele after the veto is gone (Halls' own rules apply). */
	public static boolean bangunkanAula(ServerPlayer player) {
		Halls.HallView hall = Halls.hallOf(player.getUUID());
		if (hall == null || hall.active() || vetoAlasan(player) != null) {
			return false;
		}
		ServerLevel level = player.level().getServer().getLevel(hall.dim());
		if (level == null) {
			return false;
		}
		boolean ok = Halls.relocateHall(player, level, hall.pos());
		if (ok) {
			GabungApi.anggota(player).bagian("aula").remove("stele");
			GabungData.get(player.level().getServer()).setDirty();
		}
		return ok;
	}

	private Keluar() {
	}
}
