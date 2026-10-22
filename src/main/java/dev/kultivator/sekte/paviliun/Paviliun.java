package dev.kultivator.sekte.paviliun;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Rank;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteConfig;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.PaviliunService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Module A, Paviliun Kitab &amp; Pemakaman / Altar Leluhur (DESIGN 36.2.5, butir 41 and 45; contracts C36.A1-A4): the
 * entry, wired with one line in {@code SekteContent}. It registers the two template components ({@link PaviliunKomponen}),
 * the Dupa item, its payloads, commands and config section, sets {@code SekteApi.setPaviliun} ({@code studying},
 * {@code graves}, {@code blessing}), and listens:
 * <ul>
 * <li>{@code DiscipleHooks.addTick20}: the pavilion study ({@link BelajarMandiri}), the revival check of the graves, and a
 * kneeling left over from before a restart;</li>
 * <li>{@code DiscipleHooks.addOnDeath}: a snapshot of the fallen, buried on the next tick ({@link Makam});</li>
 * <li>{@code UseBlockCallback}: the pavilion (deposit a book, open the Rak Kitab), the altar or stele (offer Dupa, lay a
 * Soul Tablet, open the Makam Leluhur), a tombstone (read its epitaph);</li>
 * <li>the server tick: pending burials every tick, the rites every second, the world picture every 2 s, the Qingming
 * check and the cemetery's upkeep every 10 s per online master.</li>
 * </ul>
 * It fires {@code DISCIPLE_STUDIED}, {@code DISCIPLE_BURIED}, {@code ANCESTOR_RITE} and {@code DISCIPLE_HONORED} (C36.A2).
 * No block is ever changed by this module at run time: its buildings come only from the sect build engine. Nothing here
 * references a client class.
 */
public final class Paviliun {

	private static PaviliunConfig config = new PaviliunConfig();
	private static boolean initialised;
	private static @Nullable MinecraftServer server;
	private static long ticks;

	public static void init() {
		if (initialised || !SekteContent.on(SekteIds.A)) {
			return;
		}
		initialised = true;
		config = SekteConfig.section(SekteIds.A, PaviliunConfig.class, PaviliunConfig::new, PaviliunConfig::clamp);
		Dupa.init();
		PaviliunKomponen.register();
		SekteApi.setPaviliun(new Service());
		PaviliunPayloads.init();
		PaviliunCommands.init();
		DiscipleHooks.addOnDeath(Makam::onDeath);
		DiscipleHooks.addTick20(Paviliun::tick20);
		UseBlockCallback.EVENT.register(Paviliun::useBlock);
		Halls.addStatusLineProvider(Paviliun::statusLine);
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerTickEvents.END_SERVER_TICK.register(Paviliun::serverTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> clear());
	}

	static PaviliunConfig config() {
		return config;
	}

	static @Nullable MinecraftServer server() {
		return server;
	}

	/** The module runs (its switch and the config's {@code aktif}). */
	static boolean active() {
		return initialised && config.aktif && SekteContent.on(SekteIds.A);
	}

	// ================================================================== the service (C36.A1, A3)

	private static final class Service implements PaviliunService {
		@Override
		public @Nullable String studying(UUID owner, UUID soul) {
			return owner == null || soul == null || !active() ? null : BelajarMandiri.studying(owner, soul);
		}

		@Override
		public List<PaviliunService.Makam> graves(UUID owner) {
			if (owner == null) {
				return List.of();
			}
			List<PaviliunService.Makam> list = new ArrayList<>();
			for (PaviliunData.Grave g : PaviliunData.graves(owner)) {
				list.add(new PaviliunService.Makam(g.soul(), g.name(), Rank.ofStep(g.step()).realm().name().toLowerCase(java.util.Locale.ROOT), g.day(),
						"kultivator.sekte.paviliun.sebab." + g.cause(), g.deeds().size(), g.enshrined()));
			}
			return List.copyOf(list);
		}

