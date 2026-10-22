package dev.kultivator.sekte.kebun;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.event.Duties;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.kebun.Kebun.Aula;
import dev.kultivator.sekte.kebun.TabelHasil.Benih;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Module W's payloads, registered from {@link Kebun#init} with {@code PayloadTypeRegistry} (the {@code aura.AuraPreviews}
 * pattern; {@code network/Payloads} and {@code Net} stay untouched):
 * <ul>
 * <li>{@link Open} (S2C): opens or refreshes the Kebun &amp; Tambang board with the whole state.</li>
 * <li>{@link World} (S2C): the beds (anchor, herb, stage) and the mine (mouth, depth, the hidden cavity cells) for the
 * client's herbs, glow, dust and pick sounds; to players within {@link Kebun#SYNC_RANGE} of the stele, every
 * {@code sinkronDetik} seconds and on every change.</li>
 * <li>{@link Fx} (S2C): one moment ({@code FX_*}).</li>
 * <li>{@link Action} (C2S): a button of the board ({@code buka}, {@code tanam}, {@code panen}, {@code cabut}, {@code ulang},
 * {@code rawat}, {@code rawat_lepas}, {@code rawat_libur}, {@code gali}, {@code gali_lepas}, {@code gali_libur},
 * {@code dalam}). Only the hall's owner acts on its own hall.</li>
 * </ul>
 */
public final class KebunPayloads {

	public static final int FX_HARVEST = 1;
	public static final int FX_PLANT = 2;
	public static final int FX_BLOOM = 3;
	public static final int FX_HAUL = 4;
	public static final int FX_CAVE_IN = 5;
	public static final int FX_INTI = 6;
	public static final int FX_DEEPEN = 7;

	public record Open(int page, CompoundTag data) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(Kultivator.id("sekte_kebun_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.page);
					ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data);
				},
				buf -> new Open(buf.readVarInt(), ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	public record World(CompoundTag data) implements CustomPacketPayload {
		public static final Type<World> TYPE = new Type<>(Kultivator.id("sekte_kebun_world"));
		public static final StreamCodec<RegistryFriendlyByteBuf, World> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data),
				buf -> new World(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<World> type() {
			return TYPE;
		}
	}

	public record Fx(int kind, double x, double y, double z, int arg) implements CustomPacketPayload {
		public static final Type<Fx> TYPE = new Type<>(Kultivator.id("sekte_kebun_fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Fx> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.kind);
					buf.writeDouble(p.x);
					buf.writeDouble(p.y);
					buf.writeDouble(p.z);
					buf.writeInt(p.arg);
				},
				buf -> new Fx(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt()));

		@Override
		public Type<Fx> type() {
			return TYPE;
		}
	}

	public record Action(String action, int a, String b) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(Kultivator.id("sekte_kebun_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeUtf(p.action == null ? "" : p.action, 32);
					buf.writeVarInt(p.a);
					buf.writeUtf(p.b == null ? "" : p.b, 64);
				},
				buf -> new Action(buf.readUtf(32), buf.readVarInt(), buf.readUtf(64)));

		@Override
		public Type<Action> type() {
			return TYPE;
		}
	}

