package dev.kultivator.dunia.kota42.acara;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:panggung_keliling} (DESIGN 42.12.2, 42.7.1 "Rombongan keliling"): the travelling troupe's wooden stage,
 * 5 x 1 x 3 blocks (floor 80 x 4 x 48 pixels, four 40-pixel poles, a cloth roof and pennants). Never saved (42D's
 * {@code Rombongan} sets it up on the plaza titik and takes it down when the troupe leaves), invulnerable; the walkable
 * floor uses separate colliders of the {@code ColliderEntity} pattern (42D), the stage itself is not solid.
 * <p>
 * Synced: {@link #varian()} the troupe ({@link #VARIAN_OPERA} .. {@link #VARIAN_PENDONGENG}: banners, roof cloth and props
 * differ), {@link #keadaan()} {@link #KEADAAN_TERBONGKAR} (bundled on a cart, hidden), {@link #KEADAAN_DIRAKIT},
 * {@link #KEADAAN_BERDIRI}, {@link #KEADAAN_DIBONGKAR} with {@link #nilai()} the assembly progress 0..1 (floor planks,
 * then poles, then roof and pennants, over {@link #RAKIT_TICK} ticks), {@link #panji()} the pennant text index (the
 * troupe's name glyph pair, 42V atlas). The pennants flutter client-side.
 * <p>
 * Behaviour (42D): {@link #rakit} raises the stage part by part over {@link #RAKIT_TICK} ticks, {@link #bongkar} takes it
 * down the same way and the entity leaves when bundled. Performers stand on the floor at {@link #LANTAI} above the
 * entity's feet, at the spots of {@link #titik} (front centre, the two wings, the back row).
 */
public class PanggungKelilingEntity extends TemaMekanismeEntity {

	/** Opera troupe: red roof, embroidered backdrop. */
	public static final int VARIAN_OPERA = 0;
	/** Acrobats: hoops, ropes and a plate rack. */
	public static final int VARIAN_AKROBAT = 1;
	/** Musicians: drum and gong stand. */
	public static final int VARIAN_PEMUSIK = 2;
	/** Storyteller: a table, a fan, the wooden block. */
	public static final int VARIAN_PENDONGENG = 3;
	public static final int VARIAN_JUMLAH = 4;

	public static final int KEADAAN_TERBONGKAR = 0;
	public static final int KEADAAN_DIRAKIT = 1;
	public static final int KEADAAN_BERDIRI = 2;
	public static final int KEADAAN_DIBONGKAR = 3;

	/** Assembly length (42.12.2: "skala 20 tick" per part, three parts). */
	public static final int RAKIT_TICK = 60;

	private static final EntityDataAccessor<Integer> DATA_PANJI = SynchedEntityData.defineId(PanggungKelilingEntity.class, EntityDataSerializers.INT);

	public PanggungKelilingEntity(EntityType<? extends PanggungKelilingEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PANJI, 0);
	}

	/** Height of the plank floor above the entity's feet (4 pixels plus the trestles). */
	public static final double LANTAI = 0.35D;

	/** Raises the stage from bundled (server). */
	public void rakit() {
		setNilai(0.0F);
		setKeadaan(KEADAAN_DIRAKIT);
	}

	/** Takes the stage down; it leaves when bundled (server). */
	public void bongkar() {
		if (keadaan() != KEADAAN_DIBONGKAR && keadaan() != KEADAAN_TERBONGKAR) {
			setKeadaan(KEADAAN_DIBONGKAR);
		}
	}

	/** True when fully standing. */
	public boolean berdiri() {
		return keadaan() == KEADAAN_BERDIRI;
	}

	/**
	 * A performer's spot on the floor: 0 front centre, 1 front left, 2 front right, 3 back centre, 4 back left, 5 back right
	 * (the stage is 5 blocks wide and 3 deep; "front" faces the audience along the entity's yaw).
	 */
	public Vec3 titik(int i) {
		double r = Math.toRadians(getYRot());
		Vec3 maju = new Vec3(-Math.sin(r), 0.0D, Math.cos(r));
		Vec3 sisi = new Vec3(Math.cos(r), 0.0D, Math.sin(r));
		int k = Math.floorMod(i, 6);
		double f = k < 3 ? 0.7D : -0.8D;
		double s = switch (k % 3) {
			case 1 -> 1.6D;
			case 2 -> -1.6D;
			default -> 0.0D;
		};
		return position().add(maju.scale(f)).add(sisi.scale(s)).add(0.0D, LANTAI, 0.0D);
	}

	/** The spot in front of the stage where the audience looks from ({@code jarak} blocks out). */
	public Vec3 depan(double jarak) {
		double r = Math.toRadians(getYRot());
		return position().add(-Math.sin(r) * jarak, 0.0D, Math.cos(r) * jarak);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel)) {
			return;
		}
		float step = 1.0F / RAKIT_TICK;
		if (keadaan() == KEADAAN_DIRAKIT) {
			float v = Math.min(1.0F, nilai() + step);
			setNilai(v);
			if (v >= 1.0F) {
				setKeadaan(KEADAAN_BERDIRI);
			}
		} else if (keadaan() == KEADAAN_DIBONGKAR) {
			float v = Math.max(0.0F, nilai() - step);
			setNilai(v);
			if (v <= 0.0F) {
				setKeadaan(KEADAAN_TERBONGKAR);
				discard();
			}
		}
	}

	/** The pennant text index (0 = plain pennants). */
	public int panji() {
		return this.entityData.get(DATA_PANJI);
	}

	public void setPanji(int panji) {
		if (panji() != Math.max(0, panji)) {
			this.entityData.set(DATA_PANJI, Math.max(0, panji));
		}
	}
}
