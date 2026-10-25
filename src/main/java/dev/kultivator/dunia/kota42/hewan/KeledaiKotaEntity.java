package dev.kultivator.dunia.kota42.hewan;

import dev.kultivator.util.Msg;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:keledai_kota} (DESIGN 42.7.5, 42.12.2): a donkey tied by a mill, a village shop, a market or a carter's
 * yard, 0.9 x 1.2 blocks; stays at its tether. Chews, snorts, stamps stubbornly and now and then lets out the long bray.
 * At a market or trading post it carries its pack bags ({@link #setMuatan}); at a mill (a {@code tambat} titik with data
 * {@code giling=1}) it walks the millstone round, a 2-block circle about its post, turn after turn by day
 * ({@link #setGiling}). Click with an empty hand: a snort; an apple, carrot or wheat: it chews.
 * <p>
 * Model 42V: the horse at 0.7 with long ears and a stiff short mane; texture 128 x 64 in two coats ({@link #VARIAN_ABU},
 * {@link #VARIAN_COKLAT}) plus pack saddle bags when {@link #nilai()} &gt; 0.5 (loaded). States: {@link #KEADAAN_DIAM},
 * {@link #KEADAAN_KUNYAH}, {@link #KEADAAN_DENGUS}, {@link #KEADAAN_HENTAK} (stamping, the stubborn donkey),
 * {@link #KEADAAN_MERINGKIK} (the bray, head up and jaw open).
 */
public class KeledaiKotaEntity extends HewanKotaEntity {

	public static final int VARIAN_ABU = 0;
	public static final int VARIAN_COKLAT = 1;
	public static final int VARIAN_JUMLAH = 2;

	public static final int KEADAAN_DIAM = 0;
	public static final int KEADAAN_KUNYAH = 1;
	public static final int KEADAAN_DENGUS = 2;
	public static final int KEADAAN_HENTAK = 3;
	public static final int KEADAAN_MERINGKIK = 4;

	/** Radius of the mill round. */
	public static final double JARI_GILING = 2.0D;

	private boolean giling;
	/** Angle along the mill round (radians). */
	private double sudut;

	public KeledaiKotaEntity(EntityType<? extends KeledaiKotaEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 16.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.18D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected boolean berkeliaran() {
		return false;
	}

	/** Pack bags on (true) or off. */
	public void setMuatan(boolean muat) {
		setNilai(muat ? 1.0F : 0.0F);
	}

	/** Walks the mill round about its post by day. */
	public void setGiling(boolean giling) {
		this.giling = giling;
		setDituntun(giling);
	}

	public boolean giling() {
		return giling;
	}

	@Override
	protected void takutMulai() {
		pose(KEADAAN_MERINGKIK, 40);
		bunyi(SoundEvents.DONKEY_ANGRY, 0.9F, 1.0F);
	}

	@Override
	protected void perilaku(ServerLevel level, long now) {
		var rnd = this.getRandom();
		long waktu = Math.floorMod(level.getOverworldClockTime(), 24000L);
		boolean kerja = waktu >= 1000L && waktu < 11000L;
		if (giling && rumah() != null && kerja && keadaan() == KEADAAN_DIAM) {
			if (rnd.nextInt(200) == 0) {
				// the stubborn halt in the middle of the round
				pose(KEADAAN_HENTAK, 60);
				bunyi(SoundEvents.DONKEY_ANGRY, 0.6F, 1.0F);
				this.getNavigation().stop();
				return;
			}
			Vec3 pusat = Vec3.atBottomCenterOf(rumah());
			if (this.getNavigation().isDone()) {
				sudut += Math.PI / 6.0D;
				Vec3 ke = pusat.add(Math.cos(sudut) * JARI_GILING, 0.0D, Math.sin(sudut) * JARI_GILING);
				jalanKe(ke, 0.6D);
			}
			return;
		}
		if (giling && !kerja && jarakRumahSqr() > (JARI_GILING + 0.5D) * (JARI_GILING + 0.5D) && rumah() != null) {
			jalanKe(Vec3.atBottomCenterOf(rumah()), 0.6D);
			return;
		}
		if (keadaan() != KEADAAN_DIAM || this.getNavigation().isInProgress()) {
			return;
		}
		int roll = rnd.nextInt(100);
		if (roll < 8) {
			pose(KEADAAN_KUNYAH, 80 + rnd.nextInt(80));
		} else if (roll < 10) {
			pose(KEADAAN_DENGUS, 30);
		} else if (roll < 12) {
			pose(KEADAAN_HENTAK, 30);
		} else if (roll < 13) {
			pose(KEADAAN_MERINGKIK, 50);
			bunyi(SoundEvents.DONKEY_AMBIENT, 1.3F, 0.95F + rnd.nextFloat() * 0.1F);
		}
	}

	@Override
	protected boolean interaksi(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			elus(player);
			return true;
		}
		if (!stack.is(Items.APPLE) && !stack.is(Items.CARROT) && !stack.is(Items.WHEAT)) {
			return false;
		}
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		this.getNavigation().stop();
		this.getLookControl().setLookAt(player, 30.0F, 30.0F);
		pose(KEADAAN_KUNYAH, 80);
		bunyi(SoundEvents.DONKEY_EAT, 0.8F, 1.0F);
		partikel(ParticleTypes.HAPPY_VILLAGER, 4, 1.1D);
		Msg.actionBar(player, Component.translatable("kultivator.kota42.hewan.keledai.makan"));
		return true;
	}

	@Override
	protected void elus(ServerPlayer player) {
		this.getLookControl().setLookAt(player, 30.0F, 30.0F);
		pose(KEADAAN_DENGUS, 30);
		bunyi(SoundEvents.DONKEY_AMBIENT, 0.5F, 1.3F);
		Msg.actionBar(player, Component.translatable(giling ? "kultivator.kota42.hewan.keledai.elus_giling" : "kultivator.kota42.hewan.keledai.elus"));
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.getRandom().nextInt(4) == 0 ? SoundEvents.DONKEY_AMBIENT : null;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 500;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.DONKEY_HURT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return SoundEvents.DONKEY_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6F;
	}
}
