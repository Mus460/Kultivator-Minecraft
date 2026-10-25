package dev.kultivator.dunia.kota42.hewan;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.ChickenSoundVariant;
import net.minecraft.world.entity.animal.chicken.ChickenSoundVariants;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code kultivator:ayam_kota} (DESIGN 42.7.5, 42.12.2): a yard chicken, 0.4 x 0.7 blocks; the chick {@link #VARIAN_ANAK} is
 * about 0.25 x 0.3 (scale {@link #SKALA_ANAK}). Made in a {@code kandang} pen: one rooster, hens, and chicks that keep at
 * their mother's heels ({@link #setInduk}).
 * <p>
 * Pecks the ground all day within a few blocks of the pen, flaps now and then, roosts still by the coop at night; the
 * rooster crows at 05:00 (and once more at sunrise, sometimes for no reason at all). A player sprinting within 6 blocks, or
 * walking right into the flock, scatters it squawking and flapping; so does a resident chasing chickens (clip 434
 * {@code kejar_ayam}). Wheat seeds in a click: it pecks them from the ground at your feet and trails you a little.
 * <p>
 * The loose-chicken street event of 42D-P ({@link HewanKota#ayamLepas}) makes chickens {@link #lepas()}: they roam wide
 * and dart away from anyone within 4 blocks; a click with an empty hand catches one ({@link HewanKota#ayamDitangkap}).
 * <p>
 * Model 42V: body 4 x 5 x 6, neck-head 2 x 4 x 2, comb 1 x 2 x 2, beak, wings 1 x 3 x 4, thin legs; texture 32 x 32 for rooster,
 * hen and chick. States: {@link #KEADAAN_DIAM} (walk from speed), {@link #KEADAAN_PATUK} (head down 50 degrees for 6
 * ticks), {@link #KEADAAN_KEPAK} (wing flap), {@link #KEADAAN_KOKOK} (neck up, beak open), {@link #KEADAAN_KABUR} (running
 * with flapping wings). {@link #nilai()}: 1 while loose (the renderer may ruffle the feathers), else 0.
 */
public class AyamKotaEntity extends HewanKotaEntity {

	public static final int VARIAN_JAGO = 0;
	public static final int VARIAN_BETINA = 1;
	public static final int VARIAN_ANAK = 2;
	public static final int VARIAN_JUMLAH = 3;
	public static final float SKALA_ANAK = 0.5F;

	public static final int KEADAAN_DIAM = 0;
	public static final int KEADAAN_PATUK = 1;
	public static final int KEADAAN_KEPAK = 2;
	public static final int KEADAAN_KOKOK = 3;
	public static final int KEADAAN_KABUR = 4;

	/** 05:00 on the world clock (time of day 23000). */
	public static final long WAKTU_KOKOK = 23000L;

	private @Nullable AyamKotaEntity induk;
	private boolean lepas;
	/** World day of the last dawn crow. */
	private long kokokHari = -1L;

	public AyamKotaEntity(EntityType<? extends AyamKotaEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 4.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.25D);
	}

	@Override
	protected float skala(int varian) {
		return varian == VARIAN_ANAK ? SKALA_ANAK : 1.0F;
	}

	/** A chick's mother (it keeps at her heels). */
	public void setInduk(@Nullable AyamKotaEntity induk) {
		this.induk = induk;
	}

	/** True while loose (the street event): wide roaming, darting off, caught with a click. */
	public boolean lepas() {
		return lepas;
	}

	public void setLepas(boolean lepas) {
		this.lepas = lepas;
		setNilai(lepas ? 1.0F : 0.0F);
	}

	@Override
	protected int jelajah() {
		if (lepas) {
			return 12;
		}
		return malam(this.level()) ? 1 : 4;
	}

	// ================================================================== idle

	@Override
	protected void perilaku(ServerLevel level, long now) {
		var rnd = this.getRandom();
		int k = keadaan();
		if (k == KEADAAN_KOKOK) {
			kokok();
			return;
		}
		// chicks: back to mother when they drift off
		if (varian() == VARIAN_ANAK && induk != null) {
			if (induk.isRemoved()) {
				induk = null;
			} else if (diikuti() == null || induk.distanceToSqr(this) > 9.0D) {
				ikuti(induk, 600);
			}
		}
		if (k == KEADAAN_KABUR) {
			return;
		}
		if (sebar(level)) {
			return;
		}
		if (varian() == VARIAN_JAGO) {
			long hari = Math.floorDiv(level.getOverworldClockTime(), 24000L);
			long waktu = Math.floorMod(level.getOverworldClockTime(), 24000L);
			if (hari != kokokHari && waktu >= WAKTU_KOKOK && waktu < WAKTU_KOKOK + 600L) {
				kokokHari = hari;
				mulaiKokok();
				return;
			}
			if (!malam(level) && rnd.nextInt(2400) == 0) {
				mulaiKokok();
				return;
			}
		}
		if (this.getNavigation().isInProgress() || k != KEADAAN_DIAM) {
			return;
		}
		if (malam(level)) {
			if (rnd.nextInt(80) == 0) {
				pose(KEADAAN_KEPAK, 14);
			}
			return;
		}
		int roll = rnd.nextInt(100);
		if (roll < 22) {
			pose(KEADAAN_PATUK, 6 + rnd.nextInt(6));
		} else if (roll < 24) {
			pose(KEADAAN_KEPAK, 16);
		}
	}

	private void mulaiKokok() {
		this.getNavigation().stop();
		pose(KEADAAN_KOKOK, 40);
		kokok();
	}

	/** The crow: three notes over its first 30 ticks (vanilla clucks pitched down, rising, then a long fall). */
	private void kokok() {
		int t = lamaKeadaan();
		if (t < 5) {
			bunyi(suara().ambientSound().value(), 1.6F, 0.55F);
		} else if (t >= 8 && t < 13) {
			bunyi(suara().ambientSound().value(), 1.8F, 0.75F);
		} else if (t >= 18 && t < 23) {
			bunyi(suara().ambientSound().value(), 1.6F, 0.5F);
		}
	}

	/** Scatters from a sprinting player within 6, a walker within 1.5, a resident chasing chickens; loose ones from anyone within 4. */
	private boolean sebar(ServerLevel level) {
		Entity dari = pemainBerlari(6.0D);
		if (dari == null) {
			Player near = level.getNearestPlayer(this, lepas ? 4.0D : 1.5D);
			if (near != null && !near.isSpectator() && !near.getUUID().equals(pemainDiikuti())) {
				dari = near;
			}
		}
		if (dari == null) {
			List<KultivatorNpc> npcs = level.getEntitiesOfClass(KultivatorNpc.class, this.getBoundingBox().inflate(5.0D),
					n -> n.isAlive() && SosokApi.gerakSekarang(n) == KotaGerakId.KEJAR_AYAM);
			if (!npcs.isEmpty()) {
				dari = npcs.getFirst();
			}
		}
		if (dari == null) {
			return false;
		}
		Vec3 arah = this.position().subtract(dari.position());
		arah = arah.lengthSqr() < 1.0E-4D ? new Vec3(this.getRandom().nextDouble() - 0.5D, 0.0D, this.getRandom().nextDouble() - 0.5D) : arah;
		double sudut = (this.getRandom().nextDouble() - 0.5D) * 1.2D;
		Vec3 putar = arah.normalize().yRot((float) sudut).scale(lepas ? 7.0D : 4.5D);
		pose(KEADAAN_KABUR, 40);
		jalanKe(this.position().add(putar), lepas ? 1.8D : 1.5D);
		if (this.getRandom().nextInt(2) == 0) {
			bunyi(suara().hurtSound().value(), 0.6F, 1.3F + this.getRandom().nextFloat() * 0.3F);
		}
		return true;
	}

	private static boolean malam(Level level) {
		long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return t >= 13000L && t < WAKTU_KOKOK;
	}

	// ================================================================== clicks

	@Override
	protected boolean interaksi(ServerPlayer player, ItemStack stack) {
		if (lepas && stack.isEmpty()) {
			HewanKota.ayamDitangkap(player, this);
			return true;
		}
		if (stack.isEmpty()) {
			elus(player);
			return true;
		}
		if (!stack.is(Items.WHEAT_SEEDS) && !stack.is(Items.BEETROOT_SEEDS) && !stack.is(Items.MELON_SEEDS) && !stack.is(Items.PUMPKIN_SEEDS)) {
			return false;
		}
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		this.getNavigation().stop();
		pose(KEADAAN_PATUK, 30);
		partikel(ParticleTypes.HAPPY_VILLAGER, 4, 0.3D);
		bunyi(suara().ambientSound().value(), 0.7F, 1.2F);
		ikuti(player, 400);
		Msg.actionBar(player, Component.translatable("kultivator.kota42.hewan.ayam.biji"));
		return true;
	}

	@Override
	protected void elus(ServerPlayer player) {
		// a chicken does not care to be petted: a flap, a squawk and off it goes
		bunyi(suara().hurtSound().value(), 0.7F, 1.4F);
		pose(KEADAAN_KABUR, 30);
		Vec3 away = this.position().subtract(player.position()).normalize().scale(3.0D).add(this.position());
		jalanKe(away, 1.5D);
		Msg.actionBar(player, Component.translatable("kultivator.kota42.hewan.ayam.elus"));
	}

	// ================================================================== sounds

	private ChickenSoundVariant.ChickenSoundSet suara() {
		ChickenSoundVariant v = net.minecraft.sounds.SoundEvents.CHICKEN_SOUNDS.get(ChickenSoundVariants.SoundSet.CLASSIC);
		return varian() == VARIAN_ANAK ? v.babySounds() : v.adultSounds();
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return malam(this.level()) ? null : suara().ambientSound().value();
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return suara().hurtSound().value();
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return suara().deathSound().value();
	}

	@Override
	protected float getSoundVolume() {
		return varian() == VARIAN_ANAK ? 0.4F : 0.6F;
	}
}
