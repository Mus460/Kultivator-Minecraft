package dev.kultivator.purba;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.abadi.Abadi;
import dev.kultivator.ascension.Stars;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.event.SectEventItems;
import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.technique.source.LearnRules;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.DamageHooks;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gejolak Purba (DESIGN 26.4.3-26.4.6): the natural appearance (every 6,000 ticks per player in Alam Abadi, staggered;
 * chance {@code 0.08 × primordialChanceScale}; only over ≥ 70% solid ground; world cap 1; 2 in-game days per player), the
 * Gulungan Gunung dan Laut, the 20 s omen, the 48-block soft arena, the health formula, the per-second intake clamp and the
 * phase lines, and the rewards (first kill / repeat / unique, Kelelahan Purba: at most 3 repeat rewards per player per day).
 * The phase scripts live in {@link PrimordialKinds}. Everything is entities, effects and items: no block is touched.
 */
final class PrimordialEvents {

	public static final double ARENA = 48.0D;
	public static final int OMEN_TICKS = 400;
	public static final int AWAY_TICKS = 600;
	public static final int NATURAL_PERIOD = 6000;
	public static final double NATURAL_CHANCE = 0.08D;
	public static final double RING_RADIUS = 24.0D;
	public static final double RING_SOLID = 0.70D;
	public static final long COOLDOWN_TICKS = 2L * 24000L;
	public static final int TRANSITION_TICKS = 60;
	public static final double LINE_1 = 2.0D / 3.0D;
	public static final double LINE_2 = 1.0D / 3.0D;
	public static final double INTAKE = 0.015D;
	public static final double INTAKE_CELAH = 0.06D;
	public static final double HEALTH_BASE = 24.0D * 40.0D;
	public static final double HEALTH_PER_REALM = 0.35D;
	public static final double HEALTH_PER_PLAYER = 0.5D;
	public static final double HEALTH_PER_DISCIPLE = 0.25D;
	public static final int MAX_DISCIPLES = 3;
	public static final double REWARD_SHARE = 0.03D;
	public static final double FIRST_SHARE = 0.40D;
	public static final double REPEAT_SHARE = 0.15D;
	public static final int FIRST_STONES = 30;
	public static final int REPEAT_STONES = 15;
	public static final int DAILY_REPEATS = 3;
	public static final double TIRED_MATERIAL = 0.5D;
	public static final double DISCIPLE_SHARE = 0.10D;
	public static final int DISCIPLE_PK = 30;
	public static final int LUKA_TICKS = 5 * 60 * 20;

	/** A player's part in a fight. */
	static final class Participant {
		final UUID id;
		double damage;
		long awaySince = -1L;
		boolean counting = true;
		boolean out;

		Participant(UUID id) {
			this.id = id;
		}
	}

	/** The one live Gejolak Purba (world cap 1). */
	static @Nullable Fight current;

	// ================================================================== the fight

	static final class Fight {
		final ServerLevel level;
		final Vec3 center;
		final int kind;
		final UUID owner;
		final boolean scroll;
		/** A scroll given back when the omen is cancelled. */
		@Nullable ItemStack refund;
		boolean active;
		boolean ended;
		long omenEnd;
		long noneSince;
		@Nullable PrimordialBeastEntity beast;
		final Map<UUID, Participant> parts = new LinkedHashMap<>();
		final Map<UUID, DiscipleEntity> disciples = new HashMap<>();
		double total;
		/** R_eff of the strongest participant at the arrival (Pelahap health). */
		int realm = 8;
		final ServerBossEvent bar;
		long phaseStart;
		long transitionUntil;
		long celahUntil;
		long windowStart;
		float windowTaken;
		final PrimordialKinds.State script = new PrimordialKinds.State();
		final List<PrimordialRelicEntity> relics = new ArrayList<>();
		final List<Mob> pelahap = new ArrayList<>();

		Fight(ServerLevel level, Vec3 center, int kind, UUID owner, boolean scroll) {
			this.level = level;
			this.center = center;
			this.kind = Mth.clamp(kind, 0, 2);
			this.owner = owner;
			this.scroll = scroll;
			this.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("kultivator.purba.bar.omen"), BossEvent.BossBarColor.PURPLE,
					BossEvent.BossBarOverlay.NOTCHED_10);
			this.noneSince = level.getGameTime();
		}

