package dev.kultivator.technique.spec.run;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.network.Payloads;
import dev.kultivator.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of the Kitab Teknik (DESIGN 22.12.10): {@code Actions.TECHNIQUE} text ops {@code slot:<0-11>:<id|->},
 * {@code autofill}, {@code clear} and {@code test:<id>} (op + creative); with {@code arg} = a disciple's entity id, the
 * disciple tab's {@code dslot:<socket>:<id|->} (combat sockets), {@code teach:<id>} (spec teaching) and
 * {@code dlearn:<id>} (the Dewa art of a 9-star title).
 */
public final class Codex {

	private static final int REFUSE = 0xFF8A7A;

	private static final java.util.List<java.util.function.BiFunction<ServerPlayer, String, @Nullable String>> NAME_PROVIDERS =
			new java.util.concurrent.CopyOnWriteArrayList<>();

	/**
	 * Adds an own-name provider (phase 9, DESIGN 26.14 item 12): (player, spec id) → the name the player gave that spec (the
	 * self-created arts), or null. Register from a module's {@code init()}. The client has its own {@code CodexNames}.
	 */
	public static void addNameProvider(java.util.function.BiFunction<ServerPlayer, String, @Nullable String> provider) {
		if (provider != null) {
			NAME_PROVIDERS.add(provider);
		}
	}

	/** The player's own name of {@code spec} as plain text, else its translated name (for server chat and action-bar lines). */
	public static Component nameOf(@Nullable ServerPlayer player, TechniqueSpec spec) {
		if (player != null) {
			for (java.util.function.BiFunction<ServerPlayer, String, @Nullable String> provider : NAME_PROVIDERS) {
				try {
					String own = provider.apply(player, spec.id());
					if (own != null && !own.isBlank()) {
						return Component.literal(own);
					}
				} catch (RuntimeException exception) {
					dev.kultivator.Kultivator.LOGGER.error("A technique name provider failed", exception);
				}
			}
		}
		return spec.displayName();
	}

	public static void handleAction(ServerPlayer player, Payloads.Action action) {
		String text = action.text() == null ? "" : action.text();
		int colon = text.indexOf(':');
		String op = colon < 0 ? text : text.substring(0, colon);
		String rest = colon < 0 ? "" : text.substring(colon + 1);
		switch (op) {
			case "slot" -> slot(player, rest);
			case "autofill" -> {
				int filled = Loadout.autofill(player);
				Msg.actionBar(player, Component.translatable("kultivator.teknik.cmd.slot.autofill", filled).withColor(0x7CFFB2));
			}
			case "clear" -> Loadout.clear(player);
			case "test" -> test(player, rest);
			case "dslot" -> discipleSlot(player, action.arg(), rest);
			case "teach" -> teach(player, action.arg(), rest);
			case "dlearn" -> dewaChoice(player, action.arg(), rest);
			default -> {
			}
		}
	}

	private static void slot(ServerPlayer player, String rest) {
		int colon = rest.indexOf(':');
		if (colon <= 0) {
			return;
		}
		int index;
		try {
			index = Integer.parseInt(rest.substring(0, colon));
		} catch (NumberFormatException exception) {
			return;
		}
		String id = rest.substring(colon + 1);
		if (!Loadout.set(player, index, id)) {
			TechniqueSpec spec = TechniqueRegistry.byId(id);
			if (spec != null) {
				Msg.actionBar(player, Component.translatable("kultivator.teknik.cmd.slot.refused", spec.displayName()).withColor(REFUSE));
			}
		}
	}

	/** {@code test:<id>}: an operator in creative mode casts any spec as {@code uji teknik <id>} would. */
	private static void test(ServerPlayer player, String id) {
		TechniqueSpec spec = TechniqueRegistry.byId(id);
		if (spec == null) {
			return;
		}
		if (!player.isCreative() || !player.level().getServer().getPlayerList().isOp(player.nameAndId())) {
			Msg.actionBar(player, Component.translatable("kultivator.teknik.test.op_only").withColor(REFUSE));
			return;
		}
		CultivationData data = Cultivators.get(player);
		if (spec.playerVisible() && data.learn(spec.id())) {
			Cultivators.dirty(player);
		}
		TechniqueRunner.cast(player, spec, CastOptions.TEST);
	}

