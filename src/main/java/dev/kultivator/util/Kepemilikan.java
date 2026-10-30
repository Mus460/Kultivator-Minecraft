package dev.kultivator.util;

import dev.kultivator.sect.Sect;
import dev.kultivator.sect.SectData;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The one ownership guard of player-owned blocks: a block registered with {@link #jaga} breaks only for its owner, a mate of the
 * owner's sect, an op or a creative player. One {@code PlayerBlockBreakEvents.BEFORE} listener (added with the first
 * registration) looks the block up in a map, so every other block pays one hash lookup. A resolver answers the owner of the block
 * at a position (from its block entity or from a module's own record) or null when it belongs to nobody (a free block breaks).
 * Pistons, explosions and the world breakers are not player breaks and are not covered here.
 */
public final class Kepemilikan {

	/** Finds the owner of the guarded block at {@code pos}; null when nobody owns it. */
	@FunctionalInterface
	public interface Pemilik {
		@Nullable UUID cari(ServerLevel level, BlockPos pos, @Nullable BlockEntity entity);
	}

	private record Aturan(Pemilik pemilik, String kunciPesan) {
	}

	/** Block registrations of this session's modules (set at mod init; never world state). */
	private static final Map<Block, Aturan> ATURAN = new HashMap<>();
	private static boolean terpasang;

	/** Guards every block of type {@code block}; {@code kunciPesan} is the refusal text (null = the common one). */
	public static synchronized void jaga(Block block, Pemilik pemilik, @Nullable String kunciPesan) {
		ATURAN.put(block, new Aturan(pemilik, kunciPesan == null ? "kultivator.kepemilikan.bukan_milikmu" : kunciPesan));
		if (!terpasang) {
			terpasang = true;
			PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
				Aturan aturan = ATURAN.get(state.getBlock());
				return aturan == null || !(level instanceof ServerLevel server)
						|| bolehPesan(player, aturan.pemilik().cari(server, pos, entity), aturan.kunciPesan());
			});
		}
	}

	/** True when {@code player} may break what {@code owner} owns: no owner, the owner, a sect mate, an op or a creative player. */
	public static boolean boleh(Player player, @Nullable UUID owner) {
		if (owner == null || player.isCreative() || owner.equals(player.getUUID())) {
			return true;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return false;
		}
		if (sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			return true;
		}
		Sect sect = SectData.get(sp.level().getServer()).sectOf(owner);
		return sect != null && sect.isMember(sp.getUUID());
	}

	/** {@link #boleh} that tells the player (action bar, red) when the answer is no. */
	public static boolean bolehPesan(Player player, @Nullable UUID owner, String kunciPesan) {
		if (boleh(player, owner)) {
			return true;
		}
		if (player instanceof ServerPlayer sp) {
			Msg.actionBar(sp, Component.translatable(kunciPesan).withStyle(ChatFormatting.RED));
		}
		return false;
	}

	private Kepemilikan() {
	}
}
