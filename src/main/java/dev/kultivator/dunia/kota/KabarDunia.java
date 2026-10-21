package dev.kultivator.dunia.kota;

import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Kabar Dunia (DESIGN 34.4.3, module K's listeners): the world's news as the teahouses hear it. The other modules'
 * events (a sky event of L, a prison seal of R, a beast's evolution of B, a ship's arrival of T) become short news lines
 * that the tea master tells a guest once, newest first, when they sit down; a Gerhana Darah makes the black market
 * dearer (×1.25: the guards are restless and smugglers lie low). News lives in memory only (at most {@link #MAX},
 * forgotten after three in-game days or a restart); nothing here changes the world.
 */
final class KabarDunia {

	static final int MAX = 6;
	static final long KEEP_TICKS = 3L * 24000L;
	/** Gerhana Darah: the black market's prices ×1.25 while it lasts. */
	static final double ECLIPSE_FACTOR = 1.25D;

	private record News(long id, long gameTime, Component line) {
	}

	private static final ArrayDeque<News> NEWS = new ArrayDeque<>();
	private static final Map<UUID, Long> HEARD = new HashMap<>();
	/** The last integrity band told per prison site, so a slowly sinking seal is news only at 50 and 25. */
	private static final Map<Long, Integer> SEAL_BANDS = new HashMap<>();
	private static long nextId = 1L;

	static void init() {
		DuniaEvents.SKY_EVENT.register((level, kind, started) -> {
			if (started && level.dimension() == Level.OVERWORLD) {
				add(level, Component.translatable("kultivator.kota.berita.langit." + kind.id()));
			}
		});
		DuniaEvents.SEAL_CHANGED.register(KabarDunia::onSeal);
		DuniaEvents.BEAST_EVOLVED.register((owner, beast, tier, line) -> {
			if (owner.level() instanceof ServerLevel level) {
				add(level, Component.translatable("kultivator.kota.berita.evolusi", owner.getDisplayName(), beast.getDisplayName(), tier));
			}
		});
		DuniaEvents.VOYAGE.register((captain, destination, arrived) -> {
			if (!arrived || !(captain.level() instanceof ServerLevel level) || !Kota.enabled()) {
				return;
			}
			long town = Towns.near(level, captain.blockPosition(), 160);
			if (town != 0L) {
				boolean abadi = KotaData.get(level.getServer()).town(town).getBooleanOr("abadi", false);
				Component name = abadi ? KotaNames.immortalCity(town) : KotaNames.town(town);
				Msg.actionBar(captain, Component.translatable("kultivator.kota.berlabuh", name).withStyle(ChatFormatting.GOLD));
				add(level, Component.translatable("kultivator.kota.berita.kapal", captain.getDisplayName(), name));
			}
		});
	}

	private static void onSeal(ServerLevel level, long siteId, int integrity, boolean broken) {
		int band = broken ? 0 : integrity <= 25 ? 1 : integrity <= 50 ? 2 : 3;
		Integer last = SEAL_BANDS.put(siteId, band);
		if (last != null && last == band || band == 3 && last == null) {
			return;
		}
		DuniaSites.Site site = DuniaSites.byId(level.getServer(), siteId);
		Component where = site == null ? Component.translatable("kultivator.kota.berita.entah")
				: Component.translatable("kultivator.kota.berita.dekat", site.pos().getX(), site.pos().getZ());
		String key = switch (band) {
			case 0 -> "kultivator.kota.berita.segel_pecah";
			case 1 -> "kultivator.kota.berita.segel_retak";
			case 2 -> "kultivator.kota.berita.segel_lemah";
			default -> "kultivator.kota.berita.segel_utuh";
		};
		add(level, Component.translatable(key, where));
	}

	private static void add(ServerLevel level, Component line) {
		long now = level.getServer().overworld().getGameTime();
		NEWS.addFirst(new News(nextId++, now, line));
		while (NEWS.size() > MAX) {
			NEWS.removeLast();
		}
	}

	/** The tea master tells {@code player} the newest piece of news they have not heard (once per piece). */
	static void tell(ServerPlayer player, KultivatorNpc teaMaster) {
		if (NEWS.isEmpty()) {
			return;
		}
		long now = player.level().getServer().overworld().getGameTime();
		for (Iterator<News> it = NEWS.iterator(); it.hasNext(); ) {
			if (now - it.next().gameTime() > KEEP_TICKS) {
				it.remove();
			}
		}
		News newest = NEWS.peekFirst();
		if (newest == null || HEARD.getOrDefault(player.getUUID(), 0L) >= newest.id()) {
			return;
		}
		HEARD.put(player.getUUID(), newest.id());
		Msg.chat(player, Component.empty().append(teaMaster.plate()).append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
				.append(Component.translatable("kultivator.kota.berita.kata", newest.line()).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
	}

	/** The black market's price factor in {@code level} now (a Gerhana Darah over the world: ×1.25). */
	static double shadowFactor(@Nullable ServerLevel level) {
		if (level == null) {
			return 1.0D;
		}
		ServerLevel overworld = level.getServer().overworld();
		return DuniaApi.sky().active(overworld) == SkyKind.BLOOD_ECLIPSE ? ECLIPSE_FACTOR : 1.0D;
	}

	/** True when a Gerhana Darah is over the world (the black-market tab shows the surcharge). */
	static boolean eclipse(@Nullable ServerLevel level) {
		return shadowFactor(level) > 1.0D;
	}

	static void clear() {
		NEWS.clear();
		HEARD.clear();
		SEAL_BANDS.clear();
		nextId = 1L;
	}

	private KabarDunia() {
	}
}
