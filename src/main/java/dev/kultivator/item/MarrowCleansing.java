package dev.kultivator.item;

import dev.kultivator.cultivation.PillToxicity;
import dev.kultivator.cultivation.SpiritRoots;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Element;
import dev.kultivator.data.RootGrade;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Marrow Cleansing Pill (DESIGN section 6): 8 s of agony (Nausea + Hunger II, black impurities seeping out), then
 * the root grade rises one step (max Heavenly) and a root of three or more elements has a 25% chance to lose one
 * non-mutant element. The end time is saved in {@code extra().roots.cleanse_until}, so logging out mid-cleansing still
 * gives the result when the player is back.
 */
public final class MarrowCleansing {

	/** Length of the cleansing. */
	public static final int DURATION = 160;
	/** Pill toxin added by one pill. */
	public static final int TOXIN = 15;
	/** Chance to wash out one element when the root has at least {@link #MIN_ELEMENTS_TO_REMOVE}. */
	public static final float ELEMENT_CHANCE = 0.25F;
	public static final int MIN_ELEMENTS_TO_REMOVE = 3;
	/** In {@code SpiritRoots.EXTRA}: game time when the running cleansing ends. */
	public static final String CLEANSE_UNTIL = "cleanse_until";

	private static final int PAIN = 0xC9A88A;
	private static final int REFUSE = 0xFF9A7A;
	private static final int PURE = 0xE8FFF4;

	/** Players whose client got the impurity effect this session (re-sent after a relog). */
	private static final Set<UUID> FX_SENT = new HashSet<>();

	/** Swallowing the pill (server side). True when it was taken (and one was consumed outside creative). */
	public static boolean use(ServerPlayer player, ItemStack stack) {
		CultivationData data = Cultivators.get(player);
		if (!data.rootTested()) {
			Msg.chat(player, Component.translatable("kultivator.roots.marrow.untested").withColor(REFUSE));
			return false;
		}
		if (SpiritRoots.ceremonyPending(player)) {
			Msg.chat(player, Component.translatable("kultivator.roots.marrow.wait_ceremony").withColor(REFUSE));
			return false;
		}
		if (cleansing(data)) {
			Msg.chat(player, Component.translatable("kultivator.roots.marrow.busy").withColor(REFUSE));
			return false;
		}
		if (data.rootGrade() == RootGrade.HEAVENLY && Element.fromMask(data.rootElements()).size() < MIN_ELEMENTS_TO_REMOVE) {
			Msg.chat(player, Component.translatable("kultivator.roots.marrow.pure").withColor(PURE));
			return false;
		}
		ServerLevel level = player.level();
		// The cooldown first: after consuming the last pill the stack is empty and has no item to put it on.
		player.getCooldowns().addCooldown(stack, DURATION);
		stack.consume(1, player);
		SpiritRoots.extra(data).putLong(CLEANSE_UNTIL, level.getGameTime() + DURATION);
		Cultivators.dirty(player);

		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, DURATION, 0));
		player.addEffect(new MobEffectInstance(MobEffects.HUNGER, DURATION, 1));
		Fx.send(level, player.position(), FxType.MARROW_CLEANSE, 0, DURATION, 0.0F);
		FX_SENT.add(player.getUUID());
		sound(player, SoundEvents.PLAYER_BURP, 0.6F, 0.8F);
		sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.0F);
		sound(player, SoundEvents.BREWING_STAND_BREW, 0.8F, 0.6F);
		Msg.chat(player, Component.translatable("kultivator.roots.marrow.start").withColor(PAIN));
		PillToxicity.add(player, TOXIN);
		return true;
	}

	/** True while a cleansing is running (or its result is still to be applied). */
	public static boolean cleansing(CultivationData data) {
		return data.extra().getCompoundOrEmpty(SpiritRoots.EXTRA).getLongOr(CLEANSE_UNTIL, 0L) > 0L;
	}

	/** Called from {@code SpiritRoots.tick} every tick; does its work every 5 ticks. */
	public static void tick(ServerPlayer player, CultivationData data) {
		if (player.tickCount % 5 != 0) {
			return;
		}
		CompoundTag own = data.extra().getCompoundOrEmpty(SpiritRoots.EXTRA);
		long until = own.getLongOr(CLEANSE_UNTIL, 0L);
		if (until <= 0L) {
			return;
		}
		long now = player.level().getGameTime();
		long left = until - now;
		if (left > DURATION) {
			// The clock is behind the saved time (another world's save): restart the countdown from the full length.
			left = DURATION;
			own.putLong(CLEANSE_UNTIL, now + DURATION);
			Cultivators.dirty(player);
		}
		if (left > 0L) {
			if (FX_SENT.add(player.getUUID())) {
				Fx.send(player.level(), player.position(), FxType.MARROW_CLEANSE, 0, (int) left, 0.0F);
			}
			agony(player);
			return;
		}
		finish(player, data, own);
	}

	/** Heartbeat, groans and bubbling filth while the cleansing runs. */
	private static void agony(ServerPlayer player) {
		if (player.tickCount % 20 == 0) {
			sound(player, SoundEvents.WARDEN_HEARTBEAT, 0.9F, 1.1F);
		}
		if (player.tickCount % 40 == 10) {
			sound(player, SoundEvents.PLAYER_HURT, 0.5F, 0.75F);
		}
		if (player.tickCount % 15 == 5) {
			sound(player, SoundEvents.LAVA_POP, 0.5F, 0.6F);
		}
	}

	private static void finish(ServerPlayer player, CultivationData data, CompoundTag own) {
		own.remove(CLEANSE_UNTIL);
		FX_SENT.remove(player.getUUID());
		Cultivators.dirty(player);
		if (!data.rootTested()) {
			return;
		}
		RootGrade before = data.rootGrade();
		RootGrade after = before.up();
		int mask = data.rootElements();
		List<Element> elements = Element.fromMask(mask);
		Element removed = null;
		RandomSource random = player.getRandom();
		if (elements.size() >= MIN_ELEMENTS_TO_REMOVE && random.nextFloat() < ELEMENT_CHANCE) {
			List<Element> impure = new ArrayList<>();
			for (Element element : elements) {
				if (!element.mutant()) {
					impure.add(element);
				}
			}
			// A root of three or more holds at most one mutant element, so a classic one can go and two always remain.
			if (!impure.isEmpty() && elements.size() > 1) {
				removed = impure.get(random.nextInt(impure.size()));
				mask &= ~removed.bit();
			}
		}
		data.setRootGrade(after);
		data.setRootElements(mask);
		Cultivators.syncNow(player);

		ServerLevel level = player.level();
		Fx.send(level, player.position(), FxType.MARROW_CLEANSE, after.ordinal(), 0, 0.0F);
		sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.2F);
		sound(player, SoundEvents.PLAYER_LEVELUP, 0.8F, 1.4F);
		sound(player, SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.5F);
		String key = after == before ? "kultivator.roots.marrow.done_max" : "kultivator.roots.marrow.done";
		Msg.chat(player, Component.translatable(key, SpiritRoots.describe(mask, after)).withColor(PURE));
		if (removed != null) {
			Msg.chat(player, Component.translatable("kultivator.roots.marrow.removed", removed.displayName().copy().withColor(removed.color()))
					.withColor(PURE));
		}
		Advancements.grant(player, "marrow_cleansed");
	}

	private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** Forgets the session marker (the effect is sent again after a relog). */
	public static void forget(ServerPlayer player) {
		FX_SENT.remove(player.getUUID());
	}

	public static void clear() {
		FX_SENT.clear();
	}

	private MarrowCleansing() {
	}
}
