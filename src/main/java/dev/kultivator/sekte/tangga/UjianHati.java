package dev.kultivator.sekte.tangga;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.hall.Candidates;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.VoiceDisciples;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Ujian Hati (DESIGN 36.2.4): a disciple climbs the stair again, alone, to test its heart. Once per
 * {@code ujianHatiHari} days (30) for {@code ujianHatiPK} hall PK (200), only while the stair stands and no climb runs.
 * The new steps and heart verdict replace the old ones in {@code extra.tangga} (the best is kept apart); module P reads
 * {@code SekteApi.tangga().resolve(data)} when this fires its {@code DISCIPLE_HONORED} with reason
 * {@code tangga.ujian_hati} and may trade one bad trait for its opposite on verdict 3 (contract C36.G3). Nothing else
 * about the disciple changes; the climb uses the same formula as the recruitment trial on the disciple's own root and
 * Tekad.
 * <p>
 * Source: Sun Wukong waits and is tested for patience before Subhuti teaches him (<i>Journey to the West</i> ch. 2).
 */
final class UjianHati {

	/** Blocks from the stele a disciple must be within to be called to the stair. */
	static final double CALL_RANGE = 128.0D;

	/**
	 * The master asks disciple {@code name} to climb ({@code free}: the test command, no PK, no waiting, no stair needed).
	 * Returns 1 when the climb started.
	 */
	static int request(ServerPlayer player, String name, boolean free) {
		UUID owner = player.getUUID();
		if (!TanggaConfig.get().aktif && !free) {
			Msg.chat(player, Component.translatable("kultivator.sekte.tangga.off").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		DiscipleEntity disciple = find(owner, name);
		if (disciple == null) {
			Msg.chat(player, Component.translatable("kultivator.sekte.tangga.no_disciple", name).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		if (!free && !Tangga.INSTANCE.hasStair(owner)) {
			Msg.chat(player, Component.translatable("kultivator.sekte.tangga.no_stair").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		if (Pendakian.running(owner)) {
			Msg.chat(player, Component.translatable("kultivator.sekte.tangga.busy").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		BlockPos stele = Candidates.hallStele(owner);
		if (stele == null || disciple.level() != Candidates.hallLevel(owner)
				|| disciple.distanceToSqr(stele.getX() + 0.5D, stele.getY(), stele.getZ() + 0.5D) > CALL_RANGE * CALL_RANGE) {
			Msg.chat(player, Component.translatable("kultivator.sekte.tangga.too_far", disciple.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		if (disciple.isReleasing() || dev.kultivator.disciple.DuelRules.inDuel(disciple)) {
			Msg.chat(player, Component.translatable("kultivator.sekte.tangga.not_now", disciple.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		MinecraftServer server = player.level().getServer();
		long today = Ingatan.today(server);
		CompoundTag mine = disciple.data().extra().getCompound(SekteIds.EXTRA_G).orElse(null);
		int wait = TanggaConfig.get().ujianHatiHari;
		if (!free && mine != null && mine.contains(Tangga.K_RECLIMB_DAY)) {
			long next = mine.getLongOr(Tangga.K_RECLIMB_DAY, 0L) + wait;
			if (today < next) {
				Msg.chat(player, Component.translatable("kultivator.sekte.tangga.wait", disciple.getDisplayName(), next - today)
						.withStyle(ChatFormatting.YELLOW));
				return 0;
			}
		}
		int cost = free ? 0 : TanggaConfig.get().ujianHatiPK;
		if (cost > 0 && !Halls.spendPool(owner, cost)) {
			Msg.chat(player, Component.translatable("kultivator.sekte.tangga.need_pk", cost).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		if (!Pendakian.beginHeart(player, owner, disciple)) {
			if (cost > 0) {
				Halls.addPool(owner, cost);
			}
			Msg.chat(player, Component.translatable("kultivator.sekte.tangga.cannot").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		if (cost > 0) {
			Msg.chat(player, Component.translatable("kultivator.sekte.tangga.paid", cost).withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	/** The re-climb's result (called by {@link Pendakian} when it is read out). */
	static void finished(MinecraftServer server, UUID owner, DiscipleEntity disciple, int steps, int heart, long day) {
		Pendakian.write(disciple.data(), steps, heart, day, true);
		int weight = switch (heart) {
			case 3 -> 5;
			case 2 -> 3;
			case 1 -> -1;
			default -> 1;
		};
		Ingatan.remember(server, disciple.data(), "tangga.ujian_hati", owner, weight, "kultivator.sekte.tangga.mem.ujian_hati", Integer.toString(steps));
		SekteBus.honored(server, owner, disciple.soulId(), "tangga.ujian_hati", heart == 3 ? 6 : 2);
		VoiceDisciples.line(disciple, heart == 3 ? "loyal" : heart == 1 ? "tired" : "thanks", 100);
		SekteLog.log(server, owner, SekteIds.G, Component.translatable("kultivator.sekte.tangga.log_heart", disciple.getName(), steps,
				Component.translatable("kultivator.sekte.tangga.hati." + heart)));
	}

	/** The master's loaded disciple named {@code name} (exact, then the first that contains it, case-insensitive). */
	static @Nullable DiscipleEntity find(UUID owner, String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		String wanted = name.trim().toLowerCase(Locale.ROOT);
		DiscipleEntity partial = null;
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.role() != DiscipleEntity.Role.DISCIPLE) {
				continue;
			}
			String own = disciple.getName().getString().toLowerCase(Locale.ROOT);
			if (own.equals(wanted)) {
				return disciple;
			}
			if (partial == null && own.contains(wanted)) {
				partial = disciple;
			}
		}
		return partial;
	}

	private UjianHati() {
	}
}
