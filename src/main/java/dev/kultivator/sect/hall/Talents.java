package dev.kultivator.sect.hall;

import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.data.RootGrade;
import dev.kultivator.disciple.DiscipleData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;

/**
 * Soul colour (Warna Alam Jiwa), talents (Bakat), rare physique (Fisik Langka) and special type (Calon Istimewa) of a
 * disciple (DESIGN 20.3.4-20.3.5): module A rolls and stores them in {@code DiscipleData.extra().talent}, module C applies
 * their effects. The ids below are the shared numbering (talents are bits, physique and special are 1-based ids).
 * <p>
 * Sources (DESIGN 20.3.5): the seven soul-realm colours of <i>Tales of Demons and Gods</i> ch. 13-14; each talent and
 * physique names its own work in the DESIGN tables.
 */
public final class Talents {

	/** Sub-compound of {@code DiscipleData.extra()} owned by module A. */
	public static final String EXTRA = "talent";

	// ---- soul colours (TDG ch. 13-14), red lowest, indigo near-legendary
	public static final int MERAH = 0;
	public static final int JINGGA = 1;
	public static final int KUNING = 2;
	public static final int HIJAU = 3;
	public static final int SIAN = 4;
	public static final int BIRU_LANGIT = 5;
	public static final int NILA = 6;
	/** RGB per colour, for cards, the Duan pillar and the aura tint. */
	public static final int[] COLOUR_RGB = {0xE0342C, 0xF08A24, 0xF2D43A, 0x4FCB5A, 0x3FD6D0, 0x4C8CFF, 0x6A4CE0};
	/** The XP factor each colour gives (DESIGN 20.3.5 table, applied by module C through {@code potentialFactor}). */
	public static final double[] COLOUR_XP = {0.6D, 0.85D, 1.0D, 1.1D, 1.3D, 1.5D, 1.7D};

	// ---- talents (bits); weights in DESIGN 20.3.5
	public static final int HATI_GIGIH = 1;
	public static final int DARAH_DEWA_KUNO = 2;
	public static final int BAKAT_PIL = 4;
	public static final int JARI_PENEMPA_ROH = 8;
	public static final int TAKUT_MATI = 16;
	public static final int INDRA_PEDANG = 32;
	public static final int TALENT_COUNT = 6;
	private static final int[] TALENT_WEIGHT = {26, 2, 22, 22, 16, 12};
	private static final String[] TALENT_ID = {"hati_gigih", "darah_dewa_kuno", "bakat_pil", "jari_penempa_roh", "takut_mati", "indra_pedang"};
	/** Talent roll: 60% none, 32% one, 8% two. */
	public static final double TALENT_ONE = 0.32D;
	public static final double TALENT_TWO = 0.08D;

	// ---- rare physiques (ids 1-9)
	public static final int RACUN_MALAPETAKA = 1;
	public static final int NAGA_TAIXU = 2;
	public static final int DOU_DI = 3;
	public static final int TUBUH_EMAS = 4;
	public static final int SIMBOL_MELAHAP = 5;
	public static final int TULANG_MAHATINGGI = 6;
	public static final int PUPIL_GANDA = 7;
	public static final int NADI_DEWA_SESAT = 8;
	public static final int TANPA_ATRIBUT = 9;
	public static final int PHYSIQUE_COUNT = 9;
	private static final int[] PHYSIQUE_WEIGHT = {14, 12, 10, 14, 8, 10, 10, 8, 14};
	private static final String[] PHYSIQUE_ID = {"racun_malapetaka", "naga_taixu", "dou_di", "tubuh_emas", "simbol_melahap", "tulang_mahatinggi",
			"pupil_ganda", "nadi_dewa_sesat", "tanpa_atribut"};
	/** Physique chance: 6% base, +1% per hall level above 1, +5% for a Calon Jenius; 30% with a HEAVENLY root. */
	public static final double PHYSIQUE_BASE = 0.06D;
	public static final double PHYSIQUE_PER_LEVEL = 0.01D;
	public static final double PHYSIQUE_GENIUS = 0.05D;
	public static final double PHYSIQUE_HEAVENLY = 0.30D;

