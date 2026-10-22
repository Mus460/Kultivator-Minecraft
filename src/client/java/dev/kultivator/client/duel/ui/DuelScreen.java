package dev.kultivator.client.duel.ui;

import dev.kultivator.client.duel.DuelClient;
import dev.kultivator.client.duel.DuelKeys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.net.DuelPayloads;
import dev.kultivator.musuh.RogueCultivatorEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The duel screen (DESIGN 35.8.5, Hub › Dunia › "Duel Pedang", {@code /kultivasi duel layar}): on the left, whom the
 * player can challenge right now (the one under the crosshair first, then players, his own disciples and rogue
 * cultivators within 24 blocks, nearest first; a row opens the {@link ChallengeScreen}), and "Duel Tonton" to pick two
 * of his disciples to watch. On the right: an incoming challenge with Terima / Tolak, the running duel with Menyerah
 * (two clicks) and Tunda, the quick settings (input, camera, kiai, sparks, intro film, deflect outside duels, crowd,
 * bars over heads), the record and the salute, and the last result. The controls in a footnote. Jade scroll, fits
 * 320 × 240, tooltips on every control; Esc returns to the hub when opened from it.
 */
public final class DuelScreen extends DuelScrollScreen {

	private static final int ROW = 13;
	private static final double RANGE = 24.0D;
	private static final long SURRENDER_CONFIRM_MILLIS = 3000L;

	/** One entity the player may challenge. */
	record Candidate(int id, Component name, String kind, int pair, float distance, Component detail, int rgb) {
	}

	private final List<Candidate> candidates = new ArrayList<>();
	private long candidatesAt;
	private String signature = "";
	private boolean pickingTwo;
	private int firstPick = -1;
	private long surrenderArmedAt = Long.MIN_VALUE / 2;
	private int listTop;
	private int listRows;

	public DuelScreen(@Nullable Screen parent) {
		super(Component.translatable("kultivator.duel.ui.screen.title"), parent);
	}

	// ================================================================== candidates

