package dev.kultivator.duel.ai;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.api.Duelist;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.duel.session.DuelRecords;
import dev.kultivator.musuh.RogueCultivatorEntity;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.voice.VoiceDisciples;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who duels besides players (DESIGN 35.7.6): entities implementing {@link Duelist}, entity types registered from outside
 * ({@link #register}, for example Dunia's wandering cultivators), and the built-in rogue cultivators and disciple bodies.
 * Also keeps the stakes an NPC won ({@link #carried}): they drop when it is later beaten for real. Memory only.
 */
public final class DuelistRegistry {

	/** A rogue offers stones of this grade by tier (index 1-4) and this many. */
	private static final int[] ROGUE_STONE_GRADE = {0, 0, 0, 1, 2};
	private static final int[] ROGUE_STONE_COUNT = {0, 4, 6, 6, 8};

	private static final Map<EntityType<?>, DuelistProfile.Provider> PROVIDERS = new LinkedHashMap<>();
	/** Profiles set by commands (the Duel Dummy) or modules for one entity, by UUID. */
	private static final Map<UUID, DuelistProfile> OVERRIDES = new HashMap<>();
	/** Stakes an NPC won and carries until it is beaten (dropped on its death) or leaves. */
	private static final Map<UUID, List<ItemStack>> CARRIED = new HashMap<>();

	/**
	 * Registers {@code provider} for every entity of {@code type} (C35.S2). A later registration for the same type
	 * replaces the earlier one. Entities implementing {@link Duelist} need no registration.
	 */
	public static void register(EntityType<?> type, DuelistProfile.Provider provider) {
		if (type != null && provider != null) {
			PROVIDERS.put(type, provider);
		}
	}

	/** Sets (or with null clears) the profile of one entity. */
	public static void override(Entity entity, @Nullable DuelistProfile profile) {
		if (entity == null) {
			return;
		}
		if (profile == null) {
			OVERRIDES.remove(entity.getUUID());
		} else {
			OVERRIDES.put(entity.getUUID(), profile);
		}
	}

	/** The profile of a non-player duelist, or null when it does not duel. */
	public static @Nullable DuelistProfile profile(@Nullable LivingEntity entity) {
		if (entity == null || entity instanceof ServerPlayer) {
			return null;
		}
		DuelistProfile override = OVERRIDES.isEmpty() ? null : OVERRIDES.get(entity.getUUID());
		if (override != null) {
			return override;
		}
		if (entity instanceof Duelist duelist) {
			try {
				return duelist.duelProfile();
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.warn("Duelist profile of {} failed", entity, exception);
				return null;
			}
		}
		DuelistProfile.Provider provider = PROVIDERS.get(entity.getType());
		if (provider != null) {
			try {
				return provider.profile(entity);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.warn("Duelist provider for {} failed", entity.getType(), exception);
				return null;
			}
		}
		if (entity instanceof RogueCultivatorEntity rogue) {
			return rogue(rogue);
		}
		if (entity instanceof DiscipleEntity disciple) {
			return disciple(disciple);
		}
		return null;
	}

	/** True when {@code entity} can be challenged by a player at all (players, disciples, profiled NPCs). */
	public static boolean challengeable(LivingEntity entity) {
		if (entity instanceof ServerPlayer) {
			return true;
		}
		if (entity instanceof RogueCultivatorEntity rogue) {
			int role = rogue.role();
			return role != RogueCultivators.ROLE_RAID && role != RogueCultivators.ROLE_REALM && profile(rogue) != null;
		}
		return profile(entity) != null;
	}

	// ================================================================ built-in providers

	/**
	 * Rogue cultivators (35.7.6): the wild ones press ({@code AGRESIF}), the ghosts trick ({@code PENIPU}), the energy
	 * bodies wait ({@code BERTAHAN}), the demonic press; a tier-4 patriarch is a {@code SANTO_PEDANG}. Rank from its realm
	 * and stage; stones by tier.
	 */
	private static DuelistProfile rogue(RogueCultivatorEntity rogue) {
		int tier = Math.max(1, Math.min(4, rogue.tier()));
		DuelStyle style;
		if (tier >= 4) {
			style = DuelStyle.SANTO_PEDANG;
		} else {
			style = switch (rogue.variant()) {
				case RogueCultivators.VARIANT_GHOST -> DuelStyle.PENIPU;
				case RogueCultivators.VARIANT_ENERGY -> DuelStyle.BERTAHAN;
				default -> DuelStyle.AGRESIF;
			};
		}
		Realm realm = rogue.realmValue();
		int stage = Math.max(0, Math.min(realm.stages() - 1, rogue.stage()));
		int step = new Rank(realm, 0, stage).step();
		return new DuelistProfile(style, step, WeaponClass.of(rogue), rogue.element().color(), "grunt.male.attack", 900 + 100 * tier,
				style.surrender, ROGUE_STONE_GRADE[tier], ROGUE_STONE_COUNT[tier], null, false);
	}

	/**
	 * Disciples and disciple bodies (35.7.6): the Sword Dao from 60 comprehension is a {@code SANTO_PEDANG}; the Shadow Dao
	 * tricks; otherwise pressing or waiting, rolled once from the soul id. Honour from the records (never the master's).
	 */
	private static DuelistProfile disciple(DiscipleEntity disciple) {
		DiscipleData data = disciple.data();
		Dao dao = data.dao();
		UUID soul = disciple.soulId();
		DuelStyle style;
		if (dao == Dao.SWORD && data.comprehension() >= 60.0D) {
			style = DuelStyle.SANTO_PEDANG;
		} else if (dao == Dao.SHADOW) {
			style = DuelStyle.PENIPU;
		} else {
			long bits = soul == null ? disciple.getUUID().getLeastSignificantBits() : soul.getLeastSignificantBits();
			style = (bits & 1L) == 0L ? DuelStyle.BERTAHAN : DuelStyle.AGRESIF;
		}
		int color = dao != null ? dao.color() : data.rootColor();
		String kiai = VoiceDisciples.female(disciple) ? "grunt.female.attack" : "grunt.male.attack";
		int honour = DuelRecords.DEFAULT_HONOUR;
		MinecraftServer server = disciple.level() instanceof ServerLevel level ? level.getServer() : null;
		if (server != null && soul != null) {
			honour = DuelRecords.get(server).honour(soul);
		}
		return new DuelistProfile(style, data.rank().step(), WeaponClass.of(disciple), color, kiai, honour, style.surrender, 0, 0, null,
				false);
	}

	// ================================================================ carried stakes

	/** Gives {@code npc} a stake it won; it drops when the NPC dies (or is lost when it simply leaves the world). */
	public static void carry(LivingEntity npc, ItemStack stack) {
		if (npc == null || stack == null || stack.isEmpty()) {
			return;
		}
		CARRIED.computeIfAbsent(npc.getUUID(), id -> new ArrayList<>()).add(stack.copy());
	}

	/** What {@code npc} carries (a copy). */
	public static List<ItemStack> carried(LivingEntity npc) {
		List<ItemStack> list = npc == null ? null : CARRIED.get(npc.getUUID());
		return list == null ? List.of() : List.copyOf(list);
	}

	/** On an NPC's death: its carried stakes fall at its feet. */
	public static void onDeath(LivingEntity entity) {
		List<ItemStack> list = CARRIED.isEmpty() ? null : CARRIED.remove(entity.getUUID());
		if (list == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		for (ItemStack stack : list) {
			ItemEntity item = new ItemEntity(level, entity.getX(), entity.getY() + 0.5D, entity.getZ(), stack);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
	}

	/** Forgets carried stakes of entities that are gone (every few seconds). */
	public static void prune(MinecraftServer server) {
		if (CARRIED.isEmpty() && OVERRIDES.isEmpty()) {
			return;
		}
		CARRIED.keySet().removeIf(id -> find(server, id) == null);
		OVERRIDES.keySet().removeIf(id -> find(server, id) == null);
	}

	private static @Nullable Entity find(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(id);
			if (entity != null && !entity.isRemoved()) {
				return entity;
			}
		}
		return null;
	}

	public static void clear() {
		OVERRIDES.clear();
		CARRIED.clear();
	}

	private DuelistRegistry() {
	}
}
