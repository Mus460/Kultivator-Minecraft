package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaPacks;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Init of 38GU caves and natural sites (DESIGN 38.C.4, 38.C.5, 38.A #20-28): hermit caves, inheritance tombs, beast
 * and demon lairs, spirit-vein caves, the Heart Mirror cave, the thunder peak, the star stage and the spirit spring.
 * Called once from {@code Tempat.init()}. Registers the entity types (always, 38.0 rule 17), the payload, config
 * section {@code gua}, the world generation pack {@code kultivator:dunia_gua} (new chunks only), the anchor handler of
 * module {@code "gua"}, the {@code Sibuk} states {@code tempat.warisan} and {@code tempat.cermin}, the place factors and
 * meditation sites ({@link GuaTempat}), the tribulation listener of Puncak Petir, the {@code SpiritBeasts} and
 * {@code StarCalamity} hooks, the spring's drink and the {@code uji tempat_gua} commands. Nothing here ever places or breaks a
 * block, makes lightning or changes weather or time (38.0 rule 1, 38.D.6).
 */
public final class Gua {

	public static final String SIBUK_WARISAN = "tempat.warisan";
	public static final String SIBUK_CERMIN = "tempat.cermin";
	/** How often the entry card of one cave shows to one player. */
	private static final long KARTU_TICKS = 6000L;

	private static boolean initialised;
	/** "player|key" -> game time of the last one-off line (entry cards, refusals). */
	private static final Map<String, Long> SEKALI = new HashMap<>();

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		GuaEntities.init();
		GuaPayload.register();
		GuaConfig config = GuaConfig.get();
		DuniaPacks.worldgen("gua", config.worldgen);
		DuniaAnchors.register(GuaAnchors.MODULE, new AnchorHandler() {
			@Override
			public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
				Gua.loaded(level, anchor);
			}

			@Override
			public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
				Gua.nearby(level, anchor, within);
			}

			@Override
			public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
				GuaRuns.drop(anchor);
			}
		});
		Sibuk.register(SIBUK_WARISAN, GuaApi::dalamMakam);
		Sibuk.register(SIBUK_CERMIN, CerminHati::aktif);
		GuaTempat.init();
		Tribulation.addListener(PuncakPetir.listener());
		Tribulation.addStrikeFactorProvider((player, target, index, last) -> TempatApi.faktor(player, "kesengsaraan"));
		SpiritBeasts.addXpFactorProvider(GuaApi::faktorXpBinatang);
		dev.kultivator.purba.StarCalamity.addFinishListener(PanggungBintang::bencana);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !player.getMainHandItem().isEmpty()
					|| !GuaConfig.on()) {
				return InteractionResult.PASS;
			}
			MataAirRoh spring = GuaRuns.at(sp, MataAirRoh.class);
			return spring != null && spring.minum(sp, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Gua::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GuaRuns.clear();
			CerminHati.lupakan();
			GuaTempat.reset();
			SEKALI.clear();
		});
		GuaCommands.init();
	}

	private static void tick(MinecraftServer server) {
		if (!GuaConfig.on()) {
			if (GuaRuns.count() > 0) {
				GuaRuns.clear();
			}
			return;
		}
		GuaRuns.tick(server);
		long t = server.getTickCount();
		if (t % 20L == 7L) {
			CerminHati.sapu(server);
		}
		if (t % 40L == 13L) {
			GuaTempat.tickParkir(server);
			GuaTempat.tickGiok(server, t - 13L);
		}
		if (t % 12000L == 0L && SEKALI.size() > 512) {
			SEKALI.clear();
		}
	}

	// ================================================================== anchors

	private static void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		switch (anchor.what()) {
			case GuaAnchors.URAT_INTI -> GuaData.get(level.getServer()).setLahir(anchor.siteOf(GuaAnchors.K_URAT_ROH), level.dimension(),
					GuaUratRoh.harta(anchor), "urat_roh");
			case GuaAnchors.BINTANG_PANGGUNG -> GuaData.get(level.getServer()).setLahir(anchor.siteOf(GuaAnchors.K_PANGGUNG), level.dimension(),
					anchor.blockPosition().above(), "panggung_bintang");
			default -> {
			}
		}
	}

	private static void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!GuaConfig.on()) {
			return;
		}
		String what = anchor.what();
		if (GuaAnchors.MASUK.equals(what)) {
			kartu(anchor, within);
			return;
		}
		if (GuaAnchors.SITUS.equals(what)) {
			return;
		}
		GuaRun run = GuaRuns.touch(anchor, level, a -> buat(a, level));
		if (run != null && run.started()) {
			run.nearby(within);
		}
		// the sites without an entry anchor show their card when a player steps onto them
		String nama = switch (what) {
			case GuaAnchors.URAT_INTI -> "urat_roh";
			case GuaAnchors.PETIR_ALTAR -> "puncak_petir";
			case GuaAnchors.BINTANG_PANGGUNG -> "panggung_bintang";
			case GuaAnchors.MATA_AIR -> "mata_air_roh";
			default -> null;
		};
		if (nama != null && run != null) {
			Realm ranah = GuaAnchors.URAT_INTI.equals(what) ? Realm.GOLDEN_CORE : null;
			for (ServerPlayer player : within) {
				if (run.inside(player)) {
					kartu(player, anchor.site(), nama, ranah);
				}
			}
		}
	}

	private static @Nullable GuaRun buat(DuniaAnchorEntity anchor, ServerLevel level) {
		String what = anchor.what();
		if (what.startsWith("iblis_")) {
			return SarangIblis.buat(anchor, level);
		}
		return switch (what) {
			case GuaAnchors.PERTAPA -> new PertapaRun(anchor, level);
			case GuaAnchors.MAKAM_PENGHALANG -> new MakamRun.Penghalang(anchor, level);
			case GuaAnchors.MAKAM_HATI -> new MakamRun.Hati(anchor, level);
			case GuaAnchors.MAKAM_KEKUATAN -> new MakamRun.Kekuatan(anchor, level);
			case GuaAnchors.MAKAM_PEMAHAMAN -> new MakamRun.Pemahaman(anchor, level);
			case GuaAnchors.MAKAM_AULA -> new MakamRun.Aula(anchor, level);
			case GuaAnchors.SARANG_BOS -> new SarangBinatang(anchor, level);
			case GuaAnchors.URAT_INTI -> new GuaUratRoh(anchor, level);
			case GuaAnchors.CERMIN_KOLAM -> new CerminHati(anchor, level);
			case GuaAnchors.PETIR_ALTAR -> new PuncakPetir(anchor, level);
			case GuaAnchors.BINTANG_PANGGUNG -> new PanggungBintang(anchor, level);
			case GuaAnchors.MATA_AIR -> new MataAirRoh(anchor, level);
			default -> null;
		};
	}

	/**
	 * The entry card of a cave (38.0 rule 13): the place's name as a banner and, below its suggested realm, a red
	 * warning; once per player per cave every five minutes.
	 */
	private static void kartu(DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		String nama = anchor.data().getStringOr("nama", "gua_pertapa");
		Realm ranah = GuaAnchors.ranahMasuk(anchor);
		for (ServerPlayer player : within) {
			if (player.position().distanceToSqr(anchor.position()) <= 8.0D * 8.0D) {
				kartu(player, anchor.site(), nama, ranah);
			}
		}
	}

	/** One player's entry card of place {@code nama}, the realm warning and, on the first visit, the place's Sumber line. */
	private static void kartu(ServerPlayer player, long site, String nama, @Nullable Realm ranah) {
		if (!sekali(player, "kartu:" + site, KARTU_TICKS)) {
			return;
		}
		boolean bawah = ranah != null && !Cultivators.get(player).realm().atLeast(ranah);
		GuaPayload.kartu(player, nama, ranah == null ? "" : ranah.id(), bawah);
		if (ranah != null) {
			Msg.chat(player, Component.translatable(bawah ? "kultivator.gua.masuk.bahaya" : "kultivator.gua.masuk.ranah",
					Component.translatable("kultivator.gua.tempat." + nama), ranah.displayName())
					.withStyle(bawah ? ChatFormatting.RED : ChatFormatting.GRAY));
		}
		// a line of the place's own air on every card: what the cave smells and sounds like (4 per place)
		Msg.chat(player, acak("kultivator.gua.lore." + nama, 4).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		if (sekali(player, "sumber:" + nama, 72000L)) {
			Msg.chat(player, Component.translatable("kultivator.gua." + nama + ".inspired").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	/** One of the {@code n} variants {@code key.0 .. key.(n-1)} of a line, picked at random. */
	static net.minecraft.network.chat.MutableComponent acak(String key, int n) {
		return Component.translatable(key + "." + java.util.concurrent.ThreadLocalRandom.current().nextInt(n));
	}

	/** True at most once per {@code ticks} for {@code player} and {@code key} (one-off lines). */
	static boolean sekali(ServerPlayer player, String key, long ticks) {
		String k = player.getUUID() + "|" + key;
		long now = player.level().getGameTime();
		Long last = SEKALI.get(k);
		if (last != null && now - last < ticks) {
			return false;
		}
		SEKALI.put(k, now);
		return true;
	}

	private Gua() {
	}
}
