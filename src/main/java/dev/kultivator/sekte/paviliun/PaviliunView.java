package dev.kultivator.sekte.paviliun;

import dev.kultivator.data.Rank;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.HallData;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * The two screens of module A as data (DESIGN 36.2.5 "UI"): the Rak Kitab page (the shelf, its capacity, the readers
 * and their progress, what the hand holds) and the Makam Leluhur page (the graves with their epitaphs, the Kitab
 * Silsilah, the altar's tablets and blessing, the next Qingming), and the server side of every choice made on them
 * ({@link PaviliunPayloads.Act}), checked again here: only the master of their own sect, only while the module runs.
 */
final class PaviliunView {

	private static final int LINEAGE_SHOWN = 60;

	static void open(ServerPlayer player, int page, boolean open) {
		UUID owner = player.getUUID();
		CompoundTag data = page == PaviliunPayloads.PAGE_RAK ? rak(player, owner) : makam(player.level().getServer(), owner);
		PaviliunPayloads.view(player, page, open, data);
	}

	static void onAct(ServerPlayer player, String op, String arg) {
		if (!Paviliun.active()) {
			return;
		}
		UUID owner = player.getUUID();
		if (Halls.hallOf(owner) == null) {
			Msg.actionBar(player, Component.translatable("kultivator.sekte.paviliun.no_hall").withStyle(ChatFormatting.YELLOW));
			return;
		}
		MinecraftServer server = player.level().getServer();
		switch (op) {
			case PaviliunPayloads.OP_OPEN_RAK -> open(player, PaviliunPayloads.PAGE_RAK, true);
			case PaviliunPayloads.OP_OPEN_MAKAM -> open(player, PaviliunPayloads.PAGE_MAKAM, true);
			case PaviliunPayloads.OP_DEPOSIT -> {
				String why = RakKitab.deposit(player, player.getMainHandItem());
				if (why != null) {
					Msg.actionBar(player, Component.translatable(why, RakKitab.capacity(owner)).withStyle(ChatFormatting.YELLOW));
				}
				open(player, PaviliunPayloads.PAGE_RAK, false);
			}
			case PaviliunPayloads.OP_WITHDRAW -> {
				try {
					RakKitab.withdraw(player, Integer.parseInt(arg));
				} catch (NumberFormatException ignored) {
					// a stale screen: just refresh it
				}
				open(player, PaviliunPayloads.PAGE_RAK, false);
			}
			case PaviliunPayloads.OP_ENSHRINE -> {
				UUID soul = parse(arg);
				if (soul != null) {
					AltarLeluhur.enshrine(player, soul);
				}
				open(player, PaviliunPayloads.PAGE_MAKAM, false);
			}
			case PaviliunPayloads.OP_QINGMING -> {
				String why = Upacara.startQingming(server, owner, false);
				if (why != null) {
					Msg.actionBar(player, Component.translatable(why, Upacara.daysLeft(server, owner)).withStyle(ChatFormatting.YELLOW));
				}
				open(player, PaviliunPayloads.PAGE_MAKAM, false);
			}
			default -> {
			}
		}
	}

	// ================================================================== Rak Kitab

	static CompoundTag rak(ServerPlayer player, UUID owner) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("capacity", RakKitab.capacity(owner));
		tag.putInt("level", Halls.level(owner));
		tag.putInt("readers_max", Paviliun.config().pembacaMaks);
		tag.putBoolean("library", SectBuild.anchor(owner, "library") != null);
		tag.putInt("learned", PaviliunData.count(owner, PaviliunData.LEARNED));
		ListTag books = new ListTag();
		for (PaviliunData.Kitab k : PaviliunData.rack(owner)) {
			CompoundTag b = new CompoundTag();
			b.putInt("id", k.id());
			b.putString("kind", k.kind());
			b.store("name", ComponentSerialization.CODEC, k.name());
			b.putString("by", k.by());
			b.putLong("day", k.day());
			ListTag specs = new ListTag();
			for (String id : k.specs()) {
				TechniqueSpec spec = TechniqueRegistry.byId(id);
				if (spec == null) {
					continue;
				}
				CompoundTag s = new CompoundTag();
				s.putString("id", id);
				s.store("name", ComponentSerialization.CODEC, spec.displayName());
				s.putInt("grade", spec.grade().tier());
				s.putInt("rgb", spec.grade().ribbon());
				s.store("grade_name", ComponentSerialization.CODEC, spec.grade().displayName());
				specs.add(s);
			}
			b.put("specs", specs);
			books.add(b);
		}
		tag.put("books", books);
		ListTag readers = new ListTag();
		for (DiscipleEntity d : BelajarMandiri.readers(owner)) {
			CompoundTag r = new CompoundTag();
			r.store("name", ComponentSerialization.CODEC, d.getName());
			String id = PaviliunData.studying(d.data());
			TechniqueSpec spec = id.isEmpty() ? null : TechniqueRegistry.byId(id);
			if (spec != null) {
				r.store("spec", ComponentSerialization.CODEC, spec.displayName());
				r.putInt("rgb", spec.grade().ribbon());
				double rate = BelajarMandiri.rate(owner, d.data());
				double left = BelajarMandiri.need(spec) * (1.0D - BelajarMandiri.progress(d.data()));
				r.putInt("eta", (int) Math.ceil(left / Math.max(0.01D, rate)));
			}
			r.putFloat("progress", (float) BelajarMandiri.progress(d.data()));
			readers.add(r);
		}
		tag.put("readers", readers);
		ItemStack hand = player.getMainHandItem();
		List<String> specs = RakKitab.specsOf(hand);
		if (!specs.isEmpty()) {
			tag.store("hand", ComponentSerialization.CODEC, RakKitab.title(specs));
		}
		return tag;
	}

	// ================================================================== Makam Leluhur

	static CompoundTag makam(MinecraftServer server, UUID owner) {
		CompoundTag tag = new CompoundTag();
		ListTag graves = new ListTag();
		for (PaviliunData.Grave g : PaviliunData.graves(owner)) {
			graves.add(epitaph(g));
		}
		tag.put("graves", graves);
		ListTag lineage = new ListTag();
		List<PaviliunData.Grave> all = PaviliunData.lineage(owner);
		for (int i = all.size() - 1; i >= 0 && lineage.size() < LINEAGE_SHOWN; i--) {
			lineage.add(epitaph(all.get(i)));
		}
		tag.put("lineage", lineage);
		tag.putInt("lineage_total", all.size());
		tag.putInt("folded", PaviliunData.folded(owner));
		tag.putInt("slots", Makam.slots(owner, false));
		tag.putInt("buried", PaviliunData.count(owner, PaviliunData.BURIED));
		tag.putInt("blessing", (int) Math.round(AltarLeluhur.blessing(owner) * 1000.0D));
		tag.putInt("tablets", Silsilah.tablets(owner));
		tag.putInt("fresh", (int) Math.round(AltarLeluhur.freshness(owner) * 100.0D));
		tag.putInt("cost", Paviliun.config().abadikanPK);
		tag.putInt("pool", Halls.section(owner, HallData.CORE).getIntOr("pool", 0));
		tag.putBoolean("altar", SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_ALTAR) != null);
		tag.putBoolean("incense_today", PaviliunData.getLong(owner, PaviliunData.INCENSE_DAY, -1L) == Ingatan.today(server));
		tag.putLong("qingming_left", Upacara.daysLeft(server, owner));
		tag.putBoolean("qingming_running", Upacara.qingming(owner) != null);
		tag.putInt("qingming_every", Paviliun.config().qingmingHari);
		tag.putLong("today", Ingatan.today(server));
		return tag;
	}

	private static CompoundTag epitaph(PaviliunData.Grave g) {
		CompoundTag e = PaviliunData.toTag(g);
		e.store("realm", ComponentSerialization.CODEC, Rank.ofStep(g.step()).displayName());
		e.store("cause_text", ComponentSerialization.CODEC,
				Component.translatable("kultivator.sekte.paviliun.sebab." + g.cause(), g.causeArg()));
		return e;
	}

	private static UUID parse(String text) {
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private PaviliunView() {
	}
}
