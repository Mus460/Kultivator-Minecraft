package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.reruntuhan.AnchorRun;
import dev.kultivator.dunia.reruntuhan.ColliderEntity;
import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.dunia.reruntuhan.Glyphs;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.Reruntuhan;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One puzzle room of a Gua Leluhur (anchor {@code reruntuhan/teka}, DESIGN 34.5.5): the puzzle of the room's data, the
 * hint stele, the sealed exit door (a {@link ColliderEntity}) and a boss bar with the attempt's time. A player inside
 * the room (within 7.5 blocks of its centre) starts an attempt; the time running out is a backlash for everyone inside
 * and a reset. Solved rooms stay open (anchor state {@code solved_at}) and seal again {@code ruinResetDays} later, once
 * nobody is near.
 */
public final class RoomRun extends AnchorRun implements PuzzleHost {

	public static final double INSIDE = 7.5D;
	private final Puzzle.Kind kind;
	private final int tier;
	private @Nullable Puzzle puzzle;
	private @Nullable ColliderEntity door;
	private @Nullable TekaPartEntity stele;
	private final ServerBossEvent bar;
	private final Set<UUID> barred = new HashSet<>();
	private long attemptStart = -1L;
	private boolean hinted;

	public RoomRun(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
		Puzzle.Kind k = Puzzle.Kind.byId(anchor.data().getStringOr("puzzle", "bagua"));
		this.kind = k == null ? Puzzle.Kind.BAGUA : k;
		this.tier = Mth.clamp(anchor.data().getIntOr("tier", 1), 1, 3);
		this.bar = new ServerBossEvent(Mth.createInsecureUUID(level.getRandom()),
				Component.translatable("kultivator.reruntuhan.teka." + kind.id()), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
	}

	public boolean solved() {
		return state().getLongOr("solved_at", 0L) > 0L;
	}

	public Puzzle.Kind kind() {
		return kind;
	}

	// ================================================================== the run

	@Override
	protected void spawn() {
		spawnDoor();
		spawnStele();
		if (!solved() && Reruntuhan.config().ruins) {
			puzzle = Puzzle.create(kind, this);
			puzzle.build();
		}
	}

	private void spawnDoor() {
		int[] off = Frame.triple(data(), "door", 0);
		if (off == null) {
			return;
		}
		Vec3 at = frame.bottom(off[0], off[1], off[2]);
		Vec3 facing = frame.dir(off[0], off[2]);
		float width = data().getIntOr("dw", 3);
		float height = data().getIntOr("dh", 4);
		boolean alongZ = Math.abs(facing.x) > Math.abs(facing.z);
		ColliderEntity made = make(REntities.GERBANG_SEGEL, at, (float) Math.toDegrees(Math.atan2(-facing.x, facing.z)));
		if (made == null) {
			return;
		}
		made.setup(alongZ ? 0.35F : width / 2.0F, height, alongZ ? width / 2.0F : 0.35F, RuinLooks.door(tier),
				ColliderEntity.FLAG_GATE, Glyphs.DOOR_SEAL);
		made.setOwned(true);
		if (solved()) {
			made.open();
		}
		if (add(made)) {
			door = made;
		}
	}

	private void spawnStele() {
		int[] off = Frame.triple(data(), "stele", 0);
		if (off == null) {
			return;
		}
		Vec3 at = frame.bottom(off[0], off[1], off[2]);
		TekaPartEntity made = make(REntities.TEKA_PLAT, at, 0.0F);
		if (made == null) {
			return;
		}
		made.setRole(TekaPartEntity.STELE);
		made.setCenter(frame.centre());
		made.setArg(kind.ordinal());
		made.setListener((player, part, attack) -> readStele(player));
		if (add(made)) {
			stele = made;
		}
	}

	private void readStele(ServerPlayer player) {
		Component title = Component.translatable("kultivator.reruntuhan.teka." + kind.id()).withStyle(ChatFormatting.GOLD);
		Msg.chat(player, title.copy().append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.translatable("kultivator.reruntuhan.teka." + kind.id() + ".riddle").withStyle(ChatFormatting.GRAY)));
		if (puzzle != null && hinted) {
			Msg.chat(player, Component.translatable("kultivator.reruntuhan.teka.hint_prefix").withStyle(ChatFormatting.AQUA)
					.append(puzzle.hintText().copy().withStyle(ChatFormatting.WHITE)));
		} else if (solved()) {
			Msg.chat(player, Component.translatable("kultivator.reruntuhan.teka.already").withStyle(ChatFormatting.DARK_GRAY));
		}
		if (kind == Puzzle.Kind.TIMBANGAN) {
			Msg.chat(player, Component.translatable("kultivator.reruntuhan.teka.timbangan.asks", data().getIntOr("weight", 5))
					.withStyle(ChatFormatting.YELLOW));
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.8F);
	}

