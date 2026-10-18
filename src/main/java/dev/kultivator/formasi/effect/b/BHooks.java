package dev.kultivator.formasi.effect.b;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.core.Cores;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The server glue of module B (DESIGN 24.13, 24.14.2): tick hooks (trip channels, test-mob expiry, the grand-array
 * clock), the {@code b} part of the {@code formasi} hall info, the {@code travel} and {@code node} actions and the node
 * map on transport eyes. Registered by {@code FormasiB.init}.
 */
public final class BHooks {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TransportChannel.init();
		BCommands.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			GrandArrayEffect.tickClock(server.getTickCount());
			TransportChannel.tick(server);
			BCommands.tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			AlarmEffect.clearAll();
			MazeEffect.clearAll();
			RedSandEffect.clearAll();
			KillingEffect.clearAll();
			GrandArrayEffect.clearAll();
			TransportNetwork.clearAll();
			TransportChannel.clearAll();
			TerritorySweep.clear();
			BCommands.clearAll();
		});
	}

	/** {@code formasi.b} of the hall info: the transport nodes and the grand arrays of {@code owner}. */
	public static void writeInfo(ServerPlayer viewer, UUID owner, CompoundTag formasi) {
		CompoundTag b = new CompoundTag();
		b.put("nodes", TransportNetwork.info(viewer, owner));
		CompoundTag grand = new CompoundTag();
		GrandArrayEffect.Grand a1 = GrandArrayEffect.grand(owner);
		grand.putInt("a1", a1 == null ? -1 : a1.bits());
		GrandArrayEffect.Absolute a2 = GrandArrayEffect.absolute(owner);
		grand.putInt("n", a2.n());
		grand.putInt("built", a2.built());
		grand.putInt("arts", a2.arts());
		grand.putBoolean("a2", a2.active());
		b.put("grand", grand);
		b.putBoolean("transport", dev.kultivator.KultivatorConfig.get().formationTransport);
		formasi.put("b", b);
	}

	/** {@code travel:<fromId>:<toId>} (owner or sect mate). */
	public static void onTravel(ServerPlayer player, String text) {
		String[] parts = text.split(":");
		if (parts.length < 3) {
			return;
		}
		int from;
		int to;
		try {
			from = Integer.parseInt(parts[1]);
			to = Integer.parseInt(parts[2]);
		} catch (NumberFormatException exception) {
			return;
		}
		Component why = TransportNetwork.travel(player, from, to);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
		}
	}

	/** {@code node:<id>:<name>} (owner only); answers with a fresh hall info. */
	public static void onNode(ServerPlayer player, String text) {
		int first = text.indexOf(':');
		int second = first < 0 ? -1 : text.indexOf(':', first + 1);
		if (second < 0) {
			return;
		}
		int id;
		try {
			id = Integer.parseInt(text.substring(first + 1, second));
		} catch (NumberFormatException exception) {
			return;
		}
		CoreView core = Cores.byId(player.level().dimension(), id);
		if (core == null || BUtil.spec(core) == null || BUtil.spec(core).family() != dev.kultivator.formasi.spec.Family.PERPINDAHAN) {
			return;
		}
		Component why = TransportNetwork.rename(player, core, text.substring(second + 1));
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
		}
		Halls.sendInfo(player, core.owner());
	}

	/** Empty-hand sneak-use of a transport eye: the Formasi tablet opens on the node map of that node. */
	public static InteractionResult openNodeMap(ServerPlayer player, CoreView core) {
		if (!BUtil.member(player.level().getServer(), core.owner(), player)) {
			return InteractionResult.PASS;
		}
		Vec3 eye = Vec3.atCenterOf(core.eye());
		Fx.sendTo(player, eye, eye, FxType.FORMASI, FxB.NODE_MAP, core.id(), 0.0F);
		Halls.openScreen(player, core.owner(), Halls.PAGE_FORMASI);
		return InteractionResult.SUCCESS;
	}

	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> formasi) {
		BCommands.addCommands(formasi);
	}

	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		BCommands.addTestCommands(uji);
	}

	private BHooks() {
	}
}
