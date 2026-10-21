package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.dunia.reruntuhan.Glyphs;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * P2 Lima Unsur (DESIGN 34.5.5): five lamps on soul-lantern pedestals, each of one phase (placed by the room's seed).
 * They are lit with qi (5 % of the player's max Qi each) in the generating cycle Wood, Fire, Earth, Metal, Water
 * ({@code elite/ElementRelations.generates}), starting from the phase carved on the door. A wrong lamp puts them all out
 * with a backlash.
 */
final class UnsurPuzzle extends Puzzle {

	public static final double QI_COST = 0.05D;
	private static final int[][] LAMPS = {{0, 1, -5}, {5, 1, -2}, {3, 1, 4}, {-3, 1, 4}, {-5, 1, -2}};
	private final TekaPartEntity[] lamps = new TekaPartEntity[5];
	/** Phase (index in Glyphs.CYCLE) of lamp i. */
	private final int[] phase = new int[5];
	private final int start;
	private int lit;

	UnsurPuzzle(PuzzleHost host) {
		super(host);
		for (int i = 0; i < 5; i++) {
			phase[i] = i;
		}
		for (int i = 4; i > 0; i--) {
			int k = random.nextInt(i + 1);
			int t = phase[i];
			phase[i] = phase[k];
			phase[k] = t;
		}
		this.start = random.nextInt(5);
	}

	@Override
	public Kind kind() {
		return Kind.UNSUR;
	}

	@Override
	public void build() {
		Frame frame = host.frame();
		int n = Frame.triples(host.layout(), "lamps");
		for (int i = 0; i < 5; i++) {
			int[] off = i < n ? Frame.triple(host.layout(), "lamps", i) : LAMPS[i];
			// the lamp stands on the pedestal's slab top (offset y = the slab block)
			Vec3 at = frame.bottom(off[0], off[1], off[2]).add(0.0D, 0.5D, 0.0D);
			TekaPartEntity lamp = part(REntities.TEKA_LAMPU, TekaPartEntity.LAMP, at, 0.0F);
			if (lamp != null) {
				lamp.setArg(phase[i]);
				lamp.setColor(Glyphs.CYCLE[phase[i]].color());
				lamps[i] = lamp;
			}
		}
		host.doorGlyph(1 + start);
	}

	/** The phase that must be lit next. */
	private int expected() {
		return (start + lit) % 5;
	}

	@Override
	public void use(ServerPlayer player, TekaPartEntity part, boolean attack) {
		if (done || part.state() == TekaPartEntity.LIT) {
			return;
		}
		int index = -1;
		for (int i = 0; i < 5; i++) {
			if (lamps[i] == part) {
				index = i;
			}
		}
		if (index < 0) {
			return;
		}
		double cost = Qi.max(Cultivators.get(player)) * QI_COST;
		if (!player.isCreative() && !Qi.trySpend(player, cost)) {
			Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.teka.unsur.no_qi").withStyle(ChatFormatting.RED));
			return;
		}
		if (phase[index] == expected()) {
			part.setState(TekaPartEntity.LIT);
			lit++;
			fx(part.position(), RFx.LAMP_LIT, Glyphs.CYCLE[phase[index]].color(), lit, 1.0F);
			host.level().playSound(null, part.getX(), part.getY(), part.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.7F, 0.8F + 0.1F * lit);
			if (lit >= 5) {
				solve();
			}
		} else {
			host.level().playSound(null, part.getX(), part.getY(), part.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 0.7F);
			wrong(player);
		}
	}

	@Override
	public void tick(long now) {
		if (done || !host.hinted()) {
			return;
		}
		int want = expected();
		for (int i = 0; i < 5; i++) {
			TekaPartEntity lamp = lamps[i];
			if (lamp != null && lamp.state() != TekaPartEntity.LIT) {
				lamp.setState(phase[i] == want ? TekaPartEntity.HINT : TekaPartEntity.IDLE);
			}
		}
	}

	@Override
	public void reset() {
		lit = 0;
		for (TekaPartEntity lamp : lamps) {
			if (lamp != null) {
				lamp.setState(TekaPartEntity.IDLE);
			}
		}
	}

	@Override
	public float progress() {
		return lit / 5.0F;
	}

	@Override
	public Component objective() {
		return Component.translatable("kultivator.reruntuhan.teka.unsur.objective", Component.literal(Glyphs.PHASE[start]));
	}
}
