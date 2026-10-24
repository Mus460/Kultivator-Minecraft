package dev.kultivator.dunia.peta;

import dev.kultivator.sekte.diplomasi.DaftarSekte;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The NPC sect domains of the world map (DESIGN 34.5.16): {@code npcDomains} lore regions of the overworld, each an inked
 * border with a seal and a name. Pure lore: a domain changes no rule, it only names who guards a town inside it.
 * <p>
 * One list of sects (integration audit T12): domain {@code "sekte:s<i>"} belongs to NPC sect {@code s<i>} of
 * {@link DaftarSekte} (the same entry is the Diplomasi sect, the Saingan band {@code g<i>}, the elite tournament guest and
 * the sect compound of DESIGN 38). Its centre is the sect's gate (the generated gate once born, else the planned column),
 * its name comes from {@code DiplomasiService} (the plan's own name while that module is off), its ink is the sect's
 * emblem colour and its seal the glyph of the sect's name word. The map has no sects of its own any more.
 * <p>
 * Terinspirasi dari wilayah sekte-sekte besar dalam novel xianxia; nama dan letaknya rancangan game.
 */
public final class PetaDomains {

	/** Domain radius: {@code 520 + [0, 620)} blocks, seeded per world and sect. */
	private static final int RADIUS_MIN = 520;
	private static final int RADIUS_SPREAD = 620;

	/**
	 * One domain. {@code name} is a component (the sect's name); {@code seal} is one glyph, or {@code #<lang key>} of the
	 * glyph (resolved by the client).
	 */
	public record Domain(String id, Component name, String seal, BlockPos centre, int radius, int color) {
	}

	/** The domains of {@code level} (overworld only): the first {@code count} sects of the one list, in index order. */
	public static List<Domain> of(ServerLevel level, int count) {
		List<Domain> out = new ArrayList<>();
		if (count <= 0 || !Level.OVERWORLD.equals(level.dimension())) {
			return out;
		}
		MinecraftServer server = level.getServer();
		Random random = new Random(level.getSeed() ^ 0x34A67D0E1A5L);
		for (String id : DaftarSekte.ids(server)) {
			if (out.size() >= count) {
				break;
			}
			int radius = RADIUS_MIN + random.nextInt(RADIUS_SPREAD);
			GerbangPlan.Entry entry = DaftarSekte.entry(server, id);
			BlockPos centre = DaftarSekte.gate(server, id);
			if (entry == null || centre == null) {
				continue;
			}
			out.add(new Domain(DaftarSekte.domainId(entry.index()), DaftarSekte.name(server, id), entry.seal(), centre, radius,
					0xFFFFFF & entry.warna()));
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

	private PetaDomains() {
	}
}
