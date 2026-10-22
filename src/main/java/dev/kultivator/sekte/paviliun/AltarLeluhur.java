package dev.kultivator.sekte.paviliun;

import dev.kultivator.epic.EpicIds;
import dev.kultivator.epic.EpicMoments;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Altar Leluhur, the ancestral altar (DESIGN 36.2.5): the spirit tablets of the enshrined dead (the master pays
 * {@code abadikanPK} Contribution Points per tablet with "Abadikan") stand on its offering table, drawn by the client
 * in faded gold; on templates 8 and 9 the memorial stele of the cemetery serves (their ancestral hall has no anchor).
 * Its blessing, read by module P (contentment) and module E (the heir ritual):
 * <pre>berkah = min(0.10, 0.01 x tablets) + 0.05 x freshness,   freshness = 1 after any rite, 1/7 less each day</pre>
 * so 0-0.15. Rites: the daily incense ({@link Upacara#incense}), Qingming and funerals.
 * <p>
 * Sumber: papan arwah leluhur di aula leluhur keluarga (adat Tiongkok).
 */
final class AltarLeluhur {

	static final int BEAT_ENSHRINE = EpicIds.EXTRA_SEKTE_FIRST + 42;
	static final double TABLET_STEP = 0.01D;
	static final double TABLET_MAX = 0.10D;
	static final double FRESH_MAX = 0.05D;
	static final int FRESH_DAYS = 7;

	/** The blessing 0-0.15 (C36.A3). */
	static double blessing(UUID owner) {
		if (!Paviliun.active() || Halls.hallOf(owner) == null) {
			return 0.0D;
		}
		double tablets = Math.min(TABLET_MAX, TABLET_STEP * Silsilah.tablets(owner));
		return Math.max(0.0D, Math.min(TABLET_MAX + FRESH_MAX, tablets + FRESH_MAX * freshness(owner)));
	}

	/** 1 the day of a rite, falling by 1/7 a day to 0. */
	static double freshness(UUID owner) {
		long last = PaviliunData.getLong(owner, PaviliunData.RITE_DAY, -1L);
		if (last < 0L) {
			return 0.0D;
		}
		long age = Math.max(0L, Ingatan.today(Paviliun.server()) - last);
		return Math.max(0.0D, 1.0D - age / (double) FRESH_DAYS);
	}

	/** Marks today as a rite day (freshness back to 1). */
	static void rite(MinecraftServer server, UUID owner) {
		PaviliunData.putLong(owner, PaviliunData.RITE_DAY, Ingatan.today(server));
		PaviliunSync.dirty(owner);
	}

	/** "Abadikan": the master enshrines {@code soul}'s tablet for {@code abadikanPK}. False (and told why) when refused. */
	static boolean enshrine(ServerPlayer master, UUID soul) {
		UUID owner = master.getUUID();
		MinecraftServer server = master.level().getServer();
		PaviliunData.Grave grave = Silsilah.find(owner, soul);
		if (grave == null) {
			return false;
		}
		if (grave.enshrined()) {
			Msg.actionBar(master, Component.translatable("kultivator.sekte.paviliun.altar.already", grave.name()).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		int cost = Paviliun.config().abadikanPK;
		if (cost > 0 && !Halls.spendPool(owner, cost)) {
			Msg.chat(master, Component.translatable("kultivator.sekte.paviliun.altar.no_pk", cost).withStyle(ChatFormatting.RED));
			return false;
		}
		Silsilah.enshrine(owner, soul);
		PaviliunSync.dirty(owner);
		SekteLog.log(server, owner, SekteIds.A, Component.translatable("kultivator.sekte.paviliun.log.enshrined", grave.name(), cost));
		Msg.chat(master, Component.translatable("kultivator.sekte.paviliun.altar.enshrined", grave.name(), cost,
				Math.round(blessing(owner) * 100.0D)).withColor(0xFFE2A0));
		BlockPos table = PaviliunSync.tablePos(owner);
		if (table != null && master.level() instanceof ServerLevel level && level.isLoaded(table)) {
			Vec3 at = Vec3.atBottomCenterOf(table);
			Fx.pillar(level, at, 0xFFD27A, 3.0F);
			Fx.ring(level, at, 0xFFE2A0, 2.0F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 0.9F, 1.1F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 0.8F);
			if (Paviliun.config().adegan && master.distanceToSqr(at) <= 48.0D * 48.0D) {
				EpicMoments.send(level, EpicIds.EXTRA, BEAT_ENSHRINE, null, master, at.add(0.0D, 1.0D, 0.0D), level.getGameTime(),
						Silsilah.tablets(owner), 0, 0.0F, 2, 0);
			}
		}
		return true;
	}

	private AltarLeluhur() {
	}
}
