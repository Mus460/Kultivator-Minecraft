package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import dev.kultivator.sosok.GerakId;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Speech-bubble lines of 38 NPCs (DESIGN 38.S.6, fragment {@code tempat_kalimat}): greetings by time of day, shop calls,
 * weather complaints, fear, awe at a high realm, festival talk and whispers about wanted people, per schedule group
 * ({@link NpcRole.Kelompok}). Keys {@code kultivator.cerita.kalimat.<grup>.<situasi>.<n>}; the number of lines per
 * {@code <grup>.<situasi>} is in {@link KalimatIndeks} (written by {@code tools/gen_tempat_cerita.py} together with the
 * lang fragment, so the two never drift). A missing pair falls back to group {@code umum}.
 */
public final class Kalimat {

	/** The situation of a line for {@code player} meeting {@code npc} now. */
	public static String situasi(ServerPlayer player, KultivatorNpc npc) {
		long town = npc.town();
		int gap = Cultivators.get(player).realm().ordinal() - npc.realm();
		if (town != 0L && PerbaikanApi.takut(town) >= 40 || gap >= 4) {
			return "takut";
		}
		if (town != 0L && KotaBesarApi.buronan(player, town)) {
			return "buronan";
		}
		if (town != 0L && KotaBesarApi.acaraAktif(town) != null) {
			return "festival";
		}
		if (npc.level().isRainingAt(npc.blockPosition().above())) {
			return "hujan";
		}
		if (gap >= 2 && npc.getRandom().nextInt(3) == 0) {
			return "kagum";
		}
		if (npc.role().kelompok == NpcRole.Kelompok.PEDAGANG && npc.getRandom().nextInt(5) < 2) {
			return "dagang";
		}
		if (npc.getRandom().nextInt(6) == 0) {
			return "keluhan";
		}
		int hour = (int) ((Kota.timeOfDay(player.level().getServer()) / 1000L + 6L) % 24L);
		return hour >= 5 && hour < 11 ? "pagi" : hour >= 11 && hour < 17 ? "siang" : "malam";
	}

	/** The lang key of a line for this group and situation, falling back to group {@code umum}; null when none. */
	public static @Nullable String kunci(String grup, String situasi, int roll) {
		Integer n = KalimatIndeks.JUMLAH.get(grup + "." + situasi);
		String g = grup;
		if (n == null || n <= 0) {
			n = KalimatIndeks.JUMLAH.get("umum." + situasi);
			g = "umum";
		}
		if (n == null || n <= 0) {
			return null;
		}
		return "kultivator.cerita.kalimat." + g + "." + situasi + "." + Math.floorMod(roll, n);
	}

	/** The group of an NPC: its schedule group, {@code anak} for children. */
	public static String grup(KultivatorNpc npc) {
		return npc.role().anak() ? "anak" : npc.role().kelompok.id();
	}

	/** A greeting line for the greeting sheet and speech bubbles (C38.NL chatter may use it too). */
	public static Component sapa(ServerPlayer player, KultivatorNpc npc) {
		if (dev.kultivator.dunia.kota42.cakap.Cakap.menangani(npc)) {
			return dev.kultivator.dunia.kota42.cakap.Cakap.sapa(player, npc);    // 42K (42.5A.11)
		}
		String key = kunci(grup(npc), situasi(player, npc), npc.getRandom().nextInt(1 << 16));
		return key == null ? Component.translatable("kultivator.cerita.sapa.umum") : Component.translatable(key);
	}

	/** The gesture that goes with a greeting (clip id of {@link GerakId}). */
	public static int gestur(KultivatorNpc npc) {
		NpcRole role = npc.role();
		if (role.anak()) {
			return GerakId.LAMBAI;
		}
		return switch (role.kelompok) {
			case PEDAGANG -> GerakId.SERU_DAGANG;
			case PENJAGA -> GerakId.HORMAT;
			case TETUA, RAHIB, CENDEKIA -> GerakId.HORMAT_DALAM;
			case PEJABAT -> GerakId.KIPAS;
			default -> GerakId.LAMBAI;
		};
	}

	private Kalimat() {
	}
}
