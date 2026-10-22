package dev.kultivator.client.dunia.naga;

import dev.kultivator.dunia.naga.NagaKind;
import dev.kultivator.dunia.naga.NagaLiarEntity;
import dev.kultivator.dunia.naga.NagaPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The HUD of module B (DESIGN 34.5.11): the Pertarungan Kehendak ring and the Napas Naga gauge while riding a Jiao.
 * <p>
 * The contest: a ring at the screen centre; the dragon's will is an arc of its colour that wanders around the ring (a
 * sum of three seeded sines, faster for the thunder Jiao); the player's arc (gold) turns with the view (3° on the ring
 * per degree of yaw) and only counts while the use key is held. Every client tick the overlap of the two arcs is sampled;
 * every 10 ticks the average goes to the server ({@link NagaPayloads.Report}), which decides (60 % wins). A bar under the
 * ring shows the overlap so far and the time left. Drawn with fills only (no texture, no shader).
 */
public final class NagaHud implements HudElement {

	private static final int RING = 52;
	private static final int PLAYER_ARC = 40;
	private static final int GOLD = 0xFFD27A;

	/** The running contest on this client (null when none). */
	private static @Nullable Contest contest;

	private static final class Contest {
		final int session;
		final int dragonId;
		final NagaKind kind;
		final int seed;
		final int ticks;
		final int arc;
		final float startYaw;
		final float offset;
		int age;
		float sum;
		int samples;
		float totalSum;
		int totalSamples;
		int endAge = -1;
		boolean won;
		int resultPermille;

		Contest(NagaPayloads.Contest payload, float yaw) {
			this.session = payload.session();
			this.dragonId = payload.dragonId();
			this.kind = NagaKind.byOrdinal(payload.kind());
			this.seed = payload.seed();
			this.ticks = Math.max(20, payload.ticks());
			this.arc = Mth.clamp(payload.value(), 20, 90);
			this.startYaw = yaw;
			// the player's arc starts on the dragon's
			this.offset = will(0.0F);
		}

		/** The dragon's will on the ring at {@code t} ticks (degrees). */
		float will(float t) {
			float speed = kind == NagaKind.PETIR ? 1.35F : kind == NagaKind.AWAN ? 1.15F : 1.0F;
			float s1 = (seed & 0xFF) / 40.0F;
			float s2 = ((seed >> 8) & 0xFF) / 40.0F;
			float s3 = ((seed >> 16) & 0xFF) / 40.0F;
			return 140.0F * Mth.sin(t * 0.045F * speed + s1) + 70.0F * Mth.sin(t * 0.11F * speed + s2) + 35.0F * Mth.sin(t * 0.23F * speed + s3);
		}

		float player(float yaw) {
			return offset + Mth.wrapDegrees(yaw - startYaw) * 3.0F;
		}
	}