	// ---- special candidates (ids 1-3, at most one per arrival)
	public static final int PENGEMIS = 1;
	public static final int REGRESOR = 2;
	public static final int DANTIAN_TERSUMBAT = 3;
	public static final double PENGEMIS_CHANCE = 0.02D;
	public static final double REGRESOR_CHANCE = 0.03D;
	public static final double DANTIAN_CHANCE = 0.04D;
	private static final String[] SPECIAL_ID = {"pengemis", "regresor", "dantian_tersumbat"};

	/** Calon Jenius (TDG Genius class): age at most this and colour at least Sian. */
	public static final int GENIUS_AGE = 14;

	// ================================================================== reads (DESIGN 20.4.5)

	/**
	 * Soul colour 0 Merah .. 6 Nila. Without a saved colour it follows the root grade (Kuning for MID). A root raised
	 * later (Marrow Cleansing) lifts the colour to at least that grade's base colour.
	 */
	public static int soulColor(DiscipleData data) {
		CompoundTag tag = data.extra().getCompoundOrEmpty(EXTRA);
		int base = byGrade(data.rootGrade());
		if (!tag.contains("colour")) {
			return base;
		}
		return Math.max(0, Math.min(6, Math.max(tag.getIntOr("colour", base), base)));
	}

	public static void setSoulColor(DiscipleData data, int colour) {
		data.extraSection(EXTRA).putInt("colour", Math.max(0, Math.min(6, colour)));
	}

	/** Talent bits (0 none). */
	public static int talents(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(EXTRA).getIntOr("talents", 0);
	}

	/** Rare physique id (0 none). */
	public static int physique(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(EXTRA).getIntOr("physique", 0);
	}

	/** Special candidate type (0 none). */
	public static int special(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(EXTRA).getCompoundOrEmpty("special").getIntOr("type", 0);
	}

	/** The Regressor's remembered previous-life realm ordinal (0 none). */
	public static int regressorRealm(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(EXTRA).getCompoundOrEmpty("special").getIntOr("realm_r", 0);
	}

	/** Tekad (determination) 0-100. */
	public static int tekad(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(EXTRA).getIntOr("tekad", 50);
	}

	/** True once the entrance test was taken (or the disciple joined: everything is revealed then). */
	public static boolean tested(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(EXTRA).getBooleanOr("tested", false);
	}

	public static int age(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(EXTRA).getIntOr("age", 15);
	}

	/** Duan 1-9 the testing stone shows (BTTH's Dou Zhi Li). */
	public static int duan(DiscipleData data) {
		CompoundTag tag = data.extra().getCompoundOrEmpty(EXTRA);
		return tag.contains("duan") ? tag.getIntOr("duan", 1) : computeDuan(data);
	}

	public static boolean genius(DiscipleData data) {
		return age(data) <= GENIUS_AGE && soulColor(data) >= SIAN;
	}

	public static boolean hasTalent(DiscipleData data, int bit) {
		return (talents(data) & bit) != 0;
	}

	/** The synced talent int for the entity (physique, colour, special); 0 while untested. */
	public static int packed(DiscipleData data) {
		if (!tested(data)) {
			return 0;
		}
		// Masks the hidden physique and special exactly like module C's once-a-second sync, so nothing flashes.
		return dev.kultivator.disciple.power.DisciplePowers.syncedTalent(data);
	}

	/** Base colour of a root grade (MID and HIGH at their lower colour). */
	public static int byGrade(RootGrade grade) {
		return switch (grade) {
			case TRASH -> MERAH;
			case LOW -> JINGGA;
			case MID -> KUNING;
			case HIGH -> SIAN;
			case HEAVENLY -> NILA;
		};
	}

	// ================================================================== writes

	public static void addTalent(DiscipleData data, int bit) {
		data.extraSection(EXTRA).putInt("talents", talents(data) | bit);
	}

	public static void setPhysique(DiscipleData data, int physique) {
		data.extraSection(EXTRA).putInt("physique", Math.max(0, Math.min(PHYSIQUE_COUNT, physique)));
		data.extraSection(EXTRA).putInt("duan", computeDuan(data));
	}

