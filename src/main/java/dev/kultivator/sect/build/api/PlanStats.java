package dev.kultivator.sect.build.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Arrays;

/**
 * The real numbers of one template from the blueprint dry run (DESIGN 21.11, {@code uji sekte info}): footprint and
 * envelope, then per tier (index = tier - 1, always {@link SectTemplates#TIERS} long) the component count, placed blocks,
 * ops and the bill, and the estimated minutes at the base rate of 21.15. The arrays are copied in and out.
 */
public record PlanStats(int template, int width, int depth, int above, int below, int[] components, long[] blocks, long[] ops, Bill[] bills,
		double minutes) {

	public PlanStats {
		components = components == null ? new int[SectTemplates.TIERS] : Arrays.copyOf(components, SectTemplates.TIERS);
		blocks = blocks == null ? new long[SectTemplates.TIERS] : Arrays.copyOf(blocks, SectTemplates.TIERS);
		ops = ops == null ? new long[SectTemplates.TIERS] : Arrays.copyOf(ops, SectTemplates.TIERS);
		Bill[] copy = new Bill[SectTemplates.TIERS];
		for (int i = 0; i < copy.length; i++) {
			copy[i] = bills != null && i < bills.length && bills[i] != null ? bills[i] : Bill.EMPTY;
		}
		bills = copy;
	}

	/** No numbers yet (the engine is missing or still computing). */
	public static PlanStats empty(int template) {
		return new PlanStats(template, 0, 0, 0, 0, null, null, null, null, 0.0D);
	}

	@Override
	public int[] components() {
		return components.clone();
	}

	@Override
	public long[] blocks() {
		return blocks.clone();
	}

	@Override
	public long[] ops() {
		return ops.clone();
	}

	@Override
	public Bill[] bills() {
		return bills.clone();
	}

	public int components(int tier) {
		return inRange(tier) ? components[tier - 1] : 0;
	}

	public long blocks(int tier) {
		return inRange(tier) ? blocks[tier - 1] : 0L;
	}

	public long ops(int tier) {
		return inRange(tier) ? ops[tier - 1] : 0L;
	}

	public Bill bill(int tier) {
		return inRange(tier) ? bills[tier - 1] : Bill.EMPTY;
	}

	/** The bill of all nine tiers. */
	public Bill total() {
		Bill.Builder sum = Bill.builder();
		for (Bill bill : bills) {
			sum.add(bill);
		}
		return sum.build();
	}

	public int totalComponents() {
		return Arrays.stream(components).sum();
	}

	public long totalOps() {
		return Arrays.stream(ops).sum();
	}

	public boolean isEmpty() {
		return totalOps() == 0L && totalComponents() == 0;
	}

	private static boolean inRange(int tier) {
		return tier >= 1 && tier <= SectTemplates.TIERS;
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("t", template);
		tag.putIntArray("size", new int[]{width, depth, above, below});
		tag.putIntArray("comps", components);
		tag.putLongArray("blocks", blocks);
		tag.putLongArray("ops", ops);
		ListTag list = new ListTag();
		for (Bill bill : bills) {
			list.add(bill.toTag());
		}
		tag.put("bills", list);
		tag.putDouble("min", minutes);
		return tag;
	}

	public static PlanStats fromTag(CompoundTag tag) {
		int[] size = tag.getIntArray("size").orElse(new int[4]);
		if (size.length < 4) {
			size = Arrays.copyOf(size, 4);
		}
		ListTag list = tag.getListOrEmpty("bills");
		Bill[] bills = new Bill[SectTemplates.TIERS];
		for (int i = 0; i < Math.min(list.size(), bills.length); i++) {
			Tag each = list.get(i);
			bills[i] = each instanceof CompoundTag bill ? Bill.readFrom(bill) : Bill.EMPTY;
		}
		return new PlanStats(tag.getIntOr("t", 0), size[0], size[1], size[2], size[3], tag.getIntArray("comps").orElse(null),
				tag.getLongArray("blocks").orElse(null), tag.getLongArray("ops").orElse(null), bills, tag.getDoubleOr("min", 0.0D));
	}
}
