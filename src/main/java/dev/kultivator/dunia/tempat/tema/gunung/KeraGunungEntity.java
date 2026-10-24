package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.bersama.TemaAnimasi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code kultivator:kera_gunung} (DESIGN 38.T.6.4, 38.T.9.2): the mountain macaque of the Kota Gunung top row (0.7 blocks;
 * brown-grey fur, pink face, long tail). 4-8 per town: sits scratching, runs on four legs, leaps when climbing, and
 * snatches the food a player HOLDS (one item from the hand, never anything else), then flees to the cliff; hitting it or
 * throwing it fruit on the ground makes it drop the loot. Killing one on monastery ground costs Nama Baik -3.
 * <p>
 * Behaviour (38TK-G): bound to a haunt anchor ({@code tema_gunung/kera}, home point); goal {@link Rebut} picks the nearest
 * player within 6 blocks with food in a hand (DataComponents.FOOD), runs to them and takes ONE item (shown in its hand),
 * then {@link Kabur} runs it back to the haunt. A player is safe for 30 s after a theft. Hit: it drops the loot and
 * flees (the hit never kills on the first blow of a fist). Fruit (sweet berries, glow berries, apple, melon slice,
 * carrot) thrown within 6 blocks: it drops the loot, eats one fruit and sits. A dropped loot counts for the side quest
 * {@code tema_kera_nakal} (story mark {@code gunung_kera_kembali}). On the Biara Awan's ground (district
 * {@code biara_awan}) a player kill costs Nama Baik -3.
 * <p>
 * Variant {@link #VARIAN_PUTIH_TUA}: Kera Putih Tua, the spirit-beast guardian of quest {@code tema_gunung_tali} (1.8 x
 * scale through the {@code SCALE} attribute, white fur, beard and brows, tier 4: 80 health; not hostile unless attacked;
 * points and bows; never steals). Never saved; no loot table.
 */
public class KeraGunungEntity extends PathfinderMob implements TemaAnimasi {

	/** Brown-grey macaque. */
	public static final int VARIAN_COKLAT = 0;
	/** Kera Putih Tua (spirit guardian, scaled 1.8). */
	public static final int VARIAN_PUTIH_TUA = 1;

	/** Idle / walking / running (gait from speed). */
	public static final int KEADAAN_DIAM = 0;
	/** Sitting and scratching. */
	public static final int KEADAAN_DUDUK = 1;
	/** Snatch: arm swipes 6 ticks, the stolen item appears in its hand. */
	public static final int KEADAAN_REBUT = 2;
	/** Fleeing with loot. */
	public static final int KEADAAN_KABUR = 3;
	/** Climbing leap. */
	public static final int KEADAAN_PANJAT = 4;
	/** Old white guardian: pointing gesture. */
	public static final int KEADAAN_TUNJUK = 5;
	/** Old white guardian: bow. */
	public static final int KEADAAN_MEMBUNGKUK = 6;

	public static final int AMAN_TICK = 600;
	public static final double JARAK_REBUT = 6.0D;

