package dev.kultivator.cultivation;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.formation.ProtectiveFormations;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.kehancuran.ProtectedZones;
import dev.kultivator.talisman.TalismanFxIds;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gua Kediaman / Cave Abode (DESIGN 19.3.5): one meditation seat per player, set while meditating on a qi formation
 * (tier 1+) inside an active protective formation; saved in {@code extra.kultivasi.abode {dim, x, y, z}} (the seat block
 * the player sits on). Within {@link #BONUS_RANGE} blocks of a protected seat: vein drain ×0.5, enlightenment +0.01 per
 * minute, seclusion survives a short break, and it is the Return Talisman's first target. While the seat lost its
 * formation or the protective formation is unpowered the bonuses pause. Mode Kehancuran never carves within
 * {@link #ZONE_RADIUS} blocks of the seat (a {@code ProtectedZones} provider).
 * <p>
 * Sumber: RMJ ch. 391 (cave residences on Sage Mountain), ch. 520 (a concealment restriction around his new cave
 * residence), ch. 803 (a spell formation outside it); RI ch. 216 (hidden restrictions before cultivating).
 */
public final class CaveAbode {

	/** The abode bonuses act within this many blocks of the seat. */
	public static final double BONUS_RANGE = 6.0D;
	/** Mode Kehancuran never carves within this many blocks of the seat. */
	public static final double ZONE_RADIUS = 16.0D;
	/** The qi formation tier the seat needs. */
	public static final int MIN_FORMATION_TIER = 1;
	private static final String KEY = "abode";
	private static final int JADE = 0x8FE3C8;

	/** Every abode seat of a dimension, saved with the level so the zone holds while the owner is offline. */
	public static final SavedDataType<Seats> TYPE = new SavedDataType<>(Kultivator.id("cave_abodes"), Seats::new, Seats.CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
	/** Per-dimension cache of the saved seats (cleared on server stop). */
	private static final Map<ResourceKey<Level>, Seats> ZONES = new HashMap<>();

	/** Called by {@link ImmortalPerks#init()}. */
	public static void init() {
		ProtectedZones.addZoneProvider(CaveAbode::protects);
		ServerPlayerEvents.JOIN.register(CaveAbode::remember);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ZONES.clear());
	}

	// ================================================================== reading

	/** The player's abode seat, null when none is set. */
	public static @Nullable GlobalPos of(ServerPlayer player) {
		return read(Cultivators.get(player));
	}

	/** The abode seat saved in {@code data}, null when none. */
	public static @Nullable GlobalPos read(CultivationData data) {
		CompoundTag tag = data.extra().getCompoundOrEmpty(TechniquePower.PLAYER_EXTRA).getCompoundOrEmpty(KEY);
		String dim = tag.getStringOr("dim", "");
		if (dim.isEmpty()) {
			return null;
		}
		Identifier id = Identifier.tryParse(dim);
		if (id == null) {
			return null;
		}
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
		return GlobalPos.of(key, new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0)));
	}

	/** True when the player is within {@code range} blocks of their own abode seat in its dimension. */
	public static boolean near(ServerPlayer player, double range) {
		GlobalPos abode = of(player);
		if (abode == null || !abode.dimension().equals(player.level().dimension())) {
			return false;
		}
		return player.position().distanceToSqr(top(abode.pos())) <= range * range;
	}

	/** 1 when the abode bonuses act for the player now, −1 near the seat but unprotected, 0 not near an abode. */
	public static int state(ServerPlayer player) {
		GlobalPos abode = of(player);
		if (abode == null || !abode.dimension().equals(player.level().dimension())
				|| player.position().distanceToSqr(top(abode.pos())) > BONUS_RANGE * BONUS_RANGE) {
			return 0;
		}
		return guarded(player.level(), abode.pos()) ? 1 : -1;
	}

	/** True when the abode bonuses act for the player now (near a protected seat). */
	public static boolean bonusActive(ServerPlayer player) {
		return state(player) > 0;
	}

	/** The seat still has its qi formation and lies inside an active protective formation (loaded chunks only). */
	public static boolean guarded(ServerLevel level, BlockPos seat) {
		return level.isLoaded(seat) && QiFormations.tier(level, seat) >= MIN_FORMATION_TIER
				&& ProtectiveFormations.insideActiveFormation(level, top(seat));
	}

	/** Where a player stands on the seat. */
	public static Vec3 top(BlockPos seat) {
		return Vec3.atBottomCenterOf(seat.above());
	}

	// ================================================================== setting

	/** {@code gua tetapkan} / Action {@code abode:set}: checks the formations and saves the seat under the player. */
	public static boolean set(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		ServerLevel level = player.level();
		BlockPos seat = player.blockPosition().below();
		if (dev.kultivator.alam.Alam.blocked(player)) {
			// Phase 8 (DESIGN 25.2 item 12).
			Msg.chat(player, dev.kultivator.alam.Alam.refusal().withStyle(net.minecraft.ChatFormatting.RED));
			return false;
		}
		if (dev.kultivator.purba.Purba.inCalamity(player) || dev.kultivator.abadi.Abadi.inAscension(player)) {
			// Phase 9 (DESIGN 26.14 item 13).
			Msg.chat(player, net.minecraft.network.chat.Component.translatable("kultivator.spine9.refuse.busy").withStyle(net.minecraft.ChatFormatting.RED));
			return false;
		}
		if (!data.meditating()) {
			return refuse(player, "kultivator.kultivasi.abode.need_meditation");
		}
		if (QiFormations.tier(level, seat) < MIN_FORMATION_TIER) {
			return refuse(player, "kultivator.kultivasi.abode.need_formation");
		}
		if (!ProtectiveFormations.insideActiveFormation(level, player.position())) {
			return refuse(player, "kultivator.kultivasi.abode.need_protection");
		}
		write(player, data, level, seat);
		return true;
	}

	/** {@code uji gua}: saves the seat under the player without the formation checks. */
	public static void setUnchecked(ServerPlayer player) {
		write(player, Cultivators.get(player), player.level(), player.blockPosition().below());
	}

	private static void write(ServerPlayer player, CultivationData data, ServerLevel level, BlockPos seat) {
		CompoundTag tag = new CompoundTag();
		tag.putString("dim", level.dimension().identifier().toString());
		tag.putInt("x", seat.getX());
		tag.putInt("y", seat.getY());
		tag.putInt("z", seat.getZ());
		section(data).put(KEY, tag);
		Cultivators.dirty(player);
		zone(player, GlobalPos.of(level.dimension(), seat.immutable()));
		Vec3 at = top(seat);
		Fx.send(level, at, FxType.TALISMAN, TalismanFxIds.ABODE, 0, (float) ZONE_RADIUS);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 0.8F, 1.3F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
		Msg.chat(player, Component.translatable("kultivator.kultivasi.abode.set", seat.getX(), seat.getY(), seat.getZ(),
				(int) BONUS_RANGE, (int) ZONE_RADIUS).withColor(JADE));
		Advancements.grant(player, "cave_abode");
	}

	/** {@code gua hapus} / Action {@code abode:clear}. */
	public static boolean clear(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		CompoundTag section = section(data);
		if (!section.contains(KEY)) {
			return refuse(player, "kultivator.kultivasi.abode.none");
		}
		section.remove(KEY);
		Cultivators.dirty(player);
		zone(player, null);
		Msg.chat(player, Component.translatable("kultivator.kultivasi.abode.cleared").withColor(0xA8B5B0));
		return true;
	}

	/** {@code gua lihat}: where the abode is, whether it is protected and how far away. */
	public static void show(ServerPlayer player) {
		GlobalPos abode = of(player);
		if (abode == null) {
			Msg.chat(player, Component.translatable("kultivator.kultivasi.abode.none").withColor(0xA8B5B0));
			return;
		}
		BlockPos seat = abode.pos();
		Component dimension = Component.literal(abode.dimension().identifier().getPath());
		if (!abode.dimension().equals(player.level().dimension())) {
			Msg.chat(player, Component.translatable("kultivator.kultivasi.abode.show_far", dimension, seat.getX(), seat.getY(), seat.getZ())
					.withColor(JADE));
			return;
		}
		boolean guarded = guarded(player.level(), seat);
		int distance = (int) Math.round(Math.sqrt(player.position().distanceToSqr(top(seat))));
		Msg.chat(player, Component.translatable("kultivator.kultivasi.abode.show", dimension, seat.getX(), seat.getY(), seat.getZ(), distance,
				Component.translatable(guarded ? "kultivator.kultivasi.abode.guarded" : "kultivator.kultivasi.abode.unguarded")
						.withColor(guarded ? 0x7CFFB2 : 0xFF9A7A)).withColor(JADE));
	}

	// ================================================================== Mode Kehancuran zone

	/** Brings the saved seats in line with the player's own record (older saves, moved seats). */
	private static void remember(ServerPlayer player) {
		GlobalPos abode = of(player);
		if (abode != null) {
			ServerLevel level = player.level().getServer().getLevel(abode.dimension());
			if (level != null && abode.pos().equals(seats(level).get(player.getUUID()))) {
				return;
			}
		}
		zone(player, abode);
	}

	private static void zone(ServerPlayer owner, @Nullable GlobalPos abode) {
		UUID id = owner.getUUID();
		for (ServerLevel level : owner.level().getServer().getAllLevels()) {
			seats(level).remove(id);
		}
		if (abode != null) {
			ServerLevel level = owner.level().getServer().getLevel(abode.dimension());
			if (level != null) {
				seats(level).put(id, abode.pos());
			}
		}
	}

	private static Seats seats(ServerLevel level) {
		return ZONES.computeIfAbsent(level.dimension(), key -> level.getDataStorage().computeIfAbsent(TYPE));
	}

	/** The {@code ProtectedZones} provider: within {@link #ZONE_RADIUS} of any saved abode seat of this dimension. */
	private static boolean protects(ServerLevel level, BlockPos pos) {
		Seats seats = seats(level);
		if (seats.seats.isEmpty()) {
			return false;
		}
		double limit = ZONE_RADIUS * ZONE_RADIUS;
		for (BlockPos seat : seats.seats.values()) {
			if (seat.distSqr(pos) <= limit) {
				return true;
			}
		}
		return false;
	}

	/** The abode seats of one dimension: owner UUID to seat block. */
	public static final class Seats extends SavedData {

		public static final Codec<Seats> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("seats").codec()
				.xmap(Seats::fromMap, Seats::toMap);

		private final Map<UUID, BlockPos> seats = new HashMap<>();

		public Seats() {
		}

		private static Seats fromMap(Map<String, Long> map) {
			Seats data = new Seats();
			map.forEach((owner, pos) -> {
				try {
					data.seats.put(UUID.fromString(owner), BlockPos.of(pos));
				} catch (IllegalArgumentException ignored) {
					// a damaged entry is dropped
				}
			});
			return data;
		}

		private Map<String, Long> toMap() {
			Map<String, Long> map = new HashMap<>();
			seats.forEach((owner, pos) -> map.put(owner.toString(), pos.asLong()));
			return map;
		}

		@Nullable BlockPos get(UUID owner) {
			return seats.get(owner);
		}

		void put(UUID owner, BlockPos seat) {
			if (!seat.equals(seats.put(owner, seat.immutable()))) {
				setDirty();
			}
		}

		void remove(UUID owner) {
			if (seats.remove(owner) != null) {
				setDirty();
			}
		}
	}

	// ================================================================== helpers

	/** The attached {@code extra.kultivasi} compound. */
	static CompoundTag section(CultivationData data) {
		CompoundTag extra = data.extra();
		CompoundTag tag = extra.getCompoundOrEmpty(TechniquePower.PLAYER_EXTRA);
		if (!extra.contains(TechniquePower.PLAYER_EXTRA)) {
			extra.put(TechniquePower.PLAYER_EXTRA, tag);
		}
		return tag;
	}

	private static boolean refuse(ServerPlayer player, String key) {
		Msg.chat(player, Component.translatable(key).withColor(0xFF9A7A));
		return false;
	}

	private CaveAbode() {
	}
}
