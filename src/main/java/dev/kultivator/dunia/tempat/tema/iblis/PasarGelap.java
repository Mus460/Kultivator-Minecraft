package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Element;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.reruntuhan.RItems;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.SenjataKota;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Pasar Seribu Bayangan (DESIGN 38.T.7.6, 38.T.10.2): the {@code tema_pasar_gelap} screen of the brokers (anchors
 * {@code kota_tema/pasar_gelap} x2, {@code makelar}, Nyonya Bai She) and the use behaviour of the goods it sells.
 * <ul>
 * <li>Tabs: racun (weapon coatings 12 / 30 / 80 NB, band 2/3/4), pil (Pil Darah 25 NB, 3 a day), binatang (demon-beast
 * cores and the rare ancient demon core), peta (Peta Sarang 35 NB, 1 a day), topeng (Topeng Tanpa Wajah 70 NB), curian
 * (four stolen city weapons a day at 0.85 x, marked {@code asal_gelap}).</li>
 * <li>Markup 1.3; Gentar &gt;= 30: 1.2; Infami &gt;= 30: 1.15; title "Tamu Darah": 1.1 (the best applies); Bai She's grudge
 * (chapter 3 sabotage) and the Lurus ending add x1.2. Shut while the siege aftermath closes the market; intruders and
 * Pact breakers are not served.</li>
 * <li>Uses: Pil Darah heals 30 % HP at once with Weakness I 20 s; a coating poisons the next 3 hits (Poison I 4 s, II/III
 * for the stronger ones); Peta Sarang locates the nearest demon lair; the mask halves Jam Berburu challengers while worn
 * in a Kota Iblis ({@link JamBerburu}).</li>
 * </ul>
 */
public final class PasarGelap {

	public static final String GELAP_KEY = "kultivator_asal_gelap";
	public static final int CURIAN_HARIAN = 4;
	public static final int PIL_HARIAN = 3;
	public static final int PETA_HARIAN = 1;
	public static final int RACUN_HIT = 3;
	public static final TagKey<Structure> SARANG = TagKey.create(Registries.STRUCTURE, Kultivator.id("gua/sarang_iblis"));

	/** One shop row. */
	private record Barang2(String id, String tab, Supplier<ItemStack> stack, long harga, int pitaMin, int batasHarian, String hitung) {
	}

	private static final List<Barang2> BARANG = List.of(
			new Barang2("racun_lemah", TemaLayar.PasarGelap.TAB_RACUN, () -> new ItemStack(TemaItems.RACUN_LEMAH), 12L, 2, 0, ""),
			new Barang2("racun_sedang", TemaLayar.PasarGelap.TAB_RACUN, () -> new ItemStack(TemaItems.RACUN_SEDANG), 30L, 3, 0, ""),
			new Barang2("racun_kuat", TemaLayar.PasarGelap.TAB_RACUN, () -> new ItemStack(TemaItems.RACUN_KUAT), 80L, 4, 0, ""),
			new Barang2("pil_darah", TemaLayar.PasarGelap.TAB_PIL, () -> new ItemStack(TemaItems.PIL_DARAH), 25L, 0, PIL_HARIAN, "pil"),
			new Barang2("inti_rendah", TemaLayar.PasarGelap.TAB_BINATANG, () -> BeastCores.create(Element.FIRE, 1), 45L, 1, 0, ""),
			new Barang2("inti_menengah", TemaLayar.PasarGelap.TAB_BINATANG, () -> BeastCores.create(Element.WIND, 2), 140L, 3, 0, ""),
			new Barang2("inti_iblis_kuno", TemaLayar.PasarGelap.TAB_BINATANG, () -> new ItemStack(RItems.INTI_IBLIS_KUNO), 480L, 4, 1, "inti_kuno"),
			new Barang2("peta_sarang", TemaLayar.PasarGelap.TAB_PETA, () -> new ItemStack(TemaItems.PETA_SARANG), 35L, 0, PETA_HARIAN, "peta"),
			new Barang2("topeng_tanpa_wajah", TemaLayar.PasarGelap.TAB_TOPENG, () -> new ItemStack(TemaItems.TOPENG_TANPA_WAJAH), 70L, 0, 0, ""));

	private PasarGelap() {
	}

