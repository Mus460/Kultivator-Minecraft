package dev.kultivator.musuh;

import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.NoGrief;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Kultivator Liar, the rogue cultivator (DESIGN 25.4.2): a hostile {@link Monster} drawn with the disciple's player model
 * (client {@code RogueRenderer}). It is never saved (the type is {@code noSave}, and {@link TechniquePower#TAG_ROGUE} is
 * refused in {@code ALLOW_LOAD}), never despawns by itself (the module removes it), never rides, never picks anything up,
 * never opens doors and never gets a custom name, so formations never skip it: {@link #getTypeName()} carries its title.
 * It carries {@link NoGrief#TAG} so it cannot trample farmland or crush eggs. Its targets follow {@link RogueTargets}.
 * Synced: tier, realm, stage, variant, demonic sect, skin, slim, role and the channel flag; {@code home} is server-only.
 */
public class RogueCultivatorEntity extends Monster {

	private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(RogueCultivatorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_REALM = SynchedEntityData.defineId(RogueCultivatorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(RogueCultivatorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(RogueCultivatorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SECT = SynchedEntityData.defineId(RogueCultivatorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(RogueCultivatorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_SLIM = SynchedEntityData.defineId(RogueCultivatorEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_ROLE = SynchedEntityData.defineId(RogueCultivatorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_CHANNEL = SynchedEntityData.defineId(RogueCultivatorEntity.class, EntityDataSerializers.BOOLEAN);

	/** The eight demonic sects (names {@code kultivator.musuh.sect.<n>}) and their elements. */
	public static final int SECTS = 8;
	private static final Element[] SECT_ELEMENT = {Element.FIRE, Element.METAL, Element.WOOD, Element.ICE, Element.SPACE, Element.LIGHTNING,
			Element.WATER, Element.EARTH};
	/** Health multiplier by tier (index 1-4) and the per-realm growth (DESIGN 25.4.2). */
	public static final double[] TIER_HEALTH = {1.0D, 1.5D, 2.5D, 4.0D, 10.0D};
	public static final double HEALTH_PER_REALM = 0.35D;
	public static final double BASE_HEALTH = 24.0D;
	/** Name colour per tier (index 1-4). */
	public static final int[] TIER_COLOR = {0xB0B0B0, 0xC8A070, 0xD84A3A, 0xA060E0, 0xFFD27A};
	public static final double SPEED = 0.3D;

	private @Nullable BlockPos home;
	private boolean fled;
	private boolean despawned;

	public RogueCultivatorEntity(EntityType<? extends RogueCultivatorEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setCanPickUpLoot(false);
		this.getNavigation().setCanOpenDoors(false);
		this.addTag(NoGrief.TAG);
		this.addTag(TechniquePower.TAG_ROGUE);
	}

	/** Base attributes; {@link #setup} replaces health, attack and armour with the tier's numbers. */
	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 36.0D)
				.add(Attributes.ATTACK_DAMAGE, 5.0D)
				.add(Attributes.MOVEMENT_SPEED, SPEED)
				.add(Attributes.FOLLOW_RANGE, 48.0D)
				.add(Attributes.ARMOR, 2.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TIER, 1);
		builder.define(DATA_REALM, 1);
		builder.define(DATA_STAGE, 0);
		builder.define(DATA_VARIANT, RogueCultivators.VARIANT_ROGUE);
		builder.define(DATA_SECT, 0);
		builder.define(DATA_SKIN, 0);
		builder.define(DATA_SLIM, false);
		builder.define(DATA_ROLE, RogueCultivators.ROLE_TEST);
		builder.define(DATA_CHANNEL, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new RogueAi.ChannelGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true));
		this.goalSelector.addGoal(3, new RogueAi.MarchGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		// Never turns on another rogue for a stray hit.
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, RogueCultivatorEntity.class));
		this.targetSelector.addGoal(2, new RogueAi.ChooseTargetGoal(this));
	}

	// ================================================================== setup

	/**
	 * Sets the rogue's tier (1-4), realm ordinal, stage (0-3), variant, sect (0-7), role and anchor, with the stats of
	 * DESIGN 25.4.2: health {@code 24 × {1.5, 2.5, 4, 10}[tier] × (1 + 0.35R)}, melee {@code 4 + R + 1.5 × tier} (the
	 * Patriarch {@code 6 + 1.5R}), armour {@code R + 2 × tier}. Tier 4 carries {@link TechniquePower#TAG_BOSS}.
	 */
	public void setup(int tier, int realm, int stage, int variant, int sect, int role, @Nullable BlockPos home, int skin, boolean slim) {
		int t = Mth.clamp(tier, 1, 4);
		int r = Math.max(0, realm);
		this.entityData.set(DATA_TIER, t);
		this.entityData.set(DATA_REALM, r);
		this.entityData.set(DATA_STAGE, Mth.clamp(stage, 0, 3));
		this.entityData.set(DATA_VARIANT, Mth.clamp(variant, 0, 3));
		this.entityData.set(DATA_SECT, Math.floorMod(sect, SECTS));
		this.entityData.set(DATA_ROLE, Mth.clamp(role, 0, RogueCultivators.ROLE_TEST));
		this.entityData.set(DATA_SKIN, Math.floorMod(skin, 9));
		this.entityData.set(DATA_SLIM, slim);
		this.home = home;
		// Illusory phantoms of the Labirin Ilusi have half the health (DESIGN 25.3.6).
		setBase(Attributes.MAX_HEALTH, maxHealthFor(t, r) * (variant == RogueCultivators.VARIANT_GHOST ? 0.5D : 1.0D));
		setBase(Attributes.ATTACK_DAMAGE, t >= 4 ? 6.0D + 1.5D * r : 4.0D + r + 1.5D * t);
		setBase(Attributes.ARMOR, r + 2.0D * t);
		setBase(Attributes.KNOCKBACK_RESISTANCE, Math.min(0.8D, 0.2D * t));
		setBase(Attributes.MOVEMENT_SPEED, SPEED + 0.01D * t);
		this.setHealth(this.getMaxHealth());
		this.addTag(NoGrief.TAG);
		this.addTag(TechniquePower.TAG_ROGUE);
		if (t >= 4) {
			this.addTag(TechniquePower.TAG_BOSS);
		}
		this.setCanPickUpLoot(false);
		this.xpReward = 5 * t;
	}

	/** The rogue health of DESIGN 25.4.2 for {@code tier} and realm ordinal {@code realm}. */
	public static double maxHealthFor(int tier, int realm) {
		return BASE_HEALTH * TIER_HEALTH[Mth.clamp(tier, 1, 4)] * (1.0D + HEALTH_PER_REALM * Math.max(0, realm));
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	// ================================================================== synced values

	public int tier() {
		return Mth.clamp(this.entityData.get(DATA_TIER), 1, 4);
	}

	public int realm() {
		return Math.max(0, this.entityData.get(DATA_REALM));
	}

	public int stage() {
		return this.entityData.get(DATA_STAGE);
	}

	public int variant() {
		return this.entityData.get(DATA_VARIANT);
	}

	public int sect() {
		return Math.floorMod(this.entityData.get(DATA_SECT), SECTS);
	}

	public int skin() {
		return this.entityData.get(DATA_SKIN);
	}

	public boolean slim() {
		return this.entityData.get(DATA_SLIM);
	}

	public int role() {
		return this.entityData.get(DATA_ROLE);
	}

	/** True while it stands by its anchor channelling the blood-red seal (raids and village nights). */
	public boolean isChanneling() {
		return this.entityData.get(DATA_CHANNEL);
	}

	void setChanneling(boolean on) {
		if (this.entityData.get(DATA_CHANNEL) != on) {
			this.entityData.set(DATA_CHANNEL, on);
		}
	}

	/** The sect's element (also the colour of its skills). */
	public Element element() {
		return sectElement(sect());
	}

	public static Element sectElement(int sect) {
		return SECT_ELEMENT[Math.floorMod(sect, SECTS)];
	}

	/** The first sect whose element is {@code element} (0 when none). */
	public static int sectOf(Element element) {
		for (int i = 0; i < SECTS; i++) {
			if (SECT_ELEMENT[i] == element) {
				return i;
			}
		}
		return 0;
	}

	/** The server-only anchor (raid stele, village plaque, camp), or null. */
	public @Nullable BlockPos home() {
		return home;
	}

	/** Its realm, clamped to the enum. */
	public Realm realmValue() {
		return Realm.byOrdinal(Mth.clamp(realm(), 0, Realm.values().length - 1));
	}

	boolean fled() {
		return fled;
	}

	boolean despawned() {
		return despawned;
	}

	/** Leaves in a cloud-step puff; {@code flee} counts it as driven off (retreat), otherwise as despawned. */
	public void vanish(boolean flee) {
		if (this.isRemoved()) {
			return;
		}
		if (flee) {
			this.fled = true;
			dev.kultivator.voice.VoiceHooks.rogueFlee(this);
		} else {
			this.despawned = true;
		}
		if (this.level() instanceof ServerLevel level) {
			RogueSkills.vanishFx(level, this);
		}
		this.discard();
	}

	// ================================================================== name (never a custom name, DESIGN 25.1.2)

	@Override
	protected Component getTypeName() {
		return title(tier(), variant(), sect());
	}

	/** "Pengembara Liar", "Tetua Sekte Bulan Darah", "Bayangan Ilusi"... */
	public static Component title(int tier, int variant, int sect) {
		if (variant == RogueCultivators.VARIANT_GHOST) {
			return Component.translatable("kultivator.musuh.variant.ghost.name");
		}
		if (variant == RogueCultivators.VARIANT_ENERGY) {
			return Component.translatable("kultivator.musuh.variant.energy.name");
		}
		int t = Mth.clamp(tier, 1, 4);
		if (t >= 3) {
			return Component.translatable("kultivator.musuh.tier." + t + ".named", Component.translatable("kultivator.musuh.sect." + Math.floorMod(sect, SECTS)));
		}
		return Component.translatable("kultivator.musuh.tier." + t + ".name");
	}

	/** The coloured title shown on its name plate. */
	public Component plate() {
		return Component.empty().append(getTypeName()).withColor(TIER_COLOR[tier()]).withStyle(ChatFormatting.BOLD);
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		// Never: formations skip custom-named mobs (FormationTargets.neverAffected).
	}

	// ================================================================== targets

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target != null && !RogueTargets.mayHit(this, target) ? null : target);
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return RogueTargets.mayHit(this, target) && super.canAttack(target);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		return target instanceof LivingEntity living && RogueTargets.mayHit(this, living) && super.doHurtTarget(level, target);
	}

	// ================================================================== behaviour

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && this.tickCount % 20 == 0 && !this.isRemoved()) {
			RogueAi.check(this);
		}
	}

	@Override
	protected void onBelowWorld() {
		vanish(false);
	}

	// ================================================================== nothing left behind, nothing changed

	@Override
	protected boolean canRide(Entity vehicle) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
		return false;
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	protected void dropEquipment(ServerLevel level) {
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
	}

	// ================================================================== voice

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.8F + this.random.nextFloat() * 0.15F - 0.05F * tier();
	}
}
