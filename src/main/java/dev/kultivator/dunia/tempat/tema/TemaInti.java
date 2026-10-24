package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.tema.bersama.Dagang;
import dev.kultivator.dunia.tempat.tema.bersama.DagangItems;
import dev.kultivator.dunia.tempat.tema.oasis.UntaKafilahEntity;
import dev.kultivator.dunia.tempat.tema.pelabuhan.KapalDagangEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 38TK inti's implementation of contract C38.T1 ({@link TemaApi.Penyedia}), installed by {@link KotaTema#init()}, plus
 * the small shared answers the theme packages need from inti:
 * <ul>
 * <li>{@link #tema(MinecraftServer, long)} / {@link #pasar(MinecraftServer, long)}: the theme and the trade market
 * column ({@code DagangItems.PASAR_*}) of any site id: a themed town ({@code kota_tema/<tema>}), its Nama Baik town
 * ({@code kota/kota_tema}, resolved by its centre), the capital and the 34 towns ({@code PASAR_IBU_KOTA}).</li>
 * <li>{@link #dimusuhi(ServerPlayer, long)}: the enforcers of a Kota Iblis stay hostile to the player for one world day
 * after a refused Harga Darah bill ({@link #tolakTagihanIblis}; read by 38TK-I).</li>
 * <li>Caravan bodies for 38EK ({@code TemaApi.tubuhKafilah}): a string of four Bactrian camels (the lead is returned and
 * driven by 38EK; the three others walk in its footsteps, kneel when it kneels and vanish with it) or a merchant junk on
 * water.</li>
 * </ul>
 */
public final class TemaInti implements TemaApi.Penyedia {

	static final TemaInti INSTANCE = new TemaInti();

	/** Ticks of hostility after a refused iblis bill (one world day). */
	public static final long MUSUH_TICK = 24000L;
	/** Gentar lost by refusing a Harga Darah bill. */
	public static final int GENTAR_TOLAK = 10;
	/** Camels behind the lead of a caravan string. */
	public static final int UNTA_IKUT = 3;
	/** Distance between two camels of a string. */
	public static final double JARAK_UNTA = 2.8D;

	private static volatile @Nullable MinecraftServer server;
	/** Lead camel -> the camels walking behind it. */
	private static final Map<Entity, List<UntaKafilahEntity>> RANGKAIAN = new IdentityHashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			RANGKAIAN.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(TemaInti::tick);
		TemaApi.pasang(INSTANCE);
	}

	/** The running server, or null between worlds. */
	public static @Nullable MinecraftServer server() {
		return server;
	}

	// ================================================================================================ site lookups

	/** The theme of site {@code situsId} (a themed town or its Nama Baik town), or null. Satellites are not towns. */
	public static @Nullable Tema tema(@Nullable MinecraftServer current, long situsId) {
		if (current == null || situsId == 0L) {
			return null;
		}
		TemaKota.Kota kota = TemaKota.dari(current, situsId);
		if (kota != null) {
			return kota.tema();
		}
		DuniaSites.Site site = DuniaSites.byId(current, situsId);
		BlockPos centre = null;
		if (site != null) {
			if (site.kind().startsWith("kota_tema/")) {
				return Tema.dari(site.kind());
			}
			if (Towns.JENIS_KOTA_TEMA.equals(site.kind())) {
				centre = site.pos();
			}
		} else if (dikenal(current, situsId) && Towns.JENIS_KOTA_TEMA.equals(Towns.jenis(current, situsId))) {
			centre = Towns.centre(current, situsId);
		}
		if (centre == null) {
			return null;
		}
		TemaKota.Kota at = TemaKota.di(current.overworld(), centre);
		return at == null ? null : at.tema();
	}

	/**
	 * The trade market column ({@code DagangItems.PASAR_*}) of site {@code situsId}: the theme column for a themed town
	 * (iblis = black market), {@code PASAR_IBU_KOTA} for the capital and the ordinary towns, -1 elsewhere.
	 */
	public static int pasar(@Nullable MinecraftServer current, long situsId) {
		Tema tema = tema(current, situsId);
		if (tema != null) {
			return kolom(tema);
		}
		if (current == null || situsId == 0L) {
			return -1;
		}
		DuniaSites.Site site = DuniaSites.byId(current, situsId);
		String kind = site != null ? site.kind() : dikenal(current, situsId) ? Towns.jenis(current, situsId) : null;
		if (kind == null) {
			return -1;
		}
		return kind.startsWith("kota/") || kind.startsWith("kotabesar/") ? DagangItems.PASAR_IBU_KOTA : -1;
	}

	/** True when {@code town} is a known Nama Baik town (never creates a town record). */
	private static boolean dikenal(MinecraftServer current, long town) {
		return dev.kultivator.dunia.kota.KotaData.get(current).townIds().contains(town);
	}

	/** The market column of a theme. */
	public static int kolom(Tema tema) {
		return switch (tema) {
			case PELABUHAN -> DagangItems.PASAR_PELABUHAN;
			case OASIS -> DagangItems.PASAR_OASIS;
			case AIR -> DagangItems.PASAR_AIR;
			case GUNUNG -> DagangItems.PASAR_GUNUNG;
			case IBLIS -> DagangItems.PASAR_IBLIS;
		};
	}

	// ================================================================================================ C38.T1

	@Override
	public @Nullable String temaDi(ServerLevel level, BlockPos pos) {
		TemaKota.Kota kota = TemaKota.di(level, pos);
		return kota == null || !TemaConfig.aktif(kota.tema()) ? null : kota.tema().id();
	}

	@Override
	public String rupaKafilah(long situsAsal, long situsTujuan) {
		MinecraftServer current = server;
		Tema a = tema(current, situsAsal);
		Tema b = tema(current, situsTujuan);
		if (a == Tema.OASIS || b == Tema.OASIS) {
			return TemaConfig.oasis().kafilahUnta ? TemaApi.RUPA_UNTA : TemaApi.RUPA_GEROBAK;
		}
		if (a == Tema.PELABUHAN && b == Tema.PELABUHAN) {
			return TemaConfig.pelabuhan().kapalDagang ? TemaApi.RUPA_KAPAL : TemaApi.RUPA_GEROBAK;
		}
		return TemaApi.RUPA_GEROBAK;
	}

	@Override
	public double permintaan(long situsId, Item item) {
		DagangItems.Barang barang = DagangItems.barang(item);
		if (barang == null) {
			return 1.0D;
		}
		MinecraftServer current = server;
		int pasar = pasar(current, situsId);
		if (pasar < 0 || current == null) {
			return 1.0D;
		}
		return Dagang.permintaan(current, situsId, pasar, barang);
	}

	@Override
	public int infami(ServerPlayer player) {
		return KotaTemaData.get(player.level().getServer()).infami(player.getUUID());
	}

	@Override
	public void tambahInfami(ServerPlayer player, int delta, String alasan) {
		KotaTemaData data = KotaTemaData.get(player.level().getServer());
		int before = data.infami(player.getUUID());
		int after = data.setInfami(player.getUUID(), before + delta);
		if (after == before) {
			return;
		}
		CompoundTag log = data.inti().getCompoundOrEmpty("infami_catatan");
		log.putString(player.getUUID().toString(), alasan + ":" + (after - before));
		data.inti().put("infami_catatan", log);
		data.ubah();
		// crossing a tens line is worth a line (the hub card shows the meter)
		if (after / 10 != before / 10) {
			player.sendSystemMessage(Component.translatable(after > before ? "kultivator.kota_tema.infami.naik" : "kultivator.kota_tema.infami.turun", after)
					.withStyle(after > before ? ChatFormatting.DARK_RED : ChatFormatting.GRAY));
		}
	}

	@Override
	public int gentar(ServerPlayer player, long situsId) {
		return KotaTemaData.get(player.level().getServer()).gentar(player.getUUID(), situsId);
	}

	@Override
	public boolean zonaPerjanjian(ServerLevel level, BlockPos pos) {
		TemaKota.Kota kota = TemaKota.di(level, pos);
		if (kota == null || kota.tema() != Tema.IBLIS) {
			return false;
		}
		String distrik = TemaKota.distrikDi(kota, pos);
		return distrik != null && RencanaKotaTema.aman(Tema.IBLIS, distrik);
	}

	@Override
	public void tolakTagihanIblis(ServerPlayer player, long situsId) {
		MinecraftServer current = player.level().getServer();
		KotaTemaData data = KotaTemaData.get(current);
		long town = situsId;
		TemaKota.Kota kota = TemaKota.dari(current, situsId);
		if (kota == null) {
			kota = TemaKota.di(player.level(), player.blockPosition());
			if (kota != null && kota.tema() == Tema.IBLIS) {
				town = kota.situsId();
			}
		}
		data.setGentar(player.getUUID(), town, data.gentar(player.getUUID(), town) - GENTAR_TOLAK);
		CompoundTag musuh = data.inti().getCompoundOrEmpty("musuh");
		musuh.putLong(player.getUUID() + ":" + town, current.overworld().getGameTime() + MUSUH_TICK);
		data.inti().put("musuh", musuh);
		data.ubah();
		player.sendSystemMessage(Component.translatable("kultivator.kota_tema.iblis.tolak_tagihan").withStyle(ChatFormatting.DARK_RED));
	}

	/** True while the enforcers of Kota Iblis {@code situsId} hunt the player for a refused Harga Darah bill. */
	public static boolean dimusuhi(ServerPlayer player, long situsId) {
		KotaTemaData data = KotaTemaData.get(player.level().getServer());
		long until = data.inti().getCompoundOrEmpty("musuh").getLongOr(player.getUUID() + ":" + situsId, 0L);
		return until > player.level().getServer().overworld().getGameTime();
	}

	/** Ends the hostility (a paid bill, a quest). */
	public static void maafkan(ServerPlayer player, long situsId) {
		KotaTemaData data = KotaTemaData.get(player.level().getServer());
		CompoundTag musuh = data.inti().getCompoundOrEmpty("musuh");
		if (musuh.contains(player.getUUID() + ":" + situsId)) {
			musuh.remove(player.getUUID() + ":" + situsId);
			data.ubah();
		}
	}

	@Override
	public @Nullable Entity tubuhKafilah(ServerLevel level, String rupa, Vec3 pos, float yaw) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		if (TemaApi.RUPA_UNTA.equals(rupa)) {
			return untaRangkai(level, pos, yaw);
		}
		if (TemaApi.RUPA_KAPAL.equals(rupa)) {
			BlockPos at = BlockPos.containing(pos);
			if (!level.getFluidState(at).is(FluidTags.WATER) && !level.getFluidState(at.below()).is(FluidTags.WATER)) {
				return null;
			}
			KapalDagangEntity kapal = TemaMekanisme.muncul(level, TemaEntities.KAPAL_DAGANG, new Vec3(pos.x, at.getY(), pos.z), yaw, null, null);
			if (kapal != null) {
				kapal.setVarian(KapalDagangEntity.VARIAN_JUNG_LAUT);
				kapal.setKeadaan(KapalDagangEntity.KEADAAN_BERLAYAR);
			}
			return kapal;
		}
		return null;
	}

	// ================================================================================================ camel strings

	private static @Nullable Entity untaRangkai(ServerLevel level, Vec3 pos, float yaw) {
		UntaKafilahEntity lead = TemaMekanisme.muncul(level, TemaEntities.UNTA_KAFILAH, pos, yaw, null, null);
		if (lead == null) {
			return null;
		}
		lead.setVarian(UntaKafilahEntity.VARIAN_BEBAN);
		List<UntaKafilahEntity> ikut = new ArrayList<>(UNTA_IKUT);
		double rad = Math.toRadians(yaw);
		double bx = Math.sin(rad);
		double bz = -Math.cos(rad);
		for (int i = 1; i <= UNTA_IKUT; i++) {
			Vec3 at = pos.add(bx * JARAK_UNTA * i, 0.0D, bz * JARAK_UNTA * i);
			UntaKafilahEntity camel = TemaMekanisme.muncul(level, TemaEntities.UNTA_KAFILAH, at, yaw, null, null);
			if (camel == null) {
				break;
			}
			camel.setVarian(i == UNTA_IKUT ? UntaKafilahEntity.VARIAN_POLOS : UntaKafilahEntity.VARIAN_BEBAN);
			ikut.add(camel);
		}
		RANGKAIAN.put(lead, ikut);
		return lead;
	}

	/** Every 5 ticks: each camel walks to the footprint of the one before it; a string whose lead is gone vanishes. */
	private static void tick(MinecraftServer current) {
		if (RANGKAIAN.isEmpty() || current.getTickCount() % 5 != 2) {
			return;
		}
		RANGKAIAN.entrySet().removeIf(entry -> {
			Entity lead = entry.getKey();
			List<UntaKafilahEntity> ikut = entry.getValue();
			if (lead.isRemoved()) {
				for (UntaKafilahEntity camel : ikut) {
					TemaMekanisme.lepas(camel);
				}
				return true;
			}
			ikut.removeIf(Entity::isRemoved);
			Entity before = lead;
			int keadaan = lead instanceof UntaKafilahEntity u ? u.keadaan() : UntaKafilahEntity.KEADAAN_BERDIRI;
			for (UntaKafilahEntity camel : ikut) {
				ikuti(camel, before, keadaan);
				before = camel;
			}
			return false;
		});
	}

	private static void ikuti(UntaKafilahEntity camel, Entity before, int keadaanLead) {
		if (camel.keadaan() != keadaanLead && (keadaanLead == UntaKafilahEntity.KEADAAN_BERLUTUT || keadaanLead == UntaKafilahEntity.KEADAAN_BADAI
				|| keadaanLead == UntaKafilahEntity.KEADAAN_BERDIRI)) {
			camel.setKeadaan(keadaanLead);
		}
		if (camel.keadaan() == UntaKafilahEntity.KEADAAN_BERLUTUT || camel.keadaan() == UntaKafilahEntity.KEADAAN_BADAI) {
			camel.getNavigation().stop();
			return;
		}
		double rad = Math.toRadians(before.getYRot());
		Vec3 target = before.position().add(Math.sin(rad) * JARAK_UNTA, 0.0D, -Math.cos(rad) * JARAK_UNTA);
		double d2 = camel.position().distanceToSqr(target);
		if (d2 > 24.0D * 24.0D) {
			camel.snapTo(target.x, target.y, target.z, before.getYRot(), 0.0F);
		} else if (d2 > 1.2D) {
			camel.getNavigation().moveTo(target.x, target.y, target.z, Mth.clamp(Math.sqrt(d2) / 3.0D, 0.6D, 1.4D));
		}
	}

	private TemaInti() {
	}
}
