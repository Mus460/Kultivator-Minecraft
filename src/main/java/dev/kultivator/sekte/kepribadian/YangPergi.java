package dev.kultivator.sekte.kepribadian;

import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.Kepergian;
import dev.kultivator.sekte.api.Perlakuan;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * "Yang Pergi" (DESIGN 36.2.1): the last ten disciples that left the sect, kept with everything {@code Disciples.arrive}
 * needs (the Soul Tablet data from {@code Disciples.depart}), so a wanderer can come home (module X's "seek the disciple"
 * mission, contract C36.P3) and a test can bring anyone back. Kept in the hall section {@code sekte_kepribadian}
 * ({@code pergi}); a master without a hall keeps it in their own cultivation data under the same key. The same store
 * holds the queue of mistreatments that reached a disciple while it was away ({@code antre}), applied when it returns.
 */
public final class YangPergi {

	public static final int MAX = 10;
	private static final int QUEUE_MAX = 32;
	private static final String LIST = "pergi";
	private static final String QUEUE = "antre";

	/** One entry of the list, for screens and commands. */
	public record Entry(UUID soul, Component name, Kepergian how, long day, @Nullable String toSect) {
	}

	// ================================================================== store

	private static @Nullable CompoundTag store(@Nullable MinecraftServer server, UUID owner) {
		if (owner == null) {
			return null;
		}
		if (Halls.hallOf(owner) != null) {
			return Halls.section(owner, SekteIds.hallSection(SekteIds.P));
		}
		ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(owner);
		return player == null ? null : Cultivators.get(player).extra().getCompound(SekteIds.hallSection(SekteIds.P)).orElseGet(() -> {
			CompoundTag fresh = new CompoundTag();
			Cultivators.get(player).extra().put(SekteIds.hallSection(SekteIds.P), fresh);
			return fresh;
		});
	}

	private static void dirty(@Nullable MinecraftServer server, UUID owner) {
		if (Halls.hallOf(owner) != null) {
			Halls.setDirty();
			return;
		}
		ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(owner);
		if (player != null) {
			Cultivators.dirty(player);
		}
	}

	// ================================================================== the list

