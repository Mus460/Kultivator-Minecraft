package dev.kultivator.formasi.integrate;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Dao;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.formasi.Formasi;
import dev.kultivator.formasi.FormasiFxIds;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.InfoPart;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.core.CoreStore;
import dev.kultivator.formasi.core.CoreTicker;
import dev.kultivator.formasi.core.OwnerStore;
import dev.kultivator.formasi.effect.a.ShieldEffect;
import dev.kultivator.formasi.fuel.Kas;
import dev.kultivator.formasi.fuel.Upkeep;
import dev.kultivator.formasi.keeper.KeeperOrder;
import dev.kultivator.formasi.keeper.Keepers;
import dev.kultivator.formasi.own.Limits;
import dev.kultivator.formasi.own.Ownership;
import dev.kultivator.formasi.own.Slots;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.stack.FormationField;
import dev.kultivator.formasi.stack.Power;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.formation.Formation;
import dev.kultivator.formation.FormationData;
import dev.kultivator.formation.ProtectiveFormations;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.SectData;
import dev.kultivator.sect.Sects;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.FormasiAnchor;
import dev.kultivator.sect.hall.GrandFormation;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The hall-scroll side of 7c (DESIGN 24.7.1, 24.12): the {@code formasi} part of {@code HallInfo} (≤ 8 KB), the
 * {@code Actions.FORMASI} text ops of module A (each answers with a fresh {@code HallInfo}), and the drain exemption of
 * the Agung's lodestone while L1 runs.
 */
public final class HallBridge {

