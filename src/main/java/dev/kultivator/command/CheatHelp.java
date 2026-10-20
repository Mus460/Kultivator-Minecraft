package dev.kultivator.command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * {@code /kultivasi uji} (and the hidden old alias {@code /kultivasi debug}) without arguments: every cheat command of
 * the mod, grouped. Each chip puts its command into the chat box (click = suggest), the hover shows the whole command.
 * A chip starting with "/" is an absolute command (for example the one-command Mode Kehancuran switch).
 */
final class CheatHelp {

	private static final String KEY = "kultivator.cheat.";

	private record Group(String id, List<String> chips) {
	}

	private static final List<Group> GROUPS = List.of(
			new Group("persiapan", List.of("maks", "ranah <ranah> [tahap] [tingkat]", "tur [detik]", "tur stop", "kit", "fill [pemain]",
					"reset [pemain]", "show <pemain>")),
			new Group("data", List.of("set realm <ranah> [tahap] [tingkat]", "set qi <n>", "set xp <n>", "set storedqi <n>",
					"set root <mutu> <elemen>", "set root_of <pemain> <mutu> <elemen>", "set dao <dao|none>", "set comprehension <0-100>",
					"set quality <mutu>", "addxp <n>", "deviation clear", "toxicity clear", "toxicity set <0-100>", "root reroll", "root replay",
					"dao reset", "dao comprehension <dao> <0-100>", "fondasi <ranah> <kualitas>", "bintang <n>", "perks show",
					"ascension start", "ascension progress", "ascension add <jenis> <n>", "ascension reset")),
			new Group("meditasi", List.of("enlighten", "site", "vein <0-200>", "vein grade", "urat cari", "urat tingkat <tingkat>",
					"tertutup <0-5>", "gua", "jimat <jenis|semua>", "jimat tingkat <0-10>", "anugerah", "jiwa", "jalan reset",
					"jalan bab <1-15>", "jalan ujian", "harian ulang", "harian selesai", "hud")),
			new Group("terobosan", List.of("breakthrough force", "breakthrough cancel", "tribulation test", "tribulasi <ranah> [lapis]",
					"kawal <0-3>", "heartdemon summon", "heartdemon pressure <0-200>", "heartdemon clear")),
			new Group("teknik", List.of("learn <id|all>", "forget <id|all>", "teknik semua", "teknik <id>", "teknik pamer [detik]",
					"teknik bebas nyala", "teknik penguasaan <id|semua> <0-4>", "teknik gerbang", "teknik audit", "teknik murid <nama|semua>",
					"teknik mastery <id|semua> <0-4>", "teknik cooldowns reset", "teknik lupakan <id|semua>", "teknik kekuatan",
					"teknik beban", "teknik feat <jenis> <n>", "boneka [n] [bos]", "boneka hapus", "dampak <id|semua>", "dampak berhenti",
					"aura <ranah>", "aura stop", "suara <kunci>")),
			new Group("kehancuran", List.of("/kehancuran semua nyala", "/kehancuran semua mati", "teknik kehancuran nyala",
					"teknik kehancuran mati", "kehancuran total info", "kehancuran total uji <bedrock|cairan|peti|portal>")),
			new Group("titah", List.of("pusaka_langit beri", "pusaka_langit bypass nyala", "pusaka_langit <pedang|tiang|bintang|gunung>",
					"pusaka_langit tingkat <1-9>", "pusaka_langit tingkat_tabel", "pusaka_langit pahat_cepat <radius>", "pusaka_langit semua",
					"pusaka_langit monumen daftar", "pusaka_langit cabut", "pusaka_langit beban", "pusaka_langit reset")),
			new Group("murid", List.of("murid summon", "murid xp <n>", "murid set realm <ranah>", "murid <nama|semua> maks",
					"murid <nama|semua> ranah <ranah>", "murid <nama|semua> tur", "murid <nama|semua> teknik", "murid <nama|semua> bintang <n>",
					"murid fisik <id>", "murid bakat <id>", "murid istimewa <id>", "murid warna <0-6>", "murid lipat <n>", "murid api <jenis>",
					"murid tempa <n>", "murid kunpeng", "murid siap", "murid bintang set <n>", "murid bencana mulai", "murid ikatan <0-100>",
					"murid teknik mastery <nama> <id> <0-4>")),
			new Group("sekte", List.of("aula renown <n>", "aula pk <n>", "aula calon datang", "aula calon perang", "aula level",
					"aula jenjang <0-5>", "aula pkmurid <n>", "aula bangun tingkat-set <0-9>", "aula bangun komponen selesai",
					"aula bangun bank <n>", "aula bangun rencana ulang", "event turnamen mulai", "event turnamen elit", "event perang mulai",
					"event hampa <lantai>", "event terlarang buka", "event misi selesai", "sekte templat <1-9>", "sekte tingkat <0-9>",
					"sekte sumber penuh", "sekte pamer <1-9>", "sekte info", "sekte naik <2-9>", "sekte bongkar", "sekte reset",
					"sekte addmember <nama>")),
			new Group("formasi", List.of("formasi pasang <id>", "formasi pola <id>", "formasi semua", "formasi daftar", "formasi status",
					"formasi daya <jam>", "formasi kas <n>", "formasi penjaga murid", "formasi tribulasi", "formasi hapus", "formasi musuh <n>",
					"formasi alarm", "formasi simpul", "formasi stats", "formasi verify", "formasi clear", "formation power <menit>",
					"formation clear")),
			new Group("dunia", List.of("elit <1-3>", "elit hapus", "elit info", "kejenuhan <n>", "kejenuhan show", "kejenuhan reset", "unsur",
					"beast xp <n>", "beast tier <0-3>", "perampok <1-4>", "perampok hapus", "perampok info", "sergap", "serbuan mulai",
					"serbuan data", "momen", "momen <id>", "momen semua", "momen berhenti", "momen info", "momen bendera", "alam <id>",
					"alam selesai", "alam info", "kristal <n>", "desa ikrar", "desa setia <0-100>", "desa upeti", "desa jarah", "desa poi")),
			new Group("abadi", List.of("abadi naik", "abadi masuk", "abadi pulang", "abadi batu <n>", "abadi pulau", "abadi info",
					"lelang sesi", "purba <kunpeng|zhulong|taotie>", "purba hapus", "purba info", "bencana <1-7>", "murka", "utusan <n>",
					"batin", "batin lahir", "batin hari <n>", "batin bencana <jenis>", "ciptaan <slot>", "ciptaan <slot> <daoA> <daoB> <bentuk>")),
			new Group("barang", List.of("alchemy xp <n>", "alchemy give <pil> <mutu>", "batu <mutu>", "herba <id|semua>", "inti <mutu>",
					"pil <id>", "relik <tabel>", "pusaka <jenis> <tingkat>", "pusaka natal", "tempa tingkat <n>", "tempa panas", "tempa selesai",
					"roh <0-3>", "cincin <0-4>")));

