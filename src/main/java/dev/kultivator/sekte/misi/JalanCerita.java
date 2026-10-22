package dev.kultivator.sekte.misi;

import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Jalan Cerita, the story of a mission (DESIGN 36.2.7): the chance of each of the three stages and its roll. A failed
 * stage does not end the mission: the story takes its {@code gagal} branch, someone may be hurt, and the next stage goes
 * on (so the report always has three paragraphs).
 * <p>
 * Stage chance = {@code dasar(tier) + 0.03 x (mean realm - (tier - 1))} + stance (+0.08 when it fits the stage, -0.05
 * when it clashes) + traits (Berani +0.05 at a fight, Licik +0.05 at a cunning stage, Penakut -0.05 at a fight; the sum
 * capped to +-0.10) + ties (+0.03 per Sahabat / Saudara pair in the team, -0.10 per Dendam pair) + X's own team term
 * (+0.02 per member after the first) - 0.04 per member already hurt - the ambush; then times P's mean work factor (half
 * strength) and E's {@code officeFactor(TETUA_LUAR)}; clamped to 0.10-0.95.
 */
final class JalanCerita {

	static final double MIN = 0.10D;
	static final double MAX = 0.95D;
	static final double PER_REALM = 0.03D;
	static final double STANCE_FIT = 0.08D;
	static final double STANCE_CLASH = -0.05D;
	static final double TRAIT = 0.05D;
	static final double TRAIT_CAP = 0.10D;
	static final double TIE_GOOD = 0.03D;
	static final double TIE_GRUDGE = -0.10D;
	static final double TEAM_PER_MEMBER = 0.02D;
	static final double HURT = -0.04D;
	/** An ambush (module D's {@code SERGAP_MISI}) of magnitude 1-9 costs 0.05 x magnitude / 3 on the next stage. */
	static final double AMBUSH_PER_3 = 0.05D;
	/** Chance that a failed stage hurts one member, by stage kind (a Penakut member halves its own share). */
	static final double HURT_TEMPUR = 0.60D;
	static final double HURT_LICIK = 0.30D;
	static final double HURT_OTHER = 0.20D;

	/** The parts of one stage's chance (for the preview line of the board). */
	record Rincian(double base, double realm, double stance, double traits, double ties, double team, double hurt, double ambush,
			double work, double office, double total) {
	}

	/** The members' snapshot taken at departure ({@code realm}, {@code sifat}, {@code wf}). */
	static Rincian chance(UUID owner, CompoundTag mission, int stage) {
		TemplatMisi tpl = TemplatMisi.byId(mission.getStringOr("tpl", ""));
		if (tpl == null) {
			return new Rincian(0.5D, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0.5D);
		}
		int tier = Math.max(1, Math.min(9, mission.getIntOr("tier", 1)));
		TemplatMisi.Pendekatan stance = TemplatMisi.Pendekatan.byId(mission.getStringOr("sikap", ""));
		TemplatMisi.Jenis kind = stageKind(mission, tpl, stage);
		List<CompoundTag> team = MisiData.team(mission);
		MisiConfig cfg = MisiConfig.get();
		double base = cfg.peluangDasar - cfg.turunPerTingkat * (tier - 1);
		double realmSum = 0.0D;
		double workSum = 0.0D;
		double traits = 0.0D;
		int hurt = 0;
		List<UUID> souls = new ArrayList<>();
		for (CompoundTag member : team) {
			realmSum += member.getIntOr("realm", 0);
			workSum += member.getDoubleOr("wf", 1.0D);
			List<Sifat> sifat = traits(member);
			if (kind == TemplatMisi.Jenis.TEMPUR && sifat.contains(Sifat.BERANI)) {
				traits += TRAIT;
			}
			if (kind == TemplatMisi.Jenis.LICIK && sifat.contains(Sifat.LICIK)) {
				traits += TRAIT;
			}
			if (kind == TemplatMisi.Jenis.TEMPUR && sifat.contains(Sifat.PENAKUT)) {
				traits -= TRAIT;
			}
			int hurtAt = member.getIntOr("hurt_at", -1);
			if (hurtAt >= 0 && hurtAt < stage) {
				hurt++;
			}
			UUID soul = MisiData.soul(member);
			if (soul != null) {
				souls.add(soul);
			}
		}
		int n = Math.max(1, team.size());
		double realm = PER_REALM * (realmSum / n - (tier - 1));
		double stanceTerm = kind == stance.fits() ? STANCE_FIT : kind == stance.clashes() ? STANCE_CLASH : 0.0D;
		traits = Math.max(-TRAIT_CAP, Math.min(TRAIT_CAP, traits));
		double ties = ties(owner, souls);
		double teamTerm = TEAM_PER_MEMBER * (n - 1);
		double hurtTerm = HURT * hurt;
		double ambush = 0.0D;
		if (mission.getIntOr("amb_stage", -1) == stage) {
			ambush = -AMBUSH_PER_3 * mission.getIntOr("amb_mag", 3) / 3.0D;
		}
		double work = 1.0D + (workSum / n - 1.0D) * 0.5D;
		double office = SekteApi.tetua().officeFactor(owner, Jabatan.TETUA_LUAR);
		double raw = (base + realm + stanceTerm + traits + ties + teamTerm + hurtTerm + ambush) * work * office;
		return new Rincian(base, realm, stanceTerm, traits, ties, teamTerm, hurtTerm, ambush, work, office, Math.max(MIN, Math.min(MAX, raw)));
	}

