package dev.kultivator.sect.build.flow;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Cultivators;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.build.api.ComponentView;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.build.engine.PreviewData;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The founding flow (DESIGN 21.12): choose a template, preview its footprint as a hologram, rotate, confirm.
 * <ol>
 * <li>"Lihat Tapak" ({@code preview:<t>:<rot>:<glaze>} or {@code aula bangun pratinjau}) asks the engine for a preview
 * ticket; the plan is compiled on the snapshot over a few ticks.</li>
 * <li>This class polls {@code EngineApi.previewData(ticket)} and sends the {@code BuildPreview} to the player as soon as it
 * is ready (and again when the snapshot grows), with the ticket, the stele and the time left added. The hologram lasts
 * 120 s; the chat gets [Putar] [Konfirmasi] [Batal].</li>
 * <li>Confirming needs nothing red and a 100% snapshot, then the consent ("Aku paham...") and holding the jade seal for
 * 3 s on the template screen, or the clickable "[Ya, mulai bangun]" after the consent line in chat. {@code frozenScanCap}
 * is read from the hall just before {@code EngineApi.confirm}.</li>
 * </ol>
 * The same machinery serves the upgrade preview, the test preview ({@code uji sekte templat}), showcases ({@code uji sekte
 * pamer}) and the validator ({@code uji sekte periksa}). Until the first block is placed, "Ganti Templat" is free.
 * <p>
 * Source, founding one's own sect: <i>Martial Peak</i> (Yang Kai founds High Heaven Palace); <i>Battle Through the Heavens</i>
 * (Xiao Yan founds Pan Men, then Xiao Gate). The preview, consent and hold are safety game design.
 */
public final class Founding {

	/** How long a preview and its hologram last (ticks). */
	public static final int PREVIEW_TICKS = 2400;
	/** How often pending previews are polled (ticks). */
	public static final int POLL_TICKS = 10;
	public static final int MAX_WARNING_LINES = 6;
	/** A preview whose verdict did not change is sent again at most this often (ticks). */
	public static final int RESEND_TICKS = 100;

	public enum Mode {
		FOUND("found"), UPGRADE("upgrade"), TEST("test"), SHOWCASE("showcase"), CHECK("check");

		private final String id;

		Mode(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}
	}

	/** One player's preview in progress. */
	private static final class Pending {
		final UUID player;
		final long ticket;
		final int template;
		final int rot;
		final Glaze glaze;
		final Mode mode;
		final int created;
		final int tier;
		final boolean force;
		final @Nullable BlockPos origin;
		@Nullable PreviewData last;
		int lastSnap = -1;
		boolean lastConfirmable;
		int lastSent = Integer.MIN_VALUE / 2;
		boolean announced;
		/** A sky design (DESIGN 37.3.4): rotating and re-previewing go through {@code LangitRancang}. */
		boolean sky;

		Pending(UUID player, long ticket, int template, int rot, Glaze glaze, Mode mode, int created, int tier, boolean force, @Nullable BlockPos origin) {
			this.player = player;
			this.ticket = ticket;
			this.template = template;
			this.rot = rot;
			this.glaze = glaze;
			this.mode = mode;
			this.created = created;
			this.tier = tier;
			this.force = force;
			this.origin = origin;
		}
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	/** The last confirmed showcase ticket per player ({@code pamer konfirmasi}). */
	private static final Map<UUID, Long> SHOWCASE = new HashMap<>();
	private static @Nullable MinecraftServer server;

	static void setServer(@Nullable MinecraftServer value) {
		server = value;
	}

	// ================================================================== opening the template screen

	/** Opens the template screen ({@code OpenScreen.SECT_TEMPLATE}); {@code select} = the template to select first. */
	public static void openScreen(ServerPlayer player, int select) {
		BuildInfo.sendCatalog(player);
		Net.send(player, new Payloads.OpenScreen(Payloads.OpenScreen.SECT_TEMPLATE, Math.max(0, select)));
	}

