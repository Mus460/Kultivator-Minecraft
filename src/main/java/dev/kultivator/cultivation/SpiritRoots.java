package dev.kultivator.cultivation;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.RootGrade;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.item.MarrowCleansing;
import dev.kultivator.item.RootItems;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Spiritual roots (DESIGN section 6). The root is rolled once, at the start of the 8 s testing ceremony, and saved at
 * once (logging out mid-ceremony can never re-roll it). Around the spot where the ceremony began, testing stones (five,
 * or nine when the root holds a mutant element) are read one by one: the player's elements flare up with a rising
 * chime, the others flicker out with a dull knock. Then the lit stones pour their light into the player and the root
 * is revealed with a title, chat lines and advancements. The Marrow Cleansing Pill ({@link MarrowCleansing}) improves
 * a tested root later.
 * <p>
 * While the result is not revealed yet, {@code extra().roots.ceremony_until} holds the game time of the reveal; a player
 * who left before it sees the ceremony again (same root) when they come back.
 */
public final class SpiritRoots {

	/** Sub-compound of {@code CultivationData.extra()} owned by this module (shared with {@link MarrowCleansing}). */
	public static final String EXTRA = "roots";
	/** In {@link #EXTRA}: game time of the reveal of a ceremony that has not been revealed yet. */
	public static final String CEREMONY_UNTIL = "ceremony_until";
	/** Length of the ceremony. */
	public static final int CEREMONY_TICKS = 160;
	/** Radius of the circle of testing stones (the client draws them there). */
	public static final double STONE_RADIUS = 3.0D;

	/** ROOT_CEREMONY {@code a}: the stones rise. Element ordinals are the readings, {@link #FX_REVEAL} the result. */
	public static final int FX_BEGIN = -2;
	public static final int FX_REVEAL = -1;

	private static final int FIRST_READING = 18;
	private static final int INTERVAL_NINE = 14;
	private static final int INTERVAL_FIVE = 20;
	/** Note-block notes of the rising chime (a pentatonic run), one per element found. */
	private static final int[] CHIME_NOTES = {6, 8, 10, 13, 15, 18, 20, 22, 24};
	private static final int MUTANT_MASK = Element.LIGHTNING.bit() | Element.ICE.bit() | Element.WIND.bit() | Element.SPACE.bit();

	private static final int JADE = 0x8FE3C8;
	private static final int MUTED = 0x9AA3AE;
	private static final int SOFT = 0xC9D7E6;
	private static final int GOLD = 0xFFD54A;

	private static final Map<UUID, Ceremony> CEREMONIES = new HashMap<>();

