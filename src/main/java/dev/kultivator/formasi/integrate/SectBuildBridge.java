package dev.kultivator.formasi.integrate;

import dev.kultivator.formasi.SectFormationSlots;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.FormasiAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Phase 6b links (DESIGN 24.6.2): the owner's Titik Formasi anchors from {@code EngineApi.formasiAnchors} (empty until
 * P6b-cetak fills it) and the optional pattern component request, refused for specs that do not fit the anchor.
 */
public final class SectBuildBridge {

	public static List<FormasiAnchor> anchors(UUID owner) {
		return EngineApi.get().formasiAnchors(owner);
	}

	/** The anchor nearest to {@code pos} within its own radius + 8, or null. */
	public static @Nullable FormasiAnchor near(UUID owner, BlockPos pos) {
		FormasiAnchor best = null;
		double bestDistance = Double.MAX_VALUE;
		for (FormasiAnchor anchor : anchors(owner)) {
			double distance = anchor.centre().distSqr(pos);
			double reach = anchor.radius() + 8.0D;
			if (distance <= reach * reach && distance < bestDistance) {
				best = anchor;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** Asks the 6b engine for a pattern component; null on success, else why not. */
	public static @Nullable Component request(UUID owner, FormasiAnchor anchor, FormationSpec spec, int rot) {
		if (!SectFormationSlots.fits(spec, anchor.radius())) {
			return Component.translatable("kultivator.formasi.anchor.no_fit", spec.name(), anchor.radius());
		}
		return EngineApi.get().requestPattern(owner, anchor.id(), spec.id(), rot);
	}

	private SectBuildBridge() {
	}
}