	/** Why {@code player} may not preview {@code template} for founding now, or null. */
	static @Nullable Component blocker(ServerPlayer player, int template, Mode mode) {
		KultivatorConfig config = KultivatorConfig.get();
		UUID owner = player.getUUID();
		if (!config.sectConstruction) {
			return Component.translatable("kultivator.sectbuild.disabled");
		}
		if (!EngineApi.registered()) {
			return Component.translatable(EngineApi.UNAVAILABLE_KEY);
		}
		if (!SectTemplates.validAny(template)) {
			return Component.translatable("kultivator.sectbuild.template.unknown");
		}
		if (mode == Mode.SHOWCASE || mode == Mode.CHECK) {
			return null;
		}
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null) {
			return Component.translatable("kultivator.sectbuild.no_hall");
		}
		if (!hall.active()) {
			return Component.translatable("kultivator.sectbuild.hall_dormant");
		}
		if (mode == Mode.TEST) {
			return null;
		}
		if (SectTemplates.isSky(template) && mode == Mode.FOUND) {
			// Sekte Langit (DESIGN 37.3.3): its own realm gate and the rules of path A and B.
			return SectTemplates.rank(template) > config.sectBuildMaxTemplate
					? Component.translatable("kultivator.sectbuild.template.too_large", config.sectBuildMaxTemplate) : LangitRancang.blocker(player, template);
		}
		if (template > config.sectBuildMaxTemplate) {
			return Component.translatable("kultivator.sectbuild.template.too_large", config.sectBuildMaxTemplate);
		}
		if (config.sectTemplateRealmGate && Cultivators.get(player).realm().ordinal() < template - 1) {
			return Component.translatable("kultivator.sectbuild.template.realm_gate", SectTemplates.name(template),
					Component.translatable("kultivator.sectbuild.realm_hint." + template));
		}
		int current = EngineApi.get().template(owner);
		if (mode == Mode.FOUND && current > 0) {
			return Component.translatable("kultivator.sectbuild.template.already", SectTemplates.name(current));
		}
		if (mode == Mode.UPGRADE) {
			return Upgrade.blocker(owner, template);
		}
		return null;
	}

	/** The rotation whose gate faces the side of the stele where the player stands (DESIGN 21.12 step 4). */
	static int autoRotation(ServerPlayer player, BlockPos stele) {
		double dx = player.getX() - (stele.getX() + 0.5D);
		double dz = player.getZ() - (stele.getZ() + 0.5D);
		Direction side = Math.abs(dx) > Math.abs(dz) ? (dx > 0.0D ? Direction.EAST : Direction.WEST) : (dz < 0.0D ? Direction.NORTH : Direction.SOUTH);
		return SectTemplates.rotFor(side);
	}

	// ================================================================== previews

