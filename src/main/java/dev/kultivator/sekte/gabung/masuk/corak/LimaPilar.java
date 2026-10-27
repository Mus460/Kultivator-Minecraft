package dev.kultivator.sekte.gabung.masuk.corak;

import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sekte.gabung.masuk.GabungEntities;
import dev.kultivator.sekte.gabung.masuk.PilarQiEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * UNSUR "Lima Pilar Qi" (DESIGN 41.5.4; the five phases, 五行): five carved pillars ({@link PilarQiEntity}) stand on
 * titik {@code pilar_0..4} (else in a ring of 4.5 blocks). Visit them in the generating cycle Kayu → Api → Tanah → Logam →
 * Air ("wood feeds fire, fire makes earth, earth bears metal, metal gathers water"). At each pillar keep the right mouse
 * button on it: holding pushes the Qi needle up, letting go lets it sink, and the needle drifts by itself; keep it inside
 * the narrow band (which wanders) for 15 s. The band narrows with the sect's class (22 % of the bar at Kecil, 12 % at
 * Abadi). The pillar's light shows the accuracy; a pillar at &ge; 50 % ends lit, below it ends cracked grey.
 * <p>
 * {@code S2} = the mean accuracy of the five pillars (a pillar never started counts 0).
 */
public final class LimaPilar extends UjianDasar {

	/** Wu Xing generating order of the visits (element ids of {@link PilarQiEntity}). */
	public static final int[] URUTAN = {PilarQiEntity.KAYU, PilarQiEntity.API, PilarQiEntity.TANAH, PilarQiEntity.LOGAM, PilarQiEntity.AIR};
	public static final int TICK_PILAR = 15 * 20;
	private static final double DEKAT = 2.6D;
	private static final double JARI = 4.5D;
	/** The use key repeats every 4 ticks on the client; 5 ticks bridge the gap with one tick of slack. */
	private static final int TAHAN_SISA = 5;
	/**
	 * Needle physics (per tick): the pull while held, the sink while not, the damping. The terminal speed is about 0.4 of
	 * the bar per second, so the lag of the key repeat (5 ticks, 0.1 of the bar) stays inside even the narrowest band
	 * (12 %, so 6 % either side) with a steady hand.
	 */
	private static final double TARIK = 0.0022D;
	private static final double TURUN = 0.0020D;
	private static final double REDAM = 0.90D;

	private final PilarQiEntity[] pilar = new PilarQiEntity[5];
	private final double[] ketepatan = new double[5];
	private int aktif;
	private boolean berjalan;
	private int tickPilar;
	private int diDalam;
	private int tahanTerakhir = -100;
	private double jarum = 0.3D;
	private double laju;
	private double pita = 0.5D;
	private final double lebar;
	private final double fase1;
	private final double fase2;

	public LimaPilar(ServerPlayer pemain, String sekte, int kelas, Vec3 pusat, long benih) {
		super(pemain, sekte, Corak.UNSUR, kelas, pusat, benih);
		this.lebar = 0.22D - 0.025D * (this.kelas - 1);
		Random r = new Random(benih);
		this.fase1 = r.nextDouble() * Math.PI * 2.0D;
		this.fase2 = r.nextDouble() * Math.PI * 2.0D;
	}

	@Override
	public int jedaHud() {
		return berjalan ? 2 : 5;
	}

	@Override
	public @Nullable Component siapkan() {
		if (sisaAnggaran() < 5) {
			return t("anggaran_kurang", 5);
		}
		Vec3[] spot = new Vec3[5];
		boolean dariTitik = true;
		for (int i = 0; i < 5; i++) {
			spot[i] = titik(TitikGabung.PILAR + i);
			if (spot[i] == null) {
				dariTitik = false;
			}
			for (int j = 0; j < i && dariTitik; j++) {
				if (sama(spot[i], spot[j])) {
					dariTitik = false;
				}
			}
		}
		for (int i = 0; i < 5; i++) {
			double a = Math.PI * 2.0D * i / 5.0D - Math.PI / 2.0D;
			Vec3 at = dariTitik ? spot[i] : dariPusat(Math.cos(a) * JARI, Math.sin(a) * JARI);
			PilarQiEntity p = GabungEntities.PILAR_QI.create(level, EntitySpawnReason.EVENT);
			if (p == null) {
				return t("tidak_bisa");
			}
			p.snapTo(at.x, at.y, at.z, yaw(at, pusat), 0.0F);
			p.setUnsur(URUTAN[i]);
			p.setFase(PilarQiEntity.FASE_DIAM);
			p.setPendengar(this::ditahan);
			if (!muncul(p)) {
				return t("anggaran_kurang", 5);
			}
			pilar[i] = p;
		}
		chat(t(ChatFormatting.GOLD, "lima_pilar.mulai", unsur(0)));
		return null;
	}