	public static void init() {
		RootItems.init();
		ServerPlayerEvents.LEAVE.register(player -> {
			// A ceremony that was not revealed is shown again (same root) when the player is back.
			CEREMONIES.remove(player.getUUID());
			MarrowCleansing.forget(player);
		});
		// A new body after death: the impurity effect is drawn again around it.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> MarrowCleansing.forget(newPlayer));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CEREMONIES.clear();
			MarrowCleansing.clear();
		});
	}

	public static void tick(ServerPlayer player, CultivationData data) {
		Ceremony ceremony = CEREMONIES.get(player.getUUID());
		if (ceremony != null) {
			tickCeremony(player, data, ceremony);
		} else if (player.tickCount >= 60 && player.tickCount % 20 == 0 && pendingReveal(data)) {
			if (data.rootTested()) {
				begin(player, data, true);
			} else {
				clearMarker(data);
				Cultivators.dirty(player);
			}
		} else if (player.tickCount == 100 && data.rootTested() && !pendingReveal(data)) {
			// Roots tested before the ceremony existed (phase 1 saves) still earn the first root advancement.
			Advancements.grant(player, "root_tested");
		}
		MarrowCleansing.tick(player, data);
	}

	// ================================================================ ceremony

	/** Begins the root test for anyone whose root is untested; the root is rolled and saved right away. */
	public static void startCeremony(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (CEREMONIES.containsKey(player.getUUID())) {
			Msg.chat(player, Component.translatable("kultivator.roots.ceremony.running").withColor(SOFT));
			return;
		}
		if (data.rootTested() && pendingReveal(data)) {
			// Rolled before, never revealed (the player left): show the same root again.
			begin(player, data, true);
			return;
		}
		if (data.rootTested()) {
			Msg.chat(player, Component.translatable("kultivator.core.root_already", describe(data.rootElements(), data.rootGrade())));
			return;
		}
		Roll roll = roll(player.getRandom());
		data.setRoot(roll.elements(), roll.grade());
		extra(data).putLong(CEREMONY_UNTIL, player.level().getGameTime() + CEREMONY_TICKS);
		Qi.clamp(player);
		Cultivators.dirty(player);
		begin(player, data, false);
	}

	/** True while the player's root has been rolled but the ceremony has not revealed it yet. */
	public static boolean ceremonyPending(ServerPlayer player) {
		return CEREMONIES.containsKey(player.getUUID()) || pendingReveal(Cultivators.get(player));
	}

	/** True when the mask holds a mutant element (the ceremony then reads all nine stones). */
	public static boolean hasMutant(int elements) {
		return (elements & MUTANT_MASK) != 0;
	}

	/** This module's sub-compound of {@code extra()}, created when missing. Call dirty() after changing it. */
	public static CompoundTag extra(CultivationData data) {
		CompoundTag extra = data.extra();
		Optional<CompoundTag> existing = extra.getCompound(EXTRA);
		if (existing.isPresent()) {
			return existing.get();
		}
		CompoundTag created = new CompoundTag();
		extra.put(EXTRA, created);
		return created;
	}

	private static boolean pendingReveal(CultivationData data) {
		return revealPending(data);
	}

	/**
	 * True while the root is saved but its testing ceremony has not revealed it yet. Pure data (the client may call it on
	 * its synced copy to keep the result hidden until the reveal).
	 */
	public static boolean revealPending(CultivationData data) {
		return data.extra().getCompoundOrEmpty(EXTRA).contains(CEREMONY_UNTIL);
	}

	private static void clearMarker(CultivationData data) {
		data.extra().getCompound(EXTRA).ifPresent(own -> own.remove(CEREMONY_UNTIL));
	}

	private static void begin(ServerPlayer player, CultivationData data, boolean resumed) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		int mask = data.rootElements();
		boolean nine = hasMutant(mask);
		Ceremony ceremony = new Ceremony(level.dimension(), player.position(), now, nine ? 9 : 5, nine ? INTERVAL_NINE : INTERVAL_FIVE);
		CEREMONIES.put(player.getUUID(), ceremony);
		extra(data).putLong(CEREMONY_UNTIL, now + CEREMONY_TICKS);
		Cultivators.dirty(player);

		Fx.send(level, ceremony.center, FxType.ROOT_CEREMONY, FX_BEGIN, mask, data.rootGrade().ordinal());
		sound(player, ceremony, SoundEvents.BEACON_ACTIVATE, 1.0F, 0.7F);
		sound(player, ceremony, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.6F);
		Msg.chat(player, Component.translatable(resumed ? "kultivator.roots.ceremony.resume" : "kultivator.roots.ceremony.start").withColor(JADE));
		Msg.actionBar(player, Component.translatable("kultivator.roots.ceremony.reading").withColor(JADE));
	}

	private static void tickCeremony(ServerPlayer player, CultivationData data, Ceremony ceremony) {
		if (!data.rootTested() || !pendingReveal(data)) {
			// Reset or changed by a debug command in the meantime.
			CEREMONIES.remove(player.getUUID());
			return;
		}
		long elapsed = player.level().getGameTime() - ceremony.start;
		while (ceremony.next < ceremony.count && elapsed >= FIRST_READING + (long) ceremony.next * ceremony.interval) {
			read(player, data, ceremony, Element.values()[ceremony.next]);
			ceremony.next++;
		}
		long lastReading = FIRST_READING + (long) (ceremony.count - 1) * ceremony.interval;
		if (!ceremony.converging && elapsed >= lastReading + 8) {
			ceremony.converging = true;
			sound(player, ceremony, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.9F, 1.2F);
			sound(player, ceremony, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F);
			Msg.actionBar(player, Component.translatable("kultivator.roots.ceremony.converge").withColor(GOLD));
		}
		if (elapsed >= CEREMONY_TICKS) {
			CEREMONIES.remove(player.getUUID());
			reveal(player, data, ceremony);
		}
	}

	/** One testing stone is read. */
	private static void read(ServerPlayer player, CultivationData data, Ceremony ceremony, Element element) {
		int mask = data.rootElements();
		boolean owned = (mask & element.bit()) != 0;
		Fx.send(level(player, ceremony), ceremony.center, FxType.ROOT_CEREMONY, element.ordinal(), mask, data.rootGrade().ordinal());
		Component name = element.displayName().copy().withColor(element.color());
		if (owned) {
			int note = CHIME_NOTES[Math.min(CHIME_NOTES.length - 1, ceremony.found)];
			ceremony.found++;
			float pitch = (float) Math.pow(2.0D, (note - 12) / 12.0D);
			sound(player, ceremony, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F, pitch);
			sound(player, ceremony, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.6F + 0.12F * ceremony.found);
			if (element.mutant()) {
				sound(player, ceremony, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, 0.7F, 1.3F);
			}
			Msg.actionBar(player, Component.translatable(element.mutant() ? "kultivator.roots.step.mutant" : "kultivator.roots.step.resonates", name)
					.withColor(element.color()));
		} else {
			sound(player, ceremony, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.6F, 0.55F);
			sound(player, ceremony, SoundEvents.DEEPSLATE_HIT, 0.8F, 0.6F);
			Msg.actionBar(player, Component.translatable("kultivator.roots.step.silent", name).withColor(MUTED));
		}
	}

	/** The result: burst, title, chat lines, advancements. */
	private static void reveal(ServerPlayer player, CultivationData data, Ceremony ceremony) {
		int mask = data.rootElements();
		RootGrade grade = data.rootGrade();
		List<Element> elements = Element.fromMask(mask);
		Element mutant = null;
		for (Element element : elements) {
			if (element.mutant()) {
				mutant = element;
			}
		}
		clearMarker(data);

		Fx.send(level(player, ceremony), ceremony.center, FxType.ROOT_CEREMONY, FX_REVEAL, mask, grade.ordinal());
		dev.kultivator.epic.EpicHooks.extra(level(player, ceremony), dev.kultivator.epic.EpicIds.EXTRA_ROOT_TEST, player, player, ceremony.center, grade.ordinal(), mask);
		switch (grade) {
			case TRASH -> {
				sound(player, ceremony, SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.6F);
				sound(player, ceremony, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.5F);
			}
			case LOW -> sound(player, ceremony, SoundEvents.PLAYER_LEVELUP, 0.8F, 0.6F);
			case MID -> sound(player, ceremony, SoundEvents.PLAYER_LEVELUP, 0.9F, 0.9F);
			case HIGH -> {
				sound(player, ceremony, SoundEvents.PLAYER_LEVELUP, 1.0F, 1.2F);
				sound(player, ceremony, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.2F);
			}
			case HEAVENLY -> {
				sound(player, ceremony, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
				sound(player, ceremony, SoundEvents.BELL_RESONATE, 1.0F, 1.0F);
				sound(player, ceremony, SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.8F);
			}
		}
		if (mutant != null) {
			sound(player, ceremony, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.0F, 0.8F);
		}

		Msg.title(player, Component.translatable("kultivator.roots.reveal.title", grade.displayName()).withColor(grade.color()),
				elementList(mask), 10, 70, 20);
		Msg.chat(player, Component.translatable("kultivator.core.root_result", describe(mask, grade)));
		Msg.chat(player, Component.translatable("kultivator.roots.verdict." + grade.id()).withColor(grade == RootGrade.TRASH ? MUTED : grade.color()));
		if (elements.size() == 1) {
			Msg.chat(player, Component.translatable("kultivator.roots.verdict.single").withColor(elements.get(0).color()));
			Advancements.grant(player, "single_root");
		}
		if (mutant != null) {
			Msg.chat(player, Component.translatable("kultivator.roots.verdict.mutant", mutant.displayName().copy().withColor(mutant.color()))
					.withColor(SOFT));
			Advancements.grant(player, "mutant_root");
		}
		if (data.realm().isMortal()) {
			Msg.chat(player, Component.translatable("kultivator.roots.verdict.next").withStyle(ChatFormatting.GRAY));
		}
		if (KultivatorConfig.get().announceBreakthroughs) {
			if (grade == RootGrade.HEAVENLY) {
				announce(player, Component.translatable("kultivator.roots.broadcast.heavenly", player.getDisplayName()).withColor(GOLD));
			} else if (mutant != null) {
				announce(player, Component.translatable("kultivator.roots.broadcast.mutant", player.getDisplayName(),
						mutant.displayName().copy().withColor(mutant.color())).withColor(SOFT));
			}
		}
		Advancements.grant(player, "root_tested");
		if (grade == RootGrade.HEAVENLY) {
			Advancements.grant(player, "heavenly_root");
		}
		Cultivators.syncNow(player);
	}

	/** Tells everyone else on the server (the player already got the full story). */
	private static void announce(ServerPlayer player, Component message) {
		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			if (other != player) {
				other.sendSystemMessage(Msg.prefixed(message));
			}
		}
	}

	/** The level the ceremony's stones stand in (the player's own if it is not loaded). */
	private static ServerLevel level(ServerPlayer player, Ceremony ceremony) {
		ServerLevel level = player.level().getServer().getLevel(ceremony.dimension);
		return level == null ? player.level() : level;
	}

	/** Plays at the stones while the player is near them, else at the player. */
	private static void sound(ServerPlayer player, Ceremony ceremony, SoundEvent sound, float volume, float pitch) {
		ServerLevel level = player.level();
		Vec3 at = level.dimension() == ceremony.dimension && player.position().distanceToSqr(ceremony.center) < 256.0D
				? ceremony.center : player.position();
		level.playSound(null, at.x, at.y + 1.0D, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** A running ceremony (server side). */
	private static final class Ceremony {
		private final ResourceKey<Level> dimension;
		private final Vec3 center;
		private final long start;
		/** 5 classic stones, or all 9 when the root holds a mutant element. */
		private final int count;
		private final int interval;
		private int next;
		private int found;
		private boolean converging;

		private Ceremony(ResourceKey<Level> dimension, Vec3 center, long start, int count, int interval) {
			this.dimension = dimension;
			this.center = center;
			this.start = start;
			this.count = count;
			this.interval = interval;
		}
	}

	// ================================================================ roll and text

	/** A rolled root: element bitmask and grade. */
	public record Roll(int elements, RootGrade grade) {
	}

	/** The root roll from DESIGN section 6. Never called twice for the same player (the result is saved). */
	public static Roll roll(RandomSource random) {
		int r = random.nextInt(100);
		int count = r < 10 ? 5 : r < 35 ? 4 : r < 65 ? 3 : r < 90 ? 2 : 1;
		List<Element> classic = new ArrayList<>(List.of(Element.METAL, Element.WOOD, Element.WATER, Element.FIRE, Element.EARTH));
		List<Element> mutant = new ArrayList<>(List.of(Element.LIGHTNING, Element.ICE, Element.WIND, Element.SPACE));
		int mask = 0;
		boolean mutated = random.nextInt(100) < 6;
		for (int i = 0; i < count; i++) {
			Element element;
			if (mutated && i == 0) {
				element = mutant.remove(random.nextInt(mutant.size()));
			} else {
				element = classic.remove(random.nextInt(classic.size()));
			}
			mask |= element.bit();
		}
		int g = random.nextInt(100);
		int grade = g < 15 ? 0 : g < 45 ? 1 : g < 75 ? 2 : g < 93 ? 3 : 4;
		// Fewer elements are purer: shift up by (3 - count) * 0.5 steps, the half step decided by chance.
		double shift = (3 - count) * 0.5D;
		int whole = (int) Math.floor(shift);
		double fraction = shift - whole;
		grade += whole + (random.nextDouble() < fraction ? 1 : 0);
		return new Roll(mask, RootGrade.byOrdinal(Math.max(0, Math.min(4, grade))));
	}

	/** "Fire + Wood root, High grade". */
	public static MutableComponent describe(int elements, RootGrade grade) {
		return Component.translatable("kultivator.core.root_description", elementList(elements), grade.displayName().copy().withColor(grade.color()));
	}

	/** "Fire + Wood", each element in its colour. */
	public static MutableComponent elementList(int elements) {
		MutableComponent names = Component.empty();
		List<Element> list = Element.fromMask(elements);
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				names.append(Component.literal(" + "));
			}
			names.append(list.get(i).displayName().copy().withColor(list.get(i).color()));
		}
		return names;
	}

	// ================================================================ commands

	/** {@code root test}, {@code root info} and the op-only {@code debug root reroll} / {@code debug root replay}. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("root")
				.then(Commands.literal("test").executes(ctx -> {
					startCeremony(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("info").executes(ctx -> info(ctx.getSource(), ctx.getSource().getPlayerOrException()))));
		debug.then(Commands.literal("root")
				.then(Commands.literal("reroll").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.roots.debug.reroll")), false);
					reroll(player);
					return 1;
				}))
				.then(Commands.literal("replay").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.roots.debug.replay")), false);
					replay(player);
					return 1;
				})));
	}

	/** Debug: plays the ceremony again for the current root (e.g. one set with {@code debug set root}), no new roll. */
	public static void replay(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		CEREMONIES.remove(player.getUUID());
		if (!data.rootTested()) {
			clearMarker(data);
			startCeremony(player);
			return;
		}
		begin(player, data, false);
	}

	/** Debug: forgets the root (and any unrevealed ceremony) and starts a new ceremony. */
	public static void reroll(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		CEREMONIES.remove(player.getUUID());
		clearMarker(data);
		data.clearRoot();
		Qi.clamp(player);
		Cultivators.dirty(player);
		startCeremony(player);
	}

	private static int info(CommandSourceStack source, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (!data.rootTested()) {
			source.sendSuccess(() -> Msg.prefixed(line("kultivator.core.info.root",
					Component.translatable("kultivator.core.info.root_unknown").withStyle(ChatFormatting.GRAY))), false);
			source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.roots.info.hint").withColor(JADE)), false);
			return 1;
		}
		if (ceremonyPending(player)) {
			source.sendSuccess(() -> Msg.prefixed(line("kultivator.core.info.root",
					Component.translatable("kultivator.roots.info.reading").withColor(JADE))), false);
			return 1;
		}
		int mask = data.rootElements();
		RootGrade grade = data.rootGrade();
		source.sendSuccess(() -> Msg.prefixed(line("kultivator.core.info.root", describe(mask, grade))), false);
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.roots.info.multiplier",
				Meditation.multiplier(grade.multiplier())).withColor(grade.color())), false);
		MutableComponent daos = Component.empty();
		boolean any = false;
		for (Dao dao : Dao.values()) {
			if (dao.hasAffinity(mask)) {
				if (any) {
					daos.append(Component.literal(", "));
				}
				daos.append(dao.displayName().copy().withColor(dao.color()));
				any = true;
			}
		}
		Component affinity = any ? daos : Component.translatable("kultivator.roots.info.affinity_none").withStyle(ChatFormatting.GRAY);
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.roots.info.affinity", affinity).withColor(SOFT)), false);
		return 1;
	}

	private static MutableComponent line(String key, Component value) {
		return Component.translatable(key).withStyle(ChatFormatting.GRAY).append(Component.literal(" ")).append(value);
	}

	private SpiritRoots() {
	}
}
