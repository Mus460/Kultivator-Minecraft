package dev.kultivator.dunia.dimensi;

import dev.kultivator.dunia.dimensi.bawah.ArwahEntity;
import dev.kultivator.dunia.dimensi.bawah.BawahPeople;
import dev.kultivator.dunia.dimensi.bawah.Courts;
import dev.kultivator.dunia.dimensi.bawah.GhostQuests;
import dev.kultivator.dunia.dimensi.laut.LautNpcEntity;
import dev.kultivator.dunia.dimensi.laut.SeaPeople;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The buttons of module D's scrolls ({@link DimensiNet.Action}, C2S). Every action names the NPC or mark it was opened
 * from; the server checks that it exists, has the right role and stands within 8 b (10 b for the Book), so a client can
 * never act from afar or on another NPC. Unknown actions are ignored.
 */
public final class DimensiActions {

	public static final double RANGE = 8.0D;

	static void handle(ServerPlayer player, DimensiNet.Action action) {
		if (player == null || action == null || player.isSpectator()) {
			return;
		}
		String text = action.action();
		int colon = text.indexOf(':');
		String group = colon < 0 ? text : text.substring(0, colon);
		String what = colon < 0 ? "" : text.substring(colon + 1);
		Entity target = player.level().getEntity(action.entity());
		double range = "book".equals(group) ? 10.0D : RANGE;
		if (target == null || target.isRemoved() || player.distanceToSqr(target) > range * range) {
			return;
		}
		switch (group) {
			case "soup" -> {
				if (target instanceof ArwahEntity npc && npc.role() == ArwahEntity.MENG_PO) {
					BawahPeople.soup(player, npc, what, false);
				}
			}
			case "court" -> {
				if (target instanceof ArwahEntity npc && npc.role() == ArwahEntity.HAKIM) {
					Courts.action(player, npc, what);
				}
			}
			case "book" -> {
				if (target instanceof ArwahEntity npc && npc.role() == ArwahEntity.RAJA_YAMA
						|| target instanceof TandaEntity mark && mark.kind() == TandaEntity.BUKU) {
					BawahPeople.book(player, what, false);
				}
			}
			case "ghost" -> {
				if (target instanceof ArwahEntity npc && npc.role() == ArwahEntity.ARWAH) {
					GhostQuests.action(player, npc, what);
				}
			}
			case "raja" -> {
				if (target instanceof LautNpcEntity npc && npc.role() == LautNpcEntity.RAJA_NAGA) {
					SeaPeople.kingAction(player, npc, what);
				}
			}
			case "beli" -> {
				if (target instanceof LautNpcEntity npc && npc.role() == LautNpcEntity.PEDAGANG) {
					try {
						SeaPeople.buy(player, Integer.parseInt(what));
					} catch (NumberFormatException ignored) {
						// a malformed index buys nothing
					}
				}
			}
			case "penglai" -> {
				if (target instanceof LautNpcEntity npc && npc.role() == LautNpcEntity.DEWA_PENGLAI && "uji".equals(what)) {
					SeaPeople.penglaiTrial(player, npc);
				}
			}
			default -> {
			}
		}
	}

	private DimensiActions() {
	}
}
