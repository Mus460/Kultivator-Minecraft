package dev.kultivator.client.sekte.seragam;

import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.seragam.SeragamData;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What a disciple's robe looks like under its hall's uniform (DESIGN 36.2.8), read by {@code DiscipleRobeLayer}: the
 * body and trim colour of its sect rank (or the old Dao tint and rank trim when the master left them "Asli"), the cut,
 * and the hall's embroidery sheet in place of the old medallion once the master has saved a design. Only real disciples
 * ({@code Role.DISCIPLE}) wear the uniform; rivals, phantoms and clones keep their looks. The cut rides the render state
 * ({@link #CUT}) because the robe model is posed later, when the submitted models are drawn.
 */
public final class JubahLook {

	/** The robe cut of this render state ({@code SeragamData.CUT_*}); read by the robe model's pose. */
	public static final RenderStateDataKey<Integer> CUT = RenderStateDataKey.create(() -> Kultivator.MOD_ID + ":sekte_jubah_cut");

	/** The hall entry of the disciple drawn in {@code state}, or null (no uniform: keep the old look). */
	public static @Nullable LambangCache.Entry of(DiscipleRenderState state) {
		if (state.role != DiscipleEntity.Role.DISCIPLE.ordinal()) {
			return null;
		}
		UUID owner = SeragamOwners.ownerAt(state.x, state.y, state.z);
		LambangCache.Entry entry = LambangCache.forOwner(owner);
		if (entry == null || (entry.data.flags() & SeragamData.NO_ROBE) != 0) {
			return null;
		}
		return entry;
	}

	/** The robe body RGB: the uniform's colour of the state's rank, else {@code fallback}. */
	public static int robe(@Nullable LambangCache.Entry entry, DiscipleRenderState state, int fallback) {
		int rgb = entry == null ? -1 : entry.data.robeRgb(state.sectRank);
		return rgb < 0 ? fallback : rgb;
	}

	/** The trim RGB: the uniform's trim of the state's rank, else {@code fallback}. */
	public static int trim(@Nullable LambangCache.Entry entry, DiscipleRenderState state, int fallback) {
		int rgb = entry == null ? -1 : entry.data.trimRgb(state.sectRank);
		return rgb < 0 ? fallback : rgb;
	}

	public static int cut(@Nullable LambangCache.Entry entry, DiscipleRenderState state) {
		return entry == null ? SeragamData.CUT_PANJANG : entry.data.cutOf(state.sectRank);
	}

	/** The hall's embroidery sheet, or null to keep {@code robe_emblem.png} (no saved design yet). */
	public static @Nullable net.minecraft.resources.Identifier embroidery(@Nullable LambangCache.Entry entry) {
		return entry == null || !entry.data.designed() ? null : LambangAtlas.robe(entry);
	}

	/** Stores the cut for the robe model's pose. */
	public static void storeCut(DiscipleRenderState state, int cut) {
		((FabricRenderState) state).setData(CUT, cut);
	}

	/** The cut stored in {@code state} ({@code CUT_PANJANG} when none). */
	public static int cutOf(AvatarRenderState state) {
		Integer cut = ((FabricRenderState) state).getDataOrDefault(CUT, SeragamData.CUT_PANJANG);
		return cut == null ? SeragamData.CUT_PANJANG : cut;
	}

	private JubahLook() {
	}
}
