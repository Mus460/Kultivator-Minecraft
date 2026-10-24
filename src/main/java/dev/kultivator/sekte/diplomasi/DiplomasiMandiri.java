package dev.kultivator.sekte.diplomasi;

import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The bridge of module 39F (Perseteruan antar-sekte, DESIGN 39.F.0, 39.F.9) into module D. The feud ladder never copies
 * D's rules: at T6 it changes the standing through D's own {@code Standing.adjust} (same scale, its own reason key
 * {@code mandiri.seteru.<sebab>} under {@code kultivator.sekte.diplomasi.alasan.}, so the Diplomasi screen and chronicle
 * show why), and at T7 the sect master acts through D's own event path {@code Simulasi.apply} (TANTANG, PERANG, DAMAI,
 * UTUSAN), with D's offers, cooldowns and war rules. It also lends 39F the sect body of {@code Tubuh} (role RIVAL, the
 * sect banner) and reads alignment, tier, pacts and gates. No D number is changed here; this is the only 39F file in
 * {@code sekte.diplomasi}. Server thread only.
 */
public final class DiplomasiMandiri {

	/** What T7 did (the feud's history line). */
	public enum Langkah { TANTANG, PERANG, UTUSAN, DAMAI, TIDAK }

	// ================================================================== reads

	/** True when D runs and knows the sect. */
	public static boolean ada(MinecraftServer server, @Nullable String sekte) {
		return server != null && Diplomasi.server() != null && DiplomasiData.get(server).sect(sekte) != null;
	}

	/** The sect master's rank step ({@code SekteNpc.masterRankStep}), 6 when unknown. */
	public static int langkahKetua(MinecraftServer server, String sekte) {
		SekteNpc sect = server == null ? null : DiplomasiData.get(server).sect(sekte);
		return sect == null ? 6 : sect.masterRankStep();
	}

	/** True while the hall and the sect have a non-aggression pact or an alliance (their elders part feuds above T3). */
	public static boolean berpakta(MinecraftServer server, UUID owner, String sekte) {
		if (server == null || owner == null) {
			return false;
		}
		Relasi r = DiplomasiData.get(server).peek(owner, sekte);
		return r != null && r.pakta != Relasi.PAKTA_TIDAK;
	}

	/** True while the hall is at war with the sect. */
	public static boolean perang(MinecraftServer server, UUID owner, String sekte) {
		if (server == null || owner == null) {
			return false;
		}
		Relasi r = DiplomasiData.get(server).peek(owner, sekte);
		return r != null && r.perang;
	}

	/** True when the sect is a vassal of the hall (its disciples do not pick fights with their lord's disciples). */
	public static boolean bawahan(MinecraftServer server, UUID owner, String sekte) {
		if (server == null || owner == null) {
			return false;
		}
		Relasi r = DiplomasiData.get(server).peek(owner, sekte);
		return r != null && r.bawahan;
	}

	// ================================================================== T6: a diplomatic incident

	/**
	 * The feud reached the sect master's ears: the standing changes by {@code delta} (clamped to -20..20) with reason
	 * {@code mandiri.seteru.<sebab>}. 39F keeps its own weekly cap; D's once-per-day rule of other modules is respected.
	 */
	public static boolean insiden(MinecraftServer server, UUID owner, String sekte, int delta, String sebab) {
		SekteNpc sect = server == null ? null : DiplomasiData.get(server).sect(sekte);
		if (sect == null || owner == null || delta == 0 || Diplomasi.server() == null) {
			return false;
		}
		DiplomasiData data = DiplomasiData.get(server);
		if (!data.once(owner, sect.id(), "mandiri_seteru", Diplomasi.today(server))) {
			return false;
		}
		Standing.adjust(server, owner, sect, Math.max(-20, Math.min(20, delta)), "mandiri.seteru." + safe(sebab));
		data.setDirty();
		return true;
	}

	/** A chronicle line of the sect for the hall (D's Diplomasi screen shows it). */
	public static void kronik(MinecraftServer server, UUID owner, String sekte, Component line) {
		SekteNpc sect = server == null ? null : DiplomasiData.get(server).sect(sekte);
		if (sect == null || owner == null || line == null) {
			return;
		}
		DiplomasiData data = DiplomasiData.get(server);
		data.relasi(owner, sect).note(Diplomasi.today(server), line);
		data.setDirty();
	}

	// ================================================================== T7: the sect master acts

