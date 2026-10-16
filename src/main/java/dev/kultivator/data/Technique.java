package dev.kultivator.data;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Every technique. The id is saved (learned techniques, selection), so never rename an id. The behaviour lives in the
 * technique package (phase 2); this enum only holds the rules from docs/DESIGN.md section 8.
 */
public enum Technique {
	CLOUD_STEP("cloud_step", null, Realm.QI_REFINING, 6, 10, Unlock.REALM, 0),
	QI_PALM("qi_palm", null, Realm.QI_REFINING, 10, 30, Unlock.REALM, 0),
	FLYING_SWORD("flying_sword", null, Realm.FOUNDATION, 2, 40, Unlock.REALM, 0),
	DIVINE_SENSE("divine_sense", null, Realm.NASCENT_SOUL, 30, 400, Unlock.REALM, 0),
	BUDDHA_PALM("buddha_palm", null, Realm.GOLDEN_CORE, 45, 240, Unlock.BOOK, 0),
	DHARMA_FORM("dharma_form", null, Realm.DEITY_TRANSFORMATION, 80, 1200, Unlock.REALM, 0),
	DAO_DOMAIN("dao_domain", null, Realm.DAO_UNION, 120, 1800, Unlock.REALM, 0),

	SWORD_QI("sword_qi", Dao.SWORD, Realm.GOLDEN_CORE, 20, 60, Unlock.DAO, 0),
	THOUSAND_SWORDS("thousand_swords", Dao.SWORD, Realm.NASCENT_SOUL, 90, 800, Unlock.BOOK, 30),
	FLAME_PALM("flame_palm", Dao.FIRE, Realm.GOLDEN_CORE, 20, 60, Unlock.DAO, 0),
	PHOENIX_FLAME("phoenix_flame", Dao.FIRE, Realm.NASCENT_SOUL, 80, 700, Unlock.BOOK, 30),
	FROST_LOTUS("frost_lotus", Dao.ICE, Realm.GOLDEN_CORE, 20, 80, Unlock.DAO, 0),
	GLACIER_PRISON("glacier_prison", Dao.ICE, Realm.NASCENT_SOUL, 70, 600, Unlock.BOOK, 30),
	THUNDER_PALM("thunder_palm", Dao.LIGHTNING, Realm.GOLDEN_CORE, 25, 80, Unlock.DAO, 0),
	HEAVENLY_THUNDER("heavenly_thunder", Dao.LIGHTNING, Realm.NASCENT_SOUL, 90, 800, Unlock.BOOK, 30),
	IRON_BODY("iron_body", Dao.BODY, Realm.GOLDEN_CORE, 25, 600, Unlock.DAO, 0),
	MOUNTAIN_STOMP("mountain_stomp", Dao.BODY, Realm.NASCENT_SOUL, 60, 400, Unlock.BOOK, 30),
	SPRING_OF_LIFE("spring_of_life", Dao.LIFE, Realm.GOLDEN_CORE, 30, 400, Unlock.DAO, 0),
	LIFE_DRAIN("life_drain", Dao.LIFE, Realm.NASCENT_SOUL, 60, 500, Unlock.BOOK, 30),
	SHADOW_STEP("shadow_step", Dao.SHADOW, Realm.GOLDEN_CORE, 20, 120, Unlock.DAO, 0),
	GALE("gale", Dao.SHADOW, Realm.NASCENT_SOUL, 50, 400, Unlock.BOOK, 30),
	SPACE_BLINK("space_blink", Dao.SPACE, Realm.GOLDEN_CORE, 25, 100, Unlock.DAO, 0),
	SPACE_PRISON("space_prison", Dao.SPACE, Realm.NASCENT_SOUL, 80, 700, Unlock.BOOK, 30);

	/** How a technique is learned. */
	public enum Unlock {
		/** Known as soon as the realm is reached. */
		REALM,
		/** Known when its Dao is chosen (and the realm is reached). */
		DAO,
		/** Learned from a vanilla enchanted book once realm (and comprehension, for Dao books) allow it. */
		BOOK
	}

	private final String id;
	private final @Nullable Dao dao;
	private final Realm minRealm;
	private final int qiCost;
	private final int cooldownTicks;
	private final Unlock unlock;
	private final double minComprehension;

	Technique(String id, @Nullable Dao dao, Realm minRealm, int qiCost, int cooldownTicks, Unlock unlock, double minComprehension) {
		this.id = id;
		this.dao = dao;
		this.minRealm = minRealm;
		this.qiCost = qiCost;
		this.cooldownTicks = cooldownTicks;
		this.unlock = unlock;
		this.minComprehension = minComprehension;
	}

	public String id() {
		return id;
	}

	/** Null for universal techniques. */
	public @Nullable Dao dao() {
		return dao;
	}

	public Realm minRealm() {
		return minRealm;
	}

	/** Qi per cast (for the flying sword: per second of flight). */
	public int qiCost() {
		return qiCost;
	}

	public int cooldownTicks() {
		return cooldownTicks;
	}

	public Unlock unlock() {
		return unlock;
	}

	/** Dao comprehension needed before its book can be understood (0 for none). */
	public double minComprehension() {
		return minComprehension;
	}

	public Component displayName() {
		return Component.translatable("kultivator.technique." + id);
	}

	public Component description() {
		return Component.translatable("kultivator.technique." + id + ".desc");
	}

	/** Null when unknown. */
	public static @Nullable Technique byId(String id) {
		for (Technique technique : values()) {
			if (technique.id.equals(id)) {
				return technique;
			}
		}
		return null;
	}
}
