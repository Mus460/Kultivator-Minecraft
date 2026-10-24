package dev.kultivator.sekte.mandiri.cerita;

import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notices to the master (DESIGN 39.7.5, contract C39.E2, installed with {@code MandiriApi.setPemberita}):
 * <ul>
 * <li>a coloured chat line with the disciple's news and a {@code [Lihat]} that opens the Jalan Hidup Murid board, plus a
 * small toast in the top right; at most one toast per {@link #JEDA_TOAST} ticks, a burst merges into one card ("3 kabar
 * baru dari murid");</li>
 * <li>while {@code Sibuk.why(master)} is not null (tribulation, duel, a trial) the notice waits and is tried again every
 * {@code Sibuk.RETRY_TICKS} (M2 rule);</li>
 * <li>for an offline master the notice is kept in the hall record ({@code cerita.tunda}, at most {@link #TUNDA_MAKS}) and
 * delivered as one summary a few seconds after the next login ("Selama Guru pergi: ...").</li>
 * </ul>
 * The elder's morning report already receives every line ({@code MandiriApi.beritahu} writes {@code SekteLog} first).
 */
final class Pemberitahuan {

	static final int JEDA_TOAST = 100;
	static final int TUNDA_MAKS = 24;
	/** Lines a login summary shows before "... and N more". */
	static final int RINGKAS_BARIS = 6;
	/** Delay of the login summary after joining (the client settles first). */
	static final int RINGKAS_TUNDA = 100;
	static final String PERINTAH_PAPAN = "/kultivasi perguruan mandiri papan";
	private static final String K = "kultivator.sekte.mandiri.cerita.";

	private record Antre(Kebijakan.Pemberitahuan jenis, Component pesan, boolean penting) {
	}

	private static final Map<UUID, Deque<Antre>> SIBUK = new HashMap<>();
	private static final Map<UUID, Long> TOAST_TERAKHIR = new HashMap<>();
	private static final Map<UUID, int[]> TOAST_GABUNG = new HashMap<>();
	private static final Map<UUID, Long> RINGKAS_PADA = new HashMap<>();

	/** The installed {@code MandiriApi.Pemberita}. */
	static void kirim(MinecraftServer server, UUID owner, Kebijakan.Pemberitahuan jenis, Component pesan, boolean penting) {
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master == null) {
			tunda(server, owner, jenis, pesan, penting);
			return;
		}
		if (Sibuk.why(master) != null) {
			Deque<Antre> queue = SIBUK.computeIfAbsent(owner, id -> new ArrayDeque<>());
			if (queue.size() >= TUNDA_MAKS) {
				queue.pollFirst();
			}
			queue.addLast(new Antre(jenis, pesan, penting));
			return;
		}
		antar(master, jenis, pesan, penting);
	}

	private static void antar(ServerPlayer master, Kebijakan.Pemberitahuan jenis, Component pesan, boolean penting) {
		Msg.chat(master, baris(jenis, pesan, penting));
		long now = master.level().getGameTime();
		Long last = TOAST_TERAKHIR.get(master.getUUID());
		if (last == null || now - last >= JEDA_TOAST) {
			TOAST_TERAKHIR.put(master.getUUID(), now);
			MandiriPayloads.kirim(master, new MandiriPayloads.Toast(jenis.ordinal(), pesan, penting));
		} else {
			int[] merged = TOAST_GABUNG.computeIfAbsent(master.getUUID(), id -> new int[2]);
			merged[0]++;
			merged[1] |= penting ? 1 : 0;
		}
	}

	/** One chat line: the seal of the kind, the news, and {@code [Lihat]}. */
	static MutableComponent baris(Kebijakan.Pemberitahuan jenis, Component pesan, boolean penting) {
		ChatFormatting tone = switch (jenis) {
			case TEROBOSAN, TEKNIK -> ChatFormatting.GOLD;
			case LUKA, TERTAWAN -> ChatFormatting.RED;
			case IBLIS -> ChatFormatting.DARK_PURPLE;
			case SETERU -> ChatFormatting.GOLD;
			default -> ChatFormatting.GREEN;
		};
		MutableComponent seal = Component.translatable(K + "pesan.cap." + jenis.name().toLowerCase(java.util.Locale.ROOT))
				.withStyle(tone, ChatFormatting.BOLD);
		MutableComponent body = pesan.copy().withStyle(penting ? ChatFormatting.WHITE : ChatFormatting.GRAY);
		return Component.literal("").append(seal).append(Component.literal(" ")).append(body).append(Component.literal(" ")).append(lihat());
	}

	static MutableComponent lihat() {
		return Component.translatable(K + "pesan.lihat").withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
				.withClickEvent(new ClickEvent.RunCommand(PERINTAH_PAPAN))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(K + "pesan.lihat.tip"))));
	}

	private static void tunda(MinecraftServer server, UUID owner, Kebijakan.Pemberitahuan jenis, Component pesan, boolean penting) {
		CompoundTag root = Cerita.bagian(server, owner, "tunda");
		ListTag list = root.getListOrEmpty("daftar").copy();
		CompoundTag line = new CompoundTag();
		line.putString("j", jenis.name());
		line.putBoolean("p", penting);
		line.store("t", ComponentSerialization.CODEC, pesan);
		list.add(line);
		int dropped = 0;
		while (list.size() > TUNDA_MAKS) {
			dropped++;
			// drop the oldest ordinary line first, else the oldest
			int drop = 0;
			for (int i = 0; i < list.size(); i++) {
				if (!list.getCompoundOrEmpty(i).getBooleanOr("p", false)) {
					drop = i;
					break;
				}
			}
			list.remove(drop);
		}
		root.put("daftar", list);
		root.putInt("lebih", root.getIntOr("lebih", 0) + dropped);
		dev.kultivator.sekte.mandiri.api.MandiriApi.tandai(server);
	}

	/** Keeps a notice for the next login summary (also the test {@code uji ... ringkas}). */
	static void kirimTunda(MinecraftServer server, UUID owner, Kebijakan.Pemberitahuan jenis, Component pesan, boolean penting) {
		tunda(server, owner, jenis, pesan, penting);
	}

	/** A master logged in: the summary follows after {@link #RINGKAS_TUNDA} ticks. */
	static void masuk(ServerPlayer player) {
		RINGKAS_PADA.put(player.getUUID(), player.level().getGameTime() + RINGKAS_TUNDA);
	}

	/** A master logged out: busy notices are kept for the next login. */
	static void keluar(MinecraftServer server, UUID owner) {
		Deque<Antre> queue = SIBUK.remove(owner);
		if (queue != null) {
			for (Antre a : queue) {
				tunda(server, owner, a.jenis(), a.pesan(), a.penting());
			}
		}
		TOAST_TERAKHIR.remove(owner);
		TOAST_GABUNG.remove(owner);
		RINGKAS_PADA.remove(owner);
	}

	/** Every second (from {@code Cerita}'s server tick). */
	static void detik(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!SIBUK.isEmpty() && now % Sibuk.RETRY_TICKS < 20) {
			for (UUID owner : new ArrayList<>(SIBUK.keySet())) {
				ServerPlayer master = server.getPlayerList().getPlayer(owner);
				if (master == null) {
					keluar(server, owner);
					continue;
				}
				if (Sibuk.why(master) != null) {
					continue;
				}
				Deque<Antre> queue = SIBUK.remove(owner);
				for (Antre a : queue) {
					antar(master, a.jenis(), a.pesan(), a.penting());
				}
			}
		}
		if (!TOAST_GABUNG.isEmpty()) {
			for (UUID owner : new ArrayList<>(TOAST_GABUNG.keySet())) {
				ServerPlayer master = server.getPlayerList().getPlayer(owner);
				Long last = TOAST_TERAKHIR.get(owner);
				if (master == null) {
					TOAST_GABUNG.remove(owner);
				} else if (last == null || now - last >= JEDA_TOAST) {
					int[] merged = TOAST_GABUNG.remove(owner);
					TOAST_TERAKHIR.put(owner, now);
					MandiriPayloads.kirim(master, new MandiriPayloads.Toast(MandiriPayloads.TOAST_RINGKAS,
							Component.translatable(K + "toast.gabung", merged[0]), merged[1] != 0));
				}
			}
		}
		if (!RINGKAS_PADA.isEmpty()) {
			for (UUID owner : new ArrayList<>(RINGKAS_PADA.keySet())) {
				ServerPlayer master = server.getPlayerList().getPlayer(owner);
				if (master == null) {
					RINGKAS_PADA.remove(owner);
				} else if (now >= RINGKAS_PADA.get(owner) && Sibuk.why(master) == null) {
					RINGKAS_PADA.remove(owner);
					ringkas(server, master);
				}
			}
		}
	}

	/** "Selama Guru pergi: ..." with the kept lines, important ones first. */
	private static void ringkas(MinecraftServer server, ServerPlayer master) {
		CompoundTag root = Cerita.bagian(server, master.getUUID(), "tunda");
		ListTag list = root.getListOrEmpty("daftar");
		if (list.isEmpty()) {
			return;
		}
		List<Component> penting = new ArrayList<>();
		List<Component> biasa = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag line = list.getCompoundOrEmpty(i);
			Component text = line.read("t", ComponentSerialization.CODEC).orElse(null);
			if (text != null) {
				(line.getBooleanOr("p", false) ? penting : biasa).add(text);
			}
		}
		int total = penting.size() + biasa.size() + root.getIntOr("lebih", 0);
		Msg.chat(master, Component.translatable(K + "ringkas.judul", total).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		int shown = 0;
		for (List<Component> group : List.of(penting, biasa)) {
			for (Component text : group) {
				if (shown >= RINGKAS_BARIS) {
					break;
				}
				Msg.chat(master, Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY).append(text.copy().withStyle(
						group == penting ? ChatFormatting.WHITE : ChatFormatting.GRAY)));
				shown++;
			}
		}
		MutableComponent tail = total > shown ? Component.translatable(K + "ringkas.lebih", total - shown).withStyle(ChatFormatting.GRAY)
				: Component.literal("");
		Msg.chat(master, tail.append(Component.literal(" ")).append(lihat()));
		Component card = penting.isEmpty() ? Component.translatable(K + "toast.ringkas", total)
				: Component.translatable(K + "toast.ringkas_penting", total, penting.get(0));
		MandiriPayloads.kirim(master, new MandiriPayloads.Toast(MandiriPayloads.TOAST_RINGKAS, card, !penting.isEmpty()));
		root.remove("daftar");
		root.remove("lebih");
		dev.kultivator.sekte.mandiri.api.MandiriApi.tandai(server);
	}

	/** Forgets the in-memory queues (server stop). */
	static void bersih() {
		SIBUK.clear();
		TOAST_TERAKHIR.clear();
		TOAST_GABUNG.clear();
		RINGKAS_PADA.clear();
	}

	private Pemberitahuan() {
	}
}
