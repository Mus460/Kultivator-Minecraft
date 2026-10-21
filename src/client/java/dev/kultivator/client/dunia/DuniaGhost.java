package dev.kultivator.client.dunia;

import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.fx.spec.DisplayKey;
import dev.kultivator.client.fx.spec.PhantomDisplays;
import dev.kultivator.dunia.DuniaGhostPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The ghost preview of {@code uji struktur lihat} (DESIGN 34.4.6): the template's merged boxes as client-only block
 * displays (through {@link PhantomDisplays}, so the display budget, the per-tick creation limit and the "phantoms"
 * setting apply), at most {@code EpicBudget.displayCap() × 8} boxes, the largest first, for the payload's ticks. Never a
 * block, never a server entity.
 */
final class DuniaGhost {

	private static final Deque<int[]> PENDING = new ArrayDeque<>();
	private static final List<Display> SHOWN = new ArrayList<>();
	private static Vec3 base = Vec3.ZERO;
	private static int left;

	static void onPayload(DuniaGhostPayload payload) {
		clear();
		if (payload.count() == 0 || payload.ticks() <= 0) {
			return;
		}
		base = new Vec3(payload.x(), payload.y(), payload.z());
		left = payload.ticks();
		int cap = Math.max(8, EpicBudget.displayCap() * 8);
		int[] boxes = payload.boxes();
		for (int i = 0; i < payload.count() && i < cap; i++) {
			int[] box = new int[DuniaGhostPayload.INTS_PER_BOX];
			System.arraycopy(boxes, i * DuniaGhostPayload.INTS_PER_BOX, box, 0, DuniaGhostPayload.INTS_PER_BOX);
			PENDING.add(box);
		}
		if (!PhantomDisplays.enabled()) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player != null) {
				minecraft.player.sendOverlayMessage(Component.translatable("kultivator.dunia.ghost.no_phantoms"));
			}
		}
	}

	static void tick() {
		if (left <= 0) {
			if (!SHOWN.isEmpty() || !PENDING.isEmpty()) {
				clear();
			}
			return;
		}
		left--;
		int made = 0;
		while (!PENDING.isEmpty() && made < PhantomDisplays.createdPerTick() && PhantomDisplays.room() > 0) {
			int[] box = PENDING.peekFirst();
			Vec3 at = base.add(box[0], box[1], box[2]);
			if (!PhantomDisplays.canCreate(at)) {
				break;
			}
			PENDING.pollFirst();
			Display.BlockDisplay display = PhantomDisplays.create(EntityTypes.BLOCK_DISPLAY, at, 2.0F);
			if (display == null) {
				break;
			}
			BlockState state = Block.stateById(box[6]);
			display.setBlockState(state);
			DisplayKey.place(display, new Vector3f(), new Quaternionf(), new Vector3f(box[3], box[4], box[5]));
			SHOWN.add(display);
			made++;
		}
	}

	static void clear() {
		for (Display display : SHOWN) {
			PhantomDisplays.remove(display);
		}
		SHOWN.clear();
		PENDING.clear();
		left = 0;
	}

	private DuniaGhost() {
	}
}
