package dev.kultivator.sekte.diplomasi;

import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.AksiSekte;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.Sikap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * The standing rules of DESIGN 36.2.9: the starting standing by alignment (Lurus +10, Netral 0, Iblis −40, +10 for a hall
 * whose founder became the Leluhur Pendiri), every change with its reason in the chronicle and the hall log when the
 * stance crosses a line, the slow daily drift back toward the start, and the alliance that breaks when trust falls below
 * 40. Numbers: gifts +5..+15, envoys +5, a refused challenge −5, an inter-sect tournament ±10, a mission meeting ±5, a raid
 * −20.
 */
public final class Standing {

	public static final int AWAL_LURUS = 10;
	public static final int AWAL_NETRAL = 0;
	public static final int AWAL_IBLIS = -40;
	public static final int LELUHUR = 10;
	/** Below this an alliance breaks. */
	public static final int SEKUTU_PUTUS = 40;
	public static final int PAKTA_DAMAI_MIN = 20;
	public static final int PAKTA_SEKUTU_MIN = 60;
	public static final int PERANG_MAX = -30;

	static int awal(SekteNpc sect) {
		return switch (sect.aliran) {
			case SekteNpc.LURUS -> AWAL_LURUS;
			case SekteNpc.IBLIS -> AWAL_IBLIS;
			default -> AWAL_NETRAL;
		};
	}

	/**
	 * Changes the standing of {@code owner} with {@code sect} by {@code delta} for {@code reasonKey} (a lang key under
	 * {@code kultivator.sekte.diplomasi.alasan.}). Writes the chronicle; a stance change goes to the hall log too. An
	 * alliance below {@link #SEKUTU_PUTUS} breaks.
	 */
	static void adjust(MinecraftServer server, UUID owner, SekteNpc sect, int delta, String reasonKey) {
		if (delta == 0) {
			return;
		}
		DiplomasiData data = DiplomasiData.get(server);
		Relasi r = data.relasi(owner, sect);
		Sikap before = r.sikap();
		r.add(delta);
		long day = Diplomasi.today(server);
		r.note(day, Component.translatable(GeneratorNama.KEY + "kronik.standing",
				Component.translatable(GeneratorNama.KEY + "alasan." + reasonKey), (delta > 0 ? "+" : "") + delta, r.standing));
		if (r.pakta == Relasi.PAKTA_SEKUTU && r.standing < SEKUTU_PUTUS) {
			r.pakta = Relasi.PAKTA_DAMAI;
			r.note(day, Component.translatable(GeneratorNama.KEY + "kronik.sekutu_putus"));
			SekteLog.log(server, owner, SekteIds.D, Component.translatable(GeneratorNama.KEY + "log.sekutu_putus", sect.name())
					.withStyle(ChatFormatting.RED));
			SekteBus.rivalAction(server, owner, sect.id, AksiSekte.PUTUS, sect.tier);
		}
		Sikap now = r.sikap();
		if (now != before) {
			SekteLog.log(server, owner, SekteIds.D, Component.translatable(GeneratorNama.KEY + "log.sikap", sect.name(),
					Component.translatable(before.key()), Component.translatable(now.key())));
		}
		data.setDirty();
	}

	/** Once a day: one point back toward the start every third day (never during a war or under a pact's floor). */
	static void drift(SekteNpc sect, Relasi r, long day) {
		if (r.perang || Math.floorMod(day + sect.index, 3) != 0) {
			return;
		}
		int target = awal(sect);
		if (r.pakta != Relasi.PAKTA_TIDAK) {
			target = Math.max(target, r.pakta == Relasi.PAKTA_SEKUTU ? PAKTA_SEKUTU_MIN : PAKTA_DAMAI_MIN);
		}
		if (r.bawahan) {
			target = Math.max(target, 30);
		}
		if (r.standing > target) {
			r.add(-1);
		} else if (r.standing < target) {
			r.add(1);
		}
	}

	private Standing() {
	}
}
