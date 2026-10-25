package dev.kultivator.dunia.kota42.hewan;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.animal.feline.CatSoundVariant;
import net.minecraft.world.entity.animal.feline.CatSoundVariants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * {@code kultivator:kucing_kota} (DESIGN 42.7.5, 42.12.2): a town cat, 0.5 x 0.6 blocks. Sleeps in the sun on its roof or
 * window sill ({@code kucing_tidur} titik, where it is made), wakes with a stretch, licks a paw, strolls the lanes, follows
 * the fish seller, hisses at dogs, stalks landed pigeons, leaps up to its roof again, and comes to the townsfolk who feed
 * it (clip 361 {@code beri_makan_kucing}, habit {@code teman_kucing}) and children who play with it (clip 447
 * {@code main_kucing}).
 * <p>
 * Click with an empty hand: it purrs (hearts). Give it a fish (cod, salmon, cooked or tropical): it eats and follows the
 * giver for one game hour, rubbing round their legs whenever they stop. {@link #laoHua()} is <b>Lao Hua</b> (老花), the
 * old orange cat of the capital's market (42.10): the first time he follows a player, advancement {@code kota42_kucing}.
 * <p>
 * Model 42V: body 4 x 4 x 9, head 4 x 4 x 4 with ears and muzzle, two-segment tail, four legs; texture 64 x 32 with six coats
 * ({@link #VARIAN_ORANYE} .. {@link #VARIAN_KALIKO}). States: {@link #KEADAAN_DIAM} (walk from speed),
 * {@link #KEADAAN_DUDUK}, {@link #KEADAAN_TIDUR} (curled, tail over the nose), {@link #KEADAAN_MENJILAT} (licking a paw),
 * {@link #KEADAAN_MEREGANG} (stretch, front lean 20 degrees), {@link #KEADAAN_LOMPAT} (the leap up to a roof); the tail
 * sine and blinking are client-only. {@link #nilai()}: 1 while purring (petted or rubbing), else 0.
 */
public class KucingKotaEntity extends HewanKotaEntity {

	public static final int VARIAN_ORANYE = 0;
	public static final int VARIAN_HITAM = 1;
	public static final int VARIAN_PUTIH = 2;
	public static final int VARIAN_BELANG_ABU = 3;
	public static final int VARIAN_HITAM_PUTIH = 4;
	public static final int VARIAN_KALIKO = 5;
	public static final int VARIAN_JUMLAH = 6;

	public static final int KEADAAN_DIAM = 0;
	public static final int KEADAAN_DUDUK = 1;
	public static final int KEADAAN_TIDUR = 2;
	public static final int KEADAAN_MENJILAT = 3;
	public static final int KEADAAN_MEREGANG = 4;
	public static final int KEADAAN_LOMPAT = 5;

	/** One game hour: how long a fed cat follows its giver. */
	public static final int IKUT_IKAN = 1000;

	private boolean laoHua;
	/** Game time of the last leap (a cat leaps at most every 3 s). */
	private long lompatT;
	/** Game time until the purr value is held. */
	private long dengkurSampai;

	public KucingKotaEntity(EntityType<? extends KucingKotaEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 8.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.3D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	/** True for Lao Hua, the named cat of the capital. */
	public boolean laoHua() {
		return laoHua;
	}

	/** Makes this Lao Hua (old orange tabby, named). */
	public void jadikanLaoHua() {
		this.laoHua = true;
		setVarian(VARIAN_ORANYE);
		setCustomName(Component.translatable("kultivator.kota42.hewan.nama.lao_hua"));
	}

	@Override
	protected void tambahTujuan() {
		this.goalSelector.addGoal(2, new TujuanLompat());
		this.goalSelector.addGoal(4, new TujuanWarga());
		this.goalSelector.addGoal(5, new TujuanBuru());
	}

	@Override
	protected int jelajah() {
		return 10;
	}

	@Override
	protected boolean istirahat(int keadaan) {
		return keadaan == KEADAAN_TIDUR || keadaan == KEADAAN_DUDUK || keadaan == KEADAAN_MENJILAT;
	}

	@Override
	protected boolean tidur() {
		return keadaan() == KEADAAN_TIDUR;
	}

	@Override
	protected @Nullable BlockPos tempatSembunyi() {
		return rumah();
	}

	@Override
	protected void takutMulai() {
		bunyi(suara().hissSound().value(), 0.8F, 1.0F);
	}

	// ================================================================== idle

	@Override
	protected void perilaku(ServerLevel level, long now) {
		setNilai(now < dengkurSampai ? 1.0F : 0.0F);
		var rnd = this.getRandom();
		int k = keadaan();
		if (k == KEADAAN_TIDUR) {
			// woken by a player walking right up, else sleeps on; the sun cat wakes with a stretch
			if (rnd.nextInt(lamaKeadaan() > 2400 ? 20 : 400) == 0 || level.getNearestPlayer(this, 1.6D) != null && rnd.nextInt(8) == 0) {
				pose(KEADAAN_MEREGANG, 40);
			}
			return;
		}
		if (k != KEADAAN_DIAM || this.getNavigation().isInProgress()) {
			return;
		}
		AnjingKotaEntity anjing = dekat(level, AnjingKotaEntity.class, 4.0D);
		if (anjing != null) {
			// a dog too close: back arched, a hiss, then off a few blocks
			this.getLookControl().setLookAt(anjing, 30.0F, 30.0F);
			if (rnd.nextInt(4) == 0) {
				bunyi(suara().hissSound().value(), 0.7F, 0.9F + rnd.nextFloat() * 0.2F);
				Vec3 away = this.position().subtract(anjing.position()).normalize().scale(4.0D).add(this.position());
				jalanKe(away, 1.3D);
			}
			return;
		}
		var ikut = diikuti();
		if (ikut != null) {
			if (ikut.distanceToSqr(this) < 4.0D && rnd.nextInt(6) == 0) {
				// rubbing round the legs of whoever it follows
				dengkurSampai = now + 60L;
				bunyi(suara().purrSound().value(), 0.6F, 1.0F);
				pose(rnd.nextBoolean() ? KEADAAN_DUDUK : KEADAAN_MENJILAT, 40 + rnd.nextInt(40));
			}
			return;
		}
		boolean siang = siang(level);
		BlockPos home = rumah();
		boolean diRumah = home != null && jarakRumahSqr() < 2.5D;
		if (diRumah && rnd.nextInt(siang ? 6 : 30) == 0) {
			setKeadaan(KEADAAN_TIDUR);
			return;
		}
		int roll = rnd.nextInt(100);
		if (roll < 3) {
			pose(KEADAAN_MENJILAT, 60 + rnd.nextInt(60));
		} else if (roll < 6) {
			pose(KEADAAN_DUDUK, 100 + rnd.nextInt(140));
		} else if (roll < 7) {
			pose(KEADAAN_MEREGANG, 40);
		} else if (roll < 9 && !siang) {
			pose(KEADAAN_DUDUK, 60);
		}
		if (rnd.nextInt(60) == 0) {
			ikutPenjualIkan(level);
		}
	}

	private static boolean siang(ServerLevel level) {
		int jam = (int) Math.floorMod(Math.floorMod(level.getOverworldClockTime(), 24000L) / 1000L + 6L, 24L);
		return jam >= 7 && jam < 18;
	}

	/** Trails the fish seller or a fisherman passing by for half a minute. */
	private void ikutPenjualIkan(ServerLevel level) {
		List<KultivatorNpc> npcs = level.getEntitiesOfClass(KultivatorNpc.class, this.getBoundingBox().inflate(12.0D),
				n -> n.isAlive() && (n.role() == NpcRole.PENJUAL_IKAN || n.role() == NpcRole.NELAYAN));
		if (!npcs.isEmpty()) {
			ikuti(npcs.getFirst(), 600);
		}
	}

	private <T extends net.minecraft.world.entity.Entity> @Nullable T dekat(ServerLevel level, Class<T> cls, double r) {
		List<T> list = level.getEntitiesOfClass(cls, this.getBoundingBox().inflate(r), e -> e != this && e.isAlive());
		return list.isEmpty() ? null : list.getFirst();
	}

	// ================================================================== clicks

	@Override
	protected boolean interaksi(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			elus(player);
			return true;
		}
		if (!ikan(stack)) {
			return false;
		}
		if (player.getUUID().equals(pemainDiikuti())) {
			Msg.actionBar(player, Component.translatable("kultivator.kota42.hewan.kucing.kenyang", nama()));
			return true;
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		bunyi(suara().eatSound().value(), 0.8F, 1.0F);
		partikel(ParticleTypes.HAPPY_VILLAGER, 6, 0.5D);
		setKeadaan(KEADAAN_DIAM);
		ikuti(player, IKUT_IKAN);
		if (laoHua) {
			Msg.chat(player, Component.translatable("kultivator.kota42.hewan.lao_hua.ikut"));
			Advancements.grant(player, "tempat/kota42_kucing");
		} else {
			Msg.actionBar(player, Component.translatable("kultivator.kota42.hewan.kucing.ikut", nama()));
		}
		return true;
	}

	@Override
	protected void elus(ServerPlayer player) {
		long now = this.level().getGameTime();
		boolean bangun = keadaan() == KEADAAN_TIDUR;
		dengkurSampai = now + 80L;
		setNilai(1.0F);
		this.getLookControl().setLookAt(player, 30.0F, 30.0F);
		bunyi(suara().purrSound().value(), 0.8F, 1.0F);
		partikel(ParticleTypes.HEART, 2, 0.6D);
		pose(bangun ? KEADAAN_MEREGANG : KEADAAN_DUDUK, bangun ? 40 : 80);
		Msg.actionBar(player, Component.translatable(laoHua ? "kultivator.kota42.hewan.lao_hua.elus" : "kultivator.kota42.hewan.kucing.elus", nama()));
	}

	private Component nama() {
		return laoHua ? Component.translatable("kultivator.kota42.hewan.nama.lao_hua") : Component.translatable("kultivator.kota42.hewan.kucing");
	}

	/** True for the fish a cat takes. */
	public static boolean ikan(ItemStack stack) {
		return stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.COOKED_COD) || stack.is(Items.COOKED_SALMON)
				|| stack.is(Items.TROPICAL_FISH);
	}

	// ================================================================== sounds

	private static CatSoundVariant.CatSoundSet suara() {
		return net.minecraft.sounds.SoundEvents.CAT_SOUNDS.get(CatSoundVariants.SoundSet.CLASSIC).adultSounds();
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		if (tidur()) {
			return null;
		}
		return this.getRandom().nextInt(4) == 0 ? suara().purreowSound().value() : suara().strayAmbientSound().value();
	}

	@Override
	public int getAmbientSoundInterval() {
		return 400;
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
		return 0.5F;
	}

	// ================================================================== goals

	/** The leap up to a roof or sill: when its titik is above it and close, it jumps there in one arc. */
	private final class TujuanLompat extends Goal {
		private int tunggu;

		TujuanLompat() {
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			BlockPos home = rumah();
			if (home == null || !KucingKotaEntity.this.onGround() || diikuti() != null) {
				return false;
			}
			long now = KucingKotaEntity.this.level().getGameTime();
			if (now - lompatT < 60L) {
				return false;
			}
			double dy = home.getY() - KucingKotaEntity.this.getY();
			double dx = home.getX() + 0.5D - KucingKotaEntity.this.getX();
			double dz = home.getZ() + 0.5D - KucingKotaEntity.this.getZ();
			double h2 = dx * dx + dz * dz;
			// wants home (frightened, or tired of strolling) and home is a short leap up
			boolean ingin = takut() || KucingKotaEntity.this.getRandom().nextInt(200) == 0 || siang((ServerLevel) KucingKotaEntity.this.level())
					&& KucingKotaEntity.this.getRandom().nextInt(80) == 0;
			return ingin && dy > 1.2D && dy <= 4.5D && h2 <= 4.5D * 4.5D;
		}

		@Override
		public void start() {
			BlockPos home = rumah();
			if (home == null) {
				return;
			}
			lompatT = KucingKotaEntity.this.level().getGameTime();
			KucingKotaEntity.this.getNavigation().stop();
			KucingKotaEntity.this.getLookControl().setLookAt(Vec3.atCenterOf(home));
			ubahKeLompat();
			double dy = home.getY() - KucingKotaEntity.this.getY();
			double dx = home.getX() + 0.5D - KucingKotaEntity.this.getX();
			double dz = home.getZ() + 0.5D - KucingKotaEntity.this.getZ();
			// vertical: enough for the height plus a margin (gravity 0.08, drag 0.98); horizontal: air drag 0.91 over ~12 ticks
			double vy = Math.sqrt(2.0D * 0.08D * (dy + 0.7D) / 0.9D);
			double t = Math.max(8.0D, vy / 0.08D + 2.0D);
			double reach = (1.0D - Math.pow(0.91D, t)) / 0.09D;
			KucingKotaEntity.this.setDeltaMovement(dx / reach, vy, dz / reach);
			KucingKotaEntity.this.needsSync = true;
			tunggu = 30;
		}

		private void ubahKeLompat() {
			pose(KEADAAN_LOMPAT, 14);
		}

		@Override
		public boolean canContinueToUse() {
			return tunggu > 0 && !(KucingKotaEntity.this.onGround() && tunggu < 25);
		}

		@Override
		public void tick() {
			tunggu--;
		}

		@Override
		public void stop() {
			BlockPos home = rumah();
			if (home != null && jarakRumahSqr() < 2.5D && !takut()) {
				// landed on its roof: a little sit before the nap
				pose(KEADAAN_DUDUK, 60);
			}
		}
	}

	/** Comes to a resident feeding cats (clip 361) or a child playing with string (clip 447) within 14 blocks. */
	private final class TujuanWarga extends Goal {
		private @Nullable KultivatorNpc warga;

		TujuanWarga() {
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (diikuti() != null || KucingKotaEntity.this.getRandom().nextInt(40) != 0 || tidur()) {
				return false;
			}
			List<KultivatorNpc> npcs = KucingKotaEntity.this.level().getEntitiesOfClass(KultivatorNpc.class,
					KucingKotaEntity.this.getBoundingBox().inflate(14.0D), n -> n.isAlive() && memberiMakan(n));
			if (npcs.isEmpty()) {
				return false;
			}
			warga = npcs.getFirst();
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return warga != null && warga.isAlive() && memberiMakan(warga) && warga.distanceToSqr(KucingKotaEntity.this) > 2.0D
					&& !KucingKotaEntity.this.getNavigation().isDone();
		}

		@Override
		public void start() {
			if (warga != null) {
				setKeadaan(KEADAAN_DIAM);
				KucingKotaEntity.this.getNavigation().moveTo(warga, 1.1D);
			}
		}

		@Override
		public void tick() {
			if (warga != null) {
				KucingKotaEntity.this.getLookControl().setLookAt(warga, 30.0F, 30.0F);
			}
		}

		@Override
		public void stop() {
			if (warga != null && warga.distanceToSqr(KucingKotaEntity.this) <= 4.0D) {
				// eating from the hand / batting at the string, purring
				dengkurSampai = KucingKotaEntity.this.level().getGameTime() + 120L;
				pose(KEADAAN_DUDUK, 160);
				bunyi(suara().eatSound().value(), 0.5F, 1.1F);
			}
			warga = null;
		}

		private static boolean memberiMakan(KultivatorNpc n) {
			int klip = SosokApi.gerakSekarang(n);
			return klip == KotaGerakId.BERI_MAKAN_KUCING || klip == KotaGerakId.MAIN_KUCING;
		}
	}

	/** Stalks a landed pigeon flock within 10 blocks; at 2.5 blocks the whole flock bursts up. */
	private final class TujuanBuru extends Goal {
		private @Nullable KawananMerpatiEntity kawanan;

		TujuanBuru() {
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (diikuti() != null || tidur() || KucingKotaEntity.this.getRandom().nextInt(300) != 0) {
				return false;
			}
			List<KawananMerpatiEntity> list = KucingKotaEntity.this.level().getEntitiesOfClass(KawananMerpatiEntity.class,
					KucingKotaEntity.this.getBoundingBox().inflate(10.0D), k -> k.keadaan() == KawananMerpatiEntity.KEADAAN_MENDARAT);
			if (list.isEmpty()) {
				return false;
			}
			kawanan = list.getFirst();
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return kawanan != null && !kawanan.isRemoved() && kawanan.keadaan() == KawananMerpatiEntity.KEADAAN_MENDARAT
					&& !KucingKotaEntity.this.getNavigation().isDone();
		}

		@Override
		public void start() {
			if (kawanan != null) {
				setKeadaan(KEADAAN_DIAM);
				// the slow crouching creep first, the rush at the end (tick)
				KucingKotaEntity.this.getNavigation().moveTo(kawanan.getX(), kawanan.getY(), kawanan.getZ(), 0.55D);
			}
		}

		@Override
		public void tick() {
			if (kawanan == null) {
				return;
			}
			KucingKotaEntity.this.getLookControl().setLookAt(kawanan, 30.0F, 30.0F);
			double d2 = kawanan.distanceToSqr(KucingKotaEntity.this);
			if (d2 < 4.0D * 4.0D) {
				KucingKotaEntity.this.getNavigation().moveTo(kawanan.getX(), kawanan.getY(), kawanan.getZ(), 1.5D);
			}
			if (d2 < 2.5D * 2.5D) {
				kawanan.kagetkan(KucingKotaEntity.this.position());
			}
		}

		@Override
		public void stop() {
			kawanan = null;
		}
	}
}
