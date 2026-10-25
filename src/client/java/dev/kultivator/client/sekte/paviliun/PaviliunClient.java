package dev.kultivator.client.sekte.paviliun;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.SekteScenes;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.paviliun.PaviliunPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * The client of module A (DESIGN 36.2.5, 36.4): the receivers of A's payloads (the data of the Rak Kitab and Makam subs of
 * Sekte › Kas &amp; Toko, DESIGN 43.5, and the world picture of the master's own sect), the poses of activities 13 and 14 ({@link PaviliunPoses}), the world renderer
 * ({@link PaviliunRenderer}), four scenes of beats 140-143 through the lead's {@code SekteScenes} (every 7b camera guard
 * applies), and the reader's motes: a golden glyph-mote rising from each floating sutra every 1.5 s (two on Maksimal,
 * none on Minimal), only for readers within 24 blocks.
 */
public final class PaviliunClient {

	public static final int BEAT_QINGMING = EpicIds.EXTRA_SEKTE_FIRST + 40;
	public static final int BEAT_FUNERAL = EpicIds.EXTRA_SEKTE_FIRST + 41;
	public static final int BEAT_ENSHRINE = EpicIds.EXTRA_SEKTE_FIRST + 42;
	public static final int BEAT_LEARNED = EpicIds.EXTRA_SEKTE_FIRST + 43;

	/** How long a page's own request waits for its answer before an "open" counts as a click in the world. */
	private static final long MINTA_MS = 3000L;

