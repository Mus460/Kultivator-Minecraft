package dev.kultivator.client.sekte.kebun;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.SekteScenes;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.sekte.wilayah.WilayahPage;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.kebun.Kebun;
import dev.kultivator.sekte.kebun.KebunPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The client of module W (DESIGN 36.2.10, 36.4 W; one line in {@code SekteContentClient}): the receivers of W's payloads
 * (the page {@link KebunScreen}, the world picture for {@link TanamanRenderer}, the moments of {@link KebunFx}), the page
 * Sekte › Wilayah ({@link WilayahPage}, DESIGN 43.5), the poses of activities 15 and 16 ({@link KebunPoses}), the two scenes of beats 190-191
 * (through the lead's {@code SekteScenes}, so every 7b camera guard applies), and two small ambient touches:
 * <ul>
 * <li>the miners at work: the ring of a pick from inside the mountain every 6 s (a second, deeper strike with two or
 * more miners), only within 24 blocks of the mouth;</li>
 * <li>the gardeners at work: a few drops from the watering gourd every half second (Normal and Maximal, at most six
 * gardeners within 16 blocks).</li>
 * </ul>
 */
public final class KebunClient {

	private static final double SOUND_RANGE = 24.0D;
	private static final double DROP_RANGE = 16.0D;
	private static boolean initialised;
	private static long ticks;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(KebunPayloads.Open.TYPE, (p, context) -> onOpen(p));
		ClientPlayNetworking.registerGlobalReceiver(KebunPayloads.World.TYPE, (p, context) -> TanamanRenderer.onWorld(p.data()));
		ClientPlayNetworking.registerGlobalReceiver(KebunPayloads.Fx.TYPE, (p, context) -> KebunFx.on(p));
		ClientTickEvents.END_CLIENT_TICK.register(KebunClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> TanamanRenderer.clear()));
		TanamanRenderer.init();
		KebunPoses.init();
		scenes();
		// Sekte › Wilayah (DESIGN 43.5): Pertahanan, Desa & Pakta, Cabang, Kebun & Tambang, Langit.
		WilayahPage.daftar();
		// the board's log in the one chronicle of Sekte › Ikhtisar › Catatan (43.11 G37)
		HallClient.sumberCatatan("kebun", net.minecraft.network.chat.Component.translatable("kultivator.gui43.m10.kebun.catatan"), KebunScreen::entri);
	}

	/** A button of the board (the server checks the owner and everything else). */
	static void send(String action, int a, String b) {
		if (ClientPlayNetworking.canSend(KebunPayloads.Action.TYPE)) {
			ClientPlayNetworking.send(new KebunPayloads.Action(action, a, b == null ? "" : b));
		}
	}

	/** A fresh state: the shown page picks it up; from the world (soil, mine mouth, command) the Gulungan opens on it. */
	private static void onOpen(KebunPayloads.Open p) {
		boolean diminta = KebunScreen.dimintaBaru();
		KebunScreen.terima(p.page(), p.data());
		if (!KebunScreen.ditampilkan() && !diminta) {
			HubNav.buka(Tujuan.of(HubNav.SEKTE, Urutan.ID_WILAYAH).sub(WilayahPage.SUB_KEBUN));
		}
	}

	/** Beats 190-191 (W's range of the EXTRA moment). */
	private static void scenes() {
		// 190: a rare bloom at harvest (an extra herb three times as old): a jade stamp over a slow push toward the bed
		SekteScenes.register(Kebun.BEAT_BLOOM, new SekteScenes.Recipe("kultivator.sekte.kebun.scene.bloom", SekteScenes.Arg.NUMBER_A, null, "華",
				0x2E8963, false, 60, SekteScenes.Move.PUSH, 3.5F, MusicCue.Cue.M_GENTLE, 8, null, -1, 90));
		// 191: the Inti Roh Gunung found in the deep: a full card, a low heroic shot of the miner, a golden sky and a flash
		SekteScenes.register(Kebun.BEAT_CORE, new SekteScenes.Recipe("kultivator.sekte.kebun.scene.core", SekteScenes.Arg.NUMBER_A,
				"kultivator.sekte.kebun.scene.core.sub", "石", 0xB08A2A, true, 80, SekteScenes.Move.LOW_HERO, 4.5F, MusicCue.Cue.M_TRIUMPH, 10,
				SkyDirector.Grade.SK_GOLD, 0xFFE9A8, 140));
	}

	private static void tick(Minecraft client) {
		ticks++;
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null) {
			return;
		}
		if (ticks % 20L == 0L) {
			TanamanRenderer.prune();
		}
		picks(level, player);
		if (ticks % 10L == 0L) {
			drops(level, player);
		}
	}

	/** The ring of picks from inside the mountain while a shift is at work. */
	private static void picks(ClientLevel level, LocalPlayer player) {
		for (TanamanRenderer.Site site : TanamanRenderer.sites()) {
			TanamanRenderer.Mine mine = site.mine;
			if (mine == null || mine.miners <= 0 || player.distanceToSqr(Vec3.atCenterOf(mine.mouth)) > SOUND_RANGE * SOUND_RANGE) {
				continue;
			}
			long beat = (ticks + Math.floorMod(site.hall, 120L)) % 120L;
			BlockPos deep = mine.inside.length > 1 ? mine.inside[1] : mine.mouth;
			if (beat == 0L) {
				level.playLocalSound(deep.getX() + 0.5D, deep.getY() + 1.0D, deep.getZ() + 0.5D, SoundEvents.STONE_HIT, SoundSource.NEUTRAL,
						0.6F, 0.8F + level.getRandom().nextFloat() * 0.25F, false);
			} else if (beat == 9L && mine.miners >= 2) {
				BlockPos other = mine.inside.length > 3 ? mine.inside[3] : deep;
				level.playLocalSound(other.getX() + 0.5D, other.getY() + 1.0D, other.getZ() + 0.5D, SoundEvents.DEEPSLATE_HIT, SoundSource.NEUTRAL,
						0.5F, 0.7F + level.getRandom().nextFloat() * 0.2F, false);
			}
		}
	}

	/** Water drops from the gourds of the gardeners at work (Normal and Maximal). */
	private static void drops(ClientLevel level, LocalPlayer player) {
		if (!Qi.on() || EffectQuality.level() == EffectQuality.Level.MINIMAL) {
			return;
		}
		AABB box = player.getBoundingBox().inflate(DROP_RANGE);
		int count = 0;
		for (Entity entity : level.getEntities(player, box, e -> e instanceof DiscipleEntity)) {
			if (!(entity instanceof DiscipleEntity disciple) || disciple.syncedActivity() != SekteIds.ACT_TEND || count >= 6) {
				continue;
			}
			count++;
			float yaw = disciple.yBodyRot * Mth.DEG_TO_RAD;
			double fx = -Mth.sin(yaw);
			double fz = Mth.cos(yaw);
			// the gourd's spout ahead of the right hand, a little off to the side
			double x = disciple.getX() + fx * 0.75D - fz * 0.25D;
			double z = disciple.getZ() + fz * 0.75D + fx * 0.25D;
			double y = disciple.getY() + 0.55D;
			for (int i = 0; i < 2; i++) {
				level.addParticle(Qi.mote(0x9FD8FF, 0.06F, 10), x + (level.getRandom().nextDouble() - 0.5D) * 0.1D, y,
						z + (level.getRandom().nextDouble() - 0.5D) * 0.1D, 0.0D, -0.12D, 0.0D);
			}
		}
	}

	private KebunClient() {
	}
}