	@Override
	protected void tick(long now) {
		List<ServerPlayer> inside = challengers();
		updateBar(inside, now);
		Puzzle current = puzzle;
		if (current == null || current.done()) {
			return;
		}
		if (attemptStart < 0L) {
			if (!inside.isEmpty()) {
				attemptStart = now;
				hinted = false;
				current.started();
				for (ServerPlayer player : inside) {
					Msg.title(player, Component.translatable("kultivator.reruntuhan.teka." + kind.id()).withStyle(ChatFormatting.GOLD),
							current.objective().copy().withStyle(ChatFormatting.GRAY), 5, 50, 10);
				}
			}
			return;
		}
		if (inside.isEmpty() && playersWithin(INSIDE + 6.0D).isEmpty()) {
			// everybody left: the attempt rests (no timer runs for an empty room)
			attemptStart = -1L;
			current.reset();
			return;
		}
		long age = now - attemptStart;
		if (!hinted && age >= Reruntuhan.config().hintSeconds * 20L) {
			hinted = true;
			current.hint();
			for (ServerPlayer player : inside) {
				Msg.chat(player, Component.translatable("kultivator.reruntuhan.teka.hint_prefix").withStyle(ChatFormatting.AQUA)
						.append(current.hintText().copy().withStyle(ChatFormatting.WHITE)));
			}
		}
		if (age >= Puzzle.TIME_LIMIT) {
			for (ServerPlayer player : inside) {
				Msg.chat(player, Component.translatable("kultivator.reruntuhan.teka.time_up").withStyle(ChatFormatting.RED));
			}
			current.wrongAll();
			attemptStart = -1L;
			return;
		}
		current.tick(now);
	}

