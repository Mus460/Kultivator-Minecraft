package dev.kultivator.dunia.kota;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.DaftarSekte;
import dev.kultivator.sekte.diplomasi.GeneratorNama;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

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
 * <p>
 * <b>One list (integration audit T12).</b> The groups are not a list of their own: group {@code "g" + i} is the travelling
 * band of NPC sect {@code "s" + i} of {@link GerbangPlan} (Diplomasi 36), so there are {@code diplomasi.jumlahSekte}
 * groups, each carries its sect's name (read from {@code DiplomasiService}, the plan's own name when that module is off),
 * its home region is the sect's planned gate column, its colour is the sect's emblem ink and its style follows the sect's
 * name word and alignment ({@link #styleOf}). The map domains, the elite tournament and the sect compounds of DESIGN 38
 * read the same entries.
 */
public final class SainganData extends SavedData {

	public static final Codec<SainganData> CODEC = CompoundTag.CODEC.xmap(SainganData::fromTag, SainganData::toTag);
	public static final SavedDataType<SainganData> TYPE = new SavedDataType<>(Kultivator.id("kota_saingan"), SainganData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Styles (lang {@code kultivator.kota.gaya.<id>}). */
	public static final String[] STYLES = {"licik", "angkuh", "pembunuh", "tubuh", "racun", "netral"};
	public static final int LICIK = 0;
	public static final int ANGKUH = 1;
	public static final int PEMBUNUH = 2;
	public static final int TUBUH = 3;
	public static final int RACUN = 4;
	public static final int NETRAL = 5;
	/**
	 * The band's style by its sect's noun A ({@code kultivator.sekte.diplomasi.nama.a.<n>}): sword, jade, dragon, phoenix
	 * and thunder sects walk proud; bell, tiger and spear sects refine the body; fan, wind and ghost sects are cunning;
	 * cauldron and poison sects brew poison; blood, skull and shadow sects hire out as killers; lotus, crane, scripture and
	 * moon sects stay neutral. {@link #styleOf} then bends it by alignment.
	 */
	private static final int[] STYLE_BY_WORD = {ANGKUH, NETRAL, NETRAL, ANGKUH, NETRAL, TUBUH, ANGKUH, ANGKUH, LICIK, NETRAL, TUBUH, TUBUH, ANGKUH,
			RACUN, LICIK, PEMBUNUH, RACUN, PEMBUNUH, LICIK, PEMBUNUH};

	public static final class Group {
		public final String id;
		/** The NPC sect index of {@link GerbangPlan} this group is the band of ({@code "s" + sect}). */
		public final int sect;
		final int pola;
		final int kataA;
		final int kataB;
		public final int style;
		public final int color;
		public final BlockPos region;
		public final int leaderLook;
		public final int[] members;
		public int wins;
		public int losses;
		final Map<UUID, Integer> grudge = new HashMap<>();
		final Map<UUID, Boolean> beaten = new HashMap<>();

		Group(String id, int sect, int pola, int kataA, int kataB, int style, int color, BlockPos region, int leaderLook, int[] members) {
			this.id = id;
			this.sect = sect;
			this.pola = pola;
			this.kataA = kataA;
			this.kataB = kataB;
			this.style = style;
			this.color = color;
			this.region = region;
			this.leaderLook = leaderLook;
			this.members = members;
		}

		/** The Diplomasi id of the sect this group walks for. */
		public String sectId() {
			return DaftarSekte.id(sect);
		}

		/**
		 * The group's (its sect's) name, plain: {@code DiplomasiService.name} when the Diplomasi module knows the sect, else
		 * the same generated name from the plan words.
		 */
		public Component name() {
			Component live = SekteApi.diplomasi().name(sectId());
			return live != null ? live : GeneratorNama.sect(pola, kataA, kataB);
		}

		/** Writes the name words for a client screen ({@code pola}, {@code a}, {@code b}; see {@link #nameFrom}). */
		public void putName(CompoundTag tag) {
			tag.putInt("pola", pola);
			tag.putInt("a", kataA);
			tag.putInt("b", kataB);
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
			data.seed(server.overworld().getSeed());
		}
		return data;
	}

	private void seed(long worldSeed) {
		groups.clear();
		groups.addAll(plan(worldSeed));
		seeded = true;
		setDirty();
	}

	/** The groups a world seed gives: one per entry of {@link GerbangPlan#of(long)}, in index order (pure). */
	private static List<Group> plan(long worldSeed) {
		List<Group> list = new ArrayList<>();
		RandomSource random = RandomSource.create(worldSeed ^ 0x5A1A6A4L);
		for (GerbangPlan.Entry e : GerbangPlan.of(worldSeed).entries()) {
			int size = 2 + random.nextInt(3);
			int[] members = new int[size];
			for (int m = 0; m < size; m++) {
				members[m] = random.nextInt();
			}
			list.add(new Group(e.rivalId(), e.index(), e.pola(), e.kataA(), e.kataB(), styleOf(e), e.warna(), region(e), random.nextInt(), members));
		}
		return list;
	}

	/** The band's home region: its sect's planned gate column at y 80 (the same function places the gate). */
	private static BlockPos region(GerbangPlan.Entry e) {
		return new BlockPos(e.blockX(), 80, e.blockZ());
	}

	/**
	 * The style of the band of sect {@code e}: {@link #STYLE_BY_WORD} of its noun A, bent by alignment (a righteous sect
	 * never sends cunning thieves, killers or poisoners: they walk neutral; a demonic sect is never merely proud or
	 * neutral: it walks cunning).
	 */
	public static int styleOf(GerbangPlan.Entry e) {
		int style = STYLE_BY_WORD[Math.floorMod(e.kataA(), STYLE_BY_WORD.length)];
		if (e.aliran() == DaftarSekte.LURUS && (style == LICIK || style == PEMBUNUH || style == RACUN)) {
			return NETRAL;
		}
		if (e.aliran() == DaftarSekte.IBLIS && (style == ANGKUH || style == NETRAL)) {
			return LICIK;
		}
		return style;
	}

	/** The name a client rebuilds from {@link Group#putName} ({@code GeneratorNama} lang keys, so each language orders it). */
	public static Component nameFrom(CompoundTag tag) {
		return GeneratorNama.sect(tag.getIntOr("pola", 0), tag.getIntOr("a", 0), tag.getIntOr("b", 0));
	}

	/**
	 * The seeded centre of rival group {@code i} ({@code "g" + i}) of a world seed, from the seed alone (no server): the
	 * planned gate column of sect {@code i}. Null when {@code i} is out of range.
	 */
	public static @Nullable BlockPos regionOf(long worldSeed, int i) {
		GerbangPlan.Entry e = GerbangPlan.of(worldSeed).entry(i);
		return e == null ? null : region(e);
	}

	/** The saved region of group {@code i} of this world (what the map and the bands use), or null. */
	public static @Nullable BlockPos regionOf(MinecraftServer server, int i) {
		Group group = get(server).byId(DaftarSekte.rivalId(i));
		return group == null ? null : group.region;
	}

	/** The style word of group {@code groupId} ({@code kultivator.kota.gaya.<style>}); "netral" for an unknown group. */
	public static Component styleName(MinecraftServer server, String groupId) {
		Group group = get(server).byId(groupId);
		int style = group == null ? NETRAL : Math.floorMod(group.style, STYLES.length);
		return Component.translatable("kultivator.kota.gaya." + STYLES[style]);
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
			Group group = new Group(g.getStringOr("id", "g0"), g.getIntOr("sect", 0), g.getIntOr("pola", 0), g.getIntOr("a", 0), g.getIntOr("b", 0),
					Math.floorMod(g.getIntOr("style", NETRAL), STYLES.length), g.getIntOr("color", 0xB0B0B0), BlockPos.of(g.getLongOr("region", 0L)),
					g.getIntOr("leader", 0), members);
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
			g.putInt("sect", group.sect);
			group.putName(g);
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
