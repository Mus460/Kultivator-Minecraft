package dev.kultivator.dunia.peta;

import dev.kultivator.desa.Vassals;
import dev.kultivator.desa.VillageView;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.dunia.api.TreasureBirth;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.Sects;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The marker sheet of the world map (DESIGN 34.5.16), built for one player of one dimension: their known sites (never an
 * undiscovered one), live omens near them (treasure births, meteor landings, dragon sightings), waypoints, their ship and
 * its voyage line, the return point and a soul's body, the sect territories they know (own, sect mates', seen) with
 * vassal villages, the NPC sect domains and the overlays (spirit weather around them, the running sky event, prison
 * seals). Cheap: no chunk is read; built when the screen opens and when something changes (at most once a second).
 */
final class PetaSheet {

	static CompoundTag build(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		MinecraftServer server = level.getServer();
		PetaData data = PetaData.get(server);
		CompoundTag tag = new CompoundTag();
		String dim = Peta.dimId(level);
		tag.putString("dim", dim);
		tag.putInt("cell", data.cellSize);
		tag.putInt("px", player.getBlockX());
		tag.putInt("pz", player.getBlockZ());
		tag.putFloat("yaw", player.getYRot());
		tag.putInt("explored", data.exploredCount(player.getUUID(), dim));
		tag.putInt("max_waypoints", Peta.config().waypoints);
		tag.putLongArray("regions", exploredRegions(player, data, dim));

		// known sites of this dimension
		ListTag sites = new ListTag();
		int known = 0;
		List<PetaDomains.Domain> domains = PetaDomains.of(level, Peta.config().npcDomains);
		for (DuniaSites.Site site : DuniaSites.known(player)) {
			known++;
			if (!site.dim().equals(level.dimension())) {
				continue;
			}
			CompoundTag s = site(site);
			if (site.kind().startsWith("kota/")) {
				PetaDomains.Domain guard = PetaDomains.at(domains, site.pos());
				if (guard != null) {
					s.putString("guard", guard.id());
				}
			}
			if (site.kind().startsWith("penjara/")) {
				s.putInt("seal", DuniaApi.seals().integrity(site.id()));
				s.putBoolean("broken", DuniaApi.seals().broken(site.id()));
			}
			sites.add(s);
		}
		tag.put("sites", sites);
		tag.putInt("known", known);

		// live omens within reach (everyone sees a pillar in the sky)
		ListTag live = new ListTag();
		for (DuniaSites.Site site : DuniaSites.live()) {
			if (site.distance(level.dimension(), player.blockPosition()) <= Peta.LIVE_RANGE) {
				CompoundTag s = site(site);
				s.putBoolean("live", true);
				live.add(s);
			}
		}
		for (TreasureBirth birth : DuniaApi.treasures().live(level)) {
			if (!birth.dim().equals(level.dimension())) {
				continue;
			}
			double dx = birth.pos().getX() - player.getX();
			double dz = birth.pos().getZ() - player.getZ();
			if (dx * dx + dz * dz > (double) Peta.LIVE_RANGE * Peta.LIVE_RANGE * 4.0D) {
				continue;
			}
			CompoundTag s = new CompoundTag();
			s.putLong("id", birth.id());
			s.putString("kind", "langit/harta");
			s.putInt("x", birth.pos().getX());
			s.putInt("y", birth.pos().getY());
			s.putInt("z", birth.pos().getZ());
			s.putInt("tier", birth.tier());
			s.putString("what", birth.kind());
			s.putLong("ends", birth.ends());
			s.putBoolean("live", true);
			s.putBoolean("birth", true);
			live.add(s);
		}
		tag.put("live", live);
		tag.putLong("time", level.getGameTime());

		// waypoints
		ListTag waypoints = new ListTag();
		for (CompoundTag wp : Peta.waypointsFor(player, level.dimension())) {
			waypoints.add(wp);
		}
		tag.put("waypoints", waypoints);

		// the player's ship and its voyage
		CompoundTag ship = KapalRohEntity.mapEntry(player);
		if (ship != null) {
			tag.put("ship", ship);
		}
		tag.putBoolean("captain", player.getVehicle() instanceof KapalRohEntity riding && riding.captain() == player);

		// the return point of a secret realm, the soul's body
		CompoundTag ret = dev.kultivator.data.Cultivators.get(player).extra().getCompoundOrEmpty("dunia").getCompoundOrEmpty("ret");
		if (!ret.isEmpty() && dim.equals(ret.getStringOr("dim", ""))) {
			CompoundTag r = new CompoundTag();
			r.putInt("x", (int) Math.floor(ret.getDoubleOr("x", 0.0D)));
			r.putInt("z", (int) Math.floor(ret.getDoubleOr("z", 0.0D)));
			tag.put("return", r);
		}
		PetaData.Player entry = data.peek(player.getUUID());
		if (entry != null && entry.body != null && dim.equals(entry.body.getStringOr("dim", ""))) {
			tag.put("body", entry.body.copy());
		}

		// sect territories: own, sect mates', seen
		ListTag halls = new ListTag();
		Set<UUID> owners = new LinkedHashSet<>();
		owners.add(player.getUUID());
		Sect sect = Sects.sectOf(player);
		if (sect != null) {
			for (Sect.Member member : sect.members()) {
				owners.add(member.id());
			}
		}
		if (entry != null) {
			owners.addAll(entry.seenHalls);
		}
		for (UUID owner : owners) {
			Halls.HallView hall = Halls.hallOf(owner);
			if (hall == null || !hall.dim().equals(level.dimension())) {
				continue;
			}
			CompoundTag h = new CompoundTag();
			h.putString("name", hall.name());
			h.putInt("x", hall.pos().getX());
			h.putInt("z", hall.pos().getZ());
			h.putInt("r", Math.max(16, hall.radius()));
			h.putInt("level", hall.level());
			h.putBoolean("active", hall.active());
			boolean own = owner.equals(player.getUUID());
			boolean mate = !own && sect != null && sect.isMember(owner);
			h.putBoolean("own", own);
			h.putBoolean("mate", mate);
			ServerPlayer online = server.getPlayerList().getPlayer(owner);
			h.putString("owner", online != null ? online.getName().getString() : "");
			Sect hallSect = online != null ? Sects.sectOf(online) : (mate ? sect : null);
			h.putInt("color", hallSect != null ? 0xFFFFFF & hallSect.color() : (own ? 0xFFD27A : 0xC8B890));
			halls.add(h);
			if (own || mate) {
				ListTag villages = new ListTag();
				for (VillageView village : Vassals.views(owner)) {
					CompoundTag v = new CompoundTag();
					v.putString("name", village.name());
					v.putInt("x", village.bell().getX());
					v.putInt("z", village.bell().getZ());
					v.putInt("loyalty", village.loyalty());
					v.putBoolean("threat", village.threatened());
					villages.add(v);
				}
				h.put("villages", villages);
			}
		}
		tag.put("halls", halls);

		// NPC sect domains
		tag.put("domains", PetaDomains.toTag(domains, level.registryAccess()));

		// overlays
		SkyKind sky = DuniaApi.sky().active(level);
		if (sky != null) {
			tag.putString("sky", sky.name());
		}
		ListTag weather = new ListTag();
		int step = 128;
		int bx = Math.floorDiv(player.getBlockX(), step) * step;
		int bz = Math.floorDiv(player.getBlockZ(), step) * step;
		for (int i = -5; i <= 5; i++) {
			for (int j = -5; j <= 5; j++) {
				BlockPos at = new BlockPos(bx + i * step + step / 2, player.getBlockY(), bz + j * step + step / 2);
				SpiritWeather w;
				try {
					w = DuniaApi.sky().weatherAt(level, at);
				} catch (RuntimeException exception) {
					w = SpiritWeather.CLEAR;
				}
				if (w != null && w != SpiritWeather.CLEAR) {
					CompoundTag c = new CompoundTag();
					c.putInt("x", at.getX());
					c.putInt("z", at.getZ());
					c.putInt("w", w.ordinal());
					weather.add(c);
				}
			}
		}
		tag.put("weather", weather);
		tag.putInt("weather_step", step);
		return tag;
	}

