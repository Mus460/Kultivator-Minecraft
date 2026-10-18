package dev.kultivator.client.goals;

import dev.kultivator.cultivation.QiFormations;
import dev.kultivator.formation.FormationPattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Cetak Biru Formasi / formation blueprints (DESIGN 19.3.15; block reads only). Looking within 8 blocks at a Gold Block
 * (a qi seat) or a Lodestone (a protective formation) while holding an Amethyst Block, End Rod, Gold Block, Beacon,
 * Lodestone or Crying Obsidian, or while sneaking, shows every slot of the formation for 30 s: a green outline on a
 * correct block, a pulsing gold cube on an empty slot, a red cross on a wrong block, and a label above the anchor with
 * the progress ("Formasi Pengumpul Qi Kecil 3/4 · ×1.25 → Agung 5/9"). Drawn with vanilla {@code Gizmos} from the client
 * tick like {@code SiteHologram}; at most 14 {@code getBlockState} reads every 10 ticks while triggered. Nothing is
 * placed or changed.
 */
public final class FormationBlueprint {

	public static final double REACH = 8.0D;
	public static final long STAY_MILLIS = 30000L;
	private static final double DRAW_RANGE_SQ = 32.0D * 32.0D;
	private static final int GREEN = 0x5CE07A;
	private static final int GOLD = 0xFFD54A;
	private static final int RED = 0xFF4A3A;

	private enum Kind { QI, PROTECTIVE }

	private enum State { CORRECT, EMPTY, WRONG }

	/** One slot: where, which tier needs it (1-3, protective 1), and what stands there now. */
	private record Slot(BlockPos pos, int tier, State state) {
	}

	private static @Nullable BlockPos anchor;
	private static Kind kind = Kind.QI;
	private static long lastTrigger;
	private static int ticks;
	private static final List<Slot> SLOTS = new ArrayList<>();
	private static final int[] COUNTS = new int[4];
	private static int tier;

