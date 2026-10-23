package dev.kultivator.sekte.langit.main;

import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.SkyHallView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Weather immunity of the sky islands on the server (DESIGN 37.7.2): read by {@code mixin.ServerLevelPrecipitationMixin}.
 * Lightning on the islands is the build module's ({@code RuangUdara}, visual-only bolts); the rain and snow pictures
 * are the visual module's client mixins. Nothing here changes a block, the weather or the time.
 */
public final class LangitCuaca {

	/**
	 * True when the precipitation step of the column at {@code pos} should be skipped: Sekte Langit and
	 * {@code kebalSalju} on, the overworld, and the column's top (motion blocking) inside a sky hall's volume at or above
	 * its lowest island block. Cheap: one heightmap read and the API's cached box list.
	 */
	public static boolean blockPrecipitation(ServerLevel level, BlockPos pos) {
		LangitConfig config = LangitConfig.get();
		if (!config.aktif || !config.kebalSalju || level.dimension() != Level.OVERWORLD) {
			return false;
		}
		try {
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
			SkyHallView view = LangitApi.get().at(level, top);
			return view != null && view.inIsland(top) && top.getY() >= view.skyFloorY();
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private LangitCuaca() {
	}
}
