package dev.kultivator.beast;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.data.Realm;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.wolf.Wolf;

import java.util.UUID;

/**
 * What makes an animal a spirit beast: stored in the attachment {@code kultivator:spirit_beast} on the wolf or fox
 * (saved with it, synced to every client that sees it so the aura can be drawn). Immutable: every change replaces the
 * record with {@code setAttached}.
 *
 * @param owner the cultivator the beast is bound to
 * @param tier  0 (just awakened) to 3 by spirit XP alone; 4 to 6 only through the Kesengsaraan Binatang Roh (DESIGN 34.5.12)
 * @param xp    spirit XP gathered in total (never reset by an evolution)
 * @param kind  {@link #WOLF}, {@link #FOX} or {@link #JIAO} (a subdued Jiao, DESIGN 34.5.11: its record lives with its
 *              owner and in the Mutiara Naga Jinak, never on a wolf or fox)
 */
public record BeastData(UUID owner, int tier, double xp, int kind) {

	public static final int WOLF = 0;
	public static final int FOX = 1;
	/** A subdued Jiao (phase 34, module B): it starts at tier 3 and only climbs through the beast tribulation. */
	public static final int JIAO = 2;
	/**
	 * The last tier spirit XP reaches on its own (tiers 1-3 evolve by themselves). Kept at 3 so every reader that sizes an
	 * array by it (the phase-3 auras: fox tails, cinematic tiers) stays in bounds.
	 */
	public static final int MAX_TIER = 3;
	/** The highest tier of all (DESIGN 34.5.12): 4, 5 and 6 need the Kesengsaraan Binatang Roh. */
	public static final int TOP_TIER = 6;

	/** Total spirit XP needed for each tier (4 000 / 12 000 / 40 000 for the tribulation tiers, DESIGN 34.5.12). */
	public static final double[] TIER_XP = {0.0D, 100.0D, 400.0D, 1200.0D, 4000.0D, 12000.0D, 40000.0D};
	/** The owner's realm a beast needs to reach each tier: a beast never surpasses its master. */
	public static final Realm[] TIER_REALM = {Realm.MORTAL, Realm.MORTAL, Realm.GOLDEN_CORE, Realm.NASCENT_SOUL,
			Realm.DEITY_TRANSFORMATION, Realm.IMMORTAL_ASCENSION, Realm.TRUE_IMMORTAL};

	/** Every tier title key starts with this; a custom name with such a key was given by us, not by the player. */
	public static final String TITLE_PREFIX = "kultivator.beasts.title.";
	/**
	 * Tier 0 is just awakened (Awakened Wolf / Fox); the evolutions are Spirit, Moon Thunder / Three-Tailed, Heavenly /
	 * Nine-Tailed, then (DESIGN 34.5.12, keys in lang-src/dunia_naga) Star-Silver Wolf, Tiangou, Moon-Devouring Tiangou and
	 * Qingqiu Moon Fox, Heavenly Fox, Golden Nine-Tailed Heavenly Fox. A Jiao is a Jiao up to tier 3, then Horned Jiao,
	 * True Dragon and Yinglong.
	 */
	private static final String[] WOLF_TITLES = {TITLE_PREFIX + "wolf.awakened", TITLE_PREFIX + "wolf.spirit",
			TITLE_PREFIX + "wolf.moon_thunder", TITLE_PREFIX + "wolf.heavenly", TITLE_PREFIX + "wolf.star_silver",
			TITLE_PREFIX + "wolf.tiangou", TITLE_PREFIX + "wolf.moon_devourer"};
	private static final String[] FOX_TITLES = {TITLE_PREFIX + "fox.awakened", TITLE_PREFIX + "fox.spirit",
			TITLE_PREFIX + "fox.three_tailed", TITLE_PREFIX + "fox.nine_tailed", TITLE_PREFIX + "fox.qingqiu_moon",
			TITLE_PREFIX + "fox.heavenly", TITLE_PREFIX + "fox.golden_heavenly"};
	private static final String[] JIAO_TITLES = {TITLE_PREFIX + "jiao.jiao", TITLE_PREFIX + "jiao.jiao",
			TITLE_PREFIX + "jiao.jiao", TITLE_PREFIX + "jiao.jiao", TITLE_PREFIX + "jiao.horned",
			TITLE_PREFIX + "jiao.true_dragon", TITLE_PREFIX + "jiao.yinglong"};

