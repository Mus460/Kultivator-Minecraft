package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paviliun Teh Awan (DESIGN 38.T.5.4), screen {@code tema_teh} of the tea master Lu Yuqing (keys {@code TemaLayar.Teh}),
 * the three house teas and the spring tea picking on the terraces of Bukit Teh.
 * <ul>
 * <li><b>Teh Bi Luo</b> ({@link #HARGA_BI_LUO} NB; -25 % with the brewing permit of {@code tema_air_teh_pertama}): while
 * active (10 minutes), every full minute of meditation gives +{@link #PAHAM_BI_LUO} comprehension of the player's Dao.</li>
 * <li><b>Teh Embun Gunung</b> ({@link #HARGA_EMBUN} NB): qi regeneration x{@link #REGEN_EMBUN} for 5 minutes
 * ({@code Qi.addRegenFactor}).</li>
 * <li><b>Teh Seribu Tahun</b> ({@link #HARGA_SERIBU} NB, band 3 and up, one cup per town per world day): once a world day
 * it eases heart-demon pressure by {@link #IBLIS_HATI}; when there is none, +{@link #PAHAM_SERIBU} per meditation minute
 * for 10 minutes.</li>
 * </ul>
 * Teas never stack: the strongest active one counts (seribu &gt; bi luo &gt; embun). A cup is drunk at once on use (the
 * item is consumed by this code). The tea ceremony ({@link #HARGA_UPACARA} NB, once a world day): Lu Yuqing brews and
 * pours (clips {@code seduh_teh}, {@code tuang_teh}, 4 s) and the guest receives a Bi Luo effect without a cup.
 * <p>
 * Picking: in spring (season 0 of {@code Jadwal}), 04:00-08:00, right-clicking a tea bush (azalea) on the terraces of
 * district {@code bukit_teh} gives one Daun Teh Embun (at most {@link #PETIK_PAGI} a morning; the bush is not changed).
 * Lu Yuqing buys fresh leaves at 1 NB each, {@link #JUAL_MAKS} a day (dialog effect {@code air_jual_daun}).
 */
public final class PaviliunTeh {

	public static final long HARGA_BI_LUO = 8L;
	public static final long HARGA_EMBUN = 5L;
	public static final long HARGA_SERIBU = 60L;
	public static final long HARGA_UPACARA = 12L;
	public static final int PITA_SERIBU = 3;
	public static final double PAHAM_BI_LUO = 0.03D;
	public static final double PAHAM_SERIBU = 0.05D;
	public static final double REGEN_EMBUN = 1.08D;
	public static final double IBLIS_HATI = 5.0D;
	public static final int LAMA_BI_LUO = 12000;
	public static final int LAMA_EMBUN = 6000;
	public static final int LAMA_SERIBU = 12000;
	public static final int PETIK_PAGI = 10;
	public static final int JUAL_MAKS = 20;
	public static final double DISKON_IZIN = 0.25D;

	/** Tea ranks: higher wins. */
	public static final int TEH_EMBUN = 1;
	public static final int TEH_BI_LUO = 2;
	public static final int TEH_SERIBU = 3;

	private record Aktif(int jenis, long sampai) {
	}

	private static final Map<UUID, Aktif> AKTIF = new HashMap<>();
	private static final Map<UUID, Integer> DETIK_MEDITASI = new HashMap<>();

	static void init() {
		TemaLayar.daftar(TemaLayar.Teh.LAYAR, PaviliunTeh::bangun, PaviliunTeh::aksi);
		Qi.addRegenFactor(p -> aktif(p) == TEH_EMBUN ? REGEN_EMBUN : 1.0D);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer sp) || level.isClientSide()) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			int jenis = jenisTeh(stack.getItem());
			if (jenis == 0) {
				return InteractionResult.PASS;
			}
			minum(sp, jenis, stack);
			return InteractionResult.SUCCESS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
				return InteractionResult.PASS;
			}
			BlockState state = level.getBlockState(hit.getBlockPos());
			if (!state.is(Blocks.AZALEA) && !state.is(Blocks.FLOWERING_AZALEA)) {
				return InteractionResult.PASS;
			}
			return petik(sp, sl, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}

	static void reset() {
		AKTIF.clear();
		DETIK_MEDITASI.clear();
	}

	static void keluar(UUID id) {
		DETIK_MEDITASI.remove(id);
	}

	// ================================================================== effects

	private static int jenisTeh(Item item) {
		if (item == TemaItems.TEH_BI_LUO) {
			return TEH_BI_LUO;
		}
		if (item == TemaItems.TEH_EMBUN_GUNUNG) {
			return TEH_EMBUN;
		}
		if (item == TemaItems.TEH_SERIBU_TAHUN) {
			return TEH_SERIBU;
		}
		return 0;
	}

	/** The rank of the player's active tea (0 none). */
	public static int aktif(ServerPlayer player) {
		Aktif a = AKTIF.get(player.getUUID());
		if (a == null) {
			return 0;
		}
		if (a.sampai < player.level().getGameTime()) {
			AKTIF.remove(player.getUUID());
			return 0;
		}
		return a.jenis;
	}

	private static void minum(ServerPlayer player, int jenis, ItemStack stack) {
		int now = aktif(player);
		if (now > jenis) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.teh.lebih_kuat").withStyle(ChatFormatting.YELLOW));
			return;
		}
		stack.shrink(1);
		player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.7F, 1.1F);
		beriEfek(player, jenis);
	}

	/** Starts tea {@code jenis} for the player (cup drunk or ceremony). */
	static void beriEfek(ServerPlayer player, int jenis) {
		long now = player.level().getGameTime();
		MinecraftServer server = player.level().getServer();
		if (jenis == TEH_SERIBU) {
			CompoundTag me = AirKota.pemain(server, player.getUUID());
			long hari = AirKota.hari(server.overworld());
			CultivationData data = Cultivators.get(player);
			if (me.getLongOr("seribu_hari", -1L) != hari && data.heartDemonPressure() > 0.0D) {
				me.putLong("seribu_hari", hari);
				AirKota.ubah(server);
				double before = data.heartDemonPressure();
				data.setHeartDemonPressure(Math.max(0.0D, before - IBLIS_HATI));
				Msg.chat(player, Component.translatable("kultivator.kota_tema.air.teh.seribu.hati", Msg.number(before - data.heartDemonPressure()))
						.withStyle(ChatFormatting.LIGHT_PURPLE));
				return;
			}
		}
		int lama = jenis == TEH_EMBUN ? LAMA_EMBUN : jenis == TEH_SERIBU ? LAMA_SERIBU : LAMA_BI_LUO;
		AKTIF.put(player.getUUID(), new Aktif(jenis, now + lama));
		DETIK_MEDITASI.put(player.getUUID(), 0);
		Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.teh.aktif." + jenis, lama / 1200).withStyle(ChatFormatting.GREEN));
	}

	/** Every 20 ticks (from {@link Air}): minutes of meditation under a tea. */
	static void detik(MinecraftServer server) {
		if (AKTIF.isEmpty()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int jenis = aktif(player);
			if (jenis != TEH_BI_LUO && jenis != TEH_SERIBU || !Meditation.isMeditating(player)) {
				continue;
			}
			int s = DETIK_MEDITASI.getOrDefault(player.getUUID(), 0) + 1;
			if (s >= 60) {
				s = 0;
				CultivationData data = Cultivators.get(player);
				Dao dao = data.dao() != null ? data.dao() : Dao.LIFE;
				Comprehension.grant(player, dao, jenis == TEH_SERIBU ? PAHAM_SERIBU : PAHAM_BI_LUO);
			}
			DETIK_MEDITASI.put(player.getUUID(), s);
		}
	}

	// ================================================================== picking

	private static boolean petik(ServerPlayer player, ServerLevel level, BlockPos pos) {
		AirKota.Hidup kota = AirKota.di(level, pos);
		if (kota == null || !"bukit_teh".equals(kota.distrik(pos))) {
			return false;
		}
		long hari = AirKota.hari(level);
		int musim = (int) Math.floorMod(hari / Jadwal.HARI_MUSIM, 4L);
		int jam = AirKota.jamHari(level);
		if (musim != Jadwal.SEMI || jam < 4 || jam >= 8) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.petik.bukan_waktu").withStyle(ChatFormatting.GRAY));
			return true;
		}
		CompoundTag me = AirKota.pemain(level.getServer(), player.getUUID());
		if (me.getLongOr("petik_hari", -1L) != hari) {
			me.putLong("petik_hari", hari);
			me.putInt("petik", 0);
		}
		int n = me.getIntOr("petik", 0);
		if (n >= PETIK_PAGI) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.petik.cukup", PETIK_PAGI).withStyle(ChatFormatting.YELLOW));
			return true;
		}
		me.putInt("petik", n + 1);
		AirKota.ubah(level.getServer());
		Barang.beri(player, new ItemStack(TemaItems.DAUN_TEH_EMBUN));
		level.playSound(null, pos, SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.BLOCKS, 0.7F, 1.3F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D, 4, 0.3D, 0.2D, 0.3D, 0.0D);
		Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.petik.dapat", n + 1, PETIK_PAGI).withStyle(ChatFormatting.GREEN));
		AirCerita.peristiwa(player, "air_petik", Integer.toString(n + 1));
		return true;
	}

	/** Lu Yuqing buys fresh tea leaves (dialog effect); the message says how many. */
	static void jualDaun(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompoundTag me = AirKota.pemain(server, player.getUUID());
		long hari = AirKota.hari(server.overworld());
		if (me.getLongOr("jual_daun_hari", -1L) != hari) {
			me.putLong("jual_daun_hari", hari);
			me.putInt("jual_daun", 0);
		}
		int sisa = JUAL_MAKS - me.getIntOr("jual_daun", 0);
		int have = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(TemaItems.DAUN_TEH_EMBUN)) {
				have += inv.getItem(i).getCount();
			}
		}
		int n = Math.min(sisa, have);
		if (n <= 0) {
			Msg.actionBar(player, Component.translatable(sisa <= 0 ? "kultivator.kota_tema.air.daun.cukup" : "kultivator.kota_tema.air.daun.tak_ada")
					.withStyle(ChatFormatting.YELLOW));
			return;
		}
		RumahSutra.ambil(player, TemaItems.DAUN_TEH_EMBUN, n);
		NilaiBatu.give(player, n);
		me.putInt("jual_daun", me.getIntOr("jual_daun", 0) + n);
		AirKota.ubah(server);
		Msg.chat(player, Component.translatable("kultivator.kota_tema.air.daun.jual", n, NilaiBatu.text(n)).withStyle(ChatFormatting.GREEN));
	}

	// ================================================================== the screen

	private static long harga(ServerPlayer player, int jenis) {
		long base = jenis == TEH_BI_LUO ? HARGA_BI_LUO : jenis == TEH_EMBUN ? HARGA_EMBUN : HARGA_SERIBU;
		if (jenis == TEH_BI_LUO && CeritaApi.bendera(player, "tema.air.izin_seduh")) {
			base = Math.max(1L, Math.round(base * (1.0D - DISKON_IZIN)));
		}
		return base;
	}

	private static Item item(int jenis) {
		return jenis == TEH_BI_LUO ? TemaItems.TEH_BI_LUO : jenis == TEH_EMBUN ? TemaItems.TEH_EMBUN_GUNUNG : TemaItems.TEH_SERIBU_TAHUN;
	}

	private static String id(int jenis) {
		return jenis == TEH_BI_LUO ? "bi_luo" : jenis == TEH_EMBUN ? "embun_gunung" : "seribu_tahun";
	}

	private static int jenisDari(String id) {
		return switch (id) {
			case "bi_luo" -> TEH_BI_LUO;
			case "embun_gunung" -> TEH_EMBUN;
			case "seribu_tahun" -> TEH_SERIBU;
			default -> 0;
		};
	}

	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = AirLayar.npc(player, npcId);
		if (npc == null || !AirTokoh.adalah(npc, AirTokoh.LU_YUQING)) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		AirKota.Hidup kota = AirKota.di(player);
		CompoundTag tag = AirLayar.kepala(player, "kultivator.kota_tema.air.teh.judul", LembarKunci.GAYA_KAYU, npc);
		var reg = player.level().registryAccess();
		long uang = NilaiBatu.total(player);
		int pita = Barang.pita(player);
		ListTag menu = new ListTag();
		for (int jenis : new int[] {TEH_BI_LUO, TEH_EMBUN, TEH_SERIBU}) {
			CompoundTag e = new CompoundTag();
			e.putString(LembarKunci.ID, id(jenis));
			ItemStack cup = new ItemStack(item(jenis));
			LembarKunci.teks(e, LembarKunci.NAMA, cup.getHoverName());
			LembarKunci.barang(e, cup, reg);
			long h = harga(player, jenis);
			e.putLong(LembarKunci.HARGA, h);
			LembarKunci.teks(e, TemaLayar.Teh.EFEK, Component.translatable("kultivator.kota_tema.air.teh.efek." + id(jenis)));
			int stok = -1;
			String keadaan = uang < h ? LembarKunci.K_KURANG_UANG : LembarKunci.K_OK;
			if (jenis == TEH_SERIBU) {
				e.putInt(TemaLayar.Teh.PITA_MIN, PITA_SERIBU);
				stok = kota != null && AirKota.kota(server, kota.situs).getLongOr("seribu_dijual", -1L) == AirKota.hari(server.overworld()) ? 0 : 1;
				if (pita < PITA_SERIBU) {
					keadaan = LembarKunci.K_TERKUNCI;
					LembarKunci.teks(e, LembarKunci.ALASAN, Component.translatable("kultivator.kota_tema.air.teh.pita", PITA_SERIBU));
				} else if (stok == 0) {
					keadaan = LembarKunci.K_HABIS;
				}
			}
			e.putInt(LembarKunci.STOK, stok);
			e.putString(LembarKunci.KEADAAN, keadaan);
			menu.add(e);
		}
		tag.put(TemaLayar.Teh.MENU, menu);
		int now = aktif(player);
		if (now > 0) {
			CompoundTag a = new CompoundTag();
			LembarKunci.teks(a, LembarKunci.NAMA, new ItemStack(item(now)).getHoverName());
			a.putLong(LembarKunci.SISA_TICK, Math.max(0L, AKTIF.get(player.getUUID()).sampai - player.level().getGameTime()));
			tag.put(TemaLayar.Teh.AKTIF_TEH, a);
		}
		CompoundTag upacara = new CompoundTag();
		upacara.putLong(LembarKunci.HARGA, HARGA_UPACARA);
		CompoundTag me = AirKota.pemain(server, player.getUUID());
		boolean hariIni = me.getLongOr("upacara_hari", -1L) == AirKota.hari(server.overworld());
		upacara.putBoolean(LembarKunci.BISA, !hariIni && uang >= HARGA_UPACARA);
		if (hariIni) {
			LembarKunci.teks(upacara, LembarKunci.ALASAN, Component.translatable("kultivator.kota_tema.air.teh.upacara.besok"));
		}
		tag.put(TemaLayar.Teh.UPACARA, upacara);
		return tag;
	}

	static void aksi(ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		String verb = LembarKunci.verb(op);
		KultivatorNpc npc = AirLayar.npc(player, npcId);
		if (TemaLayar.Teh.OP_BELI.equals(verb)) {
			beli(player, jenisDari(LembarKunci.arg(parts, 1)));
		} else if (TemaLayar.Teh.OP_UPACARA.equals(verb)) {
			upacara(player, npc);
		}
	}

	private static void beli(ServerPlayer player, int jenis) {
		if (jenis == 0) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		AirKota.Hidup kota = AirKota.di(player);
		if (jenis == TEH_SERIBU) {
			if (Barang.pita(player) < PITA_SERIBU || kota == null) {
				AirLayar.peringatan(player, Component.translatable("kultivator.kota_tema.air.teh.pita", PITA_SERIBU));
				return;
			}
			CompoundTag town = AirKota.kota(server, kota.situs);
			if (town.getLongOr("seribu_dijual", -1L) == AirKota.hari(server.overworld())) {
				AirLayar.peringatan(player, Component.translatable("kultivator.kota_tema.air.teh.seribu.habis"));
				return;
			}
		}
		long h = harga(player, jenis);
		if (!NilaiBatu.pay(player, h)) {
			AirLayar.pesan(player, Component.translatable("kultivator.kota_tema.air.uang_kurang", NilaiBatu.text(h)), false);
			return;
		}
		if (jenis == TEH_SERIBU && kota != null) {
			AirKota.kota(server, kota.situs).putLong("seribu_dijual", AirKota.hari(server.overworld()));
			AirKota.ubah(server);
		}
		Barang.beri(player, new ItemStack(item(jenis)));
		AirLayar.pesan(player, Component.translatable("kultivator.kota_tema.air.teh.beli", new ItemStack(item(jenis)).getHoverName(),
				NilaiBatu.text(h)), true);
	}

	private static void upacara(ServerPlayer player, @Nullable KultivatorNpc master) {
		MinecraftServer server = player.level().getServer();
		CompoundTag me = AirKota.pemain(server, player.getUUID());
		long hari = AirKota.hari(server.overworld());
		if (me.getLongOr("upacara_hari", -1L) == hari) {
			AirLayar.peringatan(player, Component.translatable("kultivator.kota_tema.air.teh.upacara.besok"));
			return;
		}
		if (!NilaiBatu.pay(player, HARGA_UPACARA)) {
			AirLayar.pesan(player, Component.translatable("kultivator.kota_tema.air.uang_kurang", NilaiBatu.text(HARGA_UPACARA)), false);
			return;
		}
		me.putLong("upacara_hari", hari);
		AirKota.ubah(server);
		if (master != null) {
			HidupApi.tatap(master, player);
			HidupApi.klip(master, GerakId.id("seduh_teh"), PropId.id("teko"), 40);
			master.say(Component.translatable("kultivator.kota_tema.air.teh.upacara.kata"), 80);
			Air.nanti(server, 40, () -> {
				if (!master.isRemoved()) {
					HidupApi.klip(master, GerakId.id("tuang_teh"), PropId.id("teko"), 40);
				}
			});
		}
		Air.nanti(server, 80, () -> {
			if (!player.isRemoved()) {
				beriEfek(player, TEH_BI_LUO);
				player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.6F, 1.0F);
				Msg.chat(player, Component.translatable("kultivator.kota_tema.air.teh.upacara.selesai").withStyle(ChatFormatting.GREEN));
				AirCerita.peristiwa(player, "air_upacara", "teh");
			}
		});
		AirLayar.pesan(player, Component.translatable("kultivator.kota_tema.air.teh.upacara.mulai"), true);
	}

	/** Everything the tea house knows as ids (for tests). */
	static List<String> menuIds() {
		return List.of("bi_luo", "embun_gunung", "seribu_tahun");
	}

	private PaviliunTeh() {
	}
}
