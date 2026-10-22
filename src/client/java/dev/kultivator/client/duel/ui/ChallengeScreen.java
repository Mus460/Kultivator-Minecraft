package dev.kultivator.client.duel.ui;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.duel.DuelClient;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.JadeSlider;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.net.DuelPayloads;
import dev.kultivator.harta.HartaItems;
import dev.kultivator.technique.source.JadeSlips;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * The challenge screen (DESIGN 35.2, 35.9 module U): the rules of a formal duel before it is sent as a
 * {@code DuelChallenge}. Each rule is a talisman that steps through its values, with a tooltip saying what it does:
 * realm difference (Bebas / Handicap / Tekan Ranah, suggested when the realms are two or more apart), techniques
 * (sword only / free), victory (the 10% line only / Deathblow decides), ring radius 6/9/12, time limit, the stake (none,
 * spirit stones of one grade with a count slider and the player's own count, one jade slip from the inventory by name,
 * Honour) limited by the pair (a disciple only plays for nothing or Honour; a watched disciple duel for nothing), and
 * the intro film and crowd switches. The server checks everything again and answers in the chat.
 */
public final class ChallengeScreen extends DuelScrollScreen {

	private static final int[] RADII = {6, 9, 12};
	private static final int[] MINUTES = {1, 2, 3, 5};
	private static final float[] COUNTS = {1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24, 32, 40, 48, 64};

	private final int targetId;
	private final int secondId;
	private final Component targetName;
	private final int pair;
	private final int targetRealm;

	private int realmRule = DuelIds.REALM_BEBAS;
	private int jurusRule = DuelIds.JURUS_PEDANG_SAJA;
	private int winRule = DuelIds.WIN_TEBASAN_PENENTU;
	private int radiusIndex = 1;
	private int minutesIndex = 2;
	private int stake = DuelIds.STAKE_TANPA;
	private int grade = 1;
	private int countIndex = 7;
	private int slipIndex;
	private boolean introFilm;
	private boolean crowd;
	private boolean suggested;
	private @Nullable JadeSlider slider;

	/**
	 * @param targetId    the challenged entity (the first disciple of a watched duel)
	 * @param secondId    the second disciple of a watched duel ({@code PAIR_TONTON}), else -1
	 * @param pair        {@code DuelIds.PAIR_*} as the duel screen guessed it (the server decides)
	 * @param targetRealm the target's realm ordinal when the client knows it, else -1
	 */
	public ChallengeScreen(@Nullable Screen parent, int targetId, int secondId, Component targetName, int pair, int targetRealm) {
		super(Component.translatable("kultivator.duel.ui.challenge.title"), parent);
		this.targetId = targetId;
		this.secondId = secondId;
		this.targetName = targetName;
		this.pair = pair;
		this.targetRealm = targetRealm;
		this.introFilm = DuelSettings.intro() && pair != DuelIds.PAIR_TONTON;
		this.crowd = pair == DuelIds.PAIR_TONTON;
		int own = ClientCultivation.received() ? ClientCultivation.data().realm().ordinal() : -1;
		if (own >= 0 && targetRealm >= 0 && Math.abs(own - targetRealm) >= 2 && pair != DuelIds.PAIR_TONTON) {
			this.realmRule = DuelIds.REALM_TEKAN_RANAH;
			this.suggested = true;
		}
	}

	// ================================================================== options

