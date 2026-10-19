package dev.kultivator.client.disciple;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.client.disciple.power.PhysiqueVisuals;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.path.StarTitles;
import dev.kultivator.disciple.power.PowerIds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

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
	/** Extra marks other client modules put on a disciple's name tag (phase 6; e.g. module D's red seal). */
	private static final List<BiFunction<DiscipleEntity, MutableComponent, MutableComponent>> NAME_DECORATORS = new ArrayList<>();

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
		return skinByIndex(disciple.skinIndex(), disciple.slimSkin());
	}

	/** One of the nine default characters by index (any int, wrapped) with slim or wide arms (phase 8: rogue cultivators). */
	public static PlayerSkin skinByIndex(int index, boolean slim) {
		int wrapped = Math.floorMod(index, CHARACTERS.length);
		return slim ? SLIM[wrapped] : WIDE[wrapped];
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

	/**
	 * The name tag (phase 6, module C): a disciple reads "[Dalam] Lin Feng · Inti Emas ★31" with the rank prefix in its
	 * trim colour, the name in its soul colour and the stars' title (module D's {@link StarTitles}); a candidate reads
	 * "Calon · ???", rivals, phantoms and Divine Clones have their own lines. Other modules may add marks
	 * ({@link #addNameDecorator}).
	 */
	public static Component nameTag(DiscipleEntity disciple) {
		Rank rank = rank(disciple);
		int realmColor = rank.realm().color();
		MutableComponent tag = switch (disciple.syncedRole()) {
			case CANDIDATE -> Component.translatable("kultivator.dpower.tag.candidate").withColor(0xE8E0C8);
			case RIVAL -> Component.translatable("kultivator.dpower.tag.rival", name(disciple), rankShort(rank)).withColor(0xFF9A7A);
			case PHANTOM -> Component.translatable("kultivator.dpower.tag.phantom", name(disciple), rankShort(rank)).withColor(0xB08AD8);
			case CLONE -> Component.translatable("kultivator.dpower.tag.clone", name(disciple)).withColor(0xBFE8FF);
			case DISCIPLE -> {
				int talent = disciple.syncedTalent();
				int soul = PowerIds.COLOUR_RGB[Mth.clamp(DiscipleEntity.talentSoulColor(talent), 0, PowerIds.COLOUR_RGB.length - 1)];
				int sectRank = Mth.clamp(disciple.syncedSectRank(), 0, PhysiqueVisuals.RANK_TRIM.length - 1);
				int stars = disciple.syncedStars();
				Component prefix = Component.translatable("kultivator.dpower.tag.rank." + sectRank).withColor(PhysiqueVisuals.RANK_TRIM[sectRank]);
				Component starLine = stars > 0 ? Component.literal(" ").append(StarTitles.title(stars).copy().withColor(0xFFE27A)) : Component.empty();
				// An explicit name colour (the gold of Nomor Satu Daftar Kuat, DESIGN 20.3.12) wins over the soul colour.
				Component custom = disciple.getCustomName();
				MutableComponent shown = name(disciple).copy();
				if (custom == null || custom.getStyle().getColor() == null) {
					shown = shown.withColor(soul);
				}
				yield Component.translatable("kultivator.dpower.tag.line", prefix, shown,
						rankShort(rank).withColor(realmColor), starLine).withColor(realmColor);
			}
		};
		if (disciple.syncedActivity() == DiscipleEntity.ACTIVITY_FORMATION_KEEPER) {
			// Phase 7c (DESIGN 24.13 item 7): a formation keeper at its post.
			tag = tag.append(Component.translatable("kultivator.spine7c.keeper_tag").withColor(0x9FE0C0));
		} else if (disciple.syncedActivity() == DiscipleEntity.ACTIVITY_FORGE_ASSIST) {
			// Phase 8 (DESIGN 25.2 item 8): a forge assistant at the Earthfire Forge.
			tag = tag.append(Component.translatable("kultivator.spine8.forge_tag").withColor(0xFFB070));
		}
		for (BiFunction<DiscipleEntity, MutableComponent, MutableComponent> decorator : NAME_DECORATORS) {
			MutableComponent decorated = decorator.apply(disciple, tag);
			if (decorated != null) {
				tag = decorated;
			}
		}
		return tag;
	}

	/** Lets a client module add a mark to every disciple's name tag (called once from its client init). */
	public static void addNameDecorator(BiFunction<DiscipleEntity, MutableComponent, MutableComponent> decorator) {
		NAME_DECORATORS.add(decorator);
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
