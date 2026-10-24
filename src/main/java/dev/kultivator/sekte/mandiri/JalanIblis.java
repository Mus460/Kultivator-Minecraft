package dev.kultivator.sekte.mandiri;

import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.disciple.path.SoulSeal;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.HatiService;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriBus;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The temptation layer (Jalan Iblis, DESIGN 39.3.3), switch {@code mandiri.jalanIblis}. Temptation 0-100 moves once a day:
 * +1 for each vengeful or sly trait, +2 for each grudge tie (at most two), +2 when contentment is below 30, +2 when the
 * memories drift below -15, +1 at the new moon, +4 after shopping in a black market; -1 for each loyal, caring or calm
 * trait, -2 when contentment is above 70. Events move it at once: a lost heart trial +15, a failed revenge +10, the
 * master's scolding -6, a Heart Calming Pill -10, the ancestor rite -5. A sealed soul or the heir never passes 59.
 * <p>
 * 60 = Tergoda (whispers, a detour to the black market, part of the gains hidden); 85 = Jalan Iblis: the path is
 * overlaid by {@code iblis} (the old one kept in {@code jalanAsal}), meditation on a new-moon night x1.3, 2 % a day to
 * take one thing from the Lumbung without leave (hall-mates remember it). Redemption: the master's 60 s ritual (the
 * disciple kneels before the stele, the master within 6 blocks, a Heart Calming Pill from the master) sets 30 and gives
 * the old path back with a lasting memory; without the master, three nights in a row of tea with a sworn friend whose own
 * temptation is below 20 take 20 a night. Nothing here changes a block.
 */
public final class JalanIblis {

	public static final int TERGODA = 60;
	public static final int IBLIS = 85;
	public static final int TEBUS_HASIL = 30;
	public static final int TEBUS_TICK = 1200;
	public static final double TEBUS_JARAK = 6.0D;
	public static final int SEGEL_MAKS = 59;
	/** {@code extra.mandiri.pasarGelap}: the world day of the last black-market purchase (39C sets it). */
	public static final String K_PASAR_GELAP = "pasarGelap";

	private static final int UNGU = 0x9B4FD1;
	private static final int EMAS = 0xFFD86A;

	static void init() {
		// Meditation on a new-moon night x1.3 for a disciple on the demonic path (39.3.3).
		DiscipleHooks.addXpFactor((entity, data, source) -> {
			if (source != XpSource.MEDITATION || entity == null || !(entity.level() instanceof ServerLevel level)
					|| MandiriApi.jalan(data) != JalanHidup.IBLIS || !aktif()) {
				return 1.0D;
			}
			return Irama.malam(Irama.jam(level)) && Irama.bulanBaru(level, entity.blockPosition()) ? 1.3D : 1.0D;
		});
		SekteBus.ANCESTOR_RITE.register((server, owner, kind, attendees) -> {
			for (UUID soul : attendees) {
				DiscipleEntity d = Perencana.cari(owner, soul);
				if (d != null) {
					ubah(d, -5, "upacara");
				}
			}
		});
	}

	static boolean aktif() {
		return MandiriConfig.get().jalanIblis && MandiriApi.aktif();
	}

	// ================================================================== the daily step

	/** The daily temptation change of {@code hari} days (catch-up passes more than one). */
	public static void harian(DiscipleEntity murid, int hari) {
		if (!aktif() || hari <= 0) {
			return;
		}
		DiscipleData data = murid.data();
		HatiService hati = SekteApi.hati();
		int d = 0;
		if (hati.has(data, Sifat.PENDENDAM)) {
			d += 1;
		}
		if (hati.has(data, Sifat.LICIK)) {
			d += 1;
		}
		UUID owner = murid.ownerId();
		if (owner != null) {
			d += 2 * Math.min(2, SekteApi.hubungan().with(owner, murid.soulId(), Ikatan.DENDAM).size());
		}
		int puas = hati.contentment(data);
		if (puas < 30) {
			d += 2;
		} else if (puas > 70) {
			d -= 2;
		}
		long today = Irama.hari(murid.level());
		if (Ingatan.drift(data, today) < -15.0D) {
			d += 2;
		}
		if (Irama.bulanBaru((ServerLevel) murid.level(), murid.blockPosition())) {
			d += 1;
		}
		if (MandiriApi.bagian(data).getLongOr(K_PASAR_GELAP, -9L) >= today - 1L) {
			d += 4;
		}
		for (Sifat good : new Sifat[]{Sifat.SETIA, Sifat.PENYAYANG, Sifat.TENANG}) {
			if (hati.has(data, good)) {
				d -= 1;
			}
		}
		if (d != 0) {
			ubah(murid, d * hari, "harian");
		}
		if (MandiriApi.jalan(data) == JalanHidup.IBLIS) {
			lumbung(murid, today);
		}
	}

