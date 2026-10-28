package dev.kultivator.client.sekte.tetua;

import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.SekteScenes;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.hall.AnggotaPage;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.tetua.Tetua;
import dev.kultivator.sekte.tetua.TetuaPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * The client of module E (DESIGN 36.2.6, 36.4): the receivers of E's payloads (the Balai Tetua screen, the morning
 * report card, the ceremony poses), the page Sekte › Anggota of the Gulungan Utama, the poses of activities 17 and 23
 * ({@link TetuaPoses}), the three scenes of beats 150-152 (through the lead's {@code SekteScenes}, so every 7b camera
 * guard applies), the report card as a HUD element, and the small light ring at the feet of an elder on duty (a few
 * {@code kultivator:qi} ground rings every second, only for elders within 32 blocks, none on Efek Minimal).
 */
public final class TetuaClient {

	private static final double RING_RANGE = 32.0D;
	/** An "open" view within this long after the page asked is the page's answer, not a world opener. */
	private static final long MINTA_MS = 3000L;
	private static boolean initialised;
	private static long ticks;
	private static CompoundTag view = new CompoundTag();
	private static long versi;
	private static long mintaAt = Long.MIN_VALUE / 2;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(TetuaPayloads.Balai.TYPE, (p, context) -> onBalai(p));
		ClientPlayNetworking.registerGlobalReceiver(TetuaPayloads.Report.TYPE, (p, context) -> LaporanCard.show(p.report()));
		ClientPlayNetworking.registerGlobalReceiver(TetuaPayloads.Ceremony.TYPE, (p, context) -> TetuaPoses.onCeremony(p));
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
		ClientDisconnect.register((handler, client) -> client.execute(() -> {
			LaporanCard.clear();
			TetuaPoses.clear();
			view = new CompoundTag();
		}));
		TetuaPoses.init();
		scenes();
		// Gulungan Utama › Sekte › Anggota (DESIGN 43.5, 43.16.2 M06): candidates, ranks, the Balai Tetua and the heir in one
		// page (replaces the hub card "Balai Tetua" and the Calon/Jenjang tablets of the old Aula scroll).
		HubPages.daftar(HubNav.SEKTE, Urutan.ID_ANGGOTA, Urutan.ANGGOTA, AnggotaPage::new);
		// 41 sheets from the world land on their Anggota sub, Perguruanku source (43.12.4)
		HubPages.daftarRumah(GabungLembar.PohonPangkat.LAYAR, java.util.Set.of(), HubNav.SEKTE, Urutan.ID_ANGGOTA, AnggotaPage.PANGKAT,
				AnggotaPage::sumberPerguruan);
		HubPages.daftarRumah(GabungLembar.Politik.LAYAR, java.util.Set.of(GabungLembar.Politik.TAB_MAJELIS), HubNav.SEKTE, Urutan.ID_ANGGOTA,
				AnggotaPage.TETUA, AnggotaPage::sumberPerguruan);
		HubPages.daftarRumah(GabungLembar.Politik.LAYAR, java.util.Set.of(GabungLembar.Politik.TAB_SUKSESI), HubNav.SEKTE, Urutan.ID_ANGGOTA,
				AnggotaPage.PENERUS, AnggotaPage::sumberPerguruan);
	}

	static void send(String op, String arg) {
		if (tersedia()) {
			ClientPlayNetworking.send(new TetuaPayloads.Act(op, arg == null ? "" : arg));
		}
	}

	/** True while the server runs module E (its action payload can be sent). */
	public static boolean tersedia() {
		return ClientPlayNetworking.canSend(TetuaPayloads.Act.TYPE);
	}

	/** The last Balai Tetua view from the server (empty until one came). */
	public static CompoundTag view() {
		return view;
	}

	/** Grows with every view. */
	public static long versi() {
		return versi;
	}

	/** Asks for a fresh view (the page came on screen); the answer is data only, it does not move the Gulungan. */
	public static void minta() {
		mintaAt = Util.getMillis();
		send(TetuaPayloads.OP_OPEN, "");
	}

	private static void onBalai(TetuaPayloads.Balai p) {
		view = p.data() == null ? new CompoundTag() : p.data();
		versi++;
		if (p.open() && Util.getMillis() - mintaAt > MINTA_MS) {
			// opened from the world (or a command): Sekte › Anggota › Tetua of the own hall
			AnggotaPage.sumberAula();
			HubNav.buka(Tujuan.of(HubNav.SEKTE, Urutan.ID_ANGGOTA).sub(AnggotaPage.TETUA));
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
