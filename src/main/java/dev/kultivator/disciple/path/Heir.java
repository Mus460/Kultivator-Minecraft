package dev.kultivator.disciple.path;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Pewaris and the Nine Hexes (DESIGN 20.3.20). Sumber: <i>I Shall Seal the Heavens</i>, ch. 102, 1379, 1534-1556 (the
 * League of Demon Sealers passes down one Demon Sealer per generation, and each generation must create a new hex; a
 * Mountain and Sea Lord must master all Nine Hexes), <i>Renegade Immortal</i> (Wang Lin inherits Tu Si's legacy and
 * memories), <i>Martial Universe</i> (Lin Dong becomes a senior direct disciple of the Desolate Hall).
 * <p>
 * An Inti+ disciple at Nascent Soul or higher with bond 80 and the Soul Seal can be named heir in a 60 s ritual
 * (master and disciple meditating within 4 blocks, 30% of the master's max qi). The heir learns every teachable
 * technique the master knows for free, keeps at least half of the master's comprehension of its Dao, gains +0.05 XP
 * factor and twice the master-meditation share. The master marks a Generation Hex (+50%); every new realm and every 9
 * stars the heir creates a hex on one of its techniques (+25%, at most 9). With all nine it is "Penguasa Sembilan Segel"
 * and its next hit every 20 s pins monsters within 6 blocks. A new heir takes every hex from the old one.
 */
public final class Heir {

	public static final int MIN_SECT_RANK = 3;
	public static final Realm MIN_REALM = Realm.NASCENT_SOUL;
	public static final int RITUAL_TICKS = 1200;
	public static final double RITUAL_RANGE = 4.0D;
	public static final double RITUAL_QI = 0.30D;
	/** The master may still sit down during the first 5 s of the ritual. */
	private static final int RITUAL_GRACE = 100;
	public static final double COMPREHENSION_SHARE = 0.5D;
	public static final double HEIR_XP = 0.05D;
	public static final double SURPASS_XP = 0.1D;
	public static final double MASTER_SHARE = 2.0D;
	public static final double GENERATION_HEX = 1.5D;
	public static final double HEX = 1.25D;
	public static final int MAX_HEXES = 9;
	public static final int PIN_COOLDOWN = 400;
	public static final double PIN_RADIUS = 6.0D;
	public static final int PIN_TICKS = 60;
	/** Hits of a hexed technique landing this long after its cast still count as that technique (delayed effects). */
	private static final int HEX_WINDOW = 60;
	/** extra.path key: the order a running heir ritual replaced (settled when no ritual runs any more). */
	private static final String RITUAL_PREV = "ritual_prev";

	private record Ritual(DiscipleEntity entity, UUID master, DiscipleEntity.Mode previous, int[] ticks) {
	}

	private record HexCast(String id, int tick) {
	}

	private static final Map<UUID, Ritual> RITUALS = new HashMap<>();
	private static final Map<DiscipleEntity, HexCast> LAST_HEX = new WeakHashMap<>();
	/** The hexed technique being cast right now from {@link #combatCast} (one cast at a time, server thread). */
	private static @Nullable DiscipleEntity castingEntity;
	private static @Nullable String castingId;

	static void register() {
		DiscipleHooks.addXpFactor((entity, data, source) -> {
			double bonus = (isHeirTag(data) ? HEIR_XP : 0.0D) + (DisciplePaths.tag(data).getBooleanOr("surpassed", false) ? SURPASS_XP : 0.0D);
			if (bonus <= 0.0D) {
				return 1.0D;
			}
			double rank = Math.max(0.05D, DiscipleHooks.rankFactor(data));
			return (rank + bonus) / rank;
		});
		DiscipleHooks.addMasterShareFactor(data -> isHeirTag(data) ? MASTER_SHARE : 1.0D);
		DiscipleHooks.addDamageFactor(Heir::damage);
		DiscipleHooks.addCombatCast(Heir::combatCast);
	}

	private static boolean isHeirTag(DiscipleData data) {
		return DisciplePaths.tag(data).contains("heir");
	}

	static CompoundTag heirTag(DiscipleData data) {
		return DisciplePaths.tag(data).getCompoundOrEmpty("heir");
	}

	/** The heir's created hexes (technique ids). */
	public static List<String> hexes(DiscipleData data) {
		List<String> list = new ArrayList<>();
		for (Tag tag : heirTag(data).getListOrEmpty("hexes")) {
			tag.asString().ifPresent(list::add);
		}
		return list;
	}

	/** The Generation Hex's technique id ("" none). */
	public static String generationHex(DiscipleData data) {
		return heirTag(data).getStringOr("gen_hex", "");
	}

	/** Hexes waiting to be created. */
	static int pending(DiscipleData data) {
		return Math.max(0, heirTag(data).getIntOr("pending", 0));
	}

	/** The master's lineage number ("Generasi ke-N"). */
	static int generation(ServerPlayer master) {
		return DisciplePaths.playerTag(master).getIntOr("generation", 0);
	}

	/** The soul id of the master's heir ("" none). */
	static String heirSoul(ServerPlayer master) {
		return DisciplePaths.playerTag(master).getStringOr("heir", "");
	}

	// ================================================================== naming

	/** Why the disciple cannot be named heir now (a translation key), or null. */
	static @Nullable String blocker(ServerPlayer master, DiscipleEntity entity) {
		DiscipleData data = entity.data();
		String p = DisciplePaths.PREFIX;
		if (isHeirTag(data) && entity.soulId().toString().equals(heirSoul(master))) {
			return p + "why.heir_already";
		}
		if (SectRanks.rank(data) < MIN_SECT_RANK) {
			return p + "why.heir_rank";
		}
		if (!data.realm().atLeast(MIN_REALM)) {
			return p + "why.heir_realm";
		}
		if (DisciplePaths.bond(data) < Bond.HEIR_BOND) {
			return p + "why.heir_bond";
		}
		if (!SoulSeal.sealed(data)) {
			return p + "why.heir_seal";
		}
		if (data.breakingThrough() || Calamity.running(entity) || DuelRules.inDuel(entity) || RITUALS.containsKey(entity.soulId())) {
			return p + "why.busy";
		}
		return null;
	}

	/** The "Angkat Pewaris" talisman; with another heir the first press only warns ({@code confirmed} the second). */
	static void request(ServerPlayer master, DiscipleEntity entity, boolean confirmed) {
		String why = blocker(master, entity);
		Component name = DisciplePaths.name(entity);
		if (why != null) {
			Msg.chat(master, Component.translatable(why, name, Bond.HEIR_BOND).withColor(DisciplePaths.REFUSE));
			return;
		}
		String old = heirSoul(master);
		if (!old.isEmpty() && !old.equals(entity.soulId().toString()) && !confirmed) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "heir.change_warn", name).withStyle(ChatFormatting.GOLD));
			return;
		}
		double cost = Qi.max(Cultivators.get(master)) * RITUAL_QI;
		if (!Qi.trySpend(master, cost)) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "why.heir_qi", Math.round(cost)).withColor(DisciplePaths.REFUSE));
			return;
		}
		DiscipleEntity.Mode previous = entity.mode();
		if (previous != DiscipleEntity.Mode.MEDITATE) {
			entity.setMode(DiscipleEntity.Mode.MEDITATE);
		}
		// Saved with the disciple: an unload or a restart mid-ritual still gives the old order back (tick20).
		DisciplePaths.section(entity.data()).putString(RITUAL_PREV, previous.id());
		RITUALS.put(entity.soulId(), new Ritual(entity, master.getUUID(), previous, new int[]{0}));
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "heir.start", name).withColor(DisciplePaths.JADE));
	}

	static void tick(MinecraftServer server) {
		if (RITUALS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Ritual> entry : List.copyOf(RITUALS.entrySet())) {
			Ritual ritual = entry.getValue();
			DiscipleEntity entity = ritual.entity();
			ServerPlayer master = server.getPlayerList().getPlayer(ritual.master());
			int ticks = ++ritual.ticks()[0];
			boolean meditating = master != null && Cultivators.get(master).meditating();
			boolean broken = entity.isRemoved() || !entity.isAlive() || master == null || !DisciplePaths.near(master, entity, RITUAL_RANGE)
					|| ticks > RITUAL_GRACE && !meditating;
			if (broken) {
				RITUALS.remove(entry.getKey());
				restoreMode(ritual);
				if (master != null) {
					Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "heir.broken", DisciplePaths.name(entity)).withColor(DisciplePaths.REFUSE));
				}
				continue;
			}
			entity.getNavigation().stop();
			if (ticks % 20 == 0 && entity.level() instanceof ServerLevel level) {
				Fx.send(level, master.position().add(0.0D, 0.7D, 0.0D), entity.position().add(0.0D, 0.6D, 0.0D), FxType.STAR, PathFxIds.HEIR_LINK, 0,
						ticks / (float) RITUAL_TICKS);
				if (ticks % 100 == 0) {
					DisciplePaths.sound(level, entity.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 0.6F + ticks / 2000.0F);
				}
				if (!meditating) {
					Msg.actionBar(master, Component.translatable(DisciplePaths.PREFIX + "heir.sit").withColor(DisciplePaths.GOLD));
				}
			}
			if (ticks >= RITUAL_TICKS) {
				RITUALS.remove(entry.getKey());
				restoreMode(ritual);
				complete(master, entity);
			}
		}
	}

	private static void restoreMode(Ritual ritual) {
		DiscipleEntity entity = ritual.entity();
		if (!entity.isRemoved() && entity.isAlive()) {
			DisciplePaths.section(entity.data()).remove(RITUAL_PREV);
			if (ritual.previous() != DiscipleEntity.Mode.MEDITATE && entity.mode() == DiscipleEntity.Mode.MEDITATE) {
				entity.setMode(ritual.previous());
			}
		}
	}

	/** A ritual cut off by an unload or a restart: the order it replaced comes back. */
	private static void settleStaleRitual(DiscipleEntity entity) {
		CompoundTag path = DisciplePaths.tag(entity.data());
		if (!path.contains(RITUAL_PREV) || RITUALS.containsKey(entity.soulId())) {
			return;
		}
		DiscipleEntity.Mode previous = DiscipleEntity.Mode.byId(path.getStringOr(RITUAL_PREV, ""));
		DisciplePaths.section(entity.data()).remove(RITUAL_PREV);
		if (previous != null && previous != DiscipleEntity.Mode.MEDITATE && entity.mode() == DiscipleEntity.Mode.MEDITATE) {
			entity.setMode(previous);
		}
	}

	/** Ritual progress 0..1 for the screen, or -1. */
	static float ritualProgress(DiscipleEntity entity) {
		Ritual ritual = RITUALS.get(entity.soulId());
		return ritual == null ? -1.0F : ritual.ticks()[0] / (float) RITUAL_TICKS;
	}

	private static void complete(ServerPlayer master, DiscipleEntity entity) {
		String old = heirSoul(master);
		String soul = entity.soulId().toString();
		if (!old.isEmpty() && !old.equals(soul)) {
			for (DiscipleEntity other : Disciples.loaded(master.getUUID())) {
				if (other.soulId().toString().equals(old)) {
					strip(other, master);
				}
			}
		}
		CompoundTag player = DisciplePaths.playerSection(master);
		int generation = player.getIntOr("generation", 0) + 1;
		player.putString("heir", soul);
		player.putInt("generation", generation);
		Cultivators.dirty(master);
		CompoundTag heir = new CompoundTag();
		heir.putInt("gen", generation);
		heir.put("hexes", new ListTag());
		heir.putString("gen_hex", "");
		heir.putInt("pending", 0);
		DisciplePaths.section(entity.data()).put("heir", heir);
		int learned = sync(master, entity);
		entity.refreshSynced();
		DisciplePaths.syncPower(entity);
		Component name = DisciplePaths.name(entity);
		if (entity.level() instanceof ServerLevel level) {
			Fx.send(level, entity.position(), FxType.STAR, PathFxIds.HEIR_NAMED, generation, 1.0F);
			dev.kultivator.epic.EpicHooks.extra(level, dev.kultivator.epic.EpicIds.EXTRA_HEIR, master, entity, entity.position(), generation, 0);
			DisciplePaths.sound(level, entity.position(), SoundEvents.BELL_RESONATE, 1.2F, 0.8F);
			DisciplePaths.sound(level, entity.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.2F);
		}
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "heir.named", name, generation).withColor(DisciplePaths.GOLD));
		if (learned > 0) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "heir.learned", name, learned).withColor(DisciplePaths.JADE));
		}
		Advancements.grant(master, "heir_named");
	}

	/** The old heir keeps its techniques but loses every hex. */
	private static void strip(DiscipleEntity entity, @Nullable ServerPlayer master) {
		DisciplePaths.section(entity.data()).remove("heir");
		entity.refreshSynced();
		DisciplePaths.syncPower(entity);
		if (master != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "heir.stripped", DisciplePaths.name(entity)).withStyle(ChatFormatting.GRAY));
		}
	}

	/** Learns the master's teachable techniques it qualifies for (free) and keeps half the master's comprehension. */
	static int sync(ServerPlayer master, DiscipleEntity entity) {
		DiscipleData data = entity.data();
		CultivationData masterData = Cultivators.get(master);
		int learned = 0;
		// Phase 6c (DESIGN 22.13.2): every teachable spec the master knows that the heir qualifies for, free, at the
		// master's mastery level.
		for (dev.kultivator.data.TechniqueSpec spec : masterData.learnedSpecs()) {
			if (data.knows(spec.id()) || !dev.kultivator.disciple.DiscipleCultivation.specQualifies(data, spec)) {
				continue;
			}
			if (data.learn(spec.id())) {
				int level = dev.kultivator.technique.spec.run.Mastery.level(masterData, spec.id());
				if (level > 0) {
					dev.kultivator.technique.spec.run.Mastery.setDiscipleLevel(data, spec.id(), level);
				}
				learned++;
			}
		}
		Dao dao = data.dao();
		if (dao != null) {
			double floor = masterData.comprehension(dao) * COMPREHENSION_SHARE;
			if (floor > data.comprehension()) {
				data.setComprehension(floor);
			}
		}
		return learned;
	}

	// ================================================================== hexes

	/** The master marks one of his techniques the heir knows as the Generation Hex (+50%). */
	static void setGenerationHex(ServerPlayer master, DiscipleEntity entity, String id) {
		Technique technique = Technique.byId(id);
		DiscipleData data = entity.data();
		if (!isHeirTag(data) || technique == null || !data.knows(technique) || !Cultivators.get(master).knows(technique)) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "why.hex_bad", DisciplePaths.name(entity)).withColor(DisciplePaths.REFUSE));
			return;
		}
		DisciplePaths.child(DisciplePaths.section(data), "heir").putString("gen_hex", technique.id());
		hexEffect(entity, hexes(data).size());
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "heir.gen_hex", DisciplePaths.name(entity), technique.displayName())
				.withColor(DisciplePaths.GOLD));
	}

	/** The heir creates a hex on one of its techniques without one (+25%). */
	static void createHex(ServerPlayer master, DiscipleEntity entity, String id) {
		Technique technique = Technique.byId(id);
		DiscipleData data = entity.data();
		List<String> hexes = hexes(data);
		String why = null;
		if (!isHeirTag(data)) {
			why = "why.hex_not_heir";
		} else if (pending(data) <= 0) {
			why = "why.hex_none";
		} else if (technique == null || !data.knows(technique) || hexes.contains(technique.id())) {
			why = "why.hex_bad";
		} else if (hexes.size() >= MAX_HEXES) {
			why = "why.hex_max";
		}
		if (why != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + why, DisciplePaths.name(entity)).withColor(DisciplePaths.REFUSE));
			return;
		}
		CompoundTag heir = DisciplePaths.child(DisciplePaths.section(data), "heir");
		ListTag list = heir.getListOrEmpty("hexes").copy();
		list.add(StringTag.valueOf(technique.id()));
		heir.put("hexes", list);
		heir.putInt("pending", pending(data) - 1);
		int count = list.size();
		hexEffect(entity, count);
		Component name = DisciplePaths.name(entity);
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "heir.hex", name, technique.displayName(), count, MAX_HEXES)
				.withColor(DisciplePaths.GOLD));
		if (count >= MAX_HEXES) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "heir.nine", name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			Advancements.grant(master, "nine_hexes");
		}
		DisciplePaths.syncPower(entity);
	}

	private static void hexEffect(DiscipleEntity entity, int count) {
		if (entity.level() instanceof ServerLevel level) {
			Fx.send(level, entity.position(), FxType.STAR, PathFxIds.HEX, count, 1.0F);
			DisciplePaths.sound(level, entity.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.7F);
			DisciplePaths.sound(level, entity.position(), SoundEvents.BELL_BLOCK, 0.6F, 1.6F);
		}
	}

	/** A new realm reached by the heir earns a hex to create. */
	static void onNewRealm(DiscipleEntity entity) {
		addPending(entity, "heir.hex_earned_realm");
	}

	/** Every ninth star earns the heir a hex to create. */
	static void onNinthStar(DiscipleEntity entity) {
		addPending(entity, "heir.hex_earned_star");
	}

	private static void addPending(DiscipleEntity entity, String key) {
		DiscipleData data = entity.data();
		if (!isHeirTag(data) || hexes(data).size() + pending(data) >= MAX_HEXES) {
			return;
		}
		DisciplePaths.child(DisciplePaths.section(data), "heir").putInt("pending", pending(data) + 1);
		ServerPlayer master = entity.owner();
		if (master != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + key, DisciplePaths.name(entity)).withColor(DisciplePaths.GOLD));
		}
	}

	/** Tersegel: with all nine hexes the heir's next hit every 20 s pins monsters within 6 blocks. */
	static void onHit(DiscipleEntity entity, LivingEntity victim) {
		DiscipleData data = entity.data();
		if (!isHeirTag(data) || hexes(data).size() < MAX_HEXES || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		long now = DisciplePaths.now(level.getServer());
		CompoundTag cd = DisciplePaths.tag(data).getCompoundOrEmpty("cd");
		if (now < cd.getLongOr("pin", 0L)) {
			return;
		}
		ServerPlayer master = entity.owner();
		if (master == null) {
			return;
		}
		DisciplePaths.child(DisciplePaths.section(data), "cd").putLong("pin", now + PIN_COOLDOWN);
		for (LivingEntity target : DiscipleTechniques.hostilesAround(entity, master, victim.position(), PIN_RADIUS)) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PIN_TICKS, 4), entity);
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PIN_TICKS, 4), entity);
		}
		Fx.send(level, victim.position(), FxType.STAR, PathFxIds.PIN, 9, (float) PIN_RADIUS);
		DisciplePaths.sound(level, victim.position(), SoundEvents.CHAIN_PLACE, 1.0F, 0.6F);
	}

	// ================================================================== hooks

	private static double damage(DiscipleEntity entity, LivingEntity target, boolean technique) {
		if (!technique || entity.role() != DiscipleEntity.Role.DISCIPLE) {
			return 1.0D;
		}
		DiscipleData data = entity.data();
		if (!isHeirTag(data)) {
			return 1.0D;
		}
		String id = null;
		if (castingEntity == entity) {
			id = castingId;
		} else {
			HexCast last = LAST_HEX.get(entity);
			if (last != null && entity.tickCount - last.tick() <= HEX_WINDOW) {
				id = last.id();
			}
		}
		return id == null ? 1.0D : hexFactor(data, id);
	}

	/** The hex factor of a technique: x1.5 for the Generation Hex, x1.25 for a created hex (both multiply). */
	static double hexFactor(DiscipleData data, String id) {
		double factor = 1.0D;
		if (id.equals(generationHex(data))) {
			factor *= GENERATION_HEX;
		}
		if (hexes(data).contains(id)) {
			factor *= HEX;
		}
		return factor;
	}

	/** In a fight the heir reaches for its hexed arts first (the cast is known, so the hex adds to it). */
	private static boolean combatCast(DiscipleEntity entity, LivingEntity target) {
		if (entity.role() != DiscipleEntity.Role.DISCIPLE) {
			return false;
		}
		DiscipleData data = entity.data();
		if (!isHeirTag(data) || data.deviating()) {
			return false;
		}
		List<String> order = new ArrayList<>();
		String generation = generationHex(data);
		if (!generation.isEmpty()) {
			order.add(generation);
		}
		for (String id : hexes(data)) {
			if (!order.contains(id)) {
				order.add(id);
			}
		}
		for (String id : order) {
			Technique technique = Technique.byId(id);
			if (technique == null || technique == Technique.SPRING_OF_LIFE || technique == Technique.IRON_BODY || technique == Technique.GALE
					|| !data.knows(technique) || !DiscipleTechniques.ready(entity, technique)) {
				continue;
			}
			castingEntity = entity;
			castingId = id;
			boolean cast;
			try {
				// through the spec runner (DESIGN 27.6): the hex gets its TechFx, its pose and its layers on every client
				cast = DiscipleTechniques.trySpec(entity, dev.kultivator.data.TechniqueRegistry.legacy(technique), target);
			} finally {
				castingEntity = null;
				castingId = null;
			}
			if (cast) {
				LAST_HEX.put(entity, new HexCast(id, entity.tickCount));
				return true;
			}
		}
		return false;
	}

	/** Every 30 s: an outdated heir (another was named meanwhile) loses its hexes; the heir learns and keeps comprehension. */
	static void tick20(DiscipleEntity entity, @Nullable ServerPlayer master) {
		settleStaleRitual(entity);
		DiscipleData data = entity.data();
		if (!isHeirTag(data) || master == null || entity.tickCount % 600 >= 20) {
			return;
		}
		if (!entity.soulId().toString().equals(heirSoul(master))) {
			strip(entity, master);
			return;
		}
		int learned = sync(master, entity);
		if (learned > 0) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "heir.learned", DisciplePaths.name(entity), learned).withColor(DisciplePaths.JADE));
		}
	}

	static void clear() {
		RITUALS.clear();
		LAST_HEX.clear();
		castingEntity = null;
		castingId = null;
	}

	private Heir() {
	}
}