	public static void setTekad(DiscipleData data, int tekad) {
		data.extraSection(EXTRA).putInt("tekad", Math.max(0, Math.min(100, tekad)));
	}

	public static void setTested(DiscipleData data, boolean tested) {
		data.extraSection(EXTRA).putBoolean("tested", tested);
	}

	/** Recomputes the stored Duan after the root or physique changed. */
	public static void refreshDuan(DiscipleData data) {
		data.extraSection(EXTRA).putInt("duan", computeDuan(data));
	}

	/** {@code clamp(1 + 2 x grade + floor((5 - elements) / 2) + (physique ? 1 : 0), 1, 9)} (DESIGN 20.3.3). */
	public static int computeDuan(DiscipleData data) {
		int elements = Math.max(1, Element.fromMask(data.rootElements()).size());
		int value = 1 + 2 * data.rootGrade().ordinal() + Math.floorDiv(5 - elements, 2) + (physique(data) > 0 ? 1 : 0);
		return Math.max(1, Math.min(9, value));
	}

	// ================================================================== rolls (module A)

	/** A colour for the grade: MID 60% Kuning / 40% Hijau, HIGH 65% Sian / 35% Biru Langit. */
	public static int rollColour(RootGrade grade, RandomSource random) {
		return switch (grade) {
			case TRASH -> MERAH;
			case LOW -> JINGGA;
			case MID -> random.nextDouble() < 0.6D ? KUNING : HIJAU;
			case HIGH -> random.nextDouble() < 0.65D ? SIAN : BIRU_LANGIT;
			case HEAVENLY -> NILA;
		};
	}

	/**
	 * Rolls talents: with {@code anyChance} < 0 the table chance (60% none, 32% one, 8% two); otherwise {@code anyChance}
	 * for at least one, a fifth of those two. Hati Gigih only for TRASH and LOW roots.
	 */
	public static int rollTalents(RootGrade grade, double anyChance, RandomSource random) {
		double one = anyChance < 0.0D ? TALENT_ONE : anyChance * 0.8D;
		double two = anyChance < 0.0D ? TALENT_TWO : anyChance * 0.2D;
		double r = random.nextDouble();
		int count = r < two ? 2 : r < two + one ? 1 : 0;
		int bits = 0;
		boolean persevering = grade == RootGrade.TRASH || grade == RootGrade.LOW;
		for (int n = 0; n < count; n++) {
			int total = 0;
			for (int i = 0; i < TALENT_COUNT; i++) {
				if ((bits & 1 << i) == 0 && (i != 0 || persevering)) {
					total += TALENT_WEIGHT[i];
				}
			}
			if (total <= 0) {
				break;
			}
			int pick = random.nextInt(total);
			for (int i = 0; i < TALENT_COUNT; i++) {
				if ((bits & 1 << i) != 0 || i == 0 && !persevering) {
					continue;
				}
				pick -= TALENT_WEIGHT[i];
				if (pick < 0) {
					bits |= 1 << i;
					break;
				}
			}
		}
		return bits;
	}

	/** One physique by weight (never 0). */
	public static int rollPhysique(RandomSource random) {
		int total = 0;
		for (int weight : PHYSIQUE_WEIGHT) {
			total += weight;
		}
		int pick = random.nextInt(total);
		for (int i = 0; i < PHYSIQUE_COUNT; i++) {
			pick -= PHYSIQUE_WEIGHT[i];
			if (pick < 0) {
				return i + 1;
			}
		}
		return TANPA_ATRIBUT;
	}

	/** The physique chance of a candidate (DESIGN 20.3.5). */
	public static double physiqueChance(RootGrade grade, int hallLevel, boolean genius) {
		if (grade == RootGrade.HEAVENLY) {
			return PHYSIQUE_HEAVENLY;
		}
		return PHYSIQUE_BASE + PHYSIQUE_PER_LEVEL * Math.max(0, hallLevel - 1) + (genius ? PHYSIQUE_GENIUS : 0.0D);
	}

