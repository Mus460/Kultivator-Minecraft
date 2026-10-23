package dev.kultivator.sect.build.flow;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Cultivators;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.SectData;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.ComponentView;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.PlanStats;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the client gets (DESIGN 21.21): the {@code "build"} part of every {@code HallInfo} for the Bangun tablet, the small
 * {@code status} push for the HUD, the wireframe of the open component and the mist (every 2 s to the owner and sect mates
 * near the site), and the {@code catalog} for the template screen. The last two travel in a {@code BuildPreview} with a
 * {@code kind} key, so the client tells them from a footprint preview (which has none).
 */
public final class BuildInfo {

	public static final int MAX_COMPONENTS = 400;
	public static final int MAX_SALVAGE = 64;
	public static final int STATUS_TICKS = 40;
	/** The catalog (plan stats of all nine templates) is cached this long (ticks). */
	public static final long CATALOG_TICKS = 12000L;

	private static @Nullable ListTag catalogStats;
	private static long catalogAt = Long.MIN_VALUE;

	// ================================================================== the Bangun tablet

	/** The {@code "build"} tag of a {@code HallInfo}: {@code owner}'s site as {@code viewer} sees it. */
	public static CompoundTag compose(ServerPlayer viewer, UUID owner) {
		CompoundTag tag = new CompoundTag();
		KultivatorConfig config = KultivatorConfig.get();
		EngineApi engine = EngineApi.get();
		boolean own = viewer.getUUID().equals(owner);
		tag.putBoolean("on", config.sectConstruction);
		tag.putBoolean("engine", EngineApi.registered());
		tag.putBoolean("readonly", !own);
		tag.putInt("max_t", config.sectBuildMaxTemplate);
		tag.putBoolean("gate", config.sectTemplateRealmGate);
		tag.putInt("realm", Cultivators.get(viewer).realm().ordinal());
		int template = engine.template(owner);
		tag.putInt("t", template);
		int tier = engine.tier(owner);
		tag.putInt("tier", tier);
		SiteView site = template > 0 ? engine.site(owner) : null;
		if (site != null) {
			tag.put("site", site.toTag());
			tag.store("status_text", ComponentSerialization.CODEC, SiteStatus.statusText(owner, site));
		}
		Bill balance = Ledger.INSTANCE.balance(owner);
		tag.put("ledger", balance.toTag());
		if (template > 0) {
			List<ComponentView> components = engine.components(owner);
			tag.put("comps", components(components, tier));
			ComponentView open = SiteStatus.open(components);
			if (open != null) {
				tag.putString("open", open.id());
			}
			ComponentView next = SiteStatus.next(components);
			if (next != null) {
				CompoundTag nextTag = new CompoundTag();
				nextTag.putString("id", next.id());
				nextTag.store("name", ComponentSerialization.CODEC, next.name());
				nextTag.putInt("tier", next.tier());
				nextTag.put("bill", next.bill().toTag());
				nextTag.put("short", next.bill().minus(balance).toTag());
				tag.put("next", nextTag);
			}
			if (tier < SectTemplates.TIERS) {
				tag.put("need1", engine.remainingBill(owner, tier + 1).toTag());
				if (site != null) {
					tag.put("bill1", engine.tierBill(template, tier + 1, site.glaze()).toTag());
				}
			}
			if (tier + 1 < SectTemplates.TIERS) {
				tag.put("need2", engine.remainingBill(owner, tier + 2).toTag());
			}
			tag.put("need_all", engine.remainingBill(owner, 0).toTag());
			tag.putBoolean("can_change", Founding.freeChange(owner));
		}
		tag.put("missions", BuildMissions.info(owner));
		tag.put("parties", GatherExpeditions.info(owner));
		tag.putInt("party_max", GatherExpeditions.maxParties(owner));
		tag.put("builders", Builders.info(viewer, owner));
		tag.put("salvage", salvage(owner));
		CompoundTag surplus = new CompoundTag();
		for (Bahan cat : Deposits.SURPLUS) {
			surplus.putLong(cat.id(), Deposits.surplusUnits(owner, cat));
		}
		tag.put("surplus", surplus);
		CompoundTag flags = new CompoundTag();
		flags.putBoolean("exc", FlowData.flag(owner, FlowData.F_EXCAVATION));
		flags.putBoolean("rm", FlowData.flag(owner, FlowData.F_REMOVE));
		flags.putBoolean("empty", FlowData.flag(owner, FlowData.F_EMPTY));
		flags.putBoolean("self", FlowData.flag(owner, FlowData.F_SELF_BUILD));
		flags.putBoolean("can_auth", own && Authority.mayAuthorize(viewer));
		tag.put("flags", flags);
		tag.put("donors", donors(owner));
		tag.putBoolean("lan", lan(viewer, owner));
		tag.put("history", FlowData.of(owner).getListOrEmpty("history").copy());
		CompoundTag guarantee = new CompoundTag();
		guarantee.putInt("tier", FlowData.guaranteeTier(owner));
		guarantee.putString("kind", FlowData.guaranteeKind(owner));
		guarantee.putLong("until", FlowData.guaranteeUntil(owner));
		tag.put("guarantee", guarantee);
		tag.putInt("frozen_cap", FlowData.frozenScanCap(owner));
		tag.putLong("day", FlowData.day());
		return tag;
	}

