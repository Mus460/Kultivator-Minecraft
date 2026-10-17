package dev.kultivator.client.disciple;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.HashMap;
import java.util.Map;

/**
 * How a disciple looks on this client, read from its synced entity data: rank, Dao, root colour, activity flags, its
 * default player skin and the seated meditation blend.
 */
public final class DiscipleLook {

	/** Activity flags of {@link DiscipleEntity#syncedFlags()}. */
	public static final int MEDITATING = DiscipleEntity.FLAG_MEDITATING;
	public static final int BREAKING_THROUGH = DiscipleEntity.FLAG_BREAKING_THROUGH;
	public static final int FIGHTING = DiscipleEntity.FLAG_FIGHTING;
	public static final int DEVIATING = DiscipleEntity.FLAG_DEVIATING;

	/** The nine default characters; each exists with wide and slim arms (vanilla {@code DefaultPlayerSkin}). */
	private static final String[] CHARACTERS = {"alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"};
	private static final PlayerSkin[] WIDE = new PlayerSkin[CHARACTERS.length];
	private static final PlayerSkin[] SLIM = new PlayerSkin[CHARACTERS.length];
	/** Ticks the seated pose takes to blend in or out. */
	private static final float BLEND_TICKS = 8.0F;
	private static final Map<Integer, Blend> BLENDS = new HashMap<>();

	static {
		for (int i = 0; i < CHARACTERS.length; i++) {
			WIDE[i] = skin("wide", CHARACTERS[i], PlayerModelType.WIDE);
			SLIM[i] = skin("slim", CHARACTERS[i], PlayerModelType.SLIM);
		}
	}

	private static PlayerSkin skin(String folder, String name, PlayerModelType model) {
		return new PlayerSkin(new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("entity/player/" + folder + "/" + name)), null, null,
				model, true);
	}

	/** Whether it is seated and since when (entity age in ticks). */
	private static final class Blend {
		boolean on;
		float changedAt;

		Blend(boolean on, float changedAt) {
			this.on = on;
			this.changedAt = changedAt;
		}

		float value(float now) {
			float t = Mth.clamp((now - changedAt) / BLEND_TICKS, 0.0F, 1.0F);
			return on ? t : 1.0F - t;
		}
	}

	// ================================================================ synced data

	/** One of the nine default characters ({@code skinIndex}) with slim or wide arms. */
	public static PlayerSkin skin(DiscipleEntity disciple) {
		int index = Math.floorMod(disciple.skinIndex(), CHARACTERS.length);
		return disciple.slimSkin() ? SLIM[index] : WIDE[index];
	}

	public static int realm(DiscipleEntity disciple) {
		return Mth.clamp(disciple.syncedRealm(), 0, Realm.values().length - 1);
	}

	public static Rank rank(DiscipleEntity disciple) {
		return new Rank(Realm.byOrdinal(realm(disciple)), disciple.syncedLayer(), disciple.syncedStage());
	}

	/** The chosen Dao's ordinal, -1 for none. */
	public static int dao(DiscipleEntity disciple) {
		int dao = disciple.syncedDao();
		return dao >= 0 && dao < Dao.COUNT ? dao : -1;
	}

	/** RGB of the root's main element (pale blue-white when unknown). */
	public static int rootColor(DiscipleEntity disciple) {
		int color = disciple.syncedRootColor() & 0xFFFFFF;
		return color == 0 ? Element.mainColor(0) : color;
	}

	public static int flags(DiscipleEntity disciple) {
		return disciple.syncedFlags();
	}

	public static boolean has(DiscipleEntity disciple, int flag) {
		return (flags(disciple) & flag) != 0;
	}

	/** Robe and aura tint: the Dao colour once a Dao is chosen, the realm colour before. */
	public static int color(int realm, int dao) {
		Dao chosen = Dao.byOrdinal(dao);
		return chosen != null ? chosen.color() : Realm.byOrdinal(realm).color();
	}

	// ================================================================ names

	/** The disciple's own name (its custom name; the entity type name when it has none). */
	public static Component name(Entity disciple) {
		return disciple.getName();
	}

	/** "Golden Core Late" style rank for name tags (Mortal: the realm only). */
	public static MutableComponent rankShort(Rank rank) {
		Realm realm = rank.realm();
		if (realm.isMortal()) {
			return realm.displayName().copy();
		}
		if (realm.layers() > 1) {
			return Component.translatable("kultivator.discipleui.rank_short_layered", realm.displayName(), rank.layer() + 1, rank.stageName());
		}
		return Component.translatable("kultivator.discipleui.rank_short", realm.displayName(), rank.stageName());
	}

	/** "&lt;name&gt; · &lt;rank&gt;" in the realm colour. */
	public static Component nameTag(DiscipleEntity disciple) {
		Rank rank = rank(disciple);
		return Component.translatable("kultivator.discipleui.nametag", name(disciple), rankShort(rank)).withColor(rank.realm().color());
	}

	// ================================================================ seated pose

	/** True when the disciple sits cross-legged: meditating or breaking through, on its own feet. */
	public static boolean seated(DiscipleEntity disciple) {
		return disciple.isAlive() && !disciple.isPassenger() && !disciple.isSwimming() && (flags(disciple) & (MEDITATING | BREAKING_THROUGH)) != 0;
	}

	/** 0..1 eased strength of the seated pose now (blends in and out over a few ticks). */
	public static float seatStrength(DiscipleEntity disciple, float partialTicks) {
		boolean on = seated(disciple);
		float now = disciple.tickCount + partialTicks;
		Blend blend = BLENDS.get(disciple.getId());
		if (blend == null) {
			blend = new Blend(on, now - BLEND_TICKS);
			BLENDS.put(disciple.getId(), blend);
		} else if (blend.on != on) {
			float current = blend.value(now);
			blend.on = on;
			blend.changedAt = now - (on ? current : 1.0F - current) * BLEND_TICKS;
		} else if (now < blend.changedAt) {
			blend.changedAt = now - BLEND_TICKS;
		}
		float t = blend.value(now);
		return t * t * (3.0F - 2.0F * t);
	}

	/** Forgets disciples that are gone (called now and then from the client tick). */
	public static void cleanup(ClientLevel level) {
		BLENDS.keySet().removeIf(id -> {
			Entity entity = level.getEntity(id);
			return !(entity instanceof DiscipleEntity) || entity.isRemoved();
		});
	}

	public static void clear() {
		BLENDS.clear();
	}

	private DiscipleLook() {
	}
}
