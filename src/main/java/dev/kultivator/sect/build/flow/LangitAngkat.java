package dev.kultivator.sect.build.flow;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.FootMode;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.sect.build.engine.BuildEngine;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.LangitPayloads;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Ascension (Upacara Naik, DESIGN 37.3.5): the hall's stele, carried by its master from the ground to the throne of
 * the sky island.
 * <ol>
 * <li>When the Cloud Core (tier 1) of a tethered sky site stands, the master is told how to rise ({@link #coreDone}).</li>
 * <li>The master takes up the ground stele: the hall sleeps, the site waits ("Prasasti siap dibawa ke langit").</li>
 * <li>The master flies up (sword, ship, ferry, cloud step) and sets the stele on the throne's jade base: this listener
 * (registered before the relocation dialog) takes over and asks: path A "rise, the Mountain Foot stays / is taken down
 * / is left"; path B "rise"; or cancel. The Grand Formation of the ground does not rise with the hall.</li>
 * <li>The choice wakes the hall on the throne ({@code Halls.relocateHall}), swaps the sites ({@link EngineApi#ascend}),
 * guarantees the old hall level for {@code jaminanHari} world days, spends the offering, rings the bells, raises the
 * nine rings of light ({@code FX_NAIK}) and brings the master's disciples up through the clouds, two a second.</li>
 * </ol>
 * A stele set anywhere else on the ground re-tethers the site within reach (or pauses it). Nothing here changes a block.
 */
public final class LangitAngkat implements Halls.SteleMoveListener {

	public static final LangitAngkat INSTANCE = new LangitAngkat();
	/** Disciples within this many blocks of the old stele rise with the hall. */
	public static final double DISCIPLE_RANGE = 256.0D;
	/** One disciple rises every this many ticks (two a second). */
	public static final int DISCIPLE_EVERY = 10;
	/** The voice line of the Ascension (played when its clip exists; else the bells only). */
	public static final String VOICE = "heaven.langit_naik";

	private record Pending(ResourceKey<Level> dim, BlockPos throne, long deadline) {
	}

	private record Rising(UUID owner, UUID disciple) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static final Deque<Rising> RISING = new ArrayDeque<>();
	private static long ticks;
	private static @Nullable MinecraftServer server;

	// ================================================================== the steps

	/** The Cloud Core of a tethered sky site stands (from the engine). */
	public static void coreDone(ServerPlayer master, SiteView sky) {
		Msg.chat(master, Component.translatable("kultivator.sekte_langit.naik.inti_siap").withStyle(ChatFormatting.GOLD));
		MutableComponent buttons = Component.empty();
		buttons.append(Ceremonies.button("kultivator.sectbuild.button.langit_cara_naik", "/kultivasi aula langit info"));
		master.sendSystemMessage(buttons);
		ServerLevel level = master.level();
		if (sky.dim().equals(level.dimension())) {
			BlockPos ground = sky.stele();
			Halls.HallView hall = Halls.hallOf(master.getUUID());
			if (hall != null) {
				ground = hall.pos();
			}
			LangitPayloads.fxNear(level, LangitIds.FX_PILAR, ground, sky.stele(), 256.0D);
		}
	}

	@Override
	public void onRemoved(UUID owner, @Nullable ServerLevel level, @Nullable BlockPos pos) {
		SiteView sky = EngineApi.get().skySite(owner);
		if (sky == null || !sky.tethered() || pos == null) {
			return;
		}
		BuildEngine.INSTANCE.setAscent(owner, 1);
		ServerPlayer master = Ceremonies.online(owner);
		if (master != null) {
			Msg.actionBar(master, Component.translatable("kultivator.sekte_langit.naik.siap_dibawa").withStyle(ChatFormatting.GOLD));
		}
	}

	@Override
	public boolean beforeMove(ServerPlayer player, UUID owner, ServerLevel level, @Nullable BlockPos from, BlockPos to, boolean wake) {
		SiteView sky = EngineApi.get().skySite(owner);
		if (sky == null || !sky.tethered() || !player.getUUID().equals(owner) || !sky.dim().equals(level.dimension())) {
			return false;
		}
		if (to.equals(sky.stele())) {
			ask(player, sky, level, to);
			return true;
		}
		// Elsewhere on the ground: the sky site follows the stele within reach, else it waits (37.3.5 item 7).
		int dx = Math.abs(to.getX() - sky.stele().getX());
		int dz = Math.abs(to.getZ() - sky.stele().getZ());
		boolean reach = dx <= LangitPlan.offsetMax() && dz <= LangitPlan.offsetMax();
		boolean pathB = EngineApi.get().template(owner) == sky.template();
		if (!pathB && !reach) {
			// Path A, far away: the ordinary move dialog of the ground site decides; the sky site pauses.
			BuildEngine.INSTANCE.retether(owner, to, false);
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.naik.terlalu_jauh").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!pathB) {
			// Path A within reach: the ground site's own move dialog runs; the sky site follows the new stele.
			BuildEngine.INSTANCE.retether(owner, to, true);
			return false;
		}
		// Path B: the hall simply wakes (or moves) there; the sky site follows or pauses.
		if (Halls.relocateHall(player, level, to)) {
			BuildEngine.INSTANCE.retether(owner, to, reach);
			if (!reach) {
				Msg.chat(player, Component.translatable("kultivator.sekte_langit.naik.terlalu_jauh").withStyle(ChatFormatting.YELLOW));
			}
		}
		return true;
	}

	private static void ask(ServerPlayer player, SiteView sky, ServerLevel level, BlockPos throne) {
		UUID owner = player.getUUID();
		boolean pathA = EngineApi.get().template(owner) != sky.template();
		if (sky.tier() < LangitPlan.TETHER_MAX_TIER) {
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.naik.inti_belum").withStyle(ChatFormatting.YELLOW));
		}
		PENDING.put(owner, new Pending(level.dimension(), throne.immutable(), ticks + Relocation.MOVE_TICKS));
		Component name = hallName(owner);
		Msg.chat(player, Component.translatable("kultivator.sekte_langit.naik.tanya", name).withStyle(ChatFormatting.GOLD));
		Msg.chat(player, Component.translatable("kultivator.sekte_langit.naik.peringatan").withStyle(ChatFormatting.GRAY));
		MutableComponent buttons = Component.empty();
		if (pathA) {
			buttons.append(Ceremonies.button("kultivator.sectbuild.button.langit_naik_tetap", "/kultivasi aula langit naik tetap"));
			buttons.append(Component.literal(" "));
			buttons.append(Ceremonies.button("kultivator.sectbuild.button.langit_naik_bongkar", "/kultivasi aula langit naik bongkar"));
			buttons.append(Component.literal(" "));
			buttons.append(Ceremonies.button("kultivator.sectbuild.button.langit_naik_tinggalkan", "/kultivasi aula langit naik tinggalkan"));
		} else {
			buttons.append(Ceremonies.button("kultivator.sectbuild.button.langit_naik", "/kultivasi aula langit naik tetap"));
		}
		buttons.append(Component.literal(" "));
		buttons.append(Ceremonies.button("kultivator.sectbuild.button.cancel", "/kultivasi aula langit naik batal"));
		player.sendSystemMessage(buttons);
	}

	/** The master's answer: rise with {@code mode} (path B ignores it). */
	public static void confirm(ServerPlayer player, FootMode mode) {
		UUID owner = player.getUUID();
		Pending pending = PENDING.remove(owner);
		if (pending == null || ticks > pending.deadline() || server == null) {
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.naik.tidak_ada").withStyle(ChatFormatting.YELLOW));
			return;
		}
		ServerLevel level = server.getLevel(pending.dim());
		if (level == null) {
			return;
		}
		EngineApi engine = EngineApi.get();
		SiteView sky = engine.skySite(owner);
		if (sky == null || !sky.tethered() || !sky.stele().equals(pending.throne())) {
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.naik.tidak_ada").withStyle(ChatFormatting.YELLOW));
			return;
		}
		BlockPos oldStele = Halls.hallOf(owner) == null ? pending.throne() : Halls.hallOf(owner).pos();
		int oldTier = Math.max(engine.tier(owner), FlowData.guaranteeTier(owner));
		boolean pathA = engine.template(owner) != sky.template();
		if (!Halls.relocateHall(player, level, pending.throne())) {
			return;
		}
		Component error = engine.ascend(owner, mode);
		if (error != null) {
			Msg.chat(player, error.copy().withStyle(ChatFormatting.RED));
			return;
		}
		ascended(player, level, sky, oldStele, oldTier, pathA ? mode : null);
	}

	/** {@code /kultivasi aula langit naik batal}: the stele stays on the throne, the hall asleep; nothing changes. */
	public static void cancel(ServerPlayer player) {
		if (PENDING.remove(player.getUUID()) != null) {
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.naik.batal").withStyle(ChatFormatting.GRAY));
		}
	}

	/** Everything after the swap: guarantee, offering, story, effects, the disciples' rise. */
	static void ascended(ServerPlayer player, ServerLevel level, SiteView sky, BlockPos oldStele, int oldTier, @Nullable FootMode foot) {
		UUID owner = player.getUUID();
		int days = LangitConfig.get().jaminanHari;
		if (oldTier > 0 && days > 0) {
			FlowData.setGuarantee(owner, oldTier, FlowData.day() + days, "move");
		}
		LangitSesaji.settle(owner);
		Halls.log(owner, Component.translatable("kultivator.sekte_langit.naik.log", SectTemplates.name(sky.template())));
		Halls.refreshBuildings(owner);
		Msg.chat(player, Component.translatable("kultivator.sekte_langit.naik.berhasil", hallName(owner), oldTier, days).withStyle(ChatFormatting.GOLD));
		if (foot != null) {
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.naik.kaki_" + foot.word()).withStyle(ChatFormatting.GRAY));
		}
		Advancements.grant(player, LangitIds.ADV + "naik");
		Vec3 throne = Vec3.atBottomCenterOf(sky.stele());
		if (VoiceEvents.exists(VOICE)) {
			Voice.heavenNear(level, throne, VOICE, 96.0D);
		}
		level.playSound(null, throne.x, throne.y, throne.z, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 0.8F);
		level.playSound(null, throne.x, throne.y, throne.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.playSound(null, throne.x, throne.y, throne.z, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.2F, 0.6F);
		LangitPayloads.fxNear(level, LangitIds.FX_NAIK, sky.stele(), sky.stele().above(40), 256.0D);
		// The disciples rise with the hall, two a second, out of a puff of cloud.
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.level() == level && disciple.role() == DiscipleEntity.Role.DISCIPLE
					&& disciple.position().distanceToSqr(Vec3.atCenterOf(oldStele)) <= DISCIPLE_RANGE * DISCIPLE_RANGE) {
				RISING.add(new Rising(owner, disciple.getUUID()));
			}
		}
	}

	// ================================================================== tick

	static void tick(MinecraftServer current, long now) {
		server = current;
		ticks = now;
		if (now % 20L == 0L) {
			PENDING.values().removeIf(pending -> now > pending.deadline());
		}
		if (RISING.isEmpty() || now % DISCIPLE_EVERY != 0L) {
			return;
		}
		Rising next = RISING.poll();
		if (next == null) {
			return;
		}
		BlockPos arrival = EngineApi.get().anchor(next.owner(), "arrival");
		SiteView sky = EngineApi.get().site(next.owner());
		if (arrival == null && sky != null) {
			arrival = sky.stele().south(6);
		}
		if (arrival == null || sky == null) {
			return;
		}
		ServerLevel level = current.getLevel(sky.dim());
		if (level == null) {
			return;
		}
		for (DiscipleEntity disciple : Disciples.loaded(next.owner())) {
			if (!disciple.getUUID().equals(next.disciple()) || disciple.level() != level) {
				continue;
			}
			int spread = (int) (now / DISCIPLE_EVERY % 5) - 2;
			BlockPos from = disciple.blockPosition();
			disciple.teleportTo(arrival.getX() + 0.5D + spread, arrival.getY(), arrival.getZ() + 0.5D + (spread % 2));
			disciple.resetFallDistance();
			LangitPayloads.fxNear(level, LangitIds.FX_ARRIVE, disciple.blockPosition(), from, 96.0D);
			break;
		}
	}

	private static Component hallName(UUID owner) {
		Halls.HallView hall = Halls.hallOf(owner);
		return hall == null || hall.name() == null || hall.name().isBlank() ? Component.translatable("kultivator.sekte_langit.udara.tanpa_nama")
				: Component.literal(hall.name());
	}

	static void clear() {
		PENDING.clear();
		RISING.clear();
		server = null;
	}

	private LangitAngkat() {
	}
}
