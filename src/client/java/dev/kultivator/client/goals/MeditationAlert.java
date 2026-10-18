package dev.kultivator.client.goals;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.elite.EliteData;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The meditation danger alert (DESIGN 19.3.15, setting "Peringatan bahaya", default on): every 10 ticks while meditating
 * it looks for visible monsters within 12 blocks (never the heart demon or the player's own pets). A new one rings a
 * quiet bell, reddens the screen edge it comes from and writes "Bahaya: Zombie 7 blok" above the action bar (elites:
 * "Bahaya: Binatang Iblis (Zombie) 7 blok"); while one stays within 6 blocks the alert repeats every 5 s.
 * <p>
 * Sumber: game design (UX).
 */
public final class MeditationAlert implements HudElement {

	public static final double RANGE = 12.0D;
	public static final double CLOSE = 6.0D;
	private static final long SHOW_MILLIS = 3000L;
	private static final long REPING_MILLIS = 5000L;
	private static final Identifier HEART_DEMON = Kultivator.id("heart_demon");

	private static final Set<Integer> SEEN = new HashSet<>();
	private static int ticks;
	private static long shownUntil;
	private static long lastPing;
	private static @Nullable Component text;
	/** Bearing of the mob relative to the view, radians (0 ahead, positive to the right). */
	private static double bearing;

	/** Every client tick (works every 10th). */
	static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || !ClientCultivation.data().meditating() || !EffectQuality.dangerAlert()) {
			SEEN.clear();
			return;
		}
		if (++ticks % 10 != 0) {
			return;
		}
		List<Mob> mobs = minecraft.level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE), mob -> threat(player, mob));
		Mob nearest = null;
		double best = Double.MAX_VALUE;
		Set<Integer> present = new HashSet<>();
		for (Mob mob : mobs) {
			double distance = mob.distanceTo(player);
			if (distance > RANGE || !player.hasLineOfSight(mob)) {
				continue;
			}
			present.add(mob.getId());
			if (distance < best) {
				best = distance;
				nearest = mob;
			}
		}
		if (nearest == null) {
			SEEN.retainAll(present);
			return;
		}
		long now = Util.getMillis();
		boolean fresh = false;
		for (int id : present) {
			if (!SEEN.contains(id)) {
				fresh = true;
			}
		}
		SEEN.retainAll(present);
		SEEN.addAll(present);
		if (fresh || best <= CLOSE && now - lastPing >= REPING_MILLIS) {
			ping(minecraft, player, nearest, best, now);
		}
	}

	private static boolean threat(LocalPlayer player, Mob mob) {
		if (!(mob instanceof Enemy) || !mob.isAlive() || mob.isInvisible()) {
			return false;
		}
		if (BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).equals(HEART_DEMON)) {
			return false;
		}
		if (mob instanceof OwnableEntity ownable) {
			EntityReference<LivingEntity> owner = ownable.getOwnerReference();
			if (owner != null && owner.getUUID().equals(player.getUUID())) {
				return false;
			}
		}
		return true;
	}

	private static void ping(Minecraft minecraft, LocalPlayer player, Mob mob, double distance, long now) {
		lastPing = now;
		shownUntil = now + SHOW_MILLIS;
		int blocks = (int) Math.round(distance);
		Component name = mob.getType().getDescription();
		// Entity tags never reach the client, so an elite is known by its synced attachment (as EliteFx does).
		text = mob.hasAttached(EliteData.TYPE)
				? Component.translatable("kultivator.goals.alert.elite", name, blocks)
				: Component.translatable("kultivator.goals.alert.mob", name, blocks);
		double dx = mob.getX() - player.getX();
		double dz = mob.getZ() - player.getZ();
		double yaw = Math.toRadians(player.getYRot());
		double ahead = dx * -Math.sin(yaw) + dz * Math.cos(yaw);
		double right = dx * -Math.cos(yaw) + dz * -Math.sin(yaw);
		bearing = Math.atan2(right, ahead);
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7F, 0.35F));
	}

	static void reset() {
		SEEN.clear();
		shownUntil = 0L;
		text = null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		long now = Util.getMillis();
		boolean demo = HudDemo.active();
		Component line = demo ? Component.translatable("kultivator.goals.alert.elite", Component.translatable("entity.minecraft.zombie"), 7) : text;
		double side = demo ? Math.PI / 2.0D : bearing;
		if (line == null || !demo && now >= shownUntil) {
			return;
		}
		float fade = demo ? 1.0F : Math.min(1.0F, (shownUntil - now) / 600.0F);
		int width = g.guiWidth();
		int height = g.guiHeight();
		int edge = Math.max(12, Math.min(40, width / 12));
		int strong = Palette.argb(Math.round(110 * fade), 0xC0201A);
		int clear = Palette.argb(0, 0xC0201A);
		double a = Math.abs(side);
		if (a < Math.PI / 4.0D) {
			g.fillGradient(0, 0, width, edge, strong, clear);
		} else if (a > Math.PI * 3.0D / 4.0D) {
			g.fillGradient(0, height - edge, width, height, clear, strong);
		} else if (side > 0.0D) {
			sideGradient(g, width - edge, width, height, false, strong, clear);
		} else {
			sideGradient(g, 0, edge, height, true, strong, clear);
		}
		// Above the action bar, which meditation uses.
		Brush.centered(g, minecraft.font, Brush.bold(line), width / 2.0F, height - 82.0F, Palette.argb(Math.round(255 * fade), 0xFF6A5A), 0.9F, true);
	}

	/** A horizontal gradient made of thin vertical strips (the GUI fills only grade vertically). */
	private static void sideGradient(GuiGraphicsExtractor g, int x0, int x1, int height, boolean fromLeft, int strong, int clear) {
		int span = Math.max(1, x1 - x0);
		int strips = Math.min(12, span);
		for (int i = 0; i < strips; i++) {
			int sx0 = x0 + span * i / strips;
			int sx1 = x0 + span * (i + 1) / strips;
			float t = (i + 0.5F) / strips;
			g.fill(sx0, 0, sx1, height, Palette.mix(strong, clear, fromLeft ? t : 1.0F - t));
		}
	}
}
