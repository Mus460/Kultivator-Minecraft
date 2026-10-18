package dev.kultivator.sect.build.flow;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.SectData;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.BuildFxIds;
import dev.kultivator.sect.build.api.BuildListener;
import dev.kultivator.sect.build.api.ComponentView;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The construction ceremonies and messages (DESIGN 21.12 step 7, 21.16, 21.23), the {@link BuildListener} the engine fires:
 * <ul>
 * <li><b>Batu Pertama</b> on confirming a footprint: the outline flashes, the stele chimes, disciples in the territory turn
 * and bow, advancement {@code first_stone}, a Catatan line.</li>
 * <li><b>Component done</b>: a cherry-petal burst along its roofline, an amethyst chime, "Paviliun Kitab selesai".</li>
 * <li><b>Tier done</b> (10 s): disciples bow, three bells, a gold light pillar from {@code main_hall}, lantern orbs, a
 * gold ribbon along the axis, the title "Sekte ‹nama› · Tingkat 5 — Formasi Agung", a sect chat line, a toast, a log line
 * and advancements; the grand version and a server line (template 5+) when the template is complete.</li>
 * <li>Blocked pings (a red X shimmer every 5 s for up to 30 s) and "waiting for materials" lines with the deposit button.</li>
 * </ul>
 * No renown is awarded (DESIGN 20.3.1). Test sites play every effect but grant no advancements or server lines.
 * Source: game design; the petals echo the plum motif of <i>Return of the Mount Hua Sect</i>.
 */
public final class Ceremonies implements BuildListener {

	public static final Ceremonies INSTANCE = new Ceremonies();

	// ---- the flow's own FxType.SECT_BUILD sub-effects (above the shared BuildFxIds), drawn by client.sectbuild.BuildFx
	/** A builder's hammer dust and gold qi thread: pos = the hand, target = the work layer, b = colour. */
	public static final int FX_WORK = 100;
	/** Materials flowing into the stele: pos = the depositor, target = the stele top, b = category colour, f = units. */
	public static final int FX_DEPOSIT = 101;
	/** A lantern orb rising from a finished component: pos = its roof centre, b = tier. */
	public static final int FX_ORB = 102;
	/** The owner's toast of a finished tier (sent to the owner only): b = tier, f = template. */
	public static final int FX_TOAST = 103;
	/** The grand finale of a complete template: pos = the main hall, target = the gate end of the axis, b = template. */
	public static final int FX_GRAND = 104;
	/** A jade puff where a sect block was refunded: pos = the cell, b = category colour. */
	public static final int FX_PUFF = 105;

	/** Disciples bow this long (ticks), like the recruit bow of DESIGN 20.3.3. */
	public static final int BOW_TICKS = 40;
	public static final long BLOCKED_MESSAGE_TICKS = 1200L;
	public static final long WAITING_ACTIONBAR_TICKS = 6000L;
	public static final long WAITING_CHAT_TICKS = 12000L;
	private static final int JADE = 0x8FE3C8;

	private record Bow(DiscipleEntity entity, Vec3 toward, int start) {
	}

	private record Scheduled(int tick, Runnable action) {
	}

	private static final List<Bow> BOWS = new ArrayList<>();
	private static final List<Scheduled> SCHEDULE = new ArrayList<>();
	private static final Map<UUID, Long> BLOCKED_SAID = new HashMap<>();
	private static final Map<UUID, Long> WAITING_BAR = new HashMap<>();
	private static final Map<UUID, Long> WAITING_CHAT = new HashMap<>();
	private static @Nullable MinecraftServer server;

	static void setServer(@Nullable MinecraftServer value) {
		server = value;
	}

	// ================================================================== BuildListener

