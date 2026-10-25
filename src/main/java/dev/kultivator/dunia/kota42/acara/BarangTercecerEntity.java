package dev.kultivator.dunia.kota42.acara;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:barang_tercecer} (DESIGN 42.12.2, 42.7.4 "gerobak terguling"): goods spilled on the street from an
 * overturned cart or a dropped basket, 0.4 x 0.3 blocks. Never saved (42D-P's {@code GerobakTerguling} scatters 6-10 of
 * them around the cart), invulnerable, not solid, pickable: a click gathers it back for the owner (42D-P counts the
 * returned goods; never an item for the player).
 * <p>
 * Synced: {@link #varian()} the thing ({@link #VARIAN_SAYUR} .. {@link #VARIAN_KAIN}), {@link #keadaan()}
 * {@link #KEADAAN_TERGELETAK} / {@link #KEADAAN_DIPUNGUT} (a short lift-and-vanish animation, then it is discarded),
 * {@link #nilai()} the roll angle 0..1 of a full turn so jars and cabbages lie at varied angles.
 * <p>
 * Behaviour (42D-P): {@link #pungut()} starts the lift; after {@link #PUNGUT_TICK} ticks the body is gone. Goods whose
 * event stopped holding them ({@link #pegang}) are gathered by themselves.
 */
public class BarangTercecerEntity extends TemaMekanismeEntity {

	/** Cabbages and radishes. */
	public static final int VARIAN_SAYUR = 0;
	/** A clay jar (cracked lid). */
	public static final int VARIAN_GUCI = 1;
	/** A burst rice sack. */
	public static final int VARIAN_KARUNG = 2;
	/** Oranges and pears. */
	public static final int VARIAN_BUAH = 3;
	/** A bolt of cloth unrolled. */
	public static final int VARIAN_KAIN = 4;
	public static final int VARIAN_JUMLAH = 5;

	public static final int KEADAAN_TERGELETAK = 0;
	public static final int KEADAAN_DIPUNGUT = 1;

	/** Length of the pick-up animation. */
	public static final int PUNGUT_TICK = 10;

	private long pegangSampai;

	public BarangTercecerEntity(EntityType<? extends BarangTercecerEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean bisaDipilih() {
		return keadaan() == KEADAAN_TERGELETAK;
	}

	/** True while it still lies on the street. */
	public boolean tergeletak() {
		return keadaan() == KEADAAN_TERGELETAK && !this.isRemoved();
	}

	/** Server: picked up (the lift animation runs, then the body is removed). */
	public void pungut() {
		setKeadaan(KEADAAN_DIPUNGUT);
	}

	/** Server: the owning event keeps the goods until game time {@code sampai} (renewed while it runs). */
	public void pegang(long sampai) {
		this.pegangSampai = sampai;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		if (keadaan() == KEADAAN_DIPUNGUT) {
			if (umurKeadaan(now) >= PUNGUT_TICK) {
				this.discard();
			}
		} else if (pegangSampai > 0L && now > pegangSampai) {
			pungut();
		}
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		return player instanceof ServerPlayer sp && this.level() instanceof ServerLevel level && PeristiwaJalan.klikEntitas(level, sp, this)
				? InteractionResult.SUCCESS : InteractionResult.PASS;
	}
}
