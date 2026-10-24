package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The voice of module 39B towards the master and the diary (DESIGN 39.7.5, 39.8.1).
 * <ul>
 *   <li>{@link #kirim}: a coloured chat line with click buttons when the master is online and not busy
 *   ({@code Sibuk.why}); while the master is busy (a tribulation, a duel, a trial) the line waits and is tried again every
 *   {@code Sibuk.RETRY_TICKS}; offline it goes to the Tetua's morning report ({@code SekteLog}, module {@code mandiri}).
 *   What is told follows the hall policy's notification bits ({@code Kebijakan.Pemberitahuan}).</li>
 *   <li>{@link #catat}: a diary entry ({@code MandiriApi.catat}, composed by 39E from keys and real arguments).</li>
 *   <li>{@link #advancement}: the guide's advancements of 39B are the master's; a disciple keeps them pending in its own
 *   data until the master is online.</li>
 * </ul>
 */
final class KabarTumbuh {

	static final String P = "kultivator.sekte.mandiri.tumbuh.";
	private static final String ADV = "adv";
	private static final int ANTRE_MAKS = 16;

	private record Tertunda(Component line, long sejak) {
	}

	/** Lines held back while the master is busy, per master. */
	private static final Map<UUID, List<Tertunda>> TERTUNDA = new HashMap<>();
	private static int tickCount;

	// ================================================================== the master

	/** Tells the master of {@code entity} about {@code jenis} (when the policy wants it). */
	static void kirim(DiscipleEntity entity, Kebijakan.Pemberitahuan jenis, Component line) {
		UUID owner = entity.ownerId();
		if (owner == null || line == null) {
			return;
		}
		if (jenis != null && !MandiriApi.kebijakan(entity).diberitahu(jenis)) {
			return;
		}
		kirim(entity.level().getServer(), owner, line);
	}

	/** Chat, the busy queue or the morning report. */
	static void kirim(@Nullable MinecraftServer server, UUID owner, Component line) {
		if (server == null || owner == null || line == null) {
			return;
		}
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master == null) {
			SekteLog.log(server, owner, MandiriIds.MODUL, line);
			return;
		}
		if (Sibuk.why(master) != null) {
			List<Tertunda> list = TERTUNDA.computeIfAbsent(owner, key -> new ArrayList<>());
			if (list.size() >= ANTRE_MAKS) {
				list.remove(0);
			}
			list.add(new Tertunda(line, server.overworld().getGameTime()));
			return;
		}
		Msg.chat(master, line);
		master.level().playSound(null, master.getX(), master.getY(), master.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.5F);
	}

	/** Every server tick: the held lines go out once the master is free again (checked every RETRY_TICKS). */
	static void tick(MinecraftServer server) {
		if (TERTUNDA.isEmpty() || ++tickCount % Sibuk.RETRY_TICKS != 0) {
			return;
		}
		Iterator<Map.Entry<UUID, List<Tertunda>>> it = TERTUNDA.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, List<Tertunda>> entry = it.next();
			ServerPlayer master = server.getPlayerList().getPlayer(entry.getKey());
			if (master == null) {
				for (Tertunda line : entry.getValue()) {
					SekteLog.log(server, entry.getKey(), MandiriIds.MODUL, line.line());
				}
				it.remove();
				continue;
			}
			if (Sibuk.why(master) != null) {
				continue;
			}
			for (Tertunda line : entry.getValue()) {
				Msg.chat(master, line.line());
			}
			it.remove();
		}
	}

	static void clear() {
		TERTUNDA.clear();
	}

	/** A click button {@code [label]} running {@code command}, with a hover line. */
	static MutableComponent tombol(String labelKey, String command, String hoverKey, int color) {
		return Component.literal("[").append(Component.translatable(labelKey)).append("]").withStyle(style -> style.withColor(color)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(hoverKey))));
	}

	/** The disciple's name in the warm parchment colour of the old chat lines. */
	static Component nama(DiscipleEntity entity) {
		return entity.getName().copy().withColor(0xF2E6C8);
	}

	/** A line of 39B: key under {@code kultivator.sekte.mandiri.tumbuh.}, the given colour. */
	static MutableComponent baris(String key, int color, Object... args) {
		return Component.translatable(P + key, args).withColor(color);
	}

	// ================================================================== diary

	/** Writes a diary entry of the disciple (DESIGN 39.8.1); the args follow the convention {@code t:/r:/d:/i:/n:}. */
	static void catat(DiscipleEntity entity, String jenis, String kunci, EntriHarian.Suasana suasana, boolean penting, String... args) {
		UUID owner = entity.ownerId();
		if (owner == null) {
			return;
		}
		MinecraftServer server = entity.level().getServer();
		MandiriApi.catat(owner, entity.soulId(), new EntriHarian(DataTumbuh.hari(server), DataTumbuh.jam(server), jenis, kunci,
				args == null ? List.of() : Arrays.asList(args), suasana, penting));
	}

	// ================================================================== advancements

	/** Grants {@code sekte/<adv>} to the online master now, or keeps it pending in the disciple's data. */
	static void advancement(DiscipleEntity entity, String adv) {
		ServerPlayer master = entity.owner();
		if (master != null) {
			Advancements.grant(master, dev.kultivator.sekte.SekteIds.ADVANCEMENT_FOLDER + adv);
			return;
		}
		CompoundTag section = entity.data().extraSection(DataTumbuh.SECTION);
		ListTag list = section.getListOrEmpty(ADV);
		for (int i = 0; i < list.size(); i++) {
			if (adv.equals(list.getStringOr(i, ""))) {
				return;
			}
		}
		list.add(StringTag.valueOf(adv));
		section.put(ADV, list);
	}

	/** Once a second per loaded disciple: pending advancements reach the master as soon as it is online. */
	static void advancementTertunda(DiscipleEntity entity) {
		CompoundTag own = DataTumbuh.baca(entity.data());
		if (!own.contains(ADV)) {
			return;
		}
		ServerPlayer master = entity.owner();
		if (master == null) {
			return;
		}
		ListTag list = own.getListOrEmpty(ADV);
		for (int i = 0; i < list.size(); i++) {
			String adv = list.getStringOr(i, "");
			if (!adv.isEmpty()) {
				Advancements.grant(master, dev.kultivator.sekte.SekteIds.ADVANCEMENT_FOLDER + adv);
			}
		}
		entity.data().extraSection(DataTumbuh.SECTION).remove(ADV);
	}

	/** Gray small text for a reason line. */
	static MutableComponent abu(Component text) {
		return text.copy().withStyle(ChatFormatting.GRAY);
	}

	private KabarTumbuh() {
	}
}
