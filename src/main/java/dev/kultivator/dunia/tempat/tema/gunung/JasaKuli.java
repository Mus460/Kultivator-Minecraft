package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.Akibat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jasa Kuli (DESIGN 38.T.6.4): the porters of the Pos Kuli carry a player's goods up the Seribu Anak Tangga for 2 NB a
 * stack. Lao Dan's dialog runs effect {@code gunung_titip <biara|aula_emas>}: the stack in the main hand (never a filled
 * container, 27 stacks at most in transit) is kept in {@link GunungData} (persistent) and arrives after about three world
 * minutes (3600 ticks); while it travels a porter is seen climbing (a KULI_GUNUNG body with the {@code daki_pikul} clip
 * standing on the landing of its progress, moved landing by landing, only where a player is near). The keeper of the
 * delivery room ({@code tema_gunung/titipan}) hands everything that arrived there back to its owner only (effect
 * {@code gunung_ambil}).
 * <p>
 * Side quest {@code tema_kuli_sehari}: effect {@code gunung_pikul} gives one porter's load (a barrel with the custom data
 * {@code kultivator_gunung: muatan}); carried to a delivery room without flying or riding it counts
 * ({@code tanda gunung_muatan}); flying or riding drops it.
 */
public final class JasaKuli {

	public static final long ONGKOS = 2L;
	public static final int PERJALANAN = 3600;
	public static final int MAKS = 27;
	public static final String MUATAN = "muatan";

