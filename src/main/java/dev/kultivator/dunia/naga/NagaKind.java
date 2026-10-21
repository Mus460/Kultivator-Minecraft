package dev.kultivator.dunia.naga;

import dev.kultivator.data.Realm;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The three wild Jiao of DESIGN 34.5.11 (a Jiao is a dragon that is not yet a true dragon). The ordinal is synced and
 * saved (the tamed Jiao's record), so never reorder.
 * <ul>
 * <li>{@link #AIR}: azure, over rivers and oceans; its breath is water (Slowness);</li>
 * <li>{@link #AWAN}: white jade, over mountains above y 140 and in dense Qi mist; its breath is wind (knockback);</li>
 * <li>{@link #PETIR}: purple-gold, only while the vanilla weather is a thunderstorm (read, never changed); its breath is a
 * lightning chain (particles only, never a LightningBolt).</li>
 * </ul>
 */
public enum NagaKind {
	AIR(0x3FA0E8, 0x9FE8FF, Realm.DEITY_TRANSFORMATION),
	AWAN(0xE8F4EC, 0xB8F0D8, Realm.DEITY_TRANSFORMATION),
	PETIR(0x8A4AE0, 0xFFD54A, Realm.DAO_UNION);

	/** The body colour (HUD, particles, boss bar tint). */
	public final int color;
	/** The breath and glow colour. */
	public final int glow;
	/** The realm a player needs to subdue this kind with the Selendang. */
	public final Realm subdueRealm;

	NagaKind(int color, int glow, Realm subdueRealm) {
		this.color = color;
		this.glow = glow;
		this.subdueRealm = subdueRealm;
	}

	/** "air", "awan", "petir": model ids ({@code fx/naga/head_<id>}), lang keys and commands. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** "Jiao Air" / "Jiao Awan" / "Jiao Petir". */
	public Component displayName() {
		return Component.translatable("entity.kultivator.naga_liar." + id());
	}

	public static NagaKind byOrdinal(int ordinal) {
		NagaKind[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}

	public static NagaKind byId(String id) {
		for (NagaKind kind : values()) {
			if (kind.id().equals(id)) {
				return kind;
			}
		}
		return AIR;
	}
}
