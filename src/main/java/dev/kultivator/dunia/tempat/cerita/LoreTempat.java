package dev.kultivator.dunia.tempat.cerita;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Catatan Tempat (DESIGN 38.S.7): the lore of the capital and its districts and guilds, villages and clans, NPC sect
 * compounds, caves, natural sites and the roads. The texts live in {@code lang-src/tempat_lore.*} (keys
 * {@code kultivator.lore.<id>.judul|<n>|tabel.<n>|sumber}); {@link LoreIndeks} (generated with them) says how many
 * paragraphs each entry has, whether it has a table (one row picked per site: a village's own legend, a compound's
 * founder tale, a hermit's reason) and which filler its {@code %s} takes.
 *
 * <p>A player learns an entry the first time they discover a site of its kind ({@code DuniaEvents.SITE_DISCOVERED}) or
 * stand inside a place or capital district (one {@link TempatApi#tempatDi} look-up per player every 200 ticks, from
 * {@link Cerita}): the title and the first lines go to chat with a click to read the rest. {@code /kultivasi legenda}
 * lists what they know, {@code /kultivasi legenda <id>} reads one; in a
 * conversation the effect {@code lore <id>} teaches one (a speaker telling the tale). Player compound key {@code lore}: entry id -> site id it was learned at (picks the table row and the
 * filler later). Text only: nothing here changes a block or spawns anything.
 */
public final class LoreTempat {

	/** Ticks between two place look-ups of one player (spread by {@link Cerita}). */
	static final long SELA_TICK = 200L;
	private static final String KUNCI = "lore";
	private static final int WARNA_JUDUL = 0xE8C890;
	private static final int WARNA_TEKS = 0xC8C0B0;
	private static final int WARNA_SUMBER = 0x8A8A9A;

	static void init() {
		Akibat.daftar("lore", (p, a) -> {
			if (!a.isEmpty() && LoreIndeks.ENTRI.containsKey(a.get(0))) {
				// a conversation teaches the entry (title, first lines and a click to read it all); nothing when known
				pelajari(p, a.get(0), TempatDialog.situs(p), true);
			}
		});
		DuniaEvents.SITE_DISCOVERED.register((player, site) -> {
			if (CeritaConfig.jalan()) {
				for (String id : idUntuk(site.kind(), null)) {
					pelajari(player, id, site.id(), true);
				}
			}
		});
		DuniaCommands.player("legenda", () -> Commands.literal("legenda")
				.executes(LoreTempat::daftar)
				.then(Commands.argument("id", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(dikenal(ctx.getSource().getPlayer()), b))
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							String id = StringArgumentType.getString(ctx, "id");
							if (!CeritaConfig.jalan()) {
								ctx.getSource().sendFailure(Component.translatable("kultivator.cerita.mati"));
								return 0;
							}
							if (!LoreIndeks.ENTRI.containsKey(id)) {
								ctx.getSource().sendFailure(Component.translatable("kultivator.lore.ui.tidak_ada", id));
								return 0;
							}
							if (situs(player, id) == null) {
								ctx.getSource().sendFailure(Component.translatable("kultivator.lore.ui.belum"));
								return 0;
							}
							baca(player, id);
							return 1;
						})));
	}

	/** From {@link Cerita#tick}: the place (and capital district) the player stands in teaches its entries. */
	static void periksa(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		TempatInfo here = TempatApi.tempatDi(level, player.blockPosition());
		if (here == null) {
			return;
		}
		for (String id : idUntuk(here.jenis(), here.distrik())) {
			pelajari(player, id, here.situsId(), true);
		}
	}

	/**
	 * The entries a place kind (and capital district) teaches, most general first: the capital itself before its district,
	 * the Guild Quarter also teaches the three guilds.
	 */
	static List<String> idUntuk(String kind, @Nullable String distrik) {
		List<String> out = new ArrayList<>();
		if (kind == null) {
			return out;
		}
		switch (kind) {
			case "kota/kota_besar" -> {
				out.add("kota_besar");
				if (distrik != null && LoreIndeks.ENTRI.containsKey("kota_besar." + distrik)) {
					out.add("kota_besar." + distrik);
					if ("gilda".equals(distrik)) {
						out.add("gilda.alkemis");
						out.add("gilda.pandai");
						out.add("gilda.kultivator");
					}
				}
			}
			case "kampung/dusun", "kampung/desa", "kampung/desa_besar" -> out.add("kampung");
			case "kampung/klan" -> {
				out.add("kampung");
				out.add("kampung.klan");
			}
			case "sekte/kompleks" -> out.add("kompleks");
			case "pos/tonggak" -> out.add("jalan.tonggak");
			case "pos/kuil_jalan" -> out.add("jalan.kuil");
			case "pos/singgah", "pos/singgah_besar" -> out.add("jalan.singgah");
			case "pos/dagang" -> out.add("jalan.dagang");
			case "pos/kemah_begal" -> out.add("jalan.kemah_begal");
			case "pos/penyeberangan" -> out.add("jalan.penyeberangan");
			default -> {
				// gua/<x>, situs/<x> and kota_tema/<x> map one to one (gua.pertapa, situs.puncak_petir, kota_tema.oasis, ...)
				String id = kind.replace('/', '.');
				if ((kind.startsWith("gua/") || kind.startsWith("situs/") || kind.startsWith("kota_tema/")) && LoreIndeks.ENTRI.containsKey(id)) {
					out.add(id);
				}
			}
		}
		return out;
	}

	/** Records {@code id} as known (learned at {@code site}); the first time, tells the player (title, first lines, a click). */
	static void pelajari(ServerPlayer player, String id, long site, boolean beritahu) {
		LoreIndeks.Entri entri = LoreIndeks.ENTRI.get(id);
		if (entri == null || situs(player, id) != null) {
			return;
		}
		CeritaData data = CeritaData.get(player.level().getServer());
		CompoundTag record = data.player(player.getUUID());
		CompoundTag known = record.getCompoundOrEmpty(KUNCI);
		known.putLong(id, site);
		record.put(KUNCI, known);
		data.touch();
		if (!beritahu) {
			return;
		}
		Msg.chat(player, Component.translatable("kultivator.lore.ui.baru", Component.translatable(base(id) + ".judul")
				.withColor(WARNA_JUDUL)).withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(baris(player, id, entri, 0, site).withColor(WARNA_TEKS).withStyle(ChatFormatting.ITALIC));
		player.sendSystemMessage(tombol(id, "kultivator.lore.ui.baca"));
	}

	/** Sends the whole entry: title, the site's table row, every paragraph, the Sumber line. */
	static void baca(ServerPlayer player, String id) {
		LoreIndeks.Entri entri = LoreIndeks.ENTRI.get(id);
		if (entri == null) {
			return;
		}
		Long site = situs(player, id);
		long at = site == null ? 0L : site;
		player.sendSystemMessage(Component.translatable("kultivator.lore.ui.judul", Component.translatable(base(id) + ".judul"))
				.withColor(WARNA_JUDUL).withStyle(ChatFormatting.BOLD));
		int lines = entri.paragraf() + (entri.tabel() > 0 ? 1 : 0);
		for (int i = 0; i < lines; i++) {
			player.sendSystemMessage(baris(player, id, entri, i, at).withColor(WARNA_TEKS));
		}
		if (entri.sumber()) {
			player.sendSystemMessage(Component.translatable(base(id) + ".sumber").withColor(WARNA_SUMBER).withStyle(ChatFormatting.ITALIC));
		}
	}

	/** Line {@code i} of an entry: the table row first (when it has one), then paragraphs 1..n. */
	private static MutableComponent baris(ServerPlayer player, String id, LoreIndeks.Entri entri, int i, long site) {
		String key;
		if (entri.tabel() > 0) {
			key = i == 0 ? base(id) + ".tabel." + baris(site, id, entri.tabel()) : base(id) + "." + i;
		} else {
			key = base(id) + "." + (i + 1);
		}
		if (entri.isian().isEmpty()) {
			return Component.translatable(key);
		}
		return Component.translatable(key, isian(player, entri.isian(), site));
	}

	/** The table row of a site: stable per site and entry, different between neighbouring villages. */
	static int baris(long site, String id, int rows) {
		long h = site * 0x9E3779B97F4A7C15L ^ id.hashCode() * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (int) Math.floorMod(h, (long) rows);
	}

	private static Component isian(ServerPlayer player, String name, long site) {
		if ("sekte_situs".equals(name)) {
			String sect = site == 0L ? null : KompleksApi.sekteDi(site);
			return Isian.one(player, site, sect == null ? "sekte_terdekat" : "sekte:" + sect);
		}
		return Isian.one(player, site, name);
	}

	private static int daftar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!CeritaConfig.jalan()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.cerita.mati"));
			return 0;
		}
		List<String> known = dikenal(player);
		if (known.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.lore.ui.kosong").withStyle(ChatFormatting.GRAY));
			return 1;
		}
		Msg.chat(player, Component.translatable("kultivator.lore.ui.daftar", known.size(), LoreIndeks.ENTRI.size())
				.withStyle(ChatFormatting.GOLD));
		for (String id : known) {
			player.sendSystemMessage(tombol(id, "kultivator.lore.ui.butir"));
		}
		return known.size();
	}

	private static MutableComponent tombol(String id, String key) {
		return Component.translatable(key, Component.translatable(base(id) + ".judul")).withStyle(style -> style
				.withColor(ChatFormatting.YELLOW).withClickEvent(new ClickEvent.RunCommand("/kultivasi legenda " + id)));
	}

	/** Entries this player knows, in index order. */
	static List<String> dikenal(@Nullable ServerPlayer player) {
		List<String> out = new ArrayList<>();
		if (player == null) {
			return out;
		}
		CompoundTag known = CeritaData.get(player.level().getServer()).player(player.getUUID()).getCompoundOrEmpty(KUNCI);
		for (String id : new java.util.TreeSet<>(LoreIndeks.ENTRI.keySet())) {
			if (known.contains(id)) {
				out.add(id);
			}
		}
		return out;
	}

	private static @Nullable Long situs(ServerPlayer player, String id) {
		UUID uuid = player.getUUID();
		CompoundTag known = CeritaData.get(player.level().getServer()).player(uuid).getCompoundOrEmpty(KUNCI);
		return known.contains(id) ? known.getLongOr(id, 0L) : null;
	}

	private static String base(String id) {
		return "kultivator.lore." + id;
	}

	private LoreTempat() {
	}
}
