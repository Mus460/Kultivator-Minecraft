package dev.kultivator.dunia.kota42.ekonomi;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

/**
 * {@code kultivator:tenda_lapak} (DESIGN 42.12.2, 42.6.2 "lapak sewa"): the rented market stall of a player or a townsfolk
 * business, a striped cloth canopy 32 x 2 x 32 pixels on four poles over a mat of 24 x 1 x 16 with a crate and baskets;
 * 2 x 2.4 blocks, never saved (re-made by 42C's {@code LapakKota} on its market titik), invulnerable, not solid, pickable
 * (right-click = the stall, 42C).
 * <p>
 * Synced: {@link #varian()} the goods shown ({@link #VARIAN_SAYUR} .. {@link #VARIAN_KAIN}), {@link #keadaan()}
 * {@link #KEADAAN_BERDIRI} / {@link #KEADAAN_DIPASANG} / {@link #KEADAAN_DIBONGKAR} with {@link #nilai()} the assembly
 * progress 0..1 (poles rise, then the cloth unrolls; reversed when taken down, scale {@link #RAKIT_TICK} ticks),
 * {@link #warna()} the canopy stripe colour 0xRRGGBB (the second stripe is always undyed cloth). The cloth ripple is
 * client-only.
 * <p>
 * Behaviour (42C-2): {@link #pasang()} / {@link #bongkar()} run the assembly on the server ({@link #nilai()} moves by
 * {@code 1 / RAKIT_TICK} a tick; a taken-down tent is discarded at 0); right-click goes to the handler 42C's
 * {@code LapakKota} registers with {@link #setPenangan} (none: nothing happens).
 */
public class TendaLapakEntity extends TemaMekanismeEntity {

	public static final int VARIAN_SAYUR = 0;
	public static final int VARIAN_BUAH = 1;
	public static final int VARIAN_GERABAH = 2;
	public static final int VARIAN_OBAT = 3;
	public static final int VARIAN_KAIN = 4;
	public static final int VARIAN_JUMLAH = 5;

	/** Standing, open for trade. */
	public static final int KEADAAN_BERDIRI = 0;
	/** Being put up ({@link #nilai()} 0 -> 1). */
	public static final int KEADAAN_DIPASANG = 1;
	/** Being taken down ({@link #nilai()} 1 -> 0). */
	public static final int KEADAAN_DIBONGKAR = 2;

	public static final int RAKIT_TICK = 20;
	/** Default stripe: faded red. */
	public static final int WARNA_BAWAAN = 0xB0413E;

	private static final EntityDataAccessor<Integer> DATA_WARNA = SynchedEntityData.defineId(TendaLapakEntity.class, EntityDataSerializers.INT);
	private static volatile @Nullable BiPredicate<TendaLapakEntity, ServerPlayer> penangan;

	/** 42C ({@code LapakKota}): what a right-click on a stall does; true when handled. */
	public static void setPenangan(@Nullable BiPredicate<TendaLapakEntity, ServerPlayer> handler) {
		penangan = handler;
	}

	public TendaLapakEntity(EntityType<? extends TendaLapakEntity> type, Level level) {
		super(type, level);
		setNilai(1.0F);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_WARNA, WARNA_BAWAAN);
	}

	/** The canopy stripe colour 0xRRGGBB. */
	public int warna() {
		return this.entityData.get(DATA_WARNA);
	}

	public void setWarna(int rgb) {
		this.entityData.set(DATA_WARNA, rgb & 0xFFFFFF);
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	/** Puts the tent up from bare poles (server). */
	public void pasang() {
		setNilai(0.0F);
		setKeadaan(KEADAAN_DIPASANG);
	}

	/** Takes the tent down; it is discarded when folded (server). */
	public void bongkar() {
		setKeadaan(KEADAAN_DIBONGKAR);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			return;
		}
		if (keadaan() == KEADAAN_DIPASANG) {
			float next = Math.min(1.0F, nilai() + 1.0F / RAKIT_TICK);
			setNilai(next);
			if (next >= 1.0F) {
				setKeadaan(KEADAAN_BERDIRI);
			}
		} else if (keadaan() == KEADAAN_DIBONGKAR) {
			float next = nilai() - 1.0F / RAKIT_TICK;
			if (next <= 0.0F) {
				this.discard();
			} else {
				setNilai(next);
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
		BiPredicate<TendaLapakEntity, ServerPlayer> handler = penangan;
		return handler != null && player instanceof ServerPlayer sp && handler.test(this, sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}
}