	private static Component unsur(int i) {
		return t("unsur." + URUTAN[i]);
	}

	private void ditahan(PilarQiEntity p, ServerPlayer player) {
		if (selesai() || !player.getUUID().equals(uuid()) || aktif >= 5 || p != pilar[aktif]) {
			if (!selesai() && aktif < 5 && p != pilar[aktif] && player.getUUID().equals(uuid())) {
				bar(t(ChatFormatting.YELLOW, "lima_pilar.bukan", unsur(aktif)));
			}
			return;
		}
		if (!berjalan) {
			if (player.position().distanceTo(p.position()) > DEKAT + 1.0D) {
				bar(t(ChatFormatting.YELLOW, "lima_pilar.dekati"));
				return;
			}
			berjalan = true;
			tickPilar = 0;
			diDalam = 0;
			jarum = 0.3D;
			laju = 0.0D;
			p.setFase(PilarQiEntity.FASE_AKTIF);
			bunyi(p.position(), SoundEvents.BEACON_ACTIVATE, 0.6F, 1.2F + 0.1F * aktif);
		}
		tahanTerakhir = umur;
	}

	@Override
	public void tik() {
		for (PilarQiEntity p : pilar) {
			if (p == null || p.isRemoved()) {
				akhiri(rata(), "rusak", true);
				return;
			}
		}
		if (!berjalan || aktif >= 5) {
			return;
		}
		PilarQiEntity p = pilar[aktif];
		boolean tahan = umur - tahanTerakhir <= TAHAN_SISA && pemain.position().distanceTo(p.position()) <= DEKAT + 1.5D;
		double detik = tickPilar / 20.0D;
		// the band wanders slowly (its peak speed is 0.3 of the bar per second, under the needle's 0.4)
		pita = Mth.clamp(0.5D + 0.24D * Math.sin(fase1 + detik * 0.6D + aktif) + 0.10D * Math.sin(fase2 + detik * 1.5D), lebar / 2.0D, 1.0D - lebar / 2.0D);
		Random r = acak();
		laju += tahan ? TARIK : -TURUN;
		laju *= REDAM;
		jarum += laju + (r.nextDouble() - 0.5D) * 0.008D;
		if (jarum <= 0.0D || jarum >= 1.0D) {
			jarum = Mth.clamp(jarum, 0.0D, 1.0D);
			laju = 0.0D;
		}
		tickPilar++;
		if (Math.abs(jarum - pita) <= lebar / 2.0D) {
			diDalam++;
		}
		ketepatan[aktif] = diDalam / (double) TICK_PILAR;
		if (tickPilar % 5 == 0) {
			p.setNyala((float) (diDalam / (double) Math.max(1, tickPilar)));
		}
		skor = rata();
		if (tickPilar >= TICK_PILAR) {
			boolean baik = ketepatan[aktif] >= 0.5D;
			p.setFase(baik ? PilarQiEntity.FASE_LULUS : PilarQiEntity.FASE_GAGAL);
			p.setNyala((float) ketepatan[aktif]);
			bunyi(p.position(), baik ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.CHAIN_BREAK, 1.0F, 0.8F + (float) ketepatan[aktif] * 0.6F);
			int persen = (int) Math.round(ketepatan[aktif] * 100.0D);
			berjalan = false;
			aktif++;
			if (aktif >= 5) {
				akhiri(rata(), "tuntas", false);
				return;
			}
			bar(t(ChatFormatting.AQUA, "lima_pilar.selesai", persen, unsur(aktif)));
		}
	}

	private double rata() {
		double sum = 0.0D;
		for (double k : ketepatan) {
			sum += k;
		}
		return sum / 5.0D;
	}

	@Override
	public void waktuHabis() {
		akhiri(rata(), "waktu", false);
	}

	@Override
	public void bersihkan() {
		for (PilarQiEntity p : pilar) {
			if (p != null) {
				p.setPendengar(null);
			}
		}
		super.bersihkan();
	}

	@Override
	public int isiData(byte[] d) {
		d[1] = n(Math.min(aktif, 4));
		d[2] = b(jarum);
		d[3] = b(pita);
		d[4] = b(lebar);
		d[5] = b(aktif < 5 ? ketepatan[aktif] : 0.0D);
		d[6] = n(umur - tahanTerakhir <= TAHAN_SISA ? 1 : 0);
		for (int i = 0; i < 5; i++) {
			d[7 + i] = b(ketepatan[i]);
		}
		d[12] = n(berjalan ? tickPilar / 20 : 0);
		return 12;
	}
}