	/** Changes temptation by {@code delta} ({@code sebab} for the log) with every threshold consequence. */
	public static void ubah(DiscipleEntity murid, int delta, String sebab) {
		if (!aktif() || delta == 0) {
			return;
		}
		set(murid, MandiriApi.iblis(murid.data()) + delta);
	}

	/** Sets temptation (the test command and the redemption), clamped and capped for a sealed soul or the heir. */
	public static void set(DiscipleEntity murid, int value) {
		DiscipleData data = murid.data();
		int cap = SoulSeal.sealed(data) || DisciplePaths.isHeir(data) ? SEGEL_MAKS : 100;
		int before = MandiriApi.iblis(data);
		int after = Math.max(0, Math.min(cap, value));
		if (after == before) {
			return;
		}
		MandiriApi.bagianTulis(data).putInt(MandiriIds.K_IBLIS, after);
		ServerLevel level = (ServerLevel) murid.level();
		MinecraftServer server = level.getServer();
		UUID owner = murid.ownerId();
		UUID soul = murid.soulId();
		boolean naikTergoda = before < TERGODA && after >= TERGODA;
		boolean naikIblis = before < IBLIS && after >= IBLIS;
		boolean turunIblis = before >= IBLIS && after < IBLIS;
		boolean turunTergoda = before >= TERGODA && after < TERGODA;
		if (!(naikTergoda || naikIblis || turunIblis || turunTergoda)) {
			return;
		}
		MandiriBus.iblis(server, owner, soul, before, after);
		long hari = Irama.hari(level);
		int jam = Irama.jam(level);
		String nama = murid.getName().getString();
		if (naikIblis) {
			JalanHidup asal = MandiriApi.jalan(data);
			if (asal != null && asal != JalanHidup.IBLIS) {
				MandiriApi.bagianTulis(data).putString(MandiriIds.K_JALAN_ASAL, asal.id());
			}
			PilihJalan.tetapkan(murid, asal, JalanHidup.IBLIS, "iblis");
			Ingatan.remember(server, data, "mandiri.iblis", null, -4, "kultivator.sekte.mandiri.inti.ingatan.iblis", "");
			MandiriApi.catat(owner, soul, new EntriHarian(hari, jam, "iblis", "kultivator.sekte.mandiri.inti.harian.iblis.jatuh",
					List.of(nama), EntriHarian.Suasana.TERGODA, true));
			MandiriApi.beritahu(server, owner, soul, Kebijakan.Pemberitahuan.IBLIS,
					Component.translatable("kultivator.sekte.mandiri.inti.pesan.iblis_jatuh", murid.getName()), true);
			Fx.ring(level, murid.position().add(0.0D, 0.1D, 0.0D), UNGU, 2.5F);
		} else if (naikTergoda) {
			MandiriApi.catat(owner, soul, new EntriHarian(hari, jam, "iblis", "kultivator.sekte.mandiri.inti.harian.iblis.tergoda",
					List.of(nama), EntriHarian.Suasana.TERGODA, false));
			MandiriApi.beritahu(server, owner, soul, Kebijakan.Pemberitahuan.IBLIS,
					Component.translatable("kultivator.sekte.mandiri.inti.pesan.iblis_tergoda", murid.getName()), false);
		}
		if (turunIblis) {
			kembalikanJalan(murid);
		}
		MandiriApi.kabar(server, owner, soul, "kultivator.sekte.mandiri.inti.kabar.iblis", naikIblis, nama,
				String.valueOf(before), String.valueOf(after));
	}

	/** Gives back the path the overlay replaced. */
	private static void kembalikanJalan(DiscipleEntity murid) {
		DiscipleData data = murid.data();
		if (MandiriApi.jalan(data) != JalanHidup.IBLIS) {
			return;
		}
		CompoundTag section = MandiriApi.bagianTulis(data);
		JalanHidup asal = JalanHidup.byId(section.getStringOr(MandiriIds.K_JALAN_ASAL, ""));
		section.remove(MandiriIds.K_JALAN_ASAL);
		PilihJalan.tetapkan(murid, JalanHidup.IBLIS, asal == null ? JalanHidup.PERTAPA : asal, "tebus");
	}