	/** The components in placement order, compact: the bill only for the tier being built and the next one. */
	private static ListTag components(List<ComponentView> components, int tier) {
		ListTag list = new ListTag();
		for (ComponentView component : components) {
			if (list.size() >= MAX_COMPONENTS) {
				break;
			}
			CompoundTag entry = new CompoundTag();
			entry.putString("id", component.id());
			entry.putString("kind", component.kind());
			entry.putInt("tier", component.tier());
			entry.store("name", ComponentSerialization.CODEC, component.name());
			BoundingBox box = component.box();
			entry.putIntArray("box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
			entry.putInt("st", component.state());
			entry.putInt("pg", (int) Math.round(component.progress() * 100.0D));
			entry.putInt("bl", component.blocked());
			if (component.tier() >= tier + 1 && component.tier() <= tier + 2 && component.state() != ComponentView.STATE_DONE) {
				entry.put("bill", component.bill().toTag());
			}
			list.add(entry);
		}
		return list;
	}

	private static CompoundTag salvage(UUID owner) {
		CompoundTag tag = new CompoundTag();
		ListTag counts = new ListTag();
		int more = 0;
		for (Map.Entry<Item, Long> entry : Ledger.INSTANCE.salvageCounts(owner).entrySet()) {
			if (counts.size() >= MAX_SALVAGE) {
				more++;
				continue;
			}
			CompoundTag each = new CompoundTag();
			each.putString("item", Ledger.id(entry.getKey()));
			each.putLong("n", entry.getValue());
			counts.add(each);
		}
		tag.put("list", counts);
		tag.putInt("more", more);
		ListTag rich = new ListTag();
		List<ItemStack> stacks = Ledger.INSTANCE.richStacks(owner);
		for (int i = 0; i < Math.min(32, stacks.size()); i++) {
			CompoundTag each = new CompoundTag();
			each.putInt("index", i);
			each.putString("item", Ledger.id(stacks.get(i).getItem()));
			each.putInt("n", stacks.get(i).getCount());
			each.store("name", ComponentSerialization.CODEC, stacks.get(i).getHoverName());
			rich.add(each);
		}
		tag.put("rich", rich);
		return tag;
	}

	private static ListTag donors(UUID owner) {
		CompoundTag section = FlowData.of(owner);
		CompoundTag donors = section.getCompoundOrEmpty("donors");
		CompoundTag names = section.getCompoundOrEmpty("donor_names");
		List<CompoundTag> entries = new ArrayList<>();
		for (String key : donors.keySet()) {
			CompoundTag entry = new CompoundTag();
			entry.putString("name", names.getStringOr(key, "?"));
			entry.putLong("units", donors.getLongOr(key, 0L));
			entries.add(entry);
		}
		entries.sort((a, b) -> Long.compare(b.getLongOr("units", 0L), a.getLongOr("units", 0L)));
		ListTag list = new ListTag();
		for (int i = 0; i < Math.min(20, entries.size()); i++) {
			list.add(entries.get(i));
		}
		return list;
	}

	/** True on LAN or a server, or when the owner's /sect has more than one member (the Penyumbang panel shows). */
	private static boolean lan(ServerPlayer viewer, UUID owner) {
		MinecraftServer server = viewer.level().getServer();
		if (server.isPublished() || !server.isSingleplayer()) {
			return true;
		}
		Sect sect = SectData.get(server).sectOf(owner);
		return sect != null && sect.size() > 1;
	}

	// ================================================================== the status push

	/** Every 2 s: the small status tag to the owner and sect mates within the build range of the site. */
	static void pushStatus(MinecraftServer server) {
		if (server.getTickCount() % STATUS_TICKS != 0) {
			return;
		}
		EngineApi engine = EngineApi.get();
		double range = KultivatorConfig.get().sectBuildRange;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID owner = siteNear(player, range);
			if (owner == null) {
				continue;
			}
			SiteView site = engine.site(owner);
			if (site == null) {
				continue;
			}
			Net.send(player, new Payloads.BuildPreview(status(player, owner, site)));
		}
	}

