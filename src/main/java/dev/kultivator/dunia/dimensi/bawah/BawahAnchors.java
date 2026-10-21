package dev.kultivator.dunia.dimensi.bawah;

import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.dimensi.Dimensi;
import dev.kultivator.dunia.dimensi.DimensiConfig;
import dev.kultivator.dunia.dimensi.TandaEntity;
import dev.kultivator.dunia.dimensi.laut.YakshaEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The handler of every {@code bawah/*} anchor (DESIGN 34.4.1, contract C34.S1). Anchors stand where the templates of
 * {@code tools/gen_dunia_dimensi.py} put them; this handler turns them into people and marks while a player is near
 * and removes them when the chunk unloads (nothing of it is saved):
 * <ul>
 * <li>{@code teras}: the arrival ring; {@code gerbang_pulang}: the Jalan Pulang gate (return to the body);
 * {@code buku}: the Book of Life and Death; {@code dizang}: Dizang's shrine; {@code altar}: the incense altar of an
 * overworld Kuil Gerbang Hantu (the soul's way in).</li>
 * <li>{@code meng_po}, {@code pengadilan} (data {@code court}), {@code penjaga} (data {@code role}, {@code court}),
 * {@code yama}: the officials, one each, facing the anchor's (rotated) direction.</li>
 * <li>{@code huangquan}: up to two wandering souls on the Yellow Springs road; {@code naihe}: crossing and escorts.</li>
 * <li>{@code arwah} (data {@code peran}): a quest ghost of Desa Arwah; {@code hantu}: hungry ghosts (at most
 * {@code dimensi.ghostsPerPlayer} around each player); {@code yaksha}: up to two court patrols.</li>
 * </ul>
 * Every spawn asks {@link DuniaBudget} first (the server caps of DESIGN 34.4.4).
 */
public final class BawahAnchors implements AnchorHandler {

	private static final Map<UUID, List<Entity>> MADE = new HashMap<>();

	static void clear() {
		MADE.clear();
	}

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		int kind = markKind(anchor.what());
		if (kind >= 0) {
			// never add an entity from inside the chunk's entity-load callback: one tick later, if still loaded
			Dimensi.schedule(1, () -> {
				if (!anchor.isRemoved() && anchor.level() == level) {
					ensureMark(level, anchor, kind);
				}
			});
		}
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		List<Entity> made = made(anchor);
		int kind = markKind(anchor.what());
		if (kind >= 0) {
			ensureMark(level, anchor, kind);
		}
		int realm = BawahPeople.realmNear(level, anchor.position());
		switch (anchor.what()) {
			case "altar" -> SoulJourney.altarPass(level, anchor.position(), within);
			case "naihe" -> Courts.naihePass(level, anchor, within);
			case "meng_po" -> ensureOne(level, anchor, made, ArwahEntity.MENG_PO, 0, realm);
			case "pengadilan" -> ensureOne(level, anchor, made, ArwahEntity.HAKIM, anchor.data().getIntOr("court", 1), realm);
			case "yama" -> ensureOne(level, anchor, made, ArwahEntity.RAJA_YAMA, 11, realm);
			case "penjaga" -> ensureOne(level, anchor, made, "kuda".equals(anchor.data().getStringOr("role", "lembu"))
					? ArwahEntity.MUKA_KUDA : ArwahEntity.KEPALA_LEMBU, anchor.data().getIntOr("court", 1), realm);
			case "arwah" -> {
				ArwahEntity ghost = ensureOne(level, anchor, made, ArwahEntity.ARWAH, 0, realm);
				if (ghost != null && ghost.following() == null && ghost.note().isEmpty()) {
					ghost.setNote(anchor.data().getStringOr("peran", "pesan"));
					ghost.setFlag(dev.kultivator.dunia.dimensi.DimensiNpc.FLAG_QUEST, true);
				}
			}
			case "huangquan" -> wanderers(level, anchor, made, realm);
			case "hantu" -> hungryGhosts(level, anchor, made, within, realm);
			case "yaksha" -> patrol(level, anchor, made, realm);
			default -> {
			}
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		List<Entity> made = MADE.remove(anchor.getUUID());
		if (made != null) {
			// a tick later: never remove entities from inside the chunk's entity-unload callback
			Dimensi.schedule(1, () -> {
				for (Entity entity : made) {
					if (!entity.isRemoved() && !(entity instanceof ArwahEntity ghost && ghost.following() != null)) {
						entity.discard();
					}
				}
			});
		}
	}

	@Override
	public int radius() {
		return 48;
	}

	// ================================================================== helpers

	private static int markKind(String what) {
		return switch (what) {
			case "teras" -> TandaEntity.TERAS;
			case "gerbang_pulang" -> TandaEntity.GERBANG;
			case "buku" -> TandaEntity.BUKU;
			case "dizang" -> TandaEntity.DIZANG;
			case "altar" -> TandaEntity.ALTAR;
			default -> -1;
		};
	}

	private static List<Entity> made(DuniaAnchorEntity anchor) {
		List<Entity> list = MADE.computeIfAbsent(anchor.getUUID(), key -> new ArrayList<>());
		list.removeIf(Entity::isRemoved);
		return list;
	}

	private static void ensureMark(ServerLevel level, DuniaAnchorEntity anchor, int kind) {
		List<Entity> made = made(anchor);
		for (Entity entity : made) {
			if (entity instanceof TandaEntity) {
				return;
			}
		}
		TandaEntity mark = Dimensi.mark(level, anchor.position(), kind, anchor.getUUID());
		if (mark != null) {
			made.add(mark);
		}
	}

	private static ArwahEntity ensureOne(ServerLevel level, DuniaAnchorEntity anchor, List<Entity> made, int role, int variant, int realm) {
		for (Entity entity : made) {
			if (entity instanceof ArwahEntity npc && npc.role() == role) {
				return npc;
			}
		}
		ArwahEntity npc = BawahPeople.spawn(level, anchor.position(), anchor.getYRot(), role, variant, realm, anchor.getUUID(), true);
		if (npc != null) {
			made.add(npc);
		}
		return npc;
	}

	private static void wanderers(ServerLevel level, DuniaAnchorEntity anchor, List<Entity> made, int realm) {
		long souls = made.stream().filter(e -> e instanceof ArwahEntity npc && npc.role() == ArwahEntity.ARWAH).count();
		if (souls >= 2 || level.getRandom().nextInt(3) != 0) {
			return;
		}
		Vec3 at = anchor.position().add((level.getRandom().nextDouble() - 0.5D) * 6.0D, 0.0D, (level.getRandom().nextDouble() - 0.5D) * 20.0D);
		ArwahEntity soul = BawahPeople.spawn(level, at, level.getRandom().nextFloat() * 360.0F, ArwahEntity.ARWAH, 0, realm, anchor.getUUID(), true);
		if (soul != null) {
			made.add(soul);
		}
	}

	private static void hungryGhosts(ServerLevel level, DuniaAnchorEntity anchor, List<Entity> made, List<ServerPlayer> within, int realm) {
		int cap = DimensiConfig.get().ghostsPerPlayer;
		if (cap <= 0 || within.isEmpty()) {
			return;
		}
		ServerPlayer nearest = within.get(0);
		int near = level.getEntitiesOfClass(ArwahEntity.class, new AABB(nearest.blockPosition()).inflate(32.0D),
				npc -> npc.isAlive() && npc.role() == ArwahEntity.HANTU_LAPAR).size();
		if (near >= cap || made.size() >= 3 || level.getRandom().nextInt(3) != 0) {
			return;
		}
		Vec3 at = anchor.position().add((level.getRandom().nextDouble() - 0.5D) * 10.0D, 0.0D, (level.getRandom().nextDouble() - 0.5D) * 10.0D);
		ArwahEntity ghost = BawahPeople.spawn(level, at, level.getRandom().nextFloat() * 360.0F, ArwahEntity.HANTU_LAPAR, 0, realm,
				anchor.getUUID(), true);
		if (ghost != null) {
			made.add(ghost);
		}
	}

	private static void patrol(ServerLevel level, DuniaAnchorEntity anchor, List<Entity> made, int realm) {
		if (made.size() >= 2 || !DuniaBudget.mayAdd(level, DuniaBudget.NPC, anchor.position())) {
			return;
		}
		YakshaEntity yaksha = dev.kultivator.dunia.dimensi.DimensiEntities.YAKSHA.create(level, EntitySpawnReason.EVENT);
		if (yaksha == null) {
			return;
		}
		yaksha.setup(YakshaEntity.BAWAH, Math.max(0, realm - 4), false);
		Vec3 at = anchor.position();
		yaksha.snapTo(at.x + made.size() * 1.5D, at.y, at.z, anchor.getYRot(), 0.0F);
		if (level.addFreshEntity(yaksha)) {
			made.add(yaksha);
			DuniaBudget.track(DuniaBudget.NPC, yaksha);
		}
	}
}
