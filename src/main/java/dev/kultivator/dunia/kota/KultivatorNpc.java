package dev.kultivator.dunia.kota;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Rank;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.duel.ai.DuelistProfile;
import dev.kultivator.duel.api.Duelist;
import dev.kultivator.dunia.tempat.hidup.GolPenduduk;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.RupaKunci;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.sosok.SosokGen;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.NoGrief;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * {@code kultivator:kultivator_npc} (DESIGN 34.5.1-34.5.4): the human cultivators of the towns, the wanderers, the rival
 * groups and the immortals. A {@link PathfinderMob}, never an {@code Enemy}: area techniques never hit a peaceful NPC
 * ({@code Targeting.isHostileTo} only accepts a mob targeting the caster). It is never saved (the type is {@code noSave}
 * and the {@link #TAG} is refused on load), never picks anything up, never opens doors, never tramples farmland
 * ({@link NoGrief#TAG}) and never gets a custom name. The spawner (anchors, wanderers, rivals) re-makes it.
 * <p>
 * Synced: role, look seed (skin and slim model), realm, stage, Dao (-1 none), voice (0-5: m1-m3, f1-f3), robe colour (0
 * = the role's), flags ({@link #FLAG_SITTING}, {@link #FLAG_CHANNEL}, {@link #FLAG_HOSTILE}, {@link #FLAG_AURA} and the
 * body-language flags {@link #FLAG_TALK}, {@link #FLAG_BOW}, {@link #FLAG_GAZE}, {@link #FLAG_WORK} the client poses),
 * the name plate and the spoken line shown as a speech bubble ({@link #say}). Server only: home, town, group,
 * temperament, the players it is hostile to, the lonely-despawn rule and a companion owner.
 * <p>
 * Sosok (DESIGN 40.2, module 40I): the shared look and animation data ({@link SosokApi.Pemegang}): identity
 * ({@code DATA_SOSOK}: sex, visible age, build, look seed), style ({@code DATA_GAYA}), the server clip and its start
 * ({@code DATA_GERAK}, {@code DATA_GERAK_T0}), props ({@code DATA_PROP}), the entity it looks at ({@code DATA_LOOK_AT})
 * and the look key ({@code DATA_RUPA}, empty = its role). {@link #setup} derives the Sosok from role and look seed
 * ({@link SosokGen#untukNpc}) so every spawner is right unedited, makes the voice match the sex and sets
 * {@code Attributes.SCALE}; a look key set later ({@code SosokApi.setRupa}) re-derives it unless a Sosok was given
 * explicitly ({@code SosokApi.setSosok}).
 * <p>
 * Wanderers, rivals and guards fight sword duels (DESIGN 35.7.6) through the optional {@link Duelist} interface; the
 * duel never depends on this class ({@link KotaDuel} answers for it).
 * <p>
 * DESIGN 38 residents (38.N.1, module 38NL): a body may stand for one resident of a place ({@link #setPenduduk}; site
 * id and resident index, server only, never saved: the body is re-made from the deterministic data). Such a body gets
 * its day from the place's schedule (delegated through {@code NpcAi.check} and {@code NpcLife.tick} to
 * {@code TubuhPenduduk}), walks with {@link GolPenduduk}, is not pushed while it sleeps or sits
 * ({@link #diamDiTitik}) and never vanishes in a puff (the caller releases it unseen).
 */
public class KultivatorNpc extends PathfinderMob implements Duelist, SosokApi.Pemegang {

	public static final String TAG = "kultivator.kota_npc";

	public static final int FLAG_SITTING = 1;
	public static final int FLAG_CHANNEL = 2;
	public static final int FLAG_HOSTILE = 4;
	/** Shows the cultivation aura (wanderers, rivals, immortals; the client reads it). */
	public static final int FLAG_AURA = 8;
	/** Translucent (a ghost or an illusion; reserved for the Dunia Bawah's use of K's NPCs on Pulau Pasar it stays 0). */
	public static final int FLAG_GHOST = 16;
	/** Talking: the hands move with the words, the head nods (a speech bubble or a screen is open). */
	public static final int FLAG_TALK = 32;
	/** The cupped-fist salute and a small bow (to a senior, a guest, a lecture's end). */
	public static final int FLAG_BOW = 64;
	/** Looking up at the sky (a sky event, a treasure light, the noon bell). */
	public static final int FLAG_GAZE = 128;
	/** The role's work: counting on the abacus, hammering, pouring tea, the morning exercise. */
	public static final int FLAG_WORK = 256;
	/** A rival group's leader (the client dresses it in the gold guan and the cloak). */
	public static final int FLAG_LEADER = 512;

	public static final String[] VOICES = {"m1", "m2", "m3", "f1", "f2", "f3"};

	private static final EntityDataAccessor<Integer> DATA_ROLE = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_REALM = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_DAO = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_VOICE = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ROBE = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Component> DATA_PLATE = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.COMPONENT);
	/** The line it is saying (a speech bubble above the plate; empty = none). */
	private static final EntityDataAccessor<Component> DATA_LINE = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.COMPONENT);
	// Sosok (DESIGN 40.2.4, 40.7.4): the names 38.N.1 planned as DATA_ANIM/DATA_ANIM_T0 are DATA_GERAK/DATA_GERAK_T0 here.
	private static final EntityDataAccessor<Integer> DATA_SOSOK = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GAYA = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GERAK = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GERAK_T0 = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PROP = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_LOOK_AT = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.INT);
	/** The look key ({@link RupaKunci} text form, at most 96 chars; empty = the role's own row). */
	private static final EntityDataAccessor<String> DATA_RUPA = SynchedEntityData.defineId(KultivatorNpc.class, EntityDataSerializers.STRING);

	public static final double BASE_HEALTH = 24.0D;
	public static final double[] TIER_HEALTH = {1.0D, 1.5D, 2.5D, 4.0D, 10.0D};
	public static final double HEALTH_PER_REALM = 0.35D;
	public static final double SPEED = 0.3D;

	// ------------------------------------------------------------------ server state (never saved)
	private @Nullable BlockPos home;
	private long town;
	private long anchorSite;
	private String group = "";
	private int temperament;
	private final Set<UUID> hostileTo = new HashSet<>();
	private boolean fightsMonsters;
	private @Nullable UUID companionOf;
	private long companionUntil;
	private double lonelyRange = 64.0D;
	private int lonelyTicks = 600;
	private long lonelySince;
	private long lastHurtLine;
	/** Free-form state of the spawner (wanderer meeting, rival race, lecture...). */
	private final net.minecraft.nbt.CompoundTag memory = new net.minecraft.nbt.CompoundTag();
	private boolean vanished;
	/** Game times the body-language flags and the speech line end at (0 = not set). */
	private long lineUntil;
	private long bowUntil;
	private long gazeUntil;
	private long workUntil;
	/** The last game time it fought (the blade goes back into its sheath three seconds later). */
	private long lastFight;
	/** Timers of the timed Sosok clips and moods (DESIGN 40, session state). */
	private final SosokApi.Waktu sosokWaktu = new SosokApi.Waktu();
	/** True once a caller gave an explicit Sosok ({@code SosokApi.setSosok}): a later look key no longer re-derives it. */
	private boolean sosokTetap;
	/** DESIGN 38 resident (38.N.1 {@code TubuhPenduduk}): the place it lives in and its index there (-1 = not a resident). */
	private long situsPenduduk;
	private int pendudukId = -1;
	/** Asleep, seated or kneeling at its titik: not pushed aside, the vanilla head turns stop (38.N.5.1). */
	private boolean diamDiTitik;

	public KultivatorNpc(EntityType<? extends KultivatorNpc> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setCanPickUpLoot(false);
		this.getNavigation().setCanOpenDoors(false);
		this.addTag(NoGrief.TAG);
		this.addTag(TAG);
		// realm-scaled Dunia foe: the trial-mob rule of 6c (audit M4 T5)
		TechniquePower.markRealmFoe(this);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 36.0D)
				.add(Attributes.ATTACK_DAMAGE, 5.0D)
				.add(Attributes.MOVEMENT_SPEED, SPEED)
				.add(Attributes.FOLLOW_RANGE, 40.0D)
				.add(Attributes.ARMOR, 2.0D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ROLE, NpcRole.WARGA.ordinal());
		builder.define(DATA_LOOK, 0);
		builder.define(DATA_REALM, 1);
		builder.define(DATA_STAGE, 0);
		builder.define(DATA_DAO, -1);
		builder.define(DATA_VOICE, 0);
		builder.define(DATA_ROBE, 0);
		builder.define(DATA_FLAGS, 0);
		builder.define(DATA_PLATE, Component.empty());
		builder.define(DATA_LINE, Component.empty());
		builder.define(DATA_SOSOK, Sosok.KOSONG);
		builder.define(DATA_GAYA, 0);
		builder.define(DATA_GERAK, 0);
		builder.define(DATA_GERAK_T0, 0);
		builder.define(DATA_PROP, 0);
		builder.define(DATA_LOOK_AT, SosokApi.TATAP_TIDAK_ADA);
		builder.define(DATA_RUPA, "");
	}

	/**
	 * Goals of 34 plus the three of a DESIGN 38 resident ({@link GolPenduduk}, idle for every other NPC): holding still
	 * at a bed, chair or kneeling spot (priority 1, before a hold or a walk), walking to the schedule's titik (4: L0
	 * vanilla path near a player, L1 straight steps along the {@code jalan} nodes) and keeping the eyes on the work at
	 * hand (5: before the vanilla glances at players).
	 */
	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new GolPenduduk.Diam(this));
		this.goalSelector.addGoal(1, new NpcAi.HoldGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true));
		this.goalSelector.addGoal(3, new NpcAi.FollowGoal(this));
		this.goalSelector.addGoal(4, new GolPenduduk.Jalan(this));
		this.goalSelector.addGoal(4, new NpcAi.HomeGoal(this));
		this.goalSelector.addGoal(5, new GolPenduduk.Fokus(this));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new NpcAi.ChooseTargetGoal(this));
	}

	// ================================================================== setup

	/**
	 * Sets role, look seed, realm ordinal, stage (0-3), Dao (null: none), voice (0-5), robe colour (0: the role's) and
	 * the combat numbers of DESIGN 25.4.2 for the role's tier: health {@code 24 × tier × (1 + 0.35 R)}, melee
	 * {@code 4 + R + 1.5 × tier}, armour {@code R + 2 × tier}. The Sosok follows from role and look (DESIGN 40.2.2): sex,
	 * age, build and seed, the voice forced to the sex ({@code f1-f3} = 3 + voice % 3 for a woman, {@code m1-m3} =
	 * voice % 3 for a man) and {@code Attributes.SCALE}.
	 */
	public void setup(NpcRole role, int look, int realm, int stage, @Nullable Dao dao, int voice, int robe, Component plate) {
		setup(role, role.tier, look, realm, stage, dao, voice, robe, plate);
	}

	public void setup(NpcRole role, int tier, int look, int realm, int stage, @Nullable Dao dao, int voice, int robe, Component plate) {
		int r = Mth.clamp(realm, 0, Realm.values().length - 1);
		int t = Mth.clamp(tier, 1, 4);
		this.entityData.set(DATA_ROLE, role.ordinal());
		this.entityData.set(DATA_LOOK, look);
		this.entityData.set(DATA_REALM, r);
		this.entityData.set(DATA_STAGE, Mth.clamp(stage, 0, 3));
		this.entityData.set(DATA_DAO, dao == null ? -1 : dao.ordinal());
		this.entityData.set(DATA_VOICE, Math.floorMod(voice, VOICES.length));
		this.entityData.set(DATA_ROBE, robe);
		this.entityData.set(DATA_PLATE, plate == null ? Component.empty() : plate);
		RupaKunci rupa = RupaKunci.parse(this.entityData.get(DATA_RUPA));
		if (sosokTetap) {
			cocokkanSuara();
		} else {
			terapkanSosok(rupa.kosong() ? SosokGen.untukNpc(role, look) : SosokGen.untukRupa(rupa.kunci(), role, look));
		}
		setBase(Attributes.MAX_HEALTH, BASE_HEALTH * TIER_HEALTH[t] * (1.0D + HEALTH_PER_REALM * r));
		setBase(Attributes.ATTACK_DAMAGE, 4.0D + r + 1.5D * t);
		setBase(Attributes.ARMOR, r + 2.0D * t);
		setBase(Attributes.KNOCKBACK_RESISTANCE, Math.min(0.8D, 0.2D * t));
		setBase(Attributes.MOVEMENT_SPEED, SPEED + 0.01D * t);
		this.memory.putInt("tier", t);
		this.setHealth(this.getMaxHealth());
		if (t >= 4) {
			this.addTag(TechniquePower.TAG_BOSS);
		}
		boolean aura = role == NpcRole.PENGEMBARA || role == NpcRole.SAINGAN || role.immortal() || role == NpcRole.PENJAGA;
		setFlag(FLAG_AURA, aura);
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	// ================================================================== synced values

	public NpcRole role() {
		return NpcRole.byOrdinal(this.entityData.get(DATA_ROLE));
	}

	public int look() {
		return this.entityData.get(DATA_LOOK);
	}

	public int realm() {
		return Math.max(0, this.entityData.get(DATA_REALM));
	}

	public Realm realmValue() {
		return Realm.byOrdinal(Mth.clamp(realm(), 0, Realm.values().length - 1));
	}

	public int stage() {
		return this.entityData.get(DATA_STAGE);
	}

	public @Nullable Dao dao() {
		int index = this.entityData.get(DATA_DAO);
		Dao[] all = Dao.values();
		return index >= 0 && index < all.length ? all[index] : null;
	}

	public int voice() {
		return Math.floorMod(this.entityData.get(DATA_VOICE), VOICES.length);
	}

	/** True for a woman: the explicit Sosok sex (DESIGN 40.0 rule 10), the voice only before {@link #setup} ran. */
	public boolean female() {
		Sosok sosok = Sosok.unpack(this.entityData.get(DATA_SOSOK));
		return sosok != null ? sosok.perempuan() : voice() >= 3;
	}

	// ================================================================== Sosok (DESIGN 40.2.4, contract C40.I1)

	/** The identity: sex, visible age, build, look seed (the entity-seed stand-in before {@link #setup}). */
	public Sosok syncedSosok() {
		return Sosok.dari(this.entityData.get(DATA_SOSOK), this.getUUID());
	}

	/** The changeable style (traits, path, wound, special outfit, mood). */
	public Gaya syncedGaya() {
		return Gaya.unpack(this.entityData.get(DATA_GAYA));
	}

	/** The server clip ({@code GerakId.pack}; 0 = none). */
	public int syncedGerak() {
		return this.entityData.get(DATA_GERAK);
	}

	/** Game time {@code & 0x7FFFFFFF} when the server clip started. */
	public int syncedGerakT0() {
		return this.entityData.get(DATA_GERAK_T0);
	}

	/** The props of the server clip ({@code PropId.pack}; 0 = the clip's own). */
	public int syncedProp() {
		return this.entityData.get(DATA_PROP);
	}

	/** The id of the entity it looks at, or {@link SosokApi#TATAP_TIDAK_ADA}. */
	public int syncedLookAt() {
		return this.entityData.get(DATA_LOOK_AT);
	}

	/** The look key in text form (empty = the role's own row {@code peran.<role id>}). */
	public String syncedRupa() {
		return this.entityData.get(DATA_RUPA);
	}

	/**
	 * The look key the client resolves: the one set with {@code SosokApi.setRupa}, else the role's id ({@code juru_lelang},
	 * {@code menteri}; 40K falls back to {@link NpcRole#rupaKelompok()} when the recipe has no row).
	 */
	public RupaKunci rupaKunci() {
		RupaKunci rupa = RupaKunci.parse(syncedRupa());
		return rupa.kosong() ? RupaKunci.dari(role().id()) : rupa;
	}

	@Override
	public int sosokData() {
		return this.entityData.get(DATA_SOSOK);
	}

	/** An explicit Sosok ({@code SosokApi.setSosok}): kept even when a look key is set later; the voice follows the sex. */
	@Override
	public void setSosokData(int packed) {
		this.sosokTetap = true;
		this.entityData.set(DATA_SOSOK, packed);
		cocokkanSuara();
	}

	@Override
	public int gayaData() {
		return this.entityData.get(DATA_GAYA);
	}

	@Override
	public void setGayaData(int packed) {
		this.entityData.set(DATA_GAYA, packed);
	}

	@Override
	public int gerakData() {
		return this.entityData.get(DATA_GERAK);
	}

	@Override
	public int gerakT0() {
		return this.entityData.get(DATA_GERAK_T0);
	}

	@Override
	public void setGerakData(int gerak, int t0) {
		this.entityData.set(DATA_GERAK, gerak);
		this.entityData.set(DATA_GERAK_T0, t0);
	}

	@Override
	public int propData() {
		return this.entityData.get(DATA_PROP);
	}

	@Override
	public void setPropData(int packed) {
		this.entityData.set(DATA_PROP, packed);
	}

	@Override
	public int tatapData() {
		return this.entityData.get(DATA_LOOK_AT);
	}

	@Override
	public void setTatapData(int entityId) {
		this.entityData.set(DATA_LOOK_AT, entityId);
	}

	@Override
	public String rupaData() {
		return this.entityData.get(DATA_RUPA);
	}

	/**
	 * A new look key ({@code SosokApi.setRupa}): an NPC sect member {@code murid.2|sekte=npc:s3}, a sect master
	 * {@code ketua_sekte|sekte=...}. Unless a Sosok was given explicitly, sex and age are re-derived from the key's policy
	 * (a {@code murid.*} row is mostly young) with the same look seed, and the voice follows.
	 */
	@Override
	public void setRupaData(String rupa) {
		String text = rupa == null ? "" : rupa;
		this.entityData.set(DATA_RUPA, text);
		if (!sosokTetap && !this.level().isClientSide()) {
			RupaKunci key = RupaKunci.parse(text);
			terapkanSosok(key.kosong() ? SosokGen.untukNpc(role(), look()) : SosokGen.untukRupa(key.kunci(), role(), look()));
		}
	}

	@Override
	public SosokApi.Waktu waktuSosok() {
		return sosokWaktu;
	}

	/** Mouth movement without a line: the old talking flag for {@code tick} ticks (no bubble). */
	@Override
	public boolean bicaraSendiri(int tick) {
		if (this.level().isClientSide()) {
			return true;
		}
		long until = this.level().getGameTime() + Math.max(10, tick);
		if (until > this.lineUntil) {
			this.lineUntil = until;
		}
		setFlag(FLAG_TALK, true);
		return true;
	}

	/** The derived Sosok (role, look key): synced, voice forced to the sex, {@code Attributes.SCALE}. Server side. */
	private void terapkanSosok(Sosok sosok) {
		if (sosok == null || this.level().isClientSide()) {
			return;
		}
		this.entityData.set(DATA_SOSOK, sosok.pack());
		cocokkanSuara();
		SosokApi.terapkanSkala(this, sosok);
	}

	/** Keeps the voice inside the sex's three (m1-m3 men, f1-f3 women). */
	private void cocokkanSuara() {
		Sosok sosok = Sosok.unpack(this.entityData.get(DATA_SOSOK));
		if (sosok == null) {
			return;
		}
		int voice = Math.floorMod(this.entityData.get(DATA_VOICE), VOICES.length);
		int matched = sosok.perempuan() ? 3 + voice % 3 : voice % 3;
		if (matched != voice) {
			this.entityData.set(DATA_VOICE, matched);
		}
	}

	/** The robe colour (the role's when 0). */
	public int robe() {
		int robe = this.entityData.get(DATA_ROBE);
		return robe != 0 ? robe : role().robe;
	}

	public int flags() {
		return this.entityData.get(DATA_FLAGS);
	}

	public boolean hasFlag(int flag) {
		return (flags() & flag) != 0;
	}

	public void setFlag(int flag, boolean on) {
		int flags = flags();
		int next = on ? flags | flag : flags & ~flag;
		if (next != flags) {
			this.entityData.set(DATA_FLAGS, next);
		}
	}

	public Component plate() {
		return this.entityData.get(DATA_PLATE);
	}

	public void setPlate(Component plate) {
		this.entityData.set(DATA_PLATE, plate == null ? Component.empty() : plate);
	}

	/** The line it is saying now (empty when silent). */
	public Component line() {
		return this.entityData.get(DATA_LINE);
	}

	/**
	 * Says {@code text} for {@code ticks}: a speech bubble above the name plate for players within 12 blocks, and the
	 * talking gesture. Text only (the voice is the caller's, through {@code Voice}). Server side.
	 */
	public void say(Component text, int ticks) {
		if (this.level().isClientSide() || text == null) {
			return;
		}
		this.entityData.set(DATA_LINE, text);
		this.lineUntil = this.level().getGameTime() + Math.max(20, ticks);
		setFlag(FLAG_TALK, true);
	}

	/** The cupped-fist salute for {@code ticks}. */
	public void bow(int ticks) {
		if (!this.level().isClientSide()) {
			this.bowUntil = this.level().getGameTime() + Math.max(10, ticks);
			setFlag(FLAG_BOW, true);
		}
	}

	/** Looks up at the sky for {@code ticks}. */
	public void gaze(int ticks) {
		if (!this.level().isClientSide()) {
			this.gazeUntil = this.level().getGameTime() + Math.max(10, ticks);
			setFlag(FLAG_GAZE, true);
		}
	}

	/** Works (the role's gesture) for {@code ticks}; 0 stops. */
	public void work(int ticks) {
		if (!this.level().isClientSide()) {
			this.workUntil = ticks <= 0 ? 0L : this.level().getGameTime() + ticks;
			setFlag(FLAG_WORK, ticks > 0);
		}
	}

	/** True while the speech line or a bow is still running (chatter waits for it). */
	public boolean busyTalking() {
		return hasFlag(FLAG_TALK) || hasFlag(FLAG_BOW);
	}

	/** Ends the body language whose time is up (every tick, a few comparisons). */
	private void tickBodyLanguage(long now) {
		if (lineUntil != 0L && now >= lineUntil) {
			lineUntil = 0L;
			this.entityData.set(DATA_LINE, Component.empty());
			setFlag(FLAG_TALK, false);
		}
		if (bowUntil != 0L && now >= bowUntil) {
			bowUntil = 0L;
			setFlag(FLAG_BOW, false);
		}
		if (gazeUntil != 0L && now >= gazeUntil) {
			gazeUntil = 0L;
			setFlag(FLAG_GAZE, false);
		}
		if (workUntil != 0L && now >= workUntil) {
			workUntil = 0L;
			setFlag(FLAG_WORK, false);
		}
	}

	/** The realm (and Dao) line under the plate: "Inti Emas, Akhir · Dao Pedang" (client renderer, within 10 blocks). */
	@Override
	public @Nullable Component belowNameDisplay() {
		Component scoreboard = super.belowNameDisplay();
		if (scoreboard != null || plate().getString().isEmpty()) {
			return scoreboard;
		}
		Realm realm = realmValue();
		net.minecraft.network.chat.MutableComponent text = new Rank(realm, 0, Mth.clamp(stage(), 0, Math.max(0, realm.stages() - 1)))
				.displayName().withColor(realm.color());
		Dao dao = dao();
		if (dao != null && role() != NpcRole.WARGA) {
			text.append(Component.literal(" \u00b7 ").withColor(0x9A9A9A)).append(dao.displayName().copy().withColor(dao.color()));
		}
		return text;
	}

	// ================================================================== weapons (DESIGN 34.5.2-34.5.3, 35.7.6)

	/** Remembers a fight now (the blade stays drawn while it fights and three seconds after). */
	public void fought() {
		this.lastFight = this.level().getGameTime();
	}

	public long lastFight() {
		return lastFight;
	}

	/**
	 * Draws ({@code true}) or sheathes its weapon (the main hand; the sheath and hilt are drawn by the client when the
	 * hand is empty). A copy without attribute modifiers: the role's combat numbers stay those of DESIGN 25.4.2.
	 */
	public void arm(boolean drawn) {
		ItemStack held = this.getMainHandItem();
		if (drawn == !held.isEmpty()) {
			return;
		}
		this.setItemSlot(EquipmentSlot.MAINHAND, drawn ? NpcGear.weapon(this) : ItemStack.EMPTY);
	}

	// ================================================================== sword duels (the duel module asks, KotaDuel answers)

	@Override
	public @Nullable DuelistProfile duelProfile() {
		return KotaDuel.profile(this);
	}

	@Override
	public boolean acceptsDuel(ServerPlayer challenger) {
		return KotaDuel.accepts(this, challenger);
	}

	@Override
	public void onDuelEnd(boolean won) {
		KotaDuel.ended(this, won);
	}

	/** {@code disciple.<voice>.<line>} (DESIGN 34.8: reused disciple voices). */
	public String voiceKey(String line) {
		return "disciple." + VOICES[voice()] + "." + line;
	}

	// ================================================================== server state

	public @Nullable BlockPos home() {
		return home;
	}

	public void setHome(@Nullable BlockPos home) {
		this.home = home == null ? null : home.immutable();
	}

	/** The town site id (0 outside a town). */
	public long town() {
		return town;
	}

	public void setTown(long town) {
		this.town = town;
	}

	/** The anchor site that spawned it (0 for wanderers and rivals). */
	public long anchorSite() {
		return anchorSite;
	}

	public void setAnchorSite(long site) {
		this.anchorSite = site;
	}

	/** The rival group id ("" for none). */
	public String group() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group == null ? "" : group;
	}

	/** Wanderer temperament: 0 Ramah, 1 Angkuh, 2 Licik. */
	public int temperament() {
		return temperament;
	}

	public void setTemperament(int temperament) {
		this.temperament = Mth.clamp(temperament, 0, 2);
	}

	public net.minecraft.nbt.CompoundTag memory() {
		return memory;
	}

	// ================================================================== DESIGN 38 resident (38.N.1, module 38NL)

	/**
	 * True for the body of a DESIGN 38 resident ({@code dunia/tempat/hidup/Pemanggil}): its day comes from the place's
	 * schedule ({@code Jadwal}) instead of the old round, its walks from {@link GolPenduduk}, and it is released quietly
	 * (no puff) when players leave.
	 */
	public boolean penduduk() {
		return pendudukId >= 0;
	}

	/** The site id of the place the resident lives in (0 for every other NPC). */
	public long situsPenduduk() {
		return situsPenduduk;
	}

	/** The resident's index in its place ({@code Penduduk.id}), or -1. */
	public int pendudukId() {
		return pendudukId;
	}

	/** Binds the body to resident {@code id} of place {@code situs} ({@code id < 0} unbinds). Server side, once. */
	public void setPenduduk(long situs, int id) {
		this.situsPenduduk = id < 0 ? 0L : situs;
		this.pendudukId = Math.max(-1, id);
		if (id < 0) {
			this.diamDiTitik = false;
		}
	}

	/** True while it sleeps, sits or kneels at its titik (set by the resident's pass). */
	public boolean diamDiTitik() {
		return diamDiTitik;
	}

	public void setDiamDiTitik(boolean diam) {
		this.diamDiTitik = diam;
	}

	/** A sleeper in its bed or a man on his chair is not shoved off by whoever brushes past. */
	@Override
	public boolean isPushable() {
		return !diamDiTitik && super.isPushable();
	}

	/** Makes the NPC hostile to {@code player} only (a challenge, a raid, a town's grudge). */
	public void markHostile(ServerPlayer player) {
		hostileTo.add(player.getUUID());
		setFlag(FLAG_HOSTILE, true);
		if (getTarget() == null) {
			setTarget(player);
		}
	}

	public void forgive(UUID player) {
		hostileTo.remove(player);
		if (hostileTo.isEmpty()) {
			setFlag(FLAG_HOSTILE, false);
		}
		if (getTarget() != null && getTarget().getUUID().equals(player)) {
			setTarget(null);
		}
	}

	public boolean isHostileTo(UUID player) {
		return hostileTo.contains(player);
	}

	public Set<UUID> hostiles() {
		return hostileTo;
	}

	/** Guards and companions hunt monsters near them. */
	public void setFightsMonsters(boolean on) {
		this.fightsMonsters = on;
	}

	public boolean fightsMonsters() {
		return fightsMonsters;
	}

	public @Nullable UUID companionOf() {
		return companionOf;
	}

	public long companionUntil() {
		return companionUntil;
	}

	/** Follows {@code owner} until game time {@code until} (null ends it). */
	public void setCompanion(@Nullable UUID owner, long until) {
		this.companionOf = owner;
		this.companionUntil = until;
		this.fightsMonsters = owner != null || role() == NpcRole.PENJAGA;
	}

	/** Leaves when no survival or adventure player is within {@code range} for {@code ticks}. */
	public void setLonely(double range, int ticks) {
		this.lonelyRange = Math.max(8.0D, range);
		this.lonelyTicks = Math.max(20, ticks);
		this.lonelySince = this.level().getGameTime();
	}

	public double lonelyRange() {
		return lonelyRange;
	}

	public int lonelyTicks() {
		return lonelyTicks;
	}

	long lonelySince() {
		return lonelySince;
	}

	void resetLonely(long now) {
		this.lonelySince = now;
	}

	boolean vanished() {
		return vanished;
	}

	/** Leaves in a cloud-step puff (despawn, flee, end of a meeting). */
	public void vanish() {
		if (this.isRemoved()) {
			return;
		}
		vanished = true;
		if (this.level() instanceof ServerLevel level) {
			NpcSkills.vanishFx(level, this);
		}
		if (DuelRules.inDuel(this)) {
			DuelRules.cancel(this);
		}
		this.discard();
	}

	// ================================================================== name (never a custom name)

	@Override
	protected Component getTypeName() {
		Component plate = plate();
		return plate.getString().isEmpty() ? Component.translatable(role().titleKey()) : plate;
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		// Never: formations skip custom-named mobs, and the plate carries the name.
	}

	// ================================================================== targets

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target != null && !NpcAi.mayHit(this, target) ? null : target);
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return NpcAi.mayHit(this, target) && super.canAttack(target);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		return target instanceof LivingEntity living && NpcAi.mayHit(this, living) && super.doHurtTarget(level, target);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) {
			Kota.onNpcHurt(this, source);
			long now = level.getGameTime();
			if (now - lastHurtLine > 100L && this.isAlive() && this.random.nextInt(3) == 0) {
				lastHurtLine = now;
				dev.kultivator.voice.Voice.say(this, female() ? "grunt.female.hurt" : "grunt.male.hurt", "");
			}
		}
		return hurt;
	}

	// ================================================================== behaviour

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && !this.isRemoved()) {
			tickBodyLanguage(this.level().getGameTime());
			SosokApi.tik(this);
			if (this.tickCount % 20 == 0) {
				NpcAi.check(this);
			}
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		return Kota.interact(serverPlayer, this) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
	}

	@Override
	protected void onBelowWorld() {
		vanish();
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
		return female() ? 1.15F : 0.95F;
	}
}
