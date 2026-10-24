package dev.kultivator.dunia.tempat.kompleks;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The chat cards of the villages and sect compounds (DESIGN 38.C.2.3, 38.C.3.3; owner 38DS): a framed card in the chat
 * with a heading, lines and clickable chips that run the player commands {@code /kultivasi kampung ...} and
 * {@code /kultivasi kompleks ...} (the same way the Sword Duel sends its accept chips). The talk screen of the NPCs is
 * 38ST's; these cards carry the place's own offers (sparring, the sect and village markets, the young master's
 * challenge, the patriarch's invitation) that arise from walking into a spot, not from a talk.
 */
public final class Kartu {

	/** Card frame colour (old gold) and the body text colour (parchment). */
	public static final int BINGKAI = 0xC9A24A;
	public static final int TEKS = 0xE8D9A8;

	/** A clickable chip {@code [label]} that runs {@code command} (with a leading slash), with a hover line. */
	public static MutableComponent chip(Component label, String command, Component hover, ChatFormatting colour) {
		return Component.literal("[").append(label).append("]").withStyle(style -> style.withColor(colour)
				.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	/** Sends a card: a gold frame line with {@code judul}, the {@code baris} lines, then the chips on one line. */
	public static void kirim(ServerPlayer player, Component judul, List<Component> baris, List<Component> chips) {
		player.sendSystemMessage(Component.literal("══ ").withColor(BINGKAI).append(judul.copy().withColor(BINGKAI))
				.append(Component.literal(" ══").withColor(BINGKAI)));
		for (Component line : baris) {
			player.sendSystemMessage(Component.literal("  ").append(line.copy().withColor(TEKS)));
		}
		if (!chips.isEmpty()) {
			MutableComponent row = Component.literal("  ");
			for (int i = 0; i < chips.size(); i++) {
				if (i > 0) {
					row.append(Component.literal("  "));
				}
				row.append(chips.get(i));
			}
			player.sendSystemMessage(row);
		}
	}

	/** A speaker's line: {@code <name>: "<text>"} in the card's colours. */
	public static Component ucapan(Component nama, Component teks) {
		return Component.empty().append(nama.copy().withStyle(ChatFormatting.GOLD)).append(Component.literal(": “"))
				.append(teks).append(Component.literal("”"));
	}

	private Kartu() {
	}
}
