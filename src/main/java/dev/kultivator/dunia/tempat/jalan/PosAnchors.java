package dev.kultivator.dunia.tempat.jalan;

import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.tempat.TempatZones;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * The anchors of road-side posts (module {@code pos}, DESIGN 38.C.6.3): {@code pos/tonggak}, {@code pos/kuil_jalan},
 * {@code pos/singgah}, {@code pos/singgah_besar}, {@code pos/dagang}, {@code pos/kemah_begal},
 * {@code pos/penyeberangan}. On load each publishes its site (map marker kinds 36-39) and, except the bandit camp,
 * registers its box with {@link TempatZones} (protected from Mode Kehancuran, safe ground: no ambush in a rest post,
 * 38.0 rule 11). While players are near: milestones send their carved board ({@link PapanTonggak}) and are touched,
 * ferry rafts are moored, bandit camps wake. The other
 * anchors inside the posts ({@code kotabesar/layanan}, {@code kota/kursi_pengembara}, {@code kota/papan},
 * {@code kotabesar/cakram}) belong to their modules and need no code here.
 */
final class PosAnchors implements AnchorHandler {

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		String kind = anchor.kind();
		String situs = anchor.data().getStringOr("situs", kind);
		long site = anchor.siteOf(situs);
		BlockPos at = anchor.origin() != null ? anchor.origin() : anchor.blockPosition();
		DuniaSites.publish(level, situs, at, 1);
		if (kind.equals("pos/kemah_begal")) {
			return;
		}
		int r = Math.max(4, anchor.protectRadius());
		BlockPos c = anchor.blockPosition();
		BoundingBox box = new BoundingBox(c.getX() - r, c.getY() - 16, c.getZ() - r, c.getX() + r, c.getY() + 24, c.getZ() + r);
		TempatZones.daftar(site, level.dimension(), box, true, situs, kind.equals("pos/singgah_besar") || kind.equals("pos/dagang") ? 2 : 1);
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		switch (anchor.kind()) {
			case "pos/tonggak" -> {
				PapanTonggak.dekat(level, anchor, within);
				for (ServerPlayer player : within) {
					if (player.distanceToSqr(anchor) <= Tonggak.JARAK * Tonggak.JARAK && !player.isSpectator()) {
						Tonggak.sentuh(level, anchor, player);
					}
				}
			}
			case "pos/penyeberangan" -> Penyeberangan.jaga(level, anchor);
			case "pos/kemah_begal" -> KemahBegal.dekat(level, anchor, within);
			default -> {
			}
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		if (anchor.kind().equals("pos/penyeberangan")) {
			Penyeberangan.lepas(anchor);
		}
	}

	@Override
	public int radius() {
		return 48;
	}
}
