package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * "Hiasan Tingkat n", the ornament component that ends every tier (DESIGN 21.2 rule 7): it adds lanterns, flowers and
 * banners across the existing parts so the whole sect looks upgraded, not just one plot.
 * <ul>
 * <li>It calls {@link Part#decorate} with tier n on every part of every component of tiers 1..n (wind bells at T4, eave
 * lanterns at T9...), and lets the components of tier n itself catch up on the decorations of earlier tiers.</li>
 * <li>Its own spots: {@link #lanterns} (a {@link LanternPost.Style} at each spot), {@link #flowers} (potted bonsai and
 * flowering azalea), {@link #banners} (red banners on poles).</li>
 * </ul>
 * Template-local. Give each template one ornament component per tier, in group ORNAMENT.
 */
public final class Ornament implements Part {

	private final int tier;
	private final Lv level;
	private final List<int[]> lanterns = new ArrayList<>();
	private LanternPost.Style lanternStyle = LanternPost.Style.STONE;
	private final List<int[]> flowers = new ArrayList<>();
	private final List<int[]> banners = new ArrayList<>();

	public Ornament(int tier, Lv level) {
		this.tier = tier;
		this.level = level;
	}

	public Ornament lanterns(LanternPost.Style style, int... xzPairs) {
		this.lanternStyle = style;
		for (int i = 0; i + 1 < xzPairs.length; i += 2) {
			lanterns.add(new int[]{xzPairs[i], xzPairs[i + 1]});
		}
		return this;
	}

	public Ornament flowers(int... xzPairs) {
		for (int i = 0; i + 1 < xzPairs.length; i += 2) {
			flowers.add(new int[]{xzPairs[i], xzPairs[i + 1]});
		}
		return this;
	}

	public Ornament banners(int... xzPairs) {
		for (int i = 0; i + 1 < xzPairs.length; i += 2) {
			banners.add(new int[]{xzPairs[i], xzPairs[i + 1]});
		}
		return this;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		BoundingBox box = new BoundingBox(0, y, 0, 0, y, 0);
		List<int[]> all = new ArrayList<>(lanterns);
		all.addAll(flowers);
		all.addAll(banners);
		for (int[] spot : all) {
			box = BoundingBox.encapsulating(box, new BoundingBox(spot[0], y, spot[1], spot[0], y + 5, spot[1]));
		}
		return box;
	}

	@Override
	public String kind() {
		return "ornament";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		for (Component component : ctx.spec().components()) {
			if (component.tier() > tier || component.group() == Component.Group.ORNAMENT) {
				continue;
			}
			for (Part part : component.parts()) {
				if (component.tier() == tier) {
					for (int t = 1; t <= tier; t++) {
						part.decorate(ctx, sink, t);
					}
				} else {
					part.decorate(ctx, sink, tier);
				}
			}
		}
		int y = ctx.level(level);
		sink.phase(Phase.ORNAMENT);
		for (int[] spot : lanterns) {
			LanternPost.emitOne(sink, spot[0], y, spot[1], lanternStyle, Direction.SOUTH);
		}
		int kind = 0;
		for (int[] spot : flowers) {
			if (kind % 3 == 2) {
				sink.set(spot[0], y, spot[1], Palette.st(Blocks.FLOWERING_AZALEA));
			} else {
				sink.set(spot[0], y, spot[1], Palette.st(Blocks.POLISHED_ANDESITE));
				sink.set(spot[0], y + 1, spot[1], Palette.st(kind % 3 == 0 ? Blocks.POTTED_FLOWERING_AZALEA : Blocks.POTTED_AZALEA));
			}
			kind++;
		}
		for (int[] spot : banners) {
			for (int i = 0; i < 4; i++) {
				sink.set(spot[0], y + i, spot[1], Palette.st(Palette.FENCE));
			}
			sink.set(spot[0], y + 4, spot[1], Palette.banner(DyeColor.RED, Direction.SOUTH));
		}
	}

	public int tier() {
		return tier;
	}
}
