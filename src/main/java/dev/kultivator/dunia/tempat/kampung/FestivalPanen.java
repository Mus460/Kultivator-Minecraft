package dev.kultivator.dunia.tempat.kampung;

import dev.kultivator.desa.Desa;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Harvest Festival of a village (Festival Panen, DESIGN 38.C.2.3): on every {@code kampung.siklusPanen}-th day
 * ({@code d % 12 == 0}) from 09:00 to 18:00, the first time a player is near the bell of a village. The residents leave
 * their work for the festival schedule (38NL {@code Jadwal} reads the override {@code festival_panen} given through
 * {@link HidupApi#timpaJadwal}: harvest dance, the shared meal at the square), the bell rings, drums and flutes play
 * every ten seconds and green motes rise over the square; each visitor gets a share of the common meal once. A pledged
 * village gains {@code kampung.kesetiaanPanen} loyalty through the public {@code Desa.loyaltyAt}. Start and end are told
 * on {@link TempatEvents#ACARA} ({@code festival_panen}) so stories and rumours can follow.
 * <p>
 * Effects are vanilla particles and sounds sent by the server (a few per second near one bell, no blocks change); the
 * running festivals are memory only and the day of the last festival is saved per village ({@link KampungData}).
 */
final class FestivalPanen {

	static final String ACARA = "festival_panen";
	static final int MULAI = 9;
	static final int AKHIR = 18;

	/** Site -> game time the running festival ends. */
	private static final Map<Long, Long> AKTIF = new HashMap<>();
	/** Site -> players already fed by this festival. */
	private static final Map<Long, Set<UUID>> JAMUAN = new HashMap<>();
	/** Site -> game time the running festival started. */
	private static final Map<Long, Long> MULA = new HashMap<>();
	/** Site -> the next act of the day programme (0..{@link #AMBANG}.length). */
	private static final Map<Long, Integer> BABAK = new HashMap<>();
	/** Share of the festival's length at which each act begins: harvest dance, lion dance, tug of war, lanterns. */
	private static final double[] AMBANG = {0.08D, 0.32D, 0.58D, 0.85D};

	static void reset() {
		AKTIF.clear();
		JAMUAN.clear();
		MULA.clear();
		BABAK.clear();
	}

	/** The hour 0-23 of the overworld clock (0 ticks = 06:00, 38.N.3). */
	static int jam(MinecraftServer server) {
		return (int) ((Kota.timeOfDay(server) / 1000L + 6L) % 24L);
	}

	/** True on a festival day. */
	static boolean hariFestival(long day) {
		return Math.floorMod(day, KampungConfig.get().siklusPanen) == 0;
	}

	/** True while the festival of {@code site} runs. */
	static boolean aktif(long site, long now) {
		Long end = AKTIF.get(site);
		return end != null && now < end;
	}

	/** Every second near the bell. */
	static void dekat(ServerLevel level, DuniaAnchorEntity anchor, long site, List<ServerPlayer> within) {
		KampungConfig config = KampungConfig.get();
		if (!config.festivalPanen) {
			return;
		}
		long now = level.getGameTime();
		Long end = AKTIF.get(site);
		if (end != null) {
			if (now >= end) {
				Component name = namaDesa(level, site);
				for (ServerPlayer player : within) {
					Msg.chat(player, Component.translatable("kultivator.kampung.panen.tutup", name).withStyle(ChatFormatting.GOLD));
				}
				level.playSound(null, anchor.getX(), anchor.getY() + 3.0D, anchor.getZ(), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.5F, 0.8F);
				akhiri(level, site);
			} else {
				meriah(level, anchor, site, within, now);
			}
			return;
		}
		MinecraftServer server = level.getServer();
		long day = Kota.day(server);
		int hour = jam(server);
		if (!hariFestival(day) || hour < MULAI || hour >= AKHIR) {
			return;
		}
		KampungData data = KampungData.get(server);
		KampungData.Desa d = data.desa(site);
		if (d.hariPanen == day) {
			return;
		}
		mulai(level, anchor, data, d, day, hour);
	}

	/** Starts the festival of village {@code d} now (also {@code uji kampung panen}). */
	static void mulai(ServerLevel level, DuniaAnchorEntity anchor, KampungData data, KampungData.Desa d, long day, int hour) {
		long now = level.getGameTime();
		long tod = Kota.timeOfDay(level.getServer());
		long left = Math.max(1200L, (long) (AKHIR - Math.max(MULAI, Math.min(AKHIR - 1, hour))) * 1000L - tod % 1000L);
		long end = now + left;
		data.hariPanen(d, day);
		AKTIF.put(d.situs, end);
		JAMUAN.put(d.situs, new HashSet<>());
		MULA.put(d.situs, now);
		BABAK.put(d.situs, 0);
		HidupApi.timpaJadwal(d.situs, ACARA, end);
		TempatEvents.fireAcara(level, d.situs, ACARA, true);
		Component name = KampungNames.nama(d.ukuran, d.situs);
		int loyalty = KampungConfig.get().kesetiaanPanen;
		String pledged = loyalty > 0 ? Desa.loyaltyAt(level, anchor.blockPosition(), loyalty) : null;
		for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(anchor) <= 64.0D * 64.0D)) {
			Msg.title(player, Component.translatable("kultivator.kampung.panen.judul").withStyle(ChatFormatting.GOLD), name, 10, 60, 20);
			Msg.chat(player, Component.translatable("kultivator.kampung.panen.mulai", name).withStyle(ChatFormatting.GOLD));
			if (pledged != null) {
				Msg.chat(player, Component.translatable("kultivator.kampung.panen.setia", name, loyalty).withStyle(ChatFormatting.GREEN));
			}
		}
		level.playSound(null, anchor.getX(), anchor.getY() + 3.0D, anchor.getZ(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
		level.playSound(null, anchor.getX(), anchor.getY(), anchor.getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.2F, 1.0F);
	}

	/** The running festival's music, motes and the shared meal. */
	private static void meriah(ServerLevel level, DuniaAnchorEntity anchor, long site, List<ServerPlayer> within, long now) {
		double x = anchor.getX();
		double y = anchor.getY() + 1.2D;
		double z = anchor.getZ();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 6, 5.0D, 0.8D, 5.0D, 0.02D);
		long beat = Math.floorMod(now, 200L);
		if (beat < 20L) {
			level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_BASEDRUM, SoundSource.RECORDS, 1.4F, 0.9F);
			level.playSound(null, x + 3.0D, y, z, SoundEvents.NOTE_BLOCK_FLUTE, SoundSource.RECORDS, 1.0F, 1.2F);
			level.sendParticles(ParticleTypes.NOTE, x, y + 1.5D, z, 4, 2.0D, 0.6D, 2.0D, 0.6D);
		} else if (beat >= 100L && beat < 120L) {
			level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_BASEDRUM, SoundSource.RECORDS, 1.2F, 1.1F);
			level.playSound(null, x - 3.0D, y, z, SoundEvents.NOTE_BLOCK_FLUTE, SoundSource.RECORDS, 1.0F, 1.5F);
		}
		Long start = MULA.get(site);
		Long end = AKTIF.get(site);
		int next = BABAK.getOrDefault(site, AMBANG.length);
		if (start != null && end != null && next < AMBANG.length && end > start
				&& (double) (now - start) / (double) (end - start) >= AMBANG[next]) {
			BABAK.put(site, next + 1);
			babak(level, anchor, site, next, within);
		}
		KampungData.Desa d = KampungData.get(level.getServer()).cari(site);
		String gaya = d == null ? "sedang" : d.gaya;
		Set<UUID> fed = JAMUAN.computeIfAbsent(site, s -> new HashSet<>());
		for (ServerPlayer player : within) {
			if (player.isSpectator() || player.distanceToSqr(anchor) > 14.0D * 14.0D || !fed.add(player.getUUID())) {
				continue;
			}
			for (ItemStack stack : hidangan(gaya)) {
				beri(level, player, stack);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.6F, 1.0F);
			Msg.chat(player, Component.translatable("kultivator.kampung.panen.jamuan." + gaya).withStyle(ChatFormatting.YELLOW));
		}
	}

	/** One act of the day programme: a told scene with its own sounds and a short burst of vanilla particles. */
	private static void babak(ServerLevel level, DuniaAnchorEntity anchor, long site, int act, List<ServerPlayer> within) {
		double x = anchor.getX();
		double y = anchor.getY() + 1.0D;
		double z = anchor.getZ();
		Component name = namaDesa(level, site);
		Component line;
		switch (act) {
			case 0 -> {
				// harvest dance: a ring of motes and a rising flute
				for (int i = 0; i < 12; i++) {
					double a = Math.PI * 2.0D * i / 12.0D;
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x + Math.cos(a) * 5.0D, y, z + Math.sin(a) * 5.0D, 2, 0.2D, 0.4D, 0.2D, 0.0D);
				}
				level.sendParticles(ParticleTypes.NOTE, x, y + 2.0D, z, 6, 3.0D, 0.5D, 3.0D, 0.8D);
				level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_FLUTE, SoundSource.RECORDS, 1.2F, 1.0F);
				level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_CHIME, SoundSource.RECORDS, 1.0F, 1.4F);
				line = Component.translatable("kultivator.kampung.panen.babak.0", name);
			}
			case 1 -> {
				// lion dance: firecracker sparks, drum and snare
				level.sendParticles(ParticleTypes.FIREWORK, x, y + 1.5D, z, 24, 2.5D, 1.0D, 2.5D, 0.08D);
				level.sendParticles(ParticleTypes.CRIT, x, y + 0.5D, z, 12, 2.0D, 0.3D, 2.0D, 0.3D);
				level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.AMBIENT, 1.5F, 1.2F);
				level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_BASEDRUM, SoundSource.RECORDS, 1.6F, 0.7F);
				level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_SNARE, SoundSource.RECORDS, 1.2F, 1.0F);
				line = Component.translatable("kultivator.kampung.panen.babak.1", name);
			}
			case 2 -> {
				// tug of war: the crowd cheers the winning side
				Component side = Component.translatable("kultivator.kampung.panen.regu." + level.getRandom().nextInt(2));
				level.sendParticles(ParticleTypes.CRIT, x, y, z, 10, 4.0D, 0.2D, 1.0D, 0.2D);
				level.playSound(null, x, y, z, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.4F, 0.9F);
				level.playSound(null, x, y, z, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.2F, 1.2F);
				line = Component.translatable("kultivator.kampung.panen.babak.2", name, side);
			}
			default -> {
				// lanterns: slow rising lights over the square
				level.sendParticles(ParticleTypes.END_ROD, x, y + 2.0D, z, 16, 4.0D, 1.0D, 4.0D, 0.02D);
				level.sendParticles(ParticleTypes.SMALL_FLAME, x, y + 1.0D, z, 8, 3.0D, 0.5D, 3.0D, 0.01D);
				level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1.2F, 0.8F);
				line = Component.translatable("kultivator.kampung.panen.babak.3", name);
			}
		}
		for (ServerPlayer player : within) {
			Msg.chat(player, line.copy().withStyle(ChatFormatting.YELLOW));
		}
	}

	/** The common meal of a village style (stacks made per call, never kept). */
	private static List<ItemStack> hidangan(String gaya) {
		return switch (gaya) {
			case "salju" -> List.of(new ItemStack(Items.RABBIT_STEW, 1), new ItemStack(Items.COOKED_MUTTON, 2), new ItemStack(Items.BAKED_POTATO, 2));
			case "sabana" -> List.of(new ItemStack(Items.COOKED_BEEF, 2), new ItemStack(Items.BREAD, 2), new ItemStack(Items.MELON_SLICE, 3));
			case "sakura" -> List.of(new ItemStack(Items.COOKED_SALMON, 2), new ItemStack(Items.BREAD, 2), new ItemStack(Items.COOKIE, 4));
			case "gurun" -> List.of(new ItemStack(Items.COOKED_RABBIT, 2), new ItemStack(Items.BREAD, 2), new ItemStack(Items.MELON_SLICE, 3));
			case "rawa" -> List.of(new ItemStack(Items.COOKED_COD, 2), new ItemStack(Items.BEETROOT_SOUP, 1), new ItemStack(Items.BREAD, 2));
			case "terasering" -> List.of(new ItemStack(Items.COOKED_CHICKEN, 2), new ItemStack(Items.BAKED_POTATO, 2), new ItemStack(Items.PUMPKIN_PIE, 1));
			default -> List.of(new ItemStack(Items.BREAD, 2), new ItemStack(Items.BAKED_POTATO, 2), new ItemStack(Items.PUMPKIN_PIE, 1));
		};
	}

	private static Component namaDesa(ServerLevel level, long site) {
		KampungData.Desa d = KampungData.get(level.getServer()).cari(site);
		return d == null ? KampungNames.nama("desa", site) : KampungNames.nama(d.ukuran, d.situs);
	}

	private static void beri(ServerLevel level, ServerPlayer player, ItemStack stack) {
		player.getInventory().add(stack);
		if (!stack.isEmpty()) {
			player.spawnAtLocation(level, stack);
		}
	}

	/** Ends the festival of {@code site}. */
	static void akhiri(ServerLevel level, long site) {
		if (AKTIF.remove(site) == null) {
			return;
		}
		JAMUAN.remove(site);
		MULA.remove(site);
		BABAK.remove(site);
		HidupApi.timpaJadwal(site, "", 0L);
		TempatEvents.fireAcara(level, site, ACARA, false);
	}

	/** The bell unloaded: a festival keeps its end time; nothing to clean. */
	static void lepas(long site) {
		JAMUAN.computeIfPresent(site, (k, v) -> v.isEmpty() ? null : v);
	}

	private FestivalPanen() {
	}
}
