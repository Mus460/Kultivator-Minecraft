package dev.kultivator.dunia.dimensi.laut;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.dimensi.Dimensi;
import dev.kultivator.dunia.dimensi.DimensiEntities;
import dev.kultivator.dunia.dimensi.DimensiLevels;
import dev.kultivator.dunia.dimensi.TandaEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The handler of every {@code laut/*} anchor (DESIGN 34.4.1): the overworld sea gate ({@code pusaran}: the whirl mark
 * on the water and the crossing), the return gates ({@code gerbang_pulang}), herb beds ({@code herba}), the people
 * ({@code raja_naga}, {@code penjaga}, {@code pertapa}, {@code pedagang}, {@code nelayan}, {@code penglai} with its spring
 * mark), the island sites ({@code situs} with data {@code jenis}: the kinds a player has stood on) and the
 * reef monsters ({@code karang}, data {@code monster}: at most two per anchor). Pulau Pasar's {@code kota/*} anchors are
 * handled by module K. Nothing is saved; everything leaves with the chunk.
 */
public final class LautAnchors implements AnchorHandler {

	private static final Map<UUID, List<Entity>> MADE = new HashMap<>();

	static void clear() {
		MADE.clear();
	}

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		// never add an entity from inside the chunk's entity-load callback: one tick later, if still loaded
		Dimensi.schedule(1, () -> {
			if (!anchor.isRemoved() && anchor.level() == level) {
				mark(level, anchor);
			}
		});
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		List<Entity> made = made(anchor);
		mark(level, anchor);
		int realm = realmNear(within);
		switch (anchor.what()) {
			case "pusaran" -> SeaGate.whirlPass(level, anchor, within);
			case "raja_naga" -> person(level, anchor, made, LautNpcEntity.RAJA_NAGA, realm);
			case "penjaga" -> person(level, anchor, made, LautNpcEntity.PENJAGA, realm);
			case "pertapa" -> person(level, anchor, made, LautNpcEntity.PERTAPA, realm);
			case "pedagang" -> person(level, anchor, made, LautNpcEntity.PEDAGANG, realm);
			case "nelayan" -> person(level, anchor, made, LautNpcEntity.NELAYAN, realm);
			case "situs" -> {
				// standing on a spirit island counts its kind (the four kinds: dunia/islands_all)
				String jenis = anchor.data().getStringOr("jenis", "");
				if (!jenis.isEmpty()) {
					for (ServerPlayer player : within) {
						if (player.position().distanceToSqr(anchor.position()) < 40.0D * 40.0D) {
							SeaPeople.islandSeen(player, jenis);
						}
					}
				}
			}
			case "penglai" -> person(level, anchor, made, LautNpcEntity.DEWA_PENGLAI, realm);
			case "karang" -> reef(level, anchor, made, realm);
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
					if (!entity.isRemoved() && !(entity instanceof Mob mob && mob.getTarget() != null)) {
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

	private static List<Entity> made(DuniaAnchorEntity anchor) {
		List<Entity> list = MADE.computeIfAbsent(anchor.getUUID(), key -> new ArrayList<>());
		list.removeIf(Entity::isRemoved);
		return list;
	}

	private static int realmNear(List<ServerPlayer> within) {
		int best = Realm.GOLDEN_CORE.ordinal();
		for (ServerPlayer player : within) {
			best = Math.max(best, Cultivators.get(player).realm().ordinal());
		}
		return best;
	}

	/** The anchor's mark (whirl, gate, herb bed, spring), made once. */
	private static void mark(ServerLevel level, DuniaAnchorEntity anchor) {
		int kind = switch (anchor.what()) {
			case "pusaran" -> TandaEntity.PUSARAN;
			case "gerbang_pulang" -> TandaEntity.GERBANG;
			case "herba" -> TandaEntity.HERBA;
			case "penglai" -> TandaEntity.MATA_AIR;
			default -> -1;
		};
		if (kind < 0) {
			return;
		}
		List<Entity> made = made(anchor);
		for (Entity entity : made) {
			if (entity instanceof TandaEntity) {
				return;
			}
		}
		Vec3 at = anchor.position();
		if (kind == TandaEntity.PUSARAN) {
			at = new Vec3(at.x, SeaGate.surfaceAbove(level, anchor.blockPosition()), at.z);
		}
		TandaEntity mark = Dimensi.mark(level, at, kind, anchor.getUUID());
		if (mark != null) {
			made.add(mark);
		}
	}

	private static void person(ServerLevel level, DuniaAnchorEntity anchor, List<Entity> made, int role, int realm) {
		for (Entity entity : made) {
			if (entity instanceof LautNpcEntity npc && npc.role() == role) {
				return;
			}
		}
		LautNpcEntity npc = SeaPeople.spawn(level, anchor.position(), anchor.getYRot(), role, realm, anchor.getUUID());
		if (npc != null) {
			made.add(npc);
		}
	}

	private static void reef(ServerLevel level, DuniaAnchorEntity anchor, List<Entity> made, int realm) {
		if (made.size() >= 2 || level.getRandom().nextInt(4) != 0 || !DimensiLevels.isLaut(level)
				|| !DuniaBudget.mayAdd(level, SeaMonsters.MONSTER, anchor.position())) {
			return;
		}
		int tier = SeaMonsters.tierAt(anchor.position());
		Vec3 at = anchor.position().add((level.getRandom().nextDouble() - 0.5D) * 4.0D, 0.0D, (level.getRandom().nextDouble() - 0.5D) * 4.0D);
		Mob mob;
		if ("yaksha".equals(anchor.data().getStringOr("monster", "kepiting"))) {
			YakshaEntity yaksha = DimensiEntities.YAKSHA.create(level, EntitySpawnReason.EVENT);
			if (yaksha == null) {
				return;
			}
			yaksha.setup(YakshaEntity.LAUT, tier, false);
			mob = yaksha;
		} else {
			KepitingBatuEntity crab = DimensiEntities.KEPITING_BATU.create(level, EntitySpawnReason.EVENT);
			if (crab == null) {
				return;
			}
			crab.setup(tier);
			mob = crab;
		}
		mob.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		if (level.addFreshEntity(mob)) {
			made.add(mob);
			DuniaBudget.track(SeaMonsters.MONSTER, mob);
		}
	}
}