	private static final EntityDataAccessor<Integer> DATA_VARIAN = SynchedEntityData.defineId(KeraGunungEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_KEADAAN = SynchedEntityData.defineId(KeraGunungEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_MULAI = SynchedEntityData.defineId(KeraGunungEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_NILAI = SynchedEntityData.defineId(KeraGunungEntity.class, EntityDataSerializers.FLOAT);

	/** Players safe from theft until game time (shared by all monkeys). */
	private static final Map<UUID, Long> AMAN = new HashMap<>();

	private long situsId;
	private @Nullable BlockPos rumah;
	private @Nullable UUID pemilikCurian;
	private boolean marah;

	public KeraGunungEntity(EntityType<? extends KeraGunungEntity> type, Level level) {
		super(type, level);
		this.setCanPickUpLoot(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 10.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.32D)
				.add(Attributes.FOLLOW_RANGE, 16.0D)
				.add(Attributes.ATTACK_DAMAGE, 3.0D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIAN, VARIAN_COKLAT);
		builder.define(DATA_KEADAAN, KEADAAN_DIAM);
		builder.define(DATA_MULAI, 0);
		builder.define(DATA_NILAI, 0.0F);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25D, true) {
			@Override
			public boolean canUse() {
				return marah && super.canUse();
			}
		});
		this.goalSelector.addGoal(2, new Kabur());
		this.goalSelector.addGoal(3, new Rebut());
		this.goalSelector.addGoal(5, new Pulang());
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				return varian() == VARIAN_PUTIH_TUA && super.canUse();
			}
		});
	}

	/** Binds the monkey to its town (0 = test spawn). */
	public void ikat(long situsId) {
		this.situsId = situsId;
	}

	/** Binds the monkey to its town and haunt. */
	public void ikat(long situsId, BlockPos rumah) {
		this.situsId = situsId;
		this.rumah = rumah.immutable();
	}

	public long situsId() {
		return situsId;
	}

	/** Makes this the Kera Putih Tua (scale 1.8, 80 health). */
	public void jadikanPutihTua() {
		setVarian(VARIAN_PUTIH_TUA);
		AttributeInstance scale = getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(1.8D);
		}
		AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) {
			hp.setBaseValue(80.0D);
			setHealth(80.0F);
		}
		AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
		if (dmg != null) {
			dmg.setBaseValue(9.0D);
		}
	}

	/** Turns the old guardian hostile (taking the fibre by force). */
	public void marah(@Nullable Player sasaran) {
		this.marah = true;
		if (sasaran != null) {
			this.setTarget(sasaran);
		}
	}

	@Override
	public int varian() {
		return this.entityData.get(DATA_VARIAN);
	}

	@Override
	public int keadaan() {
		return this.entityData.get(DATA_KEADAAN);
	}

	@Override
	public int mulai() {
		return this.entityData.get(DATA_MULAI);
	}

	/** Tail curl 0..1 (relaxed .. raised in alarm). */
	@Override
	public float nilai() {
		return this.entityData.get(DATA_NILAI);
	}

	public void setVarian(int varian) {
		this.entityData.set(DATA_VARIAN, varian);
	}

	public void setKeadaan(int keadaan) {
		if (keadaan() != keadaan) {
			this.entityData.set(DATA_KEADAAN, keadaan);
			this.entityData.set(DATA_MULAI, (int) (this.level().getGameTime() & 0x7fffffffL));
		}
	}

	public void setNilai(float nilai) {
		this.entityData.set(DATA_NILAI, nilai);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected net.minecraft.world.InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND && varian() == VARIAN_PUTIH_TUA && player instanceof ServerPlayer sp && !marah) {
			return GunungCerita.keraPutihDiklik(sp, this) ? net.minecraft.world.InteractionResult.SUCCESS_SERVER
					: net.minecraft.world.InteractionResult.PASS;
		}
		return super.mobInteract(player, hand);
	}

	// ================================================================================================ loot

	private boolean bawaCurian() {
		return !getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
	}

	/** True for food in a hand that a monkey snatches. */
	private static boolean makanan(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.FOOD);
	}

	private static boolean buah(ItemStack stack) {
		return stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES) || stack.is(Items.APPLE) || stack.is(Items.MELON_SLICE)
				|| stack.is(Items.CARROT);
	}

	/** Drops what it carries (hit or lured); a player who made it drop gets the story mark. */
	private void jatuhkan(ServerLevel level, @Nullable ServerPlayer oleh) {
		ItemStack loot = getItemBySlot(EquipmentSlot.MAINHAND);
		if (loot.isEmpty()) {
			return;
		}
		setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		spawnAtLocation(level, loot);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6F, 0.6F);
		if (oleh != null) {
			CeritaApi.peristiwa(oleh, "tanda", "gunung_kera_kembali");
		}
		pemilikCurian = null;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		ServerPlayer by = source.getEntity() instanceof ServerPlayer p ? p : null;
		if (bawaCurian()) {
			jatuhkan(level, by);
			setKeadaan(KEADAAN_KABUR);
		}
		setNilai(1.0F);
		if (by != null && varian() == VARIAN_PUTIH_TUA) {
			marah(by);
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level) {
			jatuhkan(level, null);
			if (source.getEntity() instanceof ServerPlayer killer && varian() == VARIAN_COKLAT) {
				TemaKota.Kota kota = GunungTata.kotaDi(level, blockPosition());
				String distrik = kota == null ? null : dev.kultivator.dunia.tempat.tema.TemaKota.distrikDi(kota, blockPosition());
				if ("biara_awan".equals(distrik)) {
					long town = Towns.within(level, blockPosition());
					if (town != 0L) {
						KotaData.get(level.getServer()).addReputation(town, killer.getUUID(), -3);
					}
					Msg.chat(killer, Component.translatable("kultivator.kota_tema.gunung.kera.biksu_marah").withStyle(ChatFormatting.RED));
				}
			}
		}
		super.die(source);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel level) || this.tickCount % 20 != 0 || varian() == VARIAN_PUTIH_TUA) {
			return;
		}
		// fruit on the ground: drop the loot, eat one, sit
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(6.0D))) {
			if (buah(item.getItem())) {
				ServerPlayer owner = pemilikCurian == null ? null : level.getServer().getPlayerList().getPlayer(pemilikCurian);
				if (item.getOwner() instanceof ServerPlayer thrower) {
					owner = thrower;
				}
				jatuhkan(level, owner);
				getNavigation().moveTo(item, 1.1D);
				if (distanceToSqr(item) < 2.5D) {
					item.getItem().shrink(1);
					if (item.getItem().isEmpty()) {
						item.discard();
					}
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.6F, 1.4F);
					setKeadaan(KEADAAN_DUDUK);
				}
				return;
			}
		}
		if (keadaan() == KEADAAN_DIAM && random.nextInt(12) == 0) {
			setKeadaan(KEADAAN_DUDUK);
		} else if (keadaan() == KEADAAN_DUDUK && random.nextInt(4) == 0) {
			setKeadaan(KEADAAN_DIAM);
		}
		setNilai(Math.max(0.0F, nilai() - 0.1F));
	}

	// ================================================================================================ goals

	/** Run to the nearest player holding food and take one item. */
	private final class Rebut extends Goal {
		private @Nullable ServerPlayer sasaran;
		private int waktu;

		Rebut() {
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (varian() == VARIAN_PUTIH_TUA || bawaCurian() || !TemaConfig.gunung().keraGunung || tickCount % 10 != 0
					|| !(level() instanceof ServerLevel level)) {
				return false;
			}
			long now = level.getGameTime();
			ServerPlayer best = null;
			double bestD = JARAK_REBUT * JARAK_REBUT;
			for (ServerPlayer p : level.players()) {
				if (p.isSpectator() || p.isCreative() || AMAN.getOrDefault(p.getUUID(), 0L) > now) {
					continue;
				}
				double d = p.distanceToSqr(KeraGunungEntity.this);
				if (d < bestD && (makanan(p.getMainHandItem()) || makanan(p.getOffhandItem()))) {
					best = p;
					bestD = d;
				}
			}
			sasaran = best;
			return best != null;
		}

		@Override
		public boolean canContinueToUse() {
			return sasaran != null && sasaran.isAlive() && waktu < 120 && !bawaCurian()
					&& (makanan(sasaran.getMainHandItem()) || makanan(sasaran.getOffhandItem()));
		}

		@Override
		public void start() {
			waktu = 0;
			setNilai(0.6F);
		}

		@Override
		public void tick() {
			if (sasaran == null) {
				return;
			}
			waktu++;
			getLookControl().setLookAt(sasaran);
			getNavigation().moveTo(sasaran, 1.4D);
			if (distanceToSqr(sasaran) < 2.2D) {
				InteractionHand hand = makanan(sasaran.getMainHandItem()) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
				ItemStack held = sasaran.getItemInHand(hand);
				ItemStack satu = held.copyWithCount(1);
				held.shrink(1);
				setItemSlot(EquipmentSlot.MAINHAND, satu);
				setKeadaan(KEADAAN_REBUT);
				pemilikCurian = sasaran.getUUID();
				AMAN.put(sasaran.getUUID(), level().getGameTime() + AMAN_TICK);
				level().playSound(null, getX(), getY(), getZ(), SoundEvents.FOX_SNIFF, SoundSource.NEUTRAL, 0.9F, 1.6F);
				Msg.actionBar(sasaran, Component.translatable("kultivator.kota_tema.gunung.kera.rebut", satu.getHoverName())
						.withStyle(ChatFormatting.YELLOW));
				sasaran = null;
			}
		}

		@Override
		public void stop() {
			sasaran = null;
			getNavigation().stop();
		}
	}

	/** With loot: run back to the haunt (and up the cliff). */
	private final class Kabur extends Goal {
		private int waktu;

		Kabur() {
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return bawaCurian() && rumah != null && distanceToSqr(Vec3.atCenterOf(rumah)) > 9.0D;
		}

		@Override
		public boolean canContinueToUse() {
			return bawaCurian() && waktu < 400 && rumah != null && distanceToSqr(Vec3.atCenterOf(rumah)) > 4.0D;
		}

		@Override
		public void start() {
			waktu = 0;
			if (keadaan() != KEADAAN_REBUT || umurKeadaan(level().getGameTime()) > 6) {
				setKeadaan(KEADAAN_KABUR);
			}
		}

		@Override
		public void tick() {
			waktu++;
			if (waktu == 7) {
				setKeadaan(KEADAAN_KABUR);
			}
			if (rumah != null && waktu % 20 == 1) {
				getNavigation().moveTo(rumah.getX() + 0.5D, rumah.getY(), rumah.getZ() + 0.5D, 1.6D);
			}
			if (horizontalCollision && onGround()) {
				setKeadaan(KEADAAN_PANJAT);
				setDeltaMovement(getDeltaMovement().add(0.0D, 0.45D, 0.0D));
			}
		}

		@Override
		public void stop() {
			setKeadaan(KEADAAN_DUDUK);
		}
	}

	/** Stray too far from the haunt: walk back. */
	private final class Pulang extends Goal {
		Pulang() {
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return rumah != null && tickCount % 40 == 0 && distanceToSqr(Vec3.atCenterOf(rumah)) > 20.0D * 20.0D;
		}

		@Override
		public void start() {
			if (rumah != null) {
				getNavigation().moveTo(rumah.getX() + 0.5D, rumah.getY(), rumah.getZ() + 0.5D, 1.0D);
			}
		}
	}

	// ================================================================================================ haunts

	/** A haunt anchor with players near: keep {@code jumlah} monkeys alive around it (town and world caps apply). */
	static void dekatSarang(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within, List<Entity> buatan) {
		if (!TemaConfig.gunung().keraGunung) {
			return;
		}
		int jumlah = Math.max(1, Math.min(4, GunungAnchors.angka(anchor, "jumlah", 2)));
		int hidup = 0;
		for (Entity e : buatan) {
			if (e instanceof KeraGunungEntity && e.isAlive()) {
				hidup++;
			}
		}
		if (hidup >= jumlah || level.getServer().getTickCount() % 40 != 0) {
			return;
		}
		TemaKota.Kota kota = GunungAnchors.kota(anchor);
		Vec3 at = anchor.position().add(level.getRandom().nextInt(5) - 2, 0.0D, level.getRandom().nextInt(5) - 2);
		KeraGunungEntity kera = TemaMekanisme.muncul(level, TemaEntities.KERA_GUNUNG, at, level.getRandom().nextFloat() * 360.0F, kota,
				anchor.blockPosition());
		if (kera != null) {
			kera.ikat(kota == null ? 0L : kota.situsId(), anchor.blockPosition());
			buatan.add(kera);
		}
	}

	/** Server stop. */
	static void reset() {
		AMAN.clear();
	}
}