	private void refreshCandidates() {
		candidates.clear();
		candidatesAt = Util.getMillis();
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		List<Integer> seen = new ArrayList<>();
		Entity aimed = mc.crosshairPickEntity;
		if (aimed instanceof LivingEntity living && living.isAlive() && living.distanceTo(player) <= 12.0F && candidate(player, living) != null) {
			Candidate c = candidate(player, living);
			candidates.add(c);
			seen.add(living.getId());
		}
		List<Candidate> near = new ArrayList<>();
		AABB box = player.getBoundingBox().inflate(RANGE);
		for (Entity entity : mc.level.getEntities(player, box)) {
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || seen.contains(entity.getId()) || entity.distanceTo(player) > RANGE) {
				continue;
			}
			if (pickingTwo && !(entity instanceof DiscipleEntity)) {
				continue;
			}
			Candidate c = candidate(player, living);
			if (c != null && (c.kind().equals("player") || c.kind().equals("murid") || c.kind().equals("perampok"))) {
				near.add(c);
			}
		}
		near.sort(Comparator.comparingDouble(Candidate::distance));
		if (pickingTwo) {
			candidates.removeIf(c -> !c.kind().equals("murid"));
		}
		candidates.addAll(near);
	}

	private static @Nullable Candidate candidate(LocalPlayer player, LivingEntity living) {
		float distance = living.distanceTo(player);
		if (living instanceof ArmorStand || living == player) {
			return null;
		}
		if (living instanceof Player other) {
			if (other.isSpectator()) {
				return null;
			}
			return new Candidate(other.getId(), other.getDisplayName(), "player", DuelIds.PAIR_PVP, distance,
					Component.translatable("kultivator.duel.ui.kind.player"), 0x9FD3FF);
		}
		if (living instanceof DiscipleEntity disciple) {
			if (disciple.role() != DiscipleEntity.Role.DISCIPLE || !disciple.isOwnedBy(player)) {
				return null;
			}
			Component realm = Realm.byOrdinal(disciple.syncedRealm()).displayName();
			return new Candidate(disciple.getId(), disciple.getDisplayName(), "murid", DuelIds.PAIR_MURID, distance,
					Component.translatable("kultivator.duel.ui.kind.murid", realm), 0x9FE08F);
		}
		if (living instanceof RogueCultivatorEntity rogue) {
			Component realm = Realm.byOrdinal(rogue.realm()).displayName();
			return new Candidate(rogue.getId(), rogue.getDisplayName(), "perampok", DuelIds.PAIR_NPC, distance,
					Component.translatable("kultivator.duel.ui.kind.perampok", realm, rogue.tier()), rogue.tier() >= 4 ? 0xFF7A6A : 0xFFC870);
		}
		// Any other figure under the crosshair (a wandering cultivator of the Dunia, a sect NPC): the server decides.
		return new Candidate(living.getId(), living.getDisplayName(), "npc", DuelIds.PAIR_NPC, distance,
				Component.translatable("kultivator.duel.ui.kind.npc"), 0xE8DCC0);
	}

	/** What decides the layout: rebuilt when it changes. */
	private String currentSignature() {
		DuelPayloads.DuelSessionSync s = DuelClient.localSession();
		List<DuelClient.Offer> offers = DuelClient.offers();
		return (s == null ? "-" : s.sessionId() + ":" + s.phase()) + "|" + (offers.isEmpty() ? "-" : offers.get(0).offer().challengeId()) + "|"
				+ candidates.size() + "|" + pickingTwo;
	}

	// ================================================================== layout

	@Override
	protected void build() {
		if (Util.getMillis() - candidatesAt > 1000L || candidates.isEmpty()) {
			refreshCandidates();
		}
		signature = currentSignature();
		int mid = left + Math.round((right - left) * 0.56F);
		// ---- left column: the list, then "Duel Tonton"
		listTop = top + 26;
		int listBottom = bottom - 32;
		listRows = Math.max(1, (listBottom - listTop) / ROW);
		int lw = mid - left - 6;
		add(new TalismanButton(left, bottom - 28, lw, 14,
				() -> Component.translatable(pickingTwo ? "kultivator.duel.ui.screen.tonton_cancel" : "kultivator.duel.ui.screen.tonton"))
				.stampedWhen(() -> pickingTwo)
				.onPress(() -> {
					pickingTwo = !pickingTwo;
					firstPick = -1;
					refreshCandidates();
					rebuildWidgets();
				})
				.tooltip(() -> List.of(Component.translatable("kultivator.duel.ui.screen.tonton"),
						Component.translatable("kultivator.duel.ui.screen.tonton.tip").withStyle(ChatFormatting.GRAY))));

		// ---- right column
		int rx = mid + 4;
		int rw = right - rx;
		int y = top + 26;
		List<DuelClient.Offer> offers = DuelClient.offers();
		if (!offers.isEmpty()) {
			DuelPayloads.DuelChallengeOffer offer = offers.get(0).offer();
			y += 28;
			int bw = (rw - 4) / 2;
			add(new TalismanButton(rx, y, bw, 14, () -> Component.translatable("kultivator.duel.ui.offer.accept"))
					.urgentWhen(() -> true)
					.onPress(() -> DuelClient.answer(offer.challengeId(), DuelIds.ANSWER_TERIMA))
					.tooltip(() -> List.of(Component.translatable("kultivator.duel.ui.offer.accept"),
							ChallengeOfferToast.rulesLine(offer.rules()), ChallengeOfferToast.stakeLine(offer))));
			add(new TalismanButton(rx + bw + 4, y, bw, 14, () -> Component.translatable("kultivator.duel.ui.offer.decline"))
					.onPress(() -> DuelClient.answer(offer.challengeId(), DuelIds.ANSWER_TOLAK))
					.tooltip(() -> List.of(Component.translatable("kultivator.duel.ui.offer.decline"),
							Component.translatable("kultivator.duel.ui.offer.decline.tip").withStyle(ChatFormatting.GRAY))));
			y += 20;
		}
		DuelPayloads.DuelSessionSync s = DuelClient.localSession();
		if (s != null && s.phase() != DuelIds.PHASE_ENDED) {
			y += 20;
			int bw = (rw - 4) / 2;
			add(new TalismanButton(rx, y, bw, 14, () -> Component.translatable(surrenderArmed()
					? "kultivator.duel.ui.screen.surrender_confirm" : "kultivator.duel.ui.screen.surrender"))
					.urgentWhen(this::surrenderArmed)
					.enabledWhen(() -> DuelClient.inFight())
					.onPress(() -> {
						if (surrenderArmed()) {
							command("kultivasi duel menyerah");
							surrenderArmedAt = Long.MIN_VALUE / 2;
						} else {
							surrenderArmedAt = Util.getMillis();
						}
					})
					.tooltip(() -> List.of(Component.translatable("kultivator.duel.ui.screen.surrender"),
							Component.translatable("kultivator.duel.ui.screen.surrender.tip", DuelHud.parryKey()).withStyle(ChatFormatting.GRAY))));
			add(new TalismanButton(rx + bw + 4, y, bw, 14, () -> Component.translatable("kultivator.duel.ui.screen.pause"))
					.onPress(() -> command("kultivasi duel tunda"))
					.tooltip(() -> List.of(Component.translatable("kultivator.duel.ui.screen.pause"),
							Component.translatable("kultivator.duel.ui.screen.pause.tip").withStyle(ChatFormatting.GRAY))));
			y += 20;
		}
		// quick settings: cycles as talismans, switches as jade coins
		y += 12;
		int sh = 13;
		if (y + sh * 4 + 30 <= bottom) {
			add(cycle(rx, y, rw, sh, "input", () -> DuelSettings.input().id(), DuelSettings::cycleInput));
			y += sh + 2;
			add(cycle(rx, y, rw, sh, "camera", () -> DuelSettings.camera().id(), () -> DuelSettings.nextCamera(true)));
			y += sh + 2;
			add(cycle(rx, y, rw, sh, "kiai", () -> DuelSettings.kiai().id(), DuelSettings::cycleKiai));
			y += sh + 2;
			add(cycle(rx, y, rw, sh, "sparks", () -> DuelSettings.sparks().id(), DuelSettings::cycleSparks));
			y += sh + 3;
		}
		if (y + 22 + 16 <= bottom) {
			int cw = (rw - 4) / 2;
			add(coin(rx, y, cw, "intro", DuelSettings::intro, DuelSettings::toggleIntro));
			add(coin(rx + cw + 4, y, cw, "parry_outside", DuelSettings::parryOutside, DuelSettings::toggleParryOutside));
			y += 12;
			add(coin(rx, y, cw, "crowd", DuelSettings::crowd, DuelSettings::toggleCrowd));
			add(coin(rx + cw + 4, y, cw, "hud_world", DuelSettings::hudWorld, DuelSettings::toggleHudWorld));
			y += 14;
		}
		int bw = (rw - 4) / 2;
		add(new TalismanButton(rx, bottom - 28, bw, 14, () -> Component.translatable("kultivator.duel.ui.screen.records"))
				.onPress(() -> {
					command("kultivasi duel catatan");
					onClose();
				})
				.tooltip(() -> List.of(Component.translatable("kultivator.duel.ui.screen.records"),
						Component.translatable("kultivator.duel.ui.screen.records.tip").withStyle(ChatFormatting.GRAY))));
		add(new TalismanButton(rx + bw + 4, bottom - 28, bw, 14, () -> Component.translatable("kultivator.duel.ui.screen.salute"))
				.onPress(() -> {
					command("kultivasi duel hormat");
					onClose();
				})
				.tooltip(() -> List.of(Component.translatable("kultivator.duel.ui.screen.salute"),
						Component.translatable("kultivator.duel.ui.screen.salute.tip").withStyle(ChatFormatting.GRAY))));
	}

	private TalismanButton cycle(int x, int y, int w, int h, String key, java.util.function.Supplier<String> value, Runnable next) {
		String t = "kultivator.duel.setting.";
		TalismanButton button = new TalismanButton(x, y, w, h,
				() -> Component.translatable(t + key, Component.translatable(t + key + "." + value.get())));
		button.onPress(next).tooltip(() -> List.of(Component.translatable(t + key, Component.translatable(t + key + "." + value.get())),
				Component.translatable(t + key + ".tip").withStyle(ChatFormatting.GRAY),
				Component.translatable(t + key + "." + value.get() + ".tip").withStyle(ChatFormatting.DARK_AQUA)));
		return button;
	}

	private JadeCoin coin(int x, int y, int w, String key, java.util.function.BooleanSupplier on, Runnable toggle) {
		String t = "kultivator.duel.setting.";
		JadeCoin coin = new JadeCoin(x, y, w, 10, () -> Component.translatable(t + key), on);
		coin.onPress(toggle).tooltip(() -> List.of(Component.translatable(t + key), Component.translatable(t + key + ".tip").withStyle(ChatFormatting.GRAY),
				Component.translatable(on.getAsBoolean() ? t + "on" : t + "off")
						.withStyle(on.getAsBoolean() ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY)));
		return coin;
	}

	private boolean surrenderArmed() {
		return Util.getMillis() - surrenderArmedAt < SURRENDER_CONFIRM_MILLIS;
	}

	private static void command(String command) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null) {
			mc.getConnection().sendCommand(command);
		}
	}

	// ================================================================== drawing

	@Override
	protected void drawPaper(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (Util.getMillis() - candidatesAt > 1000L) {
			refreshCandidates();
		}
		if (!signature.equals(currentSignature())) {
			rebuildWidgets();
			return;
		}
		int mid = left + Math.round((right - left) * 0.56F);
		// title
		Brush.fitCentered(g, font, Brush.bold(title), (left + right) / 2.0F, top, right - left, Palette.INK, 1.0F, 0.6F, false);
		Brush.fitCentered(g, font, Component.translatable("kultivator.duel.ui.screen.subtitle"), (left + right) / 2.0F, top + 10.0F, right - left,
				Palette.INK_SOFT, 0.6F, 0.45F, false);
		g.fill(mid, top + 22, mid + 1, bottom - 4, Palette.argb(0x60, Palette.GOLD_DARK));

		drawList(g, mouseX, mouseY, mid);
		drawRight(g, mid);
		// the controls, in a footnote
		Component keys = Component.translatable("kultivator.duel.ui.screen.controls", DuelHud.parryKey(),
				DuelKeys.CAMERA.isUnbound() ? Component.literal("-") : DuelKeys.CAMERA.getTranslatedKeyMessage());
		Brush.fit(g, font, keys, left, bottom - 10.0F, right - left, Palette.INK_FAINT, 0.5F, 0.4F, false);
	}

	private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY, int mid) {
		float lw = mid - left - 6.0F;
		heading(g, Component.translatable(pickingTwo
				? (firstPick < 0 ? "kultivator.duel.ui.screen.pick_first" : "kultivator.duel.ui.screen.pick_second")
				: "kultivator.duel.ui.screen.challenge"), left, top + 12.0F, lw);
		if (candidates.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(pickingTwo ? "kultivator.duel.ui.screen.no_disciples" : "kultivator.duel.ui.screen.nobody"),
					left + 2.0F, listTop + 2.0F, lw - 4.0F, Palette.INK_SOFT, 0.6F, false);
			return;
		}
		int shown = Math.min(listRows, candidates.size());
		for (int i = 0; i < shown; i++) {
			Candidate c = candidates.get(i);
			int y = listTop + i * ROW;
			boolean over = mouseX >= left && mouseX < mid - 6 && mouseY >= y && mouseY < y + ROW - 1;
			boolean picked = c.id() == firstPick;
			if (over || picked) {
				g.fill(left, y, mid - 6, y + ROW - 1, Palette.argb(picked ? 0x70 : 0x40, picked ? Palette.SEAL : Palette.JADE_PALE));
			}
			int glyph = switch (c.kind()) {
				case "player" -> Glyphs.STATUS;
				case "murid" -> Glyphs.TALENT;
				case "perampok" -> Glyphs.TECHNIQUES;
				default -> Glyphs.PATH;
			};
			Glyphs.tab(g, glyph, left + 6.0F, y + 6.0F, 9.0F, Palette.onPaper(c.rgb()));
			float nameW = Brush.fit(g, font, c.name(), left + 13.0F, y + 1.0F, lw * 0.55F, Palette.INK, 0.7F, 0.5F, false);
			Brush.fit(g, font, c.detail(), left + 15.0F + nameW, y + 2.0F, lw - 30.0F - nameW, Palette.INK_SOFT, 0.55F, 0.4F, false);
			Brush.fit(g, font, Component.literal(Math.round(c.distance()) + " b"), mid - 28.0F, y + 2.0F, 20.0F, Palette.INK_FAINT, 0.55F, 0.4F, false);
			if (over) {
				tooltip = List.of(c.name(), c.detail(), Component.translatable(pickingTwo ? "kultivator.duel.ui.screen.pick.tip"
						: "kultivator.duel.ui.screen.row.tip").withStyle(ChatFormatting.GRAY));
			}
		}
		if (candidates.size() > shown) {
			Brush.fit(g, font, Component.translatable("kultivator.duel.ui.screen.more", candidates.size() - shown), left + 2.0F, listTop + shown * ROW,
					lw, Palette.INK_FAINT, 0.5F, 0.4F, false);
		}
	}

	private void drawRight(GuiGraphicsExtractor g, int mid) {
		float rx = mid + 4.0F;
		float rw = right - rx;
		float y = top + 12.0F;
		List<DuelClient.Offer> offers = DuelClient.offers();
		DuelPayloads.DuelSessionSync s = DuelClient.localSession();
		if (!offers.isEmpty()) {
			DuelClient.Offer o = offers.get(0);
			y = heading(g, Component.translatable("kultivator.duel.ui.screen.incoming", o.ticksLeft() / 20), rx, y, rw);
			Brush.fit(g, font, o.offer().name(), rx, y, rw, Palette.SEAL_DARK, 0.75F, 0.5F, false);
			Brush.fit(g, font, ChallengeOfferToast.rulesLine(o.offer().rules()), rx, y + 9.0F, rw, Palette.INK_SOFT, 0.5F, 0.38F, false);
			Brush.fit(g, font, ChallengeOfferToast.stakeLine(o.offer()), rx, y + 16.0F, rw, Palette.INK_SOFT, 0.5F, 0.38F, false);
			y += 40.0F;
		}
		if (s != null && s.phase() != DuelIds.PHASE_ENDED) {
			LocalPlayer player = Minecraft.getInstance().player;
			boolean isA = player != null && s.aId() == player.getId();
			Component opponent = isA ? s.bName() : s.aName();
			y = heading(g, Component.translatable("kultivator.duel.ui.screen.running", Component.translatable("kultivator.duel.ui.phase." + s.phase())),
					rx, y, rw);
			Brush.fit(g, font, Component.translatable("kultivator.duel.ui.screen.versus", opponent), rx, y, rw, Palette.SEAL_DARK, 0.7F, 0.45F, false);
			y += 28.0F;
		}
		heading(g, Component.translatable("kultivator.duel.ui.screen.settings"), rx, y, rw);
		DuelPayloads.DuelResult last = DuelClient.lastResult();
		if (last != null) {
			Component line = Component.translatable("kultivator.duel.ui.screen.last", last.title(),
					Component.translatable("kultivator.duel.ui.reason." + Math.max(0, Math.min(6, last.reason()))));
			Brush.fit(g, font, line, rx, bottom - 40.0F, rw, Palette.INK_SOFT, 0.5F, 0.38F, false);
		}
	}

	@Override
	protected boolean clickPaper(double mouseX, double mouseY) {
		int mid = left + Math.round((right - left) * 0.56F);
		if (mouseX < left || mouseX >= mid - 6 || mouseY < listTop) {
			return false;
		}
		int index = (int) ((mouseY - listTop) / ROW);
		if (index < 0 || index >= Math.min(listRows, candidates.size())) {
			return false;
		}
		Candidate c = candidates.get(index);
		UiSound.press();
		if (pickingTwo) {
			if (firstPick < 0) {
				firstPick = c.id();
				return true;
			}
			if (firstPick == c.id()) {
				firstPick = -1;
				return true;
			}
			Candidate first = null;
			for (Candidate other : candidates) {
				if (other.id() == firstPick) {
					first = other;
				}
			}
			Component firstName = first == null ? Component.literal("?") : first.name();
			minecraft.gui.setScreen(new ChallengeScreen(this, firstPick, c.id(), Component.translatable("kultivator.duel.ui.challenge.pair_names",
					firstName, c.name()), DuelIds.PAIR_TONTON, -1));
			pickingTwo = false;
			firstPick = -1;
			return true;
		}
		int realm = -1;
		Minecraft mc = Minecraft.getInstance();
		Entity entity = mc.level == null ? null : mc.level.getEntity(c.id());
		if (entity instanceof DiscipleEntity disciple) {
			realm = disciple.syncedRealm();
		} else if (entity instanceof RogueCultivatorEntity rogue) {
			realm = rogue.realm();
		}
		minecraft.gui.setScreen(new ChallengeScreen(this, c.id(), -1, c.name(), c.pair(), realm));
		return true;
	}
}
