package dev.kultivator.tempa;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.formasi.stack.FormationField;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.network.Payloads;
import dev.kultivator.util.DamageHooks;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Penempaan and Pusaka Natal Diri: the Earthfire Forge, artifacts, the player's natal treasure, weapon spirits and storage
 * rings (DESIGN 25.5, 25.7, owned by P8-tempa). {@link #init()} registers the blocks, items, menus and hooks; the module
 * ticks forge menus every tick and each player's artifacts every 10 ticks (cheap reads, no block access but the forge's one
 * {@code getBlockState} a second). Block safety: nothing here changes a block.
 */
public final class Tempa {

	/** Registers the forge, items, menus and hooks; called from {@code Kultivator.onInitialize} before {@code ModItems.init()}. */
	public static void init() {
		TempaItems.init();
		TempaMenus.init();
		Object look = NatalTreasureSelf.LOOK;
		ArtifactEffects.init();
		Forging.init();
		WeaponSpirit.init();
		RingProtection.init();
		ServerTickEvents.END_SERVER_TICK.register(Tempa::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (DamageHooks.isTechnique(source) && source.getEntity() instanceof ServerPlayer player && entity != player) {
				WeaponSpirit.onKill(player, entity);
			}
		});
		ServerPlayerEvents.JOIN.register(NatalTreasureSelf::refreshLook);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> NatalTreasureSelf.refreshLook(newPlayer));
		ServerPlayerEvents.LEAVE.register(player -> {
			NatalTreasureSelf.forget(player);
			ArtifactEffects.forget(player);
			Heat.forget(player);
		});
		if (look == null) {
			throw new IllegalStateException("natal look attachment missing");
		}
	}

	private static void tick(MinecraftServer server) {
		Forging.tick(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.tickCount % 10 != 0 || !player.isAlive()) {
				continue;
			}
			ArtifactEffects.tick(player);
			if (player.tickCount % 20 == 0) {
				NatalTreasureSelf.tickRitual(player);
				WeaponSpirit.tick(player);
			}
		}
	}

	// ================================================================== sync

	/** Sends the Pusaka tablet's data (natal treasure, links, skill) and, with the forge open, its preview card. */
	public static void sync(ServerPlayer player) {
		Payloads.DuniaSync.send(player, Payloads.DuniaSync.CH_TEMPA, data(player));
	}

	static CompoundTag data(ServerPlayer player) {
		CompoundTag tag = new CompoundTag();
		CultivationData data = Cultivators.get(player);
		CompoundTag art = NatalTreasureSelf.record(player);
		if (art != null) {
			CompoundTag natal = new CompoundTag();
			natal.put("art", art.copy());
			natal.putBoolean("tempered", NatalTreasureSelf.tempered(player));
			tag.put("natal", natal);
		}
		long cd = data.extra().getCompoundOrEmpty(NatalTreasureSelf.DUNIA).getLongOr(NatalTreasureSelf.BIND_CD, 0L) - player.level().getGameTime();
		tag.putLong("bind_cd", Math.max(0L, cd));
		ListTag linked = new ListTag();
		List<UUID> ids = NatalTreasureSelf.linked(player);
		Inventory inventory = player.getInventory();
		for (UUID id : ids) {
			CompoundTag entry = new CompoundTag();
			entry.putString("id", id.toString());
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (ArtifactItem.is(stack) && id.equals(ArtifactItem.id(ArtifactItem.data(stack)))) {
					entry.put("art", ArtifactItem.data(stack));
					break;
				}
			}
			linked.add(entry);
		}
		tag.put("linked", linked);
		double xp = Forging.xp(player);
		int level = Forging.level(xp);
		tag.putInt("skill", level);
		tag.putDouble("xp", xp);
		tag.putDouble("xp_from", Forging.xpFor(level));
		tag.putDouble("xp_to", Forging.xpFor(Math.min(10, level + 1)));
		CompoundTag tempa = data.extra().getCompoundOrEmpty(NatalTreasureSelf.TEMPA);
		tag.putInt("made", tempa.getIntOr("made", 0));
		tag.putInt("refined", tempa.getIntOr("refined", 0));
		tag.putInt("realm", data.realm().ordinal());
		if (player.containerMenu instanceof ForgeMenu menu) {
			tag.put("forge", Forging.preview(player, menu));
		}
		return tag;
	}

	// ================================================================== actions

	/**
	 * Action {@code TEMPA}: text = "op[:param]"; "sync" answers with {@code DuniaSync} channel {@code CH_TEMPA}, "ring:open"
	 * opens the best bound ring (the unbound "Buka Cincin" key); "natal:bind", "natal:unbind", "link" and "unlink" come from
	 * the Pusaka tablet.
	 */
	public static void handleAction(ServerPlayer player, String text) {
		String op = text == null ? "" : text;
		switch (op) {
			case "ring:open" -> StorageRingItem.openBest(player, false);
			case "natal:bind" -> NatalTreasureSelf.beginBind(player);
			case "natal:unbind" -> NatalTreasureSelf.unbind(player, false);
			case "link" -> NatalTreasureSelf.link(player);
			case "unlink" -> NatalTreasureSelf.unlink(player);
			default -> {
			}
		}
		sync(player);
	}

	// ================================================================== commands

	/** {@code /kultivasi pusaka ...}, {@code cincin} and {@code debug tempa ...} (DESIGN 25.11). */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("pusaka")
				.then(Commands.literal("info").executes(ctx -> info(self(ctx))))
				.then(Commands.literal("ikat").executes(ctx -> {
					NatalTreasureSelf.beginBind(self(ctx));
					return 1;
				}))
				.then(Commands.literal("lepas").executes(ctx -> {
					NatalTreasureSelf.unbind(self(ctx), false);
					return 1;
				}))
				.then(Commands.literal("tautkan").executes(ctx -> {
					NatalTreasureSelf.link(self(ctx));
					return 1;
				}))
				.then(Commands.literal("lepastautan").executes(ctx -> {
					NatalTreasureSelf.unlink(self(ctx));
					return 1;
				})));
		root.then(Commands.literal("cincin").executes(ctx -> StorageRingItem.openBest(self(ctx), false) ? 1 : 0));
		debug.then(Commands.literal("tempa").then(Commands.literal("panas").executes(ctx -> debugHeat(self(ctx)))));
	}

	/** {@code /kultivasi uji pusaka|tempa|roh|cincin ...} (DESIGN 25.11). */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		TempaTests.add(uji);
	}

	static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static int info(ServerPlayer player) {
		CompoundTag art = NatalTreasureSelf.record(player);
		if (art == null) {
			Msg.chat(player, Component.translatable("kultivator.tempa.natal.none").withStyle(ChatFormatting.GRAY));
		} else {
			double ap = ArtifactItem.powerOf(art) * ArtifactItem.NATAL_FACTOR;
			Msg.chat(player, Component.translatable("kultivator.tempa.info.natal", ArtifactItem.displayName(art), ArtifactItem.fmt(ap))
					.withStyle(ChatFormatting.GOLD));
			Msg.chat(player, ArtifactItem.effectLine(ArtifactItem.form(art), ArtifactItem.grade(art), ap).withStyle(ChatFormatting.DARK_AQUA));
			Msg.chat(player, Component.translatable("kultivator.tempa.tip.spirit", ArtifactItem.spiritName(ArtifactItem.spiritStage(art)),
					(int) ArtifactItem.spiritYears(art)).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		Msg.chat(player, Component.translatable("kultivator.tempa.info.skill", Forging.title(Forging.skillLevel(player)),
				ArtifactItem.fmt(Forging.xp(player))).withStyle(ChatFormatting.YELLOW));
		Msg.chat(player, Component.translatable("kultivator.tempa.info.linked", NatalTreasureSelf.linked(player).size(), NatalTreasureSelf.MAX_LINKED)
				.withStyle(ChatFormatting.GRAY));
		return 1;
	}

	/** {@code debug tempa panas}: the heat read under the forge you look at and the formation field there. */
	private static int debugHeat(ServerPlayer player) {
		BlockHitResult hit = Targeting.blockHit(player, 6.0D);
		if (hit.getType() != HitResult.Type.BLOCK || !player.level().getBlockState(hit.getBlockPos()).is(TempaItems.EARTHFIRE_FORGE)) {
			Msg.chat(player, Component.translatable("kultivator.tempa.debug.look").withStyle(ChatFormatting.RED));
			return 0;
		}
		BlockPos pos = hit.getBlockPos();
		Heat.Reading reading = Heat.read(player.level(), pos, player, false);
		FormationField field = Stacking.fieldAtBlock(player.level(), pos, player.getUUID());
		Msg.chat(player, Component.translatable("kultivator.tempa.debug.heat", Heat.name(reading.heat()), ArtifactItem.gradeName(reading.heat()),
				player.level().getBlockState(pos.below()).getBlock().getName(), Forging.signed(field.refineChance()),
				ArtifactItem.fmt(field.refineTime())).withStyle(ChatFormatting.AQUA));
		return 1;
	}

	/** The module's items for the "Fase 8" test kit. */
	public static List<ItemStack> kitItems() {
		List<ItemStack> list = new ArrayList<>();
		list.add(new ItemStack(TempaItems.EARTHFIRE_FORGE_ITEM));
		for (int tier = 0; tier <= 4; tier++) {
			list.add(StorageRingItem.create(tier, null));
		}
		for (String form : ArtifactItem.FORMS) {
			list.add(ArtifactItem.create(form, 3, 1, 3));
		}
		return list;
	}

	private Tempa() {
	}
}
