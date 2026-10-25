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
 * {@code kultivator:api_kecil} (DESIGN 42.12.2, 42.7.4 street event "kebakaran kecil"): a small fire, 1 x 1.2 blocks. A
 * mechanism, never a block: it never sets anything alight and never damages blocks (42.0 rule, K6); the flames are six
 * crossed quads of an 8-frame atlas plus three glowing embers, smoke is client-only. Never saved (42D-P's
 * {@code PeristiwaJalan} makes it on a titik), invulnerable to weapons, not solid, pickable: a click with a water bucket,
 * or a bucket passed along the residents' chain (clips 390/391), douses it.
 * <p>
 * Synced: {@link #varian()} what burns ({@link #VARIAN_JERAMI} .. {@link #VARIAN_KAYU}: flame colour and ember size),
 * {@link #keadaan()} {@link #KEADAAN_MENYALA} / {@link #KEADAAN_MENJALAR} (spreading to a neighbouring titik) /
 * {@link #KEADAAN_PADAM} (going out), {@link #nilai()} the size 0..1 (grows while burning, shrinks while doused; 0 = out).
 * <p>
 * Behaviour (42D-P): the street event sets the size; {@link #padamkan()} lets the flames sink to nothing over
 * {@link #PADAM_TICK} ticks, then the body is discarded; a spreading flare returns to burning after
 * {@link #JALAR_TICK} ticks; a fire whose event stopped holding it ({@link #pegang}) goes out by itself, so a reload or a
 * lost event never leaves a fire behind.
 */
public class ApiKecilEntity extends TemaMekanismeEntity {

	/** Straw or a cart load: yellow, fast. */
	public static final int VARIAN_JERAMI = 0;
	/** Cooking oil from a food stall: orange, sputtering. */
	public static final int VARIAN_MINYAK = 1;
	/** Timber and crates: red, slow, big embers. */
	public static final int VARIAN_KAYU = 2;
	public static final int VARIAN_JUMLAH = 3;

	public static final int KEADAAN_MENYALA = 0;
	public static final int KEADAAN_MENJALAR = 1;
	public static final int KEADAAN_PADAM = 2;

	/** Ticks the flames take to sink to nothing once doused. */
	public static final int PADAM_TICK = 20;
	/** Ticks of the spreading flare before it burns steadily again. */
	public static final int JALAR_TICK = 30;

	private long pegangSampai;

	public ApiKecilEntity(EntityType<? extends ApiKecilEntity> type, Level level) {
		super(type, level);
		setNilai(0.5F);
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	/** Server: the owning event keeps the fire alive until game time {@code sampai} (renewed while it runs). */
	public void pegang(long sampai) {
		this.pegangSampai = sampai;
	}

	/** Server: the fire goes out ({@link #KEADAAN_PADAM}); it sinks and is removed. */
	public void padamkan() {
		setKeadaan(KEADAAN_PADAM);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		if (pegangSampai > 0L && now > pegangSampai && keadaan() != KEADAAN_PADAM) {
			padamkan();
		}
		switch (keadaan()) {
			case KEADAAN_PADAM -> {
				float next = nilai() - 1.0F / PADAM_TICK;
				if (next <= 0.0F) {
					this.discard();
				} else {
					setNilai(next);
				}
			}
			case KEADAAN_MENJALAR -> {
				if (umurKeadaan(now) >= JALAR_TICK) {
					setKeadaan(KEADAAN_MENYALA);
				}
			}
			default -> {
			}
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
