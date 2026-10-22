package dev.kultivator.client.sekte.tangga;

import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.epic.SekteScenes;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.client.hub.HubCards;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.sekte.SektePoses;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.tangga.Tangga;
import dev.kultivator.sekte.tangga.TanggaPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Module G on the client (DESIGN 36.2.4): the illusory stair ({@link TanggaIlusiRenderer}) from the server's
 * {@code TanggaPayloads.State} ({@link TanggaIlusi}), the climbers' scroll on the HUD ({@link TanggaHud}), the climb
 * pose of activity 21 ({@code SekteIds.ACT_CLIMB}: the cupped-fist salute in the line, bowed and trembling under the
 * stair's pressure, kneeling before an illusion, both arms raised on the summit, head hung when it turned back), the
 * scene recipes of beats 130 "Puncak!" and 131 (a disciple's Ujian Hati on the peak) through {@code SekteScenes} (every
 * 7b camera guard applies: Penuh/Singkat/Mati, Backspace, danger), and the hub card "Catatan Tangga" in the Murid
 * section. Reads only its own payload. One line in {@code SekteContentClient}.
 */
public final class TanggaClient {

	private static boolean initialised;

	public static void init() {
		if (initialised || !Tangga.initialised()) {
			// The server side of the module is off: no payload type, nothing to draw.
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(TanggaPayloads.State.TYPE, (payload, context) -> TanggaIlusi.onState(payload));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> TanggaIlusi.tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TanggaIlusi.clear());
		LevelRenderEvents.COLLECT_SUBMITS.register(TanggaIlusiRenderer::collect);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("sekte_tangga_gulungan"), new TanggaHud());
		SektePoses.register(SekteIds.ACT_CLIMB, TanggaClient::pose, List.of());
		HubCards.register(HubNav.MURID, "sekte_tangga_catatan", Glyphs.TALENT, null, () -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.getConnection() != null) {
				minecraft.gui.setScreen(null);
				minecraft.getConnection().sendCommand("kultivasi perguruan " + SekteIds.G);
			}
		});
		scenes();
	}

	private static void scenes() {
		// 130 "Puncak!": a stamp and a short orbit around the one who reached the summit, golden sky, a short triumph.
		SekteScenes.register(Tangga.BEAT_PEAK, new SekteScenes.Recipe("kultivator.sekte.tangga.scene.peak", SekteScenes.Arg.SUBJECT_NAME, null, "天",
				0xF2C14E, false, 60, SekteScenes.Move.ORBIT, 4.0F, MusicCue.Cue.M_TRIUMPH, 8, SkyDirector.Grade.SK_GOLD, 0xFFF0C0, 100));
		// 131 a disciple's Ujian Hati on the summit: a rising crane shot, the jade sky, no music.
		SekteScenes.register(Tangga.BEAT_HEART_PEAK, new SekteScenes.Recipe("kultivator.sekte.tangga.scene.heart", SekteScenes.Arg.SUBJECT_NAME, null,
				"心", 0x5FE3C0, false, 60, SekteScenes.Move.CRANE_UP, 4.0F, null, 0, SkyDirector.Grade.SK_JADE, -1, 90));
	}

	/** The climb pose (activity 21), applied only while the body stands still (DutyPoses' rule). */
	private static void pose(PlayerModel model, DiscipleRenderState state, float s, float t) {
		TanggaIlusi.Climber climber = TanggaIlusi.climber(state.id);
		int what = climber == null ? -1 : climber.state;
		if (what == TanggaPayloads.C_PEAK) {
			// on the summit: both arms raised to the sky, chin up
			float sway = Mth.sin(t * 0.15F) * 0.06F;
			set(model.rightArm, s, -2.9F + sway, 0.0F, 0.35F);
			set(model.leftArm, s, -2.9F - sway, 0.0F, -0.35F);
			model.head.xRot = Mth.lerp(s, model.head.xRot, -0.35F);
			return;
		}
		if (what == TanggaPayloads.C_GAVE_UP) {
			// turned back: head hung, arms limp
			model.head.xRot = Mth.lerp(s, model.head.xRot, 0.75F);
			set(model.rightArm, s, 0.1F, 0.0F, 0.05F);
			set(model.leftArm, s, 0.1F, 0.0F, -0.05F);
			model.body.xRot = Mth.lerp(s, model.body.xRot, 0.12F);
			return;
		}
		if (what == TanggaPayloads.C_KNEEL || what == TanggaPayloads.C_STOP || what == TanggaPayloads.C_CLIMB && climber.step > 0.5F) {
			// under the stair's pressure: bent forward, hands braced on the knees, trembling (harder before an illusion)
			boolean kneel = what == TanggaPayloads.C_KNEEL;
			float tremble = Mth.sin(t * (kneel ? 2.4F : 1.3F)) * (kneel ? 0.05F : 0.025F);
			float bend = kneel ? 0.7F : 0.45F;
			model.body.xRot = Mth.lerp(s, model.body.xRot, bend + tremble);
			model.head.xRot = Mth.lerp(s, model.head.xRot, kneel ? 0.2F : 0.35F);
			set(model.rightArm, s, -0.75F + tremble, 0.0F, 0.12F);
			set(model.leftArm, s, -0.75F - tremble, 0.0F, -0.12F);
			if (kneel) {
				set(model.rightLeg, s, -1.25F, 0.0F, 0.05F);
				set(model.leftLeg, s, 0.35F, 0.0F, -0.05F);
			} else {
				set(model.rightLeg, s, -0.15F, 0.0F, 0.06F);
				set(model.leftLeg, s, 0.1F, 0.0F, -0.06F);
			}
			lower(model, s, bend);
			return;
		}
		// in the line before the Batu Hati: the cupped-fist salute (baoquan li), standing straight
		set(model.rightArm, s, -1.15F, -0.55F, 0.0F);
		set(model.leftArm, s, -1.15F, 0.55F, 0.0F);
		model.head.xRot = Mth.lerp(s, model.head.xRot, 0.12F);
	}

	private static void set(ModelPart part, float s, float x, float y, float z) {
		part.xRot = Mth.lerp(s, part.xRot, x);
		part.yRot = Mth.lerp(s, part.yRot, y);
		part.zRot = Mth.lerp(s, part.zRot, z);
	}

	/** A bent body moves the head and shoulders forward and down with it (like DutyPoses' bend). */
	private static void lower(PlayerModel model, float s, float bend) {
		float k = s * bend / 0.5F;
		model.head.y += 4.2F * k;
		model.body.y += 3.2F * k;
		model.rightArm.y += 3.2F * k;
		model.leftArm.y += 3.2F * k;
		model.rightLeg.z += 4.0F * k;
		model.leftLeg.z += 4.0F * k;
	}

	private TanggaClient() {
	}
}
