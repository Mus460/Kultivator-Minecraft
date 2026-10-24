package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:pintu_pasir} (DESIGN 38.T.9). Pintu aula reruntuhan gurun (38.T.2.6, pola {@code
 * PintuTebingEntity}): pelat batu pasir bertimbun yang bergeser membuka; tertutup = padat.
 * <p>
 * {@link #nilai()}: geser 0..1. Never saved; made by {@link Reruntuhan} from the ruin's {@code kota_tema/pintu_pasir}
 * anchor (data {@code ruang} 1-3). Hall 1 opens with a click; halls 2 and 3 need a Lempeng Peta in the inventory (the
 * plate shows where the sliding stone gives). Opening takes 30 ticks of grinding stone and dust; it stays open for the
 * body's life (the body goes when the last player leaves, so the door is shut again on the next visit). The renderer
 * (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link #mulai()} and {@link #nilai()} only.
 */
public class PintuPasirEntity extends TemaMekanismeEntity {

	/** Batu pasir berukir. */
	public static final int VARIAN_BIASA = 0;
	/**
	 * A leaf of the Gerbang Giok (2 wide, 3 tall: jungle planks with jade-green bosses and bronze bands), four of them
	 * close the 7-wide arch during a sandstorm ({@link BadaiPasir#gerbangDekat}); not opened by hand.
	 */
	public static final int VARIAN_GERBANG = 1;

	/** Menutup lorong. */
	public static final int KEADAAN_TERTUTUP = 0;
	/** Bergeser 30 tick, debu. */
	public static final int KEADAAN_MEMBUKA = 1;
	/** Terbuka. */
	public static final int KEADAAN_TERBUKA = 2;

	public static final int BUKA_TICK = 30;

	private int ruang = 1;

	public PintuPasirEntity(EntityType<? extends PintuPasirEntity> type, Level level) {
		super(type, level);
	}

	void setRuang(int ruang) {
		this.ruang = ruang;
		setVarian(VARIAN_BIASA);
	}

	/** Makes this body a closed leaf of the Gerbang Giok. */
	void jadiGerbang() {
		setVarian(VARIAN_GERBANG);
		setKeadaan(KEADAAN_TERTUTUP);
		setNilai(0.0F);
	}

	public int ruang() {
		return ruang;
	}

	@Override
	protected boolean padat() {
		return keadaan() != KEADAAN_TERBUKA;
	}

	@Override
	protected boolean bisaDipilih() {
		return keadaan() == KEADAAN_TERTUTUP && varian() != VARIAN_GERBANG;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level) || keadaan() != KEADAAN_MEMBUKA) {
			return;
		}
		int age = umurKeadaan(level.getGameTime());
		setNilai(Math.min(1.0F, age / (float) BUKA_TICK));
		if (age % 5 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SANDSTONE.defaultBlockState()), getX(), getY() + 1.5D, getZ(), 12,
					0.9D, 1.2D, 0.3D, 0.05D);
		}
		if (age >= BUKA_TICK) {
			setKeadaan(KEADAAN_TERBUKA);
			setNilai(1.0F);
		}
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND || keadaan() != KEADAAN_TERTUTUP || varian() == VARIAN_GERBANG) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp && this.level() instanceof ServerLevel level) {
			if (ruang > 1 && sp.getInventory().countItem(TemaItems.LEMPENG_PETA) == 0) {
				Msg.actionBar(sp, Component.translatable("kultivator.kota_tema.oasis.pintu.terkunci").withStyle(ChatFormatting.YELLOW));
				level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.5F, 0.5F);
				return InteractionResult.SUCCESS;
			}
			setKeadaan(KEADAAN_MEMBUKA);
			level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.2F, 0.5F);
			Msg.actionBar(sp, Component.translatable("kultivator.kota_tema.oasis.pintu.terbuka", ruang).withStyle(ChatFormatting.GOLD));
		}
		return InteractionResult.SUCCESS;
	}
}