	static void onContest(NagaPayloads.Contest payload) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		if (payload.state() == NagaPayloads.CONTEST_START) {
			contest = new Contest(payload, minecraft.player.getYRot());
			return;
		}
		Contest current = contest;
		if (current == null || current.session != payload.session()) {
			return;
		}
		if (payload.state() == NagaPayloads.CONTEST_ABORT) {
			contest = null;
			return;
		}
		current.won = payload.state() == NagaPayloads.CONTEST_WON;
		current.resultPermille = payload.value();
		current.endAge = current.age;
	}

	static void clear() {
		contest = null;
		dangerAt = null;
		dangerTicks = 0;
	}

	/** Every client tick: sample the overlap, report every 10 ticks. */
	static void tick(Minecraft minecraft) {
		if (!minecraft.isPaused()) {
			dangerTick(minecraft);
		}
		Contest current = contest;
		if (current == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		current.age++;
		if (current.endAge >= 0) {
			if (current.age - current.endAge > 40) {
				contest = null;
			}
			return;
		}
		if (current.age > current.ticks + 30) {
			contest = null;
			return;
		}
		if (current.age > current.ticks) {
			return;
		}
		boolean holding = minecraft.options.keyUse.isDown();
		float overlap = holding ? overlap(current.player(minecraft.player.getYRot()), current.will(current.age), current.arc) : 0.0F;
		current.sum += overlap;
		current.samples++;
		current.totalSum += overlap;
		current.totalSamples++;
		if (current.samples >= 10 || current.age == current.ticks) {
			int permille = Math.round(current.sum / current.samples * 1000.0F);
			if (ClientPlayNetworking.canSend(NagaPayloads.Report.TYPE)) {
				ClientPlayNetworking.send(new NagaPayloads.Report(current.session, permille, current.samples));
			}
			current.sum = 0.0F;
			current.samples = 0;
		}
	}

	/** The share of the player's arc that lies on the dragon's (0..1). */
	static float overlap(float player, float will, int arc) {
		float diff = Math.abs(Mth.wrapDegrees(player - will));
		float half = (PLAYER_ARC + arc) * 0.5F;
		if (diff >= half) {
			return 0.0F;
		}
		float covered = Math.min(half - diff, Math.min(PLAYER_ARC, arc));
		return Mth.clamp(covered / Math.min(PLAYER_ARC, arc), 0.0F, 1.0F);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		Contest current = contest;
		if (current != null) {
			drawContest(g, minecraft, current, partial);
		}
		// the taming contest above is input and always shows; the riding stamina follows the "HUD Kultivator" coin (audit M5, S30)
		if (dev.kultivator.client.EffectQuality.hudVisible() && minecraft.player.getVehicle() instanceof NagaLiarEntity dragon && dragon.isTamed()) {
			drawStamina(g, minecraft, dragon);
		}
		if (dangerTicks > 0 && dangerAt != null) {
			drawDanger(g, minecraft, partial);
		}
	}

	// ================================================================== the danger warning (detail pass)

	/** The telegraphed danger the local player stands in (or a breath aimed at them), kept half a second after it ends. */
	private static @Nullable net.minecraft.world.phys.Vec3 dangerAt;
	private static int dangerTicks;
	private static final int DANGER_HOLD = 10;

	/**
	 * Every client tick: is the player inside a thunder mark or a whirl's telegraph, under a wild Jiao's breath aimed at
	 * them, or inside a cloud veil about to burst? The HUD then pulses red at the screen edges and points at it.
	 */
	private static void dangerTick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			dangerTicks = 0;
			return;
		}
		net.minecraft.world.phys.Vec3 at = minecraft.player.position();
		net.minecraft.world.phys.Vec3 threat = NagaStrikes.threat(at);
		if (threat == null) {
			int id = minecraft.player.getId();
			for (NagaLiarEntity dragon : minecraft.level.getEntitiesOfClass(NagaLiarEntity.class, minecraft.player.getBoundingBox().inflate(48.0D),
					entity -> entity.isAlive() && !entity.isTamed())) {
				int skill = dragon.skill();
				int t = dragon.skillTicks();
				boolean breath = skill == NagaLiarEntity.SKILL_BREATH && dragon.aimId() == id
						&& t < NagaLiarEntity.BREATH_TELEGRAPH + NagaLiarEntity.BREATH_ACTIVE;
				boolean veil = skill == NagaLiarEntity.SKILL_SPECIAL && dragon.kind() == NagaKind.AWAN && t < 22
						&& dragon.distanceToSqr(minecraft.player) < NagaLiarEntity.VEIL_RADIUS * NagaLiarEntity.VEIL_RADIUS;
				boolean bite = skill == NagaLiarEntity.SKILL_BITE && dragon.aimId() == id;
				if (breath || veil || bite) {
					threat = dragon.position();
					break;
				}
			}
		}
		if (threat != null) {
			dangerAt = threat;
			dangerTicks = DANGER_HOLD;
		} else if (dangerTicks > 0) {
			dangerTicks--;
		}
	}

	private static void drawDanger(GuiGraphicsExtractor g, Minecraft minecraft, float partial) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		float time = minecraft.player.tickCount + partial;
		float pulse = dev.kultivator.client.EffectQuality.reduceMotion() ? 0.8F : 0.6F + 0.4F * Mth.sin(time * 0.5F);
		float fade = Mth.clamp((dangerTicks - 1 + partial) / DANGER_HOLD, 0.0F, 1.0F);
		int strips = 6;
		for (int i = 0; i < strips; i++) {
			int a = Math.round(90 * pulse * fade * (1.0F - i / (float) strips));
			if (a <= 0) {
				continue;
			}
			int c = ARGB.color(a, 0xFF3020);
			int s = i * 4;
			g.fill(s, 0, s + 4, h, c);
			g.fill(w - s - 4, 0, w - s, h, c);
			g.fill(0, s, w, s + 3, c);
			g.fill(0, h - s - 3, w, h - s, c);
		}
		// the pointer: where it comes from, relative to where you look
		double dx = dangerAt.x - minecraft.player.getX();
		double dz = dangerAt.z - minecraft.player.getZ();
		float toward = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		float relative = Mth.wrapDegrees(toward - Mth.lerp(partial, minecraft.player.yRotO, minecraft.player.getYRot())) * Mth.DEG_TO_RAD;
		int radius = 46;
		int px = w / 2 + Math.round(Mth.sin(relative) * radius);
		int py = h / 2 - Math.round(Mth.cos(relative) * radius);
		int color = ARGB.color(Math.round(255 * fade), 0xFFD060);
		g.centeredText(minecraft.font, Component.literal("⚠"), px, py - 4, color);
		g.centeredText(minecraft.font, Component.translatable("kultivator.naga.hud.danger"), w / 2, h / 2 + 30, ARGB.color(Math.round(230 * fade * pulse),
				0xFF8060));
	}

	private static void drawContest(GuiGraphicsExtractor g, Minecraft minecraft, Contest current, float partial) {
		Font font = minecraft.font;
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		float t = Math.min(current.ticks, current.age + partial);
		float fade = current.endAge >= 0 ? Mth.clamp(1.0F - (current.age - current.endAge + partial) / 40.0F, 0.0F, 1.0F) : 1.0F;
		// the ring of dots
		for (int deg = 0; deg < 360; deg += 6) {
			dot(g, cx, cy, deg, RING, 1, ARGB.color(Math.round(90 * fade), 0xFFFFFF));
		}
		float will = current.will(t);
		int kindRgb = current.kind.glow;
		for (int d = -current.arc / 2; d <= current.arc / 2; d += 2) {
			dot(g, cx, cy, will + d, RING, 2, ARGB.color(Math.round(230 * fade), kindRgb));
			dot(g, cx, cy, will + d, RING + 4, 1, ARGB.color(Math.round(110 * fade), kindRgb));
		}
		boolean holding = minecraft.options.keyUse.isDown();
		float player = current.player(Mth.lerp(partial, minecraft.player.yRotO, minecraft.player.getYRot()));
		int playerRgb = holding ? GOLD : 0x8A7A50;
		for (int d = -PLAYER_ARC / 2; d <= PLAYER_ARC / 2; d += 2) {
			dot(g, cx, cy, player + d, RING - 6, 2, ARGB.color(Math.round(230 * fade), playerRgb));
		}
		// the dragon's eye at the will, the silk knot at the player's arc
		dot(g, cx, cy, will, RING, 4, ARGB.color(Math.round(255 * fade), 0xFFFFFF));
		dot(g, cx, cy, player, RING - 6, 3, ARGB.color(Math.round(255 * fade), 0xFFF2C0));
		// the overlap bar and the time
		float share = current.totalSamples == 0 ? 0.0F : current.totalSum / current.totalSamples;
		int barW = 120;
		int bx = cx - barW / 2;
		int by = cy + RING + 18;
		g.fill(bx - 1, by - 1, bx + barW + 1, by + 6, ARGB.color(Math.round(150 * fade), 0x000000));
		int fill = Math.round(barW * share);
		g.fill(bx, by, bx + fill, by + 5, ARGB.color(Math.round(220 * fade), share >= 0.6F ? 0x9FFF9F : GOLD));
		int mark = bx + Math.round(barW * 0.6F);
		g.fill(mark, by - 2, mark + 1, by + 7, ARGB.color(Math.round(230 * fade), 0xFFFFFF));
		float left = Math.max(0.0F, (current.ticks - t) / 20.0F);
		Component title = current.endAge >= 0
				? Component.translatable(current.won ? "kultivator.naga.hud.won" : "kultivator.naga.hud.lost", current.resultPermille / 10)
				: Component.translatable("kultivator.naga.hud.title", String.format(java.util.Locale.ROOT, "%.1f", left));
		g.centeredText(font, title, cx, cy - RING - 16, ARGB.color(Math.round(255 * fade), current.endAge >= 0 ? (current.won ? 0x9FFF9F
				: 0xFF8080) : kindRgb));
		if (current.endAge < 0) {
			g.centeredText(font, Component.translatable(holding ? "kultivator.naga.hud.turn" : "kultivator.naga.hud.hold"), cx, by + 10,
					ARGB.color(Math.round(220 * fade), holding ? 0xE0E0E0 : 0xFFB060));
		}
	}

	private static void drawStamina(GuiGraphicsExtractor g, Minecraft minecraft, NagaLiarEntity dragon) {
		int w = 92;
		int x = g.guiWidth() / 2 - w / 2;
		int y = g.guiHeight() - 59;
		float stamina = dragon.stamina();
		boolean tired = dragon.hasFlag(NagaLiarEntity.FLAG_TIRED);
		int rgb = tired ? (dragon.tickCount / 5 % 2 == 0 ? 0xFF6040 : 0xFFB080) : dragon.kind().glow;
		g.fill(x - 1, y - 1, x + w + 1, y + 4, 0xA0000000);
		g.fill(x, y, x + Math.round(w * stamina), y + 3, ARGB.color(230, rgb));
		g.centeredText(minecraft.font, Component.translatable(tired ? "kultivator.naga.hud.tired" : "kultivator.naga.hud.stamina",
				Math.round(stamina * 100.0F)), g.guiWidth() / 2, y - 10, ARGB.color(230, rgb));
	}

	private static void dot(GuiGraphicsExtractor g, int cx, int cy, float deg, int radius, int size, int color) {
		float rad = (deg - 90.0F) * Mth.DEG_TO_RAD;
		int x = cx + Math.round(Mth.cos(rad) * radius);
		int y = cy + Math.round(Mth.sin(rad) * radius);
		g.fill(x - size / 2, y - size / 2, x - size / 2 + Math.max(1, size), y - size / 2 + Math.max(1, size), color);
	}
}