	static void add(MinecraftServer server, UUID owner, UUID soul, Component name, Kepergian how, long day, CompoundTag tablet,
			@Nullable String toSect) {
		CompoundTag store = store(server, owner);
		if (store == null) {
			return;
		}
		ListTag list = store.getListOrEmpty(LIST);
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i) instanceof CompoundTag old && soul.toString().equals(old.getStringOr("soul", ""))) {
				list.remove(i);
			}
		}
		CompoundTag entry = new CompoundTag();
		entry.putString("soul", soul.toString());
		entry.store("name", ComponentSerialization.CODEC, name);
		entry.putString("cara", how.id());
		entry.putLong("hari", day);
		if (toSect != null && !toSect.isEmpty()) {
			entry.putString("sekte", toSect);
		}
		entry.put("tablet", tablet.copy());
		list.add(entry);
		while (list.size() > MAX) {
			list.remove(0);
		}
		store.put(LIST, list);
		dirty(server, owner);
	}

	/** The entries, newest first. */
	public static List<Entry> list(@Nullable MinecraftServer server, UUID owner) {
		CompoundTag store = store(server, owner);
		if (store == null) {
			return List.of();
		}
		ListTag list = store.getListOrEmpty(LIST);
		List<Entry> entries = new ArrayList<>(list.size());
		for (Tag tag : list) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			UUID soul = parse(entry.getStringOr("soul", ""));
			if (soul == null) {
				continue;
			}
			Component name = entry.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			Kepergian how = Kepergian.MINGGAT;
			for (Kepergian k : Kepergian.values()) {
				if (k.id().equals(entry.getStringOr("cara", ""))) {
					how = k;
				}
			}
			String sect = entry.getStringOr("sekte", "");
			entries.add(0, new Entry(soul, name, how, entry.getLongOr("hari", 0L), sect.isEmpty() ? null : sect));
		}
		return entries;
	}

	static @Nullable Entry find(@Nullable MinecraftServer server, UUID owner, UUID soul) {
		for (Entry entry : list(server, owner)) {
			if (entry.soul().equals(soul)) {
				return entry;
			}
		}
		return null;
	}

	/** Forgets every entry (test {@code reset}). */
	static void clear(@Nullable MinecraftServer server, UUID owner) {
		CompoundTag store = store(server, owner);
		if (store != null) {
			store.remove(LIST);
			store.remove(QUEUE);
			dirty(server, owner);
		}
	}

	/**
	 * Brings {@code soul} back (contract C36.P3: X's {@code pulang:<soul>}, or the test {@code pulih}): it arrives near the
	 * hall stele (or its master), listed again, contentment 55, a memory of coming home. False with a chat line to the
	 * master when it cannot come now (master away, no room, chunk not loaded); the entry then stays.
	 */
	static boolean bringBack(MinecraftServer server, UUID owner, UUID soul, String why) {
		CompoundTag store = store(server, owner);
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (store == null || master == null) {
			return false;
		}
		ListTag list = store.getListOrEmpty(LIST);
		int at = -1;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) instanceof CompoundTag entry && soul.toString().equals(entry.getStringOr("soul", ""))) {
				at = i;
			}
		}
		if (at < 0) {
			return false;
		}
		CompoundTag entry = list.getCompoundOrEmpty(at);
		Component name = entry.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
		int alive = Disciples.aliveCount(Cultivators.get(master));
		int cap = Disciples.cap(master);
		if (alive >= cap) {
			Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.kembali.penuh", name, alive, cap).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		ServerLevel level = master.level();
		Vec3 spot = master.position();
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall != null && hall.active()) {
			ServerLevel hallLevel = server.getLevel(hall.dim());
			if (hallLevel != null) {
				level = hallLevel;
				spot = Vec3.atBottomCenterOf(hall.pos()).add(3.0D, 0.0D, 3.0D);
			}
		}
		CompoundTag tablet = entry.getCompoundOrEmpty("tablet").copy();
		if (tablet.read("soul", UUIDUtil.CODEC).isEmpty()) {
			list.remove(at);
			dirty(server, owner);
			return false;
		}
		DiscipleEntity back = Disciples.arrive(level, spot, tablet, owner);
		if (back == null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.kembali.gagal", name).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		list.remove(at);
		dirty(server, owner);
		long today = Ingatan.today(server);
		Hati.setContentment(back.data(), 55.0D);
		Hati.putInt(back.data(), "low", 0);
		Hati.putLong(back.data(), "last", today);
		Hati.putLong(back.data(), "day", today);
		Hati.putString(back.data(), "bujuk", "");
		Ingatan.remember(server, back.data(), SekteIds.P + ".kembali", owner, 5, "kultivator.sekte.kepribadian.ingat.kembali", why);
		level.playSound(null, back.getX(), back.getY(), back.getZ(), SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.6F, 1.1F);
		Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.kembali.ok", back.getDisplayName()).withStyle(ChatFormatting.GREEN));
		SekteLog.log(server, owner, SekteIds.P, Component.translatable("kultivator.sekte.kepribadian.log.kembali", back.getDisplayName()));
		Kepribadian.afterChange(back, true);
		return true;
	}

	// ================================================================== the queue of a disciple that was away

	static void queue(MinecraftServer server, UUID owner, UUID soul, Perlakuan kind, int severity, String source) {
		CompoundTag store = store(server, owner);
		if (store == null) {
			return;
		}
		ListTag queue = store.getListOrEmpty(QUEUE);
		CompoundTag entry = new CompoundTag();
		entry.putString("soul", soul.toString());
		entry.putString("kind", kind.id());
		entry.putInt("sev", severity);
		entry.putString("src", source == null ? "" : source);
		queue.add(entry);
		while (queue.size() > QUEUE_MAX) {
			queue.remove(0);
		}
		store.put(QUEUE, queue);
		dirty(server, owner);
	}

	/** True when anything waits for {@code owner}'s disciples (cheap: one list size). */
	static boolean hasQueue(@Nullable MinecraftServer server, UUID owner) {
		CompoundTag store = store(server, owner);
		return store != null && !store.getListOrEmpty(QUEUE).isEmpty();
	}

	/** Applies and removes what waited for {@code e}. */
	static void drain(MinecraftServer server, UUID owner, DiscipleEntity e) {
		CompoundTag store = store(server, owner);
		if (store == null) {
			return;
		}
		ListTag queue = store.getListOrEmpty(QUEUE);
		String soul = e.soulId().toString();
		boolean changed = false;
		for (int i = queue.size() - 1; i >= 0; i--) {
			if (!(queue.get(i) instanceof CompoundTag entry) || !soul.equals(entry.getStringOr("soul", ""))) {
				continue;
			}
			queue.remove(i);
			changed = true;
			for (Perlakuan kind : Perlakuan.values()) {
				if (kind.id().equals(entry.getStringOr("kind", ""))) {
					Kepuasan.mistreat(server, e, kind, entry.getIntOr("sev", kind.severity()), entry.getStringOr("src", ""), owner);
				}
			}
		}
		if (changed) {
			store.put(QUEUE, queue);
			dirty(server, owner);
		}
	}

	static @Nullable UUID parse(String text) {
		try {
			return text == null || text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private YangPergi() {
	}
}
