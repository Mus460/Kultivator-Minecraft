package dev.kultivator.dunia.peta;

import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.RivalService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The NPC sect domains of the world map (DESIGN 34.5.16): {@code npcDomains} lore regions of the overworld, each an inked
 * border with a seal and a name. When module K's rival groups exist, the domains are theirs (their seeded home region,
 * name and colour, {@link RivalService}); otherwise the map seeds its own from the world seed, so every world has the
 * same sects in the same places. Pure lore: a domain changes no rule, it only names who guards a town inside it.
 * <p>
 * Terinspirasi dari wilayah sekte-sekte besar dalam novel xianxia; nama dan letaknya rancangan game.
 */
public final class PetaDomains {

	/** The map's own sects (lang {@code kultivator.peta.domain.<i>}, seal glyph {@code .seal}). */
	public static final int OWN = 16;
	private static final int[] COLORS = {0x3C7FD6, 0x6E8F2E, 0xBFD8F0, 0x8E5BD8, 0xD4502A, 0xA01E2A, 0xD4A637, 0x5A6BC8, 0x4FAE7E,
			0x7A7F86, 0x9AC7C0, 0x6C5CE0, 0xE8E6DA, 0x3A3446, 0x2F9FB0, 0x5E8C3A};

	/**
	 * One domain. {@code name} is a component (a rival's own name or the map's lang key); {@code seal} is one glyph, or
	 * {@code #<lang key>} of the glyph (resolved by the client).
	 */
	public record Domain(String id, Component name, String seal, BlockPos centre, int radius, int color) {
	}

	/** The domains of {@code level} (overworld only), at most {@code count}. */
	public static List<Domain> of(ServerLevel level, int count) {
		List<Domain> out = new ArrayList<>();
		if (count <= 0 || !Level.OVERWORLD.equals(level.dimension())) {
			return out;
		}
		RivalService rivals = DuniaApi.rivals();
		long seed = level.getSeed();
		Random random = new Random(seed ^ 0x34A67D0E1A5L);
		List<String> groups = rivals.all();
		if (!groups.isEmpty()) {
			for (String id : groups) {
				if (out.size() >= count) {
					break;
				}
				BlockPos centre = rivals.region(id);
				if (centre == null) {
					continue;
				}
				Component name = rivals.name(id);
				int radius = 520 + random.nextInt(620);
				out.add(new Domain(id, name == null ? Component.literal(id) : name, seal(id), centre, radius, 0xFFFFFF & rivals.color(id)));
			}
			if (!out.isEmpty()) {
				return out;
			}
		}
		int n = Math.min(count, OWN);
		int first = random.nextInt(OWN);
		double start = random.nextDouble() * 360.0D;
		for (int i = 0; i < n; i++) {
			int index = (first + i * 5) % OWN;
			double angle = Math.toRadians(start + i * (360.0D / n) + random.nextGaussian() * 9.0D);
			double distance = 1400.0D + random.nextInt(5200);
			int x = (int) Math.round(Math.cos(angle) * distance);
			int z = (int) Math.round(Math.sin(angle) * distance);
			int radius = 520 + random.nextInt(620);
			out.add(new Domain("peta:" + index, Component.translatable("kultivator.peta.domain." + index),
					"#kultivator.peta.domain." + index + ".seal", new BlockPos(x, 64, z), radius,
					COLORS[index]));
		}
		return out;
	}

	/** The domain containing {@code pos}, or null. */
	public static @Nullable Domain at(List<Domain> domains, BlockPos pos) {
		Domain best = null;
		double bestD = Double.MAX_VALUE;
		for (Domain domain : domains) {
			double dx = pos.getX() - domain.centre().getX();
			double dz = pos.getZ() - domain.centre().getZ();
			double d = dx * dx + dz * dz;
			if (d <= (double) domain.radius() * domain.radius() && d < bestD) {
				best = domain;
				bestD = d;
			}
		}
		return best;
	}

	/** The domains as the map sheet's list. */
	static ListTag toTag(List<Domain> domains, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Domain domain : domains) {
			CompoundTag d = new CompoundTag();
			d.putString("id", domain.id());
			d.store("name", ComponentSerialization.CODEC, registries.createSerializationContext(NbtOps.INSTANCE), domain.name());
			d.putString("seal", domain.seal());
			d.putInt("x", domain.centre().getX());
			d.putInt("z", domain.centre().getZ());
			d.putInt("r", domain.radius());
			d.putInt("color", domain.color());
			list.add(d);
		}
		return list;
	}

	/** A one-glyph seal for a rival group (the first letter of its id, upper case). */
	private static String seal(String id) {
		return id == null || id.isEmpty() ? "?" : id.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
	}

	private PetaDomains() {
	}
}
