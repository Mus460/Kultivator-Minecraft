package dev.kultivator.client.formasi;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.formasi.FormasiItems;
import dev.kultivator.formasi.keeper.KeeperOrder;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.Flag;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Scope;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The bamboo slip (DESIGN 24.7.1): the detail view of one core, or of one Kitab entry. Name, grade stars, family and the
 * Sumber line; the mini pattern diagram with its bill; in core mode the state and reason, the Daya bar with its upkeep
 * breakdown, the P breakdown, live numbers, the keepers with their order coins and the controls; in Kitab mode the
 * numbers at the current hall level, the lock reason and the hologram talismans. Scrolls with the wheel (key 100). It is
 * the detail view of Sekte › Formasi: the host's back control and Esc close it (43.6 {@code rinci}). Text is 1.0 (43.0.5);
 * a hologram closes the Gulungan so it can be seen, with an action-bar line (43.13 R32).
 */
final class FormationSlip {

	private static final float PAD = 4.0F;
	private static float lastContent;
	private static boolean assigning;
	private static int anchorIndex;
	private static String lastKey = "";

	static void draw(FormasiPage page, GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, @Nullable CompoundTag core,
			@Nullable FormationSpec kitab) {
		Font font = page.font();
		FormationSpec spec = core != null ? dev.kultivator.formasi.FormationRegistry.byId(core.getStringOr("spec", "")) : kitab;
		String key = core != null ? "c" + core.getIntOr("id", -1) : "k" + (kitab == null ? "" : kitab.id());
		if (!key.equals(lastKey)) {
			lastKey = key;
			assigning = false;
			lastContent = 0.0F;
		}
		// Bamboo backing.
		g.fill(x, y, x + w, y + h, Palette.argb(0xE8, Palette.BAMBOO));
		for (int sx = x + 14; sx < x + w; sx += 14) {
			g.fill(sx, y, sx + 1, y + h, Palette.argb(0x40, Palette.BAMBOO_KNOT));
		}
		Brush.frame(g, x, y, x + w, y + h, Palette.argb(0xC0, Palette.CORD));
		float offset = page.clampScroll(100, lastContent, h);
		float top = y;
		float bottom = y + h;
		float cx = x + PAD;
		float cw = w - PAD * 2 - 3.0F;
		float cursor = y + PAD - offset;
		float start = cursor;
		page.scrollArea(x, y, x + w, y + h);
		if (spec == null) {
			page.wrap(Component.translatable("kultivator.formasiui.unknown_spec", core == null ? "?" : core.getStringOr("spec", "?")), cx,
					cursor + 4.0F, cw, Palette.DANGER);
			lastContent = 40.0F;
			return;
		}
		// ---- header
		int familyColor = Palette.opaque(spec.family().color());
		if (visible(cursor, 12.0F, top, bottom)) {
			page.line(Brush.bold(spec.name()), cx, cursor, cw, Palette.onPaper(familyColor & 0xFFFFFF));
		}
		cursor += 11.0F;
		if (visible(cursor, 9.0F, top, bottom)) {
			MutableComponent stars = Component.literal("★".repeat(Math.max(1, spec.grade().number())));
			Component line = Component.translatable("kultivator.formasiui.slip.line", stars, spec.grade().label(), spec.family().label(),
					Component.translatable(spec.scope() == Scope.TERRITORY ? "kultivator.formasiui.slip.scope.territory" : "kultivator.formasiui.slip.scope.core"));
			page.line(line, cx, cursor, cw, Palette.onPaper(spec.grade().color() & 0xFFFFFF));
		}
		cursor += 11.0F;
		Component source = spec.source();
		String sourceText = source.getString();
		boolean inspired = sourceText.contains("Terinspirasi") || sourceText.contains("Inspired");
		MutableComponent sourceLine = Component.translatable("kultivator.formasiui.slip.source", source);
		if (inspired) {
			sourceLine = sourceLine.withStyle(ChatFormatting.ITALIC);
		}
		cursor += drawWrap(g, font, sourceLine, cx, cursor, cw, Palette.INK_SOFT, 0.5F, top, bottom) + 2.0F;
		cursor += drawWrap(g, font, spec.desc(), cx, cursor, cw, Palette.INK, 0.55F, top, bottom) + 3.0F;
		// ---- diagram and bill
		int rot = core != null ? core.getIntOr("rot", 0) : 0;
		if (spec.pattern().size() > 0) {
			cursor += BlueprintDiagram.draw(page, g, spec, rot, cx, cursor, Math.round(cw), mouseX, mouseY, top, bottom);
			cursor += BlueprintDiagram.legend(page, g, spec, cx, cursor, Math.round(cw), top, bottom) + 4.0F;
		}
		// ---- mode
		if (core != null) {
			cursor = coreMode(page, g, font, spec, core, cx, cursor, cw, mouseX, mouseY, top, bottom);
		} else {
			cursor = kitabMode(page, g, font, spec, cx, cursor, cw, mouseX, mouseY, top, bottom);
		}
		if (spec.family() == Family.GABUNGAN) {
			cursor = grand(page, g, font, spec, cx, cursor, cw, top, bottom);
		}
		lastContent = cursor - start + PAD * 2.0F;
		FormasiPage.bead(g, x + w, y, y + h, offset, Math.max(0.0F, lastContent - h));
	}