	/**
	 * T7 of the ladder (39.F.3): the sect master acts through D. {@code roll} in [0, 1) picks the branch with the design's
	 * weights: the official inter-sect tournament challenge (60 %), war (25 %, only when {@code perangBoleh} and the stance is
	 * already hostile; otherwise the challenge), an envoy of peace (15 %: D's peace offer during a war, else a greeting
	 * envoy). Nothing happens without D or an unknown sect.
	 */
	public static Langkah pemicuBesar(MinecraftServer server, UUID owner, String sekte, double roll, boolean perangBoleh) {
		SekteNpc sect = server == null ? null : DiplomasiData.get(server).sect(sekte);
		if (sect == null || owner == null || Diplomasi.server() == null) {
			return Langkah.TIDAK;
		}
		DiplomasiData data = DiplomasiData.get(server);
		Relasi r = data.relasi(owner, sect);
		long today = Diplomasi.today(server);
		RandomSource random = RandomSource.create(sect.index() * 31L + today * 7919L + owner.getLeastSignificantBits());
		Peristiwa event;
		Langkah step;
		boolean hostile = r.sikap() == dev.kultivator.sekte.api.Sikap.BERMUSUHAN || r.sikap() == dev.kultivator.sekte.api.Sikap.PERANG;
		if (roll < 0.60D) {
			event = Peristiwa.TANTANG;
			step = Langkah.TANTANG;
		} else if (roll < 0.85D) {
			if (perangBoleh && hostile && !r.perang && r.pakta == Relasi.PAKTA_TIDAK) {
				event = Peristiwa.PERANG;
				step = Langkah.PERANG;
			} else {
				event = Peristiwa.TANTANG;
				step = Langkah.TANTANG;
			}
		} else if (r.perang) {
			event = Peristiwa.DAMAI;
			step = Langkah.DAMAI;
		} else {
			event = Peristiwa.UTUSAN;
			step = Langkah.UTUSAN;
		}
		Simulasi.apply(server, data, owner, sect, r, event, random, today);
		return step;
	}

	/**
	 * Two sects reconciled by forbidden lovers (39.F.3 c): D's own non-aggression pact offer is put before the hall (its
	 * envoy and screen answer it); no offer while at war.
	 */
	public static boolean tawarPakta(MinecraftServer server, UUID owner, String sekte) {
		SekteNpc sect = server == null ? null : DiplomasiData.get(server).sect(sekte);
		if (sect == null || owner == null || Diplomasi.server() == null) {
			return false;
		}
		DiplomasiData data = DiplomasiData.get(server);
		Relasi r = data.relasi(owner, sect);
		if (r.perang || r.pakta != Relasi.PAKTA_TIDAK) {
			return false;
		}
		long today = Diplomasi.today(server);
		Simulasi.apply(server, data, owner, sect, r, Peristiwa.PAKTA, RandomSource.create(today * 31L + sect.index()), today);
		return true;
	}

	/** Remembers a member who left the sect for the hall (D's defector list, shown among its gate members). */
	public static void pembelot(MinecraftServer server, String sekte, UUID soul, Component nama) {
		SekteNpc sect = server == null ? null : DiplomasiData.get(server).sect(sekte);
		if (sect != null && soul != null && nama != null) {
			sect.addPembelot(soul, nama);
			DiplomasiData.get(server).setDirty();
		}
	}

	// ================================================================== bodies

	/** A cultivator's data at rank step {@code step} like a sect body's (a defector arriving as a candidate). */
	public static dev.kultivator.disciple.DiscipleData dataTubuh(RandomSource random, int step) {
		return Tubuh.body(random, step);
	}

	/**
	 * A sect body of D's {@code Tubuh} (role RIVAL: never saved, untouchable except by a duel partner) at {@code pos} with
	 * rank step {@code step}, the given name, anchored at {@code anchor}; {@code panji} puts the sect banner in its hand.
	 */
	public static @Nullable DiscipleEntity tubuh(ServerLevel level, Vec3 pos, String sekte, int step, int slot, Component nama,
			@Nullable BlockPos anchor, boolean panji) {
		SekteNpc sect = level == null ? null : DiplomasiData.get(level.getServer()).sect(sekte);
		return sect == null ? null : Tubuh.spawn(level, pos, sect, step, slot, nama, anchor, panji);
	}

	/** The sect's banner item (made now, never cached). */
	public static ItemStack panji(ServerLevel level, String sekte) {
		SekteNpc sect = level == null ? null : DiplomasiData.get(level.getServer()).sect(sekte);
		return sect == null ? ItemStack.EMPTY : Tubuh.banner(level, sect.emblem(), sect.iblis());
	}

	/** A body leaves in a puff of cloud (never a death, never loot). */
	public static void hilang(@Nullable DiscipleEntity body) {
		Tubuh.vanish(body);
	}

	private static String safe(String sebab) {
		return sebab == null || sebab.isEmpty() ? "hinaan" : sebab.replaceAll("[^a-z_]", "");
	}

	private DiplomasiMandiri() {
	}
}