	/** The owner whose site {@code player} stands near: its own first, then sect mates'. */
	private static @Nullable UUID siteNear(ServerPlayer player, double range) {
		UUID self = player.getUUID();
		if (near(player, self, range)) {
			return self;
		}
		Sect sect = SectData.get(player.level().getServer()).sectOf(self);
		if (sect != null) {
			for (Sect.Member member : sect.members()) {
				if (!member.id().equals(self) && near(player, member.id(), range)) {
					return member.id();
				}
			}
		}
		return null;
	}

	private static boolean near(ServerPlayer player, UUID owner, double range) {
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !hall.dim().equals(player.level().dimension()) || EngineApi.get().template(owner) <= 0) {
			return false;
		}
		double dx = player.getX() - (hall.pos().getX() + 0.5D);
		double dz = player.getZ() - (hall.pos().getZ() + 0.5D);
		return dx * dx + dz * dz <= range * range;
	}

	private static CompoundTag status(ServerPlayer player, UUID owner, SiteView site) {
		CompoundTag tag = new CompoundTag();
		tag.putString("kind", "status");
		tag.putBoolean("own", player.getUUID().equals(owner));
		tag.putInt("t", site.template());
		tag.putInt("tier", site.tier());
		tag.putInt("status", site.status());
		tag.putString("glaze", site.glaze().id());
		tag.store("status_text", ComponentSerialization.CODEC, SiteStatus.statusText(owner, site));
		BoundingBox fp = site.footprint();
		tag.putIntArray("fp", new int[]{fp.minX(), fp.minY(), fp.minZ(), fp.maxX(), fp.maxY(), fp.maxZ()});
		tag.putIntArray("stele", new int[]{site.stele().getX(), site.stele().getY(), site.stele().getZ()});
		tag.putInt("plaza_y", site.plazaY());
		tag.putDouble("rate", site.rate());
		List<ComponentView> components = EngineApi.get().components(owner);
		ComponentView open = SiteStatus.open(components);
		if (open != null) {
			CompoundTag openTag = new CompoundTag();
			openTag.store("name", ComponentSerialization.CODEC, open.name());
			openTag.putInt("pg", (int) Math.round(open.progress() * 100.0D));
			BoundingBox box = open.box();
			openTag.putIntArray("box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
			openTag.putInt("y", open.y());
			tag.put("open", openTag);
		}
		Bill shortfall = SiteStatus.nextShortfall(owner);
		if (!shortfall.isEmpty() && site.status() == SiteView.STATUS_WAITING_FUNDS) {
			tag.store("short", ComponentSerialization.CODEC, Fmt.joined(shortfall, 4));
		}
		if (player.level() instanceof ServerLevel level && level.dimension().equals(site.dim())) {
			tag.putIntArray("builders", Builders.workingIds(owner, level));
			net.minecraft.core.BlockPos work = EngineApi.get().workSpot(owner);
			if (work != null && open != null) {
				tag.putIntArray("work", new int[]{work.getX(), work.getY(), work.getZ()});
			}
		}
		return tag;
	}

	// ================================================================== the catalog for the template screen

	/** Sends the template screen's catalog: the plan stats of all nine templates and the player's founding state. */
	public static void sendCatalog(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		long now = server.overworld().getGameTime();
		if (catalogStats == null || now - catalogAt > CATALOG_TICKS) {
			ListTag stats = new ListTag();
			boolean complete = true;
			// Sekte Langit (DESIGN 37.3.2): the sky templates' stats too while the feature is on.
			int last = dev.kultivator.sekte.langit.api.LangitConfig.on() ? SectTemplates.SKY_LAST : SectTemplates.COUNT;
			for (int template = 1; template <= last; template++) {
				if (!SectTemplates.validAny(template)) {
					continue;
				}
				PlanStats plan;
				try {
					plan = EngineApi.get().stats(template);
				} catch (RuntimeException exception) {
					plan = PlanStats.empty(template);
				}
				complete &= !plan.isEmpty();
				stats.add(plan.toTag());
			}
			catalogStats = stats;
			catalogAt = complete ? now : now - CATALOG_TICKS + 200L;
		}
		UUID owner = player.getUUID();
		EngineApi engine = EngineApi.get();
		CompoundTag tag = new CompoundTag();
		tag.putString("kind", "catalog");
		tag.put("stats", catalogStats.copy());
		KultivatorConfig config = KultivatorConfig.get();
		tag.putInt("max_t", config.sectBuildMaxTemplate);
		tag.putBoolean("gate", config.sectTemplateRealmGate);
		tag.putInt("realm", Cultivators.get(player).realm().ordinal());
		Halls.HallView hall = Halls.hallOf(owner);
		tag.putBoolean("hall", hall != null);
		tag.putBoolean("active", hall != null && hall.active());
		tag.putBoolean("engine", EngineApi.registered());
		tag.putInt("t", engine.template(owner));
		tag.putInt("tier", engine.tier(owner));
		SiteView site = engine.site(owner);
		tag.putInt("rot", site != null ? site.rotation() : hall == null ? 0 : Founding.autoRotation(player, hall.pos()));
		tag.putString("glaze", site != null ? site.glaze().id() : "tinta");
		tag.putBoolean("can_change", Founding.freeChange(owner));
		tag.putInt("builders", Builders.souls(owner).size());
		tag.putDouble("speed", config.sectBuildSpeed);
		// Sekte Langit: the Awan tab's gate (LangitRancang decides again on the server).
		dev.kultivator.sekte.langit.api.LangitConfig sky = dev.kultivator.sekte.langit.api.LangitConfig.get();
		tag.putBoolean("sky_on", dev.kultivator.sekte.langit.api.LangitConfig.on());
		tag.putBoolean("sky_gate", sky.gerbangRanah);
		tag.putInt("sky_max_b", sky.awanAwalMaks);
		SiteView skySite = engine.skySite(owner);
		tag.putInt("sky_t", skySite == null ? 0 : skySite.template());
		int[] territory = new int[SectTemplates.TIERS];
		for (int n = 1; n <= SectTemplates.TIERS; n++) {
			territory[n - 1] = dev.kultivator.sect.build.blueprint.Templates.get(SectTemplates.SKY_FIRST + n - 1).territory();
		}
		tag.putIntArray("sky_terr", territory);
		Net.send(player, new Payloads.BuildPreview(tag));
	}

	static void clearSession() {
		catalogStats = null;
		catalogAt = Long.MIN_VALUE;
	}

	private BuildInfo() {
	}
}