	/**
	 * The player's own living disciple {@code entityId} within {@code range}, else null; out of range the player is told
	 * so (these buttons used to fail without a word beyond 16 blocks while the screen stays open up to 64).
	 */
	private static @Nullable DiscipleEntity disciple(ServerPlayer player, int entityId, double range) {
		Entity entity = player.level().getEntity(entityId);
		if (!(entity instanceof DiscipleEntity disciple) || !disciple.isAlive() || !disciple.isOwnedBy(player)) {
			return null;
		}
		if (player.distanceToSqr(disciple) > range * range) {
			Msg.actionBar(player, Component.translatable("kultivator.disciples.too_far", disciple.getDisplayName(), (int) range).withColor(REFUSE));
			return null;
		}
		return disciple;
	}

	/** {@code dslot:<socket>:<id|->}: a disciple's combat socket (22.13.3). */
	private static void discipleSlot(ServerPlayer player, int entityId, String rest) {
		// Only a loadout choice: the same range as the remote disciple screen.
		DiscipleEntity entity = disciple(player, entityId, Disciples.VIEW_RANGE);
		int colon = rest.indexOf(':');
		if (entity == null || colon <= 0) {
			return;
		}
		int socket;
		try {
			socket = Integer.parseInt(rest.substring(0, colon));
		} catch (NumberFormatException exception) {
			return;
		}
		if (DiscipleCultivation.setCombatSlot(entity, socket, rest.substring(colon + 1))) {
			Disciples.sendInfo(player, entity);
		}
	}

	/** {@code teach:<id>}: teaches a spec to the disciple (the same rules as the Ajarkan button). */
	private static void teach(ServerPlayer player, int entityId, String id) {
		DiscipleEntity entity = disciple(player, entityId, Disciples.ACTION_RANGE);
		TechniqueSpec spec = TechniqueRegistry.byId(id);
		if (entity == null || spec == null) {
			return;
		}
		DiscipleCultivation.teach(entity, player, spec);
		if (entity.isAlive() && !entity.isRemoved()) {
			Disciples.sendInfo(player, entity);
		}
	}

	/** {@code dlearn:<id>}: the master picks the disciple's Dewa art of a 9-star title (22.13.2 self-comprehension). */
	private static void dewaChoice(ServerPlayer player, int entityId, String id) {
		// The chat offer allows any distance; the screen's button uses the remote screen's range.
		DiscipleEntity entity = disciple(player, entityId, Disciples.VIEW_RANGE);
		if (entity == null) {
			return;
		}
		DiscipleCultivation.chooseDewa(entity, player, id);
		if (entity.isAlive() && !entity.isRemoved()) {
			Disciples.sendInfo(player, entity);
		}
	}

	/**
	 * {@code /kultivasi teknik pahamkan <disciple uuid> <id>} (the chat offer's click): the same choice for one's own
	 * loaded disciple at any distance. Returns 1 when learned.
	 */
	public static int dewaChoiceCommand(ServerPlayer player, String disciple, String id) {
		for (DiscipleEntity entity : Disciples.loaded(player.getUUID())) {
			if (entity.getUUID().toString().equals(disciple)) {
				boolean learned = DiscipleCultivation.chooseDewa(entity, player, id);
				if (learned && player.distanceToSqr(entity) <= Disciples.ACTION_RANGE * Disciples.ACTION_RANGE) {
					Disciples.sendInfo(player, entity);
				}
				return learned ? 1 : 0;
			}
		}
		Msg.actionBar(player, Component.translatable("kultivator.teknik.dewa_offer.away").withColor(REFUSE));
		return 0;
	}

	private Codex() {
	}
}
