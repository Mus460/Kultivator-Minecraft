package dev.kultivator.sect.build.flow;

import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Moving, dismantling and letting go of a built sect (DESIGN 21.18, 21.17 "Stele moved"):
 * <ul>
 * <li><b>The 6b move dialog</b>: with a template, re-placing the stele anywhere but its anchor cell (or sneak-using a second
 * stele) asks "Pindah &amp; bongkar" (the old site dismantles with a refund whenever the owner is near) or "Pindah &amp;
 * tinggalkan" (the old buildings stay as ordinary blocks). Both use module A's move rules (one-day cooldown, 128 blocks)
 * through {@code Halls.relocateHall} and give {@code guaranteeTier} = the old tier for 10 in-game days or until reached;
 * the new site then goes through founding. Mining the stele makes the hall dormant and pauses construction; re-placing it
 * on the anchor resumes.</li>
 * <li><b>Bongkar</b> (bangunan keeps the landform, total also removes paving and walls) and <b>Lepaskan Templat</b> each
 * ask for a second "ya" within 30 s.</li>
 * </ul>
 * Source: the ruined terrace that can be rebuilt, <i>Perfect World</i> ch. 551/558 (the ruined Heaven Mending Pavilion).
 */
public final class Relocation implements Halls.SteleMoveListener {

	public static final Relocation INSTANCE = new Relocation();
	/** A move dialog waits this long for a choice (ticks). */
	public static final int MOVE_TICKS = 1200;
	/** A dismantle or abandon waits this long for its "ya" (ticks). */
	public static final int CONFIRM_TICKS = 600;
	public static final int MOVE_GUARANTEE_DAYS = 10;

	private record PendingMove(ResourceKey<Level> dim, BlockPos to, int deadline) {
	}

	private record PendingConfirm(String action, int deadline) {
	}

	private static final Map<UUID, PendingMove> MOVES = new HashMap<>();
	private static final Map<UUID, PendingConfirm> CONFIRMS = new HashMap<>();
	private static @Nullable MinecraftServer server;

	static void setServer(@Nullable MinecraftServer value) {
		server = value;
	}

	// ================================================================== SteleMoveListener

	@Override
	public boolean beforeMove(ServerPlayer player, UUID owner, ServerLevel level, @Nullable BlockPos from, BlockPos to, boolean wake) {
		EngineApi engine = EngineApi.get();
		if (engine.template(owner) <= 0 || !player.getUUID().equals(owner)) {
			return false;
		}
		SiteView site = engine.site(owner);
		if (site != null && wake && site.stele().equals(to) && site.dim().equals(level.dimension())) {
			// Back on its anchor cell: module A wakes the hall and construction resumes.
			return false;
		}
		MOVES.put(owner, new PendingMove(level.dimension(), to.immutable(), tick() + MOVE_TICKS));
		Msg.chat(player, Component.translatable("kultivator.sectbuild.move.ask", SectTemplates.name(engine.template(owner)), engine.tier(owner))
				.withStyle(ChatFormatting.GOLD));
		MutableComponent buttons = Component.empty();
		buttons.append(Ceremonies.button("kultivator.sectbuild.button.move_dismantle", "/kultivasi aula bangun pindah bongkar"));
		buttons.append(Component.literal(" "));
		buttons.append(Ceremonies.button("kultivator.sectbuild.button.move_leave", "/kultivasi aula bangun pindah tinggalkan"));
		buttons.append(Component.literal(" "));
		buttons.append(Ceremonies.button("kultivator.sectbuild.button.cancel", "/kultivasi aula bangun pindah batal"));
		player.sendSystemMessage(buttons);
		return true;
	}

