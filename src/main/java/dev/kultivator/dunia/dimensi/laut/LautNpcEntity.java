package dev.kultivator.dunia.dimensi.laut;

import dev.kultivator.dunia.dimensi.DimensiData;
import dev.kultivator.dunia.dimensi.DimensiNpc;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:raja_naga} (DESIGN 34.5.14): the people of Laut Tanpa Batas, drawn with the disciple player model.
 * <ul>
 * <li>{@link #RAJA_NAGA}: the Dragon King of the Eastern Sea in his Crystal Palace (Journey to the West ch. 3), in dragon
 * robes with horns, a sea pearl in his hand; audience from Deity Transformation, trades, his armoury and quests.</li>
 * <li>{@link #PENJAGA}: the palace gate guard; gives the Mutiara Penghindar Air after the first quest; bows to a True
 * Immortal.</li>
 * <li>{@link #PERTAPA}: the hermit of a Pulau Pertapa; sits in meditation while nobody is near.</li>
 * <li>{@link #DEWA_PENGLAI}: the immortal of Penglai who tests Dao Union cultivators; meditates under the peach trees.</li>
 * <li>{@link #PEDAGANG}: the island merchant of a Pulau Pasar and of the arrival dock.</li>
 * <li>{@link #NELAYAN}: the old fisherman of the dock and the island piers: for nothing but a little patience he tells
 * where the nearest island of a kind lies, and where Penyu Ao swims.</li>
 * </ul>
 */
public class LautNpcEntity extends DimensiNpc {

	public static final int RAJA_NAGA = 0;
	public static final int PENJAGA = 1;
	public static final int PERTAPA = 2;
	public static final int DEWA_PENGLAI = 3;
	public static final int PEDAGANG = 4;
	public static final int NELAYAN = 5;
	public static final int ROLES = 6;
	private static final String[] IDS = {"raja_naga", "penjaga", "pertapa", "dewa_penglai", "pedagang", "nelayan"};

	public LautNpcEntity(EntityType<? extends LautNpcEntity> type, Level level) {
		super(type, level);
	}

	public void setup(int role, int look, int realm) {
		int r = Mth.clamp(role, 0, ROLES - 1);
		setIdentity(r, look, 0);
		int rr = Math.max(3, realm);
		switch (r) {
			case DEWA_PENGLAI -> setStats(120.0D + 40.0D * rr, 8.0D + 2.5D * rr, 10.0D + rr, 0.3D);
			case PENJAGA -> setStats(80.0D + 25.0D * rr, 6.0D + 2.0D * rr, 8.0D + rr, 0.28D);
			default -> setStats(200.0D, 10.0D, 10.0D, 0.25D);
		}
		switch (r) {
			case PENJAGA -> this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
			case RAJA_NAGA -> this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.HEART_OF_THE_SEA));
			case NELAYAN -> this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
			default -> {
			}
		}
	}

	public int role() {
		return Mth.clamp(roleIndex(), 0, ROLES - 1);
	}

	public static String roleId(int role) {
		return IDS[Mth.clamp(role, 0, ROLES - 1)];
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.kultivator.raja_naga." + roleId(role()));
	}

	@Override
	protected boolean wanders() {
		return role() == PERTAPA;
	}

	@Override
	protected boolean hostileToPlayers() {
		return false;
	}

	@Override
	protected boolean meditates() {
		if (role() == PERTAPA || role() == DEWA_PENGLAI) {
			return true;
		}
		// the daily round of the harbour folk: the fisherman and the merchant sit and doze through the night (the sea
		// keeps the overworld's day), up again at dawn; a player coming near wakes them (DimensiNpc's ambient pass)
		return (role() == NELAYAN || role() == PEDAGANG) && this.level() instanceof ServerLevel level
				&& DimensiData.night(level.getServer());
	}

	@Override
	protected boolean bowsToImmortals() {
		return role() == PENJAGA || role() == NELAYAN || role() == PEDAGANG;
	}

	@Override
	public @Nullable String chatterKey() {
		return switch (role()) {
			case RAJA_NAGA -> "raja_naga";
			case PENJAGA -> "penjaga_laut";
			case PERTAPA -> "pertapa";
			case DEWA_PENGLAI -> "dewa_penglai";
			case PEDAGANG -> "pedagang";
			default -> "nelayan";
		};
	}

	@Override
	public boolean ofTheSea() {
		return true;
	}

	@Override
	protected boolean onInteract(ServerPlayer player) {
		return SeaPeople.interact(player, this);
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}
}