		long now() {
			return level.getGameTime();
		}

		int phase() {
			return beast == null ? 0 : beast.phase();
		}

		boolean celah() {
			return now() < celahUntil;
		}

		boolean transitioning() {
			return now() < transitionUntil;
		}

		/** Opens a Celah for {@code ticks} (the longer one wins). */
		void openCelah(int ticks) {
			long until = now() + ticks;
			if (until > celahUntil) {
				celahUntil = until;
				if (beast != null) {
					Fx.send(level, beast.position().add(0.0D, beast.getBbHeight() * 0.5D, 0.0D), FxType.PURBA, PurbaFxIds.CELAH, kind, ticks / 20.0F);
				}
			}
		}

		/** The health the current phase may not go below (2/3, 1/3, then 0). */
		float line() {
			if (beast == null) {
				return 0.0F;
			}
			float max = beast.getMaxHealth();
			return switch (phase()) {
				case 0 -> (float) (max * LINE_1);
				case 1 -> (float) (max * LINE_2);
				default -> 0.0F;
			};
		}

		/** The health the current phase may heal up to. */
		float ceiling() {
			if (beast == null) {
				return 0.0F;
			}
			float max = beast.getMaxHealth();
			return switch (phase()) {
				case 0 -> max;
				case 1 -> (float) (max * LINE_1);
				default -> (float) (max * LINE_2);
			};
		}

		/** Players still counting (online, in the level, not out). */
		List<ServerPlayer> fighters() {
			List<ServerPlayer> list = new ArrayList<>();
			MinecraftServer server = level.getServer();
			for (Participant part : parts.values()) {
				if (!part.counting || part.out) {
					continue;
				}
				ServerPlayer player = server.getPlayerList().getPlayer(part.id);
				if (player != null && player.isAlive() && player.level() == level && !player.isSpectator()) {
					list.add(player);
				}
			}
			return list;
		}

		boolean isOut(ServerPlayer player) {
			Participant part = parts.get(player.getUUID());
			return part != null && part.out;
		}

		void markOut(ServerPlayer player) {
			Participant part = parts.computeIfAbsent(player.getUUID(), Participant::new);
			part.out = true;
			bar.removePlayer(player);
		}

		// -------------------------------------------------------------- damage intake (26.2.2)

