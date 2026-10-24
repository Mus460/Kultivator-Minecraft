package dev.kultivator.dunia.tempat.kampung;

import dev.kultivator.data.RootGrade;
import dev.kultivator.desa.Tribute;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * The talented children of a pledged village (DESIGN 38.C.2.3 and 38.M.4.3: "kampung terikrar: 1 calon per 10 hari"):
 * when the master of a pledged village of this module (a {@code kampung/*} site, not a vanilla village) stands by its
 * bell, and {@code kampung.hariAnakBerbakat} days have passed since the last one, the village head brings a child with
 * at least a middle spiritual root; the child arrives at the hall as a candidate through {@link Halls#addCandidates}
 * (source {@code village}, origin the village's name, +10 resolve like the Desa youths). The Desa system's own youths of
 * loyal villages are not touched; this is the village-of-38's extra. The day is saved per village.
 */
final class AnakBerbakat {

	/** Resolve bonus of a village child (the same as {@code Desa.CANDIDATE_RESOLVE}). */
	static final int TEKAD_DESA = 10;
	/** Number of told variants of how the village head brings the child ({@code kampung.anak.datang.N}). */
	static final int CERITA = 5;

	/** Every second near the bell. */
	static void dekat(ServerLevel level, DuniaAnchorEntity anchor, long site, List<ServerPlayer> within) {
		Tribute.Ikrar ikrar = Tribute.ikrarDi(anchor.blockPosition(), 24.0D);
		if (ikrar == null) {
			return;
		}
		for (ServerPlayer player : within) {
			if (player.getUUID().equals(ikrar.owner()) && player.distanceToSqr(anchor) <= 24.0D * 24.0D) {
				coba(level, player, site, false);
				return;
			}
		}
	}

	/**
	 * Brings the child when due (or at once with {@code paksa}, {@code uji kampung anak}). Returns true when a child came.
	 */
	static boolean coba(ServerLevel level, ServerPlayer owner, long site, boolean paksa) {
		KampungData data = KampungData.get(level.getServer());
		KampungData.Desa d = data.cari(site);
		if (d == null) {
			return false;
		}
		long day = Kota.day(level.getServer());
		if (!paksa && day - d.hariAnak < KampungConfig.get().hariAnakBerbakat) {
			return false;
		}
		if (Halls.hallOf(owner.getUUID()) == null) {
			return false;
		}
		RandomSource random = level.getRandom();
		DiscipleData child = DiscipleData.fresh(random);
		if (child.rootGrade().ordinal() < RootGrade.MID.ordinal()) {
			child.setRootGrade(RootGrade.MID);
		}
		Component name = KampungNames.nama(d.ukuran, d.situs);
		int tekad = 40 + random.nextInt(61);
		Halls.addCandidates(owner.getUUID(), List.of(new Halls.CandidateSpec(child, tekad, -1.0D, -1.0D, false, "village", KampungNames.kataKunci(d.situs),
				TEKAD_DESA)));
		data.hariAnak(d, day);
		Msg.chat(owner, Component.translatable("kultivator.kampung.anak.datang." + random.nextInt(CERITA), name).withStyle(ChatFormatting.GOLD));
		Msg.chat(owner, Component.translatable("kultivator.kampung.anak.akar", child.rootGrade().displayName().copy()
				.withStyle(s -> s.withColor(child.rootGrade().color()))).withStyle(ChatFormatting.GRAY));
		return true;
	}

	private AnakBerbakat() {
	}
}
