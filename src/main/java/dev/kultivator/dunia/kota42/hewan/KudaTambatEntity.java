package dev.kultivator.dunia.kota42.hewan;

import dev.kultivator.dunia.tempat.ekonomi.GerobakEntity;
import dev.kultivator.dunia.tempat.tema.oasis.UntaKafilahEntity;
import dev.kultivator.util.Msg;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:kuda_tambat} (DESIGN 42.7.5, 42.12.2): a horse tied at an inn, post station or yamen hitching rail
 * ({@code tambat} titik, or a {@code kandang} pen with {@code hewan=kuda_tambat}), 1.4 x 1.6 blocks. Stays at its tether:
 * swishes its tail, nods, eats from the trough for long spells, stamps a hoof; whinnies when a caravan (a cart or a caravan
 * camel) comes within 14 blocks. Click with an empty hand: it snorts and nuzzles; an apple, carrot, sugar, wheat or hay:
 * it eats from your hand.
 * <p>
 * In a wedding procession (42.7.7, 42D-P) the groom's body rides it: the event unties it ({@link #setDituntun}), dresses it
 * ({@link #setHias}) and leads it; {@link HewanKota#kudaPengantin} makes one for an event.
 * <p>
 * Model 42V: body 10 x 10 x 22, neck 4 x 10 x 6 at 30 degrees, head 5 x 5 x 10, eight-layer quad mane, quad tail, four hoofed
 * legs; texture 128 x 64 in four coats ({@link #VARIAN_COKLAT} .. {@link #VARIAN_PUTIH}) plus the bridal finery layer when
 * {@link #hias()} (red ribbons and a silk flower). States: {@link #KEADAAN_DIAM} (walk from speed),
 * {@link #KEADAAN_KIBAS_EKOR}, {@link #KEADAAN_ANGGUK}, {@link #KEADAAN_MAKAN} (neck down), {@link #KEADAAN_HENTAK}
 * (stamping a hoof). {@link #nilai()} unused.
 */
public class KudaTambatEntity extends HewanKotaEntity {

	public static final int VARIAN_COKLAT = 0;
	public static final int VARIAN_HITAM = 1;
	public static final int VARIAN_ABU_BELANG = 2;
	public static final int VARIAN_PUTIH = 3;
	public static final int VARIAN_JUMLAH = 4;

	public static final int KEADAAN_DIAM = 0;
	public static final int KEADAAN_KIBAS_EKOR = 1;
	public static final int KEADAAN_ANGGUK = 2;
	public static final int KEADAAN_MAKAN = 3;
	public static final int KEADAAN_HENTAK = 4;

	private static final EntityDataAccessor<Boolean> DATA_HIAS = SynchedEntityData.defineId(KudaTambatEntity.class, EntityDataSerializers.BOOLEAN);

	/** Game time of the last caravan whinny (one per minute). */
	private long ringkikT = Long.MIN_VALUE / 2;

	public KudaTambatEntity(EntityType<? extends KudaTambatEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 24.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.2D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_HIAS, false);
	}

	/** True while it wears the wedding finery (42.7.7). */
	public boolean hias() {
		return this.entityData.get(DATA_HIAS);
	}

	public void setHias(boolean hias) {
		this.entityData.set(DATA_HIAS, hias);
	}

	@Override
	protected boolean berkeliaran() {
		return false;
	}

	@Override
	protected boolean istirahat(int keadaan) {
		return keadaan == KEADAAN_MAKAN;
	}

	@Override
	protected void takutMulai() {
		bunyi(SoundEvents.HORSE_ANGRY, 0.9F, 1.0F);
		pose(KEADAAN_HENTAK, 20);
	}

	@Override
	protected void perilaku(ServerLevel level, long now) {
		if (keadaan() != KEADAAN_DIAM || this.getNavigation().isInProgress() || this.isVehicle()) {
			return;
		}
		if (now - ringkikT > 1200L && kafilahDatang(level)) {
			ringkikT = now;
			pose(KEADAAN_ANGGUK, 30);
			bunyi(SoundEvents.HORSE_AMBIENT, 1.2F, 1.0F);
			return;
		}
		var rnd = this.getRandom();
		int roll = rnd.nextInt(100);
		if (roll < 6) {
			pose(KEADAAN_KIBAS_EKOR, 40);
		} else if (roll < 9) {
			pose(KEADAAN_ANGGUK, 30);
		} else if (roll < 12) {
			pose(KEADAAN_MAKAN, 100 + rnd.nextInt(120));
			bunyi(SoundEvents.HORSE_EAT, 0.6F, 1.0F);
		} else if (roll < 13) {
			pose(KEADAAN_HENTAK, 20);
			bunyi(SoundEvents.HORSE_STEP, 0.6F, 0.9F);
		}
	}

	private boolean kafilahDatang(ServerLevel level) {
		return !level.getEntitiesOfClass(Entity.class, this.getBoundingBox().inflate(14.0D),
				e -> e.isAlive() && (e instanceof GerobakEntity || e instanceof UntaKafilahEntity)).isEmpty();
	}

	@Override
	protected boolean interaksi(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			elus(player);
			return true;
		}
		if (!stack.is(Items.APPLE) && !stack.is(Items.CARROT) && !stack.is(Items.SUGAR) && !stack.is(Items.WHEAT) && !stack.is(Items.HAY_BLOCK)) {
			return false;
		}
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		this.getLookControl().setLookAt(player, 30.0F, 30.0F);
		pose(KEADAAN_MAKAN, 60);
		bunyi(SoundEvents.HORSE_EAT, 0.8F, 1.0F);
		partikel(ParticleTypes.HAPPY_VILLAGER, 5, 1.4D);
		Msg.actionBar(player, Component.translatable("kultivator.kota42.hewan.kuda.makan"));
		return true;
	}

	@Override
	protected void elus(ServerPlayer player) {
		this.getLookControl().setLookAt(player, 30.0F, 30.0F);
		pose(KEADAAN_ANGGUK, 30);
		bunyi(SoundEvents.HORSE_BREATHE, 0.9F, 1.0F);
		partikel(ParticleTypes.HEART, 1, 1.6D);
		Msg.actionBar(player, Component.translatable(hias() ? "kultivator.kota42.hewan.kuda.elus_hias" : "kultivator.kota42.hewan.kuda.elus"));
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.getRandom().nextInt(3) == 0 ? SoundEvents.HORSE_AMBIENT : SoundEvents.HORSE_BREATHE;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 400;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HORSE_HURT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return SoundEvents.HORSE_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6F;
	}
}
