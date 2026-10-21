package dev.kultivator.dunia.kota;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData {@code kultivator:kota_saingan} in the overworld (DESIGN 34.5.3): the rival groups of this world, seeded once
 * from the world seed: name, style, colour, home region, the looks of the leader and 2-4 members, wins and losses, and
 * per player a grudge 0-100 and whether they beat the leader. Also the strongest realm each player reached, with the day
 * it was seen, so a group never grows more than one realm above the strongest player of the last 7 days.
 */
public final class SainganData extends SavedData {

	public static final Codec<SainganData> CODEC = CompoundTag.CODEC.xmap(SainganData::fromTag, SainganData::toTag);
	public static final SavedDataType<SainganData> TYPE = new SavedDataType<>(Kultivator.id("kota_saingan"), SainganData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Styles (lang {@code kultivator.kota.gaya.<id>}). */
	public static final String[] STYLES = {"licik", "angkuh", "pembunuh", "tubuh", "racun", "netral"};
	/** Twelve group names (lang {@code kultivator.kota.grup.<n>}), each with its style and colour. */
	public static final int NAMES = 12;
	public static final int[] NAME_STYLE = {0, 1, 2, 3, 4, 5, 1, 2, 0, 3, 4, 5};
	public static final int[] NAME_COLOR = {0x3A3050, 0x5A8AD8, 0xC02838, 0x8A6A40, 0x5AA040, 0xA89878, 0xE0E0F0, 0x6A1A40, 0x40406A, 0xB07A30,
			0x7AC070, 0xD8B060};

	public static final class Group {
		public final String id;
		public final int name;
		public final int style;
		public final int color;
		public final BlockPos region;
		public final int leaderLook;
		public final int[] members;
		public int wins;
		public int losses;
		final Map<UUID, Integer> grudge = new HashMap<>();
		final Map<UUID, Boolean> beaten = new HashMap<>();

		Group(String id, int name, int style, int color, BlockPos region, int leaderLook, int[] members) {
			this.id = id;
			this.name = name;
			this.style = style;
			this.color = color;
			this.region = region;
			this.leaderLook = leaderLook;
			this.members = members;
		}

		public int grudge(UUID player) {
			return Mth.clamp(grudge.getOrDefault(player, 0), 0, 100);
		}

		public boolean beatenBy(UUID player) {
			return beaten.getOrDefault(player, false);
		}
	}

	private final List<Group> groups = new ArrayList<>();
	private final Map<UUID, int[]> strongest = new HashMap<>();
	private boolean seeded;

	public SainganData() {
	}

	public static SainganData get(MinecraftServer server) {
		SainganData data = server.overworld().getDataStorage().computeIfAbsent(TYPE);
		if (!data.seeded) {
			data.seed(server.overworld().getSeed(), Kota.config().rivalGroups);
		}
		return data;
	}

	private void seed(long worldSeed, int count) {
		groups.clear();
		RandomSource random = RandomSource.create(worldSeed ^ 0x5A1A6A4L);
		List<Integer> names = new ArrayList<>();
		for (int i = 0; i < NAMES; i++) {
			names.add(i);
		}
		for (int i = 0; i < count && !names.isEmpty(); i++) {
			int name = names.remove(random.nextInt(names.size()));
			double angle = Math.PI * 2.0D * i / count + random.nextDouble() * 0.6D;
			double distance = 900.0D + random.nextDouble() * 4200.0D;
			BlockPos region = new BlockPos((int) (Math.cos(angle) * distance), 80, (int) (Math.sin(angle) * distance));
			int size = 2 + random.nextInt(3);
			int[] members = new int[size];
			for (int m = 0; m < size; m++) {
				members[m] = random.nextInt();
			}
			groups.add(new Group("g" + i, name, NAME_STYLE[name], NAME_COLOR[name], region, random.nextInt(), members));
		}
		seeded = true;
		setDirty();
	}

	public List<Group> groups() {
		return groups;
	}

	public Group byId(String id) {
		for (Group group : groups) {
			if (group.id.equals(id)) {
				return group;
			}
		}
		return null;
	}

	public int grudge(String group, UUID player) {
		Group g = byId(group);
		return g == null ? 0 : g.grudge(player);
	}

	public void setGrudge(String group, UUID player, int value) {
		Group g = byId(group);
		if (g != null) {
			g.grudge.put(player, Mth.clamp(value, 0, 100));
			setDirty();
		}
	}

	public void addGrudge(String group, UUID player, int delta) {
		setGrudge(group, player, grudge(group, player) + delta);
	}

	public void setBeaten(String group, UUID player) {
		Group g = byId(group);
		if (g != null) {
			g.beaten.put(player, true);
			setDirty();
		}
	}

	public void win(String group) {
		Group g = byId(group);
		if (g != null) {
			g.wins++;
			setDirty();
		}
	}

	public void loss(String group) {
		Group g = byId(group);
		if (g != null) {
			g.losses++;
			setDirty();
		}
	}

	/** Remembers the player's realm for today. */
	public void seen(UUID player, int realm, long day) {
		int[] entry = strongest.get(player);
		if (entry == null || entry[0] != realm || entry[1] != (int) day) {
			strongest.put(player, new int[]{realm, (int) day});
			setDirty();
		}
	}

	/** The strongest realm seen within the last 7 days (0 when nobody). */
	public int strongestRecent(long day) {
		int best = 0;
		for (int[] entry : strongest.values()) {
			if (day - entry[1] <= 7L) {
				best = Math.max(best, entry[0]);
			}
		}
		return best;
	}

	private static SainganData fromTag(CompoundTag tag) {
		SainganData data = new SainganData();
		data.seeded = tag.getBooleanOr("seeded", false);
		for (Tag each : tag.getListOrEmpty("groups")) {
			if (!(each instanceof CompoundTag g)) {
				continue;
			}
			ListTag memberList = g.getListOrEmpty("members");
			int[] members = new int[memberList.size()];
			for (int i = 0; i < members.length; i++) {
				members[i] = memberList.getIntOr(i, 0);
			}
			int name = Mth.clamp(g.getIntOr("name", 0), 0, NAMES - 1);
			Group group = new Group(g.getStringOr("id", "g0"), name, g.getIntOr("style", NAME_STYLE[name]), g.getIntOr("color", NAME_COLOR[name]),
					BlockPos.of(g.getLongOr("region", 0L)), g.getIntOr("leader", 0), members);
			group.wins = g.getIntOr("wins", 0);
			group.losses = g.getIntOr("losses", 0);
			CompoundTag grudge = g.getCompoundOrEmpty("grudge");
			for (String key : grudge.keySet()) {
				try {
					group.grudge.put(UUID.fromString(key), grudge.getIntOr(key, 0));
				} catch (IllegalArgumentException ignored) {
					// dropped
				}
			}
			CompoundTag beaten = g.getCompoundOrEmpty("beaten");
			for (String key : beaten.keySet()) {
				try {
					group.beaten.put(UUID.fromString(key), beaten.getBooleanOr(key, false));
				} catch (IllegalArgumentException ignored) {
					// dropped
				}
			}
			data.groups.add(group);
		}
		CompoundTag strong = tag.getCompoundOrEmpty("strongest");
		for (String key : strong.keySet()) {
			try {
				int[] value = strong.getIntArray(key).orElse(new int[]{0, 0});
				if (value.length == 2) {
					data.strongest.put(UUID.fromString(key), value);
				}
			} catch (IllegalArgumentException ignored) {
				// dropped
			}
		}
		if (data.groups.isEmpty()) {
			data.seeded = false;
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("seeded", seeded);
		ListTag list = new ListTag();
		for (Group group : groups) {
			CompoundTag g = new CompoundTag();
			g.putString("id", group.id);
			g.putInt("name", group.name);
			g.putInt("style", group.style);
			g.putInt("color", group.color);
			g.putLong("region", group.region.asLong());
			g.putInt("leader", group.leaderLook);
			ListTag members = new ListTag();
			for (int member : group.members) {
				members.add(net.minecraft.nbt.IntTag.valueOf(member));
			}
			g.put("members", members);
			g.putInt("wins", group.wins);
			g.putInt("losses", group.losses);
			CompoundTag grudge = new CompoundTag();
			group.grudge.forEach((key, value) -> grudge.putInt(key.toString(), value));
			g.put("grudge", grudge);
			CompoundTag beaten = new CompoundTag();
			group.beaten.forEach((key, value) -> beaten.putBoolean(key.toString(), value));
			g.put("beaten", beaten);
			list.add(g);
		}
		tag.put("groups", list);
		CompoundTag strong = new CompoundTag();
		strongest.forEach((key, value) -> strong.putIntArray(key.toString(), value));
		tag.put("strongest", strong);
		return tag;
	}
}
