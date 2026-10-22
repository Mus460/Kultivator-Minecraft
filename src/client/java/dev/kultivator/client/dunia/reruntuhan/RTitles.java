package dev.kultivator.client.dunia.reruntuhan;

import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.dunia.reruntuhan.penjara.RajaIblisEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

/**
 * The nameplates of the module's creatures (detail pass): a coloured name and, under it (the vanilla "below name"
 * line, {@code EntityRenderState.scoreText}), a short title that says what the creature is: the demon kings' epithet
 * and a "Murka" mark while enraged, the band of a floor guardian, the ruin of a stone guardian, the mirror shadow and
 * the ancestor's remnant soul. Plain text on the vanilla name tag: no extra draw calls beyond the tag itself.
 */
final class RTitles {

	/** Within this many blocks the bosses show their plate (the vanilla tag fades by itself when sneaking). */
	static final double BOSS_SQ = 40.0D * 40.0D;
	static final double GUARD_SQ = 28.0D * 28.0D;
	static final double SMALL_SQ = 12.0D * 12.0D;

	static final int KING_NAME = 0xFF6A5A;
	static final int KING_TITLE = 0xD9A07A;
	static final int GUARD_NAME = 0xFFD27A;
	static final int GUARD_TITLE = 0xBFAF8A;
	static final int MUTED = 0xA8A8B8;
	static final int[] GOLEM = {0x9FE0C0, 0xFFD54A, 0xC89BFF};

	private RTitles() {
	}

	static Component name(Component name, int color) {
		return name.copy().withColor(color);
	}

	static boolean enraged(int flags) {
		return (flags & RCreature.FLAG_ENRAGED) != 0;
	}

	private static MutableComponent withRage(MutableComponent title, int flags) {
		if (!enraged(flags)) {
			return title;
		}
		return title.append(Component.literal(" · ").withColor(MUTED))
				.append(Component.translatable("kultivator.reruntuhan.title.enraged").withColor(0xFF3030));
	}

	/** The epithet of a demon king. */
	static Component king(int kind, int flags) {
		String id = RajaIblisEntity.IDS[Mth.clamp(kind, 0, RajaIblisEntity.IDS.length - 1)];
		return withRage(Component.translatable("kultivator.reruntuhan.title.king." + id).withColor(KING_TITLE), flags);
	}

	/** A floor guardian: "Penjaga Lantai 30" and its band's trial. */
	static Component guardian(int kind, int flags) {
		return withRage(Component.translatable("kultivator.reruntuhan.title.guardian", Math.max(1, kind + 1) * 10).withColor(GUARD_TITLE), flags);
	}

	static Component golem(int kind, int flags) {
		int k = Mth.clamp(kind, 0, 2);
		return withRage(Component.translatable("kultivator.reruntuhan.title.golem." + k).withColor(GOLEM[k]), flags);
	}

	static Component lesser(int flags) {
		return withRage(Component.translatable("kultivator.reruntuhan.title.lesser").withColor(0xC08080), flags);
	}

	static Component shadow(int tier) {
		return Component.translatable(tier >= 3 ? "kultivator.reruntuhan.title.shadow.diri" : "kultivator.reruntuhan.title.shadow.cermin")
				.withColor(0xB49AE0);
	}

	static Component soul(int tier) {
		return Component.translatable("kultivator.reruntuhan.title.soul." + Mth.clamp(tier, 1, 3)).withColor(0xBFE8FF);
	}
}
