package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import dev.kultivator.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:tong_minyak} (DESIGN 38.T.9). Tong Minyak Bayangan di gudang Sarang Kontrak (38.T.7.11 bab 3,
 * cabang sabotase): tong bersegel yang bisa dihancurkan pemain (entitas, bukan blok).
 * <p>
 * {@link #nilai()}: rembes 0..1. Never saved; re-made from its anchor or titik. Behaviour (spline, schedule,
 * clicks) by 38TK-I: a player's blow breaks it on chapter 3 ({@link SarangKontrak#pukul}); the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link
 * #mulai()} and {@link #nilai()} only.
 */
public class TongMinyakEntity extends TemaMekanismeEntity {

	/** Tong hitam bersegel. */
	public static final int VARIAN_BIASA = 0;

	/** Utuh. */
	public static final int KEADAAN_UTUH = 0;
	/** Pecah, asap hitam. */
	public static final int KEADAAN_PECAH = 1;

	public TongMinyakEntity(EntityType<? extends TongMinyakEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	private int nomor;

	/** Barrel index 0-5 round the store's anchor (the broken-barrel bit of the town). */
	public int nomor() {
		return nomor;
	}

	public void setNomor(int nomor) {
		this.nomor = nomor;
	}

	/** A player's blow breaks the barrel on chapter 3 of the chain ({@link SarangKontrak#pukul}); nothing else hurts it. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() instanceof ServerPlayer player && keadaan() == KEADAAN_UTUH) {
			SarangKontrak.pukul(player, this);
		}
		return false;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
			Msg.actionBar(sp, Component.translatable(keadaan() == KEADAAN_PECAH ? "kultivator.kota_tema.iblis.minyak.lihat_pecah"
					: "kultivator.kota_tema.iblis.minyak.lihat").withStyle(ChatFormatting.DARK_GRAY));
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}
}
