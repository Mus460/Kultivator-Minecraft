package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * P6 Timbangan Qi (DESIGN 34.5.5): two pans either side of a bronze fulcrum; the stele asks for the weight {@code W}
 * on each pan. Weight classes: a player or a disciple 4, a spirit beast or a tamed pet 2, a dropped item 1 (at most 8
 * items per pan). Both pans at exactly {@code W} for three seconds solve it, so it works alone (stand on one pan, drop
 * items on the other) and in a party. The client tilts the balance beam by the difference.
 * <p>
 * Pan data: {@code arg} = its weight now, {@code arg2} = the weight asked.
 */
final class TimbanganPuzzle extends Puzzle {

	public static final int HOLD = 60;
	private final TekaPartEntity[] pans = new TekaPartEntity[2];
	private final int weight;
	private int held;

	TimbanganPuzzle(PuzzleHost host) {
		super(host);
		int asked = host.layout().getIntOr("weight", 0);
		this.weight = asked > 0 ? asked : 5 + random.nextInt(3);
	}

	@Override
	public Kind kind() {
		return Kind.TIMBANGAN;
	}

	@Override
	public void build() {
		Frame frame = host.frame();
		int[] at = host.layout().getIntArray("pans").orElse(new int[]{-4, 0, 4, 0});
		for (int i = 0; i < 2; i++) {
			int dx = at.length >= 4 ? at[i * 2] : (i == 0 ? -4 : 4);
			int dz = at.length >= 4 ? at[i * 2 + 1] : 0;
			Vec3 p = frame.bottom(dx, 0, dz).add(0.0D, 0.02D, 0.0D);
			TekaPartEntity pan = part(REntities.TEKA_TIMBANGAN, TekaPartEntity.PAN, p, 0.0F);
			if (pan != null) {
				pan.setArg2(weight | (i << 8));
				pans[i] = pan;
			}
		}
	}

	private int weigh(@Nullable TekaPartEntity pan) {
		if (pan == null || pan.isRemoved()) {
			return 0;
		}
		AABB box = new AABB(pan.getX() - 1.5D, pan.getY() - 0.2D, pan.getZ() - 1.5D, pan.getX() + 1.5D, pan.getY() + 2.2D, pan.getZ() + 1.5D);
		int total = 0;
		int items = 0;
		for (Entity e : host.level().getEntities((Entity) null, box, e -> e.isAlive() && !e.isSpectator())) {
			if (e instanceof Player player) {
				if (!player.isCreative()) {
					total += 4;
				}
			} else if (e instanceof DiscipleEntity) {
				total += 4;
			} else if (e instanceof ItemEntity) {
				if (items < 8) {
					items++;
					total += 1;
				}
			} else if (e instanceof TamableAnimal tame && tame.isTame() || e instanceof net.minecraft.world.entity.LivingEntity living && SpiritBeasts.data(living) != null) {
				total += 2;
			}
		}
		return total;
	}

	@Override
	public void tick(long now) {
		if (done) {
			return;
		}
		int left = weigh(pans[0]);
		int right = weigh(pans[1]);
		for (int i = 0; i < 2; i++) {
			TekaPartEntity pan = pans[i];
			if (pan != null) {
				pan.setArg(i == 0 ? left : right);
				pan.setState(host.hinted() ? TekaPartEntity.HINT : (left == weight && right == weight ? TekaPartEntity.LIT : TekaPartEntity.IDLE));
			}
		}
		if (left == weight && right == weight) {
			held++;
			if (held % 20 == 0 && pans[0] != null) {
				host.level().playSound(null, pans[0].getX(), pans[0].getY(), pans[0].getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS,
						0.8F, 0.8F + held / 60.0F);
			}
			if (held >= HOLD) {
				solve();
			}
		} else {
			held = 0;
		}
	}

	@Override
	public void reset() {
		held = 0;
	}

	@Override
	public float progress() {
		return Math.min(1.0F, held / (float) HOLD);
	}

	@Override
	public Component objective() {
		return Component.translatable("kultivator.reruntuhan.teka.timbangan.objective", weight,
				pans[0] == null ? 0 : pans[0].arg(), pans[1] == null ? 0 : pans[1].arg());
	}
}