	/** Region keys of this dimension the player (and, when shared, the sect) explored, nearest first, at most 4 096. */
	private static long[] exploredRegions(ServerPlayer player, PetaData data, String dim) {
		it.unimi.dsi.fastutil.longs.LongOpenHashSet keys = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		addRegions(keys, data.peek(player.getUUID()), dim);
		if (Peta.config().shareWithSect) {
			Sect sect = Sects.sectOf(player);
			if (sect != null) {
				for (Sect.Member member : sect.members()) {
					addRegions(keys, data.peek(member.id()), dim);
				}
			}
		}
		long[] all = keys.toLongArray();
		int span = PetaData.REGION * data.cellSize;
		int prx = Math.floorDiv(player.getBlockX(), span);
		int prz = Math.floorDiv(player.getBlockZ(), span);
		Long[] boxed = new Long[all.length];
		for (int i = 0; i < all.length; i++) {
			boxed[i] = all[i];
		}
		java.util.Arrays.sort(boxed, java.util.Comparator.comparingLong(key -> {
			long dx = PetaData.regionX(key) - prx;
			long dz = PetaData.regionZ(key) - prz;
			return dx * dx + dz * dz;
		}));
		int n = Math.min(4096, boxed.length);
		long[] out = new long[n];
		for (int i = 0; i < n; i++) {
			out[i] = boxed[i];
		}
		return out;
	}

	private static void addRegions(it.unimi.dsi.fastutil.longs.LongOpenHashSet keys, PetaData.Player entry, String dim) {
		if (entry == null) {
			return;
		}
		it.unimi.dsi.fastutil.longs.Long2ObjectMap<long[]> map = entry.fog.get(dim);
		if (map != null) {
			keys.addAll(map.keySet());
		}
	}

	private static CompoundTag site(DuniaSites.Site site) {
		CompoundTag s = new CompoundTag();
		s.putLong("id", site.id());
		s.putString("kind", site.kind());
		s.putInt("x", site.pos().getX());
		s.putInt("y", site.pos().getY());
		s.putInt("z", site.pos().getZ());
		s.putInt("tier", site.tier());
		s.putLong("day", site.foundDay());
		return s;
	}

	private PetaSheet() {
	}
}