	@Override
	public void onRemoved(UUID owner, @Nullable ServerLevel level, @Nullable BlockPos pos) {
		if (EngineApi.get().template(owner) <= 0) {
			return;
		}
		ServerPlayer master = Ceremonies.online(owner);
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.sectbuild.stele_removed").withStyle(ChatFormatting.YELLOW));
		}
	}

	/** The owner's choice in the move dialog. */
	public static void choose(ServerPlayer player, boolean dismantleOld) {
		UUID owner = player.getUUID();
		PendingMove move = MOVES.remove(owner);
		if (move == null || tick() > move.deadline() || server == null) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.move.none").withStyle(ChatFormatting.YELLOW));
			return;
		}
		ServerLevel level = server.getLevel(move.dim());
		if (level == null) {
			return;
		}
		EngineApi engine = EngineApi.get();
		int oldTier = Math.max(engine.tier(owner), FlowData.guaranteeTier(owner));
		int oldTemplate = engine.template(owner);
		if (!Halls.relocateHall(player, level, move.to())) {
			return;
		}
		Component error = engine.relocate(owner, move.to(), dismantleOld);
		if (error != null) {
			Msg.chat(player, error.copy().withStyle(ChatFormatting.RED));
			return;
		}
		if (oldTier > 0) {
			FlowData.setGuarantee(owner, oldTier, FlowData.day() + MOVE_GUARANTEE_DAYS, "move");
		}
		FlowData.closeHistory(owner);
		Builders.releaseAll(owner);
		Halls.log(owner, Component.translatable(dismantleOld ? "kultivator.sectbuild.log.move_dismantle" : "kultivator.sectbuild.log.move_leave",
				SectTemplates.name(oldTemplate)));
		Halls.refreshBuildings(owner);
		Msg.chat(player, Component.translatable(dismantleOld ? "kultivator.sectbuild.move.dismantle" : "kultivator.sectbuild.move.leave", oldTier,
				MOVE_GUARANTEE_DAYS).withStyle(ChatFormatting.GOLD));
		Founding.openScreen(player, oldTemplate);
	}

	public static void cancelMove(ServerPlayer player) {
		if (MOVES.remove(player.getUUID()) != null) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.move.cancelled").withStyle(ChatFormatting.GRAY));
		}
	}

	// ================================================================== dismantle and abandon

	/** "Bongkar bangunan" / "Bongkar total": asks first, acts on the second call with {@code yes}. */
	public static void deconstruct(ServerPlayer player, boolean total, boolean yes) {
		UUID owner = player.getUUID();
		if (EngineApi.get().template(owner) <= 0) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.upgrade.no_template").withStyle(ChatFormatting.YELLOW));
			return;
		}
		String action = total ? "deconstruct_total" : "deconstruct";
		if (!yes || !confirmed(owner, action)) {
			CONFIRMS.put(owner, new PendingConfirm(action, tick() + CONFIRM_TICKS));
			Msg.chat(player, Component.translatable(total ? "kultivator.sectbuild.deconstruct.ask_total" : "kultivator.sectbuild.deconstruct.ask")
					.withStyle(ChatFormatting.GOLD));
			player.sendSystemMessage(Ceremonies.button("kultivator.sectbuild.button.yes_dismantle",
					"/kultivasi aula bangun bongkar " + (total ? "total" : "bangunan") + " ya"));
			return;
		}
		CONFIRMS.remove(owner);
		if (!EngineApi.get().deconstruct(owner, total)) {
			Msg.chat(player, Component.translatable(EngineApi.UNAVAILABLE_KEY).withStyle(ChatFormatting.YELLOW));
			return;
		}
		Builders.releaseAll(owner);
		Halls.log(owner, Component.translatable("kultivator.sectbuild.log.deconstruct"));
		Msg.chat(player, Component.translatable("kultivator.sectbuild.deconstruct.started").withStyle(ChatFormatting.GOLD));
	}

	/** "Lepaskan Templat": the buildings stay as ordinary blocks and the hall returns to the scan. */
	public static void abandon(ServerPlayer player, boolean yes) {
		UUID owner = player.getUUID();
		EngineApi engine = EngineApi.get();
		if (engine.template(owner) <= 0) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.upgrade.no_template").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (!yes || !confirmed(owner, "abandon")) {
			CONFIRMS.put(owner, new PendingConfirm("abandon", tick() + CONFIRM_TICKS));
			Msg.chat(player, Component.translatable("kultivator.sectbuild.abandon.ask").withStyle(ChatFormatting.GOLD));
			player.sendSystemMessage(Ceremonies.button("kultivator.sectbuild.button.yes_abandon", "/kultivasi aula bangun lepaskan ya"));
			return;
		}
		CONFIRMS.remove(owner);
		int template = engine.template(owner);
		if (!engine.release(owner, true)) {
			Msg.chat(player, Component.translatable(EngineApi.UNAVAILABLE_KEY).withStyle(ChatFormatting.YELLOW));
			return;
		}
		FlowData.closeHistory(owner);
		FlowData.clearGuarantee(owner);
		Builders.releaseAll(owner);
		Halls.log(owner, Component.translatable("kultivator.sectbuild.log.abandon", SectTemplates.name(template)));
		Halls.refreshBuildings(owner);
		Msg.chat(player, Component.translatable("kultivator.sectbuild.abandon.done").withStyle(ChatFormatting.GOLD));
	}

	/** From the Templat panel, whose jade coin already asked for a second click: dismantles at once. */
	public static void deconstructNow(ServerPlayer player, boolean total) {
		CONFIRMS.put(player.getUUID(), new PendingConfirm(total ? "deconstruct_total" : "deconstruct", tick() + CONFIRM_TICKS));
		deconstruct(player, total, true);
	}

	/** From the Templat panel, whose jade coin already asked for a second click: lets the template go at once. */
	public static void abandonNow(ServerPlayer player) {
		CONFIRMS.put(player.getUUID(), new PendingConfirm("abandon", tick() + CONFIRM_TICKS));
		abandon(player, true);
	}

	private static boolean confirmed(UUID owner, String action) {
		PendingConfirm pending = CONFIRMS.get(owner);
		return pending != null && pending.action().equals(action) && tick() <= pending.deadline();
	}

	static void forget(UUID player) {
		MOVES.remove(player);
		CONFIRMS.remove(player);
	}

	static void clearSession() {
		MOVES.clear();
		CONFIRMS.clear();
	}

	private static int tick() {
		return server == null ? 0 : server.getTickCount();
	}

	private Relocation() {
	}
}
