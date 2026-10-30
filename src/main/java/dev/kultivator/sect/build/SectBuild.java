package dev.kultivator.sect.build;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.SectData;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.BuildListener;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.MaterialLedger;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.build.flow.BuildCommands;
import dev.kultivator.sect.build.flow.BuildFlow;
import dev.kultivator.sect.build.flow.BuildInfo;
import dev.kultivator.sect.build.flow.BuildMissions;
import dev.kultivator.sect.build.flow.Builders;
import dev.kultivator.sect.build.flow.Ceremonies;
import dev.kultivator.sect.build.flow.Deposits;
import dev.kultivator.sect.build.flow.FlowData;
import dev.kultivator.sect.build.flow.Founding;
import dev.kultivator.sect.build.flow.LangitRancang;
import dev.kultivator.sect.build.flow.GatherExpeditions;
import dev.kultivator.sect.build.flow.HallBridge;
import dev.kultivator.sect.build.flow.Ledger;
import dev.kultivator.sect.build.flow.Relocation;
import dev.kultivator.sect.build.flow.SiteStatus;
import dev.kultivator.sect.build.flow.SteleDeposit;
import dev.kultivator.sect.build.flow.Authority;
import dev.kultivator.sect.hall.HallLevel;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pembangunan Sekte, the flow side (DESIGN 21.12-21.24, module P6b-alur): founding, deposits, missions, gathering trips,
 * builders, ceremonies, the {@code Actions.SECT_BUILD} actions, the hall info part, all commands of 21.22 (through
 * {@link EngineApi}) and the public API the phase-6 modules use (registered as their providers by {@code flow.HallBridge}).
 * The player only has to deposit materials: each building starts and builds itself as soon as its bill is covered (DESIGN
 * 21 user decision 4); builders, missions and gathering trips are optional helpers.
 * <p>
 * Sources (the phase as a whole): <i>Return of the Mount Hua Sect</i> (the fallen Mount Hua restored); <i>Perfect World</i>
 * ch. 551/558 (Shi Hao vows to rebuild the Heaven Mending Pavilion); <i>Martial Peak</i> (Yang Kai founds High Heaven
 * Palace in the Star Boundary's Northern Territory, later High Heaven Cave Heaven); <i>Battle Through the Heavens</i>
 * (Pan Men, Xiao Gate, the Yan Alliance).
 */
public final class SectBuild {

	/** Territory radius R_t of templates 1-9 (DESIGN 21.1 Table B: {@code max(|z_min|, x_half) + 8}). */
	public static final int[] TERRITORY = {24, 32, 40, 46, 58, 68, 80, 96, 108};
	/** Disciple cap +1 for templates 5-9 once T9 is complete (DESIGN 21.1 size perks). */
	public static final int CAP_BONUS_TEMPLATE = 5;
	/** +1 candidate per arrival for templates 6-9 at tier 7 or more. */
	public static final int CANDIDATE_TEMPLATE = 6;
	public static final int CANDIDATE_TIER = 7;

	/** Called once from {@code Kultivator.onInitialize}. */
	public static void init() {
		// Titik Formasi plots join templates 3-9 through TemplateExtensions: before the first Templates.get (DESIGN 24.6.2).
		dev.kultivator.sect.build.blueprint.FormasiTitik.register();
		MaterialLedger.register(Ledger.INSTANCE);
		BuildListener.add(Ceremonies.INSTANCE);
		HallBridge.register();
		SteleDeposit.init();
		Halls.addInfoProvider((viewer, owner, info) -> {
			try {
				info.put("build", BuildInfo.compose(viewer, owner));
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("The sect construction part of a hall info failed", exception);
			}
		});
		BuildFlow.init();
	}

	// ================================================================== actions

	/** {@code Actions.SECT_BUILD}: {@code arg} = the sub-op (unused), {@code text} = "op[:param]". */
	public static void handleAction(ServerPlayer player, Payloads.Action action) {
		String text = action.text() == null ? "" : action.text();
		int colon = text.indexOf(':');
		String op = colon < 0 ? text : text.substring(0, colon);
		String param = colon < 0 ? "" : text.substring(colon + 1);
		UUID self = player.getUUID();
		UUID owner = Halls.viewedOwner(player);
		boolean own = owner.equals(self);
		try {
			if (handleShared(player, owner, own, op, param)) {
				return;
			}
			if (!own) {
				// A sect mate's tablet is read-only apart from depositing.
				return;
			}
			handleOwn(player, owner, op, param);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Sect construction action {} failed", op, exception);
		}
		Halls.sendInfo(player, owner);
	}

	/** Actions anyone with the tablet open may use (deposits by sect mates, refresh, the catalog, the FPS hint). */
	private static boolean handleShared(ServerPlayer player, UUID owner, boolean own, String op, String param) {
		switch (op) {
			case "refresh" -> {
				Halls.sendInfo(player, owner);
				return true;
			}
			case "catalog" -> {
				BuildInfo.sendCatalog(player);
				return true;
			}
			case "fps" -> {
				Builders.setFpsLow(player.getUUID(), "1".equals(param));
				return true;
			}
			case "deposit", "deposit_item", "deposit_needed", "deposit_all" -> {
				if (!own && !sectMate(player, owner)) {
					return true;
				}
				if (Halls.hallOf(owner) == null) {
					Msg.chat(player, Component.translatable("kultivator.sectbuild.no_hall").withStyle(ChatFormatting.YELLOW));
					return true;
				}
				switch (op) {
					case "deposit" -> Deposits.depositOne(player, owner, parse(param, -1));
					case "deposit_item" -> {
						Item item = item(param);
						if (item != null) {
							Deposits.depositItem(player, owner, item);
						}
					}
					case "deposit_needed" -> Deposits.depositNeeded(player, owner);
					default -> Deposits.depositAll(player, owner);
				}
				Halls.sendInfo(player, owner);
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/** The owner's own actions from the Bangun tablet and the template screen. */
	private static void handleOwn(ServerPlayer player, UUID owner, String op, String param) {
		EngineApi engine = EngineApi.get();
		switch (op) {
			case "screen" -> Founding.openScreen(player, parse(param, 0));
			case "preview" -> {
				String[] parts = param.split(":");
				int template = parts.length > 0 ? parse(parts[0], 0) : 0;
				int rot = parts.length > 1 ? parse(parts[1], -1) : -1;
				Glaze glaze = parts.length > 2 ? Glaze.byId(parts[2]) : null;
				boolean upgrade = parts.length > 3 && "upgrade".equals(parts[3]);
				Founding.preview(player, template, rot, glaze, upgrade ? Founding.Mode.UPGRADE : Founding.Mode.FOUND, false);
			}
			case "upgrade" -> Founding.preview(player, parse(param, 0), -1, null, Founding.Mode.UPGRADE, false);
			// Sekte Langit (DESIGN 37.3.4): the template screen's Awan tab.
			case "langit" -> LangitRancang.design(player, parse(param, 0), false);
			case "langit_tinggi" -> LangitRancang.heightStep(player, parse(param, 0));
			case "rotate" -> Founding.rotate(player);
			case "confirm" -> Founding.confirm(player, parseLong(param));
			case "cancel" -> Founding.cancel(player, true);
			case "change" -> Founding.change(player);
			case "lumbung" -> Deposits.fromStorehouse(player, owner);
			case "take" -> {
				Item item = item(param);
				if (item != null) {
					Deposits.takeSalvage(player, owner, item);
				}
			}
			case "take_rich" -> Deposits.takeRich(player, owner, parse(param, -1));
			case "surplus" -> {
				Bahan cat = Bahan.byId(param);
				if (cat != null) {
					Deposits.takeSurplus(player, owner, cat);
				}
			}
			case "flag" -> flag(player, owner, param);
			case "pause" -> {
				if (!engine.pause(owner, "1".equals(param))) {
					Msg.chat(player, Component.translatable("kultivator.sectbuild.upgrade.no_template").withStyle(ChatFormatting.YELLOW));
				}
			}
			case "prio" -> Msg.actionBar(player, BuildCommands.componentAction(owner, param, 0).message());
			case "skip" -> Msg.chat(player, BuildCommands.componentAction(owner, param, 1).message());
			case "finish_blocked" -> Msg.actionBar(player, BuildCommands.componentAction(owner, param, 2).message());
			case "recheck" -> {
				engine.recheck(owner);
				Msg.actionBar(player, Component.translatable("kultivator.sectbuild.recheck.done").withStyle(ChatFormatting.AQUA));
			}
			case "repair" -> BuildCommands.repair(player);
			case "deconstruct" -> Relocation.deconstructNow(player, "1".equals(param));
			case "abandon" -> Relocation.abandonNow(player);
			case "accept" -> BuildMissions.accept(player, owner, parse(param, -1));
			case "gather" -> gather(player, owner, param);
			case "builder" -> {
				String[] parts = param.split(":");
				Entity entity = parts.length > 0 ? player.level().getEntity(parse(parts[0], 0)) : null;
				boolean on = parts.length < 2 || "1".equals(parts[1]);
				if (entity instanceof DiscipleEntity disciple && disciple.isOwnedBy(player)) {
					if (on) {
						Builders.assign(player, owner, disciple);
					} else {
						Builders.release(owner, disciple.soulId(), true);
					}
				}
			}
			case "builder_off" -> {
				try {
					Builders.release(owner, UUID.fromString(param), true);
				} catch (IllegalArgumentException ignored) {
					// a broken soul id from the client is ignored
				}
			}
			default -> {
			}
		}
	}

	/** {@code flag:<exc|rm|empty|self>:<0|1>}; the two permissions need singleplayer without LAN or an op. */
	private static void flag(ServerPlayer player, UUID owner, String param) {
		String[] parts = param.split(":");
		if (parts.length < 2) {
			return;
		}
		boolean on = "1".equals(parts[1]);
		switch (parts[0]) {
			case "exc" -> FlowData.setFlag(owner, FlowData.F_EXCAVATION, on);
			case "self" -> FlowData.setFlag(owner, FlowData.F_SELF_BUILD, on);
			case "rm", "empty" -> {
				if (on && !Authority.mayAuthorize(player)) {
					Msg.chat(player, Component.translatable("kultivator.sectbuild.authorize.refused").withStyle(ChatFormatting.YELLOW));
					return;
				}
				FlowData.setFlag(owner, "rm".equals(parts[0]) ? FlowData.F_REMOVE : FlowData.F_EMPTY, on);
				if (on) {
					// Remembered so a flag set by a non-op in singleplayer stops counting once the world is opened to LAN.
					FlowData.setFlag(owner, FlowData.F_AUTH_OP, player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
				}
				FlowData.applyAuthorization(owner);
			}
			default -> {
			}
		}
	}

	/** {@code gather:<cat>:<0|1 long>:<eid,eid,eid>}. */
	private static void gather(ServerPlayer player, UUID owner, String param) {
		String[] parts = param.split(":");
		Bahan cat = parts.length > 0 ? Bahan.byId(parts[0]) : null;
		if (cat == null || parts.length < 3) {
			return;
		}
		boolean longTrip = "1".equals(parts[1]);
		List<DiscipleEntity> members = new ArrayList<>();
		for (String each : parts[2].split(",")) {
			Entity entity = player.level().getEntity(parse(each.trim(), 0));
			if (entity instanceof DiscipleEntity disciple && disciple.isOwnedBy(player) && !members.contains(disciple)) {
				members.add(disciple);
			}
		}
		GatherExpeditions.send(player, owner, cat, longTrip, members);
	}

	private static boolean sectMate(ServerPlayer player, UUID owner) {
		Sect sect = SectData.get(player.level().getServer()).sectOf(owner);
		return sect != null && sect.isMember(player.getUUID());
	}

	private static @Nullable Item item(String id) {
		Identifier key = Identifier.tryParse(id);
		Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
		return item == null || item == Items.AIR ? null : item;
	}

	private static int parse(String text, int fallback) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static long parseLong(String text) {
		try {
			return Long.parseLong(text.trim());
		} catch (NumberFormatException exception) {
			return 0L;
		}
	}

	// ================================================================== commands

	/** Player commands under {@code aula bangun} (merged into module A's {@code aula} node) and debug {@code aula bangun}. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		BuildCommands.addCommands(root, debug);
	}

	/** Test commands {@code uji sekte ...} (op only; {@code uji} already requires the permission). */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		BuildCommands.addTestCommands(uji);
	}

	// ================================================================== public API for the phase-6 modules (DESIGN 21.24)

	/**
	 * -1 without a template (module A keeps its scan), else {@code max(frozenScanCap, tier)} (and the guarantee tier of an
	 * upgrade or a move). A move guarantee without a template still holds the level for its ten days.
	 */
	public static int buildingCap(UUID owner) {
		EngineApi engine = EngineApi.get();
		int guarantee = FlowData.guaranteeTier(owner);
		if (engine.template(owner) <= 0) {
			return guarantee > 0 ? Math.max(guarantee, FlowData.frozenScanCap(owner)) : -1;
		}
		return Math.max(Math.max(FlowData.frozenScanCap(owner), engine.tier(owner)), guarantee);
	}

	/** The building bits from the tier ({@code B_PAVILION} T3, {@code B_DRILL} T7, {@code B_TOWER} T9). */
	public static int buildingFlags(UUID owner) {
		EngineApi engine = EngineApi.get();
		if (engine.template(owner) <= 0 && FlowData.guaranteeTier(owner) <= 0) {
			return 0;
		}
		int tier = Math.max(engine.tier(owner), FlowData.guaranteeTier(owner));
		int flags = 0;
		if (tier >= 3) {
			flags |= HallLevel.B_PAVILION;
		}
		if (tier >= 7) {
			flags |= HallLevel.B_DRILL;
		}
		if (tier >= 9) {
			flags |= HallLevel.B_TOWER;
		}
		return flags;
	}

	/** The completed construction tier 0-9. */
	public static int tier(UUID owner) {
		return EngineApi.get().tier(owner);
	}

	/** The template 1-9, 0 without one. */
	public static int templateSize(UUID owner) {
		return EngineApi.get().template(owner);
	}

	/** Extra disciple cap (1 for templates 5-9 once T9 is complete). */
	public static int capBonus(UUID owner) {
		EngineApi engine = EngineApi.get();
		return SectTemplates.rank(engine.template(owner)) >= CAP_BONUS_TEMPLATE && engine.tier(owner) >= SectTemplates.TIERS ? 1 : 0;
	}

	/** Extra candidates per arrival (1 for templates 6-9 at tier 7 or more). */
	public static int extraCandidates(UUID owner) {
		EngineApi engine = EngineApi.get();
		return SectTemplates.rank(engine.template(owner)) >= CANDIDATE_TEMPLATE && engine.tier(owner) >= CANDIDATE_TIER ? 1 : 0;
	}

	/** The template's territory radius R_t (0 without a template). */
	public static int territoryRadius(UUID owner) {
		int template = EngineApi.get().template(owner);
		if (SectTemplates.isSky(template)) {
			// A sky template's territory covers its whole archipelago (DESIGN 37.3.2, the layout's own radius).
			return dev.kultivator.sect.build.blueprint.Templates.get(template).territory();
		}
		return SectTemplates.valid(template) ? TERRITORY[template - 1] : 0;
	}

	/** A named anchor of the built plan, or null. */
	public static @Nullable BlockPos anchor(UUID owner, String id) {
		return EngineApi.get().anchor(owner, id);
	}

	/** True within {@code margin} blocks of any confirmed footprint or showcase. */
	public static boolean insideAnySite(Level level, BlockPos pos, int margin) {
		return EngineApi.get().insideAnySite(level, pos, margin);
	}

	/** The construction status line for the Aula tablet, or null without a template. */
	public static @Nullable Component statusLine(UUID owner) {
		if (!KultivatorConfig.get().sectConstruction) {
			return null;
		}
		return SiteStatus.line(owner);
	}

	private SectBuild() {
	}
}
