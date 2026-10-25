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
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:unta_tambat} (DESIGN 42.7.5, 42.12.2): a camel resting at a caravanserai or bazaar tether of the Kota
 * Oasis and the desert trading posts, 1.7 x 2.3 blocks; stays at its tether (the walking caravan camel of 38.T is
 * {@code unta_kafilah}, another type; its rental stays 38.T's). Chews the cud with the side-to-side jaw, kneels down to rest
 * for long spells in the midday heat and at night, gets up with a grunt, snorts and tosses its head. Click with an empty
 * hand: a snort and a sniff; a cactus, wheat or hay: it chews it slowly.
 * <p>
 * Model 42V: body 10 x 12 x 22 with two 6 x 6 x 6 humps, an S-curved two-segment neck; texture 128 x 64 in two coats
 * ({@link #VARIAN_PASIR}, {@link #VARIAN_COKLAT_TUA}) plus saddle cloth. States: {@link #KEADAAN_DIAM},
 * {@link #KEADAAN_KUNYAH} (chewing), {@link #KEADAAN_BERLUTUT} (front legs folded, lying), {@link #KEADAAN_DENGUS}
 * (snorting, head tossed). {@link #nilai()} unused.
 */
public class UntaTambatEntity extends HewanKotaEntity {

	public static final int VARIAN_PASIR = 0;
	public static final int VARIAN_COKLAT_TUA = 1;
	public static final int VARIAN_JUMLAH = 2;

	public static final int KEADAAN_DIAM = 0;
	public static final int KEADAAN_KUNYAH = 1;
	public static final int KEADAAN_BERLUTUT = 2;
	public static final int KEADAAN_DENGUS = 3;

	public UntaTambatEntity(EntityType<? extends UntaTambatEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.15D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected boolean berkeliaran() {
		return false;
	}

	@Override
	protected boolean istirahat(int keadaan) {
		return keadaan == KEADAAN_BERLUTUT;
	}

	@Override
	protected void takutMulai() {
		if (keadaan() == KEADAAN_BERLUTUT) {
			bunyi(SoundEvents.CAMEL_STAND, 0.8F, 1.0F);
		}
		pose(KEADAAN_DENGUS, 30);
	}

	@Override
	protected void perilaku(ServerLevel level, long now) {
		var rnd = this.getRandom();
		int k = keadaan();
		long waktu = Math.floorMod(level.getOverworldClockTime(), 24000L);
		boolean panasAtauMalam = waktu >= 5000L && waktu < 8000L || waktu >= 13000L;
		if (k == KEADAAN_BERLUTUT) {
			if (rnd.nextInt(panasAtauMalam ? 600 : 120) == 0) {
				bunyi(SoundEvents.CAMEL_STAND, 0.7F, 1.0F);
				pose(KEADAAN_DENGUS, 30);
			}
			return;
		}
		if (k != KEADAAN_DIAM || this.getNavigation().isInProgress()) {
			return;
		}
		int roll = rnd.nextInt(100);
		if (roll < (panasAtauMalam ? 4 : 1)) {
			bunyi(SoundEvents.CAMEL_SIT, 0.7F, 1.0F);
			setKeadaan(KEADAAN_BERLUTUT);
		} else if (roll < 12) {
			pose(KEADAAN_KUNYAH, 80 + rnd.nextInt(120));
		} else if (roll < 14) {
			pose(KEADAAN_DENGUS, 30);
			bunyi(SoundEvents.CAMEL_AMBIENT, 0.7F, 0.9F);
		}
	}

	@Override
	protected boolean interaksi(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			elus(player);
			return true;
		}
		if (!stack.is(Items.CACTUS) && !stack.is(Items.WHEAT) && !stack.is(Items.HAY_BLOCK)) {
			return false;
		}
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		this.getLookControl().setLookAt(player, 30.0F, 30.0F);
		pose(KEADAAN_KUNYAH, 120);
		bunyi(SoundEvents.CAMEL_EAT, 0.8F, 1.0F);
		partikel(ParticleTypes.HAPPY_VILLAGER, 5, 2.0D);
		Msg.actionBar(player, Component.translatable("kultivator.kota42.hewan.unta.makan"));
		return true;
	}

	@Override
	protected void elus(ServerPlayer player) {
		this.getLookControl().setLookAt(player, 30.0F, 30.0F);
		if (keadaan() != KEADAAN_BERLUTUT) {
			pose(KEADAAN_DENGUS, 30);
		}
		bunyi(SoundEvents.CAMEL_AMBIENT, 0.8F, 1.1F);
		Msg.actionBar(player, Component.translatable("kultivator.kota42.hewan.unta.elus"));
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return SoundEvents.CAMEL_AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 600;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.CAMEL_HURT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return SoundEvents.CAMEL_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6F;
	}
}
