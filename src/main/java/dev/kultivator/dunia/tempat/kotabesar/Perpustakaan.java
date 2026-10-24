package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.cultivation.Enlightenment;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The mortal lecture of the Great Library (DESIGN 38.B.4.4): every day 9000-10000 the Mortal Lecturer
 * ({@code PENCERAMAH_FANA} of the {@code kuliah} lot) teaches in the Lecture Hall, line by line in his speech bubble. A
 * cultivator up to Nascent Soul who sits through sixty seconds of it within six blocks (crouching or meditating) gains one
 * point of comprehension of his Dao (or a moment of enlightenment without one), once a world day; the pattern of the
 * Immortal City's noon lecture. Reading, copying and the archive are 38EK's {@code Jasa}.
 */
final class Perpustakaan {

	static final long MULAI = 9000L;
	static final long AKHIR = 10000L;
	static final int DETIK = 60;
	static final double JARAK = 6.0D;
	static final int BARIS = 12;

	private static final Map<UUID, Integer> DENGAR = new HashMap<>();

	static void lupakan() {
		DENGAR.clear();
	}

	/** Every 20 ticks. */
	static void tick(MinecraftServer server, long tick) {
		long t = Kota.timeOfDay(server);
		if (t < MULAI || t >= AKHIR || !KotaBesarConfig.hidup()) {
			if (!DENGAR.isEmpty()) {
				DENGAR.clear();
			}
			return;
		}
		long day = Kota.day(server);
		for (KotaBesarAnchors.KotaHidup kota : KotaBesarAnchors.semua()) {
			ServerLevel level = server.getLevel(kota.dimensi);
			DuniaAnchorEntity hall = kota.layanan("kuliah");
			KultivatorNpc lecturer = hall == null ? null : KotaBesarAnchors.tubuhDari(hall);
			if (level == null || lecturer == null) {
				continue;
			}
			if (tick % 400L == 0L) {
				Voice.say(lecturer, "kotabesar.library_hush", null);
			}
			if (tick % 120L == 0L) {
				HidupApi.klip(lecturer, GerakId.MENGAJAR, 0, 110);
				lecturer.say(Component.translatable("kultivator.kota_besar.kuliah.ajar." + (int) Math.floorMod(tick / 120L, BARIS)), 115);
			}
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(lecturer) > JARAK * JARAK || !(p.isShiftKeyDown() || Meditation.isMeditating(p))) {
					continue;
				}
				CultivationData data = Cultivators.get(p);
				if (data.realm().ordinal() > Realm.NASCENT_SOUL.ordinal()) {
					continue;
				}
				CompoundTag mine = KotaBesarData.get(server).pemain(p.getUUID());
				if (mine.getLongOr("kuliah", Long.MIN_VALUE) == day) {
					continue;
				}
				int seconds = DENGAR.merge(p.getUUID(), 1, Integer::sum);
				if (seconds % 15 == 0) {
					Msg.actionBar(p, Component.translatable("kultivator.kota_besar.kuliah.dengar", seconds, DETIK).withStyle(ChatFormatting.AQUA));
				}
				if (seconds >= DETIK) {
					DENGAR.remove(p.getUUID());
					mine.putLong("kuliah", day);
					KotaBesarData.get(server).setDirty();
					Dao dao = data.dao();
					if (dao == null) {
						Enlightenment.trigger(p);
						Msg.chat(p, Component.translatable("kultivator.kota_besar.kuliah.pencerahan").withStyle(ChatFormatting.GOLD));
					} else {
						double gained = Comprehension.grant(p, dao, 1.0D);
						Msg.chat(p, Component.translatable("kultivator.kota_besar.kuliah.paham", dao.displayName(),
								String.format(java.util.Locale.ROOT, "%.1f", gained)).withStyle(ChatFormatting.GOLD));
					}
				}
			}
		}
	}

	private Perpustakaan() {
	}
}
