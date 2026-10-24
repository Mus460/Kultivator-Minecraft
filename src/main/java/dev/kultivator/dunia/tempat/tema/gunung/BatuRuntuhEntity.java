package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import dev.kultivator.kehancuran.Kehancuran;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code kultivator:batu_runtuh} (DESIGN 38.T.9). Batu longsor (38.T.6.4, saat hujan): bongkah tiga kubus yang
 * menggelinding sepanjang spline dari tebing, terpental di pagar (collider), 4-8 kerusakan bila mengenai, pecah
 * jadi partikel saat berhenti; tidak pernah mengubah blok. Mode Kehancuran atau pukulan teknik memecahnya.
 * <p>
 * Behaviour (38TK-G): {@link #luncurkan} gives the lane (world polyline of {@link GunungTata#LONGSOR}) and a start delay;
 * the rock then moves at {@value #LAJU} blocks per tick along it with a bounce that decays per segment (a pure function
 * of the ticks since the start, so the client's interpolation shows the same path), hits each player once
 * ({@value #RUSAK_MIN}-{@value #RUSAK_MAKS} damage, falling-block source), and shatters at the lane end. A hit from a
 * player in Mode Kehancuran, or any blow of 8 damage or more (technique strikes), shatters it at once.
 * <p>
 * {@link #nilai()}: jarak tempuh (rotasi sebanding jarak). Never saved. The renderer (38TV-M) reads {@link #varian()},
 * {@link #keadaan()}, {@link #mulai()} and {@link #nilai()} only.
 */
public class BatuRuntuhEntity extends TemaMekanismeEntity {

	/** Andesit. */
	public static final int VARIAN_ANDESIT = 0;
	/** Granit. */
	public static final int VARIAN_GRANIT = 1;
	/** Berlumut. */
	public static final int VARIAN_BERLUMUT = 2;

	/** Menggelinding. */
	public static final int KEADAAN_GELINDING = 0;
	/** Pecah. */
	public static final int KEADAAN_PECAH = 1;

	public static final double LAJU = 0.35D;
	public static final int RUSAK_MIN = 4;
	public static final int RUSAK_MAKS = 8;

	private Vec3[] lajur = new Vec3[0];
	private double[] panjang = new double[0];
	private int tunda;
	private int umur;
	private int pecah = -1;
	private final Set<UUID> kena = new HashSet<>();

	public BatuRuntuhEntity(EntityType<? extends BatuRuntuhEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	/** Sets the lane (world points, first on the crest) and the start delay in ticks. */
	public void luncurkan(List<Vec3> titik, int tunda) {
		this.lajur = titik.toArray(new Vec3[0]);
		this.panjang = new double[Math.max(0, lajur.length - 1)];
		for (int i = 0; i + 1 < lajur.length; i++) {
			panjang[i] = lajur[i].distanceTo(lajur[i + 1]);
		}
		this.tunda = Math.max(0, tunda);
		this.umur = 0;
		if (lajur.length > 0) {
			pindah(lajur[0], 0.0F);
		}
		setKeadaan(KEADAAN_GELINDING);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level) || lajur.length < 2) {
			return;
		}
		if (pecah >= 0) {
			if (++pecah > 12) {
				TemaMekanisme.lepas(this);
			}
			return;
		}
		if (tunda > 0) {
			tunda--;
			return;
		}
		umur++;
		double s = umur * LAJU;
		double total = 0.0D;
		for (double p : panjang) {
			total += p;
		}
		if (s >= total) {
			hancur(level);
			return;
		}
		int seg = 0;
		double sisa = s;
		while (seg < panjang.length - 1 && sisa > panjang[seg]) {
			sisa -= panjang[seg];
			seg++;
		}
		double u = panjang[seg] <= 0.0D ? 1.0D : sisa / panjang[seg];
		Vec3 at = lajur[seg].lerp(lajur[seg + 1], u);
		// a bounce per segment, smaller each time (the rock hops off ledges and rails)
		double hop = Math.abs(Mth.sin((float) (u * Math.PI * 2.0D))) * 1.2D / (seg + 1);
		at = at.add(0.0D, hop, 0.0D);
		Vec3 dir = lajur[seg + 1].subtract(lajur[seg]);
		pindah(at, (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F);
		setNilai((float) s);
		if (umur % 10 == 1) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.7F, 0.5F + level.getRandom().nextFloat() * 0.2F);
		}
		AABB box = getBoundingBox().inflate(0.4D);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
			if (!p.isSpectator() && kena.add(p.getUUID())) {
				int rusak = RUSAK_MIN + level.getRandom().nextInt(RUSAK_MAKS - RUSAK_MIN + 1);
				p.hurtServer(level, level.damageSources().fallingBlock(this), rusak);
				p.push(dir.normalize().x * 0.6D, 0.35D, dir.normalize().z * 0.6D);
			}
		}
	}

	private void hancur(ServerLevel level) {
		if (pecah >= 0) {
			return;
		}
		pecah = 0;
		setKeadaan(KEADAAN_PECAH);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.2F, 0.6F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ANDESITE.defaultBlockState()), getX(), getY() + 0.6D, getZ(),
				16, 0.4D, 0.4D, 0.4D, 0.1D);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (pecah >= 0) {
			return false;
		}
		boolean kehancuran = source.getEntity() instanceof ServerPlayer p && Kehancuran.selfOn(p);
		if (kehancuran || damage >= 8.0F) {
			hancur(level);
			return true;
		}
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
