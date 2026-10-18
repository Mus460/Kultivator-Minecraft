package dev.kultivator.sect.hall;

import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Seni Sekte yang Hilang (DESIGN 20.3.8): five different Lembar Kitab yang Hilang of one set restore a lost art for the
 * whole hall (with a Paviliun Kitab built). Module C applies the arts to disciples of rank Dalam and above; this class
 * only restores them ({@code core.arts}: bit 1 Plum Blossom, 2 Purple Mist, 4 Northern Heaven).
 * <p>
 * Sources: <i>Return of the Mount Hua Sect</i> (the Twenty-Four Plum Blossom Sword and the Purple Mist Divine Art),
 * <i>Legend of the Northern Blade</i> (the Northern Heavenly Sect's arts from fragmentary writings), <i>Volcanic Age</i>
 * (techniques from hidden records).
 */
final class LostArts {

	/**
	 * Page sets of {@code kultivator:scripture_page}: the three hall arts, plus set 4 "Kitab Penyegel Iblis" (DESIGN 22.8.10,
	 * module P6c-seni-A), whose pages teach techniques with an SCR route ({@code technique.source.TechniqueLoot}) and are never
	 * restored here.
	 */
	public static final int SETS = 4;
	/** The sets the hall restores as lost arts (1-3). */
	public static final int RESTORABLE = 3;
	/** Set 4, Kitab Penyegel Iblis (Sealing Demon Codex): read for SCR techniques, never restored. */
	public static final int SEALING = 4;
	public static final int PIECES = 5;
	public static final int PLUM = 1;
	public static final int PURPLE_MIST = 2;
	public static final int NORTHERN_HEAVEN = 4;
	/** Renown for a restored art. */
	public static final int RENOWN = 500;
	private static final int[] COLOUR = {0xFF9EC8, 0xB88CFF, 0xBFE4FF, 0xC24B4B};

	static int bit(int set) {
		return 1 << (Math.max(1, Math.min(RESTORABLE, set)) - 1);
	}

	static MutableComponent setName(int set) {
		if (set == SEALING) {
			return Component.translatable("kultivator.sumber.scripture.set");
		}
		return Component.translatable("kultivator.hall.art." + Math.max(1, Math.min(RESTORABLE, set)));
	}

	/** Which pieces (bits 0-4) of {@code set} the player carries. */
	static int piecesCarried(ServerPlayer player, int set) {
		int bits = 0;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			HallItems.ScripturePage page = stack.is(HallItems.LOST_SCRIPTURE_PAGE) ? stack.get(HallItems.SCRIPTURE_PAGE) : null;
			if (page != null && page.set() == set) {
				bits |= 1 << (page.piece() - 1);
			}
		}
		return bits;
	}

	/** Pulihkan Seni: consumes one page of each piece, restores the art, +500 renown. */
	static void restore(ServerPlayer player, UUID owner, CompoundTag core, int set) {
		if (set < 1 || set > RESTORABLE) {
			// Set 4 (Kitab Penyegel Iblis) is read for techniques, never restored as a hall art.
			return;
		}
		int arts = core.getIntOr("arts", 0);
		if ((arts & bit(set)) != 0) {
			Msg.chat(player, Component.translatable("kultivator.hall.art.already", setName(set)).withStyle(ChatFormatting.GRAY));
			return;
		}
		if ((core.getIntOr("bflags", 0) & HallLevel.B_PAVILION) == 0) {
			Msg.chat(player, Component.translatable("kultivator.hall.art.need_pavilion").withStyle(ChatFormatting.YELLOW));
			return;
		}
		int carried = piecesCarried(player, set);
		if (carried != (1 << PIECES) - 1) {
			Msg.chat(player, Component.translatable("kultivator.hall.art.need_pieces", setName(set), Integer.bitCount(carried), PIECES)
					.withStyle(ChatFormatting.YELLOW));
			return;
		}
		Inventory inventory = player.getInventory();
		int taken = 0;
		for (int slot = 0; slot < inventory.getContainerSize() && taken != (1 << PIECES) - 1; slot++) {
			ItemStack stack = inventory.getItem(slot);
			HallItems.ScripturePage page = stack.is(HallItems.LOST_SCRIPTURE_PAGE) ? stack.get(HallItems.SCRIPTURE_PAGE) : null;
			if (page != null && page.set() == set && (taken & 1 << (page.piece() - 1)) == 0) {
				taken |= 1 << (page.piece() - 1);
				stack.shrink(1);
			}
		}
		inventory.setChanged();
		core.putInt("arts", arts | bit(set));
		Halls.setDirty();
		Halls.addRenown(owner, RENOWN, "kultivator.hall.renown.art");
		Halls.log(owner, Component.translatable("kultivator.hall.log.art", setName(set)));
		Msg.chat(player, Component.translatable("kultivator.hall.art.restored", setName(set)).withStyle(ChatFormatting.GOLD));
		Msg.chat(player, Component.translatable("kultivator.hall.art.effect." + set).withStyle(ChatFormatting.GRAY));
		Advancements.grant(player, "lost_art_restored");
		BlockPos stele = Halls.posOf(core);
		ServerLevel level = Halls.levelOf(core);
		if (stele != null && level != null) {
			Vec3 top = Vec3.atBottomCenterOf(stele).add(0.0D, 1.0D, 0.0D);
			Fx.send(level, top, FxType.HALL, HallFxIds.RESTORE, set, 4.0F);
			Fx.pillar(level, top, COLOUR[set - 1], 6.0F);
			level.playSound(null, top.x, top.y, top.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
			level.playSound(null, top.x, top.y, top.z, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 1.2F);
		}
	}

	private LostArts() {
	}
}
