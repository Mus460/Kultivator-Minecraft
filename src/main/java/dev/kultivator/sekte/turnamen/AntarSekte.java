package dev.kultivator.sekte.turnamen;

import dev.kultivator.sect.event.Tournament;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The inter-sect tournament (DESIGN 36.2.3 "antar-sekte", contract C36.T2): an NPC sect of module D challenges the hall
 * ({@code SekteBus.RIVAL_SECT_ACTION} with {@code TANTANGAN}); the master accepts or refuses with a click in chat, the hub
 * card or {@code /kultivasi perguruan turnamen terima|tolak}. Accepted, the sect sends {@link Tournament#GUESTS} of its
 * disciples (bodies of role RIVAL, like the old visiting champions) into the bracket, two of its envoys watch from the
 * tribune, and the result moves the standing between the hall and the sect by 10: winning earns their respect (+10),
 * losing makes them look down on the hall (-10). Refusing costs 3; an unanswered challenge lapses after
 * {@link #LAPSE_DAYS} days without a cost. The old tournament's rewards and schedule are unchanged (an inter-sect
 * tournament counts as one competition).
 * <p>
 * Sumber: the three-year agreement of Xiao Yan and the Misty Cloud Sect (<i>Battle Through the Heavens</i>), a contest
 * between sects settled before witnesses; the lei tai, an open challenge stage.
 */
final class AntarSekte {

	public static final int LAPSE_DAYS = 3;
	public static final int STANDING = 10;
	public static final int REFUSE_COST = 3;

	/** The sect whose visitors the next {@link Tournament#startNow} brings (read by the start hook). */
	private static final Map<UUID, String> HOSTING = new HashMap<>();

	/** Module D's challenge arrived (or the test command). */
	static void challenge(MinecraftServer server, UUID owner, String sectId) {
		if (!TurnamenConfig.get().antarSekte || sectId == null || sectId.isBlank()) {
			return;
		}
		if (Tournament.running(owner) && Tournament.KIND_GUESTS.equals(Tournament.runningKind(owner))) {
			return;
		}
		Musim.setPending(owner, sectId, Ingatan.today(server));
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		Component name = sectName(sectId);
		SekteLog.log(server, owner, SekteIds.T, Component.translatable("kultivator.sekte.turnamen.log.tantangan", name));
		if (player != null) {
			MutableComponent line = Component.translatable("kultivator.sekte.turnamen.antar.tantangan", name).withStyle(ChatFormatting.GOLD);
			line.append(" ").append(button("kultivator.sekte.turnamen.antar.terima", "/kultivasi perguruan turnamen terima", ChatFormatting.GREEN));
			line.append(" ").append(button("kultivator.sekte.turnamen.antar.tolak", "/kultivasi perguruan turnamen tolak", ChatFormatting.RED));
			Msg.chat(player, line);
		}
	}

	private static Component button(String key, String command, ChatFormatting color) {
		return Component.translatable(key).withStyle(style -> style.withColor(color).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	/** The master accepts the waiting challenge: the tournament starts now if it can. */
	static boolean accept(ServerPlayer player) {
		UUID owner = player.getUUID();
		String sect = Musim.pending(owner);
		if (sect == null) {
			Msg.chat(player, Component.translatable("kultivator.sekte.turnamen.antar.tidak_ada").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (host(player, sect, false)) {
			Musim.setPending(owner, null, 0L);
			return true;
		}
		return false;
	}

	/** The master refuses: the challenge is gone, the sect is a little offended. */
	static boolean refuse(ServerPlayer player) {
		UUID owner = player.getUUID();
		String sect = Musim.pending(owner);
		if (sect == null) {
			Msg.chat(player, Component.translatable("kultivator.sekte.turnamen.antar.tidak_ada").withStyle(ChatFormatting.GRAY));
			return false;
		}
		Musim.setPending(owner, null, 0L);
		SekteApi.diplomasi().adjust(owner, sect, -REFUSE_COST, "turnamen.tolak");
		Msg.chat(player, Component.translatable("kultivator.sekte.turnamen.antar.ditolak", sectName(sect)).withStyle(ChatFormatting.GRAY));
		return true;
	}

	/** Hosts {@code sect}'s visitors now; false (with the reason told) when the tournament cannot start. */
	static boolean host(ServerPlayer player, String sect, boolean test) {
		UUID owner = player.getUUID();
		DiplomasiService diplomacy = SekteApi.diplomasi();
		int tier = diplomacy.tier(sect);
		HOSTING.put(owner, sect);
		try {
			return Tournament.startNow(player, Tournament.KIND_GUESTS, sect, sectName(sect), tier <= 0 ? 4 : tier, test);
		} finally {
			HOSTING.remove(owner);
		}
	}

	/** The visiting sect of the tournament being started right now (inside {@link #host}), or null. */
	static @Nullable String hosting(UUID owner) {
		return HOSTING.get(owner);
	}

	/** The result of an inter-sect tournament: the standing moves by {@link #STANDING}. */
	static void result(MinecraftServer server, UUID owner, String sect, boolean hallWon) {
		SekteApi.diplomasi().adjust(owner, sect, hallWon ? STANDING : -STANDING, "turnamen.antar");
		Musim.interSect(owner, hallWon);
		SekteLog.log(server, owner, SekteIds.T, Component.translatable(hallWon ? "kultivator.sekte.turnamen.log.antar_menang"
				: "kultivator.sekte.turnamen.log.antar_kalah", sectName(sect)));
	}

	/** Every 20 s: a waiting challenge lapses after {@link #LAPSE_DAYS} days. */
	static void tick(MinecraftServer server, UUID owner) {
		String sect = Musim.pending(owner);
		if (sect != null && Ingatan.today(server) - Musim.pendingDay(owner) > LAPSE_DAYS) {
			Musim.setPending(owner, null, 0L);
			SekteLog.log(server, owner, SekteIds.T, Component.translatable("kultivator.sekte.turnamen.log.tantangan_lewat", sectName(sect)));
		}
	}

	/** The NPC sect's name (module D), or a generic "a distant sect". */
	static Component sectName(String sect) {
		Component name = SekteApi.diplomasi().name(sect);
		return name != null ? name : Component.translatable("kultivator.sekte.turnamen.penonton.sekte_jauh");
	}

	private AntarSekte() {
	}
}