	/** A founding, upgrade or test preview at the player's stele; {@code rot} -1 = facing the player. */
	public static boolean preview(ServerPlayer player, int template, int rot, @Nullable Glaze glaze, Mode mode, boolean force) {
		Component why = blocker(player, template, mode);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.YELLOW));
			return false;
		}
		UUID owner = player.getUUID();
		EngineApi engine = EngineApi.get();
		Halls.HallView hall = Halls.hallOf(owner);
		Glaze chosen = glaze != null ? glaze : mode == Mode.UPGRADE && engine.site(owner) != null ? engine.site(owner).glaze() : Glaze.TINTA;
		int rotation = rot;
		if (mode == Mode.UPGRADE && engine.site(owner) != null) {
			rotation = engine.site(owner).rotation();
		} else if (rotation < 0) {
			rotation = hall == null ? 0 : autoRotation(player, hall.pos());
		}
		rotation = Math.floorMod(rotation, 4);
		cancel(player, false);
		// The engine needs "Kosongkan" for the preview (containers amber instead of red) and both flags for the new site.
		FlowData.applyAuthorization(owner);
		long ticket = mode == Mode.TEST ? engine.previewTest(player, template, rotation, chosen, force) : engine.preview(player, template, rotation, chosen);
		if (ticket == 0L) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.preview.refused").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		PENDING.put(owner, new Pending(owner, ticket, template, rotation, chosen, mode, tick(), 0, force, null));
		Msg.actionBar(player, Component.translatable("kultivator.sectbuild.preview.compiling", SectTemplates.name(template)).withStyle(ChatFormatting.AQUA));
		return true;
	}

	/**
	 * A sky design's preview (DESIGN 37.3.4, from {@code LangitRancang}): the throne at {@code yP}, shifted {@code dx, dz}
	 * from the ground stele; founding or test. The hologram, consent and confirm are the ground's.
	 */
	static boolean previewSky(ServerPlayer player, int sky, int rot, int yP, int dx, int dz, boolean test) {
		Mode mode = test ? Mode.TEST : Mode.FOUND;
		Component why = blocker(player, sky, mode);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.YELLOW));
			return false;
		}
		UUID owner = player.getUUID();
		int rotation = Math.floorMod(rot, 4);
		cancel(player, false);
		FlowData.applyAuthorization(owner);
		long ticket = EngineApi.get().previewSky(player, sky, rotation, Glaze.TINTA, yP, dx, dz, test);
		if (ticket == 0L) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.preview.refused").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		Pending pending = new Pending(owner, ticket, sky, rotation, Glaze.TINTA, mode, tick(), 0, test, null);
		pending.sky = true;
		PENDING.put(owner, pending);
		Msg.actionBar(player, Component.translatable("kultivator.sectbuild.preview.compiling", SectTemplates.name(sky)).withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** {@code uji sekte pamer} (a showcase at {@code origin}) and {@code uji sekte periksa} (the validator, then cancelled). */
	public static boolean previewAt(ServerPlayer player, int template, int tier, int rot, BlockPos origin, Mode mode) {
		Component why = blocker(player, template, mode);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.YELLOW));
			return false;
		}
		int rotation = Math.floorMod(rot < 0 ? SectTemplates.rotFor(player.getDirection().getOpposite()) : rot, 4);
		cancel(player, false);
		long ticket = EngineApi.get().showcase(player, template, Math.max(1, Math.min(SectTemplates.TIERS, tier)), rotation, origin);
		if (ticket == 0L) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.preview.refused").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		PENDING.put(player.getUUID(), new Pending(player.getUUID(), ticket, template, rotation, Glaze.TINTA, mode, tick(), tier, false, origin.immutable()));
		Msg.actionBar(player, Component.translatable("kultivator.sectbuild.preview.compiling", SectTemplates.name(template)).withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** "Putar": the same preview turned a quarter clockwise (the gate moves to the next side). */
	public static void rotate(ServerPlayer player) {
		Pending pending = PENDING.get(player.getUUID());
		if (pending == null) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.preview.none").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (pending.mode == Mode.UPGRADE) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.upgrade.same_rotation").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (pending.sky) {
			LangitRancang.turn(player);
			return;
		}
		int next = (pending.rot + 1) % 4;
		if (pending.mode == Mode.SHOWCASE || pending.mode == Mode.CHECK) {
			previewAt(player, pending.template, pending.tier, next, pending.origin == null ? player.blockPosition() : pending.origin, pending.mode);
		} else {
			preview(player, pending.template, next, pending.glaze, pending.mode, pending.force);
		}
	}

	/** "Batal": drops the preview and its hologram. */
	public static void cancel(ServerPlayer player, boolean tell) {
		Pending pending = PENDING.remove(player.getUUID());
		if (pending != null) {
			EngineApi.get().cancel(pending.ticket);
			Net.send(player, new Payloads.BuildPreview(new CompoundTag()));
			if (tell) {
				Msg.actionBar(player, Component.translatable("kultivator.sectbuild.preview.cancelled").withStyle(ChatFormatting.GRAY));
			}
		} else if (tell) {
			Net.send(player, new Payloads.BuildPreview(new CompoundTag()));
		}
	}

	/** The ticket of the player's current preview (0 when none). */
	public static long ticket(ServerPlayer player) {
		Pending pending = PENDING.get(player.getUUID());
		return pending == null ? 0L : pending.ticket;
	}

	// ================================================================== confirming

	/** The consent line and "[Ya, mulai bangun]" (DESIGN 21.12 step 6, the chat path). */
	public static void askConsent(ServerPlayer player) {
		Pending pending = PENDING.get(player.getUUID());
		if (pending == null) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.preview.none").withStyle(ChatFormatting.YELLOW));
			return;
		}
		PreviewData data = EngineApi.get().previewData(pending.ticket);
		Component why = notConfirmable(data);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.YELLOW));
			return;
		}
		Msg.chat(player, Component.translatable("kultivator.sectbuild.consent").withStyle(ChatFormatting.GOLD));
		Msg.chat(player, Ceremonies.button("kultivator.sectbuild.button.yes_build", "/kultivasi aula bangun konfirmasi ya"));
	}

	private static @Nullable Component notConfirmable(@Nullable PreviewData data) {
		if (data == null) {
			return Component.translatable("kultivator.sectbuild.preview.still_compiling");
		}
		if (data.snapshotPercent() < 100) {
			return Component.translatable("kultivator.sectbuild.preview.snapshot", data.snapshotPercent());
		}
		if (data.hasRed()) {
			return Component.translatable("kultivator.sectbuild.preview.red");
		}
		return null;
	}

	/** Confirms the player's preview ({@code ticket} 0 = whatever is pending). True when it started. */
	public static boolean confirm(ServerPlayer player, long ticket) {
		UUID self = player.getUUID();
		Pending pending = PENDING.get(self);
		if (pending == null || ticket != 0L && pending.ticket != ticket) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.preview.none").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		EngineApi engine = EngineApi.get();
		PreviewData data = engine.previewData(pending.ticket);
		Component why = notConfirmable(data);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (pending.mode == Mode.CHECK) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.check.ok").withStyle(ChatFormatting.GREEN));
			cancel(player, false);
			return true;
		}
		Component error;
		if (pending.mode == Mode.UPGRADE) {
			error = Upgrade.begin(player, self, pending.template, pending.ticket);
		} else {
			if (pending.mode == Mode.FOUND || pending.mode == Mode.TEST) {
				// The last real building scan, never a provided cap (a move guarantee would otherwise freeze forever, DESIGN 21.17).
				int frozen = Halls.scanCap(self);
				FlowData.setFrozenScanCap(self, frozen);
			}
			// Sekte Langit: the offering is taken first (all or nothing) and given back if the engine refuses.
			boolean offering = pending.sky && pending.mode == Mode.FOUND && !LangitSesaji.held(self);
			if (offering && !LangitSesaji.take(player, pending.template)) {
				return false;
			}
			error = engine.confirm(player, pending.ticket);
			if (error != null && offering) {
				LangitSesaji.refund(player);
			}
		}
		if (error != null) {
			Msg.chat(player, error.copy().withStyle(ChatFormatting.RED));
			return false;
		}
		if (pending.sky) {
			LangitRancang.forget(self);
		}
		PENDING.remove(self);
		Net.send(player, new Payloads.BuildPreview(new CompoundTag()));
		if (pending.mode == Mode.SHOWCASE) {
			SHOWCASE.put(self, pending.ticket);
		}
		if (pending.mode == Mode.FOUND || pending.mode == Mode.TEST || pending.mode == Mode.UPGRADE) {
			Halls.refreshBuildings(self);
		}
		long ops = data.toTag().getLongOr("ops", 0L);
		Kultivator.LOGGER.info("[Kultivator] Bangun: {} {} T1 -> {} blocks ({})", player.getName().getString(), SectTemplates.id(pending.template), ops,
				pending.mode.id());
		if (pending.mode != Mode.SHOWCASE) {
			Halls.log(self, Component.translatable("kultivator.sectbuild.log.confirmed", SectTemplates.name(pending.template), ops));
		}
		return true;
	}

	/** {@code pamer konfirmasi}: confirms the pending showcase preview. */
	public static boolean confirmShowcase(ServerPlayer player) {
		Pending pending = PENDING.get(player.getUUID());
		if (pending == null || pending.mode != Mode.SHOWCASE) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.preview.none").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		return confirm(player, pending.ticket);
	}

	// ================================================================== Ganti Templat (free before the first block)

	/** True while the owner's site has not placed a single block yet. */
	public static boolean freeChange(UUID owner) {
		EngineApi engine = EngineApi.get();
		if (engine.template(owner) <= 0 || engine.tier(owner) > 0) {
			return false;
		}
		for (ComponentView component : engine.components(owner)) {
			if (component.placed() > 0 || component.state() == ComponentView.STATE_DONE) {
				return false;
			}
		}
		return true;
	}

	/** "Ganti Templat": releases the untouched site and opens the template screen again. */
	public static void change(ServerPlayer player) {
		UUID owner = player.getUUID();
		if (!freeChange(owner)) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.change.not_free").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (!EngineApi.get().release(owner, true)) {
			Msg.chat(player, Component.translatable(EngineApi.UNAVAILABLE_KEY).withStyle(ChatFormatting.YELLOW));
			return;
		}
		FlowData.closeHistory(owner);
		Builders.releaseAll(owner);
		Halls.refreshBuildings(owner);
		Msg.chat(player, Component.translatable("kultivator.sectbuild.change.done").withStyle(ChatFormatting.AQUA));
		openScreen(player, 0);
	}

	// ================================================================== polling

	static void tick(MinecraftServer current) {
		if (PENDING.isEmpty() || current.getTickCount() % POLL_TICKS != 0) {
			return;
		}
		int now = current.getTickCount();
		for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
			Pending pending = it.next().getValue();
			ServerPlayer player = current.getPlayerList().getPlayer(pending.player);
			if (player == null || now - pending.created > PREVIEW_TICKS) {
				EngineApi.get().cancel(pending.ticket);
				it.remove();
				if (player != null) {
					Net.send(player, new Payloads.BuildPreview(new CompoundTag()));
					Msg.actionBar(player, Component.translatable("kultivator.sectbuild.preview.expired").withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			PreviewData data;
			try {
				data = EngineApi.get().previewData(pending.ticket);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Reading a sect preview failed", exception);
				data = null;
			}
			if (data == null) {
				continue;
			}
			int snap = data.snapshotPercent();
			boolean confirmable = data.confirmable();
			// Resend when the snapshot or the verdict changed; a merely new object at most every 5 s (small payloads).
			boolean changed = snap != pending.lastSnap || confirmable != pending.lastConfirmable;
			if (changed || data != pending.last && now - pending.lastSent >= RESEND_TICKS) {
				pending.last = data;
				pending.lastSnap = snap;
				pending.lastConfirmable = confirmable;
				pending.lastSent = now;
				send(player, pending, data, now);
				if (!pending.announced) {
					pending.announced = true;
					announce(player, pending, data);
					if (pending.mode == Mode.CHECK) {
						it.remove();
						EngineApi.get().cancel(pending.ticket);
					}
				}
			}
		}
	}

	/** The {@code BuildPreview} with the flow's keys added: ticket, stele, mode, the ticks left and the confirm state. */
	private static void send(ServerPlayer player, Pending pending, PreviewData data, int now) {
		CompoundTag tag = data.toTag();
		tag.putLong("ticket", pending.ticket);
		tag.putString("mode", pending.mode.id());
		tag.putInt("left", Math.max(0, PREVIEW_TICKS - (now - pending.created)));
		tag.putBoolean("confirmable", data.confirmable());
		if (!tag.contains("t")) {
			tag.putInt("t", pending.template);
		}
		if (!tag.contains("rot")) {
			tag.putInt("rot", pending.rot);
		}
		if (!tag.contains("glaze")) {
			tag.putString("glaze", pending.glaze.id());
		}
		Halls.HallView hall = Halls.hallOf(player.getUUID());
		BlockPos stele = pending.origin != null ? pending.origin : hall == null ? null : hall.pos();
		if (stele != null) {
			tag.putIntArray("stele", new int[]{stele.getX(), stele.getY(), stele.getZ()});
		}
		Net.send(player, new Payloads.BuildPreview(tag));
	}

	/** The chat summary of a ready preview with [Putar] [Konfirmasi] [Batal], and its warnings. */
	private static void announce(ServerPlayer player, Pending pending, PreviewData data) {
		CompoundTag tag = data.toTag();
		List<CompoundTag> warnings = new ArrayList<>();
		for (Tag each : tag.getListOrEmpty("warn")) {
			if (each instanceof CompoundTag warning) {
				warnings.add(warning);
			}
		}
		warnings.sort((a, b) -> Integer.compare(b.getIntOr("sev", 0), a.getIntOr("sev", 0)));
		Component gate = Component.translatable("kultivator.sectbuild.gate." + SectTemplates.gate(pending.rot).getSerializedName());
		Msg.chat(player, Component.translatable("kultivator.sectbuild.preview.ready", SectTemplates.name(pending.template), gate, warnings.size(),
				data.snapshotPercent()).withStyle(ChatFormatting.AQUA));
		for (int i = 0; i < Math.min(MAX_WARNING_LINES, warnings.size()); i++) {
			CompoundTag warning = warnings.get(i);
			int sev = warning.getIntOr("sev", PreviewData.SEV_NOTE);
			ChatFormatting color = sev >= PreviewData.SEV_RED ? ChatFormatting.RED : sev == PreviewData.SEV_AMBER ? ChatFormatting.GOLD : ChatFormatting.GRAY;
			player.sendSystemMessage(Component.literal(" • ").append(warningText(warning)).withStyle(color));
		}
		if (pending.mode == Mode.CHECK) {
			Msg.chat(player, Component.translatable(data.hasRed() ? "kultivator.sectbuild.check.red" : "kultivator.sectbuild.check.ok")
					.withStyle(data.hasRed() ? ChatFormatting.RED : ChatFormatting.GREEN));
			return;
		}
		MutableComponent buttons = Component.empty();
		if (pending.sky) {
			// Sekte Langit: height and the offering's bill beside the usual buttons (DESIGN 37.3.4).
			buttons.append(Ceremonies.button("kultivator.sectbuild.button.langit_naik_tinggi", "/kultivasi aula langit tinggi naik")).append(Component.literal(" "));
			buttons.append(Ceremonies.button("kultivator.sectbuild.button.langit_turun_tinggi", "/kultivasi aula langit tinggi turun")).append(Component.literal(" "));
			if (pending.mode == Mode.FOUND) {
				Msg.chat(player, Component.translatable("kultivator.sekte_langit.sesaji.tagihan", SectTemplates.name(pending.template),
						LangitSesaji.describe(LangitSesaji.bill(pending.template))).withStyle(ChatFormatting.GOLD));
			}
		}
		if (pending.mode != Mode.UPGRADE) {
			buttons.append(Ceremonies.button("kultivator.sectbuild.button.rotate", "/kultivasi aula bangun putar")).append(Component.literal(" "));
		}
		String confirm = pending.mode == Mode.SHOWCASE ? "/kultivasi uji sekte pamer konfirmasi" : "/kultivasi aula bangun konfirmasi";
		buttons.append(Ceremonies.button("kultivator.sectbuild.button.confirm", confirm)).append(Component.literal(" "));
		buttons.append(Ceremonies.button("kultivator.sectbuild.button.cancel", "/kultivasi aula bangun batal"));
		player.sendSystemMessage(buttons);
	}

	/** One warning of the preview ({@code key} with its {@code args}). */
	public static MutableComponent warningText(CompoundTag warning) {
		String key = warning.getStringOr("key", "");
		List<Object> args = new ArrayList<>();
		Tag raw = warning.get("args");
		if (raw instanceof ListTag list) {
			for (Tag arg : list) {
				args.add(arg.asString().orElse(arg.toString()));
			}
		} else if (raw != null) {
			args.add(raw.asString().orElse(raw.toString()));
		}
		return key.isEmpty() ? Component.literal("?") : Component.translatable(key, args.toArray());
	}

	static void forget(UUID player) {
		Pending pending = PENDING.remove(player);
		if (pending != null) {
			EngineApi.get().cancel(pending.ticket);
		}
	}

	static void clearSession() {
		PENDING.clear();
		SHOWCASE.clear();
		LangitRancang.clear();
	}

	private static int tick() {
		return server == null ? 0 : server.getTickCount();
	}

	private Founding() {
	}
}
