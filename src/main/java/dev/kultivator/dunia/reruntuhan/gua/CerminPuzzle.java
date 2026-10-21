package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * P5 Cermin Hati (DESIGN 34.5.5): when a challenger walks in, their shadow steps out of the mirror on the wall:
 * {@link BayanganEntity} with the player's own skin and 60 % of their numbers. It must be beaten; healing is reflected
 * by the mirror (the shadow heals twice as much), so the fight is won by skill, not by potions. A lethal blow of the
 * shadow never kills (the host's guard leaves 1 HP and the puzzle resets).
 */
final class CerminPuzzle extends Puzzle {

	public static final double SHARE = 0.60D;
	private @Nullable BayanganEntity shadow;
	private @Nullable TekaPartEntity mirror;
	private Vec3 mirrorAt = Vec3.ZERO;

	CerminPuzzle(PuzzleHost host) {
		super(host);
	}

	@Override
	public Kind kind() {
		return Kind.CERMIN;
	}

	@Override
	public void build() {
		Frame frame = host.frame();
		int[] off = Frame.triple(host.layout(), "mirror", 0);
		if (off == null) {
			off = new int[]{-6, 0, 0};
		}
		mirrorAt = frame.bottom(off[0], off[1], off[2]);
		mirror = part(REntities.TEKA_PLAT, TekaPartEntity.MIRROR, mirrorAt, frame.yaw(-90.0F));
	}

	@Override
	public void started() {
		summon();
	}

	private void summon() {
		if (done || (shadow != null && shadow.isAlive())) {
			return;
		}
		ServerPlayer challenger = nearest();
		if (challenger == null) {
			return;
		}
		Vec3 at = mirrorAt.add(host.frame().centre().subtract(mirrorAt).normalize().scale(1.5D));
		BayanganEntity made = host.spawn(REntities.BAYANGAN, at, challenger.getYRot() + 180.0F);
		if (made == null) {
			return;
		}
		made.mirror(challenger.getUUID(), challenger.getPlainTextName(), false);
		made.setTier(host.tier());
		ShadowBrain.mirrorStats(made, challenger, SHARE);
		made.setIntakeCap(0.0D);
		made.setBrain(new ShadowBrain(challenger.getUUID(), host.frame().centre(), 9.0D, SHARE, creature -> solve()));
		shadow = made;
		if (mirror != null) {
			mirror.setState(TekaPartEntity.LIT);
		}
		RFx.at(host.level(), at.add(0.0D, 1.0D, 0.0D), RFx.SOUL_APPEAR, 0x6A4AA0, 0, 1.2F);
		host.level().playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 0.6F);
		Voice.say(made, "demon.whisper", null);
		Msg.title(challenger, Component.translatable("kultivator.reruntuhan.teka.cermin.title").withStyle(ChatFormatting.DARK_PURPLE),
				Component.translatable("kultivator.reruntuhan.teka.cermin.subtitle").withStyle(ChatFormatting.GRAY), 5, 40, 10);
	}

	private @Nullable ServerPlayer nearest() {
		ServerPlayer best = null;
		double bestSq = Double.MAX_VALUE;
		for (ServerPlayer player : host.challengers()) {
			double d = player.position().distanceToSqr(mirrorAt);
			if (d < bestSq) {
				bestSq = d;
				best = player;
			}
		}
		return best;
	}

	@Override
	public void tick(long now) {
		if (done) {
			return;
		}
		if (shadow != null && shadow.isRemoved() && !done) {
			shadow = null;
		}
		if (shadow == null && !host.challengers().isEmpty() && now % 40L == 0L) {
			summon();
		}
	}

	@Override
	public void reset() {
		if (shadow != null && !shadow.isRemoved()) {
			shadow.setBrain(null);
			shadow.discard();
		}
		shadow = null;
		if (mirror != null) {
			mirror.setState(TekaPartEntity.IDLE);
		}
	}

	@Override
	public void dispose() {
		reset();
	}

	@Override
	public float progress() {
		BayanganEntity s = shadow;
		if (done) {
			return 1.0F;
		}
		return s == null ? 0.0F : 1.0F - s.getHealth() / Math.max(1.0F, s.getMaxHealth());
	}
}
