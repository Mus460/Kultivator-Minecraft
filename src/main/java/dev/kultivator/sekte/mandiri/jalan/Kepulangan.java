package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.build.flow.LumbungOverflow;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.mandiri.MandiriConfig;
import dev.kultivator.sekte.mandiri.TempatCache;
import dev.kultivator.sekte.mandiri.api.HasilJalan;
import dev.kultivator.sekte.mandiri.api.JenisJalan;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriBus;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.talisman.TalismanKind;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coming home (DESIGN 39.6.2 stage 8, 39.6.7, 39.6.11): the {@code Halls} return handler of reason {@code mandiri_jalan}
 * and the arrival afterwards.
 * <p>
 * <b>The handler</b> runs once when the away record is due (the hall ticking, so also when the master joins later): it
 * resolves what is left of the plan, then writes everything that needs no body into the stored tablet data, purse first
 * (39.6.11 rule 2), then the goods made lazily from their ids (into the nine-slot bag, purchases marked
 * {@code beli_murid}), gear tiers, loot, wounds, memories, statistics and the guild badge, and returns where the body
 * appears (the gate, or the town body's spot when the master took the disciple home himself). The journey record is
 * deleted in the same step (idempotent: one record, one application).
 * <p>
 * <b>The arrival</b> is what the body does once it stands at the gate: the XP of the road, the temptation and the pride
 * are given through the live entity; then the TIBA scene carries the box on its back to the Lumbung, unpacks the sect's
 * errands, hands gifts to its friends and the master and reports. Without the scene (an order, the planner refused) the
 * same deliveries happen at once.
 */
public final class Kepulangan {

	/** The memory weight and text of each journey memory kind. */
	private static final Map<String, Integer> BOBOT = Map.ofEntries(Map.entry("mandiri.kota", 1), Map.entry("mandiri.begal_menang", 3),
			Map.entry("mandiri.begal_kalah", -3), Map.entry("mandiri.saingan_menang", 4), Map.entry("mandiri.saingan_kalah", -3),
			Map.entry("mandiri.copet_tangkap", 2), Map.entry("mandiri.ditipu", -2), Map.entry("mandiri.tolong_warga", 2),
			Map.entry("mandiri.kenalan_hati", 3), Map.entry("mandiri.tidak_ditolong", -6), Map.entry("mandiri.diselamatkan", 5),
			Map.entry("mandiri.ditebus", 3), Map.entry("mandiri.kesempatan", 3), Map.entry("mandiri.jebakan", -2),
			Map.entry("mandiri.buru", 2), Map.entry("mandiri.desa", 2), Map.entry("mandiri.sekte_tamu", 1), Map.entry("mandiri.gilda", 2),
			Map.entry("mandiri.ziarah", 1), Map.entry("mandiri.provokasi_menang", 2), Map.entry("mandiri.provokasi_kalah", -2));

	// ================================================================== moving the live away records

	/** Puts the planned return of every party member into its {@code core.away} record (an early recall: now). */
	static void aturPulang(MinecraftServer server, Rencana r) {
		long when = r.dipanggil() ? server.overworld().getGameTime() : r.pulang();
		boolean moved = false;
		for (UUID soul : r.rombongan()) {
			CompoundTag rec = Tahap.rekamAway(r.owner(), soul);
			if (rec != null && !rec.getBooleanOr("handled", false)) {
				rec.putLong("return_at", when);
				moved = true;
			}
			dev.kultivator.disciple.Disciples.setAwayUntil(server, r.owner(), soul, when);
		}
		if (moved) {
			Halls.setDirty();
		}
		JalanData.get(server).setDirty();
	}

	// ================================================================== the return handler

	/** {@code Halls.ReturnHandler} of {@link MandiriIds#AWAY_JALAN}. */
	static @Nullable Vec3 onReturn(ServerLevel level, UUID owner, CompoundTag stored, CompoundTag info) {
		MinecraftServer server = level.getServer();
		UUID soul = stored.read("soul", UUIDUtil.CODEC).orElse(null);
		if (soul == null) {
			return null;
		}
		JalanData jd = JalanData.get(server);
		Rencana r = Perjalanan.rencana(server, soul);
		if (r == null) {
			return null;
		}
		long now = server.overworld().getGameTime();
		Tahap.majukan(server, r, Math.max(now, r.pulang()));
		long hari = Tahap.hariPada(level, now);
		CompoundTag arrival = new CompoundTag();
		arrival.putString("id", r.id());
		arrival.putString("jenis", r.jenis().id());
		arrival.putLong("town", r.town());
		arrival.putString("owner", owner.toString());
		try {
			DiscipleData data = DiscipleData.load(stored.getCompoundOrEmpty("disciple"));
			Dompet.dengan(owner, soul, hari, () -> terapkan(level, r, soul, data, stored, arrival, now, hari));
			stored.put("disciple", data.save());
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Mandiri journey {} could not be applied to {}", r.id(), soul, exception);
		}
		jd.simpanTiba(soul, arrival);
		// the journey record of this soul ends here; the leader's record goes when the last member is home
		CompoundTag lead = r.tag();
		ListTag done = lead.getList("pulangJiwa").orElse(null);
		if (done == null) {
			done = new ListTag();
			lead.put("pulangJiwa", done);
		}
		done.add(StringTag.valueOf(soul.toString()));
		if (!soul.equals(r.soul())) {
			jd.hapus(soul);
		}
		if (done.size() >= r.rombongan().size()) {
			jd.hapus(r.soul());
			Tahap.hapusCermin(server, owner, r.rombongan());
			long site = r.tawanan().getLongOr("situs", 0L);
			if (site != 0L) {
				dev.kultivator.dunia.DuniaSites.endLive(site);
			}
		}
		jd.setDirty();
		BlockPos at = r.dipanggil() ? r.tibaPos() : null;
		if (at == null) {
			List<BlockPos> gate = TempatCache.semua(level, owner, TempatCache.Titik.GERBANG);
			at = gate.isEmpty() ? null : gate.get(Math.floorMod(soul.hashCode(), gate.size()));
		}
		if (at != null && r.dipanggil()) {
			arrival.putBoolean("ikut", true);
		}
		return at == null ? null : Vec3.atBottomCenterOf(at);
	}

	/** Writes the journey's results for {@code soul} into its stored data (body away). */
	private static void terapkan(ServerLevel level, Rencana r, UUID soul, DiscipleData data, CompoundTag stored, CompoundTag arrival, long now,
			long hari) {
		MinecraftServer server = level.getServer();
		UUID owner = r.owner();
		HolderLookup.Provider reg = level.registryAccess();
		RegistryOps<Tag> ops = reg.createSerializationContext(NbtOps.INSTANCE);
		RandomSource rnd = RandomSource.create(r.benih() ^ soul.getLeastSignificantBits());
		boolean leader = soul.equals(r.soul());
		int band = Tas.band(data);
		// 1. the purse: what was spent and what the master gave in town (rule 2: paid before anything is made)
		long change = r.uang(soul) + r.tambahan(soul);
		if (change != 0L) {
			MandiriApi.ubahDompet(data, change, change < 0L ? "jalan_belanja" : "guru");
		}
		long earned = r.pendapatan(soul);
		if (earned > 0L) {
			Dompet.terima(data, owner, hari, earned, "jalan_upah");
		}
		// 2. goods
		ListTag titipan = new ListTag();
		ListTag hadiah = new ListTag();
		int items = 0;
		long spent = 0L;
		ListTag goods = r.daftarBarang();
		for (int i = 0; i < goods.size(); i++) {
			CompoundTag one = goods.getCompoundOrEmpty(i);
			if (!soul.toString().equals(one.getStringOr("untuk", ""))) {
				continue;
			}
			String item = one.getStringOr("item", "");
			String ke = one.getStringOr("ke", DaftarBelanja.KE_TAS);
			int n = one.getIntOr("n", 0);
			boolean bought = one.getBooleanOr("beli", false);
			if (bought) {
				spent += one.getLongOr("harga", 0L);
				items += n;
				DaftarBelanja.coret(data, item, ke, n);
			}
			if ("jual".equals(ke)) {
				MandiriApi.ambilTas(data, reg, stack -> Tas.cocok(stack, item) && Tas.buatanMurid(stack), n);
				continue;
			}
			if (Perlengkapan.semu(item)) {
				Perlengkapan.pasang(data, item, hari);
				continue;
			}
			ItemStack stack = Tas.buat(item, n, band, rnd);
			if (stack.isEmpty()) {
				continue;
			}
			if (item.endsWith(":storage_pouch")) {
				data.extraSection(JalanIds.EXTRA).putBoolean("kantong", true);
			}
			if (DaftarBelanja.KE_TAS.equals(ke)) {
				if (bought) {
					Tas.tandaiBeli(stack, soul);
				}
				if (!MandiriApi.masukTas(data, reg, stack)) {
					titipan.add(stackTag(ops, stack));
				}
			} else if (DaftarBelanja.KE_LUMBUNG.equals(ke)) {
				titipan.add(stackTag(ops, stack));
			} else {
				CompoundTag gift = new CompoundTag();
				gift.put("stack", stackTag(ops, stack));
				gift.putString("ke", ke.startsWith(DaftarBelanja.KE_HADIAH) ? ke.substring(DaftarBelanja.KE_HADIAH.length()) : DaftarBelanja.KE_GURU);
				hadiah.add(gift);
			}
		}
		if (leader) {
			for (ItemStack loot : Rampasan.buat(r)) {
				if (!MandiriApi.masukTas(data, reg, loot)) {
					titipan.add(stackTag(ops, loot));
				}
			}
		}
		// 3. a beaten party lost a stack of its bag to the bandits
		int lost = r.hasil().getCompoundOrEmpty("hilangTas").getIntOr(soul.toString(), 0);
		for (int i = 0; i < lost; i++) {
			List<ItemStack> bag = MandiriApi.tas(data, reg);
			if (bag.isEmpty()) {
				break;
			}
			ItemStack victim = bag.get(rnd.nextInt(bag.size()));
			MandiriApi.ambilTas(data, reg, stack -> ItemStack.isSameItemSameComponents(stack, victim), victim.getCount());
		}
		// 4. used travel talismans
		if (r.anggota(soul).getBooleanOr("jimatLi", false)) {
			Tas.pakaiJimat(data, reg, TalismanKind.THOUSAND_MILE);
		}
		if (leader && r.hasil().getBooleanOr("perisaiDipakai", false)) {
			Tas.pakaiJimat(data, reg, TalismanKind.SHIELD);
		}
		if (leader && r.hasil().getBooleanOr("jimatKembaliDipakai", false)) {
			Tas.pakaiJimat(data, reg, TalismanKind.RETURN);
		}
		// 5. the body: wounds, deviation, fortunes, comprehension, the black market's day
		JenisLuka luka = r.luka(soul);
		float health = stored.getFloatOr("health", 20.0F);
		if (luka == JenisLuka.TERLUKA) {
			stored.putFloat("health", Math.max(1.0F, health * 0.30F));
			data.setDeviationTicks(Math.max(data.deviationTicks(), 3600));
		} else if (luka == JenisLuka.BERAT || luka == JenisLuka.TERTAWAN) {
			stored.putFloat("health", Math.max(1.0F, health * 0.30F));
			MandiriApi.lukai(data, JenisLuka.BERAT, now, now + (2L + rnd.nextInt(3)) * 24000L, "jalan");
		}
		int deviation = leader ? r.hasil().getIntOr("deviasi", 0) : 0;
		if (deviation > 0) {
			data.setDeviationTicks(Math.max(data.deviationTicks(), deviation));
		}
		double paham = r.hasil().getCompoundOrEmpty("paham").getDoubleOr(soul.toString(), 0.0D);
		if (paham > 0.0D && data.dao() != null) {
			data.addComprehension(paham);
		}
		if (leader) {
			double buah = r.hasil().getDoubleOr("buah", 0.0D);
			if (buah > 0.0D && !data.rank().isMax()) {
				double cap = data.xpRequired() * (data.rank().isBottleneck() ? 0.999D : 1.0D);
				data.setXp(Math.min(cap, data.xp() + data.xpRequired() * buah));
			}
			if (r.hasil().getBooleanOr("berkah", false)) {
				MandiriApi.bagianTulis(data).putLong(MandiriIds.K_BERKAH, now + 3L * 24000L);
			}
		}
		long gelap = r.hasil().getCompoundOrEmpty("gelap").getLongOr(soul.toString(), -1L);
		if (gelap >= 0L) {
			MandiriApi.pasarGelap(data, gelap);
		}
		// 6. memories of the trip
		ListTag kinds = r.hasil().getListOrEmpty("kenangan");
		for (int i = 0; i < kinds.size(); i++) {
			String kind = kinds.getStringOr(i, "");
			Integer weight = BOBOT.get(kind);
			if (weight != null) {
				Ingatan.remember(server, data, kind, null, weight, JalanIds.k("ingatan." + kind.substring(kind.indexOf('.') + 1)), "");
			}
		}
		// 7. statistics, the guild badge
		MandiriApi.tambahStatistik(data, MandiriIds.S_PERJALANAN, 1L);
		MandiriApi.tambahStatistik(data, MandiriIds.S_BELANJA_NB, spent);
		int menolong = r.hasil().getCompoundOrEmpty("menolong").getIntOr(soul.toString(), 0);
		if (menolong > 0) {
			MandiriApi.tambahStatistik(data, MandiriIds.S_MENOLONG, menolong);
		}
		if (leader) {
			MandiriApi.maksStatistik(data, MandiriIds.S_BINATANG, r.hasil().getIntOr("binatangTier", 0));
			if (r.jenis() == JenisJalan.ALAM_RAHASIA && r.sukses()) {
				MandiriApi.maksStatistik(data, MandiriIds.S_RAHASIA, Tahap.tier(r));
			}
			if (r.jenis() == JenisJalan.MISI_GILDA && r.hasil().getIntOr("misiSukses", 0) > 0) {
				lencana(data, r);
			}
			String target = r.hasil().getStringOr("dendam", "");
			if (!target.isEmpty()) {
				MandiriApi.dendam(server, owner, soul, target);
			}
		}
		// 8. what needs the live body, the deliveries and the report wait for the arrival
		arrival.putDouble("jam", r.jam());
		arrival.putInt("iblis", r.hasil().getCompoundOrEmpty("iblis").getIntOr(soul.toString(), 0));
		arrival.putInt("honor", r.hasil().getCompoundOrEmpty("honor").getIntOr(soul.toString(), 0) + (r.sukses() ? 1 : 0));
		arrival.put("titipan", titipan);
		arrival.put("hadiah", hadiah);
		arrival.putInt("barang", items);
		arrival.putLong("belanja", spent);
		arrival.putBoolean("sukses", r.sukses());
		arrival.putString("luka", luka.id());
		arrival.putString("tawanan", r.tawanan().getStringOr("cara", ""));
		arrival.putBoolean("pemimpin", leader);
		ListTag party = new ListTag();
		for (UUID other : r.rombongan()) {
			party.add(StringTag.valueOf(other.toString()));
		}
		arrival.put("rombongan", party);
		ListTag scroll = r.hasil().getListOrEmpty("tahap");
		arrival.putString("kisah", scroll.isEmpty() ? "" : scroll.getStringOr(scroll.size() - 1, ""));
		// 9. relations of a party that came home together, candidates, the visited sect
		if (leader) {
			for (UUID other : r.rombongan()) {
				if (!other.equals(soul)) {
					SekteApi.hubungan().adjust(owner, soul, other, r.sukses() ? 3 : 1, "mandiri.jalan_bersama");
				}
			}
			if (r.hasil().getBooleanOr("calon", false)) {
				Halls.addCandidates(owner, List.of(new Halls.CandidateSpec(DiscipleData.fresh(rnd), -1, -1.0D, -1.0D, false, "mandiri",
						JalanIds.k("calon.asal"), 5)));
			}
			String sect = r.hasil().getStringOr("sekte", "");
			if (!sect.isEmpty() && r.sukses()) {
				SekteApi.diplomasi().meet(owner, sect);
				if (r.hasil().getBooleanOr("sekteSekutu", false)) {
					SekteApi.diplomasi().adjust(owner, sect, 1, "mandiri_tamu");
				}
			}
		}
		MandiriBus.jalanTiba(server, owner, soul, hasil(r, soul, luka));
	}

	/** One more guild success; every third raises the badge, up to the rank's limit. */
	private static void lencana(DiscipleData data, Rencana r) {
		CompoundTag section = MandiriApi.bagianTulis(data);
		CompoundTag badge = section.getCompound(MandiriIds.K_LENCANA).orElse(null);
		if (badge == null) {
			badge = new CompoundTag();
			section.put(MandiriIds.K_LENCANA, badge);
		}
		int wins = badge.getIntOr("sukses", 0) + 1;
		badge.putInt("sukses", wins);
		int max = Tahap.pangkatMaks(r.anggota(r.soul()).getIntOr("jenjang", 0));
		if (wins % 3 == 0) {
			badge.putInt("pangkat", Math.min(max, badge.getIntOr("pangkat", 0) + 1));
		}
	}

	/** The bus record of the journey for {@code soul}. */
	private static HasilJalan hasil(Rencana r, UUID soul, JenisLuka luka) {
		List<String> scroll = new ArrayList<>();
		ListTag lines = r.hasil().getListOrEmpty("tahap");
		for (int i = 0; i < lines.size(); i++) {
			scroll.add(lines.getStringOr(i, ""));
		}
		List<HasilJalan.Barang> goods = new ArrayList<>();
		ListTag list = r.daftarBarang();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag one = list.getCompoundOrEmpty(i);
			if (soul.toString().equals(one.getStringOr("untuk", "")) && !"jual".equals(one.getStringOr("ke", ""))) {
				goods.add(new HasilJalan.Barang(one.getStringOr("item", ""), one.getIntOr("n", 0), one.getStringOr("ke", "tas")));
			}
		}
		List<String> memories = new ArrayList<>();
		ListTag kinds = r.hasil().getListOrEmpty("kenangan");
		for (int i = 0; i < kinds.size(); i++) {
			memories.add(kinds.getStringOr(i, ""));
		}
		return new HasilJalan(r.id(), r.jenis(), r.tujuan().id(), scroll, goods, r.uang(soul) + r.pendapatan(soul), luka, memories, r.jam(),
				r.pk(soul), r.sukses());
	}

	private static CompoundTag stackTag(RegistryOps<Tag> ops, ItemStack stack) {
		CompoundTag one = new CompoundTag();
		one.store("item", ItemStack.CODEC, ops, stack);
		return one;
	}

	static ItemStack stackDari(HolderLookup.Provider reg, CompoundTag one) {
		return one.read("item", ItemStack.CODEC, reg.createSerializationContext(NbtOps.INSTANCE)).orElse(ItemStack.EMPTY);
	}

	// ================================================================== the arrival

	/**
	 * The body of a soul with a waiting arrival record stands in the world (its tick20): the XP, temptation and pride of
	 * the road once, then the TIBA scene, or the deliveries at once when the scene cannot start.
	 */
	static void tiba(DiscipleEntity murid, CompoundTag arrival) {
		if (!(murid.level() instanceof ServerLevel level) || murid.ownerId() == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (!arrival.getBooleanOr("efek", false)) {
			arrival.putBoolean("efek", true);
			double jam = arrival.getDoubleOr("jam", 0.0D);
			if (jam > 0.0D) {
				double amount = 50.0D * DiscipleCultivation.meditationBaseTanpaMode(murid.data()) * jam * MandiriConfig.get().skalaXp;
				MandiriApi.beriXpKegiatan(murid, amount);
			}
			int iblis = arrival.getIntOr("iblis", 0);
			if (iblis != 0) {
				MandiriApi.ubahIblis(murid, iblis, "jalan");
			}
			int honor = Math.min(3, arrival.getIntOr("honor", 0));
			if (honor > 0) {
				SekteBus.honored(server, murid.ownerId(), murid.soulId(), "mandiri_jalan", honor);
			}
			String rescued = arrival.getStringOr("tawanan", "");
			if (!rescued.isEmpty()) {
				SekteSuara.line(murid, "diselamatkan", "thanks", 0);
			}
			JalanData.get(server).setDirty();
		}
		if (arrival.getBooleanOr("ikut", false)) {
			// the master took the disciple home himself: it follows him now (his order), the rest is delivered at once
			murid.setMode(DiscipleEntity.Mode.FOLLOW);
			antar(murid, arrival, true);
			selesai(murid, arrival);
			return;
		}
		if (arrival.getBooleanOr("adegan", false)) {
			return;
		}
		BlockPos lumbung = TempatCache.cari(murid, Kegiatan.TIBA);
		arrival.putBoolean("adegan", true);
		arrival.putLong("adeganT", level.getGameTime());
		if (!MandiriApi.mulaiKegiatan(murid, Kegiatan.TIBA, 1200, lumbung, null)) {
			antar(murid, arrival, true);
			selesai(murid, arrival);
		}
	}

	/**
	 * Delivers the errands to the Lumbung (overflow rule S12) and the gifts (a loaded friend nearby gets it in hand from
	 * the scene; otherwise the bond grows all the same and the gift waits in the Lumbung), and the master's gift.
	 */
	static void antar(DiscipleEntity murid, CompoundTag arrival, boolean semua) {
		if (!(murid.level() instanceof ServerLevel level) || murid.ownerId() == null) {
			return;
		}
		UUID owner = murid.ownerId();
		HolderLookup.Provider reg = level.registryAccess();
		ListTag titipan = arrival.getListOrEmpty("titipan");
		int worst = LumbungOverflow.LUMBUNG;
		for (int i = 0; i < titipan.size(); i++) {
			ItemStack stack = stackDari(reg, titipan.getCompoundOrEmpty(i));
			if (!stack.isEmpty()) {
				worst = Math.max(worst, LumbungOverflow.store(owner, stack));
			}
		}
		arrival.put("titipan", new ListTag());
		if (worst != LumbungOverflow.LUMBUNG) {
			LumbungOverflow.tell(level.getServer().getPlayerList().getPlayer(owner), owner, worst, null);
		}
		if (semua) {
			ListTag gifts = arrival.getListOrEmpty("hadiah");
			for (int i = 0; i < gifts.size(); i++) {
				Hadiah.beri(murid, gifts.getCompoundOrEmpty(i), false);
			}
			arrival.put("hadiah", new ListTag());
		}
	}

	/** The report, the advancement, the end of the arrival record. */
	static void selesai(DiscipleEntity murid, CompoundTag arrival) {
		if (!(murid.level() instanceof ServerLevel level) || murid.ownerId() == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		UUID owner = murid.ownerId();
		lapor(murid, arrival);
		JenisJalan jenis = JenisJalan.byId(arrival.getStringOr("jenis", ""));
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master != null && (jenis == JenisJalan.BELANJA || jenis == JenisJalan.JUAL) && arrival.getIntOr("barang", 0) > 0) {
			Advancements.grant(master, SekteIds.ADVANCEMENT_FOLDER + MandiriIds.ADV_BELANJA);
		}
		JalanData.get(server).hapusTiba(murid.soulId());
	}

	/** "<Nama> pulang dari <kota>: membeli <n> barang (<total> NB), <peristiwa>" to a master within 32 blocks, else the notices. */
	private static void lapor(DiscipleEntity murid, CompoundTag arrival) {
		ServerLevel level = (ServerLevel) murid.level();
		MinecraftServer server = level.getServer();
		UUID owner = murid.ownerId();
		long town = arrival.getLongOr("town", 0L);
		JenisJalan jenis = JenisJalan.byId(arrival.getStringOr("jenis", ""));
		Component where = town != 0L ? JalanIds.namaKota(town) : Component.translatable(jenis == null ? JalanIds.k("tempat") : jenis.kunciNama());
		String kisah = arrival.getStringOr("kisah", "");
		MutableComponent line = JalanIds.t("lapor." + (arrival.getIntOr("barang", 0) > 0 ? "belanja" : "jalan"),
				murid.getName().copy().withColor(JalanIds.WARNA_NAMA), where, arrival.getIntOr("barang", 0), arrival.getLongOr("belanja", 0L));
		if (!kisah.isEmpty()) {
			line.append(" ").append(JalanIds.teksTahap(kisah).copy().withColor(JalanIds.WARNA_ABU));
		}
		String luka = arrival.getStringOr("luka", JenisLuka.TIDAK.id());
		if (!JenisLuka.TIDAK.id().equals(luka)) {
			line.append(" ").append(Component.translatable(JenisLuka.byId(luka).kunciNama()).withColor(JalanIds.WARNA_MERAH));
		}
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master != null && master.level() == level && master.distanceToSqr(murid) <= 32.0D * 32.0D
				&& dev.kultivator.util.Sibuk.why(master) == null) {
			Msg.chat(master, line.withColor(JalanIds.WARNA_GIOK));
			SekteSuara.line(murid, "pulang_belanja", "greet", 0);
			dev.kultivator.sekte.SekteLog.log(server, owner, MandiriIds.MODUL, line);
		} else {
			MandiriApi.beritahu(server, owner, murid.soulId(), Kebijakan.Pemberitahuan.PULANG, line, !JenisLuka.TIDAK.id().equals(luka));
		}
		MandiriApi.kabar(server, owner, murid.soulId(), JalanIds.k("kabar.pulang"), false, murid.getName().getString(),
				jenis == null ? JalanIds.k("tempat") : jenis.kunciNama(), String.valueOf(arrival.getIntOr("barang", 0)));
	}

	private Kepulangan() {
	}
}