	// ================================================================== core mode

	private static float coreMode(FormasiPage page, GuiGraphicsExtractor g, Font font, FormationSpec spec, CompoundTag core, float cx, float cursor,
			float cw, int mouseX, int mouseY, float top, float bottom) {
		int id = core.getIntOr("id", -1);
		int state = core.getIntOr("st", 0);
		boolean edit = page.canEdit();
		cursor = rule(g, cx, cursor, cw, top, bottom);
		// State line, reason and missing blocks.
		if (visible(cursor, 10.0F, top, bottom)) {
			FormasiPage.seal(g, font, FormasiPage.stateName(state), cx + 6.0F, cursor + 5.0F, 6.0F, FormasiPage.stateColor(state));
			MutableComponent line = FormasiPage.stateName(state).copy();
			if (core.getBooleanOr("idle", false)) {
				line.append(" · ").append(Component.translatable("kultivator.formasiui.tag.idle"));
			}
			if (core.getBooleanOr("crack", false)) {
				line.append(" · ").append(Component.translatable("kultivator.formasiui.tag.crack"));
			}
			if (core.getBooleanOr("uji", false)) {
				line.append(" · ").append(Component.translatable("kultivator.formasiui.tag.test"));
			}
			page.line(Brush.bold(line), cx + 15.0F, cursor + 1.0F, cw - 15.0F, Palette.INK);
		}
		cursor += 12.0F;
		Component why = FormasiPage.text(core, "why");
		if (!why.getString().isEmpty()) {
			cursor += drawWrap(g, font, why, cx, cursor, cw, state == 1 ? Palette.DANGER : Palette.INK_SOFT, 0.55F, top, bottom) + 2.0F;
		}
		if (state == 1 && core.getIntOr("miss", 0) > 0) {
			cursor += drawWrap(g, font, Component.translatable("kultivator.formasiui.slip.missing", core.getIntOr("miss", 0)), cx, cursor, cw,
					Palette.DANGER, 0.55F, top, bottom) + 2.0F;
		}
		// Daya bar and upkeep breakdown.
		long daya = core.getLongOr("daya", 0L);
		long cap = Math.max(1L, core.getLongOr("cap", 1L));
		if (visible(cursor, 15.0F, top, bottom)) {
			page.line(Component.translatable("kultivator.formasiui.slip.daya", FormasiPage.daya(daya), FormasiPage.daya(cap)), cx, cursor, cw, Palette.INK);
			FormasiPage.bar(g, cx, cursor + 10.0F, cw, 4.0F, daya / (float) cap, Palette.QI);
		}
		cursor += 17.0F;
		Component upkeep = Component.translatable("kultivator.formasiui.slip.upkeep", FormasiPage.hours(core.getDoubleOr("hrs", 0.0D)),
				FormasiPage.decimal(spec.upkeep()), FormasiPage.decimal(core.getDoubleOr("vein", 1.0D)), FormasiPage.decimal(core.getDoubleOr("keepf", 1.0D)),
				FormasiPage.decimal(core.getDoubleOr("upk", 0.0D)));
		cursor += drawWrap(g, font, upkeep, cx, cursor, cw, Palette.INK_SOFT, 0.5F, top, bottom) + 2.0F;
		// P breakdown.
		CompoundTag pk = core.getCompoundOrEmpty("pk");
		Component power = Component.translatable("kultivator.formasiui.slip.power", FormasiPage.decimal(core.getDoubleOr("pow", 1.0D)),
				FormasiPage.decimal(pk.getDoubleOr("q", 0.0D)), FormasiPage.decimal(pk.getDoubleOr("k", 0.0D)), FormasiPage.decimal(pk.getDoubleOr("f", 0.0D)),
				FormasiPage.decimal(pk.getDoubleOr("a", 0.0D)));
		cursor += drawWrap(g, font, power, cx, cursor, cw, Palette.INK_SOFT, 0.5F, top, bottom) + 2.0F;
		// Live numbers.
		cursor += drawWrap(g, font, live(spec, core.getDoubleOr("r", spec.radiusAt(page.hallLevel())), core.getDoubleOr("pow", 1.0D)), cx, cursor, cw,
				Palette.INK, 0.52F, top, bottom) + 4.0F;
		// Controls.
		float half = (cw - 3.0F) / 2.0F;
		if (visible(cursor, 14.0F, top, bottom)) {
			boolean on = core.getBooleanOr("on", true);
			page.coin(g, cx, cursor, half, 14.0F, Component.translatable(on ? "kultivator.formasiui.slip.on" : "kultivator.formasiui.slip.off"), on, edit,
					() -> page.send("toggle:" + id), mouseX, mouseY, List.of(Component.translatable("kultivator.formasiui.slip.toggle.tip")));
			boolean draw = core.getBooleanOr("draw", true);
			page.coin(g, cx + half + 3.0F, cursor, half, 14.0F, Component.translatable("kultivator.formasiui.slip.draw"), draw, edit,
					() -> page.send("draw:" + id + ":" + (draw ? 0 : 1)), mouseX, mouseY, List.of(Component.translatable("kultivator.formasiui.slip.draw.tip")));
		}
		cursor += 17.0F;
		BlockPos eye = new BlockPos(core.getIntOr("x", 0), core.getIntOr("y", 0), core.getIntOr("z", 0));
		if (visible(cursor, 14.0F, top, bottom)) {
			page.talisman(g, cx, cursor, half, 14.0F, Component.translatable("kultivator.formasiui.slip.fill"), edit, () -> page.send("fill:" + id), mouseX,
					mouseY, List.of(Component.translatable("kultivator.formasiui.slip.fill.tip")));
			page.talisman(g, cx + half + 3.0F, cursor, half, 14.0F, Component.translatable("kultivator.formasiui.slip.mark"), true,
					() -> page.send("mark:" + id), mouseX, mouseY, List.of(Component.translatable("kultivator.formasiui.slip.mark.tip")));
		}
		cursor += 17.0F;
		if (visible(cursor, 14.0F, top, bottom)) {
			int coreRot = core.getIntOr("rot", 0);
			page.talisman(g, cx, cursor, half, 14.0F, Component.translatable("kultivator.formasiui.slip.view"), true, () -> hologram(spec, eye, coreRot),
					mouseX, mouseY, List.of(Component.translatable("kultivator.formasiui.slip.view.tip")));
			if (state == 1) {
				page.talisman(g, cx + half + 3.0F, cursor, half, 14.0F, Component.translatable("kultivator.formasiui.slip.view_missing"), true, () -> {
					page.send("mark:" + id);
					hologram(spec, eye, coreRot);
				}, mouseX, mouseY, List.of(Component.translatable("kultivator.formasiui.slip.view_missing.tip")));
			}
		}
		cursor += 18.0F;
		return keepers(page, g, font, spec, core, eye, cx, cursor, cw, mouseX, mouseY, top, bottom);
	}

