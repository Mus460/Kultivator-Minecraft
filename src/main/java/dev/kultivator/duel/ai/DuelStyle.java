package dev.kultivator.duel.ai;

/**
 * The four duel styles (DESIGN 35.7.2), after the four swords of Dugu Qiubai's Sword Tomb. The ordinal is saved in
 * nothing but sent as {@code DuelIds.STYLE_*}; never reorder. Every number is game design.
 * <p>
 * Sumber: Jin Yong, The Return of the Condor Heroes (神雕侠侣): Yang Guo finds Dugu Qiubai's Sword Tomb with four stages,
 * the sharp sword of his youth ("sharp, strong, fierce"), the soft purple sword of his twenties, the heavy blunt sword of
 * his thirties ("great skill lies in simplicity") and, after forty, no sword at all (grass, wood and stone became swords).
 * Jin Yong, The Smiling, Proud Wanderer (笑傲江湖): the Nine Swords of Dugu follow no fixed order or pattern, so they
 * cannot be read. (en.wikipedia.org/wiki/Dugu_Qiubai)
 *
 * @param attack    chance per decision to attack when in reach and free
 * @param parry     chance to answer a seen swing with a deflect press
 * @param guard     chance to answer with a held guard
 * @param dodge     chance to answer with a step aside
 * @param counter   chance to answer with an own swing (looking for a clash)
 * @param feint     chance that an own swing is a feint
 * @param thrust    chance that an own light swing is a thrust
 * @param side      chance per second to step sideways when out of reach
 * @param heavy     share of own swings that are heavy
 * @param retreat   posture share above which it backs off (1 = never)
 * @param surrender health share below which it yields when nothing is staked (0 = never)
 * @param push      blade lock push multiplier ("gayaMul", 35.3.7)
 * @param keepDistance preferred distance as a share of its reach
 * @param rhythm    random pause (0..rhythm ticks) before a combo cut lands: the soft sword changes its rhythm
 * @param colorRgb  the colour of its title on the cards
 */
public enum DuelStyle {
	/** "Pedang Tajam": combos of three, frequent heavy cuts, always pressing, rarely guarding. */
	AGRESIF(0.70F, 0.25F, 0.15F, 0.05F, 0.40F, 0.05F, 0.15F, 0.10F, 0.35F, 0.80F, 0.0F, 1.15F, 0.75F, 0, 0xE0503A),
	/** "Pedang Berat": long guards, answers after a deflect, Mountain Cleaves and guard breakers, patient. */
	BERTAHAN(0.40F, 0.40F, 0.50F, 0.05F, 0.05F, 0.05F, 0.10F, 0.10F, 0.60F, 1.0F, 0.15F, 1.05F, 0.95F, 0, 0x8C8C9C),
	/** "Pedang Lunak": feints, thrusts, side steps, a changing rhythm and the Low Sweep. */
	PENIPU(0.55F, 0.30F, 0.20F, 0.30F, 0.10F, 0.30F, 0.35F, 0.25F, 0.25F, 0.90F, 0.25F, 0.95F, 0.85F, 8, 0xA070D8),
	/** "Tanpa Pedang": no fixed pattern, reads the opponent's habits, waits for Openings, uses the release trick. */
	SANTO_PEDANG(0.55F, 0.60F, 0.20F, 0.10F, 0.10F, 0.15F, 0.20F, 0.15F, 0.40F, 1.0F, 0.20F, 1.10F, 0.90F, 4, 0xFFD87A);

	public final float attack;
	public final float parry;
	public final float guard;
	public final float dodge;
	public final float counter;
	public final float feint;
	public final float thrust;
	public final float side;
	public final float heavy;
	public final float retreat;
	public final float surrender;
	public final float push;
	public final float keepDistance;
	public final int rhythm;
	public final int colorRgb;

	DuelStyle(float attack, float parry, float guard, float dodge, float counter, float feint, float thrust, float side, float heavy,
			float retreat, float surrender, float push, float keepDistance, int rhythm, int colorRgb) {
		this.attack = attack;
		this.parry = parry;
		this.guard = guard;
		this.dodge = dodge;
		this.counter = counter;
		this.feint = feint;
		this.thrust = thrust;
		this.side = side;
		this.heavy = heavy;
		this.retreat = retreat;
		this.surrender = surrender;
		this.push = push;
		this.keepDistance = keepDistance;
		this.rhythm = rhythm;
		this.colorRgb = colorRgb;
	}

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	/** {@code kultivator.duel.style.<id>}. */
	public String langKey() {
		return "kultivator.duel.style." + id();
	}

	public static DuelStyle byOrdinal(int ordinal) {
		DuelStyle[] values = values();
		return ordinal < 0 || ordinal >= values.length ? BERTAHAN : values[ordinal];
	}

	/** Indonesian or English id ("agresif", "bertahan", "penipu", "santo" / "santo_pedang"); null when unknown. */
	public static DuelStyle byId(String id) {
		if (id == null) {
			return null;
		}
		String key = id.toLowerCase(java.util.Locale.ROOT);
		return switch (key) {
			case "agresif", "aggressive" -> AGRESIF;
			case "bertahan", "defensive" -> BERTAHAN;
			case "penipu", "trickster" -> PENIPU;
			case "santo", "santo_pedang", "saint" -> SANTO_PEDANG;
			default -> null;
		};
	}
}
