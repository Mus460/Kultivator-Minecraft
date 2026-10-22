package dev.kultivator.client.sekte.tetua;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.SekteScenes;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.hub.HubCards;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.tetua.Tetua;
import dev.kultivator.sekte.tetua.TetuaPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * The client of module E (DESIGN 36.2.6, 36.4): the receivers of E's payloads (the Balai Tetua screen, the morning
 * report card, the ceremony poses), the hub card SEKTE "Balai Tetua", the poses of activities 17 and 23
 * ({@link TetuaPoses}), the three scenes of beats 150-152 (through the lead's {@code SekteScenes}, so every 7b camera
 * guard applies), the report card as a HUD element, and the small light ring at the feet of an elder on duty (a few
 * {@code kultivator:qi} ground rings every second, only for elders within 32 blocks, none on Efek Minimal).
 */
public final class TetuaClient {

	private static final double RING_RANGE = 32.0D;
	private static boolean initialised;
	private static long ticks;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(TetuaPayloads.Balai.TYPE, (p, context) -> onBalai(p));
		ClientPlayNetworking.registerGlobalReceiver(TetuaPayloads.Report.TYPE, (p, context) -> LaporanCard.show(p.report()));
		ClientPlayNetworking.registerGlobalReceiver(TetuaPayloads.Ceremony.TYPE, (p, context) -> TetuaPoses.onCeremony(p));
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			LaporanCard.clear();
			TetuaPoses.clear();
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("sekte_tetua_laporan"), new LaporanCard());
		TetuaPoses.init();
		scenes();
		// Hub › Sekte › "Balai Tetua": the offices, the morning report, the heir and the lineage.
		HubCards.register(HubNav.SEKTE, "sekte_tetua_balai", Glyphs.HALL, null, () -> {
			HubNav.expectChild(BalaiTetuaScreen.class, HubNav.SEKTE);
			send(TetuaPayloads.OP_OPEN, "");
		});
	}

	static void send(String op, String arg) {
		if (ClientPlayNetworking.canSend(TetuaPayloads.Act.TYPE)) {
			ClientPlayNetworking.send(new TetuaPayloads.Act(op, arg == null ? "" : arg));
		}
	}

	private static void onBalai(TetuaPayloads.Balai p) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof BalaiTetuaScreen screen) {
			screen.update(p.data());
		} else if (p.open()) {
			minecraft.gui.setScreen(new BalaiTetuaScreen(p.data()));
		}
	}

	/** Beats 150-152 (E's range of the EXTRA moment). */
	private static void scenes() {
		// 150: the Succession Ceremony's climax, a full card over a rising crane shot, the glory theme, a golden sky
		SekteScenes.register(Tetua.BEAT_THRONE, new SekteScenes.Recipe("kultivator.sekte.tetua.scene.throne", SekteScenes.Arg.NUMBER_A,
				"kultivator.sekte.tetua.scene.throne.sub", "嗣", 0xB3281D, true, 90, SekteScenes.Move.CRANE_UP, 6.0F, MusicCue.Cue.M_GLORY, 16,
				SkyDirector.Grade.SK_GOLD, 0xFFE9A8, 180));
		// 151: the morning report handed over: a quiet stamp, no camera move
		SekteScenes.register(Tetua.BEAT_REPORT, new SekteScenes.Recipe("kultivator.sekte.tetua.scene.report", SekteScenes.Arg.SUBJECT_NAME, null,
				"報", 0xB3281D, false, 50, SekteScenes.Move.NONE, 1.0F, null, 0, null, -1, 60));
		// 152: an elder appointed while the master watches: a stamp and a short orbit
		SekteScenes.register(Tetua.BEAT_APPOINT, SekteScenes.Recipe.stamp("kultivator.sekte.tetua.scene.appoint", SekteScenes.Arg.SUBJECT_NAME, "任",
				0xB08A2A));
	}

	private static void tick(Minecraft client) {
		ticks++;
		LaporanCard.tick();
		TetuaPoses.tick();
		if (ticks % 20L == 0L) {
			rings(client);
		}
	}

	/** The light ring at the feet of each elder at its desk (Normal: one ring a second, Maximal: two). */
	private static void rings(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null || !Qi.on()) {
			return;
		}
		EffectQuality.Level quality = EffectQuality.level();
		if (quality == EffectQuality.Level.MINIMAL) {
			return;
		}
		AABB box = player.getBoundingBox().inflate(RING_RANGE);
		int count = 0;
		for (Entity entity : level.getEntities(player, box, e -> e instanceof DiscipleEntity)) {
			if (!(entity instanceof DiscipleEntity disciple) || disciple.syncedActivity() != SekteIds.ACT_OFFICE || count >= 8) {
				continue;
			}
			count++;
			int rgb = 0xFFD54A;
			level.addParticle(Qi.groundRing(rgb, 0.6F, 1.6F, 30), disciple.getX(), disciple.getY() + 0.05D, disciple.getZ(), 0.0D, 0.0D, 0.0D);
			if (quality == EffectQuality.Level.MAXIMAL) {
				level.addParticle(Qi.mote(rgb, 0.12F, 30), disciple.getX() + (level.getRandom().nextDouble() - 0.5D) * 0.8D,
						disciple.getY() + 0.2D, disciple.getZ() + (level.getRandom().nextDouble() - 0.5D) * 0.8D, 0.0D, 0.02D, 0.0D);
			}
		}
	}

	private TetuaClient() {
	}
}
