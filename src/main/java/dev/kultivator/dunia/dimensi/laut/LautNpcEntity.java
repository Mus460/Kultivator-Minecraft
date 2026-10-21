package dev.kultivator.dunia.dimensi.laut;

import dev.kultivator.dunia.dimensi.DimensiNpc;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:raja_naga} (DESIGN 34.5.14): the people of Laut Tanpa Batas, drawn with the disciple player model.
 * <ul>
 * <li>{@link #RAJA_NAGA}: the Dragon King of the Eastern Sea in his Crystal Palace (Journey to the West ch. 3), in dragon
 * robes with horns; audience from Deity Transformation, trades and quests.</li>
 * <li>{@link #PENJAGA}: the palace gate guard; gives the Mutiara Penghindar Air after the first quest.</li>
 * <li>{@link #PERTAPA}: the hermit of a Pulau Pertapa.</li>
 * <li>{@link #DEWA_PENGLAI}: the immortal of Penglai who tests Dao Union cultivators.</li>
 * <li>{@link #PEDAGANG}: the island merchant of a Pulau Pasar.</li>
 * </ul>
 */
public class LautNpcEntity extends DimensiNpc {

	public static final int RAJA_NAGA = 0;
	public static final int PENJAGA = 1;
	public static final int PERTAPA = 2;
	public static final int DEWA_PENGLAI = 3;
	public static final int PEDAGANG = 4;
	public static final int ROLES = 5;
	private static final String[] IDS = {"raja_naga", "penjaga", "pertapa", "dewa_penglai", "pedagang"};

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
		if (r == PENJAGA) {
			this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT));
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