	static void tick(Minecraft minecraft) {
		if (dev.kultivator.client.formasi.PatternHologram.active()) return;
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null) {
			anchor = null;
			return;
		}
		long now = Util.getMillis();
		if (triggering(player)) {
			HitResult hit = player.pick(REACH, 1.0F, false);
			if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
				BlockPos pos = block.getBlockPos();
				BlockState state = level.getBlockState(pos);
				BlockPos found = null;
				Kind foundKind = Kind.QI;
				if (state.is(Blocks.GOLD_BLOCK)) {
					found = pos;
				} else if (state.is(Blocks.LODESTONE)) {
					if (level.getBlockState(pos.above()).is(Blocks.GOLD_BLOCK)) {
						found = pos.above();
					} else {
						found = pos;
						foundKind = Kind.PROTECTIVE;
					}
				}
				if (found != null) {
					if (!found.equals(anchor) || foundKind != kind) {
						anchor = found;
						kind = foundKind;
						ticks = 0;
					}
					lastTrigger = now;
				}
			}
		}
		if (anchor == null) {
			return;
		}
		if (now - lastTrigger > STAY_MILLIS || player.distanceToSqr(Vec3.atCenterOf(anchor)) > DRAW_RANGE_SQ) {
			anchor = null;
			SLOTS.clear();
			return;
		}
		if (ticks++ % 10 == 0) {
			read(level, anchor);
		}
		try {
			draw(now);
		} catch (IllegalStateException exception) {
			// No gizmo collector in this phase of the tick: nothing to draw this time.
		}
	}

	private static boolean triggering(LocalPlayer player) {
		return player.isShiftKeyDown() || holding(player.getMainHandItem()) || holding(player.getOffhandItem());
	}

	private static boolean holding(ItemStack stack) {
		return stack.is(Items.AMETHYST_BLOCK) || stack.is(Items.END_ROD) || stack.is(Items.GOLD_BLOCK) || stack.is(Items.BEACON)
				|| stack.is(Items.LODESTONE) || stack.is(Items.CRYING_OBSIDIAN);
	}

	static void reset() {
		anchor = null;
		SLOTS.clear();
	}

	// ================================================================== reading (at most 14 block reads)

	private static void read(ClientLevel level, BlockPos at) {
		SLOTS.clear();
		java.util.Arrays.fill(COUNTS, 0);
		if (kind == Kind.PROTECTIVE) {
			for (BlockPos pillar : FormationPattern.pillars(at)) {
				add(level, pillar, 1, Blocks.CRYING_OBSIDIAN, null);
			}
			tier = COUNTS[1] == 4 ? 1 : 0;
			return;
		}
		boolean gold = level.getBlockState(at).is(Blocks.GOLD_BLOCK);
		for (int[] offset : QiFormations.cardinalOffsets()) {
			add(level, at.offset(offset[0], 0, offset[1]), 1, Blocks.AMETHYST_BLOCK, null);
		}
		for (int[] offset : QiFormations.cornerOffsets()) {
			add(level, at.offset(offset[0], 0, offset[1]), 2, Blocks.AMETHYST_BLOCK, null);
		}
		for (int[] offset : QiFormations.cardinalOffsets()) {
			add(level, at.offset(offset[0], 1, offset[1]), 3, Blocks.END_ROD, null);
		}
		add(level, at.below(), 3, Blocks.BEACON, Blocks.LODESTONE);
		int small = COUNTS[1];
		int great = small + (gold ? 1 : 0) + COUNTS[2];
		int heavenly = great + COUNTS[3];
		tier = small < 4 ? 0 : great < 9 ? 1 : heavenly < 14 ? 2 : 3;
		COUNTS[0] = great;
		COUNTS[2] = great;
		COUNTS[3] = heavenly;
	}

	private static void add(ClientLevel level, BlockPos pos, int slotTier, Block wanted, @Nullable Block alternative) {
		BlockState state = level.getBlockState(pos);
		State result;
		if (state.is(wanted) || alternative != null && state.is(alternative)) {
			result = State.CORRECT;
			COUNTS[slotTier]++;
		} else if (state.isAir() || state.canBeReplaced()) {
			result = State.EMPTY;
		} else {
			result = State.WRONG;
		}
		SLOTS.add(new Slot(pos, slotTier, result));
	}

	// ================================================================== drawing

	private static void draw(long now) {
		BlockPos at = anchor;
		if (at == null || SLOTS.isEmpty()) {
			return;
		}
		float pulse = 0.55F + 0.45F * (float) Math.sin(now / 260.0D);
		int shownTier = kind == Kind.PROTECTIVE ? 1 : Math.min(3, tier + 1);
		for (Slot slot : SLOTS) {
			if (slot.tier() > shownTier) {
				continue;
			}
			AABB box = new AABB(slot.pos());
			switch (slot.state()) {
				case CORRECT -> Gizmos.cuboid(box.inflate(0.01D), GizmoStyle.stroke(argb(220, GREEN), 2.0F));
				case EMPTY -> Gizmos.cuboid(box.deflate(0.22D), GizmoStyle.strokeAndFill(argb(Math.round(230 * pulse), GOLD), 1.5F,
						argb(Math.round(90 * pulse), GOLD)));
				case WRONG -> {
					double y = slot.pos().getY() + 1.02D;
					double x0 = slot.pos().getX();
					double z0 = slot.pos().getZ();
					Gizmos.line(new Vec3(x0, y, z0), new Vec3(x0 + 1.0D, y, z0 + 1.0D), argb(240, RED), 3.0F);
					Gizmos.line(new Vec3(x0 + 1.0D, y, z0), new Vec3(x0, y, z0 + 1.0D), argb(240, RED), 3.0F);
					Gizmos.cuboid(box.inflate(0.01D), GizmoStyle.stroke(argb(140, RED), 1.2F));
				}
			}
		}
		Vec3 top = new Vec3(at.getX() + 0.5D, at.getY() + 2.3D, at.getZ() + 0.5D);
		for (String line : label()) {
			Gizmos.billboardText(line, top, TextGizmo.Style.forColorAndCentered(argb(255, tier >= (kind == Kind.PROTECTIVE ? 1 : 3) ? GREEN : GOLD))
					.withScale(0.45F)).setAlwaysOnTop();
			top = top.add(0.0D, -0.35D, 0.0D);
		}
	}

	private static List<String> label() {
		List<String> lines = new ArrayList<>();
		if (kind == Kind.PROTECTIVE) {
			lines.add(Component.translatable("kultivator.goals.blueprint.protective", COUNTS[1]).getString());
			if (COUNTS[1] >= 4) {
				lines.add(Component.translatable("kultivator.goals.blueprint.wake").getString());
			}
			return lines;
		}
		Component name1 = QiFormations.name(1);
		Component name2 = QiFormations.name(2);
		Component name3 = QiFormations.name(3);
		String text = switch (tier) {
			case 0 -> Component.translatable("kultivator.goals.blueprint.qi0", name1, COUNTS[1], mult(1), name2, COUNTS[2]).getString();
			case 1 -> Component.translatable("kultivator.goals.blueprint.qi1", name1, mult(1), name2, COUNTS[2]).getString();
			case 2 -> Component.translatable("kultivator.goals.blueprint.qi2", name2, mult(2), name3, COUNTS[3]).getString();
			default -> Component.translatable("kultivator.goals.blueprint.qi3", name3, mult(3)).getString();
		};
		lines.add(text);
		return lines;
	}

	private static String mult(int formationTier) {
		String text = String.format(java.util.Locale.ROOT, "%.2f", QiFormations.multiplier(formationTier));
		while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
			text = text.substring(0, text.length() - 1);
		}
		return "×" + text;
	}

	private static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	private FormationBlueprint() {
	}
}
