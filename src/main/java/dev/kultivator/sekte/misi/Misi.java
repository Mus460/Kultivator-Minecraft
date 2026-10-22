package dev.kultivator.sekte.misi;

import dev.kultivator.Kultivator;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.AksiSekte;
import dev.kultivator.sekte.api.Kepergian;
import dev.kultivator.sekte.api.MisiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Module X of Sekte yang Hidup, Misi luar &amp; kepulangan (DESIGN 36.2.7, butir 43 and 44; contracts C36.X1-X4): story
 * missions to the outside world beside the existing expedition board. A team of 1-4 disciples and a stance (Damai /
 * Tegas / Licik) leave on one of twelve templates for 1-3 days as pure data ({@code Halls.storeAway}, reason
 * {@link #REASON}); three stages are rolled on the way (news reaches the master), a failure turns the story instead of
 * ending it, and at the end the team walks in through the gate with youths, treasures, a found disciple or a treasure
 * birth's place, and hands over the Gulungan Laporan.
 * <p>
 * Server side only; the client half is {@code client.sekte.misi.MisiClient}. Work per tick: one pass every second over
 * the online masters' at most {@link MisiConfig#misiMaksAktif} missions (data only, DESIGN 36.3.9).
 * <p>
 * Sumber: <i>I Shall Seal the Heavens</i> ch. 19 (outer disciples go out and each brings back two or three talented
 * youths); the expeditions into the Magical Beast Mountain Range of <i>Battle Through the Heavens</i>.
 */
public final class Misi implements MisiService {

	/** The reason of X's away records ({@code Halls.storeAway}, {@code Halls.addReturnHandler}). */
	public static final String REASON = "sekte_misi";
	/** Scene beats of X (DESIGN 36.3.7: module index 6, beats 160-169): the team is home / a legendary homecoming. */
	public static final int BEAT_HOME = EpicIds.EXTRA_SEKTE_FIRST + 60;
	public static final int BEAT_LEGEND = EpicIds.EXTRA_SEKTE_FIRST + 61;

	static final Misi SERVICE = new Misi();
	private static boolean initialised;

	/** From {@code SekteContent.init} (both physical sides). */
	/** The item and the payload types exist whatever the switches say (registries and channels must match both sides, K1). */
	public static void registerContent() {
		GulunganLaporan.register();
		MisiPayloads.register();
	}

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		MisiConfig.load();
		registerContent();
		if (!SekteContent.on(SekteIds.X)) {
			return;
		}
		SekteApi.setMisi(SERVICE);
		Halls.addReturnHandler(REASON, Kepulangan::onReturn);
		ServerTickEvents.END_SERVER_TICK.register(Misi::tick);
		SekteBus.DISCIPLE_LEFT.register((server, owner, soul, name, how, toSect, companion) -> {
			if (how == Kepergian.MENGEMBARA || how == Kepergian.MINGGAT) {
				MisiData.addSearchable(owner, soul, name, how.id(), Ingatan.today(server));
			}
		});
		SekteBus.RIVAL_SECT_ACTION.register(Misi::onRivalAction);
		MisiCommands.init();
	}

	// ================================================================== MisiService (C36.X1)

	@Override
	public boolean away(UUID owner, UUID soul) {
		return owner != null && soul != null && MisiData.missionOf(owner, soul) != null;
	}

	@Override
	public List<UUID> awaySouls(UUID owner) {
		List<UUID> list = new ArrayList<>();
		if (owner == null) {
			return list;
		}
		for (CompoundTag mission : MisiData.active(owner)) {
			for (CompoundTag member : MisiData.team(mission)) {
				UUID soul = MisiData.soul(member);
				if (soul != null && !member.getBooleanOr("back", false)) {
					list.add(soul);
				}
			}
		}
		return list;
	}

	@Override
	public int finished(UUID owner) {
		return owner == null ? 0 : MisiData.section(owner).getIntOr("selesai", 0);
	}

	@Override
	public boolean carriesEnvoys() {
		return MisiConfig.get().aktif;
	}

	@Override
	public boolean sendEnvoy(ServerPlayer player, String sectId, UUID soul, boolean officer) {
		return player != null && sectId != null && soul != null && TimMisi.sendEnvoy(player, sectId, soul, officer);
	}

	// ================================================================== tick

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 7) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID owner = player.getUUID();
			if (Halls.hallOf(owner) == null) {
				continue;
			}
			try {
				tickOwner(server, owner, player);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Sekte misi tick failed for {}", owner, exception);
			}
		}
	}

	private static void tickOwner(MinecraftServer server, UUID owner, ServerPlayer master) {
		CompoundTag section = MisiData.section(owner);
		if (section.getListOrEmpty("aktif").isEmpty() && section.getListOrEmpty("datang").isEmpty()
				&& section.getListOrEmpty("tertunda").isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (CompoundTag mission : MisiData.active(owner)) {
			if (mission.getBooleanOr("finished", false)) {
				continue;
			}
			if (!mission.getBooleanOr("ditarik", false)) {
				long start = mission.getLongOr("start", now);
				long len = Math.max(60L, mission.getLongOr("len", 24000L));
				for (int stage = mission.getIntOr("done", 0); stage < 2; stage++) {
					if (now < start + (stage + 1) * len / 3L) {
						break;
					}
					CompoundTag rolled = JalanCerita.roll(owner, mission, stage, server.overworld().getRandom());
					news(server, owner, master, mission, stage, rolled);
				}
			}
			if (now >= mission.getLongOr("end", now)) {
				Kepulangan.finish(server, owner, mission);
			}
		}
		Kepulangan.tick(server, owner, master);
	}

	/** The stage news in the master's chat and the hall log. */
	private static void news(MinecraftServer server, UUID owner, @Nullable ServerPlayer master, CompoundTag mission, int stage, CompoundTag rolled) {
		TemplatMisi tpl = TemplatMisi.byId(mission.getStringOr("tpl", ""));
		if (tpl == null) {
			return;
		}
		boolean ok = rolled.getBooleanOr("ok", false);
		Component kind = Component.translatable(TemplatMisi.Jenis.byId(rolled.getStringOr("kind", "")).key());
		Component hurt = Component.empty();
		String hurtSoul = rolled.getStringOr("hurt", "");
		if (!hurtSoul.isEmpty()) {
			for (CompoundTag member : MisiData.team(mission)) {
				if (hurtSoul.equals(member.getStringOr("soul", ""))) {
					hurt = Component.translatable("kultivator.sekte.misi.kabar.luka", MisiData.name(member, "name"));
				}
			}
		}
		Component line = Component.translatable(ok ? "kultivator.sekte.misi.kabar.ok" : "kultivator.sekte.misi.kabar.gagal",
				Component.translatable(tpl.key()), stage + 1, kind, hurt);
		SekteLog.log(server, owner, SekteIds.X, line);
		if (master != null && MisiConfig.get().kabarTahap) {
			Msg.chat(master, line.copy().withStyle(ok ? ChatFormatting.DARK_AQUA : ChatFormatting.GOLD));
		}
	}

	/** Module D's ambush of a mission team ({@code SERGAP_MISI}): the next open stage becomes a fight against that sect. */
	private static void onRivalAction(MinecraftServer server, @Nullable UUID owner, String sectId, AksiSekte action, int magnitude) {
		if (owner == null || action != AksiSekte.SERGAP_MISI) {
			return;
		}
		for (CompoundTag mission : MisiData.active(owner)) {
			int next = mission.getIntOr("done", 0);
			if (mission.getBooleanOr("finished", false) || mission.getBooleanOr("ditarik", false) || next > 2 || mission.contains("amb_stage")) {
				continue;
			}
			mission.putInt("amb_stage", next);
			mission.putInt("amb_mag", Math.max(1, Math.min(9, magnitude)));
			if (!mission.contains("sect")) {
				mission.putString("sect", sectId);
				Component name = SekteApi.diplomasi().name(sectId);
				MisiData.putName(mission, "sect_name", name != null ? name : Component.literal(sectId));
			}
			MisiData.dirty();
			ServerPlayer master = server.getPlayerList().getPlayer(owner);
			if (master != null) {
				TemplatMisi tpl = TemplatMisi.byId(mission.getStringOr("tpl", ""));
				Msg.chat(master, Component.translatable("kultivator.sekte.misi.kabar.sergap",
						tpl == null ? Component.literal("?") : Component.translatable(tpl.key()), MisiData.name(mission, "sect_name"))
						.withStyle(ChatFormatting.RED));
			}
			return;
		}
	}

	// ================================================================== client actions

	static void onAction(ServerPlayer player, MisiPayloads.Aksi aksi) {
		UUID owner = player.getUUID();
		MinecraftServer server = player.level().getServer();
		if (!SekteContent.on(SekteIds.X)) {
			return;
		}
		CompoundTag preview = null;
		try {
			switch (aksi.op()) {
				case "cek" -> preview = TimMisi.preview(server, owner, aksi.a(), stance(aksi.text()), souls(aksi.text()));
				case "kirim" -> TimMisi.send(player, aksi.a(), stance(aksi.text()), souls(aksi.text()));
				case "tarik" -> TimMisi.recall(player, aksi.a(), true);
				case "tinggal" -> TimMisi.recall(player, aksi.a(), false);
				case "baca" -> {
					List<CompoundTag> history = MisiData.history(owner);
					if (aksi.a() >= 0 && aksi.a() < history.size()) {
						MisiPayloads.sendReport(player, history.get(aksi.a()));
					}
					return;
				}
				default -> {
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Sekte misi action {} failed", aksi.op(), exception);
		}
		MisiPayloads.sendState(player, state(server, owner, preview));
	}

	/** {@code "<stance>|<soul>,<soul>..."}. */
	private static TemplatMisi.Pendekatan stance(String text) {
		int bar = text == null ? -1 : text.indexOf('|');
		return TemplatMisi.Pendekatan.byId(bar < 0 ? text : text.substring(0, bar));
	}

	private static List<UUID> souls(String text) {
		List<UUID> list = new ArrayList<>();
		int bar = text == null ? -1 : text.indexOf('|');
		if (bar < 0) {
			return list;
		}
		for (String part : text.substring(bar + 1).split(",")) {
			UUID soul = MisiData.uuid(part.trim());
			if (soul != null && !list.contains(soul) && list.size() < 4) {
				list.add(soul);
			}
		}
		return list;
	}

	/** Everything the two story tabs show (always the player's own hall). */
	static CompoundTag state(MinecraftServer server, UUID owner, @Nullable CompoundTag preview) {
		CompoundTag state = new CompoundTag();
		MisiConfig cfg = MisiConfig.get();
		Halls.HallView hall = Halls.hallOf(owner);
		state.putBoolean("hall", hall != null && hall.active());
		state.putBoolean("on", cfg.aktif);
		state.putInt("maks", cfg.misiMaksAktif);
		state.putInt("tim", cfg.timMaks);
		state.putLong("day", Ingatan.today(server));
		state.putInt("selesai", SERVICE.finished(owner));
		if (hall == null) {
			return state;
		}
		ListTag offers = new ListTag();
		ListTag board = TimMisi.board(server, owner);
		for (int i = 0; i < board.size(); i++) {
			if (!(board.get(i) instanceof CompoundTag offer)) {
				continue;
			}
			CompoundTag view = offer.copy();
			view.putInt("i", i);
			TemplatMisi tpl = TemplatMisi.byId(offer.getStringOr("tpl", ""));
			if (tpl != null) {
				view.putInt("days", (int) (tpl.ticks() / 24000L));
				view.putInt("price", tpl.has(TemplatMisi.Flags.AUCTION) ? TimMisi.AUCTION_PK_PER_TIER * offer.getIntOr("tier", 1) : 0);
			}
			view.remove("target");
			offers.add(view);
		}
		state.put("papan", offers);
		ListTag running = new ListTag();
		for (CompoundTag mission : MisiData.active(owner)) {
			CompoundTag view = new CompoundTag();
			for (String key : new String[]{"tpl", "sikap"}) {
				view.putString(key, mission.getStringOr(key, ""));
			}
			view.putInt("id", mission.getIntOr("id", 0));
			view.putInt("tier", mission.getIntOr("tier", 1));
			view.putLong("start", mission.getLongOr("start", 0L));
			view.putLong("end", mission.getLongOr("end", 0L));
			view.putBoolean("ditarik", mission.getBooleanOr("ditarik", false));
			view.putBoolean("finished", mission.getBooleanOr("finished", false));
			view.putBoolean("amb", mission.contains("amb_stage") && mission.getIntOr("amb_stage", 0) >= mission.getIntOr("done", 0));
			if (mission.contains("sect_name")) {
				MisiData.putName(view, "sect_name", MisiData.name(mission, "sect_name"));
			}
			ListTag team = new ListTag();
			for (CompoundTag member : MisiData.team(mission)) {
				CompoundTag entry = new CompoundTag();
				MisiData.putName(entry, "name", MisiData.name(member, "name"));
				entry.putBoolean("hurt", member.getIntOr("hurt_at", -1) >= 0);
				entry.putBoolean("left", member.getBooleanOr("left", false));
				entry.putBoolean("back", member.getBooleanOr("back", false));
				entry.putInt("color", member.getIntOr("color", 0xFFFFFF));
				team.add(entry);
			}
			view.put("team", team);
			view.put("stages", mission.getListOrEmpty("stages").copy());
			running.add(view);
		}
		state.put("aktif", running);
		ListTag history = new ListTag();
		List<CompoundTag> reports = MisiData.history(owner);
		for (int i = reports.size() - 1; i >= 0; i--) {
			CompoundTag report = reports.get(i);
			CompoundTag view = new CompoundTag();
			view.putInt("i", i);
			view.putString("tpl", report.getStringOr("tpl", ""));
			view.putInt("tier", report.getIntOr("tier", 1));
			view.putInt("grade", report.getIntOr("grade", 0));
			view.putLong("day", report.getLongOr("day", 0L));
			view.putInt("recruits", report.getIntOr("recruits", 0));
			view.putInt("treasures", report.getIntOr("treasures", 0));
			view.putBoolean("found", report.getBooleanOr("found", false));
			ListTag team = report.getListOrEmpty("team");
			if (!team.isEmpty() && team.get(0) instanceof CompoundTag leader) {
				MisiData.putName(view, "leader", MisiData.name(leader, "name"));
			}
			history.add(view);
		}
		state.put("riwayat", history);
		state.putInt("dicari", MisiData.searchable(owner).size());
		if (preview != null) {
			state.put("pratinjau", preview);
		}
		return state;
	}

	private Misi() {
	}
}
