package dev.kultivator.sekte.gabung.masuk.corak;

import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * DARAH "Kolam Darah" (DESIGN 41.5.4; the blood sects' pool, as Han Li saw the Blood Prohibited Ground): stand in the
 * blood pool ({@code kompleks/ujian_darah}, radius 3) while it drinks one health every 2 s (down to 30 % of the maximum,
 * never to death), and survive three waves of
 * demonic rogue cultivators ({@link RogueCultivators#VARIANT_DEMONIC}, tiers by the sect's class, realm foes). Out of the
 * pool more than 3 s fails the test. The offering: crouch still for 1 s to cut the palm, losing 20 % health for Strength II
 * for 15 s (once per 15 s; never below 1 health). Dying (the death guard) fails as usual.
 * <p>
 * {@code S2 = waves cleared / 3}; a candidate who cleared at least one wave gains Infami +10 (the blood sect's mark).
 */
public final class KolamDarah extends UjianDasar {

	public static final int GELOMBANG = 3;
	public static final double JARI_KOLAM = 3.0D;
	private static final int KURAS_TICK = 40;
	private static final int LUAR_MAKS = 60;
	private static final int KORBAN_TICK = 20;
	private static final int KUAT_TICK = 15 * 20;
	/** The drain never takes the candidate below this share of the maximum health. */
	private static final float LANTAI = 0.30F;
	/** Rogue tiers per wave by class 1-5. */
	private static final int[][][] TIER = {
			{{1, 1}, {1, 1}, {1, 2}},
			{{1, 2}, {2, 2}, {2, 2}},
			{{2, 2}, {2, 3}, {3, 3}},
			{{3, 3}, {3, 3}, {3, 4}},
			{{3, 4}, {4, 4}, {4, 4}}};

	private final List<Mob> musuh = new ArrayList<>();
	private int gelombang;
	private int lolos;
	private int jeda = 60;
	private boolean antara = true;
	private int luar;
	private int jongkok;
	private int kuatSampai;
	private boolean infamiDiberi;

	public KolamDarah(ServerPlayer pemain, String sekte, int kelas, Vec3 pusat, long benih) {
		super(pemain, sekte, Corak.DARAH, kelas, pusat, benih);
	}

	@Override
	public @Nullable Component siapkan() {
		if (sisaAnggaran() < 2) {
			return t("anggaran_kurang", 2);
		}
		chat(t(ChatFormatting.DARK_RED, "kolam_darah.mulai"));
		bunyi(pusat, SoundEvents.WARDEN_HEARTBEAT, 1.2F, 0.8F);
		return null;
	}

	private boolean diKolam() {
		double dx = pemain.getX() - pusat.x;
		double dz = pemain.getZ() - pusat.z;
		return dx * dx + dz * dz <= JARI_KOLAM * JARI_KOLAM && Math.abs(pemain.getY() - pusat.y) <= 2.0D;
	}

	@Override
	public void tik() {
		boolean di = diKolam();
		if (!di) {
			if (luar == 0) {
				bar(t(ChatFormatting.RED, "kolam_darah.kembali"));
			}
			if (++luar > LUAR_MAKS) {
				akhiri(lolos / (double) GELOMBANG, "kolam", true);
				return;
			}
		} else {
			luar = 0;
			// the pool drinks but does not kill: it stops at 30 % health (the waves are the test, not the drain)
			if (umur % KURAS_TICK == 0 && pemain.getHealth() > Math.max(1.0F, pemain.getMaxHealth() * LANTAI)) {
				pemain.hurtServer(level, level.damageSources().magic(), 1.0F);
				if (umur % (KURAS_TICK * 3) == 0) {
					bunyi(pemain.position(), SoundEvents.WARDEN_HEARTBEAT, 0.7F, 1.0F);
				}
			}
		}
		korban(di);
		musuh.removeIf(m -> m.isRemoved() || m.isDeadOrDying());
		if (!antara && musuh.isEmpty()) {
			lolos = gelombang;
			skor = lolos / (double) GELOMBANG;
			antara = true;
			jeda = 60;
			tandaInfami();
			if (lolos >= GELOMBANG) {
				bunyi(pemain.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 0.6F);
				akhiri(1.0D, "tuntas", false);
				return;
			}
			bar(t(ChatFormatting.GOLD, "kolam_darah.lolos", lolos, GELOMBANG));
		}
		if (antara && --jeda <= 0) {
			gelombangBerikut();
		}
		for (Mob m : musuh) {
			if (m.getTarget() == null) {
				m.setTarget(pemain);
			}
		}
	}

	/** Crouching still for 1 s in the pool: the blood offering. */
	private void korban(boolean di) {
		boolean diam = pemain.getDeltaMovement().horizontalDistanceSqr() < 0.0025D;
		if (di && pemain.isShiftKeyDown() && diam && umur >= kuatSampai) {
			if (++jongkok >= KORBAN_TICK) {
				jongkok = 0;
				float potong = pemain.getMaxHealth() * 0.2F;
				if (pemain.getHealth() - potong < 1.0F) {
					bar(t(ChatFormatting.RED, "kolam_darah.terlalu_lemah"));
					kuatSampai = umur + 60;
					return;
				}
				pemain.setHealth(pemain.getHealth() - potong);
				pemain.addEffect(new MobEffectInstance(MobEffects.STRENGTH, KUAT_TICK, 1));
				kuatSampai = umur + KUAT_TICK;
				bunyi(pemain.position(), SoundEvents.PLAYER_HURT, 0.9F, 0.6F);
				bar(t(ChatFormatting.DARK_RED, "kolam_darah.korban"));
			}
		} else {
			jongkok = 0;
		}
	}

	private void gelombangBerikut() {
		int[] tiers = TIER[kelas - 1][Math.min(gelombang, GELOMBANG - 1)];
		if (sisaAnggaran() < tiers.length) {
			jeda = 40;
			if (umur % 100 == 0) {
				bar(t(ChatFormatting.YELLOW, "kolam_darah.tunggu"));
			}
			return;
		}
		gelombang++;
		antara = false;
		BlockPos rumah = BlockPos.containing(pusat);
		for (int i = 0; i < tiers.length; i++) {
			double a = Math.PI * 2.0D * (i + gelombang * 0.37D) / tiers.length;
			Vec3 at = dariPusat(Math.cos(a) * 7.0D, Math.sin(a) * 7.0D);
			Mob m = RogueCultivators.spawn(level, at, tiers[i], realmLawan(), RogueCultivators.VARIANT_DEMONIC, RogueCultivators.ROLE_TEST, rumah);
			if (m == null) {
				continue;
			}
			TechniquePower.markRealmFoe(m);
			m.setTarget(pemain);
			ikut(m);
			musuh.add(m);
		}
		if (musuh.isEmpty()) {
			// the world refused every rogue (Peaceful, cap): the wave cannot be fought, it counts as passed
			lolos = gelombang;
			skor = lolos / (double) GELOMBANG;
			antara = true;
			jeda = 100;
			if (lolos >= GELOMBANG) {
				akhiri(skor, "tuntas", false);
			}
			return;
		}
		bunyi(pusat, SoundEvents.WITHER_SHOOT, 0.7F, 0.5F);
		bar(t(ChatFormatting.DARK_RED, "kolam_darah.gelombang", gelombang, GELOMBANG));
	}

	private void tandaInfami() {
		if (!infamiDiberi && lolos >= 1) {
			infamiDiberi = true;
			TemaApi.tambahInfami(pemain, 10, "gabung_darah");
		}
	}

	@Override
	public void waktuHabis() {
		tandaInfami();
		akhiri(lolos / (double) GELOMBANG, "waktu", false);
	}

	@Override
	public int isiData(byte[] d) {
		d[1] = n(gelombang);
		d[2] = n(musuh.size());
		d[3] = b(pemain.getHealth() / Math.max(1.0F, pemain.getMaxHealth()));
		d[4] = n(umur >= kuatSampai ? 1 : 0);
		d[5] = n(Math.max(0, (kuatSampai - umur) / 20));
		d[6] = n(diKolam() ? 1 : 0);
		return 6;
	}
}
