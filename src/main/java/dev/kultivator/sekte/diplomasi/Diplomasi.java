package dev.kultivator.sekte.diplomasi;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteConfig;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.AksiSekte;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.Kepergian;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.util.Advancements;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Module D, Diplomasi &amp; Sekte Rival (DESIGN 36.2.9, 36.4 D; ideas 48 and 49): the init of the module and the
 * {@link DiplomasiService} it gives the other modules (contract C36.D1).
 * <p>
 * Eight seeded NPC sects (config {@code diplomasi.jumlahSekte}) live as data ({@link DiplomasiData}): they grow one sect
 * per {@code simulasiDetik} seconds ({@link Simulasi}), feud with each other, and send each player hall up to
 * {@code peristiwaPerHari} events a day by their stance: envoys, gifts, challenges, raids on the existing
 * {@code musuh.SectRaids} schedule, mission ambushes for module X, disciple poaching for module P, war. The player answers
 * from the Diplomasi screen ({@link Aksi}): envoys, gifts, pacts, tribute, challenges, war and peace. A war ends in
 * peace, in the defeat of the hall (three sacked raids: forced tribute, never blocks or disciples) or in the defeat of the
 * enemy's sect master at its mountain gate ({@link KetuaSekte}). The gates themselves are world generation only, in new
 * chunks ({@link GerbangSekte}); their members are capped temporary bodies ({@link AnggotaSekte}).
 * <p>
 * Sumber: the Seven Great Sects of the State of Yue standing together against the Devil Dao invasion (<i>A Record of a
 * Mortal's Journey</i>); Xiao Yan's three-year promise to the Misty Cloud Sect, a feud settled by an appointed duel
 * (<i>Battle Through the Heavens</i>).
 */
public final class Diplomasi implements DiplomasiService {

	public static final Diplomasi SERVICE = new Diplomasi();

	private static DiplomasiConfig config = new DiplomasiConfig();
	private static @Nullable MinecraftServer server;
	private static boolean initialised;

	/** From {@code SekteContent.init()} (one line). */
	private static boolean content;

	/** Registry content (K1): the gate placement type and the payload types, always, whatever the switches say. */
	public static void registerContent() {
		if (content) {
			return;
		}
		content = true;
		GerbangSekte.registerContent();
		DiplomasiPayloads.register();
	}

	public static void init() {
		registerContent();
		if (initialised || !SekteContent.on(SekteIds.D)) {
			return;
		}
		initialised = true;
		config = SekteConfig.section(SekteIds.D, DiplomasiConfig.class, DiplomasiConfig::new, DiplomasiConfig::clamp);
		GerbangPlan.configure(config.jumlahSekte, config.jarakMin, config.strukturAktif);
		SekteConfig.onReload(() -> {
			GerbangPlan.configure(config.jumlahSekte, config.jarakMin, config.strukturAktif);
			if (server != null) {
				Simulasi.keepPlanOf(server);
			}
		});
		GerbangSekte.register(config.strukturAktif);
		DiplomasiCommands.register();
		Utusan.init();
		Perang.init();
		KetuaSekte.init();
		SekteApi.setDiplomasi(SERVICE);
		listen();
		UseEntityCallback.EVENT.register(Diplomasi::onUse);
		ServerLifecycleEvents.SERVER_STARTED.register(started -> {
			server = started;
			Simulasi.ensureFounded(started);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
			Utusan.clear();
			AnggotaSekte.clear();
			KetuaSekte.clear();
			Perang.clear();
			Simulasi.clear();
			server = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(Simulasi::tick);
	}

	public static DiplomasiConfig config() {
		return config;
	}

	public static @Nullable MinecraftServer server() {
		return server;
	}

	/** The world day (overworld clock / 24 000), the same day as {@code sekte.Ingatan}. */
	public static long today(MinecraftServer server) {
		return Ingatan.today(server);
	}

	// ================================================================== bus listeners (contract C36.D2)

	private static void listen() {
		// A disciple defected to an NPC sect (module P decides who and where): the sect takes it in.
		SekteBus.DISCIPLE_LEFT.register((srv, owner, soul, name, how, toSect, companion) -> {
			if (how != Kepergian.BERKHIANAT) {
				return;
			}
			DiplomasiData data = DiplomasiData.get(srv);
			SekteNpc sect = data.sect(toSect);
			if (sect == null) {
				sect = Simulasi.mostHostile(data, owner);
			}
			if (sect == null) {
				return;
			}
			sect.addPembelot(soul, name);
			Relasi r = data.relasi(owner, sect);
			r.note(today(srv), Component.translatable(GeneratorNama.KEY + "kronik.pembelot", name));
			Standing.adjust(srv, owner, sect, -5, "pembelot");
			data.setDirty();
		});
		// An inter-sect tournament hosted by module T: a win earns respect, a loss is looked down upon.
		SekteBus.TOURNAMENT_ENDED.register((srv, owner, result) -> {
			if (!"antar_sekte".equals(result.kind()) || result.guestSect() == null) {
				return;
			}
			DiplomasiData data = DiplomasiData.get(srv);
			SekteNpc sect = data.sect(result.guestSect());
			if (sect != null && data.once(owner, sect.id, SekteIds.T, today(srv))) {
				Standing.adjust(srv, owner, sect, result.guestWon() ? -10 : 10, result.guestWon() ? "turnamen_kalah" : "turnamen_menang");
				meet(srv, owner, sect);
			}
		});
		// A mission of module X met the sect on the road.
		SekteBus.MISSION_RETURNED.register((srv, owner, result) -> {
			DiplomasiData data = DiplomasiData.get(srv);
			SekteNpc sect = data.sect(result.rivalSect());
			if (sect == null) {
				return;
			}
			meet(srv, owner, sect);
			if (!data.once(owner, sect.id, SekteIds.X, today(srv))) {
				return;
			}
			if (result.success() >= 2) {
				Standing.adjust(srv, owner, sect, 5, "misi_baik");
			} else if (result.success() == 0) {
				Standing.adjust(srv, owner, sect, -5, "misi_buruk");
			}
		});
		// The founder became the Leluhur Pendiri: every sect honours the new generation once (+10).
		SekteBus.SUCCESSION.register((srv, owner, newMaster) -> {
			DiplomasiData data = DiplomasiData.get(srv);
			if (data.hall(owner).getBooleanOr("leluhur", false)) {
				return;
			}
			data.hall(owner).putBoolean("leluhur", true);
			for (SekteNpc sect : data.sects()) {
				Standing.adjust(srv, owner, sect, Standing.LELUHUR, "leluhur");
			}
			data.setDirty();
		});
	}

	/** First contact of a hall with a sect (gate visit, envoy, mission, tournament): grants {@code diplomasi_temu}. */
	static void meet(MinecraftServer srv, UUID owner, SekteNpc sect) {
		DiplomasiData data = DiplomasiData.get(srv);
		Relasi r = data.relasi(owner, sect);
		if (!r.ditemui) {
			r.ditemui = true;
			r.note(today(srv), Component.translatable(GeneratorNama.KEY + "kronik.temu"));
			SekteLog.log(srv, owner, SekteIds.D, Component.translatable(GeneratorNama.KEY + "log.temu", sect.name()));
			data.setDirty();
		}
		ServerPlayer player = srv.getPlayerList().getPlayer(owner);
		if (player != null) {
			Advancements.grant(player, SekteIds.ADVANCEMENT_FOLDER + "diplomasi_temu");
			if (sect.gateBorn()) {
				// T12: a met sect's gate is on the player's map (the site AnggotaSekte published when the gate was born).
				dev.kultivator.dunia.DuniaSites.discover(player,
						dev.kultivator.dunia.DuniaSites.idOf(srv.overworld().dimension(), sect.gate(), AnggotaSekte.SITE_KIND));
			}
		}
	}

	// ================================================================== interaction with the module's bodies

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !(entity instanceof DiscipleEntity body)
				|| body.role() != DiscipleEntity.Role.RIVAL) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		if (Utusan.interact(sp, body) || KetuaSekte.interact(sp, body) || AnggotaSekte.interact(sp, body)) {
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	// ================================================================== strength

	/**
	 * The hall's strength on the sects' scale ({@code tier × 100 + resources}): hall level × 100, plus the realms of its
	 * disciples in the world (up to twelve, realm ordinal × 12 each), plus the master's realm × 20 while online.
	 */
	static int kekuatanAula(MinecraftServer srv, UUID owner) {
		int strength = Halls.level(owner) * 100;
		int counted = 0;
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (counted++ >= 12) {
				break;
			}
			strength += disciple.data().realm().ordinal() * 12;
		}
		ServerPlayer player = srv.getPlayerList().getPlayer(owner);
		if (player != null) {
			strength += Cultivators.get(player).realm().ordinal() * 20;
		}
		return strength;
	}

	// ================================================================== DiplomasiService (C36.D1)

	@Override
	public List<String> sects() {
		MinecraftServer srv = server;
		if (srv == null) {
			return List.of();
		}
		List<String> ids = new ArrayList<>();
		for (SekteNpc sect : DiplomasiData.get(srv).sects()) {
			ids.add(sect.id);
		}
		return ids;
	}

	@Override
	public @Nullable Component name(String sectId) {
		SekteNpc sect = find(sectId);
		return sect == null ? null : sect.name();
	}

	@Override
	public Lambang emblem(String sectId) {
		SekteNpc sect = find(sectId);
		return sect == null ? Lambang.DEFAULT : sect.emblem();
	}

	@Override
	public int tier(String sectId) {
		SekteNpc sect = find(sectId);
		return sect == null ? 0 : sect.tier;
	}

	@Override
	public int standing(UUID owner, String sectId) {
		SekteNpc sect = find(sectId);
		MinecraftServer srv = server;
		if (sect == null || srv == null || owner == null) {
			return 0;
		}
		Relasi r = DiplomasiData.get(srv).peek(owner, sect.id);
		return r == null ? Standing.awal(sect) : r.standing;
	}

	@Override
	public Sikap stance(UUID owner, String sectId) {
		SekteNpc sect = find(sectId);
		MinecraftServer srv = server;
		if (sect == null || srv == null || owner == null) {
			return Sikap.NETRAL;
		}
		Relasi r = DiplomasiData.get(srv).peek(owner, sect.id);
		return r == null ? Sikap.of(Standing.awal(sect), false) : r.sikap();
	}

	@Override
	public void adjust(UUID owner, String sectId, int delta, String reason) {
		SekteNpc sect = find(sectId);
		MinecraftServer srv = server;
		if (sect == null || srv == null || owner == null || delta == 0) {
			return;
		}
		// Other modules' reasons are {@code <module>.<what>}; one change per module, sect and day (the bus event of the same
		// moment counts once with it).
		String module = reason == null ? "" : reason.contains(".") ? reason.substring(0, reason.indexOf('.')) : reason;
		if (DiplomasiData.get(srv).once(owner, sect.id, module, today(srv))) {
			Standing.adjust(srv, owner, sect, Math.max(-20, Math.min(20, delta)), "modul");
		}
	}

	@Override
	public @Nullable BlockPos gate(String sectId) {
		SekteNpc sect = find(sectId);
		return sect == null || !sect.gateBorn() ? null : sect.gate();
	}

	@Override
	public void meet(UUID owner, String sectId) {
		SekteNpc sect = find(sectId);
		MinecraftServer srv = server;
		if (sect != null && srv != null && owner != null) {
			meet(srv, owner, sect);
		}
	}

	@Override
	public boolean met(UUID owner, String sectId) {
		SekteNpc sect = find(sectId);
		MinecraftServer srv = server;
		if (sect == null || srv == null || owner == null) {
			return false;
		}
		Relasi r = DiplomasiData.get(srv).peek(owner, sect.id);
		return r != null && r.ditemui;
	}

	@Override
	public @Nullable BlockPos plannedGate(String sectId) {
		SekteNpc sect = find(sectId);
		return sect == null ? null : new BlockPos(sect.blockX, 0, sect.blockZ);
	}

	private static @Nullable SekteNpc find(@Nullable String sectId) {
		MinecraftServer srv = server;
		return srv == null || sectId == null ? null : DiplomasiData.get(srv).sect(sectId);
	}

	/** Fires {@code RIVAL_SECT_ACTION} (every NPC sect action goes through here, contract C36.D2). */
	static void fire(MinecraftServer srv, @Nullable UUID owner, SekteNpc sect, AksiSekte action) {
		SekteBus.rivalAction(srv, owner, sect.id, action, sect.tier);
	}

	/** A line to the owner (chat, coloured) when online. */
	static void tell(MinecraftServer srv, UUID owner, Component line, ChatFormatting color) {
		ServerPlayer player = srv.getPlayerList().getPlayer(owner);
		if (player != null) {
			player.sendSystemMessage(line.copy().withStyle(color));
		}
	}

	static void log(MinecraftServer srv, UUID owner, Component line) {
		SekteLog.log(srv, owner, SekteIds.D, line);
	}

	static void warn(String what, Throwable error) {
		Kultivator.LOGGER.error("Sekte diplomasi: {}", what, error);
	}

	private Diplomasi() {
	}
}
