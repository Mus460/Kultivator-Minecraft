package dev.kultivator.sekte.gabung.masuk.corak;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * BAYANGAN "Ujian Senyap" (DESIGN 41.5.4; the shadow sects' silent test): a jade task token lies on the elders' table
 * ({@code kompleks/tetua} at night; by day the dark {@code gua_renungan}). Three gate guards stand around it, each sweeping
 * its gaze slowly left and right (a 60° cone, 12 blocks in the dark, 16 in daylight; line of sight through blocks
 * only). Take the jade and carry it out of the guarded ring (14 blocks) unseen. Crouching halves the guards' reach,
 * invisibility quarters it. A guard who sees the candidate long enough (an alert meter, faster when near) calls out; the
 * guards then look elsewhere for 3 s. Three sightings fail the test.
 * <p>
 * {@code S2} 1.0 unseen, -0.34 per sighting (3 = 0).
 */
public final class UjianSenyap extends UjianDasar {

	public static final int PENJAGA = 3;
	public static final double LINGKAR = 14.0D;
	private static final double JARI_PENJAGA = 5.0D;
	private static final double KERUCUT = Math.toRadians(30.0D);
	private static final double SAPU = 75.0D;
	private static final int TENANG_TICK = 60;

	private final KultivatorNpc[] penjaga = new KultivatorNpc[PENJAGA];
	private final float[] arahDasar = new float[PENJAGA];
	private final float[] arahKini = new float[PENJAGA];
	private final double[] periode = new double[PENJAGA];
	private final double[] fase = new double[PENJAGA];
	private final double[] awas = new double[PENJAGA];
	private @Nullable ItemEntity giok;
	private boolean diambil;
	private boolean gelap;
	private int terlihat;
	private int tenang;

	public UjianSenyap(ServerPlayer pemain, String sekte, int kelas, Vec3 pusat, long benih) {
		super(pemain, sekte, Corak.BAYANGAN, kelas, pusat, benih);
	}

	@Override
	public @Nullable Component siapkan() {
		if (sisaAnggaran() < PENJAGA + 1) {
			return t("anggaran_kurang", PENJAGA + 1);
		}
		Vec3 meja = pusat;
		if (meja.distanceTo(pemain.position()) < LINGKAR + 2.0D) {
			// the candidate starts outside the ring: the table moves away from them, along their gaze
			Vec3 look = Vec3.directionFromRotation(0.0F, pemain.getYRot());
			meja = tanah(pemain.getX() + look.x * (LINGKAR + 4.0D), pemain.getY(), pemain.getZ() + look.z * (LINGKAR + 4.0D));
		}
		gelap = level.isDarkOutside() || level.getMaxLocalRawBrightness(BlockPos.containing(meja).above()) < 8;
		ItemStack token = itemUjian(new ItemStack(Items.EMERALD), "giok", t(ChatFormatting.GREEN, "ujian_senyap.giok"),
				t(ChatFormatting.GRAY, "ujian_senyap.giok_lore"));
		ItemEntity e = new ItemEntity(level, meja.x, meja.y + 0.2D, meja.z, token, 0.0D, 0.0D, 0.0D);
		e.setTarget(uuid());
		e.setNoPickUpDelay();
		e.setUnlimitedLifetime();
		if (!muncul(e)) {
			return t("anggaran_kurang", PENJAGA + 1);
		}
		giok = e;
		Random r = new Random(benih);
		for (int i = 0; i < PENJAGA; i++) {
			double a = Math.PI * 2.0D * i / PENJAGA + r.nextDouble() * 0.6D;
			Vec3 at = tanah(meja.x + Math.cos(a) * JARI_PENJAGA, meja.y, meja.z + Math.sin(a) * JARI_PENJAGA);
			// facing outward, away from the table
			arahDasar[i] = yaw(meja, at);
			arahKini[i] = arahDasar[i];
			periode[i] = 5.5D + 1.5D * i + r.nextDouble();
			fase[i] = r.nextDouble() * Math.PI * 2.0D;
			KultivatorNpc g = tubuh(NpcRole.PENJAGA_GERBANG, at, arahDasar[i], realmLawan(), t("ujian_senyap.penjaga"), null);
			if (g == null) {
				return t("anggaran_kurang", PENJAGA + 1);
			}
			g.memory().putBoolean("hold", true);
			g.setDiamDiTitik(true);
			penjaga[i] = g;
		}
		this.pusatMeja = meja;
		chat(t(ChatFormatting.DARK_AQUA, gelap ? "ujian_senyap.mulai" : "ujian_senyap.mulai_siang", (int) LINGKAR));
		return null;
	}

	private Vec3 pusatMeja = Vec3.ZERO;

	@Override
	public Vec3 pusatWilayah() {
		return pusatMeja;
	}

	@Override
	public double jariWilayah() {
		return LINGKAR + 26.0D;
	}

	@Override
	public void tik() {
		for (KultivatorNpc g : penjaga) {
			if (g == null || g.isRemoved()) {
				akhiri(Math.max(0.0D, 1.0D - 0.34D * terlihat), "rusak", true);
				return;
			}
		}
		if (!diambil) {
			if (hitungItem("giok") > 0) {
				diambil = true;
				bunyi(pemain.position(), SoundEvents.EXPERIENCE_ORB_PICKUP, 0.3F, 0.6F);
				bar(t(ChatFormatting.AQUA, "ujian_senyap.diambil", (int) LINGKAR));
			} else if (giok == null || giok.isRemoved()) {
				akhiri(0.0D, "rusak", true);
				return;
			}
		} else if (hitungItem("giok") == 0) {
			bar(t(ChatFormatting.RED, "ujian_senyap.hilang"));
			akhiri(0.0D, "rusak", true);
			return;
		}
		double detik = umur / 20.0D;
		for (int i = 0; i < PENJAGA; i++) {
			KultivatorNpc g = penjaga[i];
			float y = arahDasar[i] + (float) (SAPU * Math.sin(fase[i] + detik * Math.PI * 2.0D / periode[i]));
			arahKini[i] = y;
			g.setYRot(y);
			g.setYHeadRot(y);
			g.setYBodyRot(y);
		}
		if (tenang > 0) {
			tenang--;
		} else {
			lihat();
		}
		skor = Math.max(0.0D, 1.0D - 0.34D * terlihat);
		if (diambil && pemain.position().distanceTo(pusatMeja) > LINGKAR) {
			bunyi(pemain.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.4F);
			akhiri(skor, "tuntas", false);
		}
	}

	/** The guards' cones: fill each alert meter while the candidate is inside a cone in line of sight. */
	private void lihat() {
		double jangkau = gelap ? 12.0D : 16.0D;
		if (pemain.isShiftKeyDown()) {
			jangkau *= 0.5D;
		}
		if (pemain.hasEffect(MobEffects.INVISIBILITY)) {
			jangkau *= 0.25D;
		}
		Vec3 mataPemain = pemain.getEyePosition();
		for (int i = 0; i < PENJAGA; i++) {
			KultivatorNpc g = penjaga[i];
			Vec3 mata = g.getEyePosition();
			Vec3 ke = mataPemain.subtract(mata);
			double jarak = ke.length();
			boolean lihat = false;
			if (jarak < 1.6D) {
				lihat = true;
				awas[i] = 1.0D;
			} else if (jarak <= jangkau) {
				Vec3 hadap = Vec3.directionFromRotation(0.0F, arahKini[i]);
				double cos = (hadap.x * ke.x + hadap.z * ke.z) / Math.max(1.0E-4D, Math.sqrt(ke.x * ke.x + ke.z * ke.z));
				if (cos >= Math.cos(KERUCUT) && g.hasLineOfSight(pemain)) {
					lihat = true;
					awas[i] += (1.2D - jarak / jangkau) * 0.08D;
				}
			}
			if (!lihat) {
				awas[i] = Math.max(0.0D, awas[i] - 0.02D);
			}
			if (awas[i] >= 1.0D) {
				tertangkap(i);
				return;
			}
		}
	}

	private void tertangkap(int i) {
		terlihat++;
		java.util.Arrays.fill(awas, 0.0D);
		tenang = TENANG_TICK;
		KultivatorNpc g = penjaga[i];
		HidupApi.klip(g, GabungGerakId.PENJAGA_MEMANGGIL, 0, 40);
		g.say(t("ujian_senyap.seru." + Math.floorMod(terlihat - 1, 3)), 60);
		bunyi(g.position(), SoundEvents.BELL_BLOCK, 1.0F, 1.4F);
		if (terlihat >= 3) {
			chat(t(ChatFormatting.RED, "ujian_senyap.gagal"));
			akhiri(0.0D, "terlihat", true);
		} else {
			bar(t(ChatFormatting.RED, "ujian_senyap.terlihat", terlihat, 3));
		}
	}

	@Override
	public void waktuHabis() {
		akhiri(0.0D, "waktu", false);
	}

	@Override
	public int isiData(byte[] d) {
		double maks = 0.0D;
		double dekat = 255.0D;
		for (int i = 0; i < PENJAGA; i++) {
			maks = Math.max(maks, awas[i]);
			if (penjaga[i] != null) {
				dekat = Math.min(dekat, penjaga[i].distanceTo(pemain));
			}
		}
		d[1] = n(terlihat);
		d[2] = n(diambil ? 1 : 0);
		d[3] = b(Mth.clamp(maks, 0.0D, 1.0D));
		d[4] = n(gelap ? 1 : 0);
		d[5] = n((int) dekat);
		return 5;
	}
}