	static void init() {
		TemaLayar.daftar(TemaLayar.PasarGelap.LAYAR, PasarGelap::bangun, PasarGelap::aksi);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer p)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			Item item = stack.getItem();
			if (item == TemaItems.PIL_DARAH) {
				return pil(p, stack);
			}
			if (item == TemaItems.RACUN_LEMAH || item == TemaItems.RACUN_SEDANG || item == TemaItems.RACUN_KUAT) {
				return racun(p, stack, item == TemaItems.RACUN_LEMAH ? 0 : item == TemaItems.RACUN_SEDANG ? 1 : 2);
			}
			if (item == TemaItems.PETA_SARANG) {
				return peta(p, stack);
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && source.getEntity() instanceof ServerPlayer p && source.getDirectEntity() == p && entity != p) {
				lapisan(p, entity);
			}
		});
	}

	// ================================================================== prices

	/** The markup for this player in this town (38.T.7.6). */
	public static double markup(ServerPlayer p, TemaKota.Kota kota) {
		double m = 1.3D;
		if (IblisMeter.gentar(p, kota.situsId()) >= IblisMeter.GENTAR_SEDIKIT) {
			m = Math.min(m, 1.2D);
		}
		if (IblisMeter.infami(p) >= 30) {
			m = Math.min(m, 1.15D);
		}
		if (IblisData.gelar(p, IblisData.GELAR_TAMU)) {
			m = Math.min(m, 1.1D);
		}
		MinecraftServer server = p.level().getServer();
		if (IblisData.pemainKota(server, kota.situsId(), p.getUUID()).getBooleanOr("bai_she_musuh", false)) {
			m *= 1.2D;
		}
		if (IblisData.kota(server, kota.situsId()).getBooleanOr("pasar_naik", false)) {
			m *= 1.2D;
		}
		return m;
	}

	/** Why the market does not serve the player now, or null. */
	static @Nullable Component tolak(ServerPlayer p, TemaKota.Kota kota) {
		if (IblisData.kota(p.level().getServer(), kota.situsId()).getLongOr("pasar_tutup_sampai", -1L) >= IblisKota.hari(p.level())) {
			return Component.translatable("kultivator.kota_tema.iblis.pasar.tutup");
		}
		if (Gerbang.diburu(p, kota.situsId())) {
			return Component.translatable("kultivator.kota_tema.iblis.pasar.penyusup");
		}
		return null;
	}

	/** Today's stolen weapons of this town (a pure function of town and day). */
	static List<ItemStack> curian(long situs, long hari) {
		List<ItemStack> out = new ArrayList<>();
		SenjataKota.Bentuk[] shapes = SenjataKota.Bentuk.values();
		SenjataKota.Mutu[] grades = SenjataKota.Mutu.values();
		for (int i = 0; i < CURIAN_HARIAN; i++) {
			long h = (situs * 0x9E3779B97F4A7C15L) ^ (hari * 0xBF58476D1CE4E5B9L) ^ (i * 0x94D049BB133111EBL);
			SenjataKota.Bentuk b = shapes[(int) Math.floorMod(h >>> 7, (long) shapes.length)];
			SenjataKota.Mutu m = grades[(int) Math.floorMod(h >>> 19, 3L)];
			out.add(tandaiGelap(SenjataKota.stack(b, m)));
		}
		return out;
	}

	/** Marks a stack as of dark origin (honest shops refuse it; 38EK reads the custom data key {@value #GELAP_KEY}). */
	public static ItemStack tandaiGelap(ItemStack stack) {
		CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		data.putBoolean(GELAP_KEY, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("kultivator.kota_tema.iblis.pasar.curian_lore").withStyle(ChatFormatting.DARK_GRAY))));
		return stack;
	}

	public static boolean gelap(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(GELAP_KEY, false);
	}

	// ================================================================== the screen

	private static @Nullable CompoundTag bangun(ServerPlayer p, int npcId) {
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null) {
			return null;
		}
		KultivatorNpc npc = IblisAnchors.npc(p, npcId);
		CompoundTag tag = IblisLayar.kepala(p, "kultivator.kota_tema.iblis.layar.pasar", LembarKunci.GAYA_KAIN, npc);
		double markup = markup(p, kota);
		tag.putFloat(TemaLayar.PasarGelap.MARKUP, (float) markup);
		tag.putInt(TemaLayar.PasarGelap.GENTAR, IblisMeter.gentar(p, kota.situsId()));
		tag.putInt(TemaLayar.PasarGelap.INFAMI, IblisMeter.infami(p));
		Component no = tolak(p, kota);
		if (no != null) {
			tag.putBoolean(TemaLayar.PasarGelap.PENYUSUP, Gerbang.diburu(p, kota.situsId()));
			IblisLayar.kunci(tag, no, null);
			return tag;
		}
		var registries = p.level().registryAccess();
		MinecraftServer server = p.level().getServer();
		long hari = IblisKota.hari(p.level());
		int band = IblisKota.band(p);
		ListTag rows = new ListTag();
		for (Barang2 b : BARANG) {
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, b.id());
			row.putString(LembarKunci.TAB, b.tab());
			ItemStack stack = b.stack().get();
			LembarKunci.teks(row, LembarKunci.NAMA, stack.getHoverName());
			LembarKunci.barang(row, stack, registries);
			row.putLong(LembarKunci.HARGA, Barang.harga(b.harga(), markup));
			int left = b.batasHarian() <= 0 ? -1 : Math.max(0, b.batasHarian() - IblisData.hariIni(server, kota.situsId(), p.getUUID(), b.hitung(), hari));
			row.putInt(LembarKunci.STOK, left);
			row.putInt(LembarKunci.PITA, b.pitaMin());
			row.putString(LembarKunci.KEADAAN, band < b.pitaMin() ? LembarKunci.K_TERKUNCI : left == 0 ? LembarKunci.K_HABIS : LembarKunci.K_OK);
			row.putBoolean(TemaLayar.PasarGelap.GELAP, false);
			rows.add(row);
		}
		List<ItemStack> stolen = curian(kota.situsId(), hari);
		CompoundTag town = IblisData.kota(server, kota.situsId());
		for (int i = 0; i < stolen.size(); i++) {
			ItemStack stack = stolen.get(i);
			boolean sold = town.getLongOr("curian_hari_" + i, -1L) == hari;
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, "curian_" + i);
			row.putString(LembarKunci.TAB, TemaLayar.PasarGelap.TAB_CURIAN);
			LembarKunci.teks(row, LembarKunci.NAMA, stack.getHoverName());
			LembarKunci.barang(row, stack, registries);
			row.putLong(LembarKunci.HARGA, hargaCurian(stack, markup));
			row.putInt(LembarKunci.STOK, sold ? 0 : 1);
			SenjataKota.Jenis jenis = SenjataKota.dari(stack);
			int min = jenis == null ? 0 : jenis.mutu().pitaMin;
			row.putInt(LembarKunci.PITA, min);
			row.putString(LembarKunci.KEADAAN, sold ? LembarKunci.K_HABIS : band < min ? LembarKunci.K_TERKUNCI : LembarKunci.K_OK);
			row.putBoolean(TemaLayar.PasarGelap.GELAP, true);
			rows.add(row);
		}
		tag.put(TemaLayar.PasarGelap.BARANG_DAFTAR, rows);
		return tag;
	}

	private static long hargaCurian(ItemStack stack, double markup) {
		return Barang.harga(Math.round(SenjataKota.harga(stack) * 0.85D), markup);
	}

	private static void aksi(ServerPlayer p, int npcId, String op) {
		TemaKota.Kota kota = IblisKota.di(p);
		String[] parts = LembarKunci.urai(op);
		if (kota == null || !TemaLayar.PasarGelap.OP_BELI.equals(parts[0]) || parts.length < 2) {
			return;
		}
		Component no = tolak(p, kota);
		if (no != null) {
			IblisLayar.pesan(p, no, false);
			return;
		}
		int n = (int) Math.max(1L, Math.min(16L, IblisLayar.angka(parts, 2, 1L)));
		String id = parts[1];
		MinecraftServer server = p.level().getServer();
		long hari = IblisKota.hari(p.level());
		double markup = markup(p, kota);
		if (id.startsWith("curian_")) {
			int i = (int) IblisLayar.angka(new String[] {id.substring(7)}, 0, -1L);
			List<ItemStack> stolen = curian(kota.situsId(), hari);
			if (i < 0 || i >= stolen.size()) {
				return;
			}
			CompoundTag town = IblisData.kota(server, kota.situsId());
			if (town.getLongOr("curian_hari_" + i, -1L) == hari) {
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.pasar.habis"), false);
				return;
			}
			ItemStack stack = stolen.get(i);
			SenjataKota.Jenis jenis = SenjataKota.dari(stack);
			if (jenis != null && IblisKota.band(p) < jenis.mutu().pitaMin) {
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.pasar.pita", jenis.mutu().pitaMin), false);
				return;
			}
			long price = hargaCurian(stack, markup);
			if (!NilaiBatu.pay(p, price)) {
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.uang_kurang", NilaiBatu.text(price)), false);
				return;
			}
			town.putLong("curian_hari_" + i, hari);
			IblisData.ubah(server);
			Barang.beri(p, Barang.tandaiAsal(stack, hari));
			IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.pasar.beli", stack.getHoverName(), NilaiBatu.text(price)), true);
			return;
		}
		for (Barang2 b : BARANG) {
			if (!b.id().equals(id)) {
				continue;
			}
			if (IblisKota.band(p) < b.pitaMin()) {
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.pasar.pita", b.pitaMin()), false);
				return;
			}
			if (b.batasHarian() > 0) {
				int left = b.batasHarian() - IblisData.hariIni(server, kota.situsId(), p.getUUID(), b.hitung(), hari);
				n = Math.min(n, left);
				if (n <= 0) {
					IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.pasar.batas", b.batasHarian()), false);
					return;
				}
			}
			long price = Barang.harga(b.harga(), markup) * n;
			if (!NilaiBatu.pay(p, price)) {
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.uang_kurang", NilaiBatu.text(price)), false);
				return;
			}
			IblisData.tambahKas(server, kota.situsId(), price / 4L);
			ItemStack stack = b.stack().get();
			for (int i = 0; i < n; i++) {
				Barang.beri(p, Barang.tandaiAsal(stack.copy(), hari));
				if (b.batasHarian() > 0) {
					IblisData.hitungHariIni(server, kota.situsId(), p.getUUID(), b.hitung(), hari);
				}
			}
			IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.pasar.beli", stack.getHoverName(), NilaiBatu.text(price)), true);
			return;
		}
	}

	// ================================================================== item uses

	private static InteractionResult pil(ServerPlayer p, ItemStack stack) {
		if (p.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		p.heal(p.getMaxHealth() * 0.30F);
		p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 20, 0));
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
		p.getCooldowns().addCooldown(stack, 100);
		if (!p.isCreative()) {
			stack.shrink(1);
		}
		Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.pil.minum").withStyle(ChatFormatting.DARK_RED));
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult racun(ServerPlayer p, ItemStack stack, int kuat) {
		ItemStack weapon = p.getItemBySlot(EquipmentSlot.MAINHAND) == stack ? p.getItemBySlot(EquipmentSlot.OFFHAND) : p.getItemBySlot(EquipmentSlot.MAINHAND);
		if (weapon.isEmpty() || !(SenjataKota.dari(weapon) != null || weapon.has(DataComponents.WEAPON) || weapon.has(DataComponents.TOOL))) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.racun.tanpa_senjata").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		CompoundTag mine = IblisData.pemain(p.level().getServer(), p.getUUID());
		mine.putInt("racun_kuat", kuat);
		mine.putInt("racun_sisa", RACUN_HIT);
		IblisData.ubah(p.level().getServer());
		if (!p.isCreative()) {
			stack.shrink(1);
		}
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.7F, 0.5F);
		Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.racun.oles", RACUN_HIT).withStyle(ChatFormatting.DARK_GREEN));
		return InteractionResult.SUCCESS;
	}

	/** A coated weapon's hit: Poison I/II/III for 4 s on the next 3 hits. */
	private static void lapisan(ServerPlayer p, LivingEntity victim) {
		CompoundTag mine = IblisData.pemain(p.level().getServer(), p.getUUID());
		int left = mine.getIntOr("racun_sisa", 0);
		if (left <= 0) {
			return;
		}
		victim.addEffect(new MobEffectInstance(MobEffects.POISON, 4 * 20, Math.max(0, Math.min(2, mine.getIntOr("racun_kuat", 0)))), p);
		mine.putInt("racun_sisa", left - 1);
		IblisData.ubah(p.level().getServer());
	}

	private static InteractionResult peta(ServerPlayer p, ItemStack stack) {
		ServerLevel level = p.level();
		DuniaSites.Site site = DuniaSites.locate(level, SARANG, p.blockPosition(), 48);
		if (site == null) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.peta.tidak").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		DuniaSites.discover(p, site.id());
		BlockPos at = site.pos();
		int jarak = (int) Math.sqrt(at.distSqr(p.blockPosition()));
		p.sendSystemMessage(Component.translatable("kultivator.kota_tema.iblis.peta.arah", jarak, at.getX(), at.getZ()).withStyle(ChatFormatting.DARK_RED));
		if (!p.isCreative()) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