	private static boolean registered;

	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(World.TYPE, World.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Fx.TYPE, Fx.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
		// The types always (K1, both sides); the receiver only while the module runs.
		if (!dev.kultivator.sekte.SekteContent.on(dev.kultivator.sekte.SekteIds.W)) {
			return;
		}
		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> {
			try {
				handle(context.player(), payload);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kebun: board action {} failed", payload.action(), exception);
			}
		});
	}

	static void send(@Nullable ServerPlayer player, CustomPacketPayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** A moment for every player of {@code level} within 64 blocks of {@code at}. */
	static void fx(ServerLevel level, int kind, Vec3 at, int arg) {
		Fx payload = new Fx(kind, at.x, at.y, at.z, arg);
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(at) <= 64.0D * 64.0D) {
				send(player, payload);
			}
		}
	}

	// ================================================================== the board

	/** Opens (or refreshes) the board for the owner {@code player} on {@code page}. */
	static void open(ServerPlayer player, int page) {
		Aula aula = Kebun.aula(player.getUUID());
		if (aula == null) {
			Kebun.tell(player, Component.translatable("kultivator.sekte.kebun.no_hall"), ChatFormatting.YELLOW);
			return;
		}
		send(player, new Open(page, state(aula, player)));
	}

	/** The whole board state (see the client's {@code KebunScreen}). */
	static CompoundTag state(Aula aula, ServerPlayer player) {
		UUID owner = aula.owner();
		CompoundTag out = new CompoundTag();
		int plots = Kebun.plotCount(owner);
		out.putInt("hall", Halls.level(owner));
		out.putInt("plots", plots);
		out.putInt("rest", KebunConfig.get().hariLibur);
		out.putBoolean("garden_on", KebunConfig.get().kebunAktif);
		out.putBoolean("mine_on", KebunConfig.get().tambangAktif);
		CompoundTag tenders = aula.sec().getCompoundOrEmpty("rawat");
		ListTag beds = new ListTag();
		for (int i = 0; i < plots; i++) {
			PetakHerba bed = PetakHerba.of(aula.sec(), i);
			CompoundTag b = new CompoundTag();
			Benih herb = bed.herb();
			b.putString("herb", herb == null ? "" : herb.id());
			b.putInt("stage", bed.stage());
			b.putFloat("prog", (float) bed.progress());
			b.putBoolean("auto", bed.auto());
			b.putInt("age", bed.age());
			b.putLong("left", bed.ticksLeft());
			b.putBoolean("tended", tenders.contains(String.valueOf(i / 2 + 1)));
			b.putInt("color", herb == null ? 0 : herb.color());
			if (herb != null) {
				b.putString("item", BuiltInRegistries.ITEM.getKey(herb.item()).toString());
			}
			beds.add(b);
		}
		out.put("beds", beds);
		int tenderSlots = Kebun.tenderSlots(plots);
		out.putInt("tslots", tenderSlots);
		out.put("tenders", workers(aula, tenders, player));
		ListTag seeds = new ListTag();
		for (Benih seed : Benih.all()) {
			CompoundTag s = new CompoundTag();
			s.putString("id", seed.id());
			s.putString("item", BuiltInRegistries.ITEM.getKey(seed.item()).toString());
			s.putInt("mutu", seed.mutu());
			s.putInt("color", seed.color());
			int have = Duties.lumbungCount(owner, seed::matches);
			int hand = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (seed.matches(stack)) {
					hand += stack.getCount();
				}
			}
			s.putInt("store", have);
			s.putInt("hand", hand);
			seeds.add(s);
		}
		out.put("seeds", seeds);
		// the mine
		CompoundTag mine = new CompoundTag();
		boolean built = TambangRoh.built(owner);
		int level = TambangRoh.level(owner);
		mine.putBoolean("built", built);
		mine.putInt("level", level);
		mine.putInt("slots", TabelHasil.slots(level));
		mine.putInt("danger", level >= TambangRoh.DANGER_LEVEL ? KebunConfig.get().runtuhPersen : 0);
		mine.put("miners", workers(aula, TambangRoh.miners(aula.sec()), player));
		if (built && level < TambangRoh.MAX_LEVEL) {
			int[] cost = TambangRoh.cost(level + 1);
			CompoundTag c = new CompoundTag();
			c.putInt("pk", cost[0]);
			c.putInt("kayu", cost[1]);
			c.putInt("batu", cost[2]);
			c.putBoolean("ok_pk", TambangRoh.poolHas(owner, cost[0]));
			c.putLong("have_kayu", TambangRoh.units(owner, Bahan.KAYU));
			c.putLong("have_batu", TambangRoh.units(owner, Bahan.BATU));
			mine.put("cost", c);
		}
		ListTag table = new ListTag();
		for (Object[] line : TabelHasil.preview(Math.max(1, level))) {
			ItemStack stack = (ItemStack) line[0];
			CompoundTag t = new CompoundTag();
			t.putString("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
			t.putInt("count", stack.getCount());
			t.putInt("pct", (Integer) line[1]);
			table.add(t);
		}
		mine.put("table", table);
		out.put("mine", mine);
		// candidates near the stele
		ListTag cands = new ListTag();
		for (DiscipleEntity disciple : Kebun.nearby(aula)) {
			DiscipleData data = disciple.data();
			CompoundTag c = new CompoundTag();
			c.putString("soul", disciple.soulId().toString());
			c.store("name", ComponentSerialization.CODEC, disciple.getDisplayName());
			CompoundTag kerja = data.extra().getCompoundOrEmpty(SekteIds.EXTRA_W);
			String job = kerja.getStringOr("job", "");
			if (job.isEmpty() && Duties.dutyOf(data) != null) {
				job = "tugas";
			}
			c.putString("job", job);
			c.putInt("streak", kerja.getIntOr("streak", 0));
			c.putBoolean("green", Kebun.greenRoot(data));
			c.putBoolean("stone", Kebun.stoneRoot(data));
			c.putBoolean("defiant", SekteApi.hati().defiant(data));
			c.putBoolean("resting", kerja.getLongOr("rest_until", 0L) > aula.now());
			c.putBoolean("busy", data.breakingThrough());
			cands.add(c);
		}
		out.put("cands", cands);
		ListTag log = new ListTag();
		for (int i = aula.sec().getListOrEmpty("log").size() - 1; i >= 0; i--) {
			CompoundTag entry = aula.sec().getListOrEmpty("log").getCompoundOrEmpty(i);
			CompoundTag line = new CompoundTag();
			line.putLong("day", entry.getLongOr("day", 0L));
			entry.read("text", ComponentSerialization.CODEC).ifPresent(text -> line.store("text", ComponentSerialization.CODEC, text));
			log.add(line);
		}
		out.put("log", log);
		out.put("stat", Pertumbuhan.stats(aula.sec()).copy());
		return out;
	}

	/** The workers of a slot compound for the board: slot, soul, name, streak, present. */
	private static ListTag workers(Aula aula, CompoundTag slots, ServerPlayer player) {
		ListTag list = new ListTag();
		for (String key : slots.keySet()) {
			CompoundTag entry = slots.getCompoundOrEmpty(key);
			UUID soul = Kebun.uuid(entry.getStringOr("soul", ""));
			if (soul == null) {
				continue;
			}
			CompoundTag w = new CompoundTag();
			int slot;
			try {
				slot = Integer.parseInt(key);
			} catch (NumberFormatException exception) {
				continue;
			}
			w.putInt("slot", slot);
			w.putString("soul", soul.toString());
			w.store("name", ComponentSerialization.CODEC, Kebun.nameOf(aula.owner(), player, soul));
			DiscipleEntity disciple = Kebun.bySoul(aula.owner(), soul);
			w.putBoolean("here", disciple != null);
			w.putInt("streak", disciple == null ? 0 : disciple.data().extra().getCompoundOrEmpty(SekteIds.EXTRA_W).getIntOr("streak", 0));
			w.putInt("today", entry.getIntOr("today", 0));
			w.putInt("total", entry.getIntOr("total", 0));
			list.add(w);
		}
		return list;
	}

	// ================================================================== the world

	/** The world picture of the owner's beds and mine, to the players near the stele. */
	static void syncWorld(UUID owner) {
		Aula aula = Kebun.aula(owner);
		if (aula == null) {
			return;
		}
		Vec3 stele = Vec3.atBottomCenterOf(aula.hall().pos());
		List<ServerPlayer> near = new java.util.ArrayList<>();
		for (ServerPlayer player : aula.level().players()) {
			if (player.position().distanceToSqr(stele) <= Kebun.SYNC_RANGE * Kebun.SYNC_RANGE) {
				near.add(player);
			}
		}
		if (near.isEmpty()) {
			return;
		}
		CompoundTag data = new CompoundTag();
		data.putLong("hall", aula.hall().pos().asLong());
		ListTag beds = new ListTag();
		int plots = Kebun.plotCount(owner);
		for (int i = 0; i < plots; i++) {
			BlockPos at = EngineApi.get().anchor(owner, KebunKomponen.ANCHOR_PLOT + (i + 1));
			if (at == null) {
				continue;
			}
			PetakHerba bed = PetakHerba.of(aula.sec(), i);
			Benih herb = bed.herb();
			CompoundTag b = new CompoundTag();
			b.putLong("p", at.asLong());
			b.putInt("h", herb == null ? -1 : herb.ordinal());
			b.putInt("s", bed.stage());
			b.putFloat("f", (float) bed.progress());
			beds.add(b);
		}
		data.put("beds", beds);
		BlockPos mouth = TambangRoh.mouth(owner);
		if (mouth != null && TambangRoh.built(owner)) {
			CompoundTag mine = new CompoundTag();
			mine.putLong("p", mouth.asLong());
			mine.putInt("l", TambangRoh.level(owner));
			mine.putInt("n", TambangRoh.miners(aula.sec()).size());
			long[] inside = new long[KebunKomponen.INSIDE_SPOTS];
			for (int i = 0; i < inside.length; i++) {
				BlockPos spot = EngineApi.get().anchor(owner, KebunKomponen.ANCHOR_INSIDE + (i + 1));
				inside[i] = spot == null ? mouth.asLong() : spot.asLong();
			}
			mine.putLongArray("in", inside);
			data.put("mine", mine);
		}
		World payload = new World(data);
		for (ServerPlayer player : near) {
			send(player, payload);
		}
	}

	// ================================================================== actions

	private static void handle(ServerPlayer player, Action action) {
		UUID owner = player.getUUID();
		Kebun.compute(owner);
		Aula aula = Kebun.aula(owner);
		if (aula == null) {
			Kebun.tell(player, Component.translatable("kultivator.sekte.kebun.no_hall"), ChatFormatting.YELLOW);
			return;
		}
		int page = Kebun.PAGE_GARDEN;
		Component problem = null;
		switch (action.action()) {
			case "buka" -> page = Math.max(0, Math.min(1, action.a()));
			case "tanam" -> {
				Benih seed = Benih.byId(action.b());
				problem = seed == null ? Component.translatable("kultivator.sekte.kebun.bed.no_seed", action.b())
						: Pertumbuhan.plant(aula, action.a(), seed, player);
			}
			case "panen" -> {
				if (action.a() < 0) {
					int done = 0;
					for (int i = 0; i < Kebun.plotCount(owner); i++) {
						if (PetakHerba.of(aula.sec(), i).ripe()) {
							Pertumbuhan.harvest(aula, i, null, player);
							done++;
						}
					}
					if (done == 0) {
						problem = Component.translatable("kultivator.sekte.kebun.bed.none_ripe");
					}
				} else if (PetakHerba.of(aula.sec(), action.a()).ripe()) {
					Pertumbuhan.harvest(aula, action.a(), null, player);
				} else {
					problem = Component.translatable("kultivator.sekte.kebun.bed.not_ripe", action.a() + 1);
				}
			}
			case "cabut" -> problem = Pertumbuhan.uproot(aula, action.a(), player);
			case "ulang" -> {
				if (action.a() >= 0 && action.a() < Kebun.plotCount(owner)) {
					PetakHerba bed = PetakHerba.of(aula.sec(), action.a());
					bed.setAuto(!bed.auto());
					Halls.setDirty();
				}
			}
			case "rawat" -> {
				DiscipleEntity disciple = Kebun.bySoul(owner, Kebun.uuid(action.b()));
				problem = disciple == null ? Component.translatable("kultivator.sekte.kebun.job.gone")
						: Pertumbuhan.assign(aula, action.a(), disciple);
			}
			case "rawat_lepas", "rawat_libur" -> {
				Pertumbuhan.release(aula, action.a(), "rawat_libur".equals(action.action()));
				KebunPayloads.syncWorld(owner);
			}
			case "gali" -> {
				page = Kebun.PAGE_MINE;
				DiscipleEntity disciple = Kebun.bySoul(owner, Kebun.uuid(action.b()));
				problem = disciple == null ? Component.translatable("kultivator.sekte.kebun.job.gone")
						: GiliranTambang.assign(aula, action.a(), disciple);
			}
			case "gali_lepas", "gali_libur" -> {
				page = Kebun.PAGE_MINE;
				GiliranTambang.release(aula, action.a(), "gali_libur".equals(action.action()), false);
			}
			case "dalam" -> {
				page = Kebun.PAGE_MINE;
				problem = TambangRoh.deepen(aula, player);
			}
			default -> {
				return;
			}
		}
		if (problem != null) {
			Kebun.tell(player, problem, ChatFormatting.YELLOW);
		}
		if (action.action().startsWith("rawat") || action.action().equals("tanam") || action.action().equals("panen")
				|| action.action().equals("cabut") || action.action().equals("ulang")) {
			page = Kebun.PAGE_GARDEN;
		}
		send(player, new Open(page, state(aula, player)));
	}

	private KebunPayloads() {
	}
}