	/** Prints the grouped help; {@code root} is the alias that was typed ("kultivasi" or "cultivate"). */
	static int show(CommandSourceStack source, String root) {
		source.sendSuccess(() -> Component.translatable(KEY + "title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.translatable(KEY + "intro", "/" + root + " uji").withStyle(ChatFormatting.GRAY), false);
		for (Group group : GROUPS) {
			MutableComponent head = Component.translatable(KEY + "group." + group.id()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
					.append(Component.literal(" ").append(Component.translatable(KEY + "group." + group.id() + ".desc"))
							.withStyle(style -> style.withColor(ChatFormatting.GRAY).withBold(false)));
			source.sendSuccess(() -> head, false);
			MutableComponent chips = Component.literal(" ");
			boolean first = true;
			for (String chip : group.chips()) {
				if (!first) {
					chips.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
				}
				first = false;
				chips.append(chip(root, chip));
			}
			source.sendSuccess(() -> chips, false);
		}
		source.sendSuccess(() -> Component.translatable(KEY + "footer", "/" + root + " debug", "/" + root + " uji").withStyle(ChatFormatting.DARK_GRAY), false);
		return 1;
	}

	/** One clickable chip: the text as shown, the command up to its first argument placeholder put into the chat box. */
	private static Component chip(String root, String chip) {
		boolean absolute = chip.startsWith("/");
		String text = absolute ? chip.substring(1) : chip;
		int cut = text.length();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '<' || c == '[') {
				cut = i;
				break;
			}
		}
		String fixed = text.substring(0, cut).trim();
		boolean args = cut < text.length();
		String command = "/" + root + (absolute ? " " : " uji ") + fixed + (args ? " " : "");
		String shown = absolute ? "/" + root + " " + text : text;
		return Component.literal(shown).withStyle(style -> style.withColor(absolute ? ChatFormatting.RED : ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.SuggestCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(KEY + "hover", "/" + root + (absolute ? " " : " uji ") + text))));
	}

	private CheatHelp() {
	}
}
