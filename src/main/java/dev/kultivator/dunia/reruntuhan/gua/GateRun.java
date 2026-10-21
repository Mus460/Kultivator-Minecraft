package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.reruntuhan.AnchorRun;
import dev.kultivator.dunia.reruntuhan.ColliderEntity;
import dev.kultivator.dunia.reruntuhan.Glyphs;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.Reruntuhan;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The collapsed cliff gate of a Gua Leluhur (anchor {@code reruntuhan/gerbang}, DESIGN 34.5.5): the stone door panels
 * are a Gerbang Segel entity filling the doorway. A cultivator of the ruin's realm lays a hand on the seal (a click) and
 * channels for three seconds; then the panels slide apart (1.2 s, dust, the beat-41 card "Segel Terbuka" and no camera
 * indoors) and stay open (anchor state {@code open}). A lower realm feels the seal push back.
 */
public final class GateRun extends AnchorRun {

	public static final int CHANNEL_TICKS = 60;
	private final int tier;
	private @Nullable ColliderEntity door;
	private @Nullable ServerPlayer channeling;
	private long channelEnd;

	public GateRun(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
		this.tier = Mth.clamp(anchor.data().getIntOr("tier", 1), 1, 3);
	}

	public boolean open() {
		return state().getBooleanOr("open", false);
	}

	@Override
	protected void spawn() {
		Vec3 facing = frame.dir(0.0D, 1.0D);
		boolean alongZ = Math.abs(facing.x) > Math.abs(facing.z);
		float width = data().getIntOr("w", 3);
		float height = data().getIntOr("h", 4);
		ColliderEntity made = make(REntities.GERBANG_SEGEL, Vec3.atBottomCenterOf(anchor.blockPosition()),
				frame.yaw(0.0F));
		if (made == null) {
			return;
		}
		made.setup(alongZ ? 0.4F : width / 2.0F, height, alongZ ? width / 2.0F : 0.4F, RuinLooks.door(tier),
				ColliderEntity.FLAG_GATE | ColliderEntity.FLAG_CLICK, Glyphs.DOOR_SEAL);
		made.setOwned(true);
		made.setClickListener((player, part, attack) -> touch(player));
		if (open()) {
			made.open();
		}
		if (add(made)) {
			door = made;
		}
	}

	private void touch(ServerPlayer player) {
		if (open() || !Reruntuhan.config().ruins) {
			return;
		}
		Realm min = RuinLooks.minRealm(tier);
		if (!player.isCreative() && !Cultivators.get(player).realm().atLeast(min)) {
			Msg.chat(player, Component.translatable("kultivator.reruntuhan.gerbang.refuse", min.displayName()).withStyle(ChatFormatting.GRAY));
			Vec3 away = player.position().subtract(centre()).multiply(1.0D, 0.0D, 1.0D).normalize().scale(0.8D).add(0.0D, 0.3D, 0.0D);
			player.push(away.x, away.y, away.z);
			player.needsSync = true;
			RFx.at(level, centre().add(0.0D, 2.0D, 0.0D), RFx.BACKLASH, 0, 0, 0.6F);
			return;
		}
		if (channeling != null) {
			return;
		}
		channeling = player;
		channelEnd = now() + CHANNEL_TICKS;
		Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.gerbang.channel").withStyle(ChatFormatting.AQUA));
		level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.0F, 0.6F);
	}

	@Override
	protected void tick(long now) {
		ServerPlayer player = channeling;
		if (player == null) {
			return;
		}
		if (player.isRemoved() || !player.isAlive() || player.distanceToSqr(centre()) > 5.0D * 5.0D || player.hurtTime > 0) {
			channeling = null;
			Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.gerbang.broken").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (now % 10L == 0L) {
			RFx.line(level, player.position().add(0.0D, 1.2D, 0.0D), centre().add(0.0D, 2.0D, 0.0D), RFx.LINE, 0x9FE0C0, 10, 0.25F);
		}
		if (now >= channelEnd) {
			channeling = null;
			openNow(player);
		}
	}

	/** Opens the gate for good (also {@code uji gua selesai} at a gate). */
	public void openNow(@Nullable ServerPlayer opener) {
		state().putBoolean("open", true);
		if (door != null) {
			door.open();
		}
		RFx.at(level, centre().add(0.0D, 1.5D, 0.0D), RFx.DOOR_OPEN, tier, 1, 1.4F);
		level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.2F, 0.4F);
		level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.5F);
		if (opener != null) {
			Voice.heavenNear(level, centre(), "heaven.secret_realm", 24.0D);
			Msg.title(opener, Component.translatable("kultivator.reruntuhan.gerbang.opened").withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.reruntuhan.gua.tier." + tier).withStyle(ChatFormatting.GRAY), 10, 50, 20);
		}
	}

	/** {@code uji gua reset}: closes the gate again. */
	public void testReset() {
		state().remove("open");
		despawn();
	}

	@Override
	protected void despawn() {
		channeling = null;
		door = null;
		super.despawn();
	}
}