	private void updateBar(List<ServerPlayer> inside, long now) {
		Set<UUID> want = new HashSet<>();
		for (ServerPlayer player : inside) {
			want.add(player.getUUID());
		}
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (!want.contains(player.getUUID())) {
				bar.removePlayer(player);
				barred.remove(player.getUUID());
			}
		}
		Puzzle current = puzzle;
		if (current == null || current.done()) {
			bar.removeAllPlayers();
			barred.clear();
			return;
		}
		for (ServerPlayer player : inside) {
			if (barred.add(player.getUUID())) {
				bar.addPlayer(player);
			}
		}
		if (now % 10L == 0L) {
			long left = attemptStart < 0L ? Puzzle.TIME_LIMIT : Math.max(0L, Puzzle.TIME_LIMIT - (now - attemptStart));
			Component time = Component.literal(String.format(java.util.Locale.ROOT, "%d:%02d", left / 1200L, (left / 20L) % 60L));
			bar.setName(Component.translatable("kultivator.reruntuhan.teka.bar", Component.translatable("kultivator.reruntuhan.teka." + kind.id()),
					current.objective(), time));
			bar.setProgress(Mth.clamp(current.progress(), 0.0F, 1.0F));
		}
	}

	@Override
	protected void despawn() {
		if (puzzle != null) {
			puzzle.dispose();
			puzzle = null;
		}
		bar.removeAllPlayers();
		barred.clear();
		attemptStart = -1L;
		door = null;
		stele = null;
		super.despawn();
	}

	/** {@code uji gua selesai}: solves the room now. */
	public void testSolve() {
		if (puzzle != null) {
			puzzle.forceSolve();
		} else if (!solved()) {
			solved(null);
		}
	}

	/** {@code uji gua reset}: seals the room again. */
	public void testReset() {
		state().remove("solved_at");
		despawn();
	}

	/** Seals a solved room again after {@code ruinResetDays} (when its chunk loads and nobody is near). */
	public static void maybeReseal(DuniaAnchorEntity anchor, ServerLevel level) {
		long at = anchor.state().getLongOr("solved_at", 0L);
		if (at > 0L && level.getGameTime() - at > Reruntuhan.config().ruinResetDays * 24000L
				&& dev.kultivator.dunia.DuniaAnchors.playersNear(level, anchor.position(), 48.0D).isEmpty()) {
			anchor.state().remove("solved_at");
		}
	}

	// ================================================================== PuzzleHost

	@Override
	public ServerLevel level() {
		return level;
	}

	@Override
	public Frame frame() {
		return frame;
	}

	@Override
	public CompoundTag layout() {
		return data();
	}

	@Override
	public int tier() {
		return tier;
	}

	@Override
	public long seed() {
		long seed = data().getLongOr("seed", data().getIntOr("seed", 0));
		return seed ^ anchor.site() * 0x9E3779B97F4A7C15L;
	}

	@Override
	public List<ServerPlayer> challengers() {
		List<ServerPlayer> list = new ArrayList<>();
		Vec3 c = frame.centre();
		for (ServerPlayer player : playersWithin(INSIDE + 2.0D)) {
			double dx = player.getX() - c.x;
			double dz = player.getZ() - c.z;
			if (Math.abs(dx) <= INSIDE + 0.5D && Math.abs(dz) <= INSIDE + 0.5D && Math.abs(player.getY() - c.y) < 6.0D) {
				list.add(player);
			}
		}
		return list;
	}

	@Override
	public <T extends Entity> @Nullable T spawn(EntityType<T> type, Vec3 at, float yaw) {
		T entity = make(type, at, yaw);
		return entity != null && add(entity) ? entity : null;
	}

	@Override
	public void solved(@Nullable Puzzle solvedPuzzle) {
		state().putLong("solved_at", Math.max(1L, now()));
		if (door != null) {
			door.open();
			RFx.at(level, door.position().add(0.0D, 1.5D, 0.0D), RFx.DOOR_OPEN, tier, 0, 1.0F);
			level.playSound(null, door.getX(), door.getY(), door.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0F, 0.5F);
		}
		RFx.at(level, frame.centre(), RFx.SOLVED, tier, kind.ordinal(), 1.0F);
		for (ServerPlayer player : playersWithin(24.0D)) {
			Msg.chat(player, Component.translatable("kultivator.reruntuhan.teka.solved", Component.translatable("kultivator.reruntuhan.teka." + kind.id()))
					.withStyle(ChatFormatting.GREEN));
		}
		bar.removeAllPlayers();
		barred.clear();
		if (puzzle != null) {
			puzzle.dispose();
		}
	}

	@Override
	public void backlash(ServerPlayer player, Puzzle puzzle) {
		Reruntuhan.backlash(player, Reruntuhan.config().puzzleBacklash);
	}

	@Override
	public boolean hinted() {
		return hinted;
	}

	@Override
	public boolean inTower() {
		return false;
	}

	@Override
	public void doorGlyph(int glyph) {
		ColliderEntity d = door;
		if (d != null) {
			d.setup(d.half().x(), d.half().y(), d.half().z(), d.look(), d.flags(), glyph);
		}
	}
}
