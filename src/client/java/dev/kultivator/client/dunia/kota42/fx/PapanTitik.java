package dev.kultivator.client.dunia.kota42.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.tempat.ui.TeksDunia;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The engraved names of the towns (DESIGN 42.12.6 "Papan billboard"): the name on a gravestone ({@code nisan}, stone panel) and
 * the street name on its post ({@code nama_jalan}, wood panel), drawn with the shared world-text pattern {@link TeksDunia}
 * (vanilla text, polygon offset, within 24 blocks, the nearest twelve at once, six on Minimal). The names are the data strings
 * of the titik payload ({@code TitikVisual.teks}); nothing is invented here. Client thread only.
 */
final class PapanTitik {

	static void gambar(PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (TitikPeta.teks.isEmpty()) {
			return;
		}
		var cam = Bingkai.kamera;
		for (TitikPeta.Teks t : TitikPeta.teks) {
			double dx = t.x() - cam.x;
			double dy = t.y() - cam.y;
			double dz = t.z() - cam.z;
			double d2 = dx * dx + dy * dy + dz * dz;
			if (d2 > 24.0D * 24.0D || !TeksDunia.izin(t, d2)) {
				continue;
			}
			int light = Bingkai.cahaya(t.x(), t.y() + 0.5D, t.z());
			TeksDunia.papan(pose, collector, camera, dx, dy + (t.nisan() ? 1.45D : 0.55D), dz, List.of(Component.literal(t.teks())),
					t.nisan() ? Palette.Gaya.BATU : Palette.Gaya.KAYU, t.nisan() ? 0.12F : 0.15F, light);
		}
	}

	private PapanTitik() {
	}
}
