package dev.kultivator.dunia.tempat.kompleks;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.peta.PetaItems;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.SenjataKota;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.data.Cultivators;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The sect market of an NPC compound (DESIGN 38.C.3.3, prices 38.M.2.2: {@code Harga.value x 1.0}, allies
 * {@code -kompleksSekte.diskonSekutu}): the lot {@code pasar_sekte} of a neutral sect (anchor {@code kompleks/pasar}),
 * or the main hall's counter for the other alignments (anchor {@code kompleks/aula}). Wares by alignment:
 * <ul>
 *   <li>Lurus: healing, Qi recovery, toxin purging and heart calming pills, shield talismans, the spirit-iron jian;</li>
 *   <li>Netral: thunder, thousand-mile, return and limpidity talismans, a storage pouch, a pointer scroll, a world map;</li>
 *   <li>Iblis: weak and middling poisons, blood pills, binding talismans, Qi gathering pills.</li>
 * </ul>
 * A ware shows only from its realm band; each has 2-4 pieces a day (the same for every player of that day). Visitors
 * of stance NETRAL or better may buy (DINGIN pays the full price, BERMUSUHAN and PERANG are refused). The card lists the
 * day's wares with a buy chip each ({@code /kultivasi kompleks beli <n>}); buying needs the buyer within 8 blocks of the
 * counter. Stock counts are memory only (a restart refills the counter, like the town shops' daily roll).
 */
final class PasarSekte {

	/** Reach from the counter anchor for buying (blocks). */
	static final double JANGKAU = 8.0D;

	private record Barang(String id, int pitaMin, long dasar, Supplier<ItemStack> buat) {
	}

	/** One offer of a day: index, ware, made stack, price, pieces left. */
	record Tawaran(int no, String id, ItemStack barang, long harga, int sisa) {
	}

	private static final Map<String, List<Barang>> BARANG = new HashMap<>();
	/** "sekte|day|no" -> pieces sold. */
	private static final Map<String, Integer> TERJUAL = new HashMap<>();

	static {
		BARANG.put("lurus", List.of(
				ware("healing_pill", 0, 0L, () -> Harga.item("healing_pill")),
				ware("qi_recovery_pill", 0, 0L, () -> Harga.item("qi_recovery_pill")),
				ware("toxin_purging_pill", 1, 0L, () -> Harga.item("toxin_purging_pill")),
				ware("heart_calming_pill", 2, 0L, () -> Harga.item("heart_calming_pill")),
				ware("shield_talisman", 1, 0L, () -> tali("shield_talisman")),
				ware("pedang_besi_roh", SenjataKota.Mutu.ROH_RENDAH.pitaMin, 0L,
						() -> SenjataKota.stack(SenjataKota.Bentuk.JIAN, SenjataKota.Mutu.ROH_RENDAH))));
		BARANG.put("netral", List.of(
				ware("thunder_talisman", 1, 0L, () -> tali("thunder_talisman")),
				ware("thousand_mile_talisman", 1, 0L, () -> tali("thousand_mile_talisman")),
				ware("return_talisman", 1, 0L, () -> tali("return_talisman")),
				ware("limpidity_talisman", 2, 0L, () -> tali("limpidity_talisman")),
				ware("storage_pouch", 0, 0L, () -> tali("storage_pouch")),
				ware("gulungan_penunjuk", 0, 25L, () -> new ItemStack(PetaItems.GULUNGAN_PENUNJUK)),
				ware("peta_dunia", 0, 40L, () -> new ItemStack(PetaItems.PETA_DUNIA))));
		BARANG.put("iblis", List.of(
				ware("racun_lemah", 0, 8L, () -> new ItemStack(TemaItems.RACUN_LEMAH)),
				ware("racun_sedang", 2, 24L, () -> new ItemStack(TemaItems.RACUN_SEDANG)),
				ware("pil_darah", 1, 40L, () -> new ItemStack(TemaItems.PIL_DARAH)),
				ware("binding_talisman", 2, 0L, () -> tali("binding_talisman")),
				ware("qi_gathering_pill", 0, 0L, () -> Harga.item("qi_gathering_pill"))));
	}

	private static Barang ware(String id, int pitaMin, long dasar, Supplier<ItemStack> buat) {
		return new Barang(id, pitaMin, dasar, buat);
	}

	/** A talisman or pouch of the treasure pavilion table, made for band 2 (the table's own maker). */
	private static ItemStack tali(String id) {
		Harga.Ware ware = Harga.byId(Harga.TREASURES, id);
		return ware == null ? ItemStack.EMPTY : ware.make().make(RandomSource.create(id.hashCode()), 2);
	}

	static void reset() {
		TERJUAL.clear();
	}

	/** The offers of compound {@code k} for {@code player} today. */
	static List<Tawaran> tawaran(ServerLevel level, ServerPlayer player, RencanaKompleks.Kompleks k) {
		long day = Kota.day(level.getServer());
		int band = Mth.clamp(Cultivators.get(player).realm().ordinal(), 0, Harga.BANDS - 1);
		Sikap s = KompleksSikap.sikap(player, k.sekte());
		double factor = KompleksSikap.RAMAH.contains(s) ? 1.0D - KompleksConfig.get().diskonSekutu : 1.0D;
		List<Barang> list = BARANG.getOrDefault(k.aliran(), List.of());
		List<Tawaran> out = new ArrayList<>();
		RandomSource stockRoll = RandomSource.create(k.sekte().hashCode() * 31L + day * 1_000_003L);
		for (int i = 0; i < list.size(); i++) {
			Barang b = list.get(i);
			int stock = 2 + stockRoll.nextInt(3);
			if (band < b.pitaMin()) {
				continue;
			}
			ItemStack stack = b.buat().get();
			if (stack.isEmpty()) {
				continue;
			}
			long value = b.dasar() > 0L ? b.dasar() : EkonomiApi.harga(stack);
			if (value <= 0L) {
				continue;
			}
			long price = Math.max(1L, Math.round(value * factor));
			int left = Math.max(0, stock - TERJUAL.getOrDefault(kunci(k.sekte(), day, i), 0));
			out.add(new Tawaran(i, b.id(), stack, price, left));
		}
		return out;
	}

	private static String kunci(String sekte, long day, int no) {
		return sekte + "|" + day + "|" + no;
	}

	/** Why {@code player} may not buy here, or null. */
	static @Nullable Component tolak(ServerPlayer player, RencanaKompleks.Kompleks k) {
		Sikap s = KompleksSikap.sikap(player, k.sekte());
		if (s == Sikap.BERMUSUHAN || s == Sikap.PERANG) {
			return Component.translatable("kultivator.kompleks.pasar.tolak", KompleksSikap.namaSekte(k.sekte()));
		}
		return null;
	}

	/** Every second near the counter: the market card once per visit to a visitor within 6 blocks. */
	static void dekat(ServerLevel level, DuniaAnchorEntity anchor, RencanaKompleks.Kompleks k, List<ServerPlayer> within) {
		long now = level.getGameTime();
		for (ServerPlayer player : within) {
			if (player.isSpectator() || player.distanceToSqr(anchor) > 6.0D * 6.0D || tolak(player, k) != null) {
				continue;
			}
			if (KompleksSikap.tawarPasar(player, k.sekte(), now)) {
				kartu(level, player, k);
			}
		}
	}

	/** Sends the market card of {@code k} to {@code player}. */
	static void kartu(ServerLevel level, ServerPlayer player, RencanaKompleks.Kompleks k) {
		Component sect = KompleksSikap.namaSekte(k.sekte());
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.kompleks.pasar.salam." + k.aliran(), sect));
		List<Component> chips = new ArrayList<>();
		boolean ally = KompleksSikap.RAMAH.contains(KompleksSikap.sikap(player, k.sekte()));
		if (ally) {
			lines.add(Component.translatable("kultivator.kompleks.pasar.diskon", Math.round(KompleksConfig.get().diskonSekutu * 100.0D))
					.withStyle(ChatFormatting.GREEN));
		}
		for (Tawaran t : tawaran(level, player, k)) {
			Component name = t.barang().getHoverName();
			lines.add(Component.translatable("kultivator.kompleks.pasar.baris", t.no() + 1, name, t.harga(), t.sisa()));
			if (t.sisa() > 0) {
				chips.add(Kartu.chip(Component.translatable("kultivator.kompleks.pasar.beli", t.no() + 1), "/kultivasi kompleks beli " + t.no(),
						Component.translatable("kultivator.kompleks.pasar.beli.hover", name, t.harga()), ChatFormatting.GREEN));
			}
		}
		if (chips.isEmpty()) {
			lines.add(Component.translatable("kultivator.kompleks.pasar.habis"));
		}
		Kartu.kirim(player, Component.translatable("kultivator.kompleks.pasar.judul", sect), lines, chips);
	}

	/** {@code /kultivasi kompleks beli <n>}: buys one piece of offer {@code no} at the nearest counter. */
	static int beli(ServerPlayer player, int no) {
		ServerLevel level = player.level();
		RencanaKompleks.Kompleks k = konter(level, player);
		if (k == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kompleks.pasar.jauh").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Component why = tolak(player, k);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
			return 0;
		}
		Tawaran pick = null;
		for (Tawaran t : tawaran(level, player, k)) {
			if (t.no() == no) {
				pick = t;
			}
		}
		if (pick == null || pick.sisa() <= 0) {
			Msg.actionBar(player, Component.translatable("kultivator.kompleks.pasar.habis").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (!EkonomiApi.bayar(player, pick.harga(), "pasar_sekte")) {
			Msg.actionBar(player, Component.translatable("kultivator.kompleks.pasar.uang", pick.harga()).withStyle(ChatFormatting.RED));
			return 0;
		}
		long day = Kota.day(level.getServer());
		TERJUAL.merge(kunci(k.sekte(), day, no), 1, Integer::sum);
		ItemStack stack = pick.barang().copy();
		Component name = stack.getHoverName();
		player.getInventory().add(stack);
		if (!stack.isEmpty()) {
			player.spawnAtLocation(level, stack);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.1F);
		Msg.chat(player, Component.translatable("kultivator.kompleks.pasar.dibeli", name, pick.harga(), KompleksSikap.namaSekte(k.sekte()))
				.withStyle(ChatFormatting.GOLD));
		return 1;
	}

	/** {@code /kultivasi kompleks pasar}: the card again at the nearest counter. */
	static int lihat(ServerPlayer player) {
		RencanaKompleks.Kompleks k = konter(player.level(), player);
		if (k == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kompleks.pasar.jauh").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Component why = tolak(player, k);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
			return 0;
		}
		kartu(player.level(), player, k);
		return 1;
	}

	/** The compound of the counter (a {@code kompleks/pasar} anchor, else the main hall's) within reach, or null. */
	private static RencanaKompleks.@Nullable Kompleks konter(ServerLevel level, ServerPlayer player) {
		DuniaAnchorEntity a = DuniaAnchors.nearest(level, player.position(), "kompleks/pasar", JANGKAU);
		if (a == null) {
			a = DuniaAnchors.nearest(level, player.position(), "kompleks/aula", JANGKAU + 4.0D);
			// sects without a market lot (and a neutral compound whose lot is out of reach) sell at the hall counter
			return a == null ? null : Kompleks.dari(level, a);
		}
		return Kompleks.dari(level, a);
	}

	private PasarSekte() {
	}
}
