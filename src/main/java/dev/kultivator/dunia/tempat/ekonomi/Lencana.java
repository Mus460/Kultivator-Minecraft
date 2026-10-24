package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Lencana Bintang 1-9 of the Gilda Kultivator (DESIGN 38.M.3): guild points 10 / 30 / 60 / 100 / 160 / 240 / 340 / 460 /
 * 600 for the stars; star 3, 6 and 9 also need the guild exam (a Sword Duel against an examiner of the same realm,
 * {@link GildaKultivator}), so points beyond an exam wait for it. Perks: 3 → 5 % off at guild shops, 5 → one more mission
 * a day, 7 → S missions and the Guild Store, 8 → one more mission (at most 5), 9 → the title "Bintang Sembilan" and one
 * free teleport a day (read by 38KS through {@link #bintang}).
 */
public final class Lencana {

	public static final int[] POIN = {10, 30, 60, 100, 160, 240, 340, 460, 600};
	public static final int[] UJIAN = {3, 6, 9};
	public static final int DISKON_BINTANG = 3;
	public static final double DISKON = 0.05D;
	public static final int MISI_DASAR = 3;
	public static final int MISI_MAKS = 5;
	public static final int BINTANG_S = 7;
	public static final int BINTANG_GUDANG = 7;
	public static final int BINTANG_TELEPORT = 9;

	private static final String POIN_KEY = "gilda_poin";
	private static final String LULUS_KEY = "gilda_lulus";

	static int poin(MinecraftServer server, java.util.UUID player) {
		return Math.max(0, EkonomiData.get(server).pemain(player).getIntOr(POIN_KEY, 0));
	}

	public static int poin(ServerPlayer player) {
		return poin(player.level().getServer(), player.getUUID());
	}

	/** Highest exam star passed (0, 3, 6 or 9). */
	static int lulus(ServerPlayer player) {
		return EkonomiData.get(player.level().getServer()).pemain(player.getUUID()).getIntOr(LULUS_KEY, 0);
	}

	/** Stars 0-9 (points, held at an exam not yet passed). */
	public static int bintang(ServerPlayer player) {
		return bintangDari(poin(player), lulus(player));
	}

	/**
	 * Stars 0-9 of {@code points} when the highest exam passed is {@code passed} (0, 3, 6 or 9): the stars stop one below
	 * an exam not yet passed. Pure, so the economy simulation of 38.M.6 uses the same rule.
	 */
	public static int bintangDari(int points, int passed) {
		int stars = 0;
		for (int i = 0; i < POIN.length; i++) {
			if (points >= POIN[i]) {
				stars = i + 1;
			}
		}
		for (int exam : UJIAN) {
			if (stars >= exam && passed < exam) {
				return exam - 1;
			}
		}
		return stars;
	}

	/** The next exam star the player has to pass (3, 6 or 9), or 0 when none is waiting yet. */
	static int ujianBerikut(ServerPlayer player) {
		int passed = lulus(player);
		for (int exam : UJIAN) {
			if (exam > passed) {
				return exam;
			}
		}
		return 0;
	}

	/** Points needed for the next star (the top for 9 stars). */
	static int poinBerikut(ServerPlayer player) {
		int stars = bintang(player);
		return stars >= POIN.length ? POIN[POIN.length - 1] : POIN[stars];
	}

	/** Missions a day by stars: 3, +1 at 5, +1 at 8 (at most 5). */
	public static int misiPerHari(ServerPlayer player) {
		return misiPerHariBintang(bintang(player));
	}

	/** Missions a day at {@code stars} (pure form of {@link #misiPerHari}). */
	public static int misiPerHariBintang(int stars) {
		return Math.min(MISI_MAKS, MISI_DASAR + (stars >= 5 ? 1 : 0) + (stars >= 8 ? 1 : 0));
	}

	/** Adds guild points (missions of the guild and of the town boards). */
	public static void tambah(ServerPlayer player, int n) {
		if (n <= 0) {
			return;
		}
		int before = bintang(player);
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		CompoundTag tag = data.pemain(player.getUUID());
		tag.putInt(POIN_KEY, Math.max(0, tag.getIntOr(POIN_KEY, 0)) + n);
		data.touch();
		DaftarNagaMuda.tambahSkor(player, n);
		umumkan(player, before);
	}

	/** Marks exam {@code star} passed. */
	static void lulusUjian(ServerPlayer player, int star) {
		int before = bintang(player);
		EkonomiData data = EkonomiData.get(player.level().getServer());
		CompoundTag tag = data.pemain(player.getUUID());
		tag.putInt(LULUS_KEY, Math.max(tag.getIntOr(LULUS_KEY, 0), star));
		data.touch();
		Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.lencana.lulus", star).withStyle(ChatFormatting.GOLD));
		umumkan(player, before);
	}

	private static void umumkan(ServerPlayer player, int before) {
		int after = bintang(player);
		if (after > before) {
			Msg.title(player, Component.translatable("kultivator.tempat_ekonomi.lencana.naik", after), Component.translatable(
					"kultivator.tempat_ekonomi.lencana.naik." + after), 10, 50, 20);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.2F);
			if (after >= 9) {
				Advancements.grant(player, "tempat/lencana_9");
			}
		} else if (after == before && after > 0 && after < POIN.length && poin(player) >= POIN[after] && ujianBerikut(player) == after + 1) {
			Msg.actionBar(player, Component.translatable("kultivator.tempat_ekonomi.lencana.tunggu_ujian", after + 1).withStyle(ChatFormatting.YELLOW));
		}
	}

	/** {@code uji ekonomi lencana <1-9>}: sets points and exams to reach {@code star}. */
	public static void set(ServerPlayer player, int star) {
		int s = Math.max(0, Math.min(9, star));
		EkonomiData data = EkonomiData.get(player.level().getServer());
		CompoundTag tag = data.pemain(player.getUUID());
		tag.putInt(POIN_KEY, s == 0 ? 0 : POIN[s - 1]);
		int passed = 0;
		for (int exam : UJIAN) {
			if (s >= exam) {
				passed = exam;
			}
		}
		tag.putInt(LULUS_KEY, passed);
		data.touch();
	}

	private Lencana() {
	}
}
