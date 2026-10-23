package dev.kultivator.formasi.effect.b;

import dev.kultivator.formasi.FormasiFxIds;
import dev.kultivator.formasi.core.CoreState;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.SkyHallView;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Formasi Jaring Awan (DESIGN 37.5.6 b, spec {@code jaring_awan} of {@code CatalogLangit}): the sky island's net of clouds
 * hung between four rim poles. It does nothing to blocks or mobs itself; while it runs (each pulse renews a short lease)
 * the base Cloud Net ({@code sekte.langit.gerak.LangitJatuh}) also catches the hall's guests (invited players, 37G's
 * allied visitors) and members may take the Cloud Dive ({@code TerjunAwan}). It stops by itself when the core stops: the
 * base net always stays. Upkeep is the Misterius grade's (6 Daya an hour, ×0.25 while nobody is on the island), and it
 * can be kept by a disciple like every formation.
 * <p>
 * Extra suspension reasons ({@link #blocker}): the sky systems are off, the hall is not a standing sky hall, the sky
 * site has not finished tier 5, the eye is not on the island, or fewer than four rim poles ({@code jaring_0..3}: a
 * quartz pillar or an end rod near each anchor) still stand. <i>Terinspirasi dari</i> <i>Liezi</i>, "Tang Wen": the
 * fifteen giant turtles that held the floating mountains of Guixu so they would not drift away; the net is game design.
 */
public final class JaringAwanEffect implements FormationEffect {

	/** The lease a pulse grants (two pulses plus a second of slack). */
	public static final long LEASE_TICKS = 100L;
	/** The tier of the sky site that builds the rim poles. */
	public static final int MIN_TIER = 5;
	public static final int POLES = 4;
	/** How long a pole count is trusted (ticks). */
	private static final long POLE_CACHE = 200L;

	private static final Map<UUID, Long> ACTIVE = new ConcurrentHashMap<>();
	private static final Map<UUID, long[]> POLE_COUNT = new ConcurrentHashMap<>();

	/** True while {@code owner}'s Jaring Awan ran within the last {@link #LEASE_TICKS} ticks ({@code now}: game time). */
	public static boolean active(UUID owner, long now) {
		Long until = owner == null ? null : ACTIVE.get(owner);
		return until != null && now <= until && LangitConfig.on();
	}

	@Override
	public void pulse(CoreContext ctx) {
		long now = ctx.level().getGameTime();
		ACTIVE.put(ctx.owner(), now + LEASE_TICKS);
		if (now % 160L < ctx.spec().pulseTicks() && !ctx.allies(1).isEmpty()) {
			ctx.fx(FormasiFxIds.PULSE, ctx.centre(), ctx.spec().family().ordinal(), (float) Math.min(24.0D, ctx.radius()));
		}
	}

	@Override
	public boolean idle(CoreContext ctx) {
		return ctx.allies(1).isEmpty();
	}

	@Override
	public void onState(CoreContext ctx, CoreState from, CoreState to) {
		if (!to.running()) {
			ACTIVE.remove(ctx.owner());
		}
	}

	@Override
	public @Nullable Component blocker(CoreContext ctx) {
		if (!LangitConfig.on()) {
			return Component.translatable("kultivator.sekte_langit.gerak.jaring.mati");
		}
		SkyHallView hall = LangitApi.get().skyHall(ctx.owner());
		if (hall == null || hall.tethered() || !hall.dim().equals(ctx.level().dimension())) {
			return Component.translatable("kultivator.sekte_langit.gerak.jaring.bukan_langit");
		}
		if (hall.tier() < MIN_TIER) {
			return Component.translatable("kultivator.sekte_langit.gerak.jaring.tingkat", MIN_TIER);
		}
		BlockPos eye = ctx.core().eye();
		if (!hall.inVolume(eye)) {
			return Component.translatable("kultivator.sekte_langit.gerak.jaring.luar");
		}
		int poles = poles(ctx.level(), ctx.owner());
		if (poles < POLES) {
			return Component.translatable("kultivator.sekte_langit.gerak.jaring.tiang", poles, POLES);
		}
		return null;
	}

	@Override
	public void sync(CoreContext ctx, CompoundTag out) {
		out.putByte("jn", (byte) (active(ctx.owner(), ctx.level().getGameTime()) ? 1 : 0));
	}

	/** Rim poles still standing near the anchors {@code jaring_0..3} (cached {@link #POLE_CACHE} ticks; read-only). */
	static int poles(ServerLevel level, UUID owner) {
		long now = level.getGameTime();
		long[] cached = POLE_COUNT.get(owner);
		if (cached != null && now - cached[0] >= 0L && now - cached[0] < POLE_CACHE) {
			return (int) cached[1];
		}
		int count = 0;
		LangitApi api = LangitApi.get();
		for (int i = 0; i < POLES; i++) {
			BlockPos anchor = api.anchor(owner, "jaring_" + i);
			if (anchor != null && pole(level, anchor)) {
				count++;
			}
		}
		POLE_COUNT.put(owner, new long[]{now, count});
		return count;
	}

	/** A quartz pillar or an end rod in the 3 × 7 × 3 column around {@code anchor} (loaded chunks only). */
	private static boolean pole(ServerLevel level, BlockPos anchor) {
		if (!level.isLoaded(anchor)) {
			// an unloaded pole cannot have been broken by anyone: trust it
			return true;
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dy = -1; dy <= 5; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					cursor.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
					BlockState state = level.getBlockState(cursor);
					if (state.is(Blocks.QUARTZ_PILLAR) || state.is(Blocks.END_ROD)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Server stop. */
	public static void clearAll() {
		ACTIVE.clear();
		POLE_COUNT.clear();
	}
}
