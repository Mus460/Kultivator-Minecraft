package dev.kultivator.dunia.naga;

import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.BeastItems;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.fx.Fx;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The tamed Jiao (DESIGN 34.5.11 "Tamed"): one per player, a spirit beast of the kind {@link BeastData#JIAO} that starts
 * at tier 3. Its record lives with its owner ({@code extra.naga_jinak}, saved with the player) and is copied onto the
 * Mutiara Naga Jinak; while it is dismissed there is no entity at all. Summoned it follows its owner through the air,
 * breathes at the owner's target every 12 s (Napas Naga, only at what the owner's techniques may hit), can be ridden
 * (owner first, one extra passenger; 0.9-1.4 blocks per tick by tier) with the Napas Naga stamina (−1 % per second in
 * the air, refilled on the ground), and never dies: a lethal blow sends it back into the pearl for 3 minutes. It goes
 * back by itself when its owner leaves, dies, changes dimension or the server stops.
 * <p>
 * Spirit XP: its own kills (the victim's max health), its owner's kills within 16 blocks (half), a Spirit Beast Pill
 * (+40), Sisik Jiao (+60) or Mutiara Jiao (+300), one every 30 s. Tiers 4-6 come only from the beast tribulation.
 */
public final class TamedJiao {

	static final String KEY = "naga_jinak";
	/** After a knock-out it rests in the pearl this long (3 minutes). */
	public static final long KNOCKOUT_TICKS = 3600L;
	/** One feeding per this many ticks (30 s), like the Spirit Beast Pill. */
	public static final int FEED_COOLDOWN = 600;
	/** It goes back into its pearl beyond this distance (and comes to its owner beyond 64). */
	public static final double LEASH = 160.0D;
	/** Napas Naga: −1 % per second in the air, +5 % per second on the ground, +1.5 % per second resting. */
	public static final float DRAIN_PER_TICK = 0.01F / 20.0F;
	public static final float LANDED_REFILL_PER_TICK = 0.05F / 20.0F;
	public static final float REST_REFILL_PER_TICK = 0.015F / 20.0F;

	/** A tamed Jiao's record (immutable). */
	public record Record(NagaKind kind, int tier, double xp, float health, float stamina, long restUntil, long lukaUntil) {

		public Record {
			tier = Mth.clamp(tier, BeastData.MAX_TIER, BeastData.TOP_TIER);
			xp = Double.isFinite(xp) ? Math.max(0.0D, Math.min(1.0E9D, xp)) : 0.0D;
			health = Float.isFinite(health) ? Mth.clamp(health, 0.05F, 1.0F) : 1.0F;
			stamina = Float.isFinite(stamina) ? Mth.clamp(stamina, 0.0F, 1.0F) : 1.0F;
		}

		static Record fresh(NagaKind kind) {
			return new Record(kind, BeastData.MAX_TIER, BeastData.TIER_XP[BeastData.MAX_TIER], 0.6F, 1.0F, 0L, 0L);
		}

		Record withXp(double value) {
			return new Record(kind, tier, value, health, stamina, restUntil, lukaUntil);
		}

		Record withTier(int value) {
			return new Record(kind, value, Math.max(xp, BeastData.TIER_XP[Mth.clamp(value, 0, BeastData.TOP_TIER)]), 1.0F, 1.0F, restUntil, lukaUntil);
		}

		Record withState(float hp, float st) {
			return new Record(kind, tier, xp, hp, st, restUntil, lukaUntil);
		}

		Record withRest(long until) {
			return new Record(kind, tier, xp, health, stamina, until, lukaUntil);
		}

		Record withLuka(long until) {
			return new Record(kind, tier, xp, health, stamina, restUntil, until);
		}

		/** The beast data view of this record (title, next XP, the evolution service). */
		public BeastData data(UUID owner) {
			return new BeastData(owner, tier, xp, BeastData.JIAO);
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putInt("kind", kind.ordinal());
			tag.putInt("tier", tier);
			tag.putDouble("xp", xp);
			tag.putFloat("health", health);
			tag.putFloat("stamina", stamina);
			tag.putLong("rest", restUntil);
			tag.putLong("luka", lukaUntil);
			return tag;
		}

		static Record load(CompoundTag tag) {
			return new Record(NagaKind.byOrdinal(tag.getIntOr("kind", 0)), tag.getIntOr("tier", BeastData.MAX_TIER),
					tag.getDoubleOr("xp", BeastData.TIER_XP[BeastData.MAX_TIER]), tag.getFloatOr("health", 1.0F),
					tag.getFloatOr("stamina", 1.0F), tag.getLongOr("rest", 0L), tag.getLongOr("luka", 0L));
		}
	}

	/** Owner UUID → the summoned Jiao. */
	private static final Map<UUID, NagaLiarEntity> ACTIVE = new HashMap<>();
	/** Owner UUID → game time of the last feeding. */
	private static final Map<UUID, Long> FED = new HashMap<>();
	/** "owner:tier" already told the Jiao waits for its tribulation (once per session). */
	private static final Set<String> READY_TOLD = new HashSet<>();
	/** Owner → where they stood last tick and for how long they stood still (the Jiao rests around a still owner). */
	private static final Map<UUID, Vec3> STILL_AT = new HashMap<>();
	private static final Map<UUID, Integer> STILL_TICKS = new HashMap<>();
	/** Owner → blocks flown on the Jiao not yet written to {@link NagaFeats}. */
	private static final Map<UUID, Double> RIDDEN = new HashMap<>();
	/** The Jiao rests after its owner stood still on the ground this long (10 s). */
	public static final int REST_AFTER = 200;
	/** A Naga Sejati (tier 5+) uses its kind skill on its owner's foe at most this often (30 s). */
	public static final int SPECIAL_EVERY = 600;

	static void clear() {
		ACTIVE.clear();
		FED.clear();
		READY_TOLD.clear();
		STILL_AT.clear();
		STILL_TICKS.clear();
		RIDDEN.clear();
		NagaLines.clear();
	}

	// ================================================================== numbers

	/** Max health by tier: 240 / 360 / 520 / 720 (game design). */
	public static double maxHealth(int tier) {
		return switch (Mth.clamp(tier, BeastData.MAX_TIER, BeastData.TOP_TIER)) {
			case 4 -> 360.0D;
			case 5 -> 520.0D;
			case 6 -> 720.0D;
			default -> 240.0D;
		};
	}

	/** Riding speed in blocks per tick: 0.9 at tier 3 to 1.4 at tier 6 (DESIGN 34.5.11). */
	public static double rideSpeed(int tier) {
		return 0.9D + (Mth.clamp(tier, BeastData.MAX_TIER, BeastData.TOP_TIER) - BeastData.MAX_TIER) * (0.5D / 3.0D);
	}

	/** One pulse of its breath: 6 / 9 / 12 / 15 (five pulses a breath). */
	public static float breathPulse(int tier) {
		return 6.0F + 3.0F * (Mth.clamp(tier, BeastData.MAX_TIER, BeastData.TOP_TIER) - BeastData.MAX_TIER);
	}

	static boolean isTribulationDamage(DamageSource source) {
		return source.is(Tribulation.DAMAGE);
	}

	// ================================================================== the record

	public static @Nullable Record get(ServerPlayer player) {
		CompoundTag extra = Cultivators.get(player).extra();
		if (!extra.contains(KEY)) {
			return null;
		}
		return Record.load(extra.getCompoundOrEmpty(KEY));
	}

	static void put(ServerPlayer player, Record record) {
		CultivationData data = Cultivators.get(player);
		data.extra().put(KEY, record.save());
		Cultivators.dirty(player);
		stampPearls(player, record);
	}

	static void remove(ServerPlayer player) {
		NagaLiarEntity active = ACTIVE.remove(player.getUUID());
		if (active != null && !active.isRemoved()) {
			active.ejectPassengers();
			active.discard();
		}
		Cultivators.get(player).extra().remove(KEY);
		Cultivators.dirty(player);
	}

	/** The owner's summoned Jiao, or null. */
	public static @Nullable NagaLiarEntity active(ServerPlayer player) {
		NagaLiarEntity dragon = ACTIVE.get(player.getUUID());
		return dragon == null || dragon.isRemoved() ? null : dragon;
	}

	private static void stampPearls(ServerPlayer player, Record record) {
		var inventory = player.getInventory();
		String owner = player.getUUID().toString();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(NagaItems.MUTIARA_NAGA_JINAK) && owner.equals(MutiaraNagaItem.ownerOf(stack))) {
				MutiaraNagaItem.stamp(stack, player, record);
			}
		}
	}

	// ================================================================== taming

	/** The Pertarungan Kehendak was won: the Jiao becomes {@code player}'s (DESIGN 34.5.11). */
	static void tame(ServerPlayer player, NagaLiarEntity dragon) {
		if (!(dragon.level() instanceof ServerLevel level)) {
			return;
		}
		Record record = Record.fresh(dragon.kind());
		WildDragons.forget(dragon);
		dragon.becomeTamed(player, record.kind(), record.tier(), record.health(), record.stamina());
		ACTIVE.put(player.getUUID(), dragon);
		put(player, record);
		ItemStack pearl = new ItemStack(NagaItems.MUTIARA_NAGA_JINAK);
		MutiaraNagaItem.stamp(pearl, player, record);
		NagaItems.give(player, pearl);
		Vec3 at = dragon.position();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.NEUTRAL, 2.0F, 1.3F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.5F, 0.8F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
		Fx.burst(level, at, dragon.kind().glow, 60, 3.5F);
		NagaVoice.heaven(level, at, NagaVoice.SUBDUED, "heaven.witness", 64.0D);
		NagaPayloads.moment(level, NagaPayloads.MOMENT_SUBDUED, dragon.getId(), at, dragon.kind().ordinal(), record.tier(), 2,
				dragon.getId(), player, 128.0D);
		Msg.title(player, Component.translatable("kultivator.naga.subdued.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("kultivator.naga.subdued.subtitle", dragon.kind().displayName()).withColor(dragon.kind().glow), 10, 70, 20);
		Msg.chat(player, Component.translatable("kultivator.naga.subdued.chat", dragon.kind().displayName()).withColor(dragon.kind().glow));
		Msg.chat(player, Component.translatable("kultivator.naga.subdued.howto").withStyle(ChatFormatting.GRAY));
		Msg.chat(player, Component.translatable("kultivator.naga.liar.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		Advancements.grant(player, "dunia/dragon_subdued");
		NagaFeats.kindMet(player, dragon.kind());
		if (dragon.kind() == NagaKind.PETIR) {
			Advancements.grant(player, "dunia/jiao_storm");
		}
		for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, dragon.getBoundingBox().inflate(96.0D), p -> p != player)) {
			Msg.chat(other, Component.translatable("kultivator.naga.subdued.others", player.getDisplayName(), dragon.kind().displayName())
					.withStyle(ChatFormatting.GRAY));
		}
	}

	// ================================================================== summon and dismiss

	/** Pearl use: summons or dismisses. False (with a message) when nothing happened. */
	static boolean usePearl(ServerPlayer player, ItemStack stack) {
		String owner = MutiaraNagaItem.ownerOf(stack);
		Record record = get(player);
		if (!owner.isEmpty() && !owner.equals(player.getUUID().toString())) {
			Msg.actionBar(player, Component.translatable("kultivator.naga.pearl.not_yours").withStyle(ChatFormatting.RED));
			return false;
		}
		if (record == null) {
			Msg.actionBar(player, Component.translatable("kultivator.naga.pearl.empty").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (owner.isEmpty()) {
			MutiaraNagaItem.stamp(stack, player, record);
		}
		NagaLiarEntity active = active(player);
		if (active != null) {
			dismiss(active, "kultivator.naga.dismiss.called");
			return true;
		}
		return summon(player, false) != null;
	}

	/** Summons the player's Jiao next to them (null with a message when it cannot come). */
	static @Nullable NagaLiarEntity summon(ServerPlayer player, boolean force) {
		Record record = get(player);
		if (record == null) {
			return null;
		}
		NagaLiarEntity active = active(player);
		if (active != null) {
			return active;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!force && record.restUntil() > now && record.restUntil() - now <= KNOCKOUT_TICKS * 2) {
			Msg.actionBar(player, Component.translatable("kultivator.naga.pearl.resting", (record.restUntil() - now + 19L) / 20L)
					.withStyle(ChatFormatting.YELLOW));
			return null;
		}
		NagaLiarEntity dragon = NagaEntities.NAGA_LIAR.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (dragon == null) {
			return null;
		}
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 at = player.position().add(Mth.sin(yaw) * 3.0D, 3.5D, -Mth.cos(yaw) * 3.0D);
		dragon.snapTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
		dragon.becomeTamed(player, record.kind(), record.tier(), record.health(), record.stamina());
		if (!level.addFreshEntity(dragon)) {
			return null;
		}
		ACTIVE.put(player.getUUID(), dragon);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.2F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.NEUTRAL, 1.0F, 1.4F);
		NagaPayloads.moment(level, NagaPayloads.MOMENT_SUMMON, dragon.getId(), at, record.kind().ordinal(), record.tier(), 2, dragon.getId(),
				player, 64.0D);
		Msg.actionBar(player, Component.translatable("kultivator.naga.summoned", dragon.getDisplayName()).withColor(record.kind().glow));
		NagaLines.tamed(player, dragon, NagaLines.greetEvent(level, player), NagaLines.GREET, 1200);
		return dragon;
	}

	/** Sends the Jiao back into its pearl, keeping its health and stamina; {@code reason} is told to the owner (may be null). */
	static void dismiss(NagaLiarEntity dragon, @Nullable String reason) {
		UUID ownerId = dragon.ownerId();
		if (ownerId != null && ACTIVE.get(ownerId) == dragon) {
			ACTIVE.remove(ownerId);
		}
		ServerPlayer owner = dragon.serverOwner();
		if (owner != null) {
			Record record = get(owner);
			if (record != null) {
				put(owner, record.withState(dragon.getHealth() / dragon.getMaxHealth(), dragon.stamina()));
			}
			if (reason != null) {
				Msg.actionBar(owner, Component.translatable(reason).withStyle(ChatFormatting.GRAY));
			}
		}
		if (dragon.level() instanceof ServerLevel level) {
			NagaPayloads.moment(level, NagaPayloads.MOMENT_DISMISS, dragon.getId(), dragon.position(), dragon.kind().ordinal(), dragon.tier(), 2,
					dragon.getId(), owner, 64.0D);
			level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 1.3F);
		}
		dragon.ejectPassengers();
		dragon.discard();
	}

	/** A lethal blow: it never dies, it returns into its pearl and rests 3 minutes. */
	static void knockedOut(ServerLevel level, NagaLiarEntity dragon) {
		ServerPlayer owner = dragon.serverOwner();
		if (owner != null) {
			Record record = get(owner);
			if (record != null) {
				put(owner, record.withState(0.25F, dragon.stamina()).withRest(level.getGameTime() + KNOCKOUT_TICKS));
			}
		}
		UUID ownerId = dragon.ownerId();
		if (ownerId != null && ACTIVE.get(ownerId) == dragon) {
			ACTIVE.remove(ownerId);
		}
		level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_HURT, SoundSource.NEUTRAL, 1.5F, 1.2F);
		Fx.burst(level, dragon.position(), dragon.kind().glow, 30, 2.0F);
		if (owner != null) {
			Msg.chat(owner, Component.translatable("kultivator.naga.knocked_out", dragon.getDisplayName(), KNOCKOUT_TICKS / 1200L)
					.withStyle(ChatFormatting.YELLOW));
		}
		NagaPayloads.moment(level, NagaPayloads.MOMENT_DISMISS, dragon.getId(), dragon.position(), dragon.kind().ordinal(), dragon.tier(), 2,
				dragon.getId(), owner, 64.0D);
		dragon.ejectPassengers();
		dragon.discard();
	}

	/** The entity left the world some other way (chunk unload, a kill command): its state is kept. */
	static void onRemoved(NagaLiarEntity dragon) {
		UUID ownerId = dragon.ownerId();
		if (ownerId == null || ACTIVE.get(ownerId) != dragon) {
			return;
		}
		ACTIVE.remove(ownerId);
		ServerPlayer owner = dragon.serverOwner();
		if (owner != null) {
			Record record = get(owner);
			if (record != null) {
				put(owner, record.withState(Math.max(0.05F, dragon.getHealth() / dragon.getMaxHealth()), dragon.stamina()));
			}
		}
	}

	/** Every Jiao goes home (server stopping, the module switched off). */
	static void dismissAll(@Nullable MinecraftServer server) {
		for (NagaLiarEntity dragon : List.copyOf(ACTIVE.values())) {
			if (!dragon.isRemoved()) {
				dismiss(dragon, null);
			}
		}
		ACTIVE.clear();
	}

	static void onOwnerGone(ServerPlayer player, @Nullable String reason) {
		NagaLiarEntity dragon = active(player);
		if (dragon != null) {
			dismiss(dragon, reason);
		}
	}

	// ================================================================== behaviour (every tick, server)

	static void tick(ServerLevel level, NagaLiarEntity dragon, boolean ridden) {
		ServerPlayer owner = dragon.serverOwner();
		if (owner != null && !owner.isRemoved() && owner.level() != level && ACTIVE.get(owner.getUUID()) == dragon
				&& BodyGuards.jiaoGuards(owner.getUUID())) {
			// Penjaga Raga: its owner's soul walks Dunia Bawah, it keeps the body
			guardTick(level, dragon, owner, BodyGuards.bodyOf(owner.getUUID(), level));
			return;
		}
		if (owner == null || owner.isRemoved() || owner.level() != level || ACTIVE.get(owner.getUUID()) != dragon) {
			// an orphan (owner offline, another copy summoned): it goes back
			if (owner != null && ACTIVE.get(owner.getUUID()) == null && owner.level() == level) {
				ACTIVE.put(owner.getUUID(), dragon);
			} else {
				dismiss(dragon, null);
				return;
			}
		}
		stamina(dragon, owner, ridden);
		if (dragon.tickCount % 20 == 0 && !dragon.hasFlag(NagaLiarEntity.FLAG_TRIBULATION)) {
			if (dragon.getHealth() < dragon.getMaxHealth() && dragon.tickCount - dragon.getLastHurtByMobTimestamp() > 100) {
				dragon.heal(1.0F + 0.5F * (dragon.tier() - BeastData.MAX_TIER));
			}
			checkReady(owner);
		}
		if (dragon.hasFlag(NagaLiarEntity.FLAG_TRIBULATION)) {
			return;
		}
		if (ridden) {
			ride(level, dragon, owner);
		}
		Vec3 body = ridden ? null : BodyGuards.bodyOf(owner.getUUID(), level);
		if (body != null) {
			// the test guard (uji naga jaga): it keeps the spot instead of following
			guardTick(level, dragon, owner, body);
			return;
		}
		dragon.setFlag(NagaLiarEntity.FLAG_GUARD, false);
		boolean resting = !ridden && restCheck(dragon, owner);
		if (dragon.tickCount % 20 == 3) {
			NagaLines.realmCheck(level, owner, dragon);
		}
		double distance = Math.sqrt(dragon.distanceToSqr(owner));
		if (!ridden && distance > LEASH) {
			dismiss(dragon, "kultivator.naga.dismiss.far");
			return;
		}
		if (!ridden && distance > 64.0D) {
			float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
			Vec3 to = owner.position().add(Mth.sin(yaw) * 4.0D, 4.0D, -Mth.cos(yaw) * 4.0D);
			dragon.snapTo(to.x, to.y, to.z, owner.getYRot(), 0.0F);
			dragon.setDeltaMovement(Vec3.ZERO);
		}
		int skill = dragon.serverSkill();
		if (skill == NagaLiarEntity.SKILL_BREATH) {
			Entity aimEntity = level.getEntity(dragon.aimId());
			LivingEntity aim = aimEntity instanceof LivingEntity living && living.isAlive() ? living : null;
			if (!ridden) {
				follow(dragon, owner, 0.6D);
			}
			boolean done = NagaSkills.breath(level, dragon, aim, dragon.serverSkillTicks(),
					entity -> entity != owner && entity != dragon && (entity == aim || Targeting.isHostileTo(entity, owner, false)),
					breathPulse(dragon.tier()));
			if (done || aim == null) {
				dragon.finishSkill();
			}
			return;
		}
		if (skill == NagaLiarEntity.SKILL_SPECIAL) {
			Entity aimEntity = level.getEntity(dragon.aimId());
			LivingEntity aim = aimEntity instanceof LivingEntity living && living.isAlive() ? living : null;
			if (!ridden) {
				follow(dragon, owner, 0.6D);
			}
			if (aim == null || NagaSkills.special(level, dragon, aim, dragon.serverSkillTicks(),
					entity -> entity != owner && entity != dragon && (entity == aim || Targeting.isHostileTo(entity, owner, false)),
					dragon.power(), owner)) {
				dragon.finishSkill();
			}
			return;
		}
		if (!ridden) {
			if (resting) {
				rest(dragon, owner);
			} else {
				follow(dragon, owner, 1.0D);
			}
		}
		if (owner.getHealth() < owner.getMaxHealth() * 0.3F && owner.getLastHurtByMob() != null
				&& owner.tickCount - owner.getLastHurtByMobTimestamp() < 40) {
			// its owner bleeds: it answers at once
			NagaLines.tamed(owner, dragon, "protect", NagaLines.HURT, 2400);
			dragon.breathTimer = Math.min(dragon.breathTimer, 20);
		}
		if (skill == NagaLiarEntity.SKILL_NONE && dragon.tier() >= 5 && Naga.config().specials && --dragon.specialTimer <= 0) {
			LivingEntity target = ownerTarget(owner, dragon);
			if (target != null) {
				dragon.specialTimer = SPECIAL_EVERY;
				dragon.beginSpecial(target);
				return;
			}
			dragon.specialTimer = 40;
		}
		// ridden by an owner who fights, it breathes twice as often
		int every = ridden && ownerTarget(owner, dragon) != null ? NagaLiarEntity.TAMED_BREATH_EVERY / 2 : NagaLiarEntity.TAMED_BREATH_EVERY;
		if (skill == NagaLiarEntity.SKILL_NONE && --dragon.breathTimer <= 0) {
			dragon.breathTimer = every;
			LivingEntity target = ownerTarget(owner, dragon);
			if (target != null) {
				dragon.beginSkill(NagaLiarEntity.SKILL_BREATH, target);
			}
		}
	}

	/** True when the owner stood still on the ground long enough and nothing is fought: the Jiao rests (FLAG_RESTING). */
	private static boolean restCheck(NagaLiarEntity dragon, ServerPlayer owner) {
		UUID id = owner.getUUID();
		Vec3 last = STILL_AT.put(id, owner.position());
		boolean still = last != null && last.distanceToSqr(owner.position()) < 0.0025D && owner.onGround();
		int ticks = still ? STILL_TICKS.merge(id, 1, Integer::sum) : 0;
		if (!still) {
			STILL_TICKS.put(id, 0);
		}
		boolean resting = ticks >= REST_AFTER && dragon.serverSkill() == NagaLiarEntity.SKILL_NONE && ownerTarget(owner, dragon) == null;
		if (resting != dragon.hasFlag(NagaLiarEntity.FLAG_RESTING)) {
			dragon.setFlag(NagaLiarEntity.FLAG_RESTING, resting);
			if (resting) {
				NagaLines.tamed(owner, dragon, "rest", NagaLines.REST, 6000);
			}
		}
		return resting;
	}

	/** Resting: low and slow, a lazy coil around its owner at shoulder height. */
	private static void rest(NagaLiarEntity dragon, ServerPlayer owner) {
		double t = dragon.tickCount * 0.008D + dragon.getId() * 0.7D;
		Vec3 centre = owner.position().add(0.0D, 1.1D + Math.sin(dragon.tickCount * 0.03D) * 0.25D, 0.0D);
		Vec3 point = centre.add(Math.cos(t) * 3.4D, 0.0D, Math.sin(t) * 3.4D);
		double distance = Math.sqrt(dragon.distanceToSqr(point));
		dragon.steer(point, Math.min(0.35D, 0.05D + distance * 0.05D), 0.05D);
	}

	/** Ridden: the flight is counted for Penunggang Awan; it remarks on storms and the sea of clouds. */
	private static void ride(ServerLevel level, NagaLiarEntity dragon, ServerPlayer owner) {
		if (dragon.getControllingPassenger() != owner) {
			return;
		}
		double dx = dragon.getX() - dragon.xo;
		double dz = dragon.getZ() - dragon.zo;
		double moved = Math.sqrt(dx * dx + dz * dz);
		if (moved > 0.0D && moved < 4.0D) {
			RIDDEN.merge(owner.getUUID(), moved, Double::sum);
		}
		if (dragon.tickCount % 100 == 0) {
			Double flown = RIDDEN.remove(owner.getUUID());
			if (flown != null) {
				NagaFeats.rode(owner, flown);
			}
			if (level.isThundering()) {
				NagaLines.tamed(owner, dragon, "ride_storm", NagaLines.STORM, 12000);
			} else if (dragon.getY() > 200.0D) {
				NagaLines.tamed(owner, dragon, "ride_high", NagaLines.HIGH, 12000);
			}
		}
	}

	/**
	 * Penjaga Raga: it circles over {@code body} (or holds still until the body is known), breathes at the monsters near
	 * it every 6 s and rests its Napas Naga.
	 */
	private static void guardTick(ServerLevel level, NagaLiarEntity dragon, ServerPlayer owner, @Nullable Vec3 body) {
		dragon.setFlag(NagaLiarEntity.FLAG_GUARD, true);
		dragon.setFlag(NagaLiarEntity.FLAG_RESTING, false);
		stamina(dragon, owner, false);
		Vec3 centre = body != null ? body : dragon.position();
		int skill = dragon.serverSkill();
		if (skill == NagaLiarEntity.SKILL_BREATH) {
			Entity aimEntity = level.getEntity(dragon.aimId());
			LivingEntity aim = aimEntity instanceof LivingEntity living && living.isAlive() ? living : null;
			boolean done = NagaSkills.breath(level, dragon, aim, dragon.serverSkillTicks(),
					entity -> entity != dragon && entity instanceof Enemy, breathPulse(dragon.tier()));
			if (done || aim == null) {
				dragon.finishSkill();
			}
		} else if (skill != NagaLiarEntity.SKILL_NONE) {
			dragon.finishSkill();
		}
		double t = dragon.tickCount * 0.025D + dragon.getId() * 0.7D;
		Vec3 point = centre.add(Math.cos(t) * 6.0D, 4.5D + Math.sin(dragon.tickCount * 0.05D) * 0.6D, Math.sin(t) * 6.0D);
		double distance = Math.sqrt(dragon.distanceToSqr(point));
		dragon.steer(point, Math.min(1.0D, 0.15D + distance * 0.06D) * (skill == NagaLiarEntity.SKILL_BREATH ? 0.5D : 1.0D), 0.08D);
		if (body == null || skill != NagaLiarEntity.SKILL_NONE || --dragon.breathTimer > 0) {
			return;
		}
		dragon.breathTimer = NagaLiarEntity.TAMED_BREATH_EVERY / 2;
		List<Mob> foes = BodyGuards.foesNear(level, body);
		if (!foes.isEmpty() && foes.get(0).distanceToSqr(dragon) <= NagaLiarEntity.TAMED_BREATH_RANGE * NagaLiarEntity.TAMED_BREATH_RANGE) {
			dragon.beginSkill(NagaLiarEntity.SKILL_BREATH, foes.get(0));
		}
	}

	/** Circles its owner a few blocks above, a little behind. */
	private static void follow(NagaLiarEntity dragon, ServerPlayer owner, double pace) {
		double t = dragon.tickCount * 0.02D + dragon.getId() * 0.7D;
		Vec3 centre = owner.position().add(0.0D, 3.5D + Math.sin(dragon.tickCount * 0.05D) * 0.6D, 0.0D);
		Vec3 point = centre.add(Math.cos(t) * 5.5D, 0.0D, Math.sin(t) * 5.5D);
		double distance = Math.sqrt(dragon.distanceToSqr(point));
		dragon.steer(point, Math.min(1.3D, 0.18D + distance * 0.07D) * pace, 0.08D);
	}

	/** The thing its owner fights: the owner's last target or attacker of the last 5 s, if the owner may hurt it. */
	static @Nullable LivingEntity ownerTarget(ServerPlayer owner, NagaLiarEntity dragon) {
		LivingEntity hit = owner.getLastHurtMob();
		if (valid(hit, owner, dragon) && owner.tickCount - owner.getLastHurtMobTimestamp() <= 100) {
			return hit;
		}
		LivingEntity attacker = owner.getLastHurtByMob();
		if (valid(attacker, owner, dragon) && owner.tickCount - owner.getLastHurtByMobTimestamp() <= 100) {
			return attacker;
		}
		return null;
	}

	private static boolean valid(@Nullable LivingEntity target, ServerPlayer owner, NagaLiarEntity dragon) {
		return target != null && target.isAlive() && target != dragon && target.level() == dragon.level()
				&& target.distanceToSqr(dragon) <= NagaLiarEntity.TAMED_BREATH_RANGE * NagaLiarEntity.TAMED_BREATH_RANGE
				&& Targeting.isHostileTo(target, owner);
	}

	/** Napas Naga: drains in the air while ridden, refills on the ground and when resting. */
	private static void stamina(NagaLiarEntity dragon, ServerPlayer owner, boolean ridden) {
		float stamina = dragon.stamina();
		if (ridden && !dragon.onGround()) {
			float drain = DRAIN_PER_TICK;
			if (dragon.tier() >= BeastData.TOP_TIER && Naga.config().arts) {
				// Yinglong: +50 % flight stamina
				drain /= 1.5F;
			}
			stamina -= drain;
		} else {
			stamina += ridden ? LANDED_REFILL_PER_TICK : REST_REFILL_PER_TICK;
			if (dragon.onGround()) {
				stamina += LANDED_REFILL_PER_TICK;
			}
		}
		stamina = Mth.clamp(stamina, 0.0F, 1.0F);
		dragon.setStamina(stamina);
		if (stamina <= 0.0F && !dragon.hasFlag(NagaLiarEntity.FLAG_TIRED)) {
			dragon.setFlag(NagaLiarEntity.FLAG_TIRED, true);
			Msg.actionBar(owner, Component.translatable("kultivator.naga.tired").withStyle(ChatFormatting.YELLOW));
		} else if (stamina >= 0.25F && dragon.hasFlag(NagaLiarEntity.FLAG_TIRED)) {
			dragon.setFlag(NagaLiarEntity.FLAG_TIRED, false);
		}
	}

	// ================================================================== spirit XP and feeding

	/** Adds spirit XP to the owner's Jiao (summoned or in the pearl). */
	static void addXp(ServerPlayer owner, double amount) {
		Record record = get(owner);
		if (record == null || !(amount > 0.0D) || !Double.isFinite(amount)) {
			return;
		}
		put(owner, record.withXp(record.xp() + amount));
		checkReady(owner);
	}

	/** Pill, Sisik or Mutiara Jiao given to one's own summoned Jiao. True when it ate. */
	static boolean feed(ServerPlayer player, NagaLiarEntity dragon, ItemStack stack) {
		double xp;
		if (stack.is(BeastItems.SPIRIT_BEAST_PILL)) {
			xp = SpiritBeasts.PILL_XP;
		} else if (stack.getItem() instanceof NagaItems.FeedItem food) {
			xp = food.xp();
		} else {
			return false;
		}
		long now = player.level().getGameTime();
		Long last = FED.get(player.getUUID());
		if (last != null && now - last < FEED_COOLDOWN && now >= last) {
			Msg.actionBar(player, Component.translatable("kultivator.beasts.pill.digesting", dragon.getDisplayName(),
					(FEED_COOLDOWN - (now - last) + 19L) / 20L).withStyle(ChatFormatting.YELLOW));
			return true;
		}
		FED.put(player.getUUID(), now);
		stack.consume(1, player);
		ServerLevel level = player.level();
		level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0F, 0.7F);
		level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.8F, 1.2F);
		Fx.burst(level, dragon.mouth(), dragon.kind().glow, 16, 1.2F);
		addXp(player, xp);
		Record record = get(player);
		if (record != null) {
			BeastData data = record.data(player.getUUID());
			double next = data.nextTierXp();
			Msg.chat(player, next < 0.0D
					? Component.translatable("kultivator.beasts.pill.xp_max", dragon.getDisplayName(), Msg.number(xp)).withStyle(ChatFormatting.AQUA)
					: Component.translatable("kultivator.beasts.pill.xp", dragon.getDisplayName(), Msg.number(xp), Msg.number(record.xp()),
							Msg.number(next)).withStyle(ChatFormatting.AQUA));
		}
		return true;
	}

	/** Tells the owner once when the Jiao has the XP and realm for its next tribulation. */
	private static void checkReady(ServerPlayer owner) {
		Record record = get(owner);
		if (record == null || record.tier() >= BeastData.TOP_TIER || !Naga.config().evolution) {
			return;
		}
		int next = record.tier() + 1;
		if (record.xp() < BeastData.TIER_XP[next] || !Cultivators.get(owner).realm().atLeast(BeastData.TIER_REALM[next])) {
			return;
		}
		if (READY_TOLD.add(owner.getUUID() + ":" + next)) {
			Component name = Component.translatable(BeastData.titleKey(BeastData.JIAO, record.tier()));
			BeastEvolution.tellReady(owner, name, BeastData.JIAO, next);
		}
	}

	// ================================================================== evolution (the tribulation passed)

	/** Raises the owner's Jiao to {@code tier} (summoned or not). */
	static void evolve(ServerPlayer owner, @Nullable NagaLiarEntity dragon, int tier, boolean celebrate) {
		Record record = get(owner);
		if (record == null) {
			return;
		}
		int before = record.tier();
		Record after = record.withTier(tier);
		put(owner, after);
		READY_TOLD.remove(owner.getUUID() + ":" + tier);
		if (dragon != null && !dragon.isRemoved()) {
			dragon.setTier(after.tier());
			dragon.setHealthBase(maxHealth(after.tier()));
			dragon.setHealth(dragon.getMaxHealth());
			dragon.damageFactor = 1.0D + 0.5D * (after.tier() - BeastData.MAX_TIER);
			dragon.nameplate(owner, after.tier());
		}
		if (after.tier() >= 5) {
			Advancements.grant(owner, "dunia/true_dragon");
		}
		if (after.tier() >= BeastData.TOP_TIER) {
			Advancements.grant(owner, "dunia/yinglong");
		}
		if (!celebrate || after.tier() <= before) {
			return;
		}
		Component title = Component.translatable(BeastData.titleKey(BeastData.JIAO, after.tier()));
		Msg.title(owner, Component.translatable("kultivator.beasts.evolve.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("kultivator.beasts.evolve.subtitle", title, after.tier()).withColor(SpiritBeasts.tierColor(BeastData.JIAO,
						after.tier())), 10, 60, 20);
		Msg.chat(owner, Component.translatable("kultivator.naga.evolve.jiao." + after.tier()).withColor(SpiritBeasts.tierColor(BeastData.JIAO,
				after.tier())));
		if (dragon != null) {
			DuniaEvents.fireBeastEvolved(owner, dragon, after.tier(), "jiao");
		}
		BeastEvolution.grantTierAdvancements(owner, after.tier());
	}

	private TamedJiao() {
	}
}
