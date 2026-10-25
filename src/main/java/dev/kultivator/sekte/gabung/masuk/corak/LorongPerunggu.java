package dev.kultivator.sekte.gabung.masuk.corak;

import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * BUDDHA "Lorong Manusia Perunggu" (DESIGN 41.5.4; Shaolin's Bronze Men corridor, 十八铜人): three waves of two bronze
 * monks ({@code KultivatorNpc} {@code TETUA_SEKTE} with the look {@code biksu_perunggu}, realm by class) bar the corridor
 * ({@code kompleks/lorong}). They fight under duel rules: a monk never dies, at 10 % health he kneels (clip
 * {@code lelah_berlutut}) and is out. A wave passes when both kneel; the next steps in after 3 s, at most 2 monks stand at
 * once (41.12.3).
 * <p>
 * The vow: <b>killing any creature during the test fails it at once</b> (S2 0), by the player's hand, arrow or spirit
 * beast. {@code S2 = waves passed / 3}, minus 0.2 when the player ends below 30 % health.
 */
public final class LorongPerunggu extends UjianDasar {

	public static final int GELOMBANG = 3;
	public static final int PER_GELOMBANG = 2;
	private static final float ROBOH = 0.10F;
	private static final int JEDA_GELOMBANG = 60;
	public static final String RUPA = "biksu_perunggu";

	private final List<KultivatorNpc> biksu = new ArrayList<>();
	private final List<KultivatorNpc> roboh = new ArrayList<>();
	private Vec3 arah = new Vec3(0.0D, 0.0D, 1.0D);
	private Vec3 awal;
	private int gelombang;
	private int lolos;
	private int jeda = 40;
	private boolean sumpahPatah;
	/** Between two waves (the corridor is empty on purpose). */
	private boolean antara = true;

	public LorongPerunggu(ServerPlayer pemain, String sekte, int kelas, Vec3 pusat, long benih) {
		super(pemain, sekte, Corak.BUDDHA, kelas, pusat, benih);
		this.awal = pemain.position();
	}

	@Override
	public @Nullable Component siapkan() {
		if (sisaAnggaran() < PER_GELOMBANG) {
			return t("anggaran_kurang", PER_GELOMBANG);
		}
		Vec3 d = new Vec3(pusat.x - pemain.getX(), 0.0D, pusat.z - pemain.getZ());
		if (d.lengthSqr() < 4.0D) {
			d = Vec3.directionFromRotation(0.0F, pemain.getYRot()).multiply(1.0D, 0.0D, 1.0D);
		}
		arah = d.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : d.normalize();
		awal = pemain.position();
		chat(t(ChatFormatting.GOLD, "lorong_perunggu.mulai"));
		return null;
	}

	@Override
	public void tik() {
		biksu.removeIf(Entity::isRemoved);
		if (gelombang > 0 && !antara && biksu.isEmpty()) {
			// a monk vanished (unloaded, despawned): the wave counts as not passed, the corridor reopens
			akhiri(lolos / (double) GELOMBANG, "rusak", true);
			return;
		}
		if (gelombang > 0 && roboh.size() >= PER_GELOMBANG && biksu.stream().allMatch(roboh::contains)) {
			lolos = gelombang;
			skor = lolos / (double) GELOMBANG;
			bunyi(pemain.position(), SoundEvents.BELL_BLOCK, 1.0F, 0.8F + 0.1F * lolos);
			if (lolos >= GELOMBANG) {
				akhiri(nilai(), "tuntas", false);
				return;
			}
			bar(t(ChatFormatting.GREEN, "lorong_perunggu.lolos", lolos, GELOMBANG));
			for (KultivatorNpc b : biksu) {
				b.discard();
			}
			biksu.clear();
			roboh.clear();
			antara = true;
			jeda = JEDA_GELOMBANG;
		}
		if (biksu.isEmpty() && --jeda <= 0) {
			gelombangBerikut();
		}
		for (KultivatorNpc b : biksu) {
			if (!roboh.contains(b) && b.getTarget() == null) {
				b.markHostile(pemain);
			}
		}
	}

	private void gelombangBerikut() {
		antara = false;
		gelombang++;
		Vec3 depan = pemain.position().add(arah.scale(7.0D));
		Vec3 sisi = new Vec3(-arah.z, 0.0D, arah.x);
		for (int i = 0; i < PER_GELOMBANG; i++) {
			Vec3 at = tanah(depan.x + sisi.x * (i == 0 ? -1.5D : 1.5D), depan.y, depan.z + sisi.z * (i == 0 ? -1.5D : 1.5D));
			KultivatorNpc b = tubuh(NpcRole.TETUA_SEKTE, at, yaw(at, pemain.position()), realmLawan(), t("lorong_perunggu.biksu", gelombang), RUPA);
			if (b == null) {
				akhiri(lolos / (double) GELOMBANG, "anggaran", true);
				return;
			}
			b.memory().putBoolean("hold", false);
			b.setDiamDiTitik(false);
			b.arm(false);
			TechniquePower.markRealmFoe(b);
			HidupApi.klip(b, GabungGerakId.BIKSU_KUDA_KUDA, 0, 30);
			b.markHostile(pemain);
			biksu.add(b);
		}
		bunyi(depan, SoundEvents.BELL_BLOCK, 1.2F, 0.6F);
		bar(t(ChatFormatting.GOLD, "lorong_perunggu.gelombang", gelombang, GELOMBANG));
	}

	@Override
	public boolean bolehLuka(LivingEntity korban, DamageSource sumber, float jumlah) {
		if (!(korban instanceof KultivatorNpc b) || !biksu.contains(b)) {
			return true;
		}
		if (roboh.contains(b)) {
			return false;
		}
		if (b.getHealth() - jumlah <= b.getMaxHealth() * ROBOH) {
			b.setHealth(Math.max(1.0F, b.getMaxHealth() * ROBOH));
			b.forgive(uuid());
			b.setTarget(null);
			b.memory().putBoolean("hold", true);
			HidupApi.klip(b, GabungGerakId.LELAH_BERLUTUT, 0, 0);
			roboh.add(b);
			bunyi(b.position(), SoundEvents.ANVIL_LAND, 0.5F, 1.6F);
			return false;
		}
		return true;
	}

	@Override
	public void mati(LivingEntity korban, DamageSource sumber) {
		if (sumpahPatah || korban == pemain) {
			return;
		}
		Entity pelaku = sumber.getEntity();
		boolean milikPemain = olehPemain(sumber)
				|| pelaku instanceof Mob mob && SpiritBeasts.ownerOf(mob) != null && uuid().equals(SpiritBeasts.ownerOf(mob).getUUID());
		if (milikPemain && korban.distanceToSqr(pusat) < 64.0D * 64.0D) {
			sumpahPatah = true;
			chat(t(ChatFormatting.DARK_RED, "lorong_perunggu.sumpah", korban.getDisplayName()));
			akhiri(0.0D, "sumpah", true);
		}
	}

	private double nilai() {
		double v = lolos / (double) GELOMBANG;
		if (pemain.getHealth() < pemain.getMaxHealth() * 0.3F) {
			v -= 0.2D;
		}
		return Math.max(0.0D, v);
	}

	@Override
	public void waktuHabis() {
		akhiri(nilai(), "waktu", false);
	}

	@Override
	public int isiData(byte[] d) {
		d[1] = n(gelombang);
		d[2] = n(lolos);
		d[3] = n((int) biksu.stream().filter(b -> !roboh.contains(b)).count());
		d[4] = b(pemain.getHealth() / Math.max(1.0F, pemain.getMaxHealth()));
		d[5] = n(sumpahPatah ? 1 : 0);
		return 5;
	}

	/** Where the candidate entered the corridor (for 41V's camera hints). */
	public Vec3 awal() {
		return awal;
	}
}