	public static final Codec<BeastData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(BeastData::owner),
			Codec.INT.optionalFieldOf("tier", 0).forGetter(BeastData::tier),
			Codec.DOUBLE.optionalFieldOf("xp", 0.0D).forGetter(BeastData::xp),
			Codec.INT.optionalFieldOf("kind", WOLF).forGetter(BeastData::kind)
	).apply(instance, BeastData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, BeastData> STREAM_CODEC = StreamCodec.of(
			(buf, data) -> {
				buf.writeUUID(data.owner);
				buf.writeVarInt(data.tier);
				buf.writeDouble(data.xp);
				buf.writeVarInt(data.kind);
			},
			buf -> new BeastData(buf.readUUID(), buf.readVarInt(), buf.readDouble(), buf.readVarInt()));

	/** The attachment on the animal. Absent = an ordinary animal. */
	public static final AttachmentType<BeastData> TYPE = AttachmentRegistry.<BeastData>builder()
			.persistent(CODEC)
			.syncWith(STREAM_CODEC, AttachmentSyncPredicate.all())
			.buildAndRegister(Kultivator.id("spirit_beast"));

	public BeastData {
		if (owner == null) {
			owner = new UUID(0L, 0L);
		}
		tier = Math.max(0, Math.min(TOP_TIER, tier));
		xp = Double.isFinite(xp) ? Math.max(0.0D, Math.min(1.0E9D, xp)) : 0.0D;
		kind = kind == FOX ? FOX : kind == JIAO ? JIAO : WOLF;
	}

	/** Loads the class so the attachment is registered (called from {@code SpiritBeasts.init()}). */
	static void register() {
	}

	public BeastData withTier(int newTier) {
		return new BeastData(owner, newTier, xp, kind);
	}

	public BeastData withXp(double newXp) {
		return new BeastData(owner, tier, newXp, kind);
	}

	public boolean isWolf() {
		return kind == WOLF;
	}

	public boolean isFox() {
		return kind == FOX;
	}

	public boolean isJiao() {
		return kind == JIAO;
	}

	/** The evolution line id: "wolf", "fox" or "jiao" (DESIGN 34.4.3 {@code BeastEvolutionService.line}). */
	public String line() {
		return lineOf(kind);
	}

	public static String lineOf(int kind) {
		return kind == FOX ? "fox" : kind == JIAO ? "jiao" : "wolf";
	}

	public boolean ownedBy(Entity entity) {
		return entity != null && owner.equals(entity.getUUID());
	}

	/** Total XP of the next tier, or -1 at the top. */
	public double nextTierXp() {
		return tier >= TOP_TIER ? -1.0D : TIER_XP[tier + 1];
	}

	/** True when the next tier needs the beast tribulation (3 to 4, 4 to 5, 5 to 6). */
	public boolean nextNeedsTribulation() {
		return tier >= MAX_TIER && tier < TOP_TIER;
	}

	/** Translation key of the title for this kind at this tier (Spirit Wolf, Moon Thunder Wolf...). */
	public String titleKey() {
		return titleKey(kind, tier);
	}

	public Component title() {
		return Component.translatable(titleKey());
	}

	public static String titleKey(int kind, int tier) {
		int index = Math.max(0, Math.min(TOP_TIER, tier));
		return kind == FOX ? FOX_TITLES[index] : kind == JIAO ? JIAO_TITLES[index] : WOLF_TITLES[index];
	}

	/** {@link #WOLF} for a wolf, {@link #FOX} for a fox, -1 for anything that cannot become a spirit beast. */
	public static int kindOf(Entity entity) {
		if (entity instanceof Wolf) {
			return WOLF;
		}
		if (entity instanceof Fox) {
			return FOX;
		}
		return -1;
	}
}