	/**
	 * Writes everything of a new candidate into {@code data.extra().talent}: Tekad ({@code tekad} < 0 rolls 20-100), age
	 * 12-16, colour, talents, physique (chance {@code physiqueChance} < 0: the hall's normal chance), the Duan. The Heretic
	 * God Veins look like a TRASH root with a red soul until module C reveals them.
	 */
	public static void rollFor(DiscipleData data, int tekad, double talentChance, double physiqueChance, int hallLevel, RandomSource random) {
		CompoundTag tag = data.extraSection(EXTRA);
		int age = 12 + random.nextInt(5);
		tag.putInt("age", age);
		tag.putInt("tekad", tekad >= 0 ? Math.min(100, tekad) : 20 + random.nextInt(81));
		tag.putBoolean("tested", false);
		RootGrade grade = data.rootGrade();
		int colour = rollColour(grade, random);
		tag.putInt("colour", colour);
		tag.putInt("talents", rollTalents(grade, talentChance, random));
		boolean genius = age <= GENIUS_AGE && colour >= SIAN;
		double chance = physiqueChance >= 0.0D ? physiqueChance : physiqueChance(grade, hallLevel, genius);
		int physique = random.nextDouble() < chance ? rollPhysique(random) : 0;
		tag.putInt("physique", physique);
		if (physique == NADI_DEWA_SESAT) {
			// The veins look absolutely ordinary (AtG ch. 45-47): a trash root and a red soul until the reveal.
			data.setRootGrade(RootGrade.TRASH);
			tag.putInt("colour", MERAH);
		}
		tag.putInt("duan", computeDuan(data));
	}

	/** Makes {@code data} the special candidate {@code type} (Beggar and Regressor change what the card shows). */
	public static void makeSpecial(DiscipleData data, int type, RandomSource random) {
		CompoundTag special = new CompoundTag();
		special.putInt("type", type);
		special.putBoolean("revealed", false);
		if (type == PENGEMIS) {
			// The Plum Blossom Sword Saint reborn as a ragged beggar boy: the card shows a trash root and a red soul.
			data.setRootGrade(RootGrade.TRASH);
			data.extraSection(EXTRA).putInt("colour", MERAH);
		} else if (type == REGRESOR) {
			special.putInt("realm_r", Realm.GOLDEN_CORE.ordinal() + random.nextInt(Realm.DAO_UNION.ordinal() - Realm.GOLDEN_CORE.ordinal() + 1));
		}
		data.extraSection(EXTRA).put("special", special);
		data.extraSection(EXTRA).putInt("duan", computeDuan(data));
	}

	// ================================================================== names

	public static MutableComponent colourName(int colour) {
		int index = Math.max(0, Math.min(6, colour));
		return Component.translatable("kultivator.hall.colour." + index).withColor(COLOUR_RGB[index]);
	}

	public static MutableComponent talentName(int index) {
		return Component.translatable("kultivator.hall.talent." + TALENT_ID[Math.max(0, Math.min(TALENT_COUNT - 1, index))]);
	}

	public static MutableComponent physiqueName(int physique) {
		return Component.translatable("kultivator.hall.physique." + PHYSIQUE_ID[Math.max(1, Math.min(PHYSIQUE_COUNT, physique)) - 1]);
	}

	public static MutableComponent specialName(int special) {
		return Component.translatable("kultivator.hall.special." + SPECIAL_ID[Math.max(1, Math.min(3, special)) - 1]);
	}

	/** "Hati Gigih, Bakat Pil" or "none". */
	public static MutableComponent talentList(int bits) {
		if (bits == 0) {
			return Component.translatable("kultivator.hall.talent.none");
		}
		MutableComponent list = Component.empty();
		boolean first = true;
		for (int i = 0; i < TALENT_COUNT; i++) {
			if ((bits & 1 << i) != 0) {
				if (!first) {
					list.append(Component.literal(", "));
				}
				list.append(talentName(i));
				first = false;
			}
		}
		return list;
	}

	private Talents() {
	}
}