	private static boolean initialised;
	private static long ticks;
	/** The last data per page ({@code PAGE_RAK}, {@code PAGE_MAKAM}); empty until the server sends it. */
	private static final CompoundTag[] DATA = {new CompoundTag(), new CompoundTag()};
	private static final long[] DIMINTA = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};

	public static void init() {
		if (initialised || !dev.kultivator.sekte.SekteContent.on(SekteIds.A)) {
			// switched off: the server side registered no payloads, so nothing to receive
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(PaviliunPayloads.View.TYPE, (p, context) -> onView(p));
		ClientPlayNetworking.registerGlobalReceiver(PaviliunPayloads.Places.TYPE, (p, context) -> PaviliunWorld.set(p.data()));
		ClientTickEvents.END_CLIENT_TICK.register(PaviliunClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			DATA[0] = new CompoundTag();
			DATA[1] = new CompoundTag();
			PaviliunWorld.clear();
			PaviliunRenderer.clear();
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(PaviliunRenderer::collect);
		PaviliunPoses.init();
		scenes();
	}

	/** True when module A runs (its subs Rak Kitab and Makam exist). */
	public static boolean aktif() {
		return initialised;
	}

	/** The last data of page {@code page} (never null; empty until it arrives). */
	static CompoundTag data(int page) {
		return DATA[page == PaviliunPayloads.PAGE_RAK ? 0 : 1];
	}

	/** A shown sub asks for its page (the answer refreshes it in place, 43.0.12: only while shown). */
	static void minta(int page) {
		int i = page == PaviliunPayloads.PAGE_RAK ? 0 : 1;
		DIMINTA[i] = Util.getMillis();
		send(i == 0 ? PaviliunPayloads.OP_OPEN_RAK : PaviliunPayloads.OP_OPEN_MAKAM, "");
	}

	static void send(String op, String arg) {
		if (ClientPlayNetworking.canSend(PaviliunPayloads.Act.TYPE)) {
			ClientPlayNetworking.send(new PaviliunPayloads.Act(op, arg == null ? "" : arg));
		}
	}

	/**
	 * A page from the server: kept for the sub that draws it. An "open" that no sub asked for is a click in the world (the
	 * pavilion, the altar, a grave): the Gulungan opens on Sekte › Kas &amp; Toko › Rak Kitab or Makam (43.4.4).
	 */
	private static void onView(PaviliunPayloads.View p) {
		int i = p.page() == PaviliunPayloads.PAGE_RAK ? 0 : 1;
		DATA[i] = p.data() == null ? new CompoundTag() : p.data();
		if (p.open() && Util.getMillis() - DIMINTA[i] > MINTA_MS) {
			HubNav.buka(Tujuan.of(HubNav.SEKTE, Urutan.ID_KAS).sub(i == 0 ? "rak" : "makam"));
		}
	}

	/** Beats 140-143 (A's range of the EXTRA moment). */
	private static void scenes() {
		// 140: the Qingming begins: a crane shot rising over the kneeling rows, a gentle theme, a jade-washed sky
		SekteScenes.register(BEAT_QINGMING, new SekteScenes.Recipe("kultivator.sekte.paviliun.scene.qingming", SekteScenes.Arg.NUMBER_A,
				"kultivator.sekte.paviliun.scene.qingming.sub", "清", 0x2E8963, true, 80, SekteScenes.Move.CRANE_UP, 6.0F, MusicCue.Cue.M_GENTLE, 12,
				SkyDirector.Grade.SK_JADE, -1, 160));
		// 141: a funeral: a slow pull back from the tombstone, the sorrow theme, an ash-grey sky
		SekteScenes.register(BEAT_FUNERAL, new SekteScenes.Recipe("kultivator.sekte.paviliun.scene.funeral", SekteScenes.Arg.NUMBER_A,
				"kultivator.sekte.paviliun.scene.funeral.sub", "奠", 0x4A5A6A, true, 70, SekteScenes.Move.PULLBACK, 5.0F, MusicCue.Cue.M_SORROW, 10,
				SkyDirector.Grade.SK_ASH, -1, 140));
		// 142: a spirit tablet enshrined: a stamp and a short orbit round the altar, a soft gold flash
		SekteScenes.register(BEAT_ENSHRINE, new SekteScenes.Recipe("kultivator.sekte.paviliun.scene.enshrine", SekteScenes.Arg.NUMBER_A, null, "祀",
				0xB08A2A, false, 50, SekteScenes.Move.ORBIT, 3.0F, null, 0, null, 0xFFE9A8, 80));
		// 143: a reader comprehends an art: a quiet stamp, no camera move
		SekteScenes.register(BEAT_LEARNED, new SekteScenes.Recipe("kultivator.sekte.paviliun.scene.learned", SekteScenes.Arg.SUBJECT_NAME, null, "悟",
				0x2E8963, false, 50, SekteScenes.Move.NONE, 1.0F, null, 0, null, -1, 60));
	}

	private static void tick(Minecraft client) {
		ticks++;
		PaviliunWorld.tick(client);
		if (ticks % 30L == 0L) {
			motes(client);
		}
	}

	/** Golden glyph-motes rising from the floating sutras of the readers nearby. */
	private static void motes(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null || !Qi.on()) {
			return;
		}
		EffectQuality.Level quality = EffectQuality.level();
		if (quality == EffectQuality.Level.MINIMAL) {
			return;
		}
		AABB box = player.getBoundingBox().inflate(PaviliunRenderer.READER_RANGE);
		int count = 0;
		for (Entity entity : level.getEntities(player, box, e -> e instanceof DiscipleEntity)) {
			if (!(entity instanceof DiscipleEntity d) || d.syncedActivity() != SekteIds.ACT_STUDY || count >= 8) {
				continue;
			}
			count++;
			double rad = Math.toRadians(d.yBodyRot);
			double x = d.getX() - Math.sin(rad) * 0.55D;
			double z = d.getZ() + Math.cos(rad) * 0.55D;
			int n = quality == EffectQuality.Level.MAXIMAL ? 2 : 1;
			for (int i = 0; i < n; i++) {
				level.addParticle(Qi.mote(0xFFD27A, 0.08F, 40), x + (level.getRandom().nextDouble() - 0.5D) * 0.4D, d.getY() + 0.75D,
						z + (level.getRandom().nextDouble() - 0.5D) * 0.4D, 0.0D, 0.03D, 0.0D);
			}
		}
	}

	private PaviliunClient() {
	}
}
