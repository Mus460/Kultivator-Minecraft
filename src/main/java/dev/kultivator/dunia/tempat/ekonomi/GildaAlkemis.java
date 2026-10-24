package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.alchemy.Alchemy;
import dev.kultivator.alchemy.AlchemyFurnace;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Gilda Alkemis, the capital's Alchemist Guild counter (DESIGN 38.B.3 {@code gilda_alkemis_a/b}, 38.M.2.1, screen
 * {@code gilda_alkemis} of 38.U.3.9). The guild's eight cauldrons are the Tungku Gilda: a pill finished by a player there
 * (inside the guild district, or within 16 blocks of a guild alchemist) is recorded through the public furnace hook
 * {@code AlchemyFurnace.addFinishListener} (added by 38EK); the best quality of the day is the exam piece. Tier exams 1-9
 * ({@link GildaUjian}): alchemy level at least the tier, a pill brewed in the guild today of quality LOW (tier 1-2), MID
 * (3-4), HIGH (5-7) or PERFECT (8-9), and the fee. The guild shop sells the pill pavilion's pills whose band is at most the
 * tier and spirit herbs of up to {@code min(300, 40 × tier + 60)} years (the M3 cap of 300 years), 3 of each a day;
 * appraisal shares the three free looks of {@link TokoSenjata}. Clerk: {@link NpcRole#ALKEMIS}. The capital's Pill
 * Meeting (38KS) reads the same hook.
 */
public final class GildaAlkemis {

	static final String KUNCI = "gilda_alkemis_tingkat";
	private static final String PIL = "pil_gilda";
	static final Set<NpcRole> PERAN = Set.of(NpcRole.ALKEMIS);

	private record Ware(String id, Supplier<ItemStack> buat, long harga, int stok, int tingkat) {
	}

	static void init() {
		TempatLayar.daftar(LembarKunci.Gilda.LAYAR_ALKEMIS, GildaAlkemis::aksi, GildaAlkemis::bangun);
		AlchemyFurnace.addFinishListener(GildaAlkemis::onFinished);
	}

	static int tingkat(ServerPlayer player) {
		return GildaUjian.tingkat(player, KUNCI);
	}

	/** The pill quality an exam of {@code tier} asks for. */
	static PillQuality mutuUjian(int tier) {
		return tier <= 2 ? PillQuality.LOW : tier <= 4 ? PillQuality.MID : tier <= 7 ? PillQuality.HIGH : PillQuality.PERFECT;
	}

	/** True when {@code pos} is a guild cauldron: the capital's guild district, or near a guild alchemist. */
	static boolean diGilda(ServerLevel level, BlockPos pos) {
		TempatInfo info = TempatApi.tempatDi(level, pos);
		if (info != null && LembarKunci.DISTRIK_GILDA.equals(info.distrik())) {
			return true;
		}
		return !level.getEntitiesOfClass(KultivatorNpc.class, new AABB(pos).inflate(16.0D, 6.0D, 16.0D), npc -> npc.role() == NpcRole.ALKEMIS).isEmpty();
	}

	private static void onFinished(ServerLevel level, BlockPos pos, @Nullable UUID owner, ItemStack result, @Nullable PillQuality quality, boolean success,
			int alchemyLevel) {
		if (owner == null || !success || quality == null || !diGilda(level, pos)) {
			return;
		}
		MinecraftServer server = level.getServer();
		EkonomiData data = EkonomiData.get(server);
		CompoundTag tag = data.bagian(owner, PIL);
		long hari = Barang.hari(server);
		if (tag.getLongOr("hari", Long.MIN_VALUE) != hari) {
			tag.putLong("hari", hari);
			tag.putInt("mutu", -1);
		}
		if (quality.ordinal() > tag.getIntOr("mutu", -1)) {
			tag.putInt("mutu", quality.ordinal());
			data.touch();
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player != null) {
				Msg.actionBar(player, Component.translatable("kultivator.tempat_ekonomi.gilda_alkemis.tercatat",
						Component.translatable("kultivator.alchemy.quality." + quality.id())).withStyle(ChatFormatting.AQUA));
			}
		}
	}

	/** Today's best quality brewed in a guild cauldron (-1 none). */
	static int pilHariIni(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = EkonomiData.get(server).pemain(player.getUUID()).getCompoundOrEmpty(PIL);
		return tag.getLongOr("hari", Long.MIN_VALUE) == Barang.hari(server) ? tag.getIntOr("mutu", -1) : -1;
	}

	private static List<GildaUjian.Syarat> syarat(ServerPlayer player) {
		int next = tingkat(player) + 1;
		List<GildaUjian.Syarat> list = new ArrayList<>();
		int level = Alchemy.level(Cultivators.get(player));
		list.add(new GildaUjian.Syarat(Component.translatable("kultivator.tempat_ekonomi.gilda_alkemis.syarat.tingkat", next, level), level >= next));
		PillQuality wanted = mutuUjian(next);
		list.add(new GildaUjian.Syarat(Component.translatable("kultivator.tempat_ekonomi.gilda_alkemis.syarat.pil",
				Component.translatable("kultivator.alchemy.quality." + wanted.id())), pilHariIni(player) >= wanted.ordinal()));
		return list;
	}

	private static List<Ware> toko(ServerLevel level, long situs, double diskon) {
		long hari = Barang.hari(level.getServer());
		RandomSource random = RandomSource.create(situs * 0x1B873593L + hari * 977L + 3L);
		List<Ware> list = new ArrayList<>();
		double faktor = 1.0D - diskon;
		for (Harga.Ware ware : Harga.PILLS) {
			if ("herb".equals(ware.id()) || "core".equals(ware.id())) {
				continue;
			}
			ItemStack sample = ware.make().make(RandomSource.create(1L), ware.minBand());
			long value = Math.max(ware.base(), EkonomiApi.harga(sample));
			if (value <= 0L) {
				continue;
			}
			Harga.Ware w = ware;
			list.add(new Ware("pil." + ware.id(), () -> w.make().make(RandomSource.create(7L), w.minBand()), Barang.harga(value, faktor), 3, ware.minBand()));
		}
		for (int i = 0; i < 4; i++) {
			int tier = i * 2 + 1;
			int maxAge = Math.min(Harga.SHOP_HERB_MAX_AGE, 40 * tier + 60);
			int minAge = Math.max(10, maxAge / 2);
			long seed = random.nextLong();
			ItemStack sample = SpiritHerbs.randomFor(null, RandomSource.create(seed), minAge, maxAge);
			long value = EkonomiApi.harga(sample);
			if (sample.isEmpty() || value <= 0L) {
				continue;
			}
			list.add(new Ware("herba." + i, () -> SpiritHerbs.randomFor(null, RandomSource.create(seed), minAge, maxAge), Barang.harga(value, faktor), 2, tier));
		}
		return list;
	}

	private static CompoundTag terjual(MinecraftServer server, long situs) {
		EkonomiData data = EkonomiData.get(server);
		CompoundTag site = data.situs(situs);
		CompoundTag sold = site.getCompoundOrEmpty("gilda_alkemis_terjual");
		long hari = Barang.hari(server);
		if (sold.getLongOr("hari", Long.MIN_VALUE) != hari) {
			sold = new CompoundTag();
			sold.putLong("hari", hari);
			site.put("gilda_alkemis_terjual", sold);
			data.touch();
		}
		return sold;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sheet

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null) {
			return null;
		}
		ServerLevel level = (ServerLevel) player.level();
		CompoundTag tag = Lembar.kepala(player, "kultivator.tempat_ekonomi.gilda_alkemis.judul", LembarKunci.GAYA_BATU, LembarKunci.DISTRIK_GILDA, npc);
		if (!EkonomiConfig.get().toko) {
			Lembar.kunci(tag, Component.translatable("kultivator.tempat_ekonomi.tutup"), null);
			return tag;
		}
		int tier = tingkat(player);
		tag.putInt(LembarKunci.Gilda.TINGKAT, tier);
		LembarKunci.teks(tag, LembarKunci.Gilda.TINGKAT_NAMA, Component.translatable("kultivator.tempat_ekonomi.gilda_alkemis.tingkat." + tier));
		double diskon = Barang.diskon(player, npc.town(), true);
		tag.putFloat(LembarKunci.DISKON, (float) diskon);
		tag.put(LembarKunci.UJIAN, GildaUjian.lembar(player, KUNCI, syarat(player)));
		long situs = TokoSenjata.situs(npc);
		CompoundTag sold = terjual(level.getServer(), situs);
		ListTag rows = new ListTag();
		for (Ware ware : toko(level, situs, diskon)) {
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, ware.id);
			LembarKunci.barang(row, ware.buat.get(), player.registryAccess());
			row.putLong(LembarKunci.HARGA, ware.harga);
			int left = Math.max(0, ware.stok - sold.getIntOr(ware.id, 0));
			row.putInt(LembarKunci.STOK, left);
			row.putInt(LembarKunci.MUTU, -1);
			if (left <= 0) {
				row.putString(LembarKunci.KEADAAN, LembarKunci.K_HABIS);
			} else if (tier < ware.tingkat) {
				row.putString(LembarKunci.KEADAAN, LembarKunci.K_TERKUNCI);
				row.putBoolean(LembarKunci.TERKUNCI, true);
				LembarKunci.teks(row, LembarKunci.ALASAN, Component.translatable("kultivator.tempat_ekonomi.gilda_kerajinan.butuh_tingkat", ware.tingkat));
			} else {
				row.putString(LembarKunci.KEADAAN, Lembar.keadaan(player, ware.harga, false));
			}
			rows.add(row);
		}
		tag.put(LembarKunci.Gilda.TOKO, rows);
		tag.putInt(LembarKunci.Gilda.NILAI_SISA, TokoSenjata.nilaiSisa(player));
		return tag;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Actions

	private static void aksi(ServerPlayer player, int npcId, String op) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null || !EkonomiConfig.get().toko) {
			return;
		}
		String[] parts = LembarKunci.urai(op);
		switch (parts[0]) {
			case LembarKunci.Gilda.OP_UJIAN -> {
				if (GildaUjian.ujian(player, npc, KUNCI, syarat(player), "kultivator.tempat_ekonomi.gilda_alkemis.tingkat", "tempat/alkemis_gilda")) {
					SosokApi.gerak(npc, GerakId.HORMAT, 0, 30);
				}
			}
			case LembarKunci.Gilda.OP_BELI -> beli(player, npc, LembarKunci.arg(parts, 1), Math.max(1, Math.min(8, LembarKunci.argInt(parts, 2, 1))));
			case LembarKunci.Gilda.OP_NILAI -> TokoSenjata.nilai(player);
			default -> {
			}
		}
	}

	private static void beli(ServerPlayer player, KultivatorNpc npc, String id, int n) {
		ServerLevel level = (ServerLevel) player.level();
		long situs = TokoSenjata.situs(npc);
		Ware found = null;
		for (Ware ware : toko(level, situs, Barang.diskon(player, npc.town(), true))) {
			if (ware.id.equals(id)) {
				found = ware;
			}
		}
		if (found == null) {
			return;
		}
		if (tingkat(player) < found.tingkat) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.gilda_kerajinan.butuh_tingkat", found.tingkat), false);
			return;
		}
		CompoundTag sold = terjual(level.getServer(), situs);
		int count = Math.min(n, Math.max(0, found.stok - sold.getIntOr(found.id, 0)));
		if (count <= 0) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.toko.habis"), false);
			return;
		}
		long total = found.harga * count;
		if (!NilaiBatu.pay(player, total)) {
			Lembar.kurangUang(player, total);
			return;
		}
		sold.putInt(found.id, sold.getIntOr(found.id, 0) + count);
		EkonomiData.get(level.getServer()).touch();
		long hari = Barang.hari(level.getServer());
		Component name = Component.empty();
		for (int i = 0; i < count; i++) {
			ItemStack stack = found.buat.get();
			name = stack.getHoverName();
			Barang.beri(player, Barang.tandaiAsal(stack, hari));
		}
		SosokApi.gerak(npc, GerakId.MENULIS, 0, 30);
		level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.4F, 1.2F);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.dibeli", name, NilaiBatu.text(total)), true);
	}

	private GildaAlkemis() {
	}
}
