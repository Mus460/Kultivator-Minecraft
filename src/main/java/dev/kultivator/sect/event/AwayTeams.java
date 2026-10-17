package dev.kultivator.sect.event;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleItems;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Disciples away from the world on this module's errands (DESIGN 20.3.10, 20.3.11): expeditions, envoys, the forbidden
 * ground and the Primordial Ark. {@link Disciples#depart} turns each body into pure data (zero ticking, still counted
 * toward the limit and shown as away in the master's list); the team record keeps those tags in the owner's
 * {@code activity.teams} with its return time in game time. A due team is resolved once (on the hall tick, when the hall
 * opens or when the master joins), then each member {@link Disciples#arrive arrives} beside the stele, or beside the
 * master while the hall is dormant or unloaded. "Panggil Pulang" brings a team back at once with nothing gained.
 * <p>
 * This module keeps its own away records (instead of {@code Halls.storeAway}) because a team must be recallable at once
 * and a Hardcore loss in the forbidden ground returns as a Soul Tablet instead of a body.
 */
final class AwayTeams {

	static final String KIND_EXPEDITION = "expedition";
	static final String KIND_ENVOY = "envoy";
	static final String KIND_FORBIDDEN = "forbidden";
	static final String KIND_ARK = "ark";
	static final String TEAMS = "teams";

	/** Every departing or returning member gets this much space around the stele (blocks). */
	private static final double ARRIVAL_RADIUS = 3.0D;

	// ================================================================== departure

	/**
	 * Sends {@code members} away as one team of {@code kind} for {@code duration} ticks (duty posts, tower training and
	 * queues are left first). {@code record} gets the kind's own keys. Returns the team record, or null when nobody could
	 * leave.
	 */
	static @Nullable CompoundTag depart(ServerPlayer player, String kind, List<DiscipleEntity> members, long duration, CompoundTag record) {
		UUID owner = player.getUUID();
		ListTag tags = new ListTag();
		for (DiscipleEntity member : members) {
			UUID soul = member.soulId();
			Duties.release(owner, soul, null);
			BlazingTower.stop(owner, soul, false);
			VoidRealm.dequeue(owner, soul);
			if (member.level() instanceof ServerLevel level) {
				Vec3 at = member.position();
				EventUtil.fx(level, at, SectEventFxIds.DEPART, member.data().rootColor(), 0.0F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.8F, 0.9F);
			}
			CompoundTag tag = Disciples.depart(member, kind);
			if (!tag.isEmpty()) {
				tags.add(tag);
			}
		}
		if (tags.isEmpty()) {
			return null;
		}
		CompoundTag section = EventUtil.section(owner);
		int id = section.getIntOr("team_seq", 0) + 1;
		section.putInt("team_seq", id);
		long now = EventUtil.gameTime();
		record.putInt("id", id);
		record.putString("kind", kind);
		record.putLong("start", now);
		record.putLong("return_at", now + Math.max(20L, duration));
		record.put("members", tags);
		EventUtil.list(section, TEAMS).add(record);
		EventUtil.dirty();
		for (Tag tag : tags) {
			UUID soul = tag instanceof CompoundTag member ? soul(member) : null;
			if (soul != null) {
				Disciples.setAwayUntil(EventUtil.server(), owner, soul, record.getLongOr("return_at", now));
			}
		}
		return record;
	}

	/** Disciples on the owner's errands right now. */
	static int awayCount(UUID owner) {
		int count = 0;
		for (Tag tag : EventUtil.section(owner).getListOrEmpty(TEAMS)) {
			if (tag instanceof CompoundTag team) {
				count += team.getListOrEmpty("members").size();
			}
		}
		return count;
	}

	/** "Panggil Pulang": the team with this id comes back at once with nothing gained. */
	static boolean recall(ServerPlayer player, int id) {
		UUID owner = player.getUUID();
		for (Tag tag : EventUtil.section(owner).getListOrEmpty(TEAMS)) {
			if (tag instanceof CompoundTag team && team.getIntOr("id", -1) == id && !team.getBooleanOr("resolved", false)) {
				team.putBoolean("recalled", true);
				EventUtil.dirty();
				EventUtil.chat(player, Component.translatable("kultivator.sectevent.team.recalled").withStyle(ChatFormatting.GRAY));
				resolveDue(owner, Halls.hallOf(owner));
				return true;
			}
		}
		return false;
	}

	/** Every team's data when the test command finishes them at once. */
	static int finishAll(UUID owner) {
		int count = 0;
		for (Tag tag : EventUtil.section(owner).getListOrEmpty(TEAMS)) {
			if (tag instanceof CompoundTag team) {
				team.putLong("return_at", 0L);
				count++;
			}
		}
		EventUtil.dirty();
		return count;
	}

	// ================================================================== resolution and arrival

	/** Resolves every due team once and lets its members arrive where they can. */
	static void resolveDue(UUID owner, @Nullable Halls.HallView hall) {
		ListTag teams = EventUtil.section(owner).getListOrEmpty(TEAMS);
		if (teams.isEmpty()) {
			return;
		}
		long now = EventUtil.gameTime();
		ServerPlayer player = EventUtil.player(owner);
		boolean changed = false;
		for (int i = teams.size() - 1; i >= 0; i--) {
			if (!(teams.get(i) instanceof CompoundTag team)) {
				teams.remove(i);
				continue;
			}
			boolean recalled = team.getBooleanOr("recalled", false);
			if (!recalled && team.getLongOr("return_at", 0L) > now) {
				continue;
			}
			if (!team.getBooleanOr("resolved", false)) {
				team.putBoolean("resolved", true);
				changed = true;
				if (recalled) {
					EventUtil.chat(player, Component.translatable("kultivator.sectevent.team.back_empty").withStyle(ChatFormatting.GRAY));
				} else {
					outcome(owner, hall, team, player);
				}
			}
			ListTag members = team.getListOrEmpty("members");
			for (int j = members.size() - 1; j >= 0; j--) {
				if (!(members.get(j) instanceof CompoundTag member) || arrive(owner, hall, member, player)) {
					members.remove(j);
					changed = true;
				}
			}
			if (members.isEmpty()) {
				teams.remove(i);
				changed = true;
			}
		}
		if (changed) {
			EventUtil.dirty();
		}
	}

	private static void outcome(UUID owner, @Nullable Halls.HallView hall, CompoundTag team, @Nullable ServerPlayer player) {
		RandomSource random = RandomSource.create();
		switch (team.getStringOr("kind", "")) {
			case KIND_EXPEDITION -> Expeditions.resolve(owner, team, player, random);
			case KIND_ENVOY -> Envoys.resolve(owner, team, player, random);
			case KIND_FORBIDDEN -> ForbiddenGround.resolve(owner, team, player, random);
			case KIND_ARK -> PrimordialArk.resolve(owner, team, player, random);
			default -> {
			}
		}
	}

	/** One member comes back (or its Soul Tablet goes to the storehouse). False when it has to wait. */
	private static boolean arrive(UUID owner, @Nullable Halls.HallView hall, CompoundTag member, @Nullable ServerPlayer player) {
		if (member.getBooleanOr("as_tablet", false)) {
			ItemStack tablet = tabletOf(member);
			ItemStack rest = Storehouse.insert(owner, tablet);
			if (rest.isEmpty()) {
				fallen(owner, member);
				return true;
			}
			if (player != null) {
				EventUtil.give(player, rest);
				fallen(owner, member);
				return true;
			}
			return false;
		}
		ServerLevel level = null;
		Vec3 base = null;
		if (hall != null && EventUtil.hallLoaded(hall)) {
			level = EventUtil.hallLevel(hall);
			base = EventUtil.center(hall);
		} else if (player != null && !player.isSpectator()) {
			level = player.level();
			base = player.position();
		}
		if (level == null) {
			return false;
		}
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
		Vec3 at = base.add(Math.cos(angle) * ARRIVAL_RADIUS, 0.0D, Math.sin(angle) * ARRIVAL_RADIUS);
		float hpFraction = member.getFloatOr("arrive_hp", 1.0F);
		member.remove("arrive_hp");
		DiscipleEntity disciple = Disciples.arrive(level, at, member, owner);
		if (disciple == null) {
			member.putFloat("arrive_hp", hpFraction);
			return false;
		}
		if (hpFraction < 1.0F) {
			disciple.setHealth(Math.max(1.0F, disciple.getMaxHealth() * hpFraction));
		}
		Vec3 pos = disciple.position();
		EventUtil.fx(level, pos, SectEventFxIds.RETURN, disciple.data().rootColor(), 0.0F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.8F, 1.3F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.1F);
		EventUtil.chat(player, Component.translatable("kultivator.sectevent.team.arrived", disciple.getDisplayName()).withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** The member came back as a Soul Tablet: its list entry is fallen now, no longer away (frees the cap slot). */
	private static void fallen(UUID owner, CompoundTag member) {
		UUID soul = soul(member);
		MinecraftServer server = EventUtil.server();
		if (soul != null && server != null) {
			Disciples.markFallen(server, owner, soul);
		}
	}

	/** A Soul Tablet holding the member (DESIGN 18: revivable by its master as usual). */
	private static ItemStack tabletOf(CompoundTag member) {
		CompoundTag soul = member.copy();
		soul.remove("health");
		soul.remove("away_reason");
		soul.remove("as_tablet");
		soul.remove("arrive_hp");
		ItemStack stack = new ItemStack(DiscipleItems.DISCIPLE_TABLET);
		CompoundTag custom = new CompoundTag();
		custom.put("kultivator_tablet", soul);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
		DiscipleData data = data(member);
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("kultivator.disciples.tablet.lore_name", name(member), data.rank().displayName()).withColor(0x9FF3E6),
				Component.translatable("kultivator.disciples.tablet.lore_master", member.getStringOr("owner_name", "?")).withColor(0xB8A98A))));
		return stack;
	}

	// ================================================================== member tags

	static DiscipleData data(CompoundTag member) {
		return DiscipleData.load(member.getCompoundOrEmpty("disciple"));
	}

	static void setData(CompoundTag member, DiscipleData data) {
		member.put("disciple", data.save());
	}

	static Component name(CompoundTag member) {
		return member.read("name", ComponentSerialization.CODEC).orElse(Component.translatable("entity.kultivator.disciple"));
	}

	static @Nullable UUID soul(CompoundTag member) {
		return member.read("soul", UUIDUtil.CODEC).orElse(null);
	}

	/** The members' data of every team still away (strength of the sect, envoy checks). */
	static List<DiscipleData> awayData(UUID owner) {
		List<DiscipleData> list = new ArrayList<>();
		for (Tag tag : EventUtil.section(owner).getListOrEmpty(TEAMS)) {
			if (tag instanceof CompoundTag team) {
				for (Tag each : team.getListOrEmpty("members")) {
					if (each instanceof CompoundTag member) {
						list.add(data(member));
					}
				}
			}
		}
		return list;
	}

	/** The teams as the Misi tablet shows them. */
	static ListTag forClient(UUID owner) {
		ListTag list = new ListTag();
		for (Tag tag : EventUtil.section(owner).getListOrEmpty(TEAMS)) {
			if (!(tag instanceof CompoundTag team)) {
				continue;
			}
			CompoundTag view = new CompoundTag();
			view.putInt("id", team.getIntOr("id", 0));
			view.putString("kind", team.getStringOr("kind", ""));
			view.putInt("type", team.getIntOr("type", 0));
			view.putInt("tier", team.getIntOr("tier", 0));
			view.putLong("start", team.getLongOr("start", 0L));
			view.putLong("return_at", team.getLongOr("return_at", 0L));
			view.putBoolean("resolved", team.getBooleanOr("resolved", false));
			ListTag names = new ListTag();
			for (Tag each : team.getListOrEmpty("members")) {
				if (each instanceof CompoundTag member) {
					CompoundTag entry = new CompoundTag();
					entry.store("name", ComponentSerialization.CODEC, name(member));
					names.add(entry);
				}
			}
			view.put("names", names);
			list.add(view);
		}
		return list;
	}

	private AwayTeams() {
	}
}