	private int[] stakes() {
		return switch (pair) {
			case DuelIds.PAIR_MURID -> new int[]{DuelIds.STAKE_TANPA, DuelIds.STAKE_KEHORMATAN};
			case DuelIds.PAIR_TONTON, DuelIds.PAIR_UJI, DuelIds.PAIR_LATIH -> new int[]{DuelIds.STAKE_TANPA};
			default -> new int[]{DuelIds.STAKE_TANPA, DuelIds.STAKE_BATU_ROH, DuelIds.STAKE_GULUNGAN, DuelIds.STAKE_KEHORMATAN};
		};
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
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				count += stack.getCount();
			}
		}
		return count;
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
		if (list.isEmpty()) {
			return null;
		}
		return list.get(Math.floorMod(slipIndex, list.size()));
	}

	private int count() {
		return Math.round(COUNTS[Mth.clamp(countIndex, 0, COUNTS.length - 1)]);
	}

	/** Why the challenge cannot be sent as it stands (null: it can). */
	private @Nullable Component problem() {
		if (stake == DuelIds.STAKE_BATU_ROH && owned(stone(grade)) < count()) {
			return Component.translatable("kultivator.duel.ui.challenge.short_stones", owned(stone(grade)), count());
		}
		if (stake == DuelIds.STAKE_GULUNGAN && slip() == null) {
			return Component.translatable("kultivator.duel.ui.challenge.no_slip");
		}
		if (!DuelClient.connected()) {
			return Component.translatable("kultivator.duel.ui.challenge.offline");
		}
		return null;
	}

	// ================================================================== layout

	@Override
	protected void build() {
		int labelW = Math.round((right - left) * 0.30F);
		int vx = left + labelW;
		int vw = Math.min(150, right - vx - 4);
		int y = top + 26;
		int h = 13;
		int step = h + 3;
		boolean tonton = pair == DuelIds.PAIR_TONTON;
		add(choice(vx, y, vw, h, "realm", () -> realmRule, () -> realmRule = (realmRule + 1) % 3));
		y += step;
		add(choice(vx, y, vw, h, "jurus", () -> jurusRule, () -> jurusRule = (jurusRule + 1) % 2));
		y += step;
		add(choice(vx, y, vw, h, "win", () -> winRule, () -> winRule = (winRule + 1) % 2));
		y += step;
		add(valueButton(vx, y, vw, h, () -> Component.translatable("kultivator.duel.ui.rule.radius", RADII[radiusIndex]),
				() -> radiusIndex = (radiusIndex + 1) % RADII.length, "radius"));
		y += step;
		add(valueButton(vx, y, vw, h, () -> Component.translatable("kultivator.duel.ui.challenge.minutes", MINUTES[minutesIndex]),
				() -> minutesIndex = (minutesIndex + 1) % MINUTES.length, "time"));
		y += step;
		int[] options = stakes();
		add(valueButton(vx, y, vw, h, () -> Component.translatable("kultivator.duel.ui.stake." + stake), () -> {
			int at = 0;
			for (int i = 0; i < options.length; i++) {
				if (options[i] == stake) {
					at = i;
				}
			}
			stake = options[(at + 1) % options.length];
			rebuildWidgets();
		}, "stake").enabledWhen(() -> options.length > 1));
		y += step;
		slider = null;
		if (stake == DuelIds.STAKE_BATU_ROH) {
			int half = (vw - 4) / 2;
			add(valueButton(vx, y, half, h, () -> Component.translatable("kultivator.duel.ui.grade." + grade), () -> grade = (grade + 1) % 4, "grade"));
			IntSupplier max = () -> COUNTS.length - 1;
			JadeSlider countSlider = new JadeSlider(vx + half + 4, y - 4, vw - half - 4, 18, COUNTS, countIndex,
					value -> Component.translatable("kultivator.duel.ui.challenge.count", Math.round(value)), index -> countIndex = index).limit(max);
			slider = addRenderableWidget(countSlider);
			y += step + 4;
		} else if (stake == DuelIds.STAKE_GULUNGAN) {
			add(valueButton(vx, y, vw, h, () -> {
				ItemStack s = slip();
				return s == null ? Component.translatable("kultivator.duel.ui.challenge.no_slip") : s.getHoverName();
			}, () -> slipIndex++, "slip"));
			y += step;
		}
		int cw = (vw - 4) / 2;
		if (!tonton) {
			add(flag(vx, y, cw, "film", () -> introFilm, () -> introFilm = !introFilm));
		}
		add(flag(tonton ? vx : vx + cw + 4, y, cw, "crowd", () -> crowd, () -> crowd = !crowd));

		int bw = Math.min(110, (right - left - 8) / 2);
		int by = bottom - 18;
		add(new TalismanButton((left + right) / 2 - bw - 4, by, bw, 15, () -> Component.translatable("kultivator.duel.ui.challenge.send"))
				.urgentWhen(() -> problem() == null)
				.enabledWhen(() -> problem() == null)
				.onPress(this::send)
				.tooltip(() -> {
					Component p = problem();
					return p == null ? List.of(Component.translatable("kultivator.duel.ui.challenge.send"),
							Component.translatable("kultivator.duel.ui.challenge.send.tip").withStyle(ChatFormatting.GRAY))
							: List.of(Component.translatable("kultivator.duel.ui.challenge.send"), p.copy().withStyle(ChatFormatting.RED));
				}));
		add(new TalismanButton((left + right) / 2 + 4, by, bw, 15, () -> Component.translatable("kultivator.duel.ui.challenge.back"))
				.onPress(this::onClose));
	}

	private TalismanButton choice(int x, int y, int w, int h, String rule, IntSupplier value, Runnable next) {
		TalismanButton button = new TalismanButton(x, y, w, h, () -> Component.translatable("kultivator.duel.ui.rule." + rule + "." + value.getAsInt()));
		button.onPress(next).tooltip(() -> List.of(Component.translatable("kultivator.duel.ui.rule." + rule + "." + value.getAsInt()),
				Component.translatable("kultivator.duel.ui.rule." + rule + "." + value.getAsInt() + ".tip").withStyle(ChatFormatting.GRAY)));
		return button;
	}

	private TalismanButton valueButton(int x, int y, int w, int h, java.util.function.Supplier<Component> label, Runnable next, String tip) {
		TalismanButton button = new TalismanButton(x, y, w, h, label);
		button.onPress(next).tooltip(() -> List.of(label.get(),
				Component.translatable("kultivator.duel.ui.challenge." + tip + ".tip").withStyle(ChatFormatting.GRAY)));
		return button;
	}

	private JadeCoin flag(int x, int y, int w, String key, java.util.function.BooleanSupplier on, Runnable toggle) {
		JadeCoin coin = new JadeCoin(x, y, w, 10, () -> Component.translatable("kultivator.duel.ui.challenge." + key), on);
		coin.onPress(toggle).tooltip(() -> List.of(Component.translatable("kultivator.duel.ui.challenge." + key),
				Component.translatable("kultivator.duel.ui.challenge." + key + ".tip").withStyle(ChatFormatting.GRAY)));
		return coin;
	}

	private void send() {
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
		int count = stake == DuelIds.STAKE_BATU_ROH ? count() : 0;
		DuelClient.challenge(new DuelPayloads.DuelChallenge(targetId, secondId, realmRule, jurusRule, winRule, RADII[radiusIndex],
				MINUTES[minutesIndex] * 60 * 20, stake, stake == DuelIds.STAKE_BATU_ROH ? grade : 0, count, slipSpec, flags));
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.sendOverlayMessage(Component.translatable("kultivator.duel.ui.challenge.sent", targetName));
		}
		onClose();
	}

	// ================================================================== drawing

	@Override
	protected void drawPaper(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		Brush.fitCentered(g, font, Brush.bold(title), (left + right) / 2.0F, top, right - left, Palette.INK, 1.0F, 0.6F, false);
		Component who = Component.translatable("kultivator.duel.ui.challenge.against", targetName,
				Component.translatable("kultivator.duel.ui.pair." + pair));
		Brush.fitCentered(g, font, who, (left + right) / 2.0F, top + 11.0F, right - left, Palette.SEAL_DARK, 0.75F, 0.45F, false);
		int labelW = Math.round((right - left) * 0.30F);
		float y = top + 28.0F;
		String[] labels = {"realm", "jurus", "win", "radius", "time", "stake"};
		for (String label : labels) {
			Brush.fit(g, font, Component.translatable("kultivator.duel.ui.challenge.label." + label), left, y, labelW - 6.0F, Palette.INK_SOFT, 0.7F, 0.5F,
					false);
			y += 16.0F;
		}
		if (stake == DuelIds.STAKE_BATU_ROH) {
			Component have = Component.translatable("kultivator.duel.ui.challenge.have", owned(stone(grade)));
			Brush.fit(g, font, have, left, y, labelW - 6.0F, owned(stone(grade)) >= count() ? Palette.INK_SOFT : Palette.DANGER, 0.6F, 0.45F, false);
			y += 20.0F;
		} else if (stake == DuelIds.STAKE_GULUNGAN) {
			Brush.fit(g, font, Component.translatable("kultivator.duel.ui.challenge.label.slip"), left, y, labelW - 6.0F, Palette.INK_SOFT, 0.7F, 0.5F,
					false);
			y += 16.0F;
		}
		// what the chosen realm rule means, in a short note; the suggestion when realms are far apart
		float noteY = y + 16.0F;
		if (noteY < bottom - 34.0F) {
			Component note = Component.translatable("kultivator.duel.ui.rule.realm." + realmRule + ".tip");
			Brush.wrap(g, font, note, left, noteY, right - left, Palette.INK_FAINT, 0.55F, false);
			if (suggested && realmRule == DuelIds.REALM_TEKAN_RANAH) {
				float nh = Brush.wrapHeight(font, note, right - left, 0.55F);
				if (noteY + nh + 8.0F < bottom - 22.0F) {
					Brush.fit(g, font, Component.translatable("kultivator.duel.ui.challenge.suggested"), left, noteY + nh + 1.0F, right - left,
							Palette.JADE_DARK, 0.55F, 0.42F, false);
				}
			}
		}
		if (stake == DuelIds.STAKE_GULUNGAN) {
			ItemStack s = slip();
			if (s != null) {
				g.item(s, right - 18, top + 28 + 6 * 16 - 2);
			}
		}
	}
}
