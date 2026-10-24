package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.cultivation.Deviation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KedaiTeh;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.technique.source.JadeSlips;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The paid services of the screen {@code jasa} (DESIGN 38.M.2.2, 38.B.4.4, 38.C.6.3, 38.U.3.9), one sheet per kind of
 * clerk:
 * <ul>
 * <li><b>Tabib</b> ({@link NpcRole#TABIB}): cures a Qi Deviation for {@code 20 × (band + 1)} NB (a village healer
 * {@code 6 × (band + 1)}, band 3 at most), and heals a wounded following disciple for {@code 15 × (band + 1)} NB through
 * the public wound record of DESIGN 39 ({@code MandiriApi}, the wound's end set to now).</li>
 * <li><b>Kuil</b> ({@link NpcRole#RAHIB}): incense for 3 NB at most once a game hour, "Restu Kuil" for 60 minutes (+5 % on
 * the item rolls of the 38 rewards, and once a world day the heart demon pressure −5); at a road shrine (a site of kind
 * {@code pos/*}) 1 NB for "Restu Jalan", Speed I for 3 minutes, at most once in 10 minutes.</li>
 * <li><b>Perpustakaan</b> ({@link NpcRole#PUSTAKAWAN}): reading the jade slip in hand ({@code 10 × grade}, the technique's
 * description and lore), copying it ({@code 0.6 × value + 10}, one copy a day, the copy marked as a shop ware so it cannot
 * be sold back at a gain), and the news archive: the nearest undiscovered place of a 38 kind within 4000 blocks for
 * {@code 1.5 ×} the tea house rumour price.</li>
 * </ul>
 */
public final class Jasa {

	static final Set<NpcRole> PERAN = Set.of(NpcRole.TABIB, NpcRole.RAHIB, NpcRole.PUSTAKAWAN);
	public static final long RESTU_KUIL_TICK = 72000L;
	public static final double RESTU_KUIL_BONUS = 0.05D;
	public static final long DUPA_JEDA = 1000L;
	public static final long RESTU_JALAN_JEDA = 12000L;
	/** Archive kinds: site kind prefix and rumour tier of {@code KedaiTeh.RUMOUR_PRICE}. */
	private static final String[][] ARSIP = {{"kampung/", "0"}, {"pos/", "0"}, {"gua/", "1"}, {"kompleks_sekte/", "1"}, {"situs/", "2"}};

	static void init() {
		TempatLayar.daftar(LembarKunci.Layanan.LAYAR_JASA, Jasa::aksi, Jasa::bangun);
	}

	private static String jenis(KultivatorNpc npc) {
		return switch (npc.role()) {
			case TABIB -> LembarKunci.Layanan.JENIS_TABIB;
			case RAHIB -> LembarKunci.Layanan.JENIS_KUIL;
			default -> LembarKunci.Layanan.JENIS_PERPUSTAKAAN;
		};
	}

	private static String jenisSitus(ServerPlayer player, KultivatorNpc npc) {
		DuniaSites.Site site = DuniaSites.byId(player.level().getServer(), npc.anchorSite() != 0L ? npc.anchorSite() : npc.town());
		return site == null ? "" : site.kind();
	}

	/** True while the player's "Restu Kuil" lasts (read by the 38 reward rolls). */
	public static boolean restuKuil(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		return EkonomiData.get(server).pemain(player.getUUID()).getLongOr("restu_kuil", 0L) > Barang.waktu(server);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sheet

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null) {
			return null;
		}
		String jenis = jenis(npc);
		CompoundTag tag = Lembar.kepala(player, "kultivator.tempat_ekonomi.jasa.judul." + jenis, LembarKunci.GAYA_GIOK, null, npc);
		if (!EkonomiConfig.get().toko) {
			Lembar.kunci(tag, Component.translatable("kultivator.tempat_ekonomi.tutup"), null);
			return tag;
		}
		tag.putString(LembarKunci.JENIS, jenis);
		ListTag menu = switch (jenis) {
			case LembarKunci.Layanan.JENIS_TABIB -> menuTabib(player, npc);
			case LembarKunci.Layanan.JENIS_KUIL -> menuKuil(player, npc);
			default -> menuPustaka(player);
		};
		tag.put(LembarKunci.Layanan.MENU, menu);
		tag.put(LembarKunci.AKTIF, aktif(player));
		if (menu.isEmpty()) {
			Lembar.kosong(tag, "kultivator.tempat_ekonomi.jasa.kosong." + jenis);
		}
		return tag;
	}

	private static CompoundTag baris(String id, Component nama, int ikon, long harga, Component efek, String keadaan, @Nullable Component alasan) {
		CompoundTag row = new CompoundTag();
		row.putString(LembarKunci.ID, id);
		LembarKunci.teks(row, LembarKunci.NAMA, nama);
		row.putInt(LembarKunci.IKON, ikon);
		row.putLong(LembarKunci.HARGA, harga);
		LembarKunci.teks(row, LembarKunci.Layanan.EFEK, efek);
		row.putString(LembarKunci.KEADAAN, keadaan);
		if (alasan != null) {
			LembarKunci.teks(row, LembarKunci.ALASAN, alasan);
		}
		return row;
	}

	private static long hargaTabib(ServerPlayer player, KultivatorNpc npc) {
		int band = Barang.pita(player);
		boolean village = jenisSitus(player, npc).startsWith("kampung") && band <= 3;
		return Barang.harga((village ? 6L : 20L) * (band + 1), 1.0D);
	}

	private static List<DiscipleEntity> terluka(ServerPlayer player) {
		List<DiscipleEntity> out = new ArrayList<>();
		long now = Barang.waktu(player.level().getServer());
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			if (disciple.level() == player.level() && disciple.distanceToSqr(player) <= 16.0D * 16.0D
					&& MandiriApi.luka(disciple.data(), now) != JenisLuka.TIDAK) {
				out.add(disciple);
			}
		}
		return out;
	}

	private static ListTag menuTabib(ServerPlayer player, KultivatorNpc npc) {
		ListTag menu = new ListTag();
		long price = hargaTabib(player, npc);
		boolean deviated = Deviation.ticksLeft(player) > 0;
		menu.add(baris("penyimpangan", Component.translatable("kultivator.tempat_ekonomi.jasa.tabib.penyimpangan"), LembarKunci.Ikon.TABIB, price,
				Component.translatable("kultivator.tempat_ekonomi.jasa.tabib.penyimpangan.efek"), deviated ? Lembar.keadaan(player, price, false)
						: LembarKunci.K_TERKUNCI, deviated ? null : Component.translatable("kultivator.tempat_ekonomi.jasa.tabib.sehat")));
		long disciplePrice = Barang.harga(15L * (Barang.pita(player) + 1), 1.0D);
		for (DiscipleEntity disciple : terluka(player)) {
			menu.add(baris("murid." + disciple.soulId(), Component.translatable("kultivator.tempat_ekonomi.jasa.tabib.murid", disciple.getDisplayName()),
					LembarKunci.Ikon.TABIB, disciplePrice, Component.translatable("kultivator.tempat_ekonomi.jasa.tabib.murid.efek"),
					Lembar.keadaan(player, disciplePrice, false), null));
		}
		return menu;
	}

	private static boolean kuilJalan(ServerPlayer player, KultivatorNpc npc) {
		return jenisSitus(player, npc).startsWith("pos/");
	}

	private static ListTag menuKuil(ServerPlayer player, KultivatorNpc npc) {
		ListTag menu = new ListTag();
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = EkonomiData.get(server).pemain(player.getUUID());
		long now = Barang.waktu(server);
		if (kuilJalan(player, npc)) {
			long price = Barang.harga(1L, 1.0D);
			long wait = tag.getLongOr("dupa_jalan", Long.MIN_VALUE / 2) + RESTU_JALAN_JEDA - now;
			menu.add(baris("dupa_jalan", Component.translatable("kultivator.tempat_ekonomi.jasa.kuil.dupa_jalan"), LembarKunci.Ikon.KUIL, price,
					Component.translatable("kultivator.tempat_ekonomi.jasa.kuil.dupa_jalan.efek"), wait > 0 ? LembarKunci.K_BATAS
							: Lembar.keadaan(player, price, false), wait > 0 ? Component.translatable("kultivator.tempat_ekonomi.jasa.kuil.tunggu",
									(wait + 1199L) / 1200L) : null));
		} else {
			long price = Barang.harga(3L, 1.0D);
			long wait = tag.getLongOr("dupa_kuil", Long.MIN_VALUE / 2) + DUPA_JEDA - now;
			menu.add(baris("dupa", Component.translatable("kultivator.tempat_ekonomi.jasa.kuil.dupa"), LembarKunci.Ikon.KUIL, price,
					Component.translatable("kultivator.tempat_ekonomi.jasa.kuil.dupa.efek"), wait > 0 ? LembarKunci.K_BATAS
							: Lembar.keadaan(player, price, false), wait > 0 ? Component.translatable("kultivator.tempat_ekonomi.jasa.kuil.tunggu",
									(wait + 1199L) / 1200L) : null));
		}
		return menu;
	}

	private static long hargaBaca(TechniqueSpec spec) {
		return Barang.harga(10L * (spec.grade().ordinal() + 1), 1.0D);
	}

	private static long hargaSalin(ItemStack slip) {
		return Barang.harga(Math.round(0.6D * Math.max(1L, EkonomiApi.harga(slip)) + 10.0D), 1.0D);
	}

	private static ListTag menuPustaka(ServerPlayer player) {
		ListTag menu = new ListTag();
		MinecraftServer server = player.level().getServer();
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		TechniqueSpec spec = JadeSlips.specOfStack(hand);
		if (spec != null) {
			long read = hargaBaca(spec);
			menu.add(baris("baca", Component.translatable("kultivator.tempat_ekonomi.jasa.pustaka.baca", spec.displayName()), LembarKunci.Ikon.PERPUSTAKAAN,
					read, Component.translatable("kultivator.tempat_ekonomi.jasa.pustaka.baca.efek"), Lembar.keadaan(player, read, false), null));
			long copy = hargaSalin(hand);
			boolean copied = EkonomiData.get(server).harian(player.getUUID(), Barang.hari(server), "salin") > 0;
			boolean allowed = !JadeSlips.stack(spec.id()).isEmpty();
			menu.add(baris("salin", Component.translatable("kultivator.tempat_ekonomi.jasa.pustaka.salin", spec.displayName()), LembarKunci.Ikon.PERPUSTAKAAN,
					copy, Component.translatable("kultivator.tempat_ekonomi.jasa.pustaka.salin.efek"), copied || !allowed ? LembarKunci.K_BATAS
							: Lembar.keadaan(player, copy, false), copied ? Component.translatable("kultivator.tempat_ekonomi.jasa.pustaka.salin.sekali")
							: allowed ? null : Component.translatable("kultivator.tempat_ekonomi.jasa.pustaka.salin.terlarang")));
		} else {
			CompoundTag hint = baris("pegang", Component.translatable("kultivator.tempat_ekonomi.jasa.pustaka.pegang"), LembarKunci.Ikon.PERPUSTAKAAN, 0L,
					Component.translatable("kultivator.tempat_ekonomi.jasa.pustaka.pegang.efek"), LembarKunci.K_TERKUNCI, null);
			menu.add(hint);
		}
		for (String[] kind : ARSIP) {
			long price = hargaArsip(Integer.parseInt(kind[1]));
			String id = kind[0].substring(0, kind[0].length() - 1);
			menu.add(baris("arsip." + id, Component.translatable("kultivator.tempat_ekonomi.jasa.arsip." + id), LembarKunci.Ikon.PERPUSTAKAAN, price,
					Component.translatable("kultivator.tempat_ekonomi.jasa.arsip.efek"), Lembar.keadaan(player, price, false), null));
		}
		return menu;
	}

	private static long hargaArsip(int tier) {
		return Barang.harga((long) Math.ceil(1.5D * KedaiTeh.RUMOUR_PRICE[Math.max(0, Math.min(2, tier))]), 1.0D);
	}

	private static ListTag aktif(ServerPlayer player) {
		ListTag list = new ListTag();
		MinecraftServer server = player.level().getServer();
		long now = Barang.waktu(server);
		long until = EkonomiData.get(server).pemain(player.getUUID()).getLongOr("restu_kuil", 0L);
		if (until > now) {
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.tempat_ekonomi.jasa.restu_kuil"));
			row.putLong(LembarKunci.SISA_TICK, until - now);
			list.add(row);
		}
		MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
		long road = EkonomiData.get(server).pemain(player.getUUID()).getLongOr("dupa_jalan", Long.MIN_VALUE / 2);
		if (speed != null && now - road < 3600L) {
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.tempat_ekonomi.jasa.restu_jalan"));
			row.putLong(LembarKunci.SISA_TICK, speed.getDuration());
			list.add(row);
		}
		list.addAll(Hidangan.efekAktif(player));
		return list;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Actions

	private static void aksi(ServerPlayer player, int npcId, String op) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null || !EkonomiConfig.get().toko) {
			return;
		}
		String[] parts = LembarKunci.urai(op);
		if (!LembarKunci.Layanan.OP_PESAN.equals(parts[0])) {
			return;
		}
		String id = LembarKunci.arg(parts, 1);
		switch (jenis(npc)) {
			case LembarKunci.Layanan.JENIS_TABIB -> tabib(player, npc, id);
			case LembarKunci.Layanan.JENIS_KUIL -> kuil(player, npc, id);
			default -> pustaka(player, npc, id);
		}
	}

	private static void tabib(ServerPlayer player, KultivatorNpc npc, String id) {
		if ("penyimpangan".equals(id)) {
			if (Deviation.ticksLeft(player) <= 0) {
				Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.jasa.tabib.sehat"), false);
				return;
			}
			long price = hargaTabib(player, npc);
			if (!NilaiBatu.pay(player, price)) {
				Lembar.kurangUang(player, price);
				return;
			}
			Deviation.clear(player);
			SosokApi.gerak(npc, GerakId.MENULIS, 0, 40);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.5F, 1.4F);
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.jasa.tabib.sembuh", NilaiBatu.text(price)), true);
			return;
		}
		if (!id.startsWith("murid.")) {
			return;
		}
		UUID soul;
		try {
			soul = UUID.fromString(id.substring("murid.".length()));
		} catch (IllegalArgumentException exception) {
			return;
		}
		for (DiscipleEntity disciple : terluka(player)) {
			if (!disciple.soulId().equals(soul)) {
				continue;
			}
			long price = Barang.harga(15L * (Barang.pita(player) + 1), 1.0D);
			if (!NilaiBatu.pay(player, price)) {
				Lembar.kurangUang(player, price);
				return;
			}
			CompoundTag section = MandiriApi.bagianTulis(disciple.data());
			CompoundTag wound = section.getCompoundOrEmpty(MandiriIds.K_LUKA);
			wound.putLong("sampai", Barang.waktu(player.level().getServer()));
			section.put(MandiriIds.K_LUKA, wound);
			disciple.heal(disciple.getMaxHealth());
			SosokApi.gerak(npc, GerakId.MENULIS, 0, 40);
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.jasa.tabib.murid.sembuh", disciple.getDisplayName(), NilaiBatu.text(price)),
					true);
			return;
		}
	}

	private static void kuil(ServerPlayer player, KultivatorNpc npc, String id) {
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		CompoundTag tag = data.pemain(player.getUUID());
		long now = Barang.waktu(server);
		boolean road = "dupa_jalan".equals(id);
		if (road != kuilJalan(player, npc)) {
			return;
		}
		String key = road ? "dupa_jalan" : "dupa_kuil";
		if (tag.getLongOr(key, Long.MIN_VALUE / 2) + (road ? RESTU_JALAN_JEDA : DUPA_JEDA) > now) {
			Lembar.peringatan(player, Component.translatable("kultivator.tempat_ekonomi.jasa.kuil.sudah"));
			return;
		}
		long price = Barang.harga(road ? 1L : 3L, 1.0D);
		if (!NilaiBatu.pay(player, price)) {
			Lembar.kurangUang(player, price);
			return;
		}
		tag.putLong(key, now);
		KasKota.tambah(server, npc.anchorSite() != 0L ? npc.anchorSite() : npc.town(), price);
		if (road) {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, 3600, 0, false, true, true));
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.jasa.kuil.restu_jalan"), true);
		} else {
			tag.putLong("restu_kuil", now + RESTU_KUIL_TICK);
			long day = Barang.hari(server);
			if (tag.getLongOr("restu_iblis_hari", Long.MIN_VALUE) != day) {
				tag.putLong("restu_iblis_hari", day);
				CultivationData cultivation = Cultivators.get(player);
				cultivation.setHeartDemonPressure(cultivation.heartDemonPressure() - 5.0D);
				Cultivators.dirty(player);
			}
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.jasa.kuil.restu_kuil"), true);
		}
		data.touch();
		SosokApi.gerak(npc, GerakId.HORMAT, 0, 40);
		player.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.4F, 1.5F);
	}

	private static void pustaka(ServerPlayer player, KultivatorNpc npc, String id) {
		MinecraftServer server = player.level().getServer();
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		TechniqueSpec spec = JadeSlips.specOfStack(hand);
		switch (id) {
			case "baca" -> {
				if (spec == null) {
					return;
				}
				long price = hargaBaca(spec);
				if (!NilaiBatu.pay(player, price)) {
					Lembar.kurangUang(player, price);
					return;
				}
				Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.jasa.pustaka.dibaca", spec.displayName()).withStyle(ChatFormatting.GOLD));
				Msg.chat(player, Component.translatable(spec.descKey()).withStyle(ChatFormatting.GRAY));
				Msg.chat(player, Component.translatable(spec.nameKey() + ".lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
				SosokApi.gerak(npc, GerakId.MENULIS, 0, 30);
				Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.jasa.pustaka.baca.ok", NilaiBatu.text(price)), true);
			}
			case "salin" -> {
				if (spec == null) {
					return;
				}
				long hari = Barang.hari(server);
				if (EkonomiData.get(server).harian(player.getUUID(), hari, "salin") > 0) {
					Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.jasa.pustaka.salin.sekali"), false);
					return;
				}
				ItemStack copy = JadeSlips.stack(spec.id());
				if (copy.isEmpty()) {
					Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.jasa.pustaka.salin.terlarang"), false);
					return;
				}
				long price = hargaSalin(hand);
				if (!NilaiBatu.pay(player, price)) {
					Lembar.kurangUang(player, price);
					return;
				}
				EkonomiData.get(server).tambahHarian(player.getUUID(), hari, "salin", 1);
				Barang.beri(player, Barang.tandaiAsal(copy, hari));
				SosokApi.gerak(npc, GerakId.MENULIS, 0, 80);
				Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.jasa.pustaka.disalin", spec.displayName(), NilaiBatu.text(price)), true);
			}
			default -> {
				if (id.startsWith("arsip.")) {
					arsip(player, id.substring("arsip.".length()));
				}
			}
		}
	}

	/** The news archive: the nearest place of kind {@code jenis} the player does not know, within 4000 blocks. */
	private static void arsip(ServerPlayer player, String jenis) {
		String prefix = null;
		int tier = 0;
		for (String[] kind : ARSIP) {
			if (kind[0].equals(jenis + "/")) {
				prefix = kind[0];
				tier = Integer.parseInt(kind[1]);
			}
		}
		if (prefix == null) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		DuniaSites.Site best = null;
		double bestSq = 4000.0D * 4000.0D;
		for (DuniaSites.Site site : DuniaSites.live()) {
			if (!site.kind().startsWith(prefix) || !site.dim().equals(level.dimension()) || DuniaSites.knows(player, site.id())) {
				continue;
			}
			double d = site.pos().distSqr(player.blockPosition());
			if (d < bestSq) {
				bestSq = d;
				best = site;
			}
		}
		if (best == null) {
			Lembar.peringatan(player, Component.translatable("kultivator.tempat_ekonomi.jasa.arsip.tidak_ada"));
			return;
		}
		long price = hargaArsip(tier);
		if (!NilaiBatu.pay(player, price)) {
			Lembar.kurangUang(player, price);
			return;
		}
		DuniaSites.discover(player, best.id());
		BlockPos at = best.pos();
		Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.jasa.arsip.ketemu", Component.translatable("kultivator.tempat_ekonomi.jasa.arsip." + jenis),
				(int) Math.sqrt(bestSq), KedaiTeh.direction(player.blockPosition(), at), at.getX(), at.getZ()).withStyle(ChatFormatting.AQUA));
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.jasa.arsip.dibeli", NilaiBatu.text(price)), true);
	}

	private Jasa() {
	}
}