	private static float keepers(FormasiPage page, GuiGraphicsExtractor g, Font font, FormationSpec spec, CompoundTag core, BlockPos eye, float cx,
			float cursor, float cw, int mouseX, int mouseY, float top, float bottom) {
		int id = core.getIntOr("id", -1);
		boolean edit = page.canEdit();
		ListTag keep = core.getListOrEmpty("keep");
		int max = spec.grade().maxKeepers();
		cursor = rule(g, cx, cursor, cw, top, bottom);
		if (visible(cursor, 12.0F, top, bottom)) {
			page.title(g, Component.translatable("kultivator.formasiui.slip.keepers", keep.size(), max), cx, cursor, cw, 1.0F);
		}
		cursor += 14.0F;
		for (Tag tag : keep) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			UUID soul = entry.read("u", UUIDUtil.CODEC).orElse(null);
			KeeperOrder order = KeeperOrder.byId(entry.getStringOr("order", "jaga"));
			if (order == null) {
				order = KeeperOrder.JAGA;
			}
			if (visible(cursor, 20.0F, top, bottom)) {
				boolean loaded = entry.contains("n");
				Brush.disc(g, cx + 6.0F, cursor + 9.0F, 5.5F, loaded ? Palette.JADE : Palette.JADE_DULL);
				Brush.centered(g, font, Component.literal(loaded ? entry.getStringOr("n", "?").substring(0, Math.min(1, entry.getStringOr("n", "?").length())) : "?"),
						cx + 6.0F, cursor + 5.0F, 0xFFFFF4D6, 1.0F, false);
				Component name = loaded ? Component.literal(entry.getStringOr("n", "?")) : Component.translatable("kultivator.formasiui.slip.keeper_away");
				Component realm = loaded ? Realm.byOrdinal(entry.getIntOr("realm", 0)).displayName() : Component.empty();
				page.line(name, cx + 14.0F, cursor, cw * 0.5F - 16.0F, Palette.INK);
				page.line(realm, cx + 14.0F, cursor + 10.0F, cw * 0.5F - 16.0F, Palette.INK_SOFT);
				float bx = cx + cw * 0.5F;
				float bw = (cw * 0.5F - 3.0F) / 2.0F;
				KeeperOrder next = order.next();
				if (soul != null) {
					page.coin(g, bx, cursor + 3.0F, bw, 14.0F, order.label(), true, edit, () -> page.send("order:" + id + ":" + soul + ":" + next.id()), mouseX,
							mouseY, List.of(Component.translatable("kultivator.formasiui.slip.order.tip", order.label(), next.label())));
					page.talisman(g, bx + bw + 3.0F, cursor + 3.0F, bw, 14.0F, Component.translatable("kultivator.formasiui.slip.release"), edit,
							() -> page.send("release:" + id + ":" + soul), mouseX, mouseY, null);
				}
			}
			cursor += 22.0F;
		}
		if (keep.size() < max && edit) {
			if (visible(cursor, 14.0F, top, bottom)) {
				page.talisman(g, cx, cursor, Math.min(120.0F, cw), 14.0F, Component.translatable("kultivator.formasiui.slip.assign"), true,
						() -> assigning = !assigning, mouseX, mouseY, List.of(Component.translatable("kultivator.formasiui.slip.assign.tip")));
			}
			cursor += 17.0F;
			if (assigning) {
				cursor += drawWrap(g, font, Component.translatable("kultivator.formasiui.slip.assign.hint"), cx, cursor, cw, Palette.INK_SOFT, 0.5F, top,
						bottom) + 2.0F;
				List<DiscipleEntity> near = eligible(eye);
				if (near.isEmpty()) {
					cursor += drawWrap(g, font, Component.translatable("kultivator.formasiui.slip.assign.none"), cx, cursor, cw, Palette.INK_FAINT, 0.5F, top,
							bottom) + 2.0F;
				}
				for (DiscipleEntity disciple : near) {
					if (visible(cursor, 14.0F, top, bottom)) {
						int entityId = disciple.getId();
						double distance = Math.sqrt(disciple.distanceToSqr(eye.getX() + 0.5D, eye.getY() + 0.5D, eye.getZ() + 0.5D));
						Component label = Component.translatable("kultivator.formasiui.slip.assign.row", disciple.getName(),
								Realm.byOrdinal(Math.max(0, disciple.syncedRealm())).displayName(), Math.round(distance));
						page.talisman(g, cx + 6.0F, cursor, cw - 6.0F, 14.0F, label, true, () -> {
							page.send("keeper:" + id + ":" + entityId + ":" + KeeperOrder.JAGA.id());
							assigning = false;
						}, mouseX, mouseY, null);
					}
					cursor += 16.0F;
				}
			}
		}
		return cursor;
	}

	/** My loaded disciples within 24 blocks of the eye (the server checks the 3-block Stay rule, realm and rank). */
	private static List<DiscipleEntity> eligible(BlockPos eye) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return List.of();
		}
		List<DiscipleEntity> list = new ArrayList<>(minecraft.level.getEntitiesOfClass(DiscipleEntity.class, new AABB(eye).inflate(24.0D),
				disciple -> disciple.isOwnedBy(player)));
		list.sort((a, b) -> Double.compare(a.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(eye)), b.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(eye))));
		return list.size() > 8 ? list.subList(0, 8) : list;
	}

	// ================================================================== Kitab mode

	private static float kitabMode(FormasiPage page, GuiGraphicsExtractor g, Font font, FormationSpec spec, float cx, float cursor, float cw, int mouseX,
			int mouseY, float top, float bottom) {
		cursor = rule(g, cx, cursor, cw, top, bottom);
		int level = page.hallLevel();
		Component numbers = Component.translatable("kultivator.formasiui.slip.kitab_numbers", Math.round(spec.radiusAt(level)), level,
				FormasiPage.decimal(spec.upkeep()), FormasiPage.decimal(spec.pulseTicks() / 20.0D), spec.minHallLevel(), spec.grade().maxKeepers(),
				spec.pattern().size());
		cursor += drawWrap(g, font, numbers, cx, cursor, cw, Palette.INK, 0.52F, top, bottom) + 2.0F;
		cursor += drawWrap(g, font, live(spec, spec.radiusAt(level), 1.0D), cx, cursor, cw, Palette.INK_SOFT, 0.5F, top, bottom) + 2.0F;
		List<Component> flags = new ArrayList<>();
		if (spec.has(Flag.FIXED_ORIENTATION)) {
			flags.add(Component.translatable("kultivator.formasiui.slip.flag.fixed"));
		}
		if (spec.has(Flag.STONE_ONLY) || spec.grade().stoneOnly()) {
			flags.add(Component.translatable("kultivator.formasiui.slip.flag.stone"));
		}
		if (spec.has(Flag.PERSONAL_OK)) {
			flags.add(Component.translatable("kultivator.formasiui.slip.flag.personal"));
		}
		if (spec.has(Flag.LEGACY_MIRROR)) {
			flags.add(Component.translatable("kultivator.formasiui.slip.flag.legacy"));
		}
		for (Component flag : flags) {
			cursor += drawWrap(g, font, Component.literal("· ").append(flag), cx, cursor, cw, Palette.INK_SOFT, 0.5F, top, bottom) + 1.0F;
		}
		cursor += drawWrap(g, font, spec.hint(), cx, cursor, cw, Palette.JADE_DARK, 0.5F, top, bottom) + 3.0F;
		Component lock = CodexPanel.lock(page, spec);
		if (lock != null) {
			cursor += drawWrap(g, font, lock, cx, cursor, cw, Palette.DANGER, 0.58F, top, bottom) + 3.0F;
		}
		if (spec.has(Flag.LEGACY_MIRROR) || spec.pattern().size() == 0) {
			return cursor;
		}
		float half = (cw - 3.0F) / 2.0F;
		LocalPlayer player = Minecraft.getInstance().player;
		if (visible(cursor, 14.0F, top, bottom) && player != null) {
			ListTag anchors = page.f().getListOrEmpty("anchors");
			// No built Titik Formasi (no sect template has one yet): the "Di Titik Formasi" talisman is hidden, the
			// hologram talisman takes the whole row and its tooltip says why.
			List<Component> frontTip = anchors.isEmpty()
					? List.of(Component.translatable("kultivator.formasiui.slip.front.tip"), Component.translatable("kultivator.sect_fix_feat.anchor.none"))
					: List.of(Component.translatable("kultivator.formasiui.slip.front.tip"));
			page.talisman(g, cx, cursor, anchors.isEmpty() ? cw : half, 14.0F, Component.translatable("kultivator.formasiui.slip.front"), true,
					() -> hologram(spec, PatternHologram.inFront(player), PatternHologram.facingRotation(player)), mouseX, mouseY, frontTip);
			if (!anchors.isEmpty()) {
				Component anchorLabel = Component.translatable("kultivator.formasiui.slip.anchor_n", anchorIndex % anchors.size() + 1, anchors.size());
				page.talisman(g, cx + half + 3.0F, cursor, half, 14.0F, anchorLabel, true, () -> {
					CompoundTag anchor = anchors.getCompoundOrEmpty(anchorIndex % anchors.size());
					anchorIndex++;
					hologram(spec, new BlockPos(anchor.getIntOr("x", 0), anchor.getIntOr("y", 0), anchor.getIntOr("z", 0)), -1);
				}, mouseX, mouseY, List.of(Component.translatable("kultivator.formasiui.slip.anchor.tip")));
			}
		}
		cursor += 17.0F;
		BlockPos looked = lookedEye();
		if (looked != null && page.canEdit()) {
			if (visible(cursor, 14.0F, top, bottom)) {
				page.talisman(g, cx, cursor, cw, 14.0F, Component.translatable("kultivator.formasiui.slip.target"), lock == null,
						() -> page.send("target:" + looked.getX() + ":" + looked.getY() + ":" + looked.getZ() + ":" + spec.id()), mouseX, mouseY,
						List.of(Component.translatable("kultivator.formasiui.slip.target.tip")));
			}
			cursor += 17.0F;
		}
		return cursor;
	}

	/** The Mata Formasi the crosshair rests on (within 8 blocks), or null. */
	private static @Nullable BlockPos lookedEye() {
		Minecraft minecraft = Minecraft.getInstance();
		HitResult hit = minecraft.hitResult;
		if (minecraft.level == null || minecraft.player == null || !(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = block.getBlockPos();
		if (pos.distToCenterSqr(minecraft.player.position()) > 64.0D || !minecraft.level.getBlockState(pos).is(FormasiItems.FORMATION_EYE)) {
			return null;
		}
		return pos;
	}

	// ================================================================== grand arrays (A1, A2)

	private static float grand(FormasiPage page, GuiGraphicsExtractor g, Font font, FormationSpec spec, float cx, float cursor, float cw, float top,
			float bottom) {
		CompoundTag grand = page.b().getCompoundOrEmpty("grand");
		cursor = rule(g, cx, cursor, cw, top, bottom);
		if ("ten_thousand_immortals".equals(spec.id())) {
			int bits = grand.getIntOr("a1", -1);
			String[] parts = {"agung", "taiji", "liangyi", "sixiang"};
			for (int i = 0; i < parts.length; i++) {
				boolean ok = bits >= 0 && (bits & (1 << i)) != 0;
				cursor += drawWrap(g, font, Component.translatable("kultivator.formasiui.grand.part", Component.literal(ok ? "✔" : "✘"),
						Component.translatable("kultivator.formasiui.grand." + parts[i])), cx, cursor, cw, ok ? Palette.JADE_DARK : Palette.INK_FAINT, 0.52F, top,
						bottom) + 1.0F;
			}
			boolean complete = bits >= 0 && (bits & 16) != 0;
			cursor += drawWrap(g, font, Component.translatable(complete ? "kultivator.formasiui.grand.a1_complete" : "kultivator.formasiui.grand.a1_incomplete"),
					cx, cursor, cw, complete ? Palette.GOLD_DARK : Palette.INK_SOFT, 0.55F, top, bottom) + 3.0F;
		} else {
			Component line = Component.translatable("kultivator.formasiui.grand.a2", grand.getIntOr("n", 0), grand.getIntOr("built", 0),
					grand.getIntOr("arts", 0), Component.translatable(grand.getBooleanOr("a2", false) ? "kultivator.formasiui.on" : "kultivator.formasiui.off"));
			cursor += drawWrap(g, font, line, cx, cursor, cw, Palette.INK, 0.55F, top, bottom) + 3.0F;
		}
		return cursor;
	}

	// ================================================================== helpers

	/** The live numbers of a spec at radius {@code r} and power {@code p}: radius, pulse, and damage/targets when the card has them. */
	private static Component live(FormationSpec spec, double r, double p) {
		MutableComponent line = Component.translatable("kultivator.formasiui.slip.live", Math.round(r), FormasiPage.decimal(spec.pulseTicks() / 20.0D),
				FormasiPage.decimal(p));
		if (spec.params().has("dmg")) {
			line.append(Component.translatable("kultivator.formasiui.slip.live.dmg", FormasiPage.decimal(spec.params().get("dmg", 0.0D) * p)));
		}
		if (spec.params().has("targets")) {
			line.append(Component.translatable("kultivator.formasiui.slip.live.targets", spec.params().getInt("targets", 0)));
		}
		return line;
	}

	/** Shows the hologram and closes the Gulungan so it can be seen (on purpose, 43.13 R32; the action bar says why). */
	private static void hologram(FormationSpec spec, BlockPos anchor, int rot) {
		PatternHologram.show(spec, anchor, rot, PatternHologram.DEFAULT_MINUTES);
		FormasiPage.closeToWorld(Component.translatable("kultivator.gui43.m07.hologram", spec.name()));
	}

	private static boolean visible(float y, float height, float top, float bottom) {
		return y + height >= top && y <= bottom;
	}

	/** Wrapped text at 1.0 (43.0.5; {@code scale} is ignored) drawn only when on screen; returns its height either way. */
	private static float drawWrap(GuiGraphicsExtractor g, Font font, Component text, float x, float y, float width, int color, float scale, float top,
			float bottom) {
		float height = Brush.wrapHeight(font, text, width, 1.0F);
		if (visible(y, height, top, bottom)) {
			Brush.wrap(g, font, text, x, y, width, color, 1.0F, false);
		}
		return height;
	}

	/** A thin gold rule; returns the new cursor. */
	private static float rule(GuiGraphicsExtractor g, float x, float y, float width, float top, float bottom) {
		if (visible(y, 4.0F, top, bottom)) {
			Brush.rect(g, x, y + 1.0F, x + width, y + 1.6F, Palette.argb(0x90, Palette.GOLD_DARK));
		}
		return y + 4.0F;
	}

	private FormationSlip() {
	}
}