		@Override
		public double blessing(UUID owner) {
			return owner == null ? 0.0D : AltarLeluhur.blessing(owner);
		}
	}

	// ================================================================== disciples

	private static void tick20(DiscipleEntity entity) {
		if (!active() || entity.role() != DiscipleEntity.Role.DISCIPLE || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		UUID owner = entity.ownerId();
		if (owner == null || Halls.hallOf(owner) == null) {
			return;
		}
		MinecraftServer s = level.getServer();
		try {
			Makam.tick20(s, owner, entity);
			if (entity.activity() == SekteIds.ACT_MOURN && !Upacara.inRite(entity)) {
				// a kneeling left over from before a restart (the rite lives only in memory)
				entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
				return;
			}
			BelajarMandiri.tick20(s, owner, entity);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Sekte paviliun tick failed for {}", entity.soulId(), exception);
		}
	}

	// ================================================================== clicks

	private static InteractionResult useBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer master) || !(level instanceof ServerLevel) || hand != InteractionHand.MAIN_HAND || player.isSpectator()
				|| !active()) {
			return InteractionResult.PASS;
		}
		UUID owner = master.getUUID();
		Halls.HallView hall = Halls.hallOf(owner);
		BlockPos pos = hit.getBlockPos();
		if (hall == null || !level.dimension().equals(hall.dim()) || pos.distSqr(hall.pos()) > 192 * 192) {
			return InteractionResult.PASS;
		}
		ItemStack stack = master.getMainHandItem();
		try {
			// the pavilion
			BlockPos library = SectBuild.anchor(owner, "library");
			if (library != null && near(pos, library, 3, 1, 3)) {
				if (RakKitab.accepts(stack) && !master.isShiftKeyDown()) {
					String why = RakKitab.deposit(master, stack);
					if (why != null) {
						Msg.actionBar(master, Component.translatable(why, RakKitab.capacity(owner)).withStyle(ChatFormatting.YELLOW));
					}
					return InteractionResult.SUCCESS_SERVER;
				}
				if (stack.isEmpty() && !master.isShiftKeyDown()) {
					PaviliunView.open(master, PaviliunPayloads.PAGE_RAK, true);
					return InteractionResult.SUCCESS_SERVER;
				}
			}
			// the altar (or, on templates 8-9, the cemetery's stele)
			if (atAltar(owner, pos)) {
				if (stack.is(Dupa.DUPA)) {
					Upacara.incense(master, stack);
					return InteractionResult.SUCCESS_SERVER;
				}
				if (Makam.isTablet(stack)) {
					return Makam.layTablet(master, stack) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
				}
				if (stack.isEmpty()) {
					PaviliunView.open(master, PaviliunPayloads.PAGE_MAKAM, true);
					return InteractionResult.SUCCESS_SERVER;
				}
			}
			// a tombstone
			int slots = Makam.slots(owner, false);
			for (int i = 0; i < slots; i++) {
				BlockPos stone = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_GRAVE + i);
				if (stone != null && (pos.equals(stone) || pos.equals(stone.above()))) {
					if (Makam.isTablet(stack)) {
						return Makam.layTablet(master, stack) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
					}
					if (!stack.isEmpty()) {
						return InteractionResult.PASS;
					}
					if (master.isShiftKeyDown()) {
						PaviliunView.open(master, PaviliunPayloads.PAGE_MAKAM, true);
					} else {
						epitaph(master, i);
					}
					return InteractionResult.SUCCESS_SERVER;
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Sekte paviliun click failed", exception);
		}
		return InteractionResult.PASS;
	}

	private static boolean atAltar(UUID owner, BlockPos pos) {
		for (String id : new String[]{PaviliunKomponen.ANCHOR_ALTAR, PaviliunKomponen.ANCHOR_TABLE, PaviliunKomponen.ANCHOR_CENSER}) {
			BlockPos a = SectBuild.anchor(owner, id);
			if (a != null && near(pos, a, 2, 1, 2)) {
				return true;
			}
		}
		if (SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_ALTAR) == null) {
			BlockPos face = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_STELE);
			return face != null && near(pos, face, 1, 1, 1);
		}
		return false;
	}

	private static boolean near(BlockPos pos, BlockPos anchor, int dxz, int below, int above) {
		return Math.abs(pos.getX() - anchor.getX()) <= dxz && Math.abs(pos.getZ() - anchor.getZ()) <= dxz && pos.getY() >= anchor.getY() - below
				&& pos.getY() <= anchor.getY() + above;
	}

	/** The words on tombstone {@code slot}: who rests there, how and when they fell, their deeds. */
	private static void epitaph(ServerPlayer master, int slot) {
		UUID owner = master.getUUID();
		PaviliunData.Grave grave = null;
		for (PaviliunData.Grave g : PaviliunData.graves(owner)) {
			if (g.slot() == slot) {
				grave = g;
				break;
			}
		}
		if (grave == null) {
			Msg.actionBar(master, Component.translatable("kultivator.sekte.paviliun.makam.empty").withStyle(ChatFormatting.GRAY));
			return;
		}
		long ago = Math.max(0L, Ingatan.today(master.level().getServer()) - grave.day());
		Msg.chat(master, Component.translatable("kultivator.sekte.paviliun.epitaph.title", grave.name(), Rank.ofStep(grave.step()).displayName())
				.withColor(0xE8DCC0));
		Msg.chat(master, Component.translatable("kultivator.sekte.paviliun.epitaph.fell", grave.day(), ago,
				Component.translatable("kultivator.sekte.paviliun.sebab." + grave.cause(), grave.causeArg())).withStyle(ChatFormatting.GRAY));
		for (PaviliunData.Deed d : grave.deeds()) {
			MutableComponent line = Component.literal(" · ").append(Component.translatable(d.text(), d.arg()));
			Msg.chat(master, line.withColor(d.weight() > 0 ? 0xCFE8D2 : 0xB8B0C8));
		}
		if (grave.enshrined()) {
			Msg.chat(master, Component.translatable("kultivator.sekte.paviliun.epitaph.enshrined").withColor(0xFFD27A));
		}
		master.level().playSound(null, master.getX(), master.getY() + 1.0D, master.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F,
				0.6F);
	}

	// ================================================================== ticks

	private static void serverTick(MinecraftServer s) {
		server = s;
		if (!active()) {
			return;
		}
		ticks++;
		try {
			Makam.tick(s);
			if (ticks % 20L == 5L) {
				Upacara.tick(s);
			}
			if (ticks % 40L == 11L) {
				PaviliunSync.tick(s, ticks);
			}
			if (ticks % 20L == 13L) {
				// every 10 s per online master, spread over the seconds
				List<ServerPlayer> players = s.getPlayerList().getPlayers();
				int slot = (int) ((ticks / 20L) % 10L);
				for (int i = slot; i < players.size(); i += 10) {
					ServerPlayer player = players.get(i);
					UUID owner = player.getUUID();
					if (Halls.hallOf(owner) != null) {
						Makam.normalize(s, owner);
						Upacara.autoCheck(s, player);
					}
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Sekte paviliun server tick failed", exception);
		}
	}

	/** The Aula tablet's status line: the shelf, the readers, the graves and the blessing. */
	private static @Nullable Component statusLine(UUID owner) {
		if (!active()) {
			return null;
		}
		int books = PaviliunData.rack(owner).size();
		int graves = PaviliunData.graves(owner).size() + PaviliunData.lineage(owner).size();
		if (books == 0 && graves == 0) {
			return null;
		}
		return Component.translatable("kultivator.sekte.paviliun.status", books, BelajarMandiri.readers(owner).size(), graves,
				Math.round(AltarLeluhur.blessing(owner) * 100.0D)).withColor(0xC8D8B0);
	}

	private static void clear() {
		BelajarMandiri.clear();
		Makam.clear();
		Upacara.clear();
		PaviliunSync.clear();
		ticks = 0L;
		server = null;
	}

	private Paviliun() {
	}
}
