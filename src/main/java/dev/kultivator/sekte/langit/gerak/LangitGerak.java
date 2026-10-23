package dev.kultivator.sekte.langit.gerak;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.LangitPayloads;
import dev.kultivator.sekte.langit.api.SkyHallView;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Sekte Langit, module 37M "Gerak" (DESIGN 37.5): the ways up and down a sky sect and the net under it.
 * <ul>
 * <li>{@link LangitJatuh} + {@link TerjunAwan}: the base Cloud Net (nobody of the sect dies falling off its own island) and
 * the Cloud Dive (members glide down to the ground with shift once the Jaring Awan formation runs);</li>
 * <li>{@code formasi/catalog/CatalogLangit}: the Cloud Transfer node pair (ground to island) and the Cloud Net formation;</li>
 * <li>{@link FeriAwanEntity} + {@link FeriRute} + {@link FeriPanggil}: the Cloud Ferry between the foot of the stair and the
 * island (T7);</li>
 * <li>{@link LangitDermaga}: the sky docks the sect ship sails to ({@code KapalRohEntity.sailHall});</li>
 * <li>{@link PatroliPedang}: core disciples flying sword patrols round the island (activity 25);</li>
 * <li>{@link LangitTanggaJejak} + {@link NaikTanggaGoal}: the Thousand Steps advancement and the new disciple's first climb.</li>
 * </ul>
 * Called once from {@code LangitContent.init()} on both physical sides. Registry content (the ferry entity type) is
 * registered always (K1); every runtime system checks its own switch of {@link LangitConfig}. The base net only follows
 * {@code jaringDasar} (DESIGN 37.8.7: safety stays even with {@code aktif = false}). Server code only: no client class is
 * referenced from here. No block is ever placed or broken by this module.
 */
public final class LangitGerak {

	/** Reason key of the follow veto (a disciple flying patrol or riding the ferry stays where it is). */
	public static final String VETO_KEY = "kultivator.sekte_langit.gerak.veto";

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		LangitGerakEntities.init();
		LangitJatuh.init();
		TerjunAwan.init();
		PindahAwan.init();
		FeriPanggil.init();
		PatroliPedang.init();
		LangitTanggaJejak.init();
		NaikTanggaGoal.init();
		// A disciple saved in the middle of a patrol flight or a climb (the game stopped hard) gets its gravity and AI back
		// when it loads, standing on its island (PinMurid's mark).
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof DiscipleEntity disciple && disciple.role() == DiscipleEntity.Role.DISCIPLE && PinMurid.pinned(disciple)
					&& !PatroliPedang.flying(disciple) && !NaikTanggaGoal.climbing(disciple)) {
				PinMurid.recover(level, disciple);
			}
		});
		// Audit 4.2: a disciple in the air (patrol) or on the ferry never follows its master through a dimension change.
		Disciples.addFollowVeto((player, origin, destination) -> PatroliPedang.anyFlying(player.getUUID())
				|| FeriAwanEntity.carriesDiscipleOf(player.getUUID()) ? VETO_KEY : null);
		Kultivator.LOGGER.info("[Kultivator] Sekte Langit: gerak (net, dive, ferry, docks, patrols, stair) ready");
	}

	// ================================================================== shared helpers

	static LangitApi api() {
		return LangitApi.get();
	}

	static LangitConfig cfg() {
		return LangitConfig.get();
	}

	/** The sky systems that are not safety (ferry, patrols, docks, dive) run. */
	static boolean on() {
		return LangitConfig.on();
	}

	/** True when the server's average tick is at or over {@code sectBuildMsptPause} (patrols and ferries wait). */
	static boolean busy(@Nullable MinecraftServer server) {
		if (server == null) {
			return false;
		}
		double ms = server.getAverageTickTimeNanos() / 1_000_000.0D;
		return ms >= KultivatorConfig.get().sectBuildMsptPause;
	}

	/** {@code owner}'s sky site when it lies in {@code level}, else null. */
	static @Nullable SkyHallView hallIn(Level level, UUID owner) {
		SkyHallView hall = api().skyHall(owner);
		return hall != null && hall.dim().equals(level.dimension()) ? hall : null;
	}

	/** An anchor of {@code owner}'s sky site, or null. */
	static @Nullable BlockPos anchor(UUID owner, String id) {
		try {
			return api().anchor(owner, id);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	/** Every anchor of {@code owner}'s sky site (never null). */
	static Map<String, BlockPos> anchors(UUID owner) {
		try {
			Map<String, BlockPos> map = api().anchors(owner);
			return map == null ? Map.of() : map;
		} catch (RuntimeException exception) {
			return Map.of();
		}
	}

	/** {@code entity} belongs to {@code owner}'s sect (owner, mate, disciple, tame animal of one of them). */
	static boolean member(UUID owner, Entity entity) {
		try {
			return api().member(owner, entity);
		} catch (RuntimeException exception) {
			return false;
		}
	}

	/** A transient sky effect ({@code LangitIds.FX_*}) for every player within 96 blocks of {@code a}. */
	static void fx(ServerLevel level, int op, BlockPos a, BlockPos b) {
		LangitPayloads.fxNear(level, op, a, b, 96.0D);
	}

	private LangitGerak() {
	}
}
