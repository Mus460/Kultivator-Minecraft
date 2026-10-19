package dev.kultivator.batin;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Roh Tanah, the land spirit of the inner world (DESIGN 26.6.3): a name rolled from eight, a voice in chat (status, the
 * warning one outside day before an earthly calamity, the calamity's outcome) and the last {@link #LOG} events in
 * {@code extra.batin.log [{d, k, a, b}]}.
 * <p>
 * Sumber: Reverend Insanity ch. 409 "Managing the Blessed Land" (the land spirit represents the land and knows everything
 * in it). The names and lines are game design.
 */
public final class LandSpirit {

	public static final int NAMES = 8;
	public static final int LOG = 10;
	private static final int VOICE = 0x9FE8C0;

	/** "Roh Rubah Kecil" and the seven other names. */
	public static Component name(CompoundTag batin) {
		int index = Math.max(0, Math.min(NAMES - 1, batin.getIntOr("spirit", 0)));
		return Component.translatable("kultivator.batin.spirit.name." + index);
	}

	/** A chat line in the spirit's voice: "[name] line". */
	public static MutableComponent say(CompoundTag batin, Component line) {
		return Component.literal("[").withColor(VOICE).append(name(batin).copy().withStyle(ChatFormatting.BOLD)).append(Component.literal("] "))
				.append(line.copy().withColor(0xE8F4EC));
	}

	/** Appends an event ({@code kultivator.batin.log.<key>} with two numbers), keeping the last ten. */
	public static void log(CompoundTag batin, long day, String key, int a, int b) {
		ListTag log = InnerWorld.list(batin, "log");
		CompoundTag entry = new CompoundTag();
		entry.putLong("d", day);
		entry.putString("k", key);
		entry.putInt("a", a);
		entry.putInt("b", b);
		log.add(entry);
		while (log.size() > LOG) {
			log.remove(0);
		}
	}

	private LandSpirit() {
	}
}
