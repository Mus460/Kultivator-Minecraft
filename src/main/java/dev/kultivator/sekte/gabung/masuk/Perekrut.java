package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.dunia.tempat.kompleks.RencanaKompleks;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.StatusAnggota;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The recruiters' "Jasa" tablet (DESIGN 41.5.1, a {@code TempatDialog.PenyediaJasa}; no edit of {@code HariGerbang}): a
 * sect body the player talks to on the 38 {@code dialog} screen offers "Tanya tentang sekte" (the directory card,
 * {@code gabung_direktori}) and "Lamar" (the application, {@code gabung_lamaran}). Listing the tablet already makes the
 * sect known (talking to its recruiter tells all) and remembers it as the sect the screens show ({@link Lamaran#lihat}).
 * <p>
 * Who recruits for which sect:
 * <ul>
 * <li>the eight stall recruiters of the capital's Hari Gerbang ({@code MURID_SEKTE_DALAM} with {@code kb_acara =
 * hari_gerbang}; the sect is the one named on the stall's name plate);</li>
 * <li>any sect body ({@code MURID_SEKTE_LUAR/DALAM}, {@code TETUA_SEKTE}, {@code PENJAGA_GERBANG}, {@code JUARA_SEKTE},
 * {@code PELAYAN_SEKTE}) inside a sect compound or within 64 blocks of a sect gate, for that sect;</li>
 * <li>the <b>Kantor Sekte</b> of an ordinary town: a {@code MURID_SEKTE_DALAM}/{@code JUARA_SEKTE} body of a town speaks for
 * one of the three sects nearest the town, turning daily.</li>
 * </ul>
 */
public final class Perekrut {

	/** A sect body this close to a gate speaks for that sect. */
	public static final double JARI_GERBANG = 64.0D;
	/** The town office turns among this many nearest sects. */
	public static final int KANTOR_GILIR = 3;

	static void init() {
		CeritaApi.daftarJasa(Perekrut::untuk);
	}

	private static List<TempatDialog.JasaEntri> untuk(ServerPlayer player, KultivatorNpc npc) {
		List<TempatDialog.JasaEntri> out = new ArrayList<>();
		if (!Gabung.aktif()) {
			return out;
		}
		String sekte = sekteDari(player.level().getServer(), npc);
		if (sekte == null || SekteApi.diplomasi().tier(sekte) <= 0) {
			return out;
		}
		Lamaran.lihat(player, sekte);
		GabungApi.kenal(player, sekte);
		Keanggotaan k = GabungApi.anggota(player);
		Component nama = Masuk.nama(sekte);
		out.add(new TempatDialog.JasaEntri(GabungLembar.Direktori.LAYAR, Masuk.t("jasa.tanya", nama), LembarKunci.Ikon.MATA_BATIN,
				Masuk.t("jasa.tanya_catatan", GabungApi.kelasSekte(sekte).nama(), GabungApi.corak(sekte).nama()), false, null));
		if (k.anggotaDi(sekte)) {
			return out;
		}
		boolean lain = k.status.anggota() || k.status == StatusAnggota.CALON && !sekte.equals(k.sekte);
		Component catatan = k.status == StatusAnggota.CALON && sekte.equals(k.sekte)
				? Masuk.t("jasa.lamar_calon", Math.max(0L, k.hariUjian - GabungApi.hari(player.level().getServer())))
				: Masuk.t("jasa.lamar_catatan", HariUjian.berikutUntuk(player.level().getServer(), sekte));
		out.add(new TempatDialog.JasaEntri(GabungLembar.Lamaran.LAYAR, Masuk.t("jasa.lamar"), LembarKunci.Ikon.CAP_MERAH, catatan, lain,
				lain ? Masuk.t("jasa.lamar_kunci", Masuk.nama(k.sekte)) : null));
		return out;
	}

	/** The sect {@code npc} speaks for, or null. */
	public static @Nullable String sekteDari(MinecraftServer server, KultivatorNpc npc) {
		String mem = npc.memory().getStringOr(Masuk.MEM_SEKTE, "");
		if (!mem.isEmpty()) {
			return mem;
		}
		NpcRole role = npc.role();
		DiplomasiService d = SekteApi.diplomasi();
		if (role == NpcRole.MURID_SEKTE_DALAM && LembarKunci.Acara.ACARA_HARI_GERBANG.equals(npc.memory().getStringOr("kb_acara", ""))) {
			String stall = dariPapanNama(npc);
			if (stall != null) {
				return stall;
			}
		}
		if (!sektePeran(role)) {
			return null;
		}
		BlockPos pos = npc.blockPosition();
		RencanaKompleks.Kompleks k = KompleksApi.kompleksDi(npc.level() instanceof net.minecraft.server.level.ServerLevel sl ? sl : server.overworld(), pos);
		if (k != null) {
			return k.sekte();
		}
		String near = d.nearest(pos, -1);
		if (near != null) {
			BlockPos gate = d.gate(near) != null ? d.gate(near) : d.plannedGate(near);
			if (gate != null && Masuk.datar(npc.position(), gate) <= JARI_GERBANG) {
				return near;
			}
		}
		if ((role == NpcRole.MURID_SEKTE_DALAM || role == NpcRole.JUARA_SEKTE) && npc.town() != 0L) {
			return kantor(server, pos);
		}
		return null;
	}

	private static boolean sektePeran(NpcRole role) {
		return role == NpcRole.MURID_SEKTE_LUAR || role == NpcRole.MURID_SEKTE_DALAM || role == NpcRole.TETUA_SEKTE
				|| role == NpcRole.PENJAGA_GERBANG || role == NpcRole.JUARA_SEKTE || role == NpcRole.PELAYAN_SEKTE;
	}

	/** The Hari Gerbang stall's sect: the name on the plate {@code kultivator.kota_besar.hari_gerbang.perekrut}. */
	private static @Nullable String dariPapanNama(KultivatorNpc npc) {
		Component plate = npc.plate();
		if (plate == null || !(plate.getContents() instanceof TranslatableContents tc) || tc.getArgs().length == 0) {
			return null;
		}
		Object arg = tc.getArgs()[0];
		String shown = arg instanceof Component c ? c.getString() : String.valueOf(arg);
		for (String s : SekteApi.diplomasi().sects()) {
			if (Masuk.nama(s).getString().equals(shown)) {
				return s;
			}
		}
		return null;
	}

	/** The Kantor Sekte's sect of the day: one of the three nearest sects, turning daily. */
	private static @Nullable String kantor(MinecraftServer server, BlockPos pos) {
		DiplomasiService d = SekteApi.diplomasi();
		List<String> sects = new ArrayList<>();
		for (String s : d.sects()) {
			BlockPos gate = d.gate(s) != null ? d.gate(s) : d.plannedGate(s);
			if (gate != null && d.tier(s) > 0) {
				sects.add(s);
			}
		}
		if (sects.isEmpty()) {
			return null;
		}
		sects.sort(Comparator.comparingDouble(s -> {
			BlockPos g = d.gate(s) != null ? d.gate(s) : d.plannedGate(s);
			return g == null ? Double.MAX_VALUE : g.distSqr(pos);
		}));
		int n = Math.min(KANTOR_GILIR, sects.size());
		return sects.get((int) Math.floorMod(Kota.day(server), (long) n));
	}

	private Perekrut() {
	}
}
