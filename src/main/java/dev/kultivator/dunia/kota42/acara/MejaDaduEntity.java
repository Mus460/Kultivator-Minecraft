package dev.kultivator.dunia.kota42.acara;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:meja_dadu} (DESIGN 42.12.2, 42.9.6 "Guanpu" gambling den): the dice table, 1.3 x 0.9 blocks, a 20 x 12 x 14
 * pixel table with green cloth, a 4 x 5 x 4 bamboo cup and three dice. Never saved (42D's {@code DistrikMalam} puts it in the
 * den at night), invulnerable, solid, pickable: a click opens the {@code guanpu} screen (42D {@code Guanpu}, 42U).
 * <p>
 * Synced: {@link #varian()} the table ({@link #VARIAN_KEDAI} a tavern corner, {@link #VARIAN_GUANPU} the licensed den with a
 * lantern, {@link #VARIAN_GELAP} the Ghost Market's), {@link #keadaan()} {@link #KEADAAN_DIAM} / {@link #KEADAAN_KOCOK} (the
 * cup shakes for {@link #KOCOK_TICK} ticks) / {@link #KEADAAN_BUKA} (cup lifted, dice shown), {@link #dadu()} the three
 * faces packed 3 bits each ({@link #pack}; 0 = hidden) so every client shows the same throw; the dice tumble client-side.
 * <p>
 * Behaviour (42D): a click opens {@code guanpu} ({@link Guanpu#buka}); {@link #kocok} shakes the cup with the faces hidden
 * and reveals them after {@link #KOCOK_TICK} ticks (the server already knows the throw; the reveal is for the eyes).
 */
public class MejaDaduEntity extends TemaMekanismeEntity {

	public static final int VARIAN_KEDAI = 0;
	public static final int VARIAN_GUANPU = 1;
	public static final int VARIAN_GELAP = 2;

	public static final int KEADAAN_DIAM = 0;
	public static final int KEADAAN_KOCOK = 1;
	public static final int KEADAAN_BUKA = 2;

	public static final int KOCOK_TICK = 20;

	private static final EntityDataAccessor<Integer> DATA_DADU = SynchedEntityData.defineId(MejaDaduEntity.class, EntityDataSerializers.INT);

	public MejaDaduEntity(EntityType<? extends MejaDaduEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_DADU, 0);
	}

	/** The packed faces ({@link #mata}). */
	public int dadu() {
		return this.entityData.get(DATA_DADU);
	}

	/** Shows the throw {@code a}, {@code b}, {@code c} (1-6 each; 0 hides that die). */
	public void setDadu(int a, int b, int c) {
		this.entityData.set(DATA_DADU, pack(a, b, c));
	}

	/** Packs three faces (each clamped to 0-6) into 9 bits. */
	public static int pack(int a, int b, int c) {
		return wajah(a) | wajah(b) << 3 | wajah(c) << 6;
	}

	/** Face of die {@code i} (0-2) in a packed value: 1-6, 0 = hidden. */
	public static int mata(int packed, int i) {
		return packed >>> (3 * i) & 7;
	}

	private static int wajah(int n) {
		return Math.max(0, Math.min(6, n));
	}

	private int tertunda;

	/** Shakes the cup now and shows {@code a}, {@code b}, {@code c} when it lifts (server). */
	public void kocok(int a, int b, int c) {
		tertunda = pack(a, b, c);
		this.entityData.set(DATA_DADU, 0);
		setKeadaan(KEADAAN_DIAM);
		setKeadaan(KEADAAN_KOCOK);
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel level && keadaan() == KEADAAN_KOCOK && level.getGameTime() - mulai() >= KOCOK_TICK) {
			this.entityData.set(DATA_DADU, tertunda);
			setKeadaan(KEADAAN_BUKA);
		}
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			Guanpu.buka(sp, this);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean padat() {
		return true;
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}
}
