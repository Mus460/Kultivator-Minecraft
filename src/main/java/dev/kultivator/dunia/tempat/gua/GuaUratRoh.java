package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Gua Urat Roh (DESIGN 38.C.4, anchor {@code gua/urat_inti} in the heart of the geode, y −20..30): calcite and
 * amethyst walls, vanilla ore veins and a spirit spring. A meditation site ×1.6 (within {@code SpiritSites.CAP},
 * {@link GuaTempat}); its treasure point ({@code harta}) is remembered in {@link GuaData} so that a treasure birth may
 * choose a known spirit-vein cave in range (35 %, {@code TreasureBirths} hook, 38.A #23). Two Crystal Guardians sit
 * dormant in the floor ({@code jaga} seats) and wake on the first visit of a world day (switch
 * {@code gua.penjagaKristal}); after that day's fight the cave is quiet until the next day.
 */
public final class GuaUratRoh extends GuaRun {

	public static final double WAKE = 14.0D;

	private final List<PenjagaKristalEntity> guardians = new ArrayList<>();
	private boolean awake;

	public GuaUratRoh(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
	}

	@Override
	public double radius() {
		return 20.0D;
	}

	/** The geode's treasure point (template {@code harta}, default 7 blocks north of the heart). */
	public static BlockPos harta(DuniaAnchorEntity anchor) {
		List<int[]> h = GuaAnchors.pairs(anchor.data(), "harta");
		int[] p = h.isEmpty() ? new int[]{0, -7} : h.get(0);
		return dev.kultivator.dunia.reruntuhan.Frame.of(anchor).block(p[0], 0, p[1]);
	}

	private boolean sudahHariIni() {
		return state().getLongOr("bangun", -1L) == today();
	}

	@Override
	protected void spawn() {
		awake = false;
		if (!GuaConfig.get().penjagaKristal || sudahHariIni()) {
			return;
		}
		duduk();
	}

	private void duduk() {
		for (int[] seat : GuaAnchors.pairs(data(), "jaga")) {
			Vec3 at = spot(seat[0], 0, seat[1]);
			Vec3 dir = centre().subtract(at);
			float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
			PenjagaKristalEntity guardian = make(GuaEntities.PENJAGA_KRISTAL, at, yaw);
			if (guardian == null) {
				continue;
			}
			guardian.setup();
			guardian.setYBodyRot(yaw);
			if (add(guardian)) {
				guardians.add(guardian);
			}
		}
	}

	/** {@code uji tempat_gua penjaga}: seats the guardians again if needed and wakes them at {@code player} now. */
	void uji(ServerPlayer player) {
		guardians.removeIf(g -> g.isRemoved());
		if (guardians.isEmpty()) {
			duduk();
		}
		for (PenjagaKristalEntity g : guardians) {
			g.bangun();
			g.setTarget(player);
		}
		awake = true;
		state().putLong("bangun", today());
	}

	@Override
	protected void nearby(List<ServerPlayer> within) {
		if (awake || guardians.isEmpty()) {
			return;
		}
		for (ServerPlayer player : within) {
			if (player.position().distanceToSqr(centre()) <= WAKE * WAKE) {
				awake = true;
				state().putLong("bangun", today());
				for (PenjagaKristalEntity g : guardians) {
					g.bangun();
					g.setTarget(player);
				}
				Msg.chat(player, Gua.acak("kultivator.gua.urat.bangun", 3).withStyle(ChatFormatting.LIGHT_PURPLE));
				level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 0.5F);
				return;
			}
		}
	}

	@Override
	protected void tick(long now) {
		if (now % 40L == 0L) {
			// the vein breathes: a faint shimmer over the spring
			Vec3 c = centre();
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.6D, c.z, 2, 1.5D, 0.4D, 1.5D, 0.005D);
		}
		guardians.removeIf(g -> g.isRemoved());
	}

	@Override
	protected void despawn() {
		guardians.clear();
		awake = false;
		super.despawn();
	}
}