		boolean hurt(PrimordialBeastEntity mob, ServerLevel serverLevel, DamageSource source, float damage) {
			if (!active || ended || transitioning()) {
				return false;
			}
			Entity attacker = source.getEntity();
			if (attacker instanceof ServerPlayer player && isOut(player)) {
				return false;
			}
			// Rakus Tak Berujung: technique damage while the mouth is open heals Taotie by half the amount.
			if (kind == PrimordialBeastEntity.TAOTIE && mob.phase() == 2 && mob.hasFlag(PrimordialBeastEntity.FLAG_MOUTH)
					&& DamageHooks.isTechnique(source)) {
				mob.setHealth(Math.min(ceiling(), mob.getHealth() + damage * 0.5F));
				Fx.send(serverLevel, mob.position().add(0.0D, 3.0D, 0.0D), FxType.PURBA, PurbaFxIds.DEVOUR, kind, 1.0F);
				serverLevel.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_BURP, SoundSource.HOSTILE, 2.0F, 0.5F);
				return false;
			}
			long time = now();
			if (time - windowStart >= 20L) {
				windowStart = time;
				windowTaken = 0.0F;
			}
			float max = mob.getMaxHealth();
			float cap = (float) (max * (celah() ? INTAKE_CELAH : INTAKE)) - windowTaken;
			if (cap <= 0.0F) {
				return false;
			}
			float line = line();
			float room = mob.getHealth() - line;
			if (mob.phase() < 2 && room <= 0.05F) {
				requestPhase();
				return false;
			}
			float amount = Math.min(damage, cap);
			if (mob.phase() < 2) {
				amount = Math.min(amount, room);
			}
			float before = mob.getHealth();
			boolean hurt = mob.hurtRaw(serverLevel, source, amount);
			float lost = Math.max(0.0F, before - mob.getHealth());
			windowTaken += lost;
			if (lost > 0.0F) {
				credit(source, lost);
			}
			if (mob.isAlive() && mob.phase() < 2 && mob.getHealth() - line <= 0.5F) {
				requestPhase();
			}
			return hurt;
		}

		private void credit(DamageSource source, float lost) {
			Entity attacker = source.getEntity();
			UUID who = null;
			if (attacker instanceof ServerPlayer player) {
				who = player.getUUID();
			} else if (attacker instanceof DiscipleEntity disciple && disciple.ownerId() != null) {
				who = disciple.ownerId();
				disciples.put(disciple.getUUID(), disciple);
			}
			if (who == null) {
				return;
			}
			Participant part = parts.computeIfAbsent(who, Participant::new);
			part.damage += lost;
			total += lost;
		}

		boolean relicHurt(PrimordialRelicEntity relic, ServerLevel serverLevel, DamageSource source, float damage) {
			if (!active || ended) {
				return false;
			}
			if (relic.kind() == PrimordialRelicEntity.LILIN) {
				PrimordialKinds.lilinHit(this, relic);
				return false;
			}
			boolean hurt = relic.hurtRaw(serverLevel, source, damage);
			if (!relic.isAlive()) {
				PrimordialKinds.windEyeBroken(this, relic);
			}
			return hurt;
		}

		/** The beast reached a phase line: 3 s invulnerable transition into the next phase. */
		void requestPhase() {
			PrimordialBeastEntity mob = beast;
			if (mob == null || mob.phase() >= 2 || transitioning()) {
				return;
			}
			enterPhase(mob.phase() + 1);
		}

		void enterPhase(int phase) {
			PrimordialBeastEntity mob = beast;
			if (mob == null) {
				return;
			}
			int next = Mth.clamp(phase, 0, 2);
			PrimordialKinds.leavePhase(this, mob.phase());
			mob.setPhase(next);
			phaseStart = now();
			transitionUntil = now() + TRANSITION_TICKS;
			celahUntil = 0L;
			mob.setFlag(PrimordialBeastEntity.FLAG_TRANSITION, true);
			mob.setFlag(PrimordialBeastEntity.FLAG_GLOW, false);
			script.reset(now());
			Vec3 at = mob.position().add(0.0D, mob.getBbHeight() * 0.5D, 0.0D);
			Fx.send(level, at, FxType.PURBA, PurbaFxIds.PHASE, kind, 12.0F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 0.6F + 0.15F * next);
			Component name = Component.translatable("kultivator.purba.phase." + Purba.KINDS.get(kind) + "." + next);
			for (ServerPlayer player : fighters()) {
				Msg.chat(player, Component.translatable("kultivator.purba.phase_line", PurbaItems.kindName(kind), name).withStyle(ChatFormatting.LIGHT_PURPLE));
				EpicHooks.extra(level, EpicIds.EXTRA_PRIMORDIAL_PHASE, player, mob, at, kind, next);
			}
			bar.setName(Component.translatable("kultivator.purba.bar.fight", PurbaItems.kindName(kind), name));
			PrimordialKinds.enterPhase(this, next);
		}

		/** The beast leaves with no reward (nobody left, chunk unloaded, Peaceful, a test removal). */
		void depart(boolean announce) {
			if (ended) {
				return;
			}
			ended = true;
			if (!active && refund != null && !refund.isEmpty()) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
				if (player != null && player.isAlive()) {
					Purba.give(player, refund);
				} else {
					// offline or dead (a dead inventory is already emptied): the Lumbung, else an owner-locked drop at the arena
					Purba.drop(level, center, Halls.hallOf(owner) != null ? Storehouse.insert(owner, refund) : refund, owner);
				}
				refund = null;
			}
			if (announce) {
				for (ServerPlayer player : fighters()) {
					Msg.chat(player, Component.translatable("kultivator.purba.departs", PurbaItems.kindName(kind)).withStyle(ChatFormatting.GRAY));
				}
			}
			cleanup();
		}

		/** Removes the beast, its relics and Pelahap, the bar and any sky grade. */
		void cleanup() {
			ended = true;
			PrimordialKinds.leavePhase(this, phase());
			if (beast != null && !beast.isRemoved()) {
				beast.fight = null;
				if (beast.isAlive()) {
					beast.discard();
				}
			}
			for (PrimordialRelicEntity relic : relics) {
				relic.fight = null;
				if (!relic.isRemoved()) {
					relic.discard();
				}
			}
			relics.clear();
			for (Mob mob : pelahap) {
				if (!mob.isRemoved()) {
					mob.discard();
				}
			}
			pelahap.clear();
			bar.removeAllPlayers();
			if (current == this) {
				current = null;
			}
			Purba.refreshNear(level, center);
		}

		// -------------------------------------------------------------- tick

		void tick() {
			long time = now();
			if (level.getDifficulty() == Difficulty.PEACEFUL) {
				depart(true);
				return;
			}
			if (!active) {
				tickOmen(time);
				return;
			}
			PrimordialBeastEntity mob = beast;
			if (mob == null || mob.isRemoved()) {
				if (mob != null && mob.isDeadOrDying()) {
					return;
				}
				depart(true);
				return;
			}
			if (time % 20L == 0L) {
				arena(time);
				if (ended) {
					return;
				}
			}
			if (transitionUntil > 0L && time >= transitionUntil) {
				transitionUntil = 0L;
				mob.setFlag(PrimordialBeastEntity.FLAG_TRANSITION, false);
			}
			mob.setFlag(PrimordialBeastEntity.FLAG_CELAH, celah());
			if (!transitioning()) {
				PrimordialKinds.tick(this, mob, time);
			} else {
				PrimordialKinds.hover(this, mob);
			}
			if (time % 10L == 0L) {
				bar.setProgress(Mth.clamp(mob.getHealth() / Math.max(1.0F, mob.getMaxHealth()), 0.0F, 1.0F));
			}
		}

		private void tickOmen(long time) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
			if (player == null || !player.isAlive() || player.level() != level) {
				depart(false);
				return;
			}
			float progress = 1.0F - Mth.clamp((omenEnd - time) / (float) OMEN_TICKS, 0.0F, 1.0F);
			bar.setProgress(progress);
			if (time % 20L == 0L) {
				for (ServerPlayer each : level.players()) {
					if (!each.isSpectator() && each.position().distanceToSqr(center) <= ARENA * ARENA) {
						bar.addPlayer(each);
					}
				}
				if (time % 100L == 0L) {
					Fx.send(level, center.add(0.0D, 30.0D, 0.0D), FxType.PURBA, PurbaFxIds.OMEN, kind, 0.0F);
				}
			}
			if (time >= omenEnd) {
				arrive();
			}
		}

		/** The omen ends: the beast arrives over the summon point with its health formula (26.4.4). */
		void arrive() {
			PrimordialBeastEntity mob = PurbaEntities.PRIMORDIAL_BEAST.create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				depart(false);
				return;
			}
			List<ServerPlayer> near = new ArrayList<>();
			for (ServerPlayer each : level.players()) {
				if (!each.isSpectator() && each.isAlive() && each.position().distanceToSqr(center) <= ARENA * ARENA) {
					near.add(each);
					parts.computeIfAbsent(each.getUUID(), Participant::new);
				}
			}
			int realm = 8;
			for (ServerPlayer each : near) {
				realm = Math.max(realm, Purba.realmEff(each));
			}
			this.realm = realm;
			int disciples = 0;
			AABB box = new AABB(center, center).inflate(ARENA, 24.0D, ARENA);
			for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, box, d -> d.isAlive() && d.ownerId() != null
					&& parts.containsKey(d.ownerId()) && d.role() == DiscipleEntity.Role.DISCIPLE)) {
				disciples++;
			}
			disciples = Math.min(MAX_DISCIPLES, disciples);
			double health = HEALTH_BASE * (1.0D + HEALTH_PER_REALM * realm) * (1.0D + HEALTH_PER_PLAYER * Math.max(0, near.size() - 1))
					* (1.0D + HEALTH_PER_DISCIPLE * disciples);
			mob.setKind(kind);
			Vec3 spawn = PrimordialKinds.startPosition(this);
			mob.snapTo(spawn.x, spawn.y, spawn.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
			AttributeInstance max = mob.getAttribute(Attributes.MAX_HEALTH);
			if (max != null) {
				max.setBaseValue(health);
			}
			mob.setHealth(mob.getMaxHealth());
			mob.fight = this;
			if (!level.addFreshEntity(mob)) {
				depart(false);
				return;
			}
			beast = mob;
			active = true;
			refund = null;
			phaseStart = now();
			script.reset(now());
			Component name = Component.translatable("kultivator.purba.phase." + Purba.KINDS.get(kind) + ".0");
			bar.setName(Component.translatable("kultivator.purba.bar.fight", PurbaItems.kindName(kind), name));
			bar.setColor(BossEvent.BossBarColor.RED);
			bar.setProgress(1.0F);
			level.playSound(null, spawn.x, spawn.y, spawn.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0F, 0.5F);
			for (ServerPlayer each : near) {
				Msg.chat(each, Component.translatable("kultivator.purba.arrives", PurbaItems.kindName(kind), name).withStyle(ChatFormatting.GOLD));
				Msg.chat(each, Component.translatable("kultivator.purba.kind." + Purba.KINDS.get(kind) + ".source")
						.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
			PrimordialKinds.enterPhase(this, 0);
			Purba.refreshNear(level, center);
		}

		/** Joins new arrivals, drops those away for 30 s, departs when nobody counts for 30 s, updates the bar viewers. */
		private void arena(long time) {
			for (ServerPlayer each : level.players()) {
				if (each.isSpectator() || !each.isAlive()) {
					continue;
				}
				boolean inside = each.position().distanceToSqr(center) <= ARENA * ARENA;
				Participant part = parts.get(each.getUUID());
				if (inside && part == null) {
					part = new Participant(each.getUUID());
					parts.put(each.getUUID(), part);
				}
				if (part == null || part.out) {
					continue;
				}
				if (inside) {
					part.awaySince = -1L;
					part.counting = true;
					bar.addPlayer(each);
				} else {
					if (part.awaySince < 0L) {
						part.awaySince = time;
					} else if (time - part.awaySince >= AWAY_TICKS) {
						part.counting = false;
					}
					if (each.position().distanceToSqr(center) > (ARENA + 16.0D) * (ARENA + 16.0D)) {
						bar.removePlayer(each);
					}
				}
			}
			for (ServerPlayer shown : List.copyOf(bar.getPlayers())) {
				if (shown.level() != level || shown.isRemoved()) {
					bar.removePlayer(shown);
				}
			}
			boolean anyone = false;
			for (ServerPlayer each : fighters()) {
				if (each.position().distanceToSqr(center) <= ARENA * ARENA) {
					anyone = true;
					break;
				}
			}
			if (anyone) {
				noneSince = time;
			} else if (time - noneSince >= AWAY_TICKS) {
				depart(true);
			}
		}

		// -------------------------------------------------------------- death and rewards (26.4.6)

		void finish(PrimordialBeastEntity mob) {
			if (ended) {
				return;
			}
			ended = true;
			MinecraftServer server = level.getServer();
			double need = total * REWARD_SHARE;
			for (Participant part : parts.values()) {
				if (part.damage <= 0.0D || part.damage + 1.0E-6D < need) {
					continue;
				}
				ServerPlayer player = server.getPlayerList().getPlayer(part.id);
				if (player != null) {
					reward(player, mob);
				}
			}
			for (DiscipleEntity disciple : disciples.values()) {
				if (disciple.isRemoved() || !disciple.isAlive()) {
					continue;
				}
				DiscipleCultivation.addXp(disciple, DisciplePaths.barSize(disciple.data()) * DISCIPLE_SHARE, XpSource.TRIAL);
				SectRanks.addPk(disciple.data(), DISCIPLE_PK, "kultivator.purba.pk_reason");
			}
			cleanup();
		}

		private void reward(ServerPlayer player, PrimordialBeastEntity mob) {
			CultivationData data = Cultivators.get(player);
			CompoundTag purba = Purba.section(data);
			String id = Purba.KINDS.get(kind);
			CompoundTag kills = purba.getCompoundOrEmpty("kills");
			int before = kills.getIntOr(id, 0);
			boolean first = before <= 0;
			long day = Purba.today(level.getServer());
			if (purba.getLongOr("rep_day", -1L) != day) {
				purba.putLong("rep_day", day);
				purba.putInt("rep_n", 0);
			}
			int repeats = purba.getIntOr("rep_n", 0);
			boolean tired = !first && repeats >= DAILY_REPEATS;
			RandomSource random = level.getRandom();
			// Kunpeng: six distinct pieces held as it falls teach kunpeng_art (checked before the new fragment is given).
			if (kind == PrimordialBeastEntity.KUNPENG) {
				wholeKunpeng(player, data);
			}
			if (first) {
				if (kind == PrimordialBeastEntity.KUNPENG) {
					Purba.give(player, new ItemStack(PurbaItems.KUN_SCALE, 2));
					Purba.give(player, new ItemStack(PurbaItems.PENG_FEATHER, 2));
				} else {
					Purba.give(player, new ItemStack(PurbaItems.material(kind, false), 2));
				}
				Purba.give(player, Purba.stones(FIRST_STONES));
				Stars.grantShare(player, FIRST_SHARE);
			} else if (!tired) {
				Purba.give(player, new ItemStack(PurbaItems.material(kind, random.nextBoolean())));
				Purba.give(player, Purba.stones(REPEAT_STONES));
				Stars.grantShare(player, REPEAT_SHARE);
				purba.putInt("rep_n", repeats + 1);
			} else {
				if (random.nextDouble() < TIRED_MATERIAL) {
					Purba.give(player, new ItemStack(PurbaItems.material(kind, random.nextBoolean())));
				}
				Msg.chat(player, Component.translatable("kultivator.purba.tired", DAILY_REPEATS).withStyle(ChatFormatting.GRAY));
			}
			if (!tired) {
				if (kind == PrimordialBeastEntity.KUNPENG) {
					Purba.give(player, SectEventItems.fragment(missingPiece(player, random)));
				} else if (kind == PrimordialBeastEntity.ZHULONG && !hasUnique(purba, "candle_eye")) {
					addUnique(purba, "candle_eye");
					Purba.give(player, new ItemStack(PurbaItems.ZHULONG_CANDLE_EYE));
				}
			}
			kills.putInt(id, before + 1);
			purba.put("kills", kills);
			Cultivators.dirty(player);
			Msg.chat(player, Component.translatable(first ? "kultivator.purba.slain.first" : "kultivator.purba.slain.repeat", PurbaItems.kindName(kind),
					before + 1).withStyle(ChatFormatting.GOLD));
			Advancements.grant(player, "primordial_slain");
			Advancements.grant(player, id + "_slain");
			if (Purba.distinctKinds(player) >= 3) {
				Advancements.grant(player, "three_primordials");
			}
			Purba.sync(player);
		}

		/** Holding six distinct Kunpeng pieces: learn {@code kunpeng_art} and consume them; nothing is used when it is known. */
		private void wholeKunpeng(ServerPlayer player, CultivationData data) {
			Inventory inventory = player.getInventory();
			int[] slot = new int[7];
			java.util.Arrays.fill(slot, -1);
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				int piece = SectEventItems.pieceOf(inventory.getItem(i));
				if (piece >= 1 && piece <= 6 && slot[piece] < 0) {
					slot[piece] = i;
				}
			}
			for (int piece = 1; piece <= 6; piece++) {
				if (slot[piece] < 0) {
					return;
				}
			}
			if (data.knows("kunpeng_art")) {
				Msg.chat(player, Component.translatable("kultivator.purba.kunpeng.known").withStyle(ChatFormatting.GRAY));
				return;
			}
			TechniqueSpec spec = TechniqueRegistry.byId("kunpeng_art");
			if (spec == null) {
				return;
			}
			for (int piece = 1; piece <= 6; piece++) {
				inventory.getItem(slot[piece]).shrink(1);
			}
			inventory.setChanged();
			LearnRules.learn(player, spec, "inheritance");
			Msg.chat(player, Component.translatable("kultivator.purba.kunpeng.whole").withStyle(ChatFormatting.LIGHT_PURPLE));
			Advancements.grant(player, "kunpeng_whole");
		}

		private int missingPiece(ServerPlayer player, RandomSource random) {
			boolean[] held = new boolean[7];
			Inventory inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				int piece = SectEventItems.pieceOf(inventory.getItem(i));
				if (piece >= 1 && piece <= 6) {
					held[piece] = true;
				}
			}
			List<Integer> missing = new ArrayList<>();
			for (int piece = 1; piece <= 6; piece++) {
				if (!held[piece]) {
					missing.add(piece);
				}
			}
			return missing.isEmpty() ? 1 + random.nextInt(6) : missing.get(random.nextInt(missing.size()));
		}
	}

	// ================================================================== unique list

	static boolean hasUnique(CompoundTag purba, String id) {
		for (Tag tag : purba.getListOrEmpty("unique")) {
			if (tag instanceof StringTag string && id.equals(string.value())) {
				return true;
			}
		}
		return false;
	}

	static void addUnique(CompoundTag purba, String id) {
		ListTag list = purba.getListOrEmpty("unique").copy();
		list.add(StringTag.valueOf(id));
		purba.put("unique", list);
	}

	// ================================================================== tick, appearance and starts

	static void tick(MinecraftServer server, long tick) {
		Fight fight = current;
		if (fight != null) {
			if (fight.ended) {
				current = null;
			} else {
				fight.tick();
			}
		}
		if (tick % 20L == 0L) {
			natural(server);
		}
	}

	/** Every 6,000 ticks per player in Alam Abadi, staggered by UUID: the chance roll of a natural Gejolak Purba. */
	private static void natural(MinecraftServer server) {
		KultivatorConfig config = KultivatorConfig.get();
		if (!config.primordialBeasts || current != null || config.primordialChanceScale <= 0.0D) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (!Abadi.isImmortalLevel(level) || level.getDifficulty() == Difficulty.PEACEFUL) {
				continue;
			}
			long time = level.getGameTime();
			for (ServerPlayer player : level.players()) {
				long phase = Math.floorMod(time + (player.getUUID().hashCode() & 0x7FFFFFFF), (long) NATURAL_PERIOD);
				if (phase >= 20L || player.isSpectator() || player.isCreative()) {
					continue;
				}
				if (cooldownLeft(player) > 0L || Purba.busy(player) || !solidRing(level, player.position())) {
					continue;
				}
				if (level.getRandom().nextDouble() >= NATURAL_CHANCE * config.primordialChanceScale) {
					continue;
				}
				if (start(player, leastKilled(player), false, null) == null) {
					continue;
				}
				return;
			}
		}
	}

	/** At least 70% of 16 samples on a 24-block ring around {@code pos} stand over ground (heightmap only; no chunk loads). */
	static boolean solidRing(ServerLevel level, Vec3 pos) {
		int solid = 0;
		int samples = 16;
		for (int i = 0; i < samples; i++) {
			double angle = Math.PI * 2.0D * i / samples;
			int x = Mth.floor(pos.x + Math.cos(angle) * RING_RADIUS);
			int z = Mth.floor(pos.z + Math.sin(angle) * RING_RADIUS);
			if (!level.isLoaded(new BlockPos(x, Mth.floor(pos.y), z))) {
				continue;
			}
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) > level.getMinY() + 1) {
				solid++;
			}
		}
		return solid >= Math.ceil(samples * RING_SOLID);
	}

	/** The kind this player has killed least (random tie-break). */
	static int leastKilled(ServerPlayer player) {
		int best = Integer.MAX_VALUE;
		List<Integer> ties = new ArrayList<>();
		for (int kind = 0; kind < Purba.KINDS.size(); kind++) {
			int kills = Purba.kills(player, Purba.KINDS.get(kind));
			if (kills < best) {
				best = kills;
				ties.clear();
			}
			if (kills == best) {
				ties.add(kind);
			}
		}
		return ties.get(player.getRandom().nextInt(ties.size()));
	}

	/** Ticks left of the player's natural cooldown. */
	static long cooldownLeft(ServerPlayer player) {
		long until = Purba.section(Cultivators.get(player)).getLongOr("cd", 0L);
		return Math.max(0L, until - Purba.clock(player.level().getServer()));
	}

	/**
	 * Starts the 20 s omen of {@code kind} at the player (natural or scroll; {@code refund} is the scroll given back when
	 * the omen is cancelled). Null when refused (another primordial, Peaceful, switched off, not in Alam Abadi).
	 */
	static @Nullable Fight start(ServerPlayer player, int kind, boolean scroll, @Nullable ItemStack refund) {
		ServerLevel level = player.level();
		if (current != null || level.getDifficulty() == Difficulty.PEACEFUL || !KultivatorConfig.get().primordialBeasts) {
			return null;
		}
		Fight fight = new Fight(level, player.position(), kind, player.getUUID(), scroll);
		fight.refund = refund;
		fight.omenEnd = level.getGameTime() + OMEN_TICKS;
		fight.parts.put(player.getUUID(), new Participant(player.getUUID()));
		current = fight;
		if (!scroll) {
			Purba.section(Cultivators.get(player)).putLong("cd", Purba.clock(level.getServer()) + COOLDOWN_TICKS);
			Cultivators.dirty(player);
		}
		Vec3 sky = fight.center.add(0.0D, 30.0D, 0.0D);
		Fx.send(level, sky, FxType.PURBA, PurbaFxIds.OMEN, kind, 0.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.5F, 0.5F);
		for (ServerPlayer each : level.players()) {
			if (!each.isSpectator() && each.position().distanceToSqr(fight.center) <= ARENA * ARENA) {
				fight.bar.addPlayer(each);
				Msg.chat(each, Component.translatable("kultivator.purba.omen", PurbaItems.kindName(kind)).withStyle(ChatFormatting.DARK_PURPLE));
				EpicHooks.extra(level, EpicIds.EXTRA_PRIMORDIAL_OMEN, each, each, sky, kind, 0);
			}
		}
		return fight;
	}

	/** Spawns {@code kind} at once (test: no omen), optionally starting at {@code phase}. */
	static @Nullable Fight startNow(ServerPlayer player, int kind, int phase) {
		Fight fight = start(player, kind, true, null);
		if (fight == null) {
			return null;
		}
		fight.arrive();
		if (fight.beast != null && phase > 0) {
			PrimordialBeastEntity mob = fight.beast;
			float max = mob.getMaxHealth();
			mob.setHealth((float) (max * (phase >= 2 ? LINE_2 : LINE_1)) - 0.01F);
			fight.enterPhase(phase);
		}
		return fight;
	}

	/** The scroll was used: same refusals as the ascension (26.3.2); consumed now, given back if the omen is cancelled. */
	static boolean useScroll(ServerPlayer player, ItemStack stack) {
		int kind = PurbaItems.kindOf(stack);
		Component why = scrollRefusal(player);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
			return false;
		}
		ItemStack refund = stack.copyWithCount(1);
		if (start(player, kind, true, refund) == null) {
			Msg.chat(player, Component.translatable("kultivator.purba.scroll.refused").withStyle(ChatFormatting.RED));
			return false;
		}
		if (!player.isCreative()) {
			stack.shrink(1);
		}
		return true;
	}

	static @Nullable Component scrollRefusal(ServerPlayer player) {
		if (!KultivatorConfig.get().primordialBeasts) {
			return Component.translatable("kultivator.purba.disabled");
		}
		if (!Abadi.isImmortalLevel(player.level())) {
			return Component.translatable("kultivator.purba.scroll.only_realm");
		}
		if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
			return Component.translatable("kultivator.purba.peaceful");
		}
		if (current != null) {
			return Component.translatable("kultivator.purba.scroll.busy_world");
		}
		if (Purba.busy(player)) {
			return Component.translatable("kultivator.spine9.refuse.busy");
		}
		return null;
	}

	/** A beast died: its fight pays the rewards. */
	static void onBeastDeath(PrimordialBeastEntity mob) {
		Fight fight = mob.fight;
		if (fight != null) {
			try {
				fight.finish(mob);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Primordial rewards failed", exception);
				fight.cleanup();
			}
		}
	}

	/** LEAVE (that owner) or SERVER_STOPPING (null): an omen still running gives the scroll back while its owner is still here. */
	static void abandonOmen(@Nullable UUID owner) {
		Fight fight = current;
		if (fight != null && !fight.active && (owner == null || fight.owner.equals(owner))) {
			fight.depart(false);
		}
	}

	static void clear() {
		Fight fight = current;
		if (fight != null) {
			fight.bar.removeAllPlayers();
		}
		current = null;
	}

	private PrimordialEvents() {
	}
}