	/** 2 % a day: one thing from the Lumbung into the bag, without leave; hall-mates remember it. */
	private static void lumbung(DiscipleEntity murid, long today) {
		DiscipleData data = murid.data();
		UUID owner = murid.ownerId();
		if (owner == null || MandiriApi.bagian(data).getLongOr(MandiriIds.K_IBLIS_HARI, -1L) == today) {
			return;
		}
		MandiriApi.bagianTulis(data).putLong(MandiriIds.K_IBLIS_HARI, today);
		if (murid.getRandom().nextFloat() >= 0.02F) {
			return;
		}
		ServerLevel level = (ServerLevel) murid.level();
		List<ItemStack> taken = Storehouse.take(owner, stack -> !stack.isEmpty(), 1);
		if (taken.isEmpty()) {
			return;
		}
		ItemStack stack = taken.get(0);
		if (!MandiriApi.masukTas(data, level.registryAccess(), stack)) {
			// The bag is full: it puts the thing back before anyone sees.
			Storehouse.insert(owner, stack);
			return;
		}
		MinecraftServer server = level.getServer();
		String nama = murid.getName().getString();
		String barang = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		MandiriApi.kabar(server, owner, murid.soulId(), "kultivator.sekte.mandiri.inti.kabar.lumbung_hilang", true, nama,
				stack.getItem().getDescriptionId());
		MandiriData.get(server).catatKas(owner, murid.soulId(), today, 0L, "lumbung_diambil:" + barang);
		for (DiscipleEntity other : Perencana.dimuat(owner)) {
			if (other != murid && other.distanceToSqr(murid) < 48.0D * 48.0D) {
				Ingatan.remember(server, other.data(), "mandiri.iblis_curi", murid.soulId(), -2,
						"kultivator.sekte.mandiri.inti.ingatan.iblis_curi", nama);
			}
		}
	}

	// ================================================================== the master's hand (39.7.3)

	private static boolean sekaliSehari(DiscipleEntity murid) {
		long today = Irama.hari(murid.level());
		CompoundTag section = MandiriApi.bagianTulis(murid.data());
		if (section.getLongOr(MandiriIds.K_TEGUR_HARI, -1L) == today) {
			return false;
		}
		section.putLong(MandiriIds.K_TEGUR_HARI, today);
		return true;
	}

	/** [Tegur]: temptation -6 (once a day, scolding or praise). */
	public static boolean tegur(DiscipleEntity murid, ServerPlayer master) {
		if (murid == null || master == null || !murid.isOwnedBy(master) || !sekaliSehari(murid)) {
			return false;
		}
		ubahSelalu(murid, -6);
		ServerLevel level = (ServerLevel) murid.level();
		MandiriApi.catat(murid.ownerId(), murid.soulId(), new EntriHarian(Irama.hari(level), Irama.jam(level), "guru",
				"kultivator.sekte.mandiri.inti.harian.tegur", List.of(murid.getName().getString()), EntriHarian.Suasana.KESAL, false));
		Msg.chat(master, Component.translatable("kultivator.sekte.mandiri.inti.pesan.tegur", murid.getName()).withStyle(ChatFormatting.GRAY));
		return true;
	}

