package dev.kultivator.dunia.dimensi.bawah;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.dimensi.DimensiNpc;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:arwah} (DESIGN 34.5.13): the people of Dunia Bawah, drawn with the disciple player model.
 * <ul>
 * <li>{@link #MENG_PO}: the old woman of the soup at the end of the Naihe bridge (Yuli Baochao); she stirs her cauldron.</li>
 * <li>{@link #HAKIM}: the judge of court {@link #variant()} 1-10 (the Ten Kings: Qinguang ... Zhuanlun), brush and ledger
 * in hand.</li>
 * <li>{@link #RAJA_YAMA}: King Yama (Yanluo) in his palace, keeper of the Book of Life and Death.</li>
 * <li>{@link #KEPALA_LEMBU} / {@link #MUKA_KUDA}: Ox-Head and Horse-Face, the courts' guards and duel partners.</li>
 * <li>{@link #ARWAH}: a ghost (quest giver, wandering soul, or a lost soul following its escort).</li>
 * <li>{@link #HANTU_LAPAR}: a hungry ghost: hunts the living soul, saps strength; the only hostile role. It cowers from a
 * True Immortal (never hunts one on sight, still fights back when struck).</li>
 * <li>{@link #WUCHANG_PUTIH} / {@link #WUCHANG_HITAM}: Bai and Hei Wuchang, the white and black Impermanence of folk
 * belief who lead the dead below; on Teras Wangxiang they greet arriving souls and show the way (a soul lantern and a
 * binding chain, their tall hats).</li>
 * </ul>
 */
public class ArwahEntity extends DimensiNpc {

	public static final int MENG_PO = 0;
	public static final int HAKIM = 1;
	public static final int KEPALA_LEMBU = 2;
	public static final int MUKA_KUDA = 3;
	public static final int ARWAH = 4;
	public static final int HANTU_LAPAR = 5;
	public static final int RAJA_YAMA = 6;
	public static final int WUCHANG_PUTIH = 7;
	public static final int WUCHANG_HITAM = 8;
	public static final int ROLES = 9;
	private static final String[] IDS = {"meng_po", "hakim", "kepala_lembu", "muka_kuda", "arwah", "hantu_lapar", "raja_yama",
			"wuchang_putih", "wuchang_hitam"};

	public ArwahEntity(EntityType<? extends ArwahEntity> type, Level level) {
		super(type, level);
	}

	/**
	 * Role, look seed and variant (court number for judges and guards), with the role's numbers scaled by {@code realm}
	 * (the realm ordinal of the players it meets: Nascent Soul and up in Dunia Bawah). What they hold is only drawn, never
	 * dropped (the stacks are made here, at runtime).
	 */
	public void setup(int role, int look, int variant, int realm) {
		int r = Mth.clamp(role, 0, ROLES - 1);
		setIdentity(r, look, variant);
		int rr = Math.max(4, realm);
		switch (r) {
			case KEPALA_LEMBU, MUKA_KUDA -> setStats(60.0D + 28.0D * rr, 5.0D + 2.2D * rr, 6.0D + rr, 0.3D);
			case HANTU_LAPAR -> setStats(24.0D + 9.0D * rr, 4.0D + 1.6D * rr, 2.0D, 0.29D);
			case ARWAH -> setStats(20.0D, 0.0D, 0.0D, 0.27D);
			case WUCHANG_PUTIH, WUCHANG_HITAM -> setStats(200.0D, 10.0D, 10.0D, 0.24D);
			default -> setStats(200.0D, 10.0D, 10.0D, 0.25D);
		}
		switch (r) {
			// Ox-Head's steel fork, Horse-Face's spear
			case KEPALA_LEMBU, MUKA_KUDA -> this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
			// the judge's brush and ledger
			case HAKIM -> {
				this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FEATHER));
				this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOOK));
			}
			// Bai Wuchang's soul lantern, Hei Wuchang's binding chain
			case WUCHANG_PUTIH -> this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SOUL_LANTERN));
			case WUCHANG_HITAM -> this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_CHAIN));
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

	/** True for the see-through dead (ghosts and hungry ghosts). */
	public boolean ghostly() {
		return role() == ARWAH || role() == HANTU_LAPAR;
	}

	/** True for Bai and Hei Wuchang. */
	public boolean wuchang() {
		return role() == WUCHANG_PUTIH || role() == WUCHANG_HITAM;
	}

	@Override
	protected Component getTypeName() {
		if (role() == HAKIM) {
			return Component.translatable("kultivator.dimensi.raja." + Mth.clamp(variant(), 1, 10));
		}
		return Component.translatable("entity.kultivator.arwah." + roleId(role()));
	}

	@Override
	protected boolean wanders() {
		return role() == HANTU_LAPAR || wuchang() || (role() == ARWAH && !hasFlag(FLAG_QUEST));
	}

	@Override
	protected boolean hostileToPlayers() {
		return role() == HANTU_LAPAR;
	}

	@Override
	protected boolean huntsPlayer(Player player) {
		// a True Immortal's aura: the hungry dead keep their distance (they still fight back when struck)
		return !(player instanceof ServerPlayer serverPlayer) || !Cultivators.get(serverPlayer).realm().atLeast(Realm.TRUE_IMMORTAL);
	}

	@Override
	protected boolean bowsToImmortals() {
		return role() == HAKIM || role() == KEPALA_LEMBU || role() == MUKA_KUDA || wuchang();
	}

	@Override
	public @Nullable String chatterKey() {
		return switch (role()) {
			case MENG_PO -> "meng_po";
			case HAKIM -> "hakim";
			case RAJA_YAMA -> "yama";
			case KEPALA_LEMBU, MUKA_KUDA -> "penjaga_alam";
			case ARWAH -> hasFlag(FLAG_FOLLOW) ? null : "arwah";
			case WUCHANG_PUTIH -> "wuchang_putih";
			case WUCHANG_HITAM -> "wuchang_hitam";
			default -> null;
		};
	}

	@Override
	protected boolean vulnerableTo(DamageSource source) {
		return role() == HANTU_LAPAR || super.vulnerableTo(source);
	}

	@Override
	protected boolean onInteract(ServerPlayer player) {
		return BawahPeople.interact(player, this);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && role() == HANTU_LAPAR && target instanceof LivingEntity living) {
			// The hungry ghost feeds on the living soul's strength (Weakness), never on its items.
			living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), this);
		}
		return hit;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level && role() == HANTU_LAPAR && source.getEntity() instanceof ServerPlayer killer) {
			BawahPeople.ghostSlain(level, killer, this);
		}
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return role() == HANTU_LAPAR ? SoundEvents.VEX_AMBIENT : null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ghostly() ? SoundEvents.VEX_HURT : SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ghostly() ? SoundEvents.VEX_DEATH : SoundEvents.PLAYER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return role() == HANTU_LAPAR ? 0.7F + this.random.nextFloat() * 0.2F : 1.0F;
	}
}
