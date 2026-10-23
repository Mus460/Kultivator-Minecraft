package dev.kultivator.sekte.langit.gerak;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.effect.b.TransportNetwork;
import dev.kultivator.sekte.langit.api.SkyHallView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The movement half of {@code /kultivasi uji langit_sekte ...} (DESIGN 37.7.9; the command tree itself is 37G's
 * {@code LangitTests}): each method acts on the tester's own sky hall (the nearest one they belong to) and returns the
 * lines to print. Op-only by the caller. Nothing here changes a block.
 */
public final class GerakUji {

	/** The nearest sky hall of {@code level} the player belongs to, or null. */
	public static @Nullable SkyHallView hallOf(ServerPlayer player) {
		SkyHallView best = null;
		double bestDistance = Double.MAX_VALUE;
		for (SkyHallView hall : LangitJatuh.halls(player.level())) {
			if (!LangitGerak.member(hall.owner(), player)) {
				continue;
			}
			double distance = hall.horizontal(player.position());
			if (distance < bestDistance) {
				bestDistance = distance;
				best = hall;
			}
		}
		return best;
	}

	/**
	 * {@code jatuh [<tinggi>] [terjun]}: moves the tester 4 blocks outside the island's box (towards where they stand),
	 * {@code height} blocks under the island's lowest block, falling at 1 block a tick; {@code dive} starts a Cloud Dive
	 * at once (when the Jaring Awan formation runs).
	 */
	public static Component jatuh(ServerPlayer player, int height, boolean dive) {
		SkyHallView hall = hallOf(player);
		if (hall == null) {
			return Component.translatable("kultivator.sekte_langit.feri.bukan_langit");
		}
		Vec3 out = outside(hall, player.position(), hall.skyFloorY() - Math.max(1, height));
		player.stopRiding();
		player.teleportTo(player.level(), out.x, out.y, out.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.setDeltaMovement(0.0D, -1.0D, 0.0D);
		player.needsSync = true;
		if (dive) {
			LangitJatuh.markGround(player.getUUID(), player.level().getGameTime());
			if (!TerjunAwan.force(player.level(), hall, player)) {
				return Component.translatable("kultivator.sekte_langit.gerak.uji.terjun_tidak");
			}
		}
		return Component.translatable("kultivator.sekte_langit.gerak.uji.jatuh", Math.round(out.x), Math.round(out.y), Math.round(out.z));
	}

	/** {@code jatuh murid}: the tester's nearest disciple is pushed off the rim (the net must catch it). */
	public static Component jatuhMurid(ServerPlayer player) {
		SkyHallView hall = hallOf(player);
		if (hall == null) {
			return Component.translatable("kultivator.sekte_langit.feri.bukan_langit");
		}
		DiscipleEntity nearest = null;
		double best = Double.MAX_VALUE;
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			double d = disciple.distanceToSqr(player);
			if (disciple.level() == player.level() && disciple.isAlive() && d < best) {
				best = d;
				nearest = disciple;
			}
		}
		if (nearest == null) {
			return Component.translatable("kultivator.sekte_langit.gerak.uji.tanpa_murid");
		}
		Vec3 out = outside(hall, nearest.position(), hall.skyFloorY() - 2);
		nearest.stopRiding();
		nearest.teleportTo(out.x, out.y, out.z);
		nearest.setDeltaMovement(0.0D, -1.0D, 0.0D);
		nearest.needsSync = true;
		return Component.translatable("kultivator.sekte_langit.gerak.uji.jatuh_murid", nearest.getDisplayName());
	}

	/** {@code feri}: calls the ferry to the tester's side now. */
	public static Component feri(ServerPlayer player) {
		Component why = FeriPanggil.callNear(player);
		return why != null ? why : Component.translatable("kultivator.sekte_langit.gerak.uji.feri");
	}

	/** {@code formasi}: the tester's Cloud Transfer nodes and whether each pair links. */
	public static List<Component> formasi(ServerPlayer player) {
		List<Component> lines = new ArrayList<>();
		ServerLevel level = player.level();
		List<CoreView> nodes = new ArrayList<>();
		for (CoreView core : TransportNetwork.nodes(player.getUUID())) {
			if (core.dim().equals(level.dimension())) {
				nodes.add(core);
			}
		}
		for (CoreView core : nodes) {
			if (!TransportNetwork.awan(core)) {
				continue;
			}
			BlockPos eye = core.eye();
			boolean site = TransportNetwork.awanSite(core.owner(), level, eye);
			lines.add(Component.translatable("kultivator.sekte_langit.gerak.uji.simpul", TransportNetwork.name(core), eye.getX(), eye.getY(),
					eye.getZ(), core.state().label(), Component.translatable(site ? "gui.yes" : "gui.no")));
			for (CoreView other : nodes) {
				if (other.id() != core.id()) {
					lines.add(Component.translatable("kultivator.sekte_langit.gerak.uji.tautan", TransportNetwork.name(core), TransportNetwork.name(other),
							Component.translatable(TransportNetwork.linked(level, core, other) ? "gui.yes" : "gui.no")));
				}
			}
		}
		if (lines.isEmpty()) {
			lines.add(Component.translatable("kultivator.sekte_langit.gerak.uji.tanpa_simpul"));
		}
		return lines;
	}

	/** {@code patroli}: starts a sword patrol at the tester's hall now (rules apply). */
	public static Component patroli(ServerPlayer player) {
		SkyHallView hall = hallOf(player);
		int sent = hall == null ? 0 : PatroliPedang.startNow(player.level(), hall.owner());
		return Component.translatable("kultivator.sekte_langit.gerak.uji.patroli", sent);
	}

	/** {@code tangga}: the tester's nearest disciple starts the First Climb from wherever it stands. */
	public static Component tangga(ServerPlayer player) {
		SkyHallView hall = hallOf(player);
		if (hall == null) {
			return Component.translatable("kultivator.sekte_langit.feri.bukan_langit");
		}
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			if (disciple.level() == player.level() && disciple.isAlive() && disciple.distanceToSqr(player) < 32.0D * 32.0D
					&& NaikTanggaGoal.start(player.level(), hall.owner(), disciple)) {
				return Component.translatable("kultivator.sekte_langit.gerak.tangga.mulai", disciple.getDisplayName());
			}
		}
		return Component.translatable("kultivator.sekte_langit.gerak.uji.tanpa_murid");
	}

	/** A point 4 blocks outside the island's box towards {@code from}, at height {@code y}. */
	static Vec3 outside(SkyHallView hall, Vec3 from, double y) {
		BoundingBox box = hall.islandBox();
		double mx = (box.minX() + box.maxX() + 1) * 0.5D;
		double mz = (box.minZ() + box.maxZ() + 1) * 0.5D;
		double dx = from.x - mx;
		double dz = from.z - mz;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-3D) {
			dx = 0.0D;
			dz = 1.0D;
		} else {
			dx /= len;
			dz /= len;
		}
		double halfX = (box.maxX() - box.minX() + 1) * 0.5D + 4.0D;
		double halfZ = (box.maxZ() - box.minZ() + 1) * 0.5D + 4.0D;
		double t = Math.min(Math.abs(dx) < 1.0E-6D ? Double.MAX_VALUE : halfX / Math.abs(dx),
				Math.abs(dz) < 1.0E-6D ? Double.MAX_VALUE : halfZ / Math.abs(dz));
		return new Vec3(mx + dx * t, y, mz + dz * t);
	}

	private GerakUji() {
	}
}
