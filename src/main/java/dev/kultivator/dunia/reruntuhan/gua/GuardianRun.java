package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.reruntuhan.AnchorRun;
import dev.kultivator.dunia.reruntuhan.ColliderEntity;
import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.dunia.reruntuhan.Glyphs;
import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.Reruntuhan;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The guardian chamber of a Gua Leluhur (anchor {@code reruntuhan/penjaga}, DESIGN 34.5.5): a challenger stepping into
 * the arena wakes the Boneka Batu Penjaga (tier by the ruin), the door to the Aula Warisan stays sealed until it falls.
 * The players who fought it are remembered in the anchor's state ({@code victors}): only they may receive the
 * inheritance. Nobody in the arena for ten seconds: the guardian sinks back (full health next time). A treasure born
 * near the ruin ({@code DuniaEvents.TREASURE_BORN}) wakes it again ({@code awake}).
 */
public final class GuardianRun extends AnchorRun {

	private final int tier;
	private final double arena;
	private @Nullable ColliderEntity door;
	private @Nullable BonekaBatuEntity golem;
	private final ServerBossEvent bar;
	private final Set<UUID> fighters = new HashSet<>();
	private int empty;

	public GuardianRun(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
		this.tier = Mth.clamp(anchor.data().getIntOr("tier", 1), 1, 3);
		this.arena = Math.max(6, anchor.data().getIntOr("arena", 10));
		this.bar = new ServerBossEvent(Mth.createInsecureUUID(level.getRandom()),
				Component.translatable("entity.kultivator.boneka_batu.tier." + (tier - 1)), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
	}

	/** True when the guardian lies defeated (and no treasure woke it). */
	public boolean cleared() {
		return state().getLongOr("cleared_at", 0L) > 0L && !state().getBooleanOr("awake", false);
	}

	/** True when {@code player} fought and won here. */
	public boolean victor(UUID player) {
		return isVictor(anchor, player);
	}

	/** True when {@code player} is among the victors of guardian anchor {@code anchor}. */
	public static boolean isVictor(DuniaAnchorEntity anchor, UUID player) {
		for (Tag tag : anchor.state().getListOrEmpty("victors")) {
			if (tag instanceof StringTag s && player.toString().equals(s.value())) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void spawn() {
		int[] off = Frame.triple(data(), "door", 0);
		if (off == null) {
			return;
		}
		Vec3 at = frame.bottom(off[0], off[1], off[2]);
		Vec3 facing = frame.dir(off[0], off[2]);
		boolean alongZ = Math.abs(facing.x) > Math.abs(facing.z);
		float width = data().getIntOr("dw", 3);
		float height = data().getIntOr("dh", 5);
		ColliderEntity made = make(REntities.GERBANG_SEGEL, at, 0.0F);
		if (made == null) {
			return;
		}
		made.setup(alongZ ? 0.35F : width / 2.0F, height, alongZ ? width / 2.0F : 0.35F, RuinLooks.door(tier), ColliderEntity.FLAG_GATE, Glyphs.DOOR_SEAL);
		made.setOwned(true);
		if (cleared()) {
			made.open();
		}
		if (add(made)) {
			door = made;
		}
	}

	@Override
	public double radius() {
		return arena + 30.0D;
	}

	@Override
	protected void tick(long now) {
		List<ServerPlayer> inArena = playersWithin(arena);
		BonekaBatuEntity current = golem;
		if (current != null && current.isRemoved()) {
			golem = null;
			current = null;
		}
		if (current == null) {
			bar.removeAllPlayers();
			if (!cleared() && Reruntuhan.config().guardians && Reruntuhan.config().ruins && !inArena.isEmpty() && now % 10L == 0L) {
				summon(inArena);
			}
			return;
		}
		for (ServerPlayer player : inArena) {
			fighters.add(player.getUUID());
			if (!bar.getPlayers().contains(player)) {
				bar.addPlayer(player);
			}
		}
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (player.isRemoved() || player.distanceToSqr(centre()) > (arena + 8.0D) * (arena + 8.0D)) {
				bar.removePlayer(player);
			}
		}
		bar.setProgress(Mth.clamp(current.getHealth() / Math.max(1.0F, current.getMaxHealth()), 0.0F, 1.0F));
		if (inArena.isEmpty()) {
			if (++empty > 200) {
				current.setBrain(null);
				current.discard();
				golem = null;
				fighters.clear();
				empty = 0;
			}
		} else {
			empty = 0;
		}
	}

	private void summon(List<ServerPlayer> inArena) {
		BonekaBatuEntity made = make(REntities.BONEKA_BATU, centre(), frame.yaw(180.0F));
		if (made == null) {
			return;
		}
		made.setKind(tier - 1);
		made.setTier(tier);
		int party = Math.max(1, inArena.size());
		double health = RuinLooks.guardianHealth(tier) * (1.0D + 0.5D * (party - 1));
		made.setBase(Attributes.MAX_HEALTH, health);
		made.setBase(Attributes.ATTACK_DAMAGE, RuinLooks.guardianAttack(tier));
		made.setBase(Attributes.ARMOR, 6.0D + 6.0D * tier);
		made.setHealth(made.getMaxHealth());
		made.setIntakeCap(0.06D);
		made.setFlag(RCreature.FLAG_RISING, true);
		made.setSkill(BonekaBatuEntity.RISE);
		made.setBrain(new GuardianBrain(centre(), arena, tier, (creature, source) -> defeated(), (creature, victim) -> {
		}));
		if (!add(made)) {
			return;
		}
		golem = made;
		fighters.clear();
		for (ServerPlayer player : inArena) {
			fighters.add(player.getUUID());
		}
		RFx.at(level, centre(), RFx.GUARDIAN_RISE, tier, 0, 3.0F);
		level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 1.4F, 0.4F);
		Voice.boom(made, "boss.awaken", 32.0D);
		for (ServerPlayer player : inArena) {
			Msg.title(player, Component.translatable("entity.kultivator.boneka_batu.tier." + (tier - 1)).withStyle(ChatFormatting.DARK_RED),
					Component.translatable("kultivator.reruntuhan.penjaga.wakes").withStyle(ChatFormatting.GRAY), 5, 40, 10);
		}
	}

	private void defeated() {
		state().putLong("cleared_at", Math.max(1L, now()));
		state().putBoolean("awake", false);
		ListTag victors = state().getListOrEmpty("victors").copy();
		for (UUID id : fighters) {
			if (!victor(id)) {
				victors.add(StringTag.valueOf(id.toString()));
			}
		}
		while (victors.size() > 64) {
			victors.remove(0);
		}
		state().put("victors", victors);
		golem = null;
		bar.removeAllPlayers();
		if (door != null) {
			door.open();
			RFx.at(level, door.position().add(0.0D, 2.0D, 0.0D), RFx.DOOR_OPEN, tier, 1, 1.2F);
		}
		RFx.at(level, centre(), RFx.GUARDIAN_DOWN, 0xC8A060, tier, 2.5F);
		level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.IRON_GOLEM_DEATH, SoundSource.HOSTILE, 1.4F, 0.5F);
		for (ServerPlayer player : playersWithin(arena + 10.0D)) {
			Msg.title(player, Component.translatable("kultivator.reruntuhan.penjaga.fallen").withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.reruntuhan.penjaga.fallen.sub").withStyle(ChatFormatting.GRAY), 10, 50, 20);
		}
	}

	/** A treasure was born near: the guardian wakes. */
	public static void wake(DuniaAnchorEntity anchor) {
		if (anchor.state().getLongOr("cleared_at", 0L) > 0L) {
			anchor.state().putBoolean("awake", true);
		}
	}

	/** Seals the chamber again {@code ruinResetDays} after the victory, once nobody is near (victors stay known). */
	public static void maybeReseal(DuniaAnchorEntity anchor, ServerLevel level) {
		long at = anchor.state().getLongOr("cleared_at", 0L);
		if (at > 0L && level.getGameTime() - at > Reruntuhan.config().ruinResetDays * 24000L
				&& dev.kultivator.dunia.DuniaAnchors.playersNear(level, anchor.position(), 48.0D).isEmpty()) {
			anchor.state().remove("cleared_at");
		}
	}

	/** {@code uji gua penjaga}: wakes the guardian now (a test fight). */
	public void testWake(ServerPlayer player) {
		state().putBoolean("awake", true);
		if (golem == null) {
			summon(List.of(player));
		}
	}

	/** {@code uji gua selesai}: the guardian counts as defeated by {@code player}. */
	public void testDefeat(ServerPlayer player) {
		fighters.add(player.getUUID());
		if (golem != null) {
			golem.setBrain(null);
			golem.discard();
		}
		defeated();
	}

	public void testReset() {
		state().remove("cleared_at");
		state().remove("awake");
		despawn();
	}

	@Override
	protected void despawn() {
		bar.removeAllPlayers();
		golem = null;
		door = null;
		fighters.clear();
		super.despawn();
	}
}
