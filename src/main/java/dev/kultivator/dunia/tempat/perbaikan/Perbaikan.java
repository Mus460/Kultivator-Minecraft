package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.kehancuran.WorldBreaker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Init of 38PR, repair and compensation (DESIGN 38.R): blueprints of cared-for places ({@link Rekam},
 * {@link CetakStore}), the scanner ({@link PindaiTempat}) with its attribution log ({@link LogAtribusi}), animated
 * repair crews ({@link PekerjaanPerbaikan}, {@link Tukang}), bills and their collector ({@link Penagihan},
 * {@link Penagih}, sheet {@link PerbaikanLayar}), fear ({@link Takut}), seized goods ({@link Sitaan}), help
 * ({@link Bantu}), the scaffold payload ({@link PerancahNet}) and the test commands ({@link PerbaikanCommands}).
 * <p>Blocks change only through {@link WorldBreaker#submitRestore} (repairs) and, for {@code uji tempat rusak}, through
 * the same restore path with air as the target (an op's test damage). Called once from {@code Tempat.init()}. Registry
 * entries (the payload, the commands, the screen) are registered always; {@code perbaikan.aktif} only gates behaviour.
 */
public final class Perbaikan {

	private static boolean initialised;
	private static long hariTerakhir = Long.MIN_VALUE;
	private static volatile @Nullable MinecraftServer server;

	private record Tugas(long saat, Runnable kerja) {
	}

	private static final java.util.List<Tugas> NANTI = new java.util.ArrayList<>();

	/** Runs {@code kerja} on the server thread {@code ticks} ticks from now (test commands; dropped at server stop). */
	static void nanti(MinecraftServer current, int ticks, Runnable kerja) {
		if (NANTI.size() < 32) {
			NANTI.add(new Tugas(current.getTickCount() + Math.max(1, ticks), kerja));
		}
	}

	/** The running server (null before start and after stop). */
	static @Nullable MinecraftServer server() {
		return server;
	}

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		PerbaikanConfig.init();
		Rekam.init();
		PerancahNet.init();
		PerbaikanLayar.init();
		PerbaikanCommands.init();
		ServerLifecycleEvents.SERVER_STARTED.register(started -> {
			server = started;
			CetakStore.mulai(started);
			Rekam.mulai(started);
			PekerjaanPerbaikan.mulai(started);
			hariTerakhir = Long.MIN_VALUE;
		});
		ServerLifecycleEvents.BEFORE_SAVE.register((saving, flush, force) -> CetakStore.simpanSemua(saving));
		ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
			Penagih.lupa();
			Tukang.lupa();
			CetakStore.berhenti(stopping);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			server = null;
			NANTI.clear();
			Rekam.berhenti();
			PindaiTempat.berhenti();
			PekerjaanPerbaikan.berhenti();
			LogAtribusi.lupa();
			Bantu.lupa();
			Takut.lupa();
			PerancahNet.lupa();
		});
		ServerTickEvents.END_SERVER_TICK.register(Perbaikan::tick);
		PlayerBlockBreakEvents.AFTER.register(Perbaikan::onBreak);
		UseBlockCallback.EVENT.register(Perbaikan::onUseBlock);
		UseEntityCallback.EVENT.register(Perbaikan::onUseEntity);
		ServerEntityEvents.ENTITY_LOAD.register(Perbaikan::onEntityLoad);
		WorldBreaker.addChangeListener(Perbaikan::onCast);
		TempatEvents.ACARA.register(Perbaikan::onAcara);
	}

	// ================================================================== tick

	private static void tick(MinecraftServer current) {
		MinecraftServer server = current;
		if (!PerbaikanConfig.get().aktif) {
			return;
		}
		if (!NANTI.isEmpty()) {
			long tick = server.getTickCount();
			for (Tugas task : java.util.List.copyOf(NANTI)) {
				if (tick >= task.saat()) {
					NANTI.remove(task);
					try {
						task.kerja().run();
					} catch (RuntimeException exception) {
						Kultivator.LOGGER.error("Kultivator: a delayed repair task failed", exception);
					}
				}
			}
		}
		try {
			int t = server.getTickCount();
			Rekam.tick(server);
			PindaiTempat.tick(server);
			if (t % 10 == 0) {
				Tukang.tick(server);
				Penagih.tick(server);
			}
			if (t % 20 == 0) {
				PekerjaanPerbaikan.tick(server);
				Bantu.tick(server);
			}
			if (t % 40 == 0) {
				Takut.tick(server);
				PerancahNet.kirim(server);
			}
			if (t % 1200 == 0) {
				LogAtribusi.bersihkan(server.overworld().getGameTime());
				long day = Kota.day(server);
				if (day != hariTerakhir) {
					hariTerakhir = day;
					Sitaan.harian(server);
					Penagihan.harian(server);
				}
			}
			if (t % CetakStore.SIMPAN_TICK == 0) {
				CetakStore.simpanSemua(server);
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: place repair tick failed", exception);
		}
	}

	// ================================================================== attribution sources (38.R.2)

	private static void onBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer) || !PerbaikanConfig.get().aktif) {
			return;
		}
		TempatRawat t = Rekam.tempatDi(server, pos);
		if (t == null) {
			return;
		}
		LogAtribusi.catatSekitar(t.kunci, server.getGameTime(), Gugus.PECAH, player.getUUID(), pos, 1);
		PindaiTempat.prioritas(t, new BoundingBox(pos).inflatedBy(1));
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer) || player.isSpectator() || !PerbaikanConfig.get().aktif) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			return InteractionResult.PASS;
		}
		BlockPos clicked = hit.getBlockPos();
		BlockPos placed = clicked.relative(hit.getDirection());
		TempatRawat t = Rekam.tempatDi(server, placed);
		if (t == null) {
			t = Rekam.tempatDi(server, clicked);
		}
		if (t == null) {
			return InteractionResult.PASS;
		}
		long now = server.getGameTime();
		if (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE)) {
			LogAtribusi.catatSekitar(t.kunci, now, Gugus.API, player.getUUID(), placed, 8);
			PindaiTempat.prioritasNanti(t, new BoundingBox(placed).inflatedBy(8), server.getServer().getTickCount() + 200L);
		} else if (held.getItem() instanceof BlockItem) {
			LogAtribusi.catat(t.kunci, now, Gugus.PASANG, player.getUUID(), BoundingBox.encapsulating(new BoundingBox(clicked), new BoundingBox(placed)));
			PindaiTempat.prioritasNanti(t, BoundingBox.encapsulating(new BoundingBox(clicked), new BoundingBox(placed)).inflatedBy(1),
					server.getServer().getTickCount() + 2L);
		}
		return InteractionResult.PASS;
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof PrimedTnt tnt) || !PerbaikanConfig.get().aktif) {
			return;
		}
		BlockPos pos = tnt.blockPosition();
		TempatRawat t = Rekam.tempatDi(level, pos);
		if (t == null) {
			t = Rekam.terdekat(level, pos, 16.0D);
		}
		if (t == null) {
			return;
		}
		LivingEntity owner = tnt.getOwner();
		UUID who = owner instanceof ServerPlayer player ? player.getUUID() : null;
		LogAtribusi.catatSekitar(t.kunci, level.getGameTime(), who == null ? Gugus.MONSTER : Gugus.TNT, who, pos, 8);
		PindaiTempat.prioritasNanti(t, new BoundingBox(pos).inflatedBy(8), level.getServer().getTickCount() + Math.max(20, tnt.getFuse() + 10));
	}

	private static void onCast(ServerLevel level, UUID owner, String disciple, BoundingBox box, int blocks) {
		if (!PerbaikanConfig.get().aktif) {
			return;
		}
		Set<TempatRawat> hit = new HashSet<>();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				for (TempatRawat t : Rekam.diChunk(level.dimension(), ChunkPos.pack(cx, cz))) {
					if (t.kotak.intersects(box.inflatedBy(2))) {
						hit.add(t);
					}
				}
			}
		}
		for (TempatRawat t : hit) {
			LogAtribusi.catat(t.kunci, level.getGameTime(), Gugus.KEHANCURAN, owner, box.inflatedBy(2));
			PindaiTempat.prioritas(t, box.inflatedBy(1));
		}
	}

	/**
	 * A destroying explosion is about to break {@code blok} (mixin on {@code ServerExplosion}): a player behind it (his
	 * TNT, his fireball) is {@code ledakan_tnt}, anything else (creeper, ghast, wither) {@code monster}.
	 */
	static void onLedakan(ServerLevel level, java.util.List<BlockPos> blok, @Nullable Entity sumber) {
		if (!PerbaikanConfig.get().aktif || blok.isEmpty()) {
			return;
		}
		BoundingBox box = BoundingBox.encapsulatingPositions(blok).orElse(null);
		if (box == null) {
			return;
		}
		UUID who = sumber instanceof ServerPlayer player ? player.getUUID() : null;
		for (TempatRawat t : tempatKena(level, box)) {
			LogAtribusi.catat(t.kunci, level.getGameTime(), who == null ? Gugus.MONSTER : Gugus.TNT, who, box.inflatedBy(1));
			PindaiTempat.prioritasNanti(t, box.inflatedBy(1), level.getServer().getTickCount() + 2L);
		}
	}

	/**
	 * A block was destroyed, placed or changed by an entity (mixin on {@code ServerLevel.gameEvent}): a mob or a stray
	 * projectile is {@code monster} (enderman, zombie at a door, wither); a player's projectile is his {@code pecah}.
	 * Players' own hands are logged by the break and use events above.
	 */
	static void onUbahBlok(ServerLevel level, BlockPos pos, Entity pelaku) {
		if (!PerbaikanConfig.get().aktif) {
			return;
		}
		TempatRawat t = Rekam.tempatDi(level, pos);
		if (t == null) {
			return;
		}
		UUID who = null;
		if (pelaku instanceof net.minecraft.world.entity.projectile.Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
			who = player.getUUID();
		}
		LogAtribusi.catatBlok(t.kunci, level.getGameTime(), who == null ? Gugus.MONSTER : Gugus.PECAH, who, pos);
		PindaiTempat.prioritasNanti(t, new BoundingBox(pos), level.getServer().getTickCount() + 2L);
	}

	/** A fire is eating the block at {@code pos} (mixin on {@code FireBlock.checkBurnOut}): natural fire spread. */
	static void onTerbakar(ServerLevel level, BlockPos pos) {
		if (!PerbaikanConfig.get().aktif) {
			return;
		}
		TempatRawat t = Rekam.tempatDi(level, pos);
		if (t != null) {
			LogAtribusi.catatSekitar(t.kunci, level.getGameTime(), Gugus.API, null, pos, 1);
			PindaiTempat.prioritasNanti(t, new BoundingBox(pos).inflatedBy(1), level.getServer().getTickCount() + 2L);
		}
	}

	private static Set<TempatRawat> tempatKena(ServerLevel level, BoundingBox box) {
		Set<TempatRawat> hit = new HashSet<>();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				for (TempatRawat t : Rekam.diChunk(level.dimension(), ChunkPos.pack(cx, cz))) {
					if (t.kotak.intersects(box.inflatedBy(1))) {
						hit.add(t);
					}
				}
			}
		}
		return hit;
	}

	private static void onAcara(ServerLevel level, long situsId, String acara, boolean mulai) {
		String a = acara == null ? "" : acara.toLowerCase(Locale.ROOT);
		if (!(a.contains("pertahanan") || a.contains("serbu") || a.contains("gelombang") || a.contains("bajak") || a.contains("serang")
				|| a.contains("raid"))) {
			return;
		}
		if (mulai) {
			LogAtribusi.serangan(situsId, level.getGameTime() + 24000L);
		} else {
			LogAtribusi.seranganSelesai(situsId);
		}
	}

	/** A right click on a crew body or foreman opens the help sheet of its job (38.R.6). */
	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)
				|| !(entity instanceof KultivatorNpc npc) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		if (Penagih.tagihanDari(sp, npc.getId()) != 0L) {
			return TempatLayar.bukaDari(sp, LembarKunci.Tagihan.LAYAR, npc.getId()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		Gugus g = Tukang.gugusDari(server.getServer(), npc);
		if (g == null) {
			return InteractionResult.PASS;
		}
		return TempatLayar.bukaDari(sp, LembarKunci.Tagihan.LAYAR, npc.getId()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	private Perbaikan() {
	}
}
