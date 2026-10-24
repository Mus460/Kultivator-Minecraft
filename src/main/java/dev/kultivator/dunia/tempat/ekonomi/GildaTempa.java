package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.tempa.Forging;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Gilda Pandai Pusaka, the capital's Forge Guild counter (DESIGN 38.B.3 {@code gilda_tempa_a/b}, 38.M.2.1, screen
 * {@code gilda_tempa} of 38.U.3.9). Tier exams 1-9 ({@link GildaUjian}): forging skill at least the tier, a forged artifact
 * in hand of grade {@code (tier − 1) / 2} or more, the fee {@code 5 × n²}. The guild shop sells today's rack of forged
 * artifacts (every form, grades 0-4, one each, grade g needs tier {@code 2g}) at {@code Harga.ARTIFACT[g] × 1.2} and the
 * forge's metals (needed by the forge orders of {@link PesananTempa}) at fixed prices, gated by tier; the order list and the
 * order pickup are the same as Empu Ou Ye's tab; appraisal shares the three free looks a day of {@link TokoSenjata}.
 * Clerks: {@link NpcRole#EMPU} and {@link NpcRole#PANDAI_PUSAKA} (in the guild's district).
 */
public final class GildaTempa {

	static final String KUNCI = "gilda_tempa_tingkat";
	static final Set<NpcRole> PERAN = Set.of(NpcRole.EMPU, NpcRole.PANDAI_PUSAKA);

	/** A ware of the guild shop: id, maker, price, stock, tier needed, grade shown (-1 none). */
	private record Ware(String id, Supplier<ItemStack> buat, long harga, int stok, int tingkat, int mutu) {
	}

	/** The forge metals: item, NB, tier. */
	private static final Object[][] LOGAM = {
			{"iron_ingot", 2L, 0}, {"gold_ingot", 4L, 1}, {"diamond", 18L, 2}, {"amethyst_block", 10L, 3}, {"netherite_ingot", 120L, 5},
			{"echo_shard", 60L, 6}, {"nether_star", 900L, 8}};

	static void init() {
		TempatLayar.daftar(LembarKunci.Gilda.LAYAR_TEMPA, GildaTempa::aksi, GildaTempa::bangun);
	}

	static int tingkat(ServerPlayer player) {
		return GildaUjian.tingkat(player, KUNCI);
	}

	private static List<GildaUjian.Syarat> syarat(ServerPlayer player) {
		int next = tingkat(player) + 1;
		List<GildaUjian.Syarat> list = new ArrayList<>();
		int skill = Forging.skillLevel(player);
		list.add(new GildaUjian.Syarat(Component.translatable("kultivator.tempat_ekonomi.gilda_tempa.syarat.keahlian", next, skill), skill >= next));
		int grade = Math.max(0, (next - 1) / 2);
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean shown = ArtifactItem.is(hand) && ArtifactItem.grade(ArtifactItem.data(hand)) >= grade;
		list.add(new GildaUjian.Syarat(Component.translatable("kultivator.tempat_ekonomi.gilda_tempa.syarat.pusaka", grade), shown));
		return list;
	}

	private static List<Ware> toko(ServerLevel level, long situs, double diskon) {
		long hari = Barang.hari(level.getServer());
		RandomSource random = RandomSource.create(situs * 0x632BE59BD9B4E019L + hari * 31L + 7L);
		List<Ware> list = new ArrayList<>();
		double faktor = 1.0D - diskon;
		for (String form : ArtifactItem.FORMS) {
			for (int g = 0; g <= Forging.ORDER_MAX_GRADE; g++) {
				long seed = random.nextLong();
				int grade = g;
				list.add(new Ware("p." + form + "." + g, () -> Forging.buatStok(level, form, grade, RandomSource.create(seed), "Gilda Pandai Pusaka"),
						Barang.harga(Math.round(Harga.ARTIFACT[g] * TokoSenjata.HARGA_PUSAKA), faktor), 1, g * 2, g));
			}
		}
		for (Object[] metal : LOGAM) {
			String id = (String) metal[0];
			Item item = switch (id) {
				case "gold_ingot" -> Items.GOLD_INGOT;
				case "diamond" -> Items.DIAMOND;
				case "amethyst_block" -> Items.AMETHYST_BLOCK;
				case "netherite_ingot" -> Items.NETHERITE_INGOT;
				case "echo_shard" -> Items.ECHO_SHARD;
				case "nether_star" -> Items.NETHER_STAR;
				default -> Items.IRON_INGOT;
			};
			int tier = (int) metal[2];
			list.add(new Ware("m." + id, () -> new ItemStack(item), Barang.harga((long) metal[1], faktor), tier >= 6 ? 1 : 16, tier, -1));
		}
		return list;
	}

	private static CompoundTag terjual(MinecraftServer server, long situs, String player) {
		EkonomiData data = EkonomiData.get(server);
		CompoundTag site = data.situs(situs);
		CompoundTag sold = site.getCompoundOrEmpty("gilda_tempa_terjual");
		long hari = Barang.hari(server);
		if (sold.getLongOr("hari", Long.MIN_VALUE) != hari) {
			sold = new CompoundTag();
			sold.putLong("hari", hari);
			site.put("gilda_tempa_terjual", sold);
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
		CompoundTag tag = Lembar.kepala(player, "kultivator.tempat_ekonomi.gilda_tempa.judul", LembarKunci.GAYA_BATU, LembarKunci.DISTRIK_GILDA, npc);
		if (!EkonomiConfig.get().toko) {
			Lembar.kunci(tag, Component.translatable("kultivator.tempat_ekonomi.tutup"), null);
			return tag;
		}
		int tier = tingkat(player);
		tag.putInt(LembarKunci.Gilda.TINGKAT, tier);
		LembarKunci.teks(tag, LembarKunci.Gilda.TINGKAT_NAMA, Component.translatable("kultivator.tempat_ekonomi.gilda_tempa.tingkat." + tier));
		double diskon = Barang.diskon(player, npc.town(), true);
		tag.putFloat(LembarKunci.DISKON, (float) diskon);
		tag.put(LembarKunci.UJIAN, GildaUjian.lembar(player, KUNCI, syarat(player)));
		long situs = TokoSenjata.situs(npc);
		CompoundTag sold = terjual(level.getServer(), situs, player.getStringUUID());
		ListTag rows = new ListTag();
		for (Ware ware : toko(level, situs, diskon)) {
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, ware.id);
			LembarKunci.barang(row, ware.buat.get(), player.registryAccess());
			row.putLong(LembarKunci.HARGA, ware.harga);
			int left = Math.max(0, ware.stok - sold.getIntOr(ware.id, 0));
			row.putInt(LembarKunci.STOK, left);
			row.putInt(LembarKunci.MUTU, ware.mutu);
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
		tag.put(LembarKunci.Gilda.PESANAN, PesananTempa.daftarLembar(player));
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
				if (GildaUjian.ujian(player, npc, KUNCI, syarat(player), "kultivator.tempat_ekonomi.gilda_tempa.tingkat", "tempat/empu_gilda")) {
					SosokApi.gerak(npc, GerakId.HORMAT, 0, 30);
				}
			}
			case LembarKunci.Gilda.OP_BELI -> beli(player, npc, LembarKunci.arg(parts, 1), Math.max(1, Math.min(16, LembarKunci.argInt(parts, 2, 1))));
			case LembarKunci.Gilda.OP_AMBIL -> PesananTempa.aksi(player, npc, new String[] {LembarKunci.TokoSenjata.OP_AMBIL, LembarKunci.arg(parts, 1)});
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
		CompoundTag sold = terjual(level.getServer(), situs, player.getStringUUID());
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
		ItemStack first = found.buat.get();
		Component name = first.getHoverName();
		if (first.getMaxStackSize() > 1) {
			first.setCount(count);
			Barang.beri(player, Barang.tandaiAsal(first, hari));
		} else {
			Barang.beri(player, Barang.tandaiAsal(first, hari));
			for (int i = 1; i < count; i++) {
				Barang.beri(player, Barang.tandaiAsal(found.buat.get(), hari));
			}
		}
		SosokApi.gerak(npc, GerakId.PALU_LANDASAN, 0, 40, PropId.PALU, PropId.TIDAK_ADA, PropId.TIDAK_ADA);
		level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.4F, 1.3F);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.dibeli", name, NilaiBatu.text(total)), true);
	}

	private GildaTempa() {
	}
}
