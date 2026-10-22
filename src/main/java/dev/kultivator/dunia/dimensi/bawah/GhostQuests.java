package dev.kultivator.dunia.dimensi.bawah;

import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.dimensi.DimensiData;
import dev.kultivator.dunia.dimensi.DimensiItems;
import dev.kultivator.dunia.dimensi.DimensiNet;
import dev.kultivator.dunia.dimensi.DimensiNpc;
import dev.kultivator.util.Msg;
import dev.kultivator.dunia.dimensi.DimensiVoice;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * The errands of Desa Arwah's ghosts (DESIGN 34.5.13).
 * <ul>
 * <li><b>Pesan untuk yang Hidup</b>: a ghost gives a Surat Arwah addressed to a town or village of the living (a known
 * {@code kota/*} or {@code desa/*} site when the player knows one, else a place 300-700 b from the world spawn); the
 * letter carries a {@code lodestone_tracker} to it. Delivered within 16 b of the address in the overworld, it pays soul
 * jade and spirit stones. One letter at a time per player.</li>
 * <li><b>Arwah Tersesat</b>: a lost soul follows the player to the Naihe bridge, where it crosses ({@link Courts}).</li>
 * </ul>
 * Rewards are fixed per errand (no shop, no loop that makes stones from nothing; one letter at a time).
 */
public final class GhostQuests {

	public static final double DELIVER_RANGE = 16.0D;
	/** Errands a day that also pay spirit stones (soul jade is always given): no endless stone loop. */
	public static final int PAID_ERRANDS_PER_DAY = 3;

	static void talk(ServerPlayer player, ArwahEntity ghost) {
		if (ghost.following() != null) {
			if (player.getUUID().equals(ghost.following())) {
				Msg.actionBar(player, Component.translatable("kultivator.dimensi.arwah.ikut").withColor(0xC9B8FF));
			}
			return;
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("peran", ghost.note().isEmpty() ? "pesan" : ghost.note());
		tag.putBoolean("letter", !SoulJourney.data(player).getCompoundOrEmpty("surat").isEmpty());
		tag.putInt("look", ghost.look());
		DimensiNet.open(player, DimensiNet.SCREEN_GHOST, ghost.getId(), tag);
		DimensiVoice.say(ghost, DimensiVoice.GHOST_PLEA, "disciple.f2.careful", "kultivator.dimensi.voice.ghost_plea");
	}

	/** {@code ghost:pesan} or {@code ghost:antar}. */
	public static void action(ServerPlayer player, ArwahEntity ghost, String what) {
		if ("pesan".equals(what)) {
			giveLetter(player, ghost);
		} else if ("antar".equals(what) && ghost.following() == null) {
			ghost.setFollow(player.getUUID());
			ghost.setNote("tersesat");
			ghost.setFlag(DimensiNpc.FLAG_QUEST, true);
			Msg.chat(player, Component.translatable("kultivator.dimensi.arwah.antar").withColor(0xC9B8FF));
		}
	}

	private static void giveLetter(ServerPlayer player, ArwahEntity ghost) {
		CompoundTag bawah = SoulJourney.data(player);
		if (!bawah.getCompoundOrEmpty("surat").isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.dimensi.surat.sudah").withStyle(ChatFormatting.GRAY));
			return;
		}
		MinecraftServer server = player.level().getServer();
		BlockPos target = address(player, server);
		CompoundTag surat = new CompoundTag();
		surat.putInt("x", target.getX());
		surat.putInt("z", target.getZ());
		surat.putInt("ghost", ghost.look());
		bawah.put("surat", surat);
		DimensiData.dirty(player);
		ItemStack letter = new ItemStack(DimensiItems.SURAT_ARWAH);
		letter.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(Level.OVERWORLD, target)), false));
		DimensiData.give(player, letter);
		Msg.chat(player, Component.translatable("kultivator.dimensi.surat.terima", target.getX(), target.getZ()).withColor(0xC9B8FF));
	}

	/** The letter's address: the nearest known town or village of the living, else a hamlet near the world spawn. */
	private static BlockPos address(ServerPlayer player, MinecraftServer server) {
		CompoundTag ret = SoulJourney.data(player).getCompoundOrEmpty("ret");
		Vec3 from = new Vec3(ret.getDoubleOr("x", 0.0D), 64.0D, ret.getDoubleOr("z", 0.0D));
		DuniaSites.Site best = null;
		double bestSq = Double.MAX_VALUE;
		for (DuniaSites.Site site : DuniaSites.known(player)) {
			if (!Level.OVERWORLD.equals(site.dim()) || !(site.kind().startsWith("kota/") || site.kind().startsWith("desa/"))) {
				continue;
			}
			double d = site.pos().distToCenterSqr(from);
			if (d < bestSq) {
				bestSq = d;
				best = site;
			}
		}
		if (best != null) {
			return best.pos();
		}
		BlockPos spawn = server.overworld().getRespawnData().pos();
		double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
		double r = 300.0D + player.getRandom().nextDouble() * 400.0D;
		return new BlockPos(Mth.floor(spawn.getX() + Math.cos(angle) * r), 64, Mth.floor(spawn.getZ() + Math.sin(angle) * r));
	}

	/** Right-clicking the letter tells the way. */
	public static void letterHint(ServerPlayer player, ItemStack letter) {
		CompoundTag surat = SoulJourney.data(player).getCompoundOrEmpty("surat");
		if (surat.isEmpty()) {
			Msg.actionBar(player, Component.translatable("kultivator.dimensi.surat.usang").withStyle(ChatFormatting.GRAY));
			return;
		}
		int x = surat.getIntOr("x", 0);
		int z = surat.getIntOr("z", 0);
		if (player.level().dimension() != Level.OVERWORLD) {
			Msg.actionBar(player, Component.translatable("kultivator.dimensi.surat.dunia_hidup", x, z).withColor(0xC9B8FF));
			return;
		}
		double dx = x + 0.5D - player.getX();
		double dz = z + 0.5D - player.getZ();
		int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		Msg.actionBar(player, Component.translatable("kultivator.dimensi.surat.arah", Component.translatable(compass(dx, dz)), distance)
				.withColor(0xC9B8FF));
	}

	/** The lang key of the compass direction of {@code (dx, dz)} ({@code kultivator.dimensi.arah.<0-7>}). */
	public static String compass(double dx, double dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		int sector = Math.floorMod((int) Math.round(angle / 45.0D), 8);
		return "kultivator.dimensi.arah." + sector;
	}

	/** Every 2 s for overworld players carrying a letter: delivered within 16 b of its address. */
	static void deliveryTick(ServerPlayer player) {
		if (player.level().dimension() != Level.OVERWORLD || !DimensiData.carries(player, DimensiItems.SURAT_ARWAH)) {
			return;
		}
		CompoundTag surat = SoulJourney.data(player).getCompoundOrEmpty("surat");
		if (surat.isEmpty()) {
			return;
		}
		double dx = surat.getIntOr("x", 0) + 0.5D - player.getX();
		double dz = surat.getIntOr("z", 0) + 0.5D - player.getZ();
		if (dx * dx + dz * dz > DELIVER_RANGE * DELIVER_RANGE) {
			return;
		}
		if (!DimensiData.takeOne(player, DimensiItems.SURAT_ARWAH)) {
			return;
		}
		SoulJourney.data(player).remove("surat");
		DimensiData.dirty(player);
		int realm = Cultivators.get(player).realm().ordinal();
		DimensiData.give(player, new ItemStack(DimensiItems.GIOK_ARWAH, 2 + Math.min(3, Math.max(0, realm - 4))));
		if (paidErrand(player)) {
			DimensiData.giveNb(player, 9L * Math.max(1, realm - 2));
		}
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.9F);
		DimensiNet.fx(level, DimensiNet.FX_SOUL_RETURN, player.position(), 9, 32.0D);
		Msg.chat(player, Component.translatable("kultivator.dimensi.surat.sampai").withColor(0xE8D9A8));
		Advancements.grant(player, "dunia/letter_delivered");
	}

	/** A lost soul crossed the Naihe bridge with its escort. */
	static void lostSoulHome(ServerPlayer player) {
		DimensiData.give(player, new ItemStack(DimensiItems.GIOK_ARWAH, 3));
		if (paidErrand(player)) {
			DimensiData.giveNb(player, 18L);
		}
		Msg.chat(player, Component.translatable("kultivator.dimensi.arwah.sampai").withColor(0xE8D9A8));
		Advancements.grant(player, "dunia/lost_soul_home");
	}

	/** Counts an errand of today; true while today's paid errands are not used up. */
	private static boolean paidErrand(ServerPlayer player) {
		CompoundTag bawah = SoulJourney.data(player);
		long day = DimensiData.day(player.level().getServer());
		int count = bawah.getLongOr("errand_day", -1L) == day ? bawah.getIntOr("errands", 0) : 0;
		bawah.putLong("errand_day", day);
		bawah.putInt("errands", count + 1);
		DimensiData.dirty(player);
		return count < PAID_ERRANDS_PER_DAY;
	}

	private GhostQuests() {
	}
}