	public static void init() {
		Halls.addInfoProvider((viewer, owner, info) -> {
			try {
				info.put("formasi", compose(viewer, owner));
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Formasi hall info failed", exception);
			}
		});
		Formasi.addActionHandler("toggle", (player, text) -> withCore(player, text, true, (record, level) -> {
			record.on = !record.on;
			CoreTicker.refreshState(level, record);
		}));
		Formasi.addActionHandler("draw", (player, text) -> withCore(player, text, true, (record, level) -> {
			String[] parts = text.split(":");
			record.draw = parts.length > 2 ? "1".equals(parts[2]) : !record.draw;
		}));
		Formasi.addActionHandler("fill", (player, text) -> withCore(player, text, true, (record, level) -> fill(player, record, Upkeep.DRAW_HOURS)));
		Formasi.addActionHandler("fillall", (player, text) -> {
			UUID owner = player.getUUID();
			long moved = 0L;
			for (CoreStore store : CoreTicker.stores()) {
				for (CoreRecord record : store.all()) {
					FormationSpec spec = FormationRegistry.byId(record.spec);
					if (spec != null && !record.virtual && record.owner.equals(owner)) {
						long got = CoreTicker.draw(record, spec, Upkeep.DRAW_HOURS);
						if (got > 0L) {
							moved += got;
							store.setDirty();
						}
					}
				}
			}
			Msg.chat(player, Component.translatable("kultivator.formasi.filled_all", moved / 100L).withStyle(ChatFormatting.GREEN));
			refresh(player);
		});
		Formasi.addActionHandler("deposit", (player, text) -> {
			UUID owner = Halls.viewedOwner(player);
			if (!owner.equals(player.getUUID()) && !Ownership.mates(player.level().getServer(), owner, player.getUUID())) {
				return;
			}
			int daya = Kas.depositInventory(player, owner, true);
			Msg.chat(player, Component.translatable(daya > 0 ? "kultivator.formasi.deposited" : "kultivator.formasi.deposit_none", daya)
					.withStyle(daya > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			refresh(player);
		});
		Formasi.addActionHandler("mark", (player, text) -> withCore(player, text, false, (record, level) -> {
			Vec3 at = Vec3.atCenterOf(record.eye);
			Fx.sendTo(player, at, at, FxType.FORMASI, FormasiFxIds.MARK_PILLAR, 0, 10.0F);
		}));
		Formasi.addActionHandler("keeper", (player, text) -> withCore(player, text, true, (record, level) -> {
			String[] parts = text.split(":");
			if (parts.length < 3) {
				return;
			}
			Entity entity = level.getEntity(parseInt(parts[2]));
			KeeperOrder order = parts.length > 3 ? KeeperOrder.byId(parts[3]) : KeeperOrder.JAGA;
			if (entity instanceof DiscipleEntity disciple) {
				Component why = Keepers.assign(player, level, record, disciple, order == null ? KeeperOrder.JAGA : order, false);
				Msg.chat(player, why != null ? why.copy().withStyle(ChatFormatting.YELLOW)
						: Component.translatable("kultivator.formasi.keeper.assigned", disciple.getName()).withStyle(ChatFormatting.GREEN));
			}
		}));
		Formasi.addActionHandler("order", (player, text) -> withCore(player, text, true, (record, level) -> {
			String[] parts = text.split(":");
			if (parts.length < 4) {
				return;
			}
			KeeperOrder order = KeeperOrder.byId(parts[3]);
			UUID soul = parseUuid(parts[2]);
			CoreRecord.Keeper keeper = soul == null ? null : record.keeper(soul);
			if (keeper == null || order == null) {
				return;
			}
			keeper.order = order;
			for (DiscipleEntity disciple : Disciples.loaded(record.owner)) {
				if (disciple.soulId().equals(soul)) {
					disciple.data().extraSection(Keepers.EXTRA).putString("order", order.id());
				}
			}
		}));
		Formasi.addActionHandler("release", (player, text) -> withCore(player, text, true, (record, level) -> {
			String[] parts = text.split(":");
			UUID soul = parts.length > 2 ? parseUuid(parts[2]) : null;
			if (soul == null) {
				return;
			}
			boolean done = false;
			for (DiscipleEntity disciple : Disciples.loaded(record.owner)) {
				if (disciple.soulId().equals(soul)) {
					Keepers.release(disciple);
					done = true;
				}
			}
			if (!done) {
				record.keepers.removeIf(keeper -> keeper.soul.equals(soul));
			}
		}));
		Formasi.addActionHandler("target", (player, text) -> {
			String[] parts = text.split(":");
			if (parts.length < 5 || !(player.level() instanceof ServerLevel level)) {
				return;
			}
			BlockPos pos = new BlockPos(parseInt(parts[1]), parseInt(parts[2]), parseInt(parts[3]));
			Formasi.setTarget(player, level, pos, parts[4]);
			refresh(player);
		});
		ProtectiveFormations.addDrainExemption(HallBridge::shieldRunning);
	}

	/** True while an L1 of the formation's owner runs in the formation's dimension (the lodestone keeps its power). */
	private static boolean shieldRunning(Formation formation) {
		MinecraftServer server = CoreTicker.server();
		if (server == null) {
			return false;
		}
		for (CoreStore store : CoreTicker.stores()) {
			for (CoreRecord record : store.all()) {
				if (!record.virtual && record.state.running() && "five_layer_shield".equals(record.spec) && record.owner.equals(formation.owner())) {
					return true;
				}
			}
		}
		return false;
	}

	@FunctionalInterface
	private interface CoreAction {
		void run(CoreRecord record, ServerLevel level);
	}

	/** Runs {@code action} on the core named by {@code op:<id>...} when the player may (owner for edits, members to view). */
	private static void withCore(ServerPlayer player, String text, boolean edit, CoreAction action) {
		String[] parts = text.split(":");
		if (parts.length < 2) {
			return;
		}
		int id = parseInt(parts[1]);
		UUID owner = Halls.viewedOwner(player);
		for (CoreStore store : CoreTicker.stores()) {
			CoreRecord record = store.byId(id);
			if (record == null || !record.owner.equals(owner)) {
				continue;
			}
			ServerLevel level = CoreTicker.level(record.dim);
			if (level == null) {
				return;
			}
			boolean member = Ownership.memberUuid(level.getServer(), record.owner, player.getUUID());
			if (edit ? !record.owner.equals(player.getUUID()) : !member) {
				Msg.chat(player, Component.translatable("kultivator.formasi.not_owner").withStyle(ChatFormatting.RED));
				return;
			}
			action.run(record, level);
			store.setDirty();
			refresh(player);
			return;
		}
	}

	private static void fill(ServerPlayer player, CoreRecord record, double hours) {
		FormationSpec spec = FormationRegistry.byId(record.spec);
		if (spec == null || record.virtual) {
			return;
		}
		long got = CoreTicker.draw(record, spec, hours);
		Msg.chat(player, Component.translatable(got > 0L ? "kultivator.formasi.filled" : "kultivator.formasi.fill_none", spec.name(), got / 100L)
				.withStyle(got > 0L ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		ServerLevel level = CoreTicker.level(record.dim);
		if (level != null) {
			CoreTicker.refreshState(level, record);
		}
	}

	private static void refresh(ServerPlayer player) {
		Halls.sendInfo(player, Halls.viewedOwner(player));
	}

	private static int parseInt(String text) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException exception) {
			return Integer.MIN_VALUE;
		}
	}

	private static @Nullable UUID parseUuid(String text) {
		try {
			return UUID.fromString(text.trim());
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	// ================================================================== HallInfo part

	/** The {@code formasi} part of 24.12 for {@code viewer} looking at {@code owner}'s hall. */
	public static CompoundTag compose(ServerPlayer viewer, UUID owner) {
		MinecraftServer server = viewer.level().getServer();
		CompoundTag out = new CompoundTag();
		CompoundTag kas = new CompoundTag();
		kas.putLong("a", Kas.amethyst(owner) / 100L);
		kas.putLong("s", Kas.stone(owner) / 100L);
		kas.putInt("best", Kas.bestGrade(owner));
		out.put("kas", kas);
		int hallLevel = CoreTicker.hallLevel(owner);
		out.putInt("hall", hallLevel);

		ListTag cores = new ListTag();
		int active = 0;
		int used = 0;
		Map<String, int[]> families = new HashMap<>();
		for (CoreStore store : CoreTicker.stores()) {
			for (CoreRecord record : store.all()) {
				if (!record.owner.equals(owner)) {
					continue;
				}
				FormationSpec spec = FormationRegistry.byId(record.spec);
				if (!record.virtual) {
					if (record.state.running() && record.kind == Ownership.KIND_SECT) {
						active++;
					}
					if (spec != null && !Limits.transport(spec) && record.kind == Ownership.KIND_SECT) {
						used++;
					}
					if (spec != null) {
						int[] count = families.computeIfAbsent(spec.family().name(), key -> new int[]{0, Limits.groupMax(spec, hallLevel)});
						count[0]++;
					}
				}
				if (cores.size() < 24) {
					cores.add(coreTag(server, record, spec));
				}
			}
		}
		out.put("cores", cores);
		CompoundTag slots = new CompoundTag();
		slots.putInt("active", active);
		slots.putInt("max", Slots.active(hallLevel));
		out.put("slots", slots);
		CompoundTag limits = new CompoundTag();
		limits.putInt("cores", Limits.registered(hallLevel));
		limits.putInt("used", used);
		CompoundTag fam = new CompoundTag();
		for (Map.Entry<String, int[]> entry : families.entrySet()) {
			ListTag pair = new ListTag();
			pair.add(net.minecraft.nbt.IntTag.valueOf(entry.getValue()[0]));
			pair.add(net.minecraft.nbt.IntTag.valueOf(entry.getValue()[1]));
			fam.put(entry.getKey(), pair);
		}
		limits.put("fam", fam);
		out.put("lim", limits);
		CompoundTag can = new CompoundTag();
		can.putBoolean("edit", viewer.getUUID().equals(owner));
		can.putBoolean("deposit", Ownership.memberUuid(server, owner, viewer.getUUID()));
		out.put("can", can);
		out.put("legacy", legacy(server, owner));
		ListTag anchors = new ListTag();
		for (FormasiAnchor anchor : EngineApi.get().formasiAnchors(owner)) {
			CompoundTag tag = new CompoundTag();
			tag.putString("id", anchor.id());
			tag.putInt("x", anchor.centre().getX());
			tag.putInt("y", anchor.centre().getY());
			tag.putInt("z", anchor.centre().getZ());
			tag.putInt("r", anchor.radius());
			tag.putInt("tier", anchor.tier());
			anchors.add(tag);
		}
		out.put("anchors", anchors);
		out.put("field", field(viewer));
		OwnerStore store = OwnerStore.get(server);
		out.put("log", store.has(owner) ? store.entry(owner).log.copy() : new ListTag());
		for (InfoPart part : Formasi.infoParts()) {
			try {
				part.write(viewer, owner, out);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Formasi info part failed", exception);
			}
		}
		return out;
	}

	private static CompoundTag coreTag(MinecraftServer server, CoreRecord record, @Nullable FormationSpec spec) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("id", record.id);
		tag.putString("spec", record.spec);
		tag.putInt("x", record.eye.getX());
		tag.putInt("y", record.eye.getY());
		tag.putInt("z", record.eye.getZ());
		tag.putInt("rot", record.rot);
		tag.putInt("st", record.state.ordinal());
		if (record.reason != null) {
			tag.store("why", ComponentSerialization.CODEC, record.reason);
		}
		tag.putBoolean("idle", record.idle);
		tag.putBoolean("crack", record.cracked);
		tag.putBoolean("on", record.on);
		tag.putBoolean("draw", record.draw);
		tag.putBoolean("uji", record.virtual);
		tag.putLong("daya", record.daya / 100L);
		if (spec != null) {
			tag.putLong("cap", Upkeep.capCenti(spec) / 100L);
			tag.putFloat("upk", (float) (record.rate * Upkeep.TICKS_PER_HOUR / 100.0D));
			double hours = Upkeep.hoursLeft(record.daya, record.rate);
			tag.putFloat("hrs", Double.isInfinite(hours) ? -1.0F : (float) hours);
			tag.putFloat("vein", (float) record.vein);
			tag.putFloat("keepf", (float) record.keeperUpkeep);
			tag.putFloat("r", (float) record.radius);
		}
		tag.putInt("best", record.best);
		tag.putFloat("pow", (float) record.power);
		CompoundTag parts = new CompoundTag();
		parts.putFloat("q", (float) Power.q(record.best));
		parts.putFloat("k", (float) Power.k(record));
		parts.putFloat("f", (float) Power.f(record));
		parts.putFloat("a", spec == null || spec.family().support() ? 0.0F : (float) Power.a(record, CoreTicker.view(record)));
		tag.put("pk", parts);
		tag.putInt("miss", record.missing);
		if (record.flaggedBy >= 0) {
			tag.putInt("flagged", record.flaggedBy);
		}
		ListTag keep = new ListTag();
		Map<UUID, DiscipleEntity> loaded = new HashMap<>();
		for (DiscipleEntity disciple : Disciples.loaded(record.owner)) {
			loaded.put(disciple.soulId(), disciple);
		}
		for (CoreRecord.Keeper keeper : record.keepers) {
			CompoundTag entry = new CompoundTag();
			entry.store("u", UUIDUtil.CODEC, keeper.soul);
			entry.putString("order", keeper.order.id());
			DiscipleEntity disciple = loaded.get(keeper.soul);
			if (disciple != null) {
				DiscipleData data = disciple.data();
				entry.putInt("e", disciple.getId());
				entry.putString("n", disciple.getName().getString());
				entry.putInt("realm", data.realm().ordinal());
				entry.putInt("rank", SectRanks.rank(data));
				Dao dao = data.dao();
				entry.putInt("dao", dao == null ? -1 : dao.ordinal());
			}
			keep.add(entry);
		}
		tag.put("keep", keep);
		if (!record.ext.isEmpty()) {
			tag.put("ext", record.ext.copy());
		}
		return tag;
	}

	private static ListTag legacy(MinecraftServer server, UUID owner) {
		ListTag list = new ListTag();
		Halls.HallView hall = Halls.hallOf(owner);
		ServerLevel level = hall == null ? null : server.getLevel(hall.dim());
		if (level == null) {
			return list;
		}
		for (Formation formation : FormationData.get(level).all()) {
			if (!formation.owner().equals(owner)) {
				continue;
			}
			GrandFormation.State state = GrandFormation.state(level, formation, server.getTickCount());
			CompoundTag tag = new CompoundTag();
			tag.putString("kind", state.grand() ? "formasi_agung" : "protective");
			tag.putInt("x", formation.pos().getX());
			tag.putInt("y", formation.pos().getY());
			tag.putInt("z", formation.pos().getZ());
			tag.putBoolean("on", formation.active());
			tag.putInt("power", formation.power());
			tag.putFloat("r", (float) state.radius());
			int shield = 0;
			for (CoreRecord record : CoreTicker.store(level).all()) {
				if (record.owner.equals(owner) && record.state.running() && "five_layer_shield".equals(record.spec)) {
					shield = ShieldEffect.inner(new dev.kultivator.formasi.effect.CoreContextImpl(level, record,
							FormationRegistry.byId(record.spec), null)) ? 2 : 1;
				}
			}
			tag.putInt("layers", Math.min(Stacking.BARRIER_LAYER_CAP, state.layers() + (state.grand() ? shield : 0)));
			list.add(tag);
			if (list.size() >= 4) {
				break;
			}
		}
		return list;
	}

	private static CompoundTag field(ServerPlayer viewer) {
		CompoundTag tag = new CompoundTag();
		if (!(viewer.level() instanceof ServerLevel level)) {
			return tag;
		}
		FormationField field = Stacking.fieldAt(level, viewer.position(), viewer);
		tag.putFloat("g", (float) field.sectGathering());
		tag.putFloat("cap", (float) field.stackCap());
		tag.putFloat("drain", (float) field.drain());
		tag.putInt("h", field.healAmp());
		tag.putFloat("qi", (float) field.qiRegen());
		tag.putFloat("dev", (float) field.deviation());
		boolean pelindung = ProtectiveFormations.insideActiveFormation(level, viewer.position());
		double trib = field.tribFactor() >= 1.0D ? 1.0D
				: Math.max(Stacking.TRIB_FORMATION_FLOOR / (pelindung ? Stacking.PELINDUNG_TRIB : 1.0D), field.tribFactor());
		tag.putFloat("t", (float) (trib * (pelindung ? Stacking.PELINDUNG_TRIB : 1.0D)));
		tag.putFloat("rc", (float) field.refineChance());
		tag.putInt("rs", field.refineSteps());
		tag.putFloat("rt", (float) field.refineTime());
		tag.putFloat("ins", (float) field.inscribe());
		tag.putFloat("q", (float) field.quality());
		int agung = ShieldEffect.agungLayers(level, viewer.getUUID());
		tag.putInt("l", Math.min(Stacking.BARRIER_LAYER_CAP, field.layers() + agung));
		tag.putInt("c", field.concealGrade());
		tag.putInt("i", field.illusionGrade());
		tag.putFloat("mate", (float) mateBonus(viewer));
		return tag;
	}

	/** {@code Sects.MEDITATION_BONUS} when a sect mate is within range (read without the chat side effect). */
	private static double mateBonus(ServerPlayer viewer) {
		Sect sect = SectData.get(viewer.level().getServer()).sectOf(viewer.getUUID());
		if (sect == null) {
			return 1.0D;
		}
		double range = Sects.MEDITATION_RANGE * Sects.MEDITATION_RANGE;
		for (ServerPlayer other : viewer.level().getServer().getPlayerList().getPlayers()) {
			if (other != viewer && other.level() == viewer.level() && sect.isMember(other.getUUID()) && other.distanceToSqr(viewer) <= range) {
				return Sects.MEDITATION_BONUS;
			}
		}
		return 1.0D;
	}

	/** Families as text for logs. */
	static String family(Family family) {
		return family.id();
	}

	private HallBridge() {
	}
}
