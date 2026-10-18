package dev.kultivator.sect.build.flow;

import dev.kultivator.kehancuran.ProtectedZones;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.event.Duties;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.core.BlockPos;

/**
 * Plugs Pembangunan Sekte into the phase-6 hall (DESIGN 21.17, 21.24 item 5): registers every provider the spine added to
 * modules A, B and D. With a template:
 * <ul>
 * <li>the building cap is {@code max(frozenScanCap, tier)} (and the guarantee tier), so the 30-second scan is skipped and the
 * hall level never drops because of a construction choice; the building bits follow the tier;</li>
 * <li>the territory radius is {@code max(20 + 4L, R_t)}, the disciple cap gets +1 for templates 5-9 at T9, candidates +1 for
 * templates 6-9 at tier 7 or more, and arrive at the plan's {@code arrival} anchor;</li>
 * <li>the Aula tablet shows the construction line instead of the building checklist;</li>
 * <li>duty posts use the {@code post_*} anchors; Mode Kehancuran refuses cells within 16 blocks of any site;</li>
 * <li>re-placing the stele elsewhere opens the 6b move dialog ({@link Relocation}).</li>
 * </ul>
 * Without a template every provider returns its "nothing" value and phase 6 is unchanged.
 * <p>
 * Source: as DESIGN 20.3.1 (Yue Lu Hall, the Magic Pavilion, Mount Hua's drill ground, the Blazing Sky tower);
 * <i>Return of the Mount Hua Sect</i> (the sect grows in stone while its fame, the renown, comes from victories);
 * <i>Perfect World</i> ch. 551/558 (rebuild to rise).
 */
public final class HallBridge {

	/** Kehancuran keeps this far from every footprint (DESIGN 21.17). */
	public static final int KEHANCURAN_MARGIN = 16;
	/** A candidate's arrival spot is spread this far around the {@code arrival} anchor. */
	public static final int ARRIVAL_SPREAD = 2;

	public static void register() {
		Halls.addBuildingCapProvider(SectBuild::buildingCap);
		Halls.addBuildingFlagsProvider(SectBuild::buildingFlags);
		Halls.addRadiusProvider(SectBuild::territoryRadius);
		Halls.addCapBonusProvider(SectBuild::capBonus);
		Halls.addCandidateBonusProvider(SectBuild::extraCandidates);
		Halls.addArrivalSpotProvider((owner, level, stele, random) -> {
			BlockPos arrival = SectBuild.anchor(owner, "arrival");
			if (arrival == null) {
				return null;
			}
			return arrival.offset(random.nextInt(ARRIVAL_SPREAD * 2 + 1) - ARRIVAL_SPREAD, 0, random.nextInt(ARRIVAL_SPREAD * 2 + 1) - ARRIVAL_SPREAD);
		});
		Halls.addStatusLineProvider(SectBuild::statusLine);
		Halls.addSteleMoveListener(Relocation.INSTANCE);
		Duties.addPostSpotProvider((owner, duty, index) -> {
			BlockPos post = SectBuild.anchor(owner, "post_" + duty);
			return post == null ? null : post.offset(2 * index, 0, 0);
		});
		ProtectedZones.addZoneProvider((level, pos) -> SectBuild.insideAnySite(level, pos, KEHANCURAN_MARGIN));
	}

	private HallBridge() {
	}
}
