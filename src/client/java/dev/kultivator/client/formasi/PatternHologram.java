package dev.kultivator.client.formasi;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.formasi.FormasiItems;
import dev.kultivator.formasi.spec.Flag;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Rule;
import dev.kultivator.formasi.spec.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The read-only hologram blueprint viewer (DESIGN 24.7.3, owned by P7c-katalog-ui). It follows the build layer by layer:
 * the current layer (the lowest with missing slots) is drawn strong, upper layers as faint ghosts, finished layers as
 * green outlines; a slot is a thin green outline when correct, a pulsing gold ghost cube tinted with the block's map
 * colour when empty, a red cross when wrong and grey when its chunk is not loaded. The six missing slots nearest the
 * player (within 6 blocks) show their rule label, and a two-line label floats over the anchor. Drawn with vanilla
 * {@code Gizmos} from the client tick like {@code FormationBlueprint}; client block reads only (≤ 32 per tick, round
 * robin), ≤ 400 gizmos per tick within 48 blocks. Ends after its minutes, beyond 64 blocks, on a dimension change or when
 * hidden. While {@link #active()} the phase-7 {@code FormationBlueprint} returns early.
 */
public final class PatternHologram {

	public static final int READS_PER_TICK = 32;
	public static final int MAX_GIZMOS = 400;
	public static final double DRAW_RANGE = 48.0D;
	public static final double END_RANGE = 64.0D;
	public static final double LABEL_RANGE = 6.0D;
	public static final int MAX_LABELS = 6;
	public static final int MIN_LABELS = 3;
	public static final int DEFAULT_MINUTES = 10;
	public static final long AUTO_MILLIS = 3000L;
	public static final double LOOK_RANGE = 8.0D;
	private static final int GREEN = 0x5CE07A;
	private static final int GOLD = 0xFFD54A;
	private static final int RED = 0xFF4A3A;
	private static final int GREY = 0x9AA4A0;

	private static final byte UNKNOWN = 0;
	private static final byte OK = 1;
	private static final byte MISSING = 2;
	private static final byte WRONG = 3;

	private static @Nullable FormationSpec spec;
	private static @Nullable BlockPos anchor;
	private static int rot;
	private static long until;
	private static boolean active;
	private static boolean auto;
	private static @Nullable ClientLevel level;
	private static byte[] states = new byte[0];
	private static int cursor;
	private static List<Slot> slots = List.of();
	private static int[] layers = new int[0];
	// Rotation choice for an existing eye: one pass over every candidate rotation counting OK slots.
	private static boolean choosing;
	private static int chooseIndex;
	private static final int[] ROT_OK = new int[4];
	private static int candidates;

	/** Shows {@code spec} at {@code anchor} in rotation {@code rot} (−1: pick the rotation with the most correct slots) for {@code minutes}. */
	public static void show(FormationSpec spec, BlockPos anchor, int rot, int minutes) {
		start(spec, anchor, rot, Util.getMillis() + (minutes <= 0 ? DEFAULT_MINUTES : minutes) * 60_000L, false);
	}

	private static void start(FormationSpec newSpec, BlockPos newAnchor, int newRot, long end, boolean automatic) {
		Minecraft minecraft = Minecraft.getInstance();
		if (newSpec == null || newAnchor == null || minecraft.level == null) {
			return;
		}
		spec = newSpec;
		anchor = newAnchor.immutable();
		level = minecraft.level;
		until = end;
		auto = automatic;
		active = true;
		boolean fixed = newSpec.has(Flag.FIXED_ORIENTATION);
		choosing = !fixed && newRot < 0;
		rot = fixed ? 0 : Math.max(0, newRot) & 3;
		candidates = fixed ? 1 : Math.max(1, newSpec.pattern().distinctRotations());
		chooseIndex = 0;
		java.util.Arrays.fill(ROT_OK, 0);
		setRotation(rot);
	}

	private static void setRotation(int newRot) {
		rot = newRot & 3;
		slots = spec == null ? List.of() : spec.pattern().slots(rot);
		states = new byte[slots.size()];
		cursor = 0;
		TreeSet<Integer> dys = new TreeSet<>();
		for (Slot slot : slots) {
			dys.add(slot.dy());
		}
		layers = dys.stream().mapToInt(Integer::intValue).toArray();
	}

	public static void hide() {
		active = false;
		spec = null;
		anchor = null;
		slots = List.of();
		states = new byte[0];
	}

	public static boolean active() {
		return active;
	}

	/** The spec shown now, or null. */
	public static @Nullable FormationSpec shown() {
		return active ? spec : null;
	}

	/** A sensible anchor "in front of me": the top of the looked-at block within 8 blocks, else 4 blocks ahead on the ground. */
	public static BlockPos inFront(LocalPlayer player) {
		HitResult hit = player.pick(LOOK_RANGE, 1.0F, false);
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			return block.getBlockPos().above();
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		return BlockPos.containing(player.position().add(flat.scale(4.0D)));
	}

	/** The rotation that turns a pattern's north away from the player: north 0, east 1, south 2, west 3. */
	public static int facingRotation(LocalPlayer player) {
		return switch (player.getDirection()) {
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
			default -> 0;
		};
	}

	static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel current = minecraft.level;
		if (player == null || current == null) {
			if (active) {
				hide();
			}
			return;
		}
		if (!active || auto) {
			autoTrigger(player, current);
		}
		if (!active || spec == null || anchor == null) {
			return;
		}
		long now = Util.getMillis();
		if (current != level || now > until || player.distanceToSqr(Vec3.atCenterOf(anchor)) > END_RANGE * END_RANGE) {
			hide();
			return;
		}
		read(current);
		try {
			draw(player, current, now);
		} catch (IllegalStateException exception) {
			// No gizmo collector in this phase of the tick: nothing to draw this time.
		}
	}

	/** Sneaking, or holding a block a pattern uses, while looking at a registered eye within 8 blocks: shows its pattern. */
	private static void autoTrigger(LocalPlayer player, ClientLevel current) {
		boolean trigger = player.isShiftKeyDown() || holdsPatternBlock(player.getMainHandItem()) || holdsPatternBlock(player.getOffhandItem());
		if (!trigger) {
			return;
		}
		HitResult hit = player.pick(LOOK_RANGE, 1.0F, false);
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos pos = block.getBlockPos();
		if (!current.getBlockState(pos).is(FormasiItems.FORMATION_EYE)) {
			return;
		}
		FormasiClient.SyncCore core = FormasiClient.coreAt(pos);
		FormationSpec target = core == null ? null : core.spec();
		if (target == null) {
			return;
		}
		long end = Util.getMillis() + AUTO_MILLIS;
		if (active && auto && pos.equals(anchor) && target == spec) {
			until = end;
			return;
		}
		start(target, pos, core.rot(), end, true);
	}

	private static boolean holdsPatternBlock(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) {
			return false;
		}
		if (item.getBlock() == FormasiItems.FORMATION_EYE) {
			return true;
		}
		for (FormasiClient.SyncCore core : FormasiClient.cores()) {
			FormationSpec each = core.spec();
			if (each == null) {
				continue;
			}
			for (Rule rule : each.pattern().legend().values()) {
				if (rule.canonical(0).getBlock() == item.getBlock()) {
					return true;
				}
			}
		}
		return false;
	}

	// ================================================================== reading (≤ 32 client block reads per tick)

	private static byte readSlot(ClientLevel current, BlockPos at, Slot slot, int rotation) {
		if (!current.isLoaded(at)) {
			return UNKNOWN;
		}
		BlockState state = current.getBlockState(at);
		FluidState fluid = current.getFluidState(at);
		if (slot.rule().test(state, fluid, rotation)) {
			return OK;
		}
		return state.isAir() || state.canBeReplaced() ? MISSING : WRONG;
	}

	private static void read(ClientLevel current) {
		if (spec == null || anchor == null) {
			return;
		}
		if (choosing) {
			int size = spec.pattern().size();
			int total = size * candidates;
			for (int i = 0; i < READS_PER_TICK && chooseIndex < total; i++, chooseIndex++) {
				int r = chooseIndex / Math.max(1, size);
				Slot slot = spec.pattern().slots(r).get(chooseIndex % size);
				if (readSlot(current, anchor.offset(slot.dx(), slot.dy(), slot.dz()), slot, r) == OK) {
					ROT_OK[r]++;
				}
			}
			if (chooseIndex >= total) {
				choosing = false;
				int best = 0;
				for (int r = 1; r < candidates; r++) {
					if (ROT_OK[r] > ROT_OK[best]) {
						best = r;
					}
				}
				setRotation(best);
			}
			return;
		}
		if (slots.isEmpty()) {
			return;
		}
		int reads = Math.min(READS_PER_TICK, slots.size());
		for (int i = 0; i < reads; i++) {
			int index = cursor;
			cursor = (cursor + 1) % slots.size();
			Slot slot = slots.get(index);
			states[index] = readSlot(current, anchor.offset(slot.dx(), slot.dy(), slot.dz()), slot, rot);
		}
	}

	// ================================================================== drawing

	private static void draw(LocalPlayer player, ClientLevel current, long now) {
		if (spec == null || anchor == null || slots.isEmpty()) {
			return;
		}
		boolean minimal = EffectQuality.minimal();
		float pulse = EffectQuality.reduceMotion() ? 0.8F : 0.55F + 0.45F * (float) Math.sin(now / 260.0D);
		int currentLayer = Integer.MAX_VALUE;
		int ok = 0;
		Map<String, Integer> missing = new LinkedHashMap<>();
		Map<String, Component> labels = new LinkedHashMap<>();
		for (int i = 0; i < slots.size(); i++) {
			byte state = states[i];
			if (state == OK) {
				ok++;
				continue;
			}
			currentLayer = Math.min(currentLayer, slots.get(i).dy());
			if (state == MISSING || state == WRONG) {
				Component label = slots.get(i).rule().label();
				String key = label.getString();
				missing.merge(key, 1, Integer::sum);
				labels.putIfAbsent(key, label);
			}
		}
		Vec3 camera = player.position();
		int gizmos = 0;
		List<Integer> near = new ArrayList<>();
		for (int i = 0; i < slots.size() && gizmos < MAX_GIZMOS; i++) {
			Slot slot = slots.get(i);
			BlockPos pos = anchor.offset(slot.dx(), slot.dy(), slot.dz());
			Vec3 centre = Vec3.atCenterOf(pos);
			if (centre.distanceToSqr(camera) > DRAW_RANGE * DRAW_RANGE) {
				continue;
			}
			AABB box = new AABB(pos);
			byte state = states[i];
			boolean finished = slot.dy() < currentLayer;
			boolean upper = slot.dy() > currentLayer;
			if (state == OK) {
				Gizmos.cuboid(box.inflate(0.01D), GizmoStyle.stroke(argb(finished ? 150 : 220, GREEN), finished ? 1.0F : 1.6F));
				gizmos++;
				continue;
			}
			if (upper) {
				int tint = tint(slot, current, pos);
				Gizmos.cuboid(box.deflate(0.3D), GizmoStyle.stroke(argb(70, state == WRONG ? RED : tint), 1.0F));
				gizmos++;
				continue;
			}
			switch (state) {
				case MISSING -> {
					int tint = mixRgb(tint(slot, current, pos), GOLD, 0.45F);
					GizmoStyle style = minimal ? GizmoStyle.stroke(argb(Math.round(230 * pulse), tint), 1.5F)
							: GizmoStyle.strokeAndFill(argb(Math.round(230 * pulse), tint), 1.5F, argb(Math.round(80 * pulse), tint));
					Gizmos.cuboid(box.deflate(0.22D), style);
					gizmos++;
					if (centre.distanceToSqr(camera) <= LABEL_RANGE * LABEL_RANGE) {
						near.add(i);
					}
				}
				case WRONG -> {
					double y = pos.getY() + 1.02D;
					double x0 = pos.getX();
					double z0 = pos.getZ();
					Gizmos.line(new Vec3(x0, y, z0), new Vec3(x0 + 1.0D, y, z0 + 1.0D), argb(240, RED), 3.0F);
					Gizmos.line(new Vec3(x0 + 1.0D, y, z0), new Vec3(x0, y, z0 + 1.0D), argb(240, RED), 3.0F);
					Gizmos.cuboid(box.inflate(0.01D), GizmoStyle.stroke(argb(140, RED), 1.2F));
					gizmos += 3;
					if (centre.distanceToSqr(camera) <= LABEL_RANGE * LABEL_RANGE) {
						near.add(i);
					}
				}
				default -> {
					Gizmos.cuboid(box.deflate(0.3D), GizmoStyle.stroke(argb(120, GREY), 1.0F));
					gizmos++;
				}
			}
		}
		// Rule labels of the nearest missing slots.
		near.sort(Comparator.comparingDouble(index -> Vec3.atCenterOf(anchor.offset(slots.get(index).dx(), slots.get(index).dy(),
				slots.get(index).dz())).distanceToSqr(camera)));
		int maxLabels = minimal ? MIN_LABELS : MAX_LABELS;
		for (int i = 0; i < near.size() && i < maxLabels; i++) {
			Slot slot = slots.get(near.get(i));
			Vec3 at = Vec3.atCenterOf(anchor.offset(slot.dx(), slot.dy(), slot.dz())).add(0.0D, 0.65D, 0.0D);
			Gizmos.billboardText(slot.rule().label().getString(), at, TextGizmo.Style.forColorAndCentered(argb(255, GOLD)).withScale(0.32F));
		}
		// The two-line label over the anchor.
		int top = layers.length == 0 ? 1 : layers[layers.length - 1] + 1;
		Vec3 head = new Vec3(anchor.getX() + 0.5D, anchor.getY() + Math.max(2, top) + 1.3D, anchor.getZ() + 0.5D);
		int layerIndex = 1;
		for (int i = 0; i < layers.length; i++) {
			if (layers[i] <= currentLayer) {
				layerIndex = i + 1;
			}
		}
		if (currentLayer == Integer.MAX_VALUE) {
			layerIndex = layers.length;
		}
		Component line1 = Component.translatable("kultivator.formasiui.holo.title", spec.name(), spec.grade().label(), spec.minHallLevel());
		MutableComponent line2 = Component.translatable("kultivator.formasiui.holo.progress", layerIndex, layers.length, ok, slots.size());
		if (!missing.isEmpty()) {
			MutableComponent list = Component.empty();
			int shown = 0;
			for (Map.Entry<String, Integer> entry : missing.entrySet()) {
				if (shown == 3) {
					list.append(Component.translatable("kultivator.formasiui.holo.more", missing.size() - 3));
					break;
				}
				if (shown > 0) {
					list.append(", ");
				}
				list.append(labels.get(entry.getKey())).append(" ×" + entry.getValue());
				shown++;
			}
			line2.append(Component.translatable("kultivator.formasiui.holo.missing", list));
		} else if (ok == slots.size()) {
			line2.append(Component.translatable("kultivator.formasiui.holo.complete"));
		}
		if (choosing) {
			line2 = Component.translatable("kultivator.formasiui.holo.reading");
		}
		int color = ok == slots.size() ? GREEN : GOLD;
		Gizmos.billboardText(line1.getString(), head, TextGizmo.Style.forColorAndCentered(argb(255, color)).withScale(0.45F)).setAlwaysOnTop();
		Gizmos.billboardText(line2.getString(), head.add(0.0D, -0.38D, 0.0D), TextGizmo.Style.forColorAndCentered(argb(235, 0xF4EBD0)).withScale(0.36F))
				.setAlwaysOnTop();
	}

	/** The canonical block's map colour (gold when it has none). */
	private static int tint(Slot slot, ClientLevel current, BlockPos pos) {
		BlockState canonical = slot.rule().canonical(rot);
		int col = canonical.getBlock().defaultMapColor().col;
		return col == 0 ? GOLD : col;
	}

	private static int mixRgb(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return r << 16 | g << 8 | bl;
	}

	private static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	private PatternHologram() {
	}
}
