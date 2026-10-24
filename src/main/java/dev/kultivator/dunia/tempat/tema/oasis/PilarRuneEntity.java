package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:pilar_rune} (DESIGN 38.T.9). Tiang formasi penyedot air di aula ketiga reruntuhan (38.T.4.7
 * bab 4): pilar bergaris rune pancar yang berdenyut; empat tiang dihancurkan atau disegel dengan air suci.
 * <p>
 * {@link #nilai()}: kekuatan denyut 0..1. Never saved; made by {@link Reruntuhan} around the scorpion king's nest for a
 * player in chain stage {@code formasi}, with the state he left it in. Three hits of a player break it
 * ({@link #KEADAAN_PECAH}); a right-click holding Air Suci seals it ({@link #KEADAAN_TERSEGEL}, one flask used, an empty
 * bottle back). The renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link #mulai()} and {@link #nilai()}
 * only; the rune circle between the four is drawn by the client from their states.
 */
public class PilarRuneEntity extends TemaMekanismeEntity {

	/** Rune hijau-air. */
	public static final int VARIAN_BIASA = 0;

	/** Rune berdenyut. */
	public static final int KEADAAN_AKTIF = 0;
	/** Retak dan memudar. */
	public static final int KEADAAN_PECAH = 1;
	/** Rune biru tenang. */
	public static final int KEADAAN_TERSEGEL = 2;

	/** Hits that break a pillar. */
	public static final int PUKUL = 3;

	private long ruin;
	private int nomor;
	private int pukul;

	public PilarRuneEntity(EntityType<? extends PilarRuneEntity> type, Level level) {
		super(type, level);
	}

	/** Server: binds the pillar to ruin {@code ruin} as pillar {@code nomor} 0-3 in state {@code keadaan}. */
	void pasang(long ruin, int nomor, int keadaan) {
		this.ruin = ruin;
		this.nomor = nomor;
		setKeadaan(keadaan);
		setNilai(keadaan == KEADAAN_AKTIF ? 1.0F : 0.0F);
	}

	long ruin() {
		return ruin;
	}

	int nomor() {
		return nomor;
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	@Override
	protected boolean padat() {
		return keadaan() != KEADAAN_PECAH;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level) || keadaan() != KEADAAN_AKTIF || this.tickCount % 20 != 0) {
			return;
		}
		float pulse = (float) (0.6D + 0.4D * Math.sin(level.getGameTime() * 0.1D + nomor));
		setNilai(pulse);
		// water drawn up out of the ground into the rune
		level.sendParticles(ParticleTypes.DRIPPING_WATER, getX(), getY() + 1.8D, getZ(), 3, 0.2D, 0.3D, 0.2D, 0.0D);
		level.sendParticles(ParticleTypes.GLOW, getX(), getY() + 1.0D, getZ(), 2, 0.25D, 0.6D, 0.25D, 0.01D);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (keadaan() != KEADAAN_AKTIF || !(source.getEntity() instanceof ServerPlayer player)) {
			return false;
		}
		pukul++;
		level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F + pukul * 0.15F);
		level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.0D, getZ(), 10, 0.3D, 0.5D, 0.3D, 0.2D);
		if (pukul >= PUKUL) {
			setKeadaan(KEADAAN_PECAH);
			setNilai(0.0F);
			level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1.0D, getZ(), 40, 0.5D, 0.8D, 0.5D, 0.2D);
			Reruntuhan.pilarBerubah(player, this);
		}
		return true;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		ItemStack held = player.getItemInHand(hand);
		if (keadaan() != KEADAAN_AKTIF || !held.is(TemaItems.AIR_SUCI)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp && this.level() instanceof ServerLevel level) {
			if (!sp.isCreative()) {
				held.shrink(1);
				ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
				OasisKota.beri(sp, bottle);
			}
			setKeadaan(KEADAAN_TERSEGEL);
			setNilai(0.3F);
			level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.2F);
			level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 1.5F);
			level.sendParticles(ParticleTypes.GLOW, getX(), getY() + 1.0D, getZ(), 30, 0.4D, 0.9D, 0.4D, 0.02D);
			Reruntuhan.pilarBerubah(sp, this);
		}
		return InteractionResult.SUCCESS;
	}
}
