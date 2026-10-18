package dev.kultivator.elite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.data.Element;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * What makes a monster an elite demonic beast (DESIGN 19.3.1): the persistent, synced attachment {@code kultivator:elite}
 * on the mob, read by the server (skills, rewards, element factor) and by every client that sees it (aura, name plate).
 * Immutable; registered when this class loads ({@link EliteMonsters#init()}).
 *
 * @param tier    1 Siluman, 2 Binatang Iblis, 3 Raja Siluman
 * @param element the element id ({@link Element#id()})
 * @param realm   the realm ordinal of the nearest player when it appeared
 * @param affix   {@link #NONE}, {@link #WARD}, {@link #DEVOURER} or {@link #RECOVERY}
 */
public record EliteData(int tier, String element, int realm, int affix) {

	public static final int NONE = 0;
	/** Perisai Unsur / Elemental Ward. */
	public static final int WARD = 1;
	/** Penyerap Qi / Qi Devourer. */
	public static final int DEVOURER = 2;
	/** Pulih Cepat / Swift Recovery. */
	public static final int RECOVERY = 3;
	/** Affix ids as used in commands and translation keys (index = affix). */
	public static final String[] AFFIX_IDS = {"tanpa", "perisai_unsur", "penyerap_qi", "pulih_cepat"};
	/** Tier ids for translation keys (index = tier). */
	public static final String[] TIER_IDS = {"none", "siluman", "binatang_iblis", "raja_siluman"};

	public static final Codec<EliteData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("tier", 1).forGetter(EliteData::tier),
			Codec.STRING.optionalFieldOf("element", Element.EARTH.id()).forGetter(EliteData::element),
			Codec.INT.optionalFieldOf("realm", 1).forGetter(EliteData::realm),
			Codec.INT.optionalFieldOf("affix", NONE).forGetter(EliteData::affix)
	).apply(instance, EliteData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, EliteData> STREAM_CODEC = StreamCodec.of(
			(buf, data) -> {
				buf.writeVarInt(data.tier);
				buf.writeUtf(data.element);
				buf.writeVarInt(data.realm);
				buf.writeVarInt(data.affix);
			},
			buf -> new EliteData(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt()));

	/** The attachment on the mob. Absent = an ordinary monster. */
	public static final AttachmentType<EliteData> TYPE = AttachmentRegistry.<EliteData>builder()
			.persistent(CODEC)
			.syncWith(STREAM_CODEC, AttachmentSyncPredicate.all())
			.buildAndRegister(Kultivator.id("elite"));

	public EliteData {
		tier = Math.max(1, Math.min(3, tier));
		if (element == null || Element.byId(element) == null) {
			element = Element.EARTH.id();
		}
		realm = Math.max(0, Math.min(8, realm));
		affix = affix < NONE || affix > RECOVERY ? NONE : affix;
	}

	/** Loads the class so the attachment is registered. */
	static void register() {
	}

	/** The elite data of {@code entity}, null for anything that is not an elite. */
	public static @Nullable EliteData of(@Nullable Entity entity) {
		return entity == null ? null : entity.getAttached(TYPE);
	}

	public Element elementValue() {
		Element value = Element.byId(element);
		return value == null ? Element.EARTH : value;
	}

	/** "Siluman" / "Binatang Iblis" / "Raja Siluman". */
	public Component tierName() {
		return tierName(tier);
	}

	public static Component tierName(int tier) {
		return Component.translatable("kultivator.combat.tier." + TIER_IDS[Math.max(1, Math.min(3, tier))]);
	}

	/** "Binatang Iblis · Api". */
	public Component title() {
		return Component.translatable("kultivator.combat.title", tierName(), elementValue().displayName());
	}

	public static Component affixName(int affix) {
		return Component.translatable("kultivator.combat.affix." + AFFIX_IDS[Math.max(0, Math.min(RECOVERY, affix))]);
	}
}
