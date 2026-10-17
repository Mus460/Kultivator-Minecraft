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
 * @param tier  0 (just awakened) to 3
 * @param xp    spirit XP gathered in total (never reset by an evolution)
 * @param kind  {@link #WOLF} or {@link #FOX}
 */
public record BeastData(UUID owner, int tier, double xp, int kind) {

	public static final int WOLF = 0;
	public static final int FOX = 1;
	public static final int MAX_TIER = 3;

	/** Total spirit XP needed for each tier. */
	public static final double[] TIER_XP = {0.0D, 100.0D, 400.0D, 1200.0D};
	/** The owner's realm a beast needs to reach each tier: a beast never surpasses its master. */
	public static final Realm[] TIER_REALM = {Realm.MORTAL, Realm.MORTAL, Realm.GOLDEN_CORE, Realm.NASCENT_SOUL};

	/** Every tier title key starts with this; a custom name with such a key was given by us, not by the player. */
	public static final String TITLE_PREFIX = "kultivator.beasts.title.";
	/** Tier 0 is just awakened (Awakened Wolf / Fox); the evolutions are Spirit, Moon Thunder / Three-Tailed, Heavenly / Nine-Tailed. */
	private static final String[] WOLF_TITLES = {TITLE_PREFIX + "wolf.awakened", TITLE_PREFIX + "wolf.spirit",
			TITLE_PREFIX + "wolf.moon_thunder", TITLE_PREFIX + "wolf.heavenly"};
	private static final String[] FOX_TITLES = {TITLE_PREFIX + "fox.awakened", TITLE_PREFIX + "fox.spirit",
			TITLE_PREFIX + "fox.three_tailed", TITLE_PREFIX + "fox.nine_tailed"};

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
		tier = Math.max(0, Math.min(MAX_TIER, tier));
		xp = Double.isFinite(xp) ? Math.max(0.0D, Math.min(1.0E9D, xp)) : 0.0D;
		kind = kind == FOX ? FOX : WOLF;
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

	public boolean ownedBy(Entity entity) {
		return entity != null && owner.equals(entity.getUUID());
	}

	/** Total XP of the next tier, or -1 at the top. */
	public double nextTierXp() {
		return tier >= MAX_TIER ? -1.0D : TIER_XP[tier + 1];
	}

	/** Translation key of the title for this kind at this tier (Spirit Wolf, Moon Thunder Wolf...). */
	public String titleKey() {
		return titleKey(kind, tier);
	}

	public Component title() {
		return Component.translatable(titleKey());
	}

	public static String titleKey(int kind, int tier) {
		int index = Math.max(0, Math.min(MAX_TIER, tier));
		return kind == FOX ? FOX_TITLES[index] : WOLF_TITLES[index];
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