	/** The kind of stage {@code stage}: an ambushed stage is always a fight. */
	static TemplatMisi.Jenis stageKind(CompoundTag mission, TemplatMisi tpl, int stage) {
		return mission.getIntOr("amb_stage", -1) == stage ? TemplatMisi.Jenis.TEMPUR : tpl.stage(stage);
	}

	static double ties(UUID owner, List<UUID> souls) {
		double sum = 0.0D;
		for (int i = 0; i < souls.size(); i++) {
			for (int j = i + 1; j < souls.size(); j++) {
				Ikatan kind = SekteApi.hubungan().kind(owner, souls.get(i), souls.get(j));
				if (kind == Ikatan.DENDAM) {
					sum += TIE_GRUDGE;
				} else if (kind.positive()) {
					sum += TIE_GOOD;
				}
			}
		}
		return sum;
	}

	static List<Sifat> traits(CompoundTag member) {
		List<Sifat> list = new ArrayList<>();
		for (Tag tag : member.getListOrEmpty("sifat")) {
			if (tag instanceof StringTag(String value)) {
				Sifat sifat = Sifat.byId(value);
				if (sifat != null) {
					list.add(sifat);
				}
			}
		}
		return list;
	}

	static ListTag traitTag(List<Sifat> traits) {
		ListTag list = new ListTag();
		for (Sifat sifat : traits) {
			list.add(StringTag.valueOf(sifat.id()));
		}
		return list;
	}

	/**
	 * Rolls stage {@code stage} (0-2) of the mission and writes {@code stages[stage]}: {@code kind}, {@code ok},
	 * {@code chance}, {@code amb}, {@code hurt} (the soul hurt by a failure, or ""). A forced result ({@code paksa} 0-3, the
	 * test command) makes the first {@code paksa} stages succeed and the rest fail. Returns the stage tag.
	 */
	static CompoundTag roll(UUID owner, CompoundTag mission, int stage, RandomSource random) {
		TemplatMisi tpl = TemplatMisi.byId(mission.getStringOr("tpl", ""));
		TemplatMisi.Jenis kind = tpl == null ? TemplatMisi.Jenis.JELAJAH : stageKind(mission, tpl, stage);
		Rincian rincian = chance(owner, mission, stage);
		int forced = mission.getIntOr("paksa", -1);
		boolean ok = forced >= 0 ? stage < forced : random.nextDouble() < rincian.total();
		CompoundTag tag = new CompoundTag();
		tag.putString("kind", kind.id());
		tag.putBoolean("ok", ok);
		tag.putDouble("chance", rincian.total());
		tag.putBoolean("amb", mission.getIntOr("amb_stage", -1) == stage);
		tag.putString("hurt", "");
		if (!ok) {
			double hurtChance = kind == TemplatMisi.Jenis.TEMPUR ? HURT_TEMPUR : kind == TemplatMisi.Jenis.LICIK ? HURT_LICIK : HURT_OTHER;
			List<CompoundTag> healthy = new ArrayList<>();
			for (CompoundTag member : MisiData.team(mission)) {
				if (member.getIntOr("hurt_at", -1) < 0) {
					healthy.add(member);
				}
			}
			if (!healthy.isEmpty()) {
				CompoundTag victim = healthy.get(random.nextInt(healthy.size()));
				double own = traits(victim).contains(Sifat.PENAKUT) ? hurtChance * 0.5D : hurtChance;
				if (random.nextDouble() < own) {
					victim.putInt("hurt_at", stage);
					tag.putString("hurt", victim.getStringOr("soul", ""));
				}
			}
		}
		ListTag stages = MisiData.list(mission, "stages");
		while (stages.size() < stage) {
			stages.add(new CompoundTag());
		}
		if (stages.size() == stage) {
			stages.add(tag);
		} else {
			stages.set(stage, tag);
		}
		mission.putInt("done", Math.max(mission.getIntOr("done", 0), stage + 1));
		MisiData.dirty();
		return tag;
	}

	/** Successful stages so far. */
	static int successes(CompoundTag mission) {
		int ok = 0;
		for (CompoundTag stage : MisiData.compounds(mission.getListOrEmpty("stages"))) {
			if (stage.getBooleanOr("ok", false)) {
				ok++;
			}
		}
		return ok;
	}

	private JalanCerita() {
	}
}
