package dev.kultivator.dunia.kota42.hewan;

import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.kota42.ekonomi.RumahKota;
import dev.kultivator.util.Msg;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code kultivator:anjing_kota} (DESIGN 42.7.5, 42.12.2): a town dog, 0.6 x 0.8 blocks; the shaggy guard dog
 * {@link #VARIAN_PENJAGA} is 0.8 x 1.0 (scale {@link #SKALA_PENJAGA}). Made at house doors, gates and the cook's yard.
 * <p>
 * By day it trails the children (role {@code ANAK}) at play, begs at the cook (sits and wags), naps across its doorstep;
 * at night it walks with the night watch (role {@code PENJAGA_MALAM}) and barks at strangers within 8 blocks (a player who
 * owns no house in this town); players of bad name in the town (Nama Baik {@value #NAMA_BURUK} or worse) are barked and
 * growled at day and night. Click with an empty hand: it wags and pants. Give it a bone: it guards the giver's house or
 * shop in this town from then on ({@link HewanKota#beriTulang}; night thieves stay away from a guarded shop), or, with
 * no house or shop here, follows the giver for one game hour.
 * <p>
 * Model 42V: body 6 x 6 x 11, head 6 x 6 x 5 with a 3 x 3 x 3 muzzle, ears, curled tail, four legs; the guard has a fur layer
 * 7 x 7 x 12; texture 64 x 32, four coats ({@link #VARIAN_KUNING}, {@link #VARIAN_HITAM}, {@link #VARIAN_BELANG},
 * {@link #VARIAN_PENJAGA}). States: {@link #KEADAAN_DIAM} (walk from speed), {@link #KEADAAN_DUDUK},
 * {@link #KEADAAN_GONGGONG} (jaw 20 degrees, head jerks), {@link #KEADAAN_TIDUR}, {@link #KEADAAN_KIBAS} (fast tail wag
 * +-35 degrees when happy). {@link #nilai()}: 1 while it growls (head low, lips back), else 0.
 */
public class AnjingKotaEntity extends HewanKotaEntity {

	public static final int VARIAN_KUNING = 0;
	public static final int VARIAN_HITAM = 1;
	public static final int VARIAN_BELANG = 2;
	/** Big shaggy guard dog (fur layer, bigger box). */
	public static final int VARIAN_PENJAGA = 3;
	public static final int VARIAN_JUMLAH = 4;
	public static final float SKALA_PENJAGA = 1.3F;

	public static final int KEADAAN_DIAM = 0;
	public static final int KEADAAN_DUDUK = 1;
	public static final int KEADAAN_GONGGONG = 2;
	public static final int KEADAAN_TIDUR = 3;
	public static final int KEADAAN_KIBAS = 4;

	/** Nama Baik at or below which the town's dogs bark at a player day and night. */
	public static final int NAMA_BURUK = -20;
	/** Blocks within which a stranger is barked at by night. */
	public static final double JARAK_GONGGONG = 8.0D;

	/** The player whose house or shop it guards (null = a street dog). */
	private @Nullable UUID tuan;
	/** Players already barked at, until game time (one bout per player per 15 s). */
	private final Map<UUID, Long> digonggong = new HashMap<>();
	private long geramSampai;
	/** Cached Nama Baik town id ({@code Long.MIN_VALUE} = not looked up yet). */
	private long kotaId = Long.MIN_VALUE;

	public AnjingKotaEntity(EntityType<? extends AnjingKotaEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 12.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.3D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected float skala(int varian) {
		return varian == VARIAN_PENJAGA ? SKALA_PENJAGA : 1.0F;
	}

	/** The player it guards for, or null. */
	public @Nullable UUID tuan() {
		return tuan;
	}

	/** Posts it as the guard of {@code pemilik}'s house or shop (the spawner, or a bone). */
	public void setTuan(@Nullable UUID pemilik) {
		this.tuan = pemilik;
	}

	@Override
	protected void tambahTujuan() {
		this.goalSelector.addGoal(4, new TujuanMinta());
	}

	@Override
	protected int jelajah() {
		return tuan != null ? 4 : 10;
	}

	@Override
	protected boolean istirahat(int keadaan) {
		return keadaan == KEADAAN_TIDUR || keadaan == KEADAAN_DUDUK;
	}

	@Override
	protected boolean tidur() {
		return keadaan() == KEADAAN_TIDUR;
	}

	@Override
	protected void takutMulai() {
		bunyi(suara().whineSound().value(), 0.8F, 1.1F);
	}

	// ================================================================== idle

	@Override
	protected void perilaku(ServerLevel level, long now) {
		setNilai(now < geramSampai ? 1.0F : 0.0F);
		var rnd = this.getRandom();
		if (jaga(level, now)) {
			return;
		}
		int k = keadaan();
		if (k == KEADAAN_TIDUR) {
			if (rnd.nextInt(lamaKeadaan() > 3000 ? 15 : 300) == 0) {
				setKeadaan(KEADAAN_DIAM);
				bunyi(net.minecraft.sounds.SoundEvents.WOLF_SHAKE, 0.4F, 1.0F);
			}
			return;
		}
		if (k != KEADAAN_DIAM || this.getNavigation().isInProgress()) {
			return;
		}
		if (diikuti() != null) {
			if (rnd.nextInt(12) == 0) {
				pose(KEADAAN_KIBAS, 30);
			}
			return;
		}
		int jam = jam(level);
		boolean malam = jam >= 20 || jam < 5;
		if (malam && tuan == null && rnd.nextInt(20) == 0 && ikutPeran(level, NpcRole.PENJAGA_MALAM, 24.0D, 800)) {
			return;
		}
		if (!malam && tuan == null && rnd.nextInt(30) == 0 && ikutPeran(level, NpcRole.ANAK, 16.0D, 600)) {
			pose(KEADAAN_KIBAS, 30);
			return;
		}
		boolean diPintu = rumah() != null && jarakRumahSqr() < 2.5D;
		if (diPintu && rnd.nextInt(malam ? 8 : 14) == 0) {
			// across the doorstep: asleep by day, lying watchful by night
			if (malam && tuan != null) {
				pose(KEADAAN_DUDUK, 400 + rnd.nextInt(400));
			} else {
				setKeadaan(KEADAAN_TIDUR);
			}
			return;
		}
		int roll = rnd.nextInt(100);
		if (roll < 4) {
			pose(KEADAAN_DUDUK, 100 + rnd.nextInt(160));
		} else if (roll < 6) {
			pose(KEADAAN_KIBAS, 30);
		}
	}

	/** Barks at strangers by night and at players of bad name always; true while a bout is on. */
	private boolean jaga(ServerLevel level, long now) {
		digonggong.values().removeIf(t -> t < now);
		int jam = jam(level);
		boolean malam = jam >= 20 || jam < 5;
		KotaData kota = KotaData.get(level.getServer());
		for (Player p : level.players()) {
			if (p.isSpectator() || p.distanceToSqr(this) > JARAK_GONGGONG * JARAK_GONGGONG || p.getUUID().equals(tuan)
					|| p.getUUID().equals(pemainDiikuti())) {
				continue;
			}
			long town = kotaNamaBaik(level);
			boolean buruk = town != 0L && kota.reputation(town, p.getUUID()) <= NAMA_BURUK;
			boolean asing = malam && !(p instanceof ServerPlayer sp && warga(sp));
			if (!buruk && !asing) {
				continue;
			}
			this.getLookControl().setLookAt(p, 40.0F, 40.0F);
			if (digonggong.containsKey(p.getUUID())) {
				return keadaan() == KEADAAN_GONGGONG;
			}
			digonggong.put(p.getUUID(), now + 300L);
			if (tidur()) {
				setKeadaan(KEADAAN_DIAM);
			}
			pose(KEADAAN_GONGGONG, 50);
			bunyi(suara().ambientSound().value(), 1.0F, varian() == VARIAN_PENJAGA ? 0.8F : 1.0F);
			if (buruk) {
				geramSampai = now + 80L;
				bunyi(suara().growlSound().value(), 0.8F, 1.0F);
			}
			return true;
		}
		return false;
	}

	/** The Nama Baik town of its post, looked up once (the dog keeps to its titik). */
	private long kotaNamaBaik(ServerLevel level) {
		if (kotaId == Long.MIN_VALUE) {
			kotaId = Towns.within(level, rumah() != null ? rumah() : this.blockPosition());
		}
		return kotaId;
	}

	/** True for a player who belongs here: owns or rents a house in this town (no stranger to its dogs). */
	private boolean warga(ServerPlayer player) {
		if (situsId() == 0L) {
			return false;
		}
		try {
			return RumahKota.milikDiTempat(player.level().getServer(), situsId(), player.getUUID()) != null;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private boolean ikutPeran(ServerLevel level, NpcRole role, double r, int tick) {
		List<KultivatorNpc> npcs = level.getEntitiesOfClass(KultivatorNpc.class, this.getBoundingBox().inflate(r),
				n -> n.isAlive() && n.role() == role);
		if (npcs.isEmpty()) {
			return false;
		}
		ikuti(npcs.get(this.getRandom().nextInt(npcs.size())), tick);
		return true;
	}

	private static int jam(ServerLevel level) {
		return (int) Math.floorMod(Math.floorMod(level.getOverworldClockTime(), 24000L) / 1000L + 6L, 24L);
	}

	// ================================================================== clicks

	@Override
	protected boolean interaksi(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			elus(player);
			return true;
		}
		if (!stack.is(Items.BONE)) {
			return false;
		}
		Component hasil = HewanKota.beriTulang(player, this);
		if (hasil == null) {
			return true;
		}
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		bunyi(net.minecraft.sounds.SoundEvents.GENERIC_EAT.value(), 0.7F, 1.2F);
		partikel(ParticleTypes.HAPPY_VILLAGER, 6, 0.6D);
		pose(KEADAAN_KIBAS, 60);
		Msg.chat(player, hasil);
		return true;
	}

	@Override
	protected void elus(ServerPlayer player) {
		if (tidur()) {
			setKeadaan(KEADAAN_DIAM);
		}
		this.getLookControl().setLookAt(player, 30.0F, 30.0F);
		pose(KEADAAN_KIBAS, 60);
		bunyi(suara().pantSound().value(), 0.8F, 1.0F);
		partikel(ParticleTypes.HEART, 2, 0.8D);
		Msg.actionBar(player, Component.translatable(varian() == VARIAN_PENJAGA ? "kultivator.kota42.hewan.anjing.elus_penjaga"
				: "kultivator.kota42.hewan.anjing.elus"));
	}

	// ================================================================== sounds

	private WolfSoundVariant.WolfSoundSet suara() {
		WolfSoundVariants.SoundSet set = varian() == VARIAN_PENJAGA ? WolfSoundVariants.SoundSet.BIG : WolfSoundVariants.SoundSet.CLASSIC;
		return net.minecraft.sounds.SoundEvents.WOLF_SOUNDS.get(set).adultSounds();
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		if (tidur()) {
			return null;
		}
		return this.getRandom().nextInt(3) == 0 ? suara().ambientSound().value() : suara().pantSound().value();
	}

	@Override
	public int getAmbientSoundInterval() {
		return 480;
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
		return 0.6F;
	}

	// ================================================================== goals

	/** Begs at the cook: walks up to a {@code KOKI} within 14 blocks by day, sits and wags, whines once. */
	private final class TujuanMinta extends Goal {
		private @Nullable KultivatorNpc koki;
		private int lama;

		TujuanMinta() {
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (tuan != null || diikuti() != null || tidur() || AnjingKotaEntity.this.getRandom().nextInt(400) != 0) {
				return false;
			}
			List<KultivatorNpc> npcs = AnjingKotaEntity.this.level().getEntitiesOfClass(KultivatorNpc.class,
					AnjingKotaEntity.this.getBoundingBox().inflate(14.0D), n -> n.isAlive() && n.role() == NpcRole.KOKI);
			if (npcs.isEmpty()) {
				return false;
			}
			koki = npcs.getFirst();
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return koki != null && koki.isAlive() && lama > 0;
		}

		@Override
		public void start() {
			lama = 240;
			setKeadaan(KEADAAN_DIAM);
			if (koki != null) {
				AnjingKotaEntity.this.getNavigation().moveTo(koki, 1.0D);
			}
		}

		@Override
		public void tick() {
			lama--;
			if (koki == null) {
				return;
			}
			AnjingKotaEntity.this.getLookControl().setLookAt(koki, 30.0F, 30.0F);
			if (koki.distanceToSqr(AnjingKotaEntity.this) < 5.0D) {
				AnjingKotaEntity.this.getNavigation().stop();
				if (keadaan() == KEADAAN_DIAM) {
					pose(lama % 80 < 40 ? KEADAAN_DUDUK : KEADAAN_KIBAS, 40);
					if (lama % 80 == 0) {
						bunyi(suara().whineSound().value(), 0.5F, 1.2F);
					}
				}
			} else if (AnjingKotaEntity.this.getNavigation().isDone()) {
				AnjingKotaEntity.this.getNavigation().moveTo(koki, 1.0D);
			}
		}

		@Override
		public void stop() {
			koki = null;
		}
	}
}
