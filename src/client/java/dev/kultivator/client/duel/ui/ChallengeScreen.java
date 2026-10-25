package dev.kultivator.client.duel.ui;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.duel.DuelClient;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihJumlah;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.net.DuelPayloads;
import dev.kultivator.harta.HartaItems;
import dev.kultivator.technique.source.JadeSlips;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The challenge form (DESIGN 35.2, 43.5 Dunia › Duel › tantang, shown as the page's detail view: G82, no screen of its
 * own any more). The rules of a formal duel before it is sent as a {@code DuelChallenge}, each a talisman stepping
 * through its values with what it does in the tooltip: realm difference (Bebas / Handicap / Tekan Ranah, suggested when
 * the realms are two or more apart), techniques, victory, ring radius 6/9/12, time limit, the stake (none, spirit stones
 * of one grade with a count picker and the player's own count, one jade slip from the inventory, Honour) limited by the
 * pair, and the intro film and crowd. Laid out in two columns from 240 px, so it fits the 289 × 148 content box of GUI
 * scale 4 without a scroll. Back is the page header's back control / Esc. The server checks everything again.
 */
final class ChallengeScreen {

	private static final String K = "kultivator.gui43.m14.";
	private static final String C = "kultivator.duel.ui.challenge.";
	private static final int[] RADII = {6, 9, 12};
	private static final int[] MINUTES = {1, 2, 3, 5};
	private static final int MAX_COUNT = 64;
	private static final int ROW = 14;
	private static final int STEP = 16;

	private final int targetId;
	private final int secondId;
	private final Component targetName;
	private final int pair;

	private int realmRule = DuelIds.REALM_BEBAS;
	private int jurusRule = DuelIds.JURUS_PEDANG_SAJA;
	private int winRule = DuelIds.WIN_TEBASAN_PENENTU;
	private int radiusIndex = 1;
	private int minutesIndex = 2;
	private int stake = DuelIds.STAKE_TANPA;
	private int grade = 1;
	private final PemilihJumlah count = new PemilihJumlah();
	private int slipIndex;
	private boolean introFilm;
	private boolean crowd;
	private boolean suggested;

	/**
	 * @param targetId    the challenged entity (the first disciple of a watched duel)
	 * @param secondId    the second disciple of a watched duel ({@code PAIR_TONTON}), else -1
	 * @param pair        {@code DuelIds.PAIR_*} as the page guessed it (the server decides)
	 * @param targetRealm the target's realm ordinal when the client knows it, else -1
	 */
	ChallengeScreen(int targetId, int secondId, Component targetName, int pair, int targetRealm) {
		this.targetId = targetId;
		this.secondId = secondId;
		this.targetName = targetName;
		this.pair = pair;
		this.introFilm = DuelSettings.intro() && pair != DuelIds.PAIR_TONTON;
		this.crowd = pair == DuelIds.PAIR_TONTON;
		this.count.set(8);
		int own = ClientCultivation.received() ? ClientCultivation.data().realm().ordinal() : -1;
		if (own >= 0 && targetRealm >= 0 && Math.abs(own - targetRealm) >= 2 && pair != DuelIds.PAIR_TONTON) {
			this.realmRule = DuelIds.REALM_TEKAN_RANAH;
			this.suggested = true;
		}
	}

	/** The challenged entity (the page closes the form when it goes away). */
	int targetId() {
		return targetId;
	}

	// ================================================================== options

	private int[] stakes() {
		return switch (pair) {
			case DuelIds.PAIR_MURID -> new int[]{DuelIds.STAKE_TANPA, DuelIds.STAKE_KEHORMATAN};
			case DuelIds.PAIR_TONTON, DuelIds.PAIR_UJI, DuelIds.PAIR_LATIH -> new int[]{DuelIds.STAKE_TANPA};
			default -> new int[]{DuelIds.STAKE_TANPA, DuelIds.STAKE_BATU_ROH, DuelIds.STAKE_GULUNGAN, DuelIds.STAKE_KEHORMATAN};
		};
	}

	private void nextStake() {
		int[] options = stakes();
		int at = 0;
		for (int i = 0; i < options.length; i++) {
			if (options[i] == stake) {
				at = i;
			}
		}
		stake = options[(at + 1) % options.length];
	}

	private static Item stone(int grade) {
		return switch (grade) {
			case 0 -> HartaItems.SPIRIT_STONE_LOW;
			case 2 -> HartaItems.SPIRIT_STONE_HIGH;
			case 3 -> HartaItems.SPIRIT_STONE_SUPREME;
			default -> HartaItems.SPIRIT_STONE_MID;
		};
	}

	private static int owned(Item item) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return 0;
		}
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** The jade slips with a technique in the inventory, in slot order. */
	private static List<ItemStack> slips() {
		List<ItemStack> list = new ArrayList<>();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return list;
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (JadeSlips.specOf(stack) != null) {
				list.add(stack);
			}
		}
		return list;
	}

	private @Nullable ItemStack slip() {
		List<ItemStack> list = slips();
		return list.isEmpty() ? null : list.get(Math.floorMod(slipIndex, list.size()));
	}

	/** Why the challenge cannot be sent as it stands (null: it can). */
	private @Nullable Component problem() {
		if (stake == DuelIds.STAKE_BATU_ROH && owned(stone(grade)) < count.nilai()) {
			return Component.translatable(C + "short_stones", owned(stone(grade)), count.nilai());
		}
		if (stake == DuelIds.STAKE_GULUNGAN && slip() == null) {
			return Component.translatable(C + "no_slip");
		}
		if (!DuelClient.connected()) {
			return Component.translatable(C + "offline");
		}
		return null;
	}

	private static Component yaTidak(boolean on) {
		return Component.translatable(on ? K + "ya" : K + "tidak");
	}

	/** "Label: value". */
	private static Component aturan(String label, Component value) {
		return Component.translatable(K + "aturan", Component.translatable(C + "label." + label), value);
	}

	// ================================================================== drawing

	/**
	 * Draws the form into the box; {@code terkirim} gets the footer line once the challenge is sent (the page then closes
	 * the form).
	 */
	void gambar(Lukis l, int x0, int y0, int x1, int y1, Consumer<Component> terkirim) {
		Component who = Component.translatable(C + "against", targetName, Component.translatable("kultivator.duel.ui.pair." + pair));
		int y = y0 + l.kepala(who, x0, y0, x1);
		boolean dua = x1 - x0 >= 240;
		int cw = dua ? (x1 - x0 - 4) / 2 : x1 - x0;

		// the six rules, two per row from 240 px
		int i = 0;
		rule(l, "realm", colX(dua, x0, cw, i), y + rowY(dua, i++), cw,
				aturan("realm", Component.translatable("kultivator.duel.ui.rule.realm." + realmRule)),
				Component.translatable("kultivator.duel.ui.rule.realm." + realmRule + ".tip"), true, null, () -> realmRule = (realmRule + 1) % 3);
		rule(l, "jurus", colX(dua, x0, cw, i), y + rowY(dua, i++), cw,
				aturan("jurus", Component.translatable("kultivator.duel.ui.rule.jurus." + jurusRule)),
				Component.translatable("kultivator.duel.ui.rule.jurus." + jurusRule + ".tip"), true, null, () -> jurusRule = (jurusRule + 1) % 2);
		rule(l, "win", colX(dua, x0, cw, i), y + rowY(dua, i++), cw,
				aturan("win", Component.translatable("kultivator.duel.ui.rule.win." + winRule)),
				Component.translatable("kultivator.duel.ui.rule.win." + winRule + ".tip"), true, null, () -> winRule = (winRule + 1) % 2);
		rule(l, "radius", colX(dua, x0, cw, i), y + rowY(dua, i++), cw,
				aturan("radius", Component.translatable(K + "blok", RADII[radiusIndex])),
				Component.translatable(C + "radius.tip"), true, null, () -> radiusIndex = (radiusIndex + 1) % RADII.length);
		rule(l, "time", colX(dua, x0, cw, i), y + rowY(dua, i++), cw,
				aturan("time", Component.translatable(C + "minutes", MINUTES[minutesIndex])),
				Component.translatable(C + "time.tip"), true, null, () -> minutesIndex = (minutesIndex + 1) % MINUTES.length);
		boolean pilihan = stakes().length > 1;
		rule(l, "stake", colX(dua, x0, cw, i), y + rowY(dua, i), cw,
				aturan("stake", Component.translatable("kultivator.duel.ui.stake." + stake)),
				Component.translatable(C + "stake.tip"), pilihan, pilihan ? null : Component.translatable(K + "taruhan_terkunci"), this::nextStake);
		y += rowY(dua, i) + STEP;

		// the stake's own row
		if (stake == DuelIds.STAKE_BATU_ROH) {
			int gw = dua ? cw : Math.max(TalismanButton.MIN_W, (x1 - x0) / 2 - 2);
			rule(l, "grade", x0, y, gw, Component.translatable(K + "mutu", Component.translatable("kultivator.duel.ui.grade." + grade)),
					Component.translatable(C + "grade.tip"), true, null, () -> grade = (grade + 1) % 4);
			int px = x0 + gw + 4;
			int pw = count.gambar(l, "duel#jumlah", px, y, MAX_COUNT);
			int have = owned(stone(grade));
			l.teks(Component.translatable(C + "have", have), px + pw + 4, y + 3, x1 - px - pw - 4,
					have >= count.nilai() ? l.gaya.teksLemah : Palette.DANGER);
			y += STEP;
		} else if (stake == DuelIds.STAKE_GULUNGAN) {
			ItemStack s = slip();
			Component name = s == null ? Component.translatable(C + "no_slip") : s.getHoverName();
			int sw = Math.max(TalismanButton.MIN_W, x1 - x0 - (s == null ? 0 : 20));
			rule(l, "slip", x0, y, sw, aturan("slip", name), Component.translatable(C + "slip.tip"), s != null, s == null ? name : null,
					() -> slipIndex++);
			if (s != null) {
				l.g.item(s, x1 - 17, y - 1);
			}
			y += STEP;
		}

		// intro film and crowd
		boolean tonton = pair == DuelIds.PAIR_TONTON;
		int fx = x0;
		if (!tonton) {
			rule(l, "film", fx, y, cw, Component.translatable(K + "aturan", Component.translatable(C + "film"), yaTidak(introFilm)),
					Component.translatable(C + "film.tip"), true, null, () -> introFilm = !introFilm);
			fx = dua ? x0 + cw + 4 : x0;
			if (!dua) {
				y += STEP;
			}
		}
		rule(l, "crowd", fx, y, cw, Component.translatable(K + "aturan", Component.translatable(C + "crowd"), yaTidak(crowd)),
				Component.translatable(C + "crowd.tip"), true, null, () -> crowd = !crowd);
		y += STEP + 2;

		// what the chosen realm rule means; the suggestion when the realms are far apart
		int sendY = y1 - ROW;
		float room = sendY - 3 - y;
		if (room >= 9.0F) {
			float used = l.bungkus(Component.translatable("kultivator.duel.ui.rule.realm." + realmRule + ".tip"), x0, y, x1 - x0, l.gaya.teksSamar,
					suggested && realmRule == DuelIds.REALM_TEKAN_RANAH ? room - 10.0F : room);
			if (suggested && realmRule == DuelIds.REALM_TEKAN_RANAH && room - used >= 10.0F) {
				l.teks(Component.translatable(C + "suggested"), x0, y + used + 1, x1 - x0, l.gaya.aksen);
			}
		}

		// send
		Component p = problem();
		Component label = Component.translatable(C + "send");
		int bw = TalismanButton.lebar(l.font, label, 90, x1 - x0);
		boolean over = TalismanButton.gambar(l, "duel#kirim", x1 - bw, sendY, bw, ROW, label, p == null, p, () -> send(terkirim), p == null, false);
		if (over) {
			l.tip(p == null ? List.of(label, Component.translatable(C + "send.tip").withStyle(ChatFormatting.GRAY))
					: List.of(label, p.copy().withStyle(ChatFormatting.RED)));
		}
	}

	private static int colX(boolean dua, int x0, int cw, int i) {
		return dua ? x0 + (i % 2) * (cw + 4) : x0;
	}

	private static int rowY(boolean dua, int i) {
		return (dua ? i / 2 : i) * STEP;
	}

	/** One rule talisman with its tooltip. */
	private static void rule(Lukis l, String kunci, int x, int y, int w, Component label, Component tip, boolean aktif, @Nullable Component alasan,
			Runnable next) {
		if (TalismanButton.gambar(l, "duel#" + kunci, x, y, w, ROW, label, aktif, alasan, next)) {
			l.tip(List.of(label, tip.copy().withStyle(ChatFormatting.GRAY)));
		}
	}

	private void send(Consumer<Component> terkirim) {
		if (problem() != null) {
			return;
		}
		String slipSpec = "";
		if (stake == DuelIds.STAKE_GULUNGAN) {
			ItemStack s = slip();
			String spec = s == null ? null : JadeSlips.specOf(s);
			slipSpec = spec == null ? "" : spec.length() > 64 ? spec.substring(0, 64) : spec;
		}
		int flags = (introFilm ? DuelIds.RF_INTRO_FILM : 0) | (crowd ? DuelIds.RF_CROWD : 0);
		int n = stake == DuelIds.STAKE_BATU_ROH ? count.nilai() : 0;
		DuelClient.challenge(new DuelPayloads.DuelChallenge(targetId, secondId, realmRule, jurusRule, winRule, RADII[radiusIndex],
				MINUTES[minutesIndex] * 60 * 20, stake, stake == DuelIds.STAKE_BATU_ROH ? grade : 0, n, slipSpec, flags));
		terkirim.accept(Component.translatable(C + "sent", targetName));
	}
}
