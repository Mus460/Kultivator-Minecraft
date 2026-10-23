package dev.kultivator.sekte.langit.main;

import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitPayloads;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.sekte.langit.gerak.FeriPanggil;
import dev.kultivator.sekte.langit.npc.LangitNpcAnchors;
import dev.kultivator.sekte.langit.npc.LangitNpcPlan;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The doors of {@code sekte.langit.LangitTests} into this package (DESIGN 37.7.9): every event starts at once (no day,
 * no cooldown, no tier gate; the {@code Sibuk} reason is only reported). Each returns null when it worked, else why not.
 */
public final class LangitTestHooks {

	/** 37V's client test ops of {@link LangitPayloads.Fx}: the halo for 60 s and the festival lanterns for 60 s. */
	public static final int OP_TEST_HALO = 100;
	public static final int OP_TEST_PESTA = 101;

	public static @Nullable Component ferry(ServerPlayer player) {
		return FeriPanggil.callNear(player);
	}

	public static @Nullable Component bajak(ServerPlayer player, int ships, boolean longbo) {
		return LangitBajak.start(player.level().getServer(), player.getUUID(), ships, longbo, true);
	}

	public static @Nullable Component utusan(ServerPlayer player) {
		return LangitUtusan.arrive(player.level().getServer(), player.getUUID(), true);
	}

	public static @Nullable Component tamu(ServerPlayer player) {
		return LangitPeristiwa.tamu(player.level().getServer(), player.getUUID(), true);
	}

	public static @Nullable Component sengsara(ServerPlayer player) {
		LangitSaksi.testVortex(player, null);
		return null;
	}

	public static @Nullable Component halo(ServerPlayer player) {
		LangitPayloads.send(player, new LangitPayloads.Fx(OP_TEST_HALO, player.blockPosition(), player.blockPosition()));
		return null;
	}

	public static @Nullable Component pesta(ServerPlayer player) {
		LangitPayloads.send(player, new LangitPayloads.Fx(OP_TEST_PESTA, player.blockPosition(), player.blockPosition()));
		if (LangitApi.get().skyHall(player.getUUID()) != null) {
			LangitPeristiwa.festivalFor(player.level().getServer(), player.getUUID(), 1);
		}
		return null;
	}

	/** One line of the hall's event clock for {@code uji langit_sekte info}. */
	public static String eventInfo(ServerPlayer player, UUID owner) {
		CompoundTag hall = LangitPeristiwa.info(player.level().getServer(), owner);
		long today = LangitPeristiwa.today(player.level().getServer());
		SkyHallView view = LangitApi.get().skyHall(owner);
		return "hari " + today + " | bajak " + day(hall, "bajak_berikut", today) + " | utusan " + day(hall, "utusan_berikut", today) + " | tamu "
				+ day(hall, "tamu_berikut", today) + " | pesta " + day(hall, "pesta_berikut", today) + " | tunda " + hall.getStringOr("tunda", "-")
				+ " | tolak " + hall.getIntOr("tolak", 0) + " | bajak_kini " + LangitBajak.active(owner) + " | utusan_layak "
				+ (view != null && LangitUtusan.eligible(view)) + " | tubuh_npc " + LangitNpcAnchors.alive().size();
	}

	private static String day(CompoundTag hall, String key, long today) {
		return hall.contains(key) ? "+" + (hall.getLongOr(key, today) - today) : "-";
	}

	public static Component npcName(LangitNpcPlan.Istana istana) {
		String id = LangitNpcPlan.sectId(istana);
		Component name = id == null ? null : dev.kultivator.sekte.api.SekteApi.diplomasi().name(id);
		return name != null ? name : Component.translatable("kultivator.sekte_langit.npc.tanpa_nama");
	}

	private LangitTestHooks() {
	}
}