	/** [Puji]: contentment through {@code DISCIPLE_HONORED} +2 (once a day, scolding or praise). */
	public static boolean puji(DiscipleEntity murid, ServerPlayer master) {
		if (murid == null || master == null || !murid.isOwnedBy(master) || !sekaliSehari(murid)) {
			return false;
		}
		ServerLevel level = (ServerLevel) murid.level();
		SekteBus.honored(level.getServer(), murid.ownerId(), murid.soulId(), "mandiri_puji", 2);
		MandiriApi.catat(murid.ownerId(), murid.soulId(), new EntriHarian(Irama.hari(level), Irama.jam(level), "guru",
				"kultivator.sekte.mandiri.inti.harian.puji", List.of(murid.getName().getString()), EntriHarian.Suasana.SENANG, false));
		Msg.chat(master, Component.translatable("kultivator.sekte.mandiri.inti.pesan.puji", murid.getName()).withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** The scolding counts even with the layer off for the next time it is switched on. */
	private static void ubahSelalu(DiscipleEntity murid, int delta) {
		if (aktif()) {
			ubah(murid, delta, "guru");
		} else {
			MandiriApi.bagianTulis(murid.data()).putInt(MandiriIds.K_IBLIS, Math.max(0, MandiriApi.iblis(murid.data()) + delta));
		}
	}

	/** A Heart Calming Pill swallowed (39B's pill rule, the master's feeding): -10. */
	public static void pilHati(DiscipleEntity murid) {
		ubah(murid, -10, "pil");
	}

	/**
	 * [Tebus]: starts the master's redemption ritual. Null when it started; else the reason (temptation below 60, too
	 * far, no pill, already running, busy).
	 */
	public static @Nullable Component mulaiTebus(DiscipleEntity murid, ServerPlayer master) {
		if (murid == null || master == null || !murid.isOwnedBy(master)) {
			return Component.translatable("kultivator.sekte.mandiri.inti.tebus.bukan");
		}
		if (MandiriApi.iblis(murid.data()) < TERGODA) {
			return Component.translatable("kultivator.sekte.mandiri.inti.tebus.tidak_perlu", murid.getName());
		}
		if (master.level() != murid.level() || master.distanceToSqr(murid) > TEBUS_JARAK * TEBUS_JARAK) {
			return Component.translatable("kultivator.sekte.mandiri.inti.tebus.jauh", String.valueOf((int) TEBUS_JARAK));
		}
		if (Perencana.acara(murid, PelaksanaDasar.ACARA_TEBUS)) {
			return Component.translatable("kultivator.sekte.mandiri.inti.tebus.berjalan");
		}
		if (murid.data().breakingThrough() || murid.getTarget() != null) {
			return Component.translatable("kultivator.sekte.mandiri.inti.tebus.sibuk", murid.getName());
		}
		if (!ambilPil(master)) {
			return Component.translatable("kultivator.sekte.mandiri.inti.tebus.pil");
		}
		CompoundTag tebus = new CompoundTag();
		tebus.putLong("mulai", murid.level().getGameTime());
		tebus.putString("guru", master.getUUID().toString());
		MandiriApi.bagianTulis(murid.data()).put(MandiriIds.K_TEBUS, tebus);
		BlockPos stele = Irama.pusat(murid.ownerId());
		BlockPos spot = stele != null && murid.distanceToSqr(stele.getX(), stele.getY(), stele.getZ()) < 64.0D * 64.0D
				? stele.south(2) : murid.blockPosition();
		Perencana.mulaiAcara(murid, PelaksanaDasar.ACARA_TEBUS, spot, TEBUS_TICK);
		Msg.chat(master, Component.translatable("kultivator.sekte.mandiri.inti.tebus.mulai", murid.getName()).withStyle(ChatFormatting.LIGHT_PURPLE));
		return null;
	}

	/** One heart-calming pill out of the master's hands or inventory. */
	private static boolean ambilPil(ServerPlayer master) {
		if (master.isCreative()) {
			return true;
		}
		Inventory inv = master.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.isEmpty() && "kultivator:heart_calming_pill".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
				stack.shrink(1);
				inv.setChanged();
				return true;
			}
		}
		return false;
	}

	/** One ritual step (every second): the master must stay within 6 blocks; violet turns to gold as it goes. */
	static boolean langkahTebus(DiscipleEntity murid, long mulai, long now) {
		CompoundTag tebus = MandiriApi.bagian(murid.data()).getCompoundOrEmpty(MandiriIds.K_TEBUS);
		UUID guru = PelaksanaDasar.uuid(tebus.getStringOr("guru", ""));
		ServerLevel level = (ServerLevel) murid.level();
		ServerPlayer master = guru == null ? null : level.getServer().getPlayerList().getPlayer(guru);
		if (master == null || master.level() != level || master.distanceToSqr(murid) > (TEBUS_JARAK + 2.0D) * (TEBUS_JARAK + 2.0D)) {
			return false;
		}
		double t = Math.min(1.0D, (now - mulai) / (double) TEBUS_TICK);
		if ((now / 20L) % 2L == 0L) {
			int rgb = lerp(UNGU, EMAS, t);
			Fx.ring(level, murid.position().add(0.0D, 0.05D, 0.0D), rgb, 1.2F + (float) t * 1.3F);
		}
		if ((now / 20L) % 5L == 0L) {
			level.playSound(null, murid.getX(), murid.getY(), murid.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.NEUTRAL,
					0.4F, 0.6F + (float) t * 0.8F);
		}
		return true;
	}

