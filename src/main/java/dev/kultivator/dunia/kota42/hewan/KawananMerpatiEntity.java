package dev.kultivator.dunia.kota42.hewan;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code kultivator:kawanan_merpati} (DESIGN 42.7.5, 42.12.2): a flock of pigeons on a plaza or temple roof as ONE entity,
 * 2 x 1 blocks, without collision. The 8-16 birds are a client boid (cohesion 0.02, separation 0.6 blocks, alignment 0.05;
 * {@code EffectQuality} caps 4 / 8 / 16) seeded by {@link #benih()}, so every client draws the same flock. Never saved
 * ({@link HewanKota}), invulnerable; a mechanism, not a mob (it never paths, it only changes state and drifts).
 * <p>
 * Server behaviour (42D-H): {@link #KEADAAN_MENDARAT} on its landing spot, pecking; a player within 3 blocks, a sprinting
 * player within 8, a monster within 8, a danger zone, a stalking cat ({@link #kagetkan}) or the 05:00 temple bell sends the
 * whole flock up ({@link #KEADAAN_TERBANG}: a 1.5 s climb to 7 blocks above, away from the fright), then it circles
 * ({@link #KEADAAN_BERPUTAR}, radius {@link #nilai()} 4-7 blocks) for 10-25 s and glides down again, most times to the same
 * spot, sometimes to another spot of the town ({@link #setTempat}). Every few minutes it takes a circuit on its own.
 * An old resident hanging out his bird cage (clip 354 {@code gantung_sangkar}, habit {@code burung_sangkar}) draws a
 * landed flock within 24 blocks down beside him.
 * <p>
 * Synced: {@link #varian()} the colour mix ({@link #VARIAN_ABU}, {@link #VARIAN_PUTIH}, {@link #VARIAN_CAMPUR}),
 * {@link #keadaan()} {@link #KEADAAN_MENDARAT} (pecking and nodding on the ground), {@link #KEADAAN_TERBANG} (all take off,
 * wings flap every 6 ticks), {@link #KEADAAN_BERPUTAR} (circling above the roof), {@link #jumlah()} the birds in the flock
 * ({@link #JUMLAH_MIN}-{@link #JUMLAH_MAKS}), {@link #benih()} the boid seed, {@link #nilai()} the circling radius in blocks
 * (the landed spread, 1.2, while on the ground).
 */
public class KawananMerpatiEntity extends TemaMekanismeEntity {

	public static final int VARIAN_ABU = 0;
	public static final int VARIAN_PUTIH = 1;
	public static final int VARIAN_CAMPUR = 2;
	public static final int VARIAN_JUMLAH = 3;

	public static final int KEADAAN_MENDARAT = 0;
	public static final int KEADAAN_TERBANG = 1;
	public static final int KEADAAN_BERPUTAR = 2;

	public static final int JUMLAH_MIN = 8;
	public static final int JUMLAH_MAKS = 16;

	/** Height of the circling above the landing spot. */
	public static final double TINGGI_PUTAR = 7.0D;
	/** The landed spread drawn by the client (blocks). */
	public static final float SEBAR_DARAT = 1.2F;

	private static final EntityDataAccessor<Integer> DATA_JUMLAH = SynchedEntityData.defineId(KawananMerpatiEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_BENIH = SynchedEntityData.defineId(KawananMerpatiEntity.class, EntityDataSerializers.INT);

	/** Where it lands now, and every landing spot of its town (its own first). */
	private @Nullable Vec3 tanah;
	private final List<Vec3> tempat = new ArrayList<>();
	/** The flight leg: from, to, start game time, length in ticks, and whether it ends on the ground. */
	private Vec3 dari = Vec3.ZERO;
	private Vec3 ke = Vec3.ZERO;
	private long terbangT;
	private int terbangLama;
	private boolean mendaratDiAkhir;
	/** End of the circling, and the next circuit of its own (game time). */
	private long putarSampai;
	private long putaranBerikut;
	/** World day of the last dawn take-off. */
	private long pagiHari = -1L;

	public KawananMerpatiEntity(EntityType<? extends KawananMerpatiEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_JUMLAH, 12);
		builder.define(DATA_BENIH, 0);
	}

	public int jumlah() {
		return this.entityData.get(DATA_JUMLAH);
	}

	public int benih() {
		return this.entityData.get(DATA_BENIH);
	}

	public void setJumlah(int jumlah) {
		this.entityData.set(DATA_JUMLAH, Mth.clamp(jumlah, JUMLAH_MIN, JUMLAH_MAKS));
	}

	public void setBenih(int benih) {
		this.entityData.set(DATA_BENIH, benih);
	}

	/** Its landing spot and the other spots of the town it may glide to (server, right after spawning). */
	public void setTempat(Vec3 sendiri, List<Vec3> lain) {
		this.tanah = sendiri;
		this.tempat.clear();
		this.tempat.add(sendiri);
		for (Vec3 v : lain) {
			if (v.distanceToSqr(sendiri) > 4.0D && v.distanceToSqr(sendiri) < 48.0D * 48.0D) {
				this.tempat.add(v);
			}
		}
		setNilai(SEBAR_DARAT);
	}

	/** Its current landing spot (null before {@link #setTempat}). */
	public @Nullable Vec3 tanah() {
		return tanah;
	}

	// ================================================================== the flock

	/** Sends the landed flock up, away from {@code sumber}; nothing when already airborne. */
	public void kagetkan(Vec3 sumber) {
		if (keadaan() != KEADAAN_MENDARAT || this.level().isClientSide()) {
			return;
		}
		Vec3 here = this.position();
		Vec3 away = here.subtract(sumber);
		away = away.horizontalDistanceSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(away.x, 0.0D, away.z).normalize();
		Vec3 puncak = here.add(away.scale(3.0D)).add(0.0D, TINGGI_PUTAR, 0.0D);
		terbang(puncak, 30, false);
		this.level().playSound(null, here.x, here.y, here.z, SoundEvents.PARROT_FLY, SoundSource.NEUTRAL, 1.0F, 0.75F);
		this.level().playSound(null, here.x, here.y, here.z, SoundEvents.BAT_TAKEOFF, SoundSource.NEUTRAL, 0.5F, 1.6F);
	}

	/** Brings the flock down beside {@code pos} (a resident's bird cage, feed thrown on the plaza); lands there. */
	public void datangKe(Vec3 pos) {
		if (keadaan() == KEADAAN_TERBANG) {
			return;
		}
		Vec3 here = this.position();
		int lama = (int) Mth.clamp(here.distanceTo(pos) * 2.5D, 30.0D, 120.0D);
		this.tanah = pos;
		terbang(pos, lama, true);
		if (keadaan() == KEADAAN_MENDARAT) {
			this.level().playSound(null, here.x, here.y, here.z, SoundEvents.PARROT_FLY, SoundSource.NEUTRAL, 0.7F, 0.9F);
		}
	}

	private void terbang(Vec3 tujuan, int lama, boolean darat) {
		this.dari = this.position();
		this.ke = tujuan;
		this.terbangT = this.level().getGameTime();
		this.terbangLama = Math.max(10, lama);
		this.mendaratDiAkhir = darat;
		setKeadaan(KEADAAN_TERBANG);
		setNilai(darat ? SEBAR_DARAT * 2.0F : 3.0F);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		if (tanah == null) {
			setTempat(this.position(), List.of());
			putaranBerikut = now + 2400L + this.random.nextInt(2400);
		}
		switch (keadaan()) {
			case KEADAAN_TERBANG -> terbangTick(now);
			case KEADAAN_BERPUTAR -> putarTick(now);
			default -> daratTick(level, now);
		}
	}

	private void terbangTick(long now) {
		float t = Mth.clamp((now - terbangT) / (float) terbangLama, 0.0F, 1.0F);
		// eased leg with a lift in the middle: a climb that rounds off, or a glide that flares at the end
		float e = t * t * (3.0F - 2.0F * t);
		double lift = Math.sin(t * Math.PI) * (mendaratDiAkhir ? 2.5D : 1.0D);
		Vec3 p = dari.lerp(ke, e).add(0.0D, lift, 0.0D);
		Vec3 d = ke.subtract(dari);
		float yaw = d.horizontalDistanceSqr() < 1.0E-4D ? this.getYRot() : (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
		pindah(p, yaw);
		if (t >= 1.0F) {
			if (mendaratDiAkhir) {
				setKeadaan(KEADAAN_MENDARAT);
				setNilai(SEBAR_DARAT);
			} else {
				setKeadaan(KEADAAN_BERPUTAR);
				setNilai(4.0F + this.random.nextFloat() * 3.0F);
				putarSampai = now + 200L + this.random.nextInt(300);
			}
		}
	}

	private void putarTick(long now) {
		// a slow drift of the circle's centre; the birds' circle is drawn by the client around it
		float a = (now % 400L) / 400.0F * Mth.TWO_PI;
		pindah(this.position().add(Math.cos(a) * 0.02D, 0.0D, Math.sin(a) * 0.02D), this.getYRot() + 2.0F);
		if (now < putarSampai) {
			return;
		}
		Vec3 land = tanah != null ? tanah : this.position().subtract(0.0D, TINGGI_PUTAR, 0.0D);
		if (tempat.size() > 1 && this.random.nextInt(10) < 3) {
			land = tempat.get(1 + this.random.nextInt(tempat.size() - 1));
			tanah = land;
		}
		int lama = (int) Mth.clamp(this.position().distanceTo(land) * 3.0D, 40.0D, 160.0D);
		terbang(land, lama, true);
	}

	private void daratTick(ServerLevel level, long now) {
		if (Math.floorMod(this.tickCount + this.getId(), 10) != 0) {
			return;
		}
		long waktu = Math.floorMod(level.getOverworldClockTime(), 24000L);
		long hari = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		if (hari != pagiHari && waktu >= AyamKotaEntity.WAKTU_KOKOK && waktu < AyamKotaEntity.WAKTU_KOKOK + 400L) {
			// the 05:00 temple bell
			pagiHari = hari;
			kagetkan(this.position().add(this.random.nextDouble() - 0.5D, 0.0D, this.random.nextDouble() - 0.5D));
			return;
		}
		Entity takut = penakut(level);
		if (takut != null) {
			kagetkan(takut.position());
			return;
		}
		if (HidupApi.bahayaDi(level, this.blockPosition()) != null) {
			kagetkan(this.position().add(1.0D, 0.0D, 0.0D));
			return;
		}
		if (now >= putaranBerikut) {
			putaranBerikut = now + 2400L + this.random.nextInt(3600);
			kagetkan(this.position().add(this.random.nextDouble() - 0.5D, 0.0D, this.random.nextDouble() - 0.5D));
			return;
		}
		if (this.random.nextInt(12) == 0) {
			KultivatorNpc tetua = pemeliharaBurung(level);
			if (tetua != null && tetua.distanceToSqr(this) > 9.0D) {
				Vec3 di = tetua.position().add(this.random.nextDouble() * 3.0D - 1.5D, 0.0D, this.random.nextDouble() * 3.0D - 1.5D);
				datangKe(di);
			}
		}
	}

	/** What frightens a landed flock now: a player within 3 (or sprinting within 8), a monster within 8. */
	private @Nullable Entity penakut(ServerLevel level) {
		for (Player p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			double d2 = p.distanceToSqr(this);
			if (d2 <= 9.0D || p.isSprinting() && d2 <= 64.0D) {
				return p;
			}
		}
		List<Monster> monsters = level.getEntitiesOfClass(Monster.class, this.getBoundingBox().inflate(8.0D), Entity::isAlive);
		return monsters.isEmpty() ? null : monsters.getFirst();
	}

	private @Nullable KultivatorNpc pemeliharaBurung(ServerLevel level) {
		List<KultivatorNpc> npcs = level.getEntitiesOfClass(KultivatorNpc.class, this.getBoundingBox().inflate(24.0D),
				n -> n.isAlive() && SosokApi.gerakSekarang(n) == KotaGerakId.GANTUNG_SANGKAR);
		return npcs.isEmpty() ? null : npcs.getFirst();
	}
}
