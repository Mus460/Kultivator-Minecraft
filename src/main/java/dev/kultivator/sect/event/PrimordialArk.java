package dev.kultivator.sect.event;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bahtera Purba, the ghostly Primordial Ark (DESIGN 20.3.10): for one in-game day after a Turnamen Elit it floats over
 * the hall (client particles only, no block); that tournament's top 3 disciples may board it once for 24,000 ticks and
 * come back with a technique book, a Kunpeng piece the hall lacks and 60% of a bar.
 * <p>
 * Sumber: Against the Gods ch. 454 (the Primordial Profound Ark opens for a single day right after the Seven Nation
 * tournament). The boarding rule is game design.
 */
final class PrimordialArk {

	public static final long OPEN_TICKS = 24000L;
	public static final long AWAY_TICKS = 24000L;
	public static final double SHARE = 0.60D;
	/** The ark floats this high over the stele. */
	public static final double HEIGHT = 18.0D;

	static CompoundTag ark(UUID owner) {
		return EventUtil.section(owner).getCompoundOrEmpty("ark");
	}

	static boolean open(UUID owner) {
		return ark(owner).getLongOr("until", 0L) > EventUtil.gameTime();
	}

	/** Opens the ark for the tournament's top three (souls in finishing order). */
	static void openFor(UUID owner, Halls.HallView hall, List<UUID> topThree) {
		CompoundTag ark = new CompoundTag();
		ark.putLong("until", EventUtil.gameTime() + OPEN_TICKS);
		ListTag top = new ListTag();
		for (UUID soul : topThree) {
			top.add(StringTag.valueOf(soul.toString()));
		}
		ark.put("top", top);
		ark.put("boarded", new ListTag());
		EventUtil.section(owner).put("ark", ark);
		EventUtil.dirty();
		ServerLevel level = EventUtil.hallLevel(hall);
		if (level != null) {
			Vec3 at = EventUtil.center(hall).add(0.0D, HEIGHT, 0.0D);
			EventUtil.fx(level, at, SectEventFxIds.ARK, 220, 0.0F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 1.0F, 0.5F);
		}
		EventUtil.chat(EventUtil.player(owner), Component.translatable("kultivator.sectevent.ark.open").withStyle(ChatFormatting.LIGHT_PURPLE));
		Halls.log(owner, Component.translatable("kultivator.sectevent.log.ark"));
	}

	/** True when the disciple may board now. */
	static boolean mayBoard(UUID owner, UUID soul) {
		CompoundTag ark = ark(owner);
		return open(owner) && contains(ark.getListOrEmpty("top"), soul) && !contains(ark.getListOrEmpty("boarded"), soul);
	}

	private static boolean contains(ListTag list, UUID soul) {
		String id = soul.toString();
		for (Tag tag : list) {
			if (id.equals(tag.asString().orElse(""))) {
				return true;
			}
		}
		return false;
	}

	static boolean board(ServerPlayer player, DiscipleEntity disciple) {
		UUID owner = player.getUUID();
		UUID soul = disciple.soulId();
		if (Expeditions.refuses(disciple)) {
			EventUtil.chat(player, Component.translatable(Expeditions.REFUSES).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!mayBoard(owner, soul)) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.ark.not_allowed", disciple.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		Component name = disciple.getDisplayName();
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall != null && disciple.level() instanceof ServerLevel level) {
			EventUtil.fx(level, disciple.position(), EventUtil.center(hall).add(0.0D, HEIGHT, 0.0D), SectEventFxIds.ARK_BOARD, 0, 0.0F);
		}
		if (AwayTeams.depart(player, AwayTeams.KIND_ARK, List.of(disciple), AWAY_TICKS, new CompoundTag()) == null) {
			return false;
		}
		EventUtil.list(EventUtil.sub(EventUtil.section(owner), "ark"), "boarded").add(StringTag.valueOf(soul.toString()));
		EventUtil.dirty();
		EventUtil.chat(player, Component.translatable("kultivator.sectevent.ark.boarded", name).withStyle(ChatFormatting.LIGHT_PURPLE));
		return true;
	}

	static void resolve(UUID owner, CompoundTag team, @Nullable ServerPlayer player, RandomSource random) {
		for (Tag tag : team.getListOrEmpty("members")) {
			if (!(tag instanceof CompoundTag member)) {
				continue;
			}
			DiscipleData data = AwayTeams.data(member);
			DisciplePaths.grantBarShare(data, SHARE);
			AwayTeams.setData(member, data);
			List<ItemStack> loot = new ArrayList<>();
			ItemStack book = Expeditions.mappedBook(random);
			if (!book.isEmpty()) {
				loot.add(book);
			}
			int have = Expeditions.pieces(owner, player);
			List<Integer> missing = new ArrayList<>();
			for (int piece = 1; piece <= 6; piece++) {
				if ((have & 1 << (piece - 1)) == 0) {
					missing.add(piece);
				}
			}
			loot.add(SectEventItems.fragment(missing.isEmpty() ? 1 + random.nextInt(6) : missing.get(random.nextInt(missing.size()))));
			// S12: a full Lumbung spills into the Peti Galian (online or not); nothing is lost while the owner is away.
			int worst = dev.kultivator.sect.build.flow.LumbungOverflow.LUMBUNG;
			Component example = null;
			for (ItemStack stack : loot) {
				Component name = stack.getHoverName();
				int where = dev.kultivator.sect.build.flow.LumbungOverflow.store(owner, stack);
				if (where > worst) {
					worst = where;
					example = name;
				}
			}
			dev.kultivator.sect.build.flow.LumbungOverflow.tell(player, owner, worst, example);
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.ark.back", AwayTeams.name(member), (int) (SHARE * 100.0D))
					.withStyle(ChatFormatting.GOLD));
			if (player != null) {
				Advancements.grant(player, "primordial_ark");
			}
		}
	}

	private PrimordialArk() {
	}
}
