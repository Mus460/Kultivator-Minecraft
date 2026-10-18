package dev.kultivator.formasi.spec;

import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The five formation grades (DESIGN 24.2.4, the 6c ladder of 22.7): hall gate and upkeep in Daya per hour. Labels:
 * {@code kultivator.formasi.grade.<name>} (module A's text). Saved by name; never reorder.
 */
public enum Grade {
	KUNING(1, 3, Realm.QI_REFINING, 1, 0xD8B04A),
	MISTERIUS(3, 6, Realm.FOUNDATION, 1, 0x9A6AE0),
	BUMI(5, 12, Realm.GOLDEN_CORE, 1, 0xB07A40),
	LANGIT(7, 18, Realm.NASCENT_SOUL, 2, 0x5AA8FF),
	ABADI(9, 24, Realm.DEITY_TRANSFORMATION, 2, 0xFFF0C0);

	/** Hours of upkeep one core can hold (the core cap of 24.2.4). */
	public static final int CAP_HOURS = 48;

	private final int gate;
	private final double upkeep;
	private final Realm keeperRealm;
	private final int maxKeepers;
	private final int color;

	Grade(int gate, double upkeep, Realm keeperRealm, int maxKeepers, int color) {
		this.gate = gate;
		this.upkeep = upkeep;
		this.keeperRealm = keeperRealm;
		this.maxKeepers = maxKeepers;
		this.color = color;
	}

	/** The lowest hall level that may run this grade. */
	public int gate() {
		return gate;
	}

	/** Default upkeep in Daya per hour. */
	public double upkeep() {
		return upkeep;
	}

	/** The lowest rank a keeper must have (first layer and stage of the realm). */
	public Rank keeperRealm() {
		return new Rank(keeperRealm, 0, 0);
	}

	public int maxKeepers() {
		return maxKeepers;
	}

	/** Bumi and up burn spirit stones only. */
	public boolean stoneOnly() {
		return ordinal() >= BUMI.ordinal();
	}

	/** 1 (Kuning) to 5 (Abadi): the effective grade number of the Daya Tembus rule (24.2.10). */
	public int number() {
		return ordinal() + 1;
	}

	/** The ribbon colour (22.7). */
	public int color() {
		return color;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component label() {
		return Component.translatable("kultivator.formasi.grade." + id());
	}
}
