package dev.kultivator.sekte.diplomasi;

import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.api.SekteApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The one list of NPC sects of a world (integration audit T12), readable by every module whether the Diplomasi module is
 * on or off. Sect {@code i} has the Diplomasi id {@code "s" + i}, its travelling band is the Saingan rival group
 * {@code "g" + i}, its map domain is {@code "sekte:s" + i} and its gate column comes from {@link GerbangPlan#of(long)}.
 * With Diplomasi on, names, tiers and gates come from {@link DiplomasiService} (the live sects: tiers grow, gates are
 * born); with it off, from the seeded plan, which is the same list the live sects were founded from.
 * <p>
 * Server only; every method is cheap (the plan is cached per seed, the service reads saved data already in memory).
 */
public final class DaftarSekte {

	/** Alignments as {@link SekteNpc}: righteous, neutral, demonic. */
	public static final int LURUS = SekteNpc.LURUS;
	public static final int NETRAL = SekteNpc.NETRAL;
	public static final int IBLIS = SekteNpc.IBLIS;

	/** The Diplomasi sect id of index {@code i}. */
	public static String id(int index) {
		return "s" + index;
	}

	/** The Saingan rival group id of index {@code i}. */
	public static String rivalId(int index) {
		return "g" + index;
	}

	/** The map domain id of index {@code i}. */
	public static String domainId(int index) {
		return "sekte:" + id(index);
	}

	/** The index of a sect id {@code "s<i>"}, a rival group id {@code "g<i>"} or a domain id {@code "sekte:s<i>"}; -1 otherwise. */
	public static int index(@Nullable String id) {
		if (id == null) {
			return -1;
		}
		String rest = id.startsWith("sekte:") ? id.substring(6) : id;
		if (rest.length() < 2 || rest.charAt(0) != 's' && rest.charAt(0) != 'g') {
			return -1;
		}
		try {
			int i = Integer.parseInt(rest.substring(1));
			return i >= 0 ? i : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** The plan of this world (the plan the live sects were founded with: {@code Simulasi.keepPlanOf} pins the config). */
	public static GerbangPlan.Plan plan(MinecraftServer server) {
		return GerbangPlan.of(server.overworld().getSeed());
	}

	/** The plan entry of a sect, rival group or domain id, or null. */
	public static @Nullable GerbangPlan.Entry entry(MinecraftServer server, @Nullable String id) {
		return plan(server).entry(index(id));
	}

	/** Every sect id of the world, in index order. */
	public static List<String> ids(MinecraftServer server) {
		List<String> live = SekteApi.diplomasi().sects();
		if (!live.isEmpty()) {
			return live;
		}
		List<String> ids = new ArrayList<>();
		for (GerbangPlan.Entry e : plan(server).entries()) {
			ids.add(e.id());
		}
		return ids;
	}

	/** The sect's name (the live sect's when Diplomasi knows it, else the plan's); a literal id for an unknown one. */
	public static Component name(MinecraftServer server, String sectId) {
		Component live = SekteApi.diplomasi().name(sectId);
		if (live != null) {
			return live;
		}
		GerbangPlan.Entry e = entry(server, sectId);
		return e == null ? Component.literal(sectId) : e.name();
	}

	/** The sect master's title and name ("Ketua Mo Tianhe"), or null for an unknown sect. */
	public static @Nullable Component master(MinecraftServer server, String sectId) {
		Component live = SekteApi.diplomasi().master(sectId);
		if (live != null) {
			return live;
		}
		GerbangPlan.Entry e = entry(server, sectId);
		return e == null ? null : e.master();
	}

	/** The alignment ({@link #LURUS}, {@link #NETRAL}, {@link #IBLIS}), -1 for an unknown sect. */
	public static int aliran(MinecraftServer server, String sectId) {
		int live = SekteApi.diplomasi().aliran(sectId);
		if (live >= 0) {
			return live;
		}
		GerbangPlan.Entry e = entry(server, sectId);
		return e == null ? -1 : e.aliran();
	}

	/** The tier 1-9 (live, grown by the simulation) or the founding tier 1-5; 0 for an unknown sect. */
	public static int tier(MinecraftServer server, String sectId) {
		int live = SekteApi.diplomasi().tier(sectId);
		if (live > 0) {
			return live;
		}
		GerbangPlan.Entry e = entry(server, sectId);
		return e == null ? 0 : e.tier();
	}

	/** The emblem (live or founding; both are the same seeded emblem). */
	public static Lambang emblem(MinecraftServer server, String sectId) {
		GerbangPlan.Entry e = entry(server, sectId);
		return e == null ? SekteApi.diplomasi().emblem(sectId) : e.emblem();
	}

	/** The sect's ink colour (map, band robes, name plates). */
	public static int warna(MinecraftServer server, String sectId) {
		GerbangPlan.Entry e = entry(server, sectId);
		return e == null ? 0xB0B0B0 : e.warna();
	}

	/** The gate: the born gate (real height) when known, else the planned column (y 64); null for an unknown sect. */
	public static @Nullable BlockPos gate(MinecraftServer server, String sectId) {
		BlockPos born = SekteApi.diplomasi().gate(sectId);
		if (born != null) {
			return born;
		}
		GerbangPlan.Entry e = entry(server, sectId);
		return e == null ? null : e.column();
	}

	/** The sect of alignment {@code aliran} (-1 = any) whose gate lies nearest {@code pos}, or null when there is none. */
	public static @Nullable String nearest(MinecraftServer server, BlockPos pos, int aliran) {
		GerbangPlan.Entry e = plan(server).nearest(pos.getX(), pos.getZ(), aliran);
		return e == null ? null : e.id();
	}

	private DaftarSekte() {
	}
}