	@Override
	public void onConfirmed(UUID owner, int template) {
		SiteView site = EngineApi.get().site(owner);
		ServerLevel level = level(site);
		FlowData.applyAuthorization(owner);
		if (site != null && level != null) {
			BoundingBox fp = site.footprint();
			// Drawn once (the engine no longer sends its own), to everyone near the footprint, not only near its corner.
			dev.kultivator.sect.build.engine.BuildEngine.sendBox(level, new BoundingBox(fp.minX(), site.plazaY(), fp.minZ(), fp.maxX(), site.plazaY(), fp.maxZ()),
					site.plazaY() + 1, BuildFxIds.FIRST_STONE, template, site.rotation());
			Vec3 top = Vec3.atBottomCenterOf(site.stele()).add(0.0D, 1.0D, 0.0D);
			level.playSound(null, top.x, top.y, top.z, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.4F, 0.8F);
			level.playSound(null, top.x, top.y, top.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2F, 1.0F);
			level.playSound(null, top.x, top.y, top.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.3F);
			bowAll(owner, level, top, Halls.hallOf(owner) == null ? 24 : Halls.hallOf(owner).radius());
		}
		// A showcase is not the hall's site: only the outline effect above, no bookkeeping.
		boolean hallSite = site != null && site.template() == template;
		if (!hallSite) {
			return;
		}
		boolean test = site.test();
		FlowData.openHistory(owner, template);
		BuildMissions.roll(owner, server == null ? net.minecraft.util.RandomSource.create() : server.overworld().getRandom());
		Halls.log(owner, Component.translatable("kultivator.sectbuild.log.first_stone", SectTemplates.name(template)));
		Halls.refreshBuildings(owner);
		ServerPlayer master = online(owner);
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.sectbuild.first_stone", SectTemplates.name(template)).withStyle(ChatFormatting.GOLD));
			Msg.chat(master, Component.translatable("kultivator.sectbuild.first_stone_hint").withStyle(ChatFormatting.GRAY));
			if (!test) {
				Advancements.grant(master, "first_stone");
			}
		}
	}

	@Override
	public void onComponentStarted(UUID owner, ComponentView component) {
		SiteView site = EngineApi.get().site(owner);
		ServerLevel level = level(site);
		BoundingBox box = component.box();
		if (level != null) {
			Fx.send(level, new Vec3(box.minX(), box.minY(), box.minZ()), new Vec3(box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1), FxType.SECT_BUILD,
					BuildFxIds.COMPONENT_OPEN, component.tier(), 0.0F);
			Vec3 center = center(box);
			level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 0.8F);
		}
		ServerPlayer master = online(owner);
		if (master != null && near(master, site)) {
			Msg.actionBar(master, Component.translatable("kultivator.sectbuild.component.started", component.name(),
					SectTemplates.tierName(component.tier())).withStyle(ChatFormatting.AQUA));
		}
	}

	@Override
	public void onComponentDone(UUID owner, ComponentView component) {
		SiteView site = EngineApi.get().site(owner);
		ServerLevel level = level(site);
		BoundingBox box = component.box();
		if (level != null) {
			Fx.send(level, new Vec3(box.minX(), box.minY(), box.minZ()), new Vec3(box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1), FxType.SECT_BUILD,
					BuildFxIds.COMPONENT_DONE, component.tier(), box.maxY() + 1);
			Vec3 center = center(box);
			level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 1.4F);
			level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 1.2F);
		}
		ServerPlayer master = online(owner);
		if (master != null) {
			if (near(master, site)) {
				Msg.actionBar(master, Component.translatable("kultivator.sectbuild.component.done", component.name()).withStyle(ChatFormatting.GOLD));
			}
			boolean glazed = site != null && (site.glaze() != Glaze.TINTA || site.template() >= 5);
			if (glazed && component.kind().contains("pagoda") && !site.test() && ownComponent(owner, component.id())) {
				Advancements.grant(master, "glazed_pagoda");
			}
		}
	}

	@Override
	public void onTierDone(UUID owner, int template, int tier) {
		SiteView site = EngineApi.get().site(owner);
		// Only the hall's own (non-test) site earns advancements and server lines; showcases just play the effects.
		boolean real = site != null && site.template() == template && !site.test();
		playTier(owner, template, tier, real);
	}

	@Override
	public void onBlocked(UUID owner, ComponentView component, int count, List<BlockPos> sample) {
		SiteView site = EngineApi.get().site(owner);
		ServerLevel level = level(site);
		if (level != null && sample != null && !sample.isEmpty()) {
			List<BlockPos> cells = new ArrayList<>(sample.subList(0, Math.min(8, sample.size())));
			for (int ping = 0; ping < 6; ping++) {
				int delay = ping * 100;
				schedule(delay, () -> {
					for (BlockPos cell : cells) {
						Vec3 at = Vec3.atCenterOf(cell);
						Fx.send(level, at, at, FxType.SECT_BUILD, BuildFxIds.BLOCKED, cells.size(), 5.0F);
					}
				});
			}
		}
		ServerPlayer master = online(owner);
		long now = FlowData.gameTime();
		Long last = BLOCKED_SAID.get(owner);
		if (master == null || last != null && now - last < BLOCKED_MESSAGE_TICKS) {
			return;
		}
		BLOCKED_SAID.put(owner, now);
		MutableComponent line = Component.translatable("kultivator.sectbuild.blocked", count, component.name()).withStyle(ChatFormatting.RED);
		line.append(Component.literal(" "));
		line.append(button("kultivator.sectbuild.button.recheck", "/kultivasi aula bangun periksa-ulang"));
		line.append(Component.literal(" "));
		line.append(button("kultivator.sectbuild.button.skip", "/kultivasi aula bangun lewati " + component.id()));
		line.append(Component.literal(" "));
		line.append(button("kultivator.sectbuild.button.finish_blocked", "/kultivasi aula bangun tanpa-terhalang " + component.id()));
		Msg.chat(master, line);
	}

	@Override
	public void onWaitingFunds(UUID owner, Bill shortfall) {
		ServerPlayer master = online(owner);
		if (master == null) {
			return;
		}
		long now = FlowData.gameTime();
		Bill need = SiteStatus.nextShortfall(owner);
		if (need.isEmpty()) {
			need = shortfall;
		}
		if (need.isEmpty()) {
			return;
		}
		Long bar = WAITING_BAR.get(owner);
		if ((bar == null || now - bar >= WAITING_ACTIONBAR_TICKS) && near(master, EngineApi.get().site(owner))) {
			WAITING_BAR.put(owner, now);
			Msg.actionBar(master, Component.translatable("kultivator.sectbuild.waiting", Fmt.joined(need, 4)).withStyle(ChatFormatting.YELLOW));
		}
		Long chat = WAITING_CHAT.get(owner);
		if (chat == null || now - chat >= WAITING_CHAT_TICKS) {
			WAITING_CHAT.put(owner, now);
			MutableComponent line = Component.translatable("kultivator.sectbuild.waiting", Fmt.joined(need, 6)).withStyle(ChatFormatting.YELLOW);
			line.append(Component.literal(" "));
			line.append(button("kultivator.sectbuild.button.deposit_needed", "/kultivasi aula bangun setor butuh"));
			Msg.chat(master, line);
		}
	}

	// ================================================================== the tier ceremony

	/** A tier ceremony (also {@code uji sekte upacara}); {@code real} grants advancements and server lines. */
	public static void playTier(UUID owner, int template, int tier, boolean real) {
		SiteView site = EngineApi.get().site(owner);
		ServerLevel level = level(site);
		Component sectName = sectName(owner);
		Component tierName = SectTemplates.tierName(tier);
		boolean complete = tier >= SectTemplates.TIERS;
		if (site != null && level != null) {
			BlockPos main = EngineApi.get().anchor(owner, "main_hall");
			Vec3 from = Vec3.atBottomCenterOf(main != null ? main : site.stele()).add(0.0D, 1.0D, 0.0D);
			Vec3 gate = gateEnd(site);
			Fx.send(level, from, gate, FxType.SECT_BUILD, BuildFxIds.TIER_DONE, tier, template);
			int bells = complete ? 9 : 3;
			for (int i = 0; i < bells; i++) {
				int delay = i * (complete ? 20 : 30);
				schedule(delay, () -> level.playSound(null, from.x, from.y, from.z, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.75F));
			}
			schedule(10, () -> level.playSound(null, from.x, from.y, from.z, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.5F, 1.0F));
			schedule(40, () -> level.playSound(null, from.x, from.y, from.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 0.8F, 1.0F));
			int index = 0;
			for (ComponentView component : EngineApi.get().components(owner)) {
				if (component.tier() == tier && component.state() == ComponentView.STATE_DONE && index < 12) {
					Vec3 orb = new Vec3((component.box().minX() + component.box().maxX() + 1) / 2.0D, component.box().maxY() + 1.5D,
							(component.box().minZ() + component.box().maxZ() + 1) / 2.0D);
					schedule(20 + index * 8, () -> Fx.send(level, orb, orb, FxType.SECT_BUILD, FX_ORB, tier, 0.0F));
					index++;
				}
			}
			if (complete) {
				schedule(60, () -> Fx.send(level, from, gate, FxType.SECT_BUILD, FX_GRAND, template, 0.0F));
			}
			bowAll(owner, level, from, Halls.hallOf(owner) == null ? 24 : Halls.hallOf(owner).radius());
		}
		ServerPlayer master = online(owner);
		Component title = Component.translatable("kultivator.sectbuild.tier.title", sectName, tier, tierName);
		if (master != null) {
			Msg.title(master, Component.translatable("kultivator.sectbuild.tier.big", tier).withStyle(ChatFormatting.GOLD),
					title.copy().withStyle(ChatFormatting.YELLOW), 10, 120, 30);
			Fx.sendTo(master, master.position(), master.position(), FxType.SECT_BUILD, FX_TOAST, tier, template);
		}
		sectChat(owner, title.copy().withStyle(ChatFormatting.GOLD));
		Halls.log(owner, Component.translatable("kultivator.sectbuild.log.tier", tier, tierName));
		Kultivator.LOGGER.info("[Kultivator] Bangun: {} {} T{} selesai", owner, SectTemplates.id(template), tier);
		if (real) {
			if (master != null) {
				if (tier >= 5) {
					Advancements.grant(master, "sect_tier_5");
				}
				if (complete) {
					Advancements.grant(master, "sect_complete");
					if (template >= 5) {
						Advancements.grant(master, "grand_sect_complete");
					}
					if (template >= 9) {
						Advancements.grant(master, "heavenly_court");
					}
				}
			}
			if (complete && template >= 5 && server != null && FlowData.award(owner, "grand_line:" + template)) {
				Msg.broadcast(server, Component.translatable("kultivator.sectbuild.grand_line", sectName, SectTemplates.name(template))
						.withStyle(ChatFormatting.GOLD));
			}
			Upgrade.onTier(owner, tier);
		}
		Halls.refreshBuildings(owner);
	}

	/** The gate end of the axis: the middle of the footprint edge the gate faces. */
	private static Vec3 gateEnd(SiteView site) {
		BoundingBox fp = site.footprint();
		Direction gate = SectTemplates.gate(site.rotation());
		double cx = site.stele().getX() + 0.5D;
		double cz = site.stele().getZ() + 0.5D;
		double y = site.plazaY() + 1.2D;
		return switch (gate) {
			case NORTH -> new Vec3(cx, y, fp.minZ() + 0.5D);
			case EAST -> new Vec3(fp.maxX() + 0.5D, y, cz);
			case WEST -> new Vec3(fp.minX() + 0.5D, y, cz);
			default -> new Vec3(cx, y, fp.maxZ() + 0.5D);
		};
	}

	// ================================================================== bows and the schedule

	/** The owner's loaded disciples within {@code radius} of {@code toward} turn to it and bow. */
	static void bowAll(UUID owner, ServerLevel level, Vec3 toward, int radius) {
		int tick = server == null ? 0 : server.getTickCount();
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.level() != level || disciple.role() != DiscipleEntity.Role.DISCIPLE || disciple.getTarget() != null
					|| disciple.distanceToSqr(toward) > (double) radius * radius) {
				continue;
			}
			BOWS.add(new Bow(disciple, toward, tick));
		}
	}

	/** Runs {@code action} on the server thread after {@code delay} ticks. */
	public static void schedule(int delay, Runnable action) {
		int tick = server == null ? 0 : server.getTickCount();
		SCHEDULE.add(new Scheduled(tick + Math.max(0, delay), action));
	}

	/** Every server tick: the bows and the scheduled ceremony steps. */
	static void tick(MinecraftServer current) {
		int tick = current.getTickCount();
		if (!SCHEDULE.isEmpty()) {
			List<Scheduled> due = new ArrayList<>();
			for (Iterator<Scheduled> it = SCHEDULE.iterator(); it.hasNext(); ) {
				Scheduled each = it.next();
				if (each.tick() <= tick) {
					due.add(each);
					it.remove();
				}
			}
			for (Scheduled each : due) {
				try {
					each.action().run();
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("A sect construction ceremony step failed", exception);
				}
			}
		}
		if (!BOWS.isEmpty()) {
			for (Iterator<Bow> it = BOWS.iterator(); it.hasNext(); ) {
				Bow bow = it.next();
				DiscipleEntity entity = bow.entity();
				int done = tick - bow.start();
				if (entity.isRemoved() || done >= BOW_TICKS || entity.getTarget() != null) {
					it.remove();
					continue;
				}
				entity.getNavigation().stop();
				double dx = bow.toward().x - entity.getX();
				double dz = bow.toward().z - entity.getZ();
				float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
				entity.setYRot(yaw);
				entity.setYHeadRot(yaw);
				entity.setXRot(done < BOW_TICKS / 2 ? done * 2.5F : (BOW_TICKS - done) * 2.5F);
			}
		}
	}

	static void clearSession() {
		BOWS.clear();
		SCHEDULE.clear();
		BLOCKED_SAID.clear();
		WAITING_BAR.clear();
		WAITING_CHAT.clear();
	}

	// ================================================================== small effects

	/** Materials flow from the depositor into the stele. */
	static void depositSwirl(UUID owner, ServerPlayer depositor, Bill added) {
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !hall.dim().equals(depositor.level().dimension())) {
			return;
		}
		Vec3 stele = Vec3.atBottomCenterOf(hall.pos()).add(0.0D, 1.2D, 0.0D);
		if (stele.distanceToSqr(depositor.position()) > 48.0D * 48.0D) {
			return;
		}
		Bahan main = Bahan.BATU;
		long best = -1L;
		for (Bahan cat : Bahan.values()) {
			if (added.centi(cat) > best) {
				best = added.centi(cat);
				main = cat;
			}
		}
		int color = best > 0L ? main.rgb() : JADE;
		float units = Math.max(1.0F, (added.totalCenti() / (float) Bahan.CENTI) + added.exact().values().stream().mapToInt(Integer::intValue).sum());
		Fx.send(depositor.level(), depositor.position().add(0.0D, 1.0D, 0.0D), stele, FxType.SECT_BUILD, FX_DEPOSIT, color, units);
	}

	// ================================================================== helpers

	static @Nullable ServerPlayer online(UUID owner) {
		return server == null ? null : server.getPlayerList().getPlayer(owner);
	}

	static @Nullable ServerLevel level(@Nullable SiteView site) {
		return site == null || server == null ? null : server.getLevel(site.dim());
	}

	private static boolean near(ServerPlayer player, @Nullable SiteView site) {
		if (site == null || !player.level().dimension().equals(site.dim())) {
			return false;
		}
		double range = dev.kultivator.KultivatorConfig.get().sectBuildRange;
		return player.distanceToSqr(Vec3.atCenterOf(site.stele())) <= range * range;
	}

	/** True when {@code id} is a component of the owner's own site (not a showcase's). */
	private static boolean ownComponent(UUID owner, String id) {
		for (ComponentView each : EngineApi.get().components(owner)) {
			if (each.id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	private static Vec3 center(BoundingBox box) {
		return new Vec3((box.minX() + box.maxX() + 1) / 2.0D, (box.minY() + box.maxY() + 1) / 2.0D, (box.minZ() + box.maxZ() + 1) / 2.0D);
	}

	/** The hall's name ("Sekte ‹owner›" when unnamed). */
	static Component sectName(UUID owner) {
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall != null && !hall.name().isBlank()) {
			return Component.literal(hall.name());
		}
		ServerPlayer master = online(owner);
		return master == null ? Component.translatable("kultivator.sectbuild.sect_default")
				: Component.translatable("kultivator.hall.default_name", master.getName().getString());
	}

	/** A line for every online member of the owner's /sect (the owner alone without one). */
	static void sectChat(UUID owner, Component line) {
		if (server == null) {
			return;
		}
		Sect sect = SectData.get(server).sectOf(owner);
		if (sect == null) {
			ServerPlayer master = online(owner);
			if (master != null) {
				Msg.chat(master, line);
			}
			return;
		}
		for (Sect.Member member : sect.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member.id());
			if (player != null) {
				Msg.chat(player, line);
			}
		}
	}

	/** A clickable chat button "[...]" that runs {@code command}. */
	static MutableComponent button(String key, String command) {
		return Component.translatable("kultivator.sectbuild.button", Component.translatable(key))
				.withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent.RunCommand(command))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	private Ceremonies() {
	}
}
