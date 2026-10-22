package dev.kultivator.sekte.tetua;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicMoments;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.VoiceDisciples;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Upacara Penyerahan Takhta, the Succession Ceremony (DESIGN 36.2.6): 90 s before the main hall (the plan's
 * {@code main_hall} anchor, else the stele). The heir walks to the centre and kneels; up to {@code barisanMaks} disciples
 * form two rows along the aisle and bow; incense rises; at 60 s the climax: the robe and bowl pass, beat 150 "Penyerahan
 * Takhta" (a full card, the glory theme, a rising crane shot; skippable, Penuh/Singkat/Mati, never in danger) and the
 * permanent result ({@link Generasi#succeed}); the new Ketua stands while the rows stay bowed until the end.
 * <p>
 * Every walk uses the elders' own spot-holding activity {@code ACT_OFFICE} (no order of any disciple changes, nothing
 * to restore after a restart), and the clients learn from E's own {@code TetuaPayloads.Ceremony} which bodies bow
 * instead of writing at a desk. It breaks off before the climax when the master walks away (24 blocks), the heir is gone
 * or fights, or an enemy comes within 24 blocks (the existing {@code EpicMoments.resultSafe}); it can simply be started
 * again. No block is touched.
 */
final class PenyerahanTakhta {

	static final int LENGTH = 1800;
	static final int CLIMAX = 1200;
	static final int GATHER = 300;
	static final double MASTER_RANGE = 24.0D;
	static final double HEIR_RANGE = 48.0D;
	/** Phases the clients pose by. */
	static final int PHASE_GATHER = 0;
	static final int PHASE_KNEEL = 1;
	static final int PHASE_RISEN = 2;
	static final int PHASE_END = 3;

	private static final class Ceremony {
		final UUID owner;
		final DiscipleEntity heir;
		final Vec3 centre;
		final Vec3 aisle;
		final List<DiscipleEntity> rows = new ArrayList<>();
		final List<BlockPos> spots = new ArrayList<>();
		int ticks;
		boolean climaxed;
		int phase = PHASE_GATHER;

		Ceremony(UUID owner, DiscipleEntity heir, Vec3 centre, Vec3 aisle) {
			this.owner = owner;
			this.heir = heir;
			this.centre = centre;
			this.aisle = aisle;
		}
	}

	private static final Map<UUID, Ceremony> RUNNING = new HashMap<>();

	static boolean running(UUID owner) {
		return RUNNING.containsKey(owner);
	}

	/** True while {@code entity} takes part in a ceremony (its ACT_OFFICE belongs to the ceremony). */
	static boolean part(DiscipleEntity entity) {
		for (Ceremony c : RUNNING.values()) {
			if (c.heir == entity || c.rows.contains(entity)) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== start

	/** Starts the ceremony (requirements checked unless {@code force}); the reason it cannot, or null. */
	static @Nullable Component start(MinecraftServer server, ServerPlayer master, boolean force, @Nullable DiscipleEntity forcedHeir) {
		UUID owner = master.getUUID();
		if (RUNNING.containsKey(owner)) {
			return Component.translatable("kultivator.sekte.tetua.takhta.why.running");
		}
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null) {
			return Component.translatable("kultivator.sekte.tetua.why.no_hall");
		}
		if (!force && !Generasi.ready(server, owner)) {
			return Component.translatable("kultivator.sekte.tetua.takhta.why.requirements");
		}
		UUID soul = forcedHeir != null ? forcedHeir.soulId() : Generasi.heir(owner);
		DiscipleEntity heir = forcedHeir != null ? forcedHeir : soul == null ? null : JabatanTetua.loaded(owner, soul);
		if (heir == null || heir.level() != master.level()) {
			return Component.translatable("kultivator.sekte.tetua.takhta.why.heir_away");
		}
		if (!(master.level() instanceof ServerLevel level) || !level.dimension().equals(hall.dim())) {
			return Component.translatable("kultivator.sekte.tetua.takhta.why.place");
		}
		BlockPos anchor = SectBuild.anchor(owner, "main_hall");
		BlockPos base = anchor != null ? anchor : hall.pos().offset(0, 0, 3);
		BlockPos centreSpot = JabatanTetua.standable(level, base);
		if (centreSpot == null) {
			return Component.translatable("kultivator.sekte.tetua.takhta.why.place");
		}
		Vec3 centre = Vec3.atBottomCenterOf(centreSpot);
		if (master.position().distanceTo(centre) > MASTER_RANGE || heir.position().distanceTo(centre) > HEIR_RANGE) {
			return Component.translatable("kultivator.sekte.tetua.takhta.why.gather", Math.round(MASTER_RANGE));
		}
		if (!EpicMoments.resultSafe(master) || !EpicMoments.safe(master, null)) {
			return Component.translatable("kultivator.sekte.tetua.takhta.why.danger");
		}
		if (!Tetua.free(heir, true)) {
			return Component.translatable("kultivator.sekte.tetua.takhta.why.heir_busy", heir.getName());
		}
		// the aisle runs from the centre toward the master (the master faces the kneeling heir)
		Vec3 toMaster = master.position().subtract(centre);
		Vec3 aisle = new Vec3(toMaster.x, 0.0D, toMaster.z);
		aisle = aisle.lengthSqr() < 1.0E-3D ? new Vec3(0.0D, 0.0D, 1.0D) : aisle.normalize();
		Ceremony c = new Ceremony(owner, heir, centre, aisle);
		heir.setActivity(SekteIds.ACT_OFFICE, centreSpot);
		// the rows: free disciples in the territory, at most barisanMaks, alternating sides behind the heir
		int max = Tetua.config().barisanMaks;
		Vec3 side = new Vec3(-aisle.z, 0.0D, aisle.x);
		int n = 0;
		for (DiscipleEntity d : Disciples.loaded(owner)) {
			if (n >= max) {
				break;
			}
			if (d == heir || d.level() != level || d.role() != DiscipleEntity.Role.DISCIPLE || !Tetua.free(d, true)
					|| d.position().distanceTo(centre) > 48.0D) {
				continue;
			}
			int rank = n / 2;
			double s = (n % 2 == 0 ? 1.0D : -1.0D) * 2.6D;
			Vec3 at = centre.add(side.scale(s)).subtract(aisle.scale(1.0D + rank * 1.6D));
			BlockPos spot = JabatanTetua.standable(level, BlockPos.containing(at));
			if (spot == null) {
				continue;
			}
			d.setActivity(SekteIds.ACT_OFFICE, spot);
			c.rows.add(d);
			c.spots.add(spot);
			n++;
		}
		RUNNING.put(owner, c);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.4F, 0.6F);
		Msg.chat(master, Component.translatable("kultivator.sekte.tetua.takhta.start", heir.getName(), c.rows.size()).withStyle(ChatFormatting.GOLD));
		for (ServerPlayer other : level.players()) {
			if (other != master && other.position().distanceTo(centre) <= 64.0D) {
				Msg.chat(other, Component.translatable("kultivator.sekte.tetua.takhta.start_other", master.getName(), heir.getName())
						.withStyle(ChatFormatting.GOLD));
			}
		}
		sync(level, c, PHASE_GATHER);
		return null;
	}

	// ================================================================== tick

	static void tick(MinecraftServer server) {
		if (RUNNING.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Ceremony>> it = RUNNING.entrySet().iterator();
		while (it.hasNext()) {
			Ceremony c = it.next().getValue();
			ServerPlayer master = server.getPlayerList().getPlayer(c.owner);
			DiscipleEntity heir = c.heir;
			int t = ++c.ticks;
			boolean heirGone = heir.isRemoved() || !heir.isAlive() || heir.getTarget() != null;
			boolean masterGone = master == null || master.level() != heir.level() || master.position().distanceTo(c.centre) > MASTER_RANGE;
			if (!c.climaxed && (heirGone || masterGone || t % 20 == 0 && master != null && !EpicMoments.resultSafe(master))) {
				if (master != null) {
					Msg.chat(master, Component.translatable("kultivator.sekte.tetua.takhta.broken", heir.getName()).withStyle(ChatFormatting.RED));
				}
				finish(c);
				it.remove();
				continue;
			}
			if (!(heir.level() instanceof ServerLevel level)) {
				finish(c);
				it.remove();
				continue;
			}
			if (t % 20 == 0) {
				lookIn(c, master);
			}
			if (t % 100 == 0) {
				// players who came near since the start learn the poses too
				sync(level, c, c.phase);
			}
			if (c.phase == PHASE_GATHER && (t >= GATHER || allArrived(c))) {
				c.phase = PHASE_KNEEL;
				sync(level, c, PHASE_KNEEL);
				level.playSound(null, c.centre.x, c.centre.y, c.centre.z, SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 1.0F, 0.7F);
			}
			if (c.phase == PHASE_KNEEL && t % 40 == 0) {
				// incense from the centre, the qi gathering toward the heir
				Fx.burst(level, c.centre.add(0.0D, 0.3D, 0.0D), 0xFFE9A8, 10, 0.5F);
				Fx.ring(level, c.centre.add(0.0D, 0.05D, 0.0D), 0xFFD54A, 1.2F + (t % 120) / 60.0F);
			}
			if (t == 600 || t == 900) {
				level.playSound(null, c.centre.x, c.centre.y, c.centre.z, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.0F, 0.75F);
			}
			if (!c.climaxed && t >= CLIMAX && master != null) {
				climax(level, master, c);
			}
			if (c.climaxed && t % 100 == 50 && !c.rows.isEmpty()) {
				DiscipleEntity voice = c.rows.get((t / 100) % c.rows.size());
				VoiceDisciples.line(voice, "cheer", 400);
			}
			if (t >= LENGTH) {
				if (master != null) {
					Msg.chat(master, Component.translatable("kultivator.sekte.tetua.takhta.end").withStyle(ChatFormatting.GRAY));
				}
				finish(c);
				it.remove();
			}
		}
	}

	private static boolean allArrived(Ceremony c) {
		if (c.heir.distanceToSqr(c.centre) > 2.5D * 2.5D) {
			return false;
		}
		for (int i = 0; i < c.rows.size(); i++) {
			DiscipleEntity d = c.rows.get(i);
			if (!d.isRemoved() && d.distanceToSqr(Vec3.atBottomCenterOf(c.spots.get(i))) > 2.5D * 2.5D) {
				return false;
			}
		}
		return true;
	}

	/** Everyone turns to the heir; the heir faces the master. */
	private static void lookIn(Ceremony c, @Nullable ServerPlayer master) {
		if (master != null && c.heir.distanceToSqr(c.centre) <= 4.0D) {
			c.heir.getLookControl().setLookAt(master, 30.0F, 30.0F);
		}
		for (DiscipleEntity d : c.rows) {
			if (!d.isRemoved() && d.getNavigation().isDone()) {
				d.getLookControl().setLookAt(c.centre.x, c.centre.y + 0.8D, c.centre.z, 30.0F, 30.0F);
			}
		}
	}

	private static void climax(ServerLevel level, ServerPlayer master, Ceremony c) {
		c.climaxed = true;
		c.phase = PHASE_RISEN;
		MinecraftServer server = level.getServer();
		int generation = Generasi.succeed(server, master, c.heir);
		Vec3 pos = c.heir.position();
		Fx.pillar(level, pos, 0xFFD54A, 9.0F);
		Fx.ring(level, pos.add(0.0D, 0.1D, 0.0D), 0xFFE9A8, 4.0F);
		Fx.burst(level, pos.add(0.0D, 1.2D, 0.0D), 0xFFD54A, 48, 1.4F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 1.4F, 0.8F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.0F, 1.1F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 0.8F, 1.0F);
		// the full card (beat 150): every 7b camera guard applies on the client
		EpicHooks.extra(level, Tetua.BEAT_THRONE, master, c.heir, pos, generation, 0);
		VoiceDisciples.line(c.heir, "loyal", 0);
		sync(level, c, PHASE_RISEN);
	}

	private static void finish(Ceremony c) {
		if (!c.heir.isRemoved() && c.heir.activity() == SekteIds.ACT_OFFICE) {
			c.heir.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		for (DiscipleEntity d : c.rows) {
			if (!d.isRemoved() && d.activity() == SekteIds.ACT_OFFICE) {
				d.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
		}
		if (c.heir.level() instanceof ServerLevel level) {
			sync(level, c, PHASE_END);
		}
	}

	/** Tells the clients near the centre which bodies bow and kneel (E's own payload). */
	private static void sync(ServerLevel level, Ceremony c, int phase) {
		int[] ids = new int[c.rows.size()];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = c.rows.get(i).getId();
		}
		int life = phase == PHASE_END ? 0 : LENGTH - c.ticks + 100;
		TetuaPayloads.ceremony(level, c.centre, ids, c.heir.getId(), phase, life);
	}

	/** Seconds left, or -1. */
	static int secondsLeft(UUID owner) {
		Ceremony c = RUNNING.get(owner);
		return c == null ? -1 : Math.max(0, (LENGTH - c.ticks) / 20);
	}

	/** {@code uji}: jump to the climax. */
	static boolean skip(UUID owner) {
		Ceremony c = RUNNING.get(owner);
		if (c == null || c.climaxed) {
			return false;
		}
		c.ticks = CLIMAX - 1;
		return true;
	}

	static void clear() {
		for (Ceremony c : RUNNING.values()) {
			finish(c);
		}
		RUNNING.clear();
	}

	private PenyerahanTakhta() {
	}
}