	/** The ritual ended: redeemed (temptation 30, the old path back, a lasting memory) or broken off. */
	static void selesaiTebus(DiscipleEntity murid, boolean berhasil) {
		DiscipleData data = murid.data();
		MandiriApi.bagianTulis(data).remove(MandiriIds.K_TEBUS);
		ServerLevel level = (ServerLevel) murid.level();
		MinecraftServer server = level.getServer();
		UUID owner = murid.ownerId();
		ServerPlayer master = owner == null ? null : server.getPlayerList().getPlayer(owner);
		if (!berhasil) {
			if (master != null) {
				Msg.chat(master, Component.translatable("kultivator.sekte.mandiri.inti.tebus.putus", murid.getName()).withStyle(ChatFormatting.RED));
			}
			return;
		}
		set(murid, Math.min(MandiriApi.iblis(data), TEBUS_HASIL));
		kembalikanJalan(murid);
		Ingatan.remember(server, data, "mandiri.ditebus", owner, 9, "kultivator.sekte.mandiri.inti.ingatan.ditebus", "");
		SekteBus.honored(server, owner, murid.soulId(), "mandiri_tebus", 5);
		Vec3 at = murid.position();
		Fx.pillar(level, at, EMAS, 4.0F);
		Fx.burst(level, at.add(0.0D, 1.0D, 0.0D), EMAS, 30, 1.2F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.8F, 1.2F);
		MandiriApi.catat(owner, murid.soulId(), new EntriHarian(Irama.hari(level), Irama.jam(level), "iblis",
				"kultivator.sekte.mandiri.inti.harian.iblis.ditebus", List.of(murid.getName().getString()), EntriHarian.Suasana.TENANG, true));
		MandiriApi.kabar(server, owner, murid.soulId(), "kultivator.sekte.mandiri.inti.kabar.ditebus", true, murid.getName().getString());
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.mandiri.inti.tebus.berhasil", murid.getName()).withStyle(ChatFormatting.GOLD));
		}
	}

	/**
	 * A night of tea with {@code teman} (19:00 or later): when the friend is sworn and its own temptation is below 20,
	 * the third night in a row and every one after takes 20 off.
	 */
	public static void malamBersahabat(DiscipleEntity murid, @Nullable DiscipleEntity teman) {
		if (!aktif() || teman == null || MandiriApi.iblis(murid.data()) < TERGODA || MandiriApi.iblis(teman.data()) >= 20
				|| murid.ownerId() == null) {
			return;
		}
		Ikatan tie = SekteApi.hubungan().kind(murid.ownerId(), murid.soulId(), teman.soulId());
		if (tie != Ikatan.SAHABAT && tie != Ikatan.SAUDARA_ANGKAT) {
			return;
		}
		long today = Irama.hari(murid.level());
		CompoundTag section = MandiriApi.bagianTulis(murid.data());
		CompoundTag tebus = section.getCompound(MandiriIds.K_TEBUS).orElse(null);
		if (tebus == null) {
			tebus = new CompoundTag();
			section.put(MandiriIds.K_TEBUS, tebus);
		}
		long last = tebus.getLongOr("malamHari", -9L);
		if (last == today) {
			return;
		}
		int malam = last == today - 1L ? tebus.getIntOr("malam", 0) + 1 : 1;
		tebus.putInt("malam", malam);
		tebus.putLong("malamHari", today);
		if (malam >= 3) {
			ubah(murid, -20, "sahabat");
			ServerLevel level = (ServerLevel) murid.level();
			Ingatan.remember(level.getServer(), murid.data(), "mandiri.sahabat_tebus", teman.soulId(), 6,
					"kultivator.sekte.mandiri.inti.ingatan.sahabat_tebus", teman.getName().getString());
			MandiriApi.catat(murid.ownerId(), murid.soulId(), new EntriHarian(today, Irama.jam(level), "iblis",
					"kultivator.sekte.mandiri.inti.harian.iblis.sahabat", List.of(murid.getName().getString(), teman.getName().getString()),
					EntriHarian.Suasana.TENANG, true));
		}
	}

	private static int lerp(int a, int b, double t) {
		int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private JalanIblis() {
	}
}