	private static final Map<Long, KultivatorNpc> KULI = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		Akibat.daftar("gunung_titip", (p, a) -> titip(p, a.isEmpty() ? "aula_emas" : a.get(0)));
		Akibat.daftar("gunung_ambil", (p, a) -> ambil(p, a.isEmpty() ? "" : a.get(0)));
		Akibat.daftar("gunung_pikul", (p, a) -> pikul(p));
		// dialog condition of the delivery-room keepers: something of the player has arrived at <biara|aula_emas>
		dev.kultivator.dunia.tempat.cerita.Syarat.daftar("gunung_titipan", (p, a) -> siap(p, a.isEmpty() ? "" : a.get(0)) > 0);
		ServerTickEvents.END_SERVER_TICK.register(JasaKuli::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> KULI.clear());
	}

	/** Puts the main-hand stack in the porters' care toward {@code tujuan}. */
	public static boolean titip(ServerPlayer player, String tujuan) {
		if (!"biara".equals(tujuan) && !"aula_emas".equals(tujuan)) {
			tujuan = "aula_emas";
		}
		ItemStack hand = player.getMainHandItem();
		if (hand.isEmpty()) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.kuli.kosong").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (Barang.berisi(hand) || muatan(hand)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.kuli.wadah").withStyle(ChatFormatting.RED));
			return false;
		}
		ListTag list = GunungData.titipan(player);
		if (list.size() >= MAKS) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.kuli.penuh").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!EkonomiApi.bayar(player, ONGKOS, "jasa_kuli")) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.kuli.uang").withStyle(ChatFormatting.RED));
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		TemaKota.Kota kota = GunungTata.terdekat(level, player.blockPosition(), 160);
		CompoundTag e = new CompoundTag();
		e.put("b", Barang.simpan(hand.copy(), level.registryAccess()));
		e.putString("tj", tujuan);
		e.putLong("sp", level.getGameTime() + PERJALANAN);
		e.putLong("t0", level.getGameTime());
		e.putLong("k", kota == null ? 0L : kota.situsId());
		list.add(e);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		GunungData.ubah(level.getServer());
		Msg.chat(player, Component.translatable("kultivator.kota_tema.gunung.kuli.titip",
				Component.translatable("kultivator.kota_tema.gunung.kuli.tujuan." + tujuan)).withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** Hands back every delivery that arrived at {@code tujuan} (empty: anywhere). */
	public static int ambil(ServerPlayer player, String tujuan) {
		ListTag list = GunungData.titipan(player);
		long now = player.level().getGameTime();
		int n = 0;
		for (int i = list.size() - 1; i >= 0; i--) {
			CompoundTag e = list.getCompoundOrEmpty(i);
			if (e.getLongOr("sp", 0L) > now || !tujuan.isEmpty() && !tujuan.equals(e.getStringOr("tj", ""))) {
				continue;
			}
			ItemStack s = Barang.muat(e.get("b"), player.level().registryAccess());
			if (!s.isEmpty()) {
				Barang.beri(player, s);
				n++;
			}
			list.remove(i);
		}
		if (n > 0) {
			GunungData.ubah(player.level().getServer());
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.kuli.ambil", n).withStyle(ChatFormatting.GREEN));
		} else {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.kuli.belum").withStyle(ChatFormatting.GRAY));
		}
		return n;
	}

	/** Arrived deliveries of {@code player} at {@code tujuan} ("" = anywhere). */
	public static int siap(ServerPlayer player, String tujuan) {
		ListTag list = GunungData.titipan(player);
		long now = player.level().getGameTime();
		int n = 0;
		for (int i = 0; i < list.size(); i++) {
			CompoundTag e = list.getCompoundOrEmpty(i);
			if (e.getLongOr("sp", 0L) <= now && (tujuan.isEmpty() || tujuan.equals(e.getStringOr("tj", "")))) {
				n++;
			}
		}
		return n;
	}

	/** Deliveries of {@code player}: in transit and arrived (for the dialog / screen lines). */
	public static int[] hitung(ServerPlayer player) {
		ListTag list = GunungData.titipan(player);
		long now = player.level().getGameTime();
		int jalan = 0;
		int tiba = 0;
		for (int i = 0; i < list.size(); i++) {
			if (list.getCompoundOrEmpty(i).getLongOr("sp", 0L) > now) {
				jalan++;
			} else {
				tiba++;
			}
		}
		return new int[]{jalan, tiba};
	}

	/** The delivery room with players near: a reminder for owners whose goods arrived here. */
	static void dekatTitipan(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (level.getServer().getTickCount() % 200 != 0) {
			return;
		}
		String tempat = anchor.data().getStringOr("tempat", "");
		long now = level.getGameTime();
		for (ServerPlayer p : within) {
			if (p.distanceToSqr(anchor) > 12.0D * 12.0D) {
				continue;
			}
			ListTag list = GunungData.titipan(p);
			int siap = 0;
			for (int i = 0; i < list.size(); i++) {
				CompoundTag e = list.getCompoundOrEmpty(i);
				if (e.getLongOr("sp", 0L) <= now && tempat.equals(e.getStringOr("tj", ""))) {
					siap++;
				}
			}
			if (siap > 0) {
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.gunung.kuli.sudah_tiba", siap).withStyle(ChatFormatting.GOLD));
			}
			if (adaMuatan(p)) {
				serahkanMuatan(p);
			}
		}
	}

	// ------------------------------------------------------------------ the porter's day (side quest)

	/** One porter's load for the side quest. */
	public static void pikul(ServerPlayer player) {
		ItemStack load = new ItemStack(Items.BARREL);
		CompoundTag tag = new CompoundTag();
		tag.putString("kultivator_gunung", MUATAN);
		load.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		load.set(DataComponents.CUSTOM_NAME, Component.translatable("kultivator.kota_tema.gunung.kuli.muatan"));
		Barang.beri(player, load);
	}

	public static boolean muatan(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && MUATAN.equals(data.copyTag().getStringOr("kultivator_gunung", ""));
	}

	private static boolean adaMuatan(ServerPlayer p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (muatan(p.getInventory().getItem(i))) {
				return true;
			}
		}
		return false;
	}

	private static void serahkanMuatan(ServerPlayer p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (muatan(s)) {
				s.shrink(1);
				CeritaApi.peristiwa(p, "tanda", "gunung_muatan");
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.gunung.kuli.muatan_tiba").withStyle(ChatFormatting.GREEN));
				return;
			}
		}
	}

	private static void jatuhkanMuatan(ServerPlayer p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (muatan(s)) {
				p.getInventory().setItem(i, ItemStack.EMPTY);
				Msg.chat(p, Component.translatable("kultivator.kota_tema.gunung.kuli.muatan_jatuh").withStyle(ChatFormatting.RED));
			}
		}
	}

	// ------------------------------------------------------------------ the tick (porters, cheating loads)

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % 20 == 13) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if ((p.getAbilities().flying || p.isFallFlying() || p.isPassenger()) && adaMuatan(p)) {
					jatuhkanMuatan(p);
				}
			}
		}
		if (t % 600 != 37) {
			return;
		}
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		Map<Long, Double> jalan = new HashMap<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			ListTag list = GunungData.titipan(p);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag e = list.getCompoundOrEmpty(i);
				long sp = e.getLongOr("sp", 0L);
				if (sp > now) {
					double u = 1.0D - (sp - now) / (double) PERJALANAN;
					jalan.merge(e.getLongOr("k", 0L), Math.max(0.0D, u), Math::max);
				}
			}
		}
		for (Map.Entry<Long, KultivatorNpc> it : Map.copyOf(KULI).entrySet()) {
			if (!jalan.containsKey(it.getKey()) || it.getValue().isRemoved()) {
				if (!it.getValue().isRemoved()) {
					it.getValue().vanish();
				}
				KULI.remove(it.getKey());
			}
		}
		for (Map.Entry<Long, Double> e : jalan.entrySet()) {
			TemaKota.Kota kota = TemaKota.dari(server, e.getKey());
			if (kota == null) {
				continue;
			}
			int no = Math.min(9, 1 + (int) Math.floor(e.getValue() * 9.0D));
			Vec3 at = GunungTata.dunia(kota, GunungTata.BORDES.get(no - 1)).add(1.5D, 0.0D, 0.0D);
			KultivatorNpc old = KULI.remove(e.getKey());
			if (old != null && !old.isRemoved()) {
				old.vanish();
			}
			if (!TemaMekanisme.pemainDekat(level, at, 64.0D)) {
				continue;
			}
			KultivatorNpc kuli = HidupApi.tubuh(level, GunungAnchors.kotaId(level, BlockPos.containing(at)), NpcRole.KULI_GUNUNG,
					BlockPos.containing(at), GunungTata.yaw(kota, 180.0F));
			if (kuli != null) {
				kuli.memory().putBoolean("hold", true);
				kuli.memory().putString("tema", GunungData.TEMA);
				HidupApi.klip(kuli, GerakId.DAKI_PIKUL, 0, 0);
				KULI.put(e.getKey(), kuli);
			}
		}
	}

	private JasaKuli() {
	}
}
