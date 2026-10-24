package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:temuan_pasir} (DESIGN 38.T.9). Temuan Pasir sesudah badai (38.T.4.6): 2-4 benda setengah
 * tertanam di bukit pasir 60-300 blok dari kota, berkilau berkala (pola {@code TandaEntity}); klik memberi Pecahan
 * Mural, Koin Kuno atau Lempeng Peta.
 * <p>
 * {@link #nilai()}: kilau 0..1. Never saved; re-made from the town data by {@link BadaiPasir} whenever a player comes
 * within 48 blocks, or made by {@link Reruntuhan} at a ruin's {@code kota_tema/temuan_reruntuhan} anchor (data id
 * {@code r:...}, once per player). A right-click gives the find ({@link BadaiPasir#ambil}, {@link Reruntuhan#ambil}); the
 * body then fades for 20 ticks and goes.
 * The renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link #mulai()} and {@link #nilai()} only.
 */
public class TemuanPasirEntity extends TemaMekanismeEntity {

	/** Koin kuno. */
	public static final int VARIAN_KOIN = 0;
	/** Pecahan mural. */
	public static final int VARIAN_MURAL = 1;
	/** Lempeng peta. */
	public static final int VARIAN_PETA = 2;

	/** Kilau berkala. */
	public static final int KEADAAN_TERTANAM = 0;
	/** Memudar. */
	public static final int KEADAAN_DIAMBIL = 1;

	/** Glint period (ticks) of the {@link #nilai()} pulse. */
	public static final int KILAU_PERIODE = 60;

	private String dataId = "";
	private int hilang = -1;

	public TemuanPasirEntity(EntityType<? extends TemuanPasirEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean bisaDipilih() {
		return keadaan() == KEADAAN_TERTANAM;
	}

	/** The town-data id of this find ({@code <situs>:<day>:<n>}). */
	public String dataId() {
		return dataId;
	}

	void setDataId(String id) {
		this.dataId = id == null ? "" : id;
	}

	/** Removes the body after {@code ticks} (the fade-out). */
	void hilangSetelah(int ticks) {
		this.hilang = Math.max(1, ticks);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (hilang > 0 && --hilang == 0) {
			this.discard();
			return;
		}
		if (keadaan() == KEADAAN_TERTANAM && this.tickCount % 20 == 0) {
			long t = level.getGameTime() % KILAU_PERIODE;
			setNilai((float) Math.max(0.0D, Math.sin(Math.PI * t / KILAU_PERIODE)));
			if (this.tickCount % KILAU_PERIODE == 0) {
				level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.35D, getZ(), 2, 0.15D, 0.1D, 0.15D, 0.01D);
			}
		}
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer && this.level() instanceof ServerLevel level) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), getX(), getY() + 0.2D, getZ(),
					12, 0.25D, 0.1D, 0.25D, 0.05D);
			if (dataId.startsWith("r:")) {
				Reruntuhan.ambil(serverPlayer, this);
			} else {
				BadaiPasir.ambil(serverPlayer, this);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
