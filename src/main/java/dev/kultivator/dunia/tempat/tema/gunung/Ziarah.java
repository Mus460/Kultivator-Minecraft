package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ziarah Seribu Anak Tangga (DESIGN 38.T.6.4): register at the Kuil Kaki (free; nine Dupa Ziarah are given), climb from
 * the Gerbang Kaki to the Aula Emas <b>without flying, cable car, pulley or teleport</b>, light incense at the nine
 * landings ({@code tema_gunung/bordes}: use a Dupa Ziarah within 3 blocks), reach the Aula Emas within one world day
 * (24000 ticks). The reward is the <b>Restu Puncak</b> for 7 world days: the Aula Emas meditation site x1.4 (through
 * {@code TemaSitus.daftarSyarat}) and +10 % meditation XP anywhere ({@link #faktorMeditasi}; the multiplier hook is
 * requested of F2), at most once per 7 days; the first pilgrimage also gives +2 comprehension of the player's Dao and
 * the advancement {@code tempat/tema_gunung_peziarah}.
 * <p>
 * Checks run once a second for climbers only (no world scan): creative/mod flight ({@code abilities.flying}, elytra,
 * or 3 s airborne without falling), riding anything, a jump of more than 32 blocks between two checks (teleport), the
 * deadline, leaving the mountain. State in {@link GunungData} ({@code zs zt zb zk zw zn rp rj rs}).
 */
public final class Ziarah {

	public static final int HARI = 24000;
	public static final long RESTU_TICK = 7L * 24000L;
	public static final int DUPA_DIBERI = 9;
	public static final double JARI_BORDES = 3.0D;

	public static final String BATAL_TERBANG = "terbang";
	public static final String BATAL_KERETA = "kereta";
	public static final String BATAL_KATROL = "katrol";
	public static final String BATAL_TELEPORT = "teleport";
	public static final String BATAL_WAKTU = "waktu";
	public static final String BATAL_PERGI = "pergi";
	public static final String BATAL_BERHENTI = "berhenti";

	/** The rules shown on the screen (keys kultivator.kota_tema.gunung.ziarah.syarat.<id>). */
	public static final List<String> SYARAT = List.of("tanpa_terbang", "tanpa_kereta", "tanpa_katrol", "tanpa_teleport", "satu_hari");

	private record Jejak(Vec3 pos, int melayang) {
	}

	private static final Map<UUID, Jejak> JEJAK = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(Ziarah::tick);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer sp && !level.isClientSide() && player.getItemInHand(hand).is(TemaItems.DUPA_ZIARAH)) {
				return nyalakan(sp, player.getItemInHand(hand)) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
			}
			return InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> JEJAK.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> JEJAK.clear());
	}

	// ================================================================================================ state

	public static int status(ServerPlayer player) {
		return GunungData.pemain(player).getIntOr("zs", GunungData.Z_TIDAK);
	}

	public static boolean berjalan(ServerPlayer player) {
		return status(player) == GunungData.Z_BERJALAN;
	}

	public static int bordesMenyala(ServerPlayer player) {
		return GunungData.pemain(player).getIntOr("zb", 0);
	}

	/** True while the Restu Puncak lasts. */
	public static boolean restuAktif(ServerPlayer player) {
		return GunungData.pemain(player).getLongOr("rp", 0L) > player.level().getGameTime();
	}

	/** Meditation XP factor of the Restu Puncak (1.1 while it lasts; for the F2 hook in Meditation). */
	public static double faktorMeditasi(ServerPlayer player) {
		return restuAktif(player) ? 1.1D : 1.0D;
	}

	/** World days left of the Restu Puncak (0 = none). */
	public static int sisaHariRestu(ServerPlayer player) {
		long left = GunungData.pemain(player).getLongOr("rp", 0L) - player.level().getGameTime();
		return left <= 0 ? 0 : (int) ((left + 23999L) / 24000L);
	}

	/** World days until a new Restu may be earned (0 = now). */
	public static int jedaHari(ServerPlayer player) {
		long left = GunungData.pemain(player).getLongOr("rj", 0L) - player.level().getGameTime();
		return left <= 0 ? 0 : (int) ((left + 23999L) / 24000L);
	}

	// ================================================================================================ actions

	/** Registers the climb (the Kuil Kaki desk); false when already climbing. */
	public static boolean daftar(ServerPlayer player, @Nullable TemaKota.Kota kota) {
		if (kota == null) {
			return false;
		}
		CompoundTag p = GunungData.pemain(player);
		if (p.getIntOr("zs", 0) == GunungData.Z_BERJALAN) {
			return false;
		}
		p.putInt("zs", GunungData.Z_BERJALAN);
		p.putLong("zt", player.level().getGameTime());
		p.putInt("zb", 0);
		p.putLong("zk", kota.situsId());
		p.remove("zw");
		GunungData.ubah(player.level().getServer());
		int punya = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(TemaItems.DUPA_ZIARAH)) {
				punya += s.getCount();
			}
		}
		if (punya < DUPA_DIBERI) {
			Barang.beri(player, new ItemStack(TemaItems.DUPA_ZIARAH, DUPA_DIBERI - punya));
		}
		JEJAK.put(player.getUUID(), new Jejak(player.position(), 0));
		Msg.title(player, Component.translatable("kultivator.kota_tema.gunung.ziarah.mulai").withStyle(ChatFormatting.GOLD),
				Component.translatable("kultivator.kota_tema.gunung.ziarah.mulai.sub"), 10, 60, 20);
		CeritaApi.peristiwa(player, "tanda", "gunung_ziarah_daftar");
		return true;
	}

	/** Cancels a running climb for {@code alasan} (a BATAL_* id); no-op otherwise. */
	public static void batal(ServerPlayer player, String alasan) {
		CompoundTag p = GunungData.pemain(player);
		if (p.getIntOr("zs", 0) != GunungData.Z_BERJALAN) {
			return;
		}
		p.putInt("zs", GunungData.Z_BATAL);
		p.putString("zw", alasan);
		GunungData.ubah(player.level().getServer());
		JEJAK.remove(player.getUUID());
		Msg.chat(player, Component.translatable("kultivator.kota_tema.gunung.ziarah.batal." + alasan).withStyle(ChatFormatting.RED));
	}

	/** Uses a Dupa Ziarah: lights the landing within 3 blocks during a climb. */
	private static boolean nyalakan(ServerPlayer player, ItemStack dupa) {
		if (!berjalan(player) || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		TemaKota.Kota kota = GunungTata.kotaDi(level, player.blockPosition());
		if (kota == null) {
			return false;
		}
		int no = GunungTata.bordesDekat(kota, player.position(), JARI_BORDES);
		if (no <= 0) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.ziarah.jauh").withStyle(ChatFormatting.GRAY));
			return true;
		}
		CompoundTag p = GunungData.pemain(player);
		int bits = p.getIntOr("zb", 0);
		if ((bits & (1 << (no - 1))) != 0) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.ziarah.sudah", no).withStyle(ChatFormatting.GRAY));
			return true;
		}
		bits |= 1 << (no - 1);
		p.putInt("zb", bits);
		GunungData.ubah(level.getServer());
		dupa.shrink(1);
		Vec3 at = GunungTata.dunia(kota, GunungTata.BORDES.get(no - 1));
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 1.0D, at.z, 4, 0.2D, 0.1D, 0.2D, 0.01D);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.CANDLE_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.8F);
		Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.ziarah.nyala", no, Integer.bitCount(bits), 9)
				.withStyle(ChatFormatting.GOLD));
		CeritaApi.peristiwa(player, "tanda", "gunung_bordes_" + no);
		return true;
	}

	/** A landing anchor with players near: a soft smoke curl over the lit landings of the climbers there. */
	static void dekatBordes(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		int no = GunungAnchors.angka(anchor, "no", 0);
		if (no <= 0 || level.getServer().getTickCount() % 60 != 0) {
			return;
		}
		for (ServerPlayer p : within) {
			if (berjalan(p) && (bordesMenyala(p) & (1 << (no - 1))) != 0 && p.distanceToSqr(anchor) < 24.0D * 24.0D) {
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, anchor.getX(), anchor.getY() + 1.2D, anchor.getZ(), 1, 0.1D, 0.0D, 0.1D, 0.005D);
				return;
			}
		}
	}

	/** The Aula Emas anchor: a climber arriving with every landing lit ends the pilgrimage. */
	static void dekatAulaEmas(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		TemaKota.Kota kota = GunungAnchors.kota(anchor);
		if (kota == null) {
			return;
		}
		for (ServerPlayer p : within) {
			if (berjalan(p) && GunungTata.diAulaEmas(kota, p.position())) {
				tiba(p, kota);
			}
		}
	}

	private static void tiba(ServerPlayer player, TemaKota.Kota kota) {
		CompoundTag p = GunungData.pemain(player);
		int bits = p.getIntOr("zb", 0);
		if (Integer.bitCount(bits & 0x1FF) < 9) {
			if (player.level().getServer().getTickCount() % 100 == 0) {
				Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.ziarah.kurang", 9 - Integer.bitCount(bits & 0x1FF))
						.withStyle(ChatFormatting.YELLOW));
			}
			return;
		}
		long now = player.level().getGameTime();
		p.putInt("zs", GunungData.Z_SELESAI);
		int kali = p.getIntOr("zn", 0) + 1;
		p.putInt("zn", kali);
		boolean restu = p.getLongOr("rj", 0L) <= now;
		if (restu) {
			p.putLong("rp", now + RESTU_TICK);
			p.putLong("rj", now + RESTU_TICK);
			Advancements.grant(player, "tempat/tema_peziarah");
		}
		GunungData.ubah(player.level().getServer());
		JEJAK.remove(player.getUUID());
		Msg.title(player, Component.translatable("kultivator.kota_tema.gunung.ziarah.tiba").withStyle(ChatFormatting.GOLD),
				Component.translatable(restu ? "kultivator.kota_tema.gunung.ziarah.restu" : "kultivator.kota_tema.gunung.ziarah.restu_jeda",
						jedaHari(player)), 10, 80, 30);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.7F);
		if (kali == 1) {
			Dao dao = Cultivators.get(player).dao();
			if (dao != null) {
				Comprehension.grant(player, dao, 2.0D);
			}
			GunungCerita.hadiah(player, "persembahan_ziarah");
		}
		if (kali >= 7) {
			Advancements.grant(player, "tempat/tema_gunung_tujuh_ziarah");
		}
		dev.kultivator.dunia.tempat.tema.bersama.TemaCerita.lapor(player, dev.kultivator.dunia.tempat.tema.bersama.TemaCerita.ZIARAH_SELESAI, GunungData.TEMA);
		CeritaApi.peristiwa(player, "tanda", "gunung_ziarah_selesai");
	}

	/** Grants a Restu Puncak without the climb (quest reward "once"); false when one is still running. */
	public static boolean restuHadiah(ServerPlayer player) {
		CompoundTag p = GunungData.pemain(player);
		long now = player.level().getGameTime();
		p.putLong("rp", Math.max(p.getLongOr("rp", 0L), now) + RESTU_TICK);
		GunungData.ubah(player.level().getServer());
		Msg.chat(player, Component.translatable("kultivator.kota_tema.gunung.ziarah.restu_hadiah").withStyle(ChatFormatting.GOLD));
		return true;
	}

	// ================================================================================================ the checks

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 11) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!berjalan(player)) {
				continue;
			}
			periksa(player);
		}
	}

	private static void periksa(ServerPlayer player) {
		CompoundTag p = GunungData.pemain(player);
		long now = player.level().getGameTime();
		if (now - p.getLongOr("zt", now) > HARI) {
			batal(player, BATAL_WAKTU);
			return;
		}
		if (player.getAbilities().flying || player.isFallFlying()) {
			batal(player, BATAL_TERBANG);
			return;
		}
		if (player.isPassenger()) {
			batal(player, BATAL_KERETA);
			return;
		}
		Jejak last = JEJAK.get(player.getUUID());
		Vec3 here = player.position();
		int melayang = 0;
		if (last != null) {
			if (last.pos().distanceToSqr(here) > 32.0D * 32.0D || player.level() != player.level()) {
				batal(player, BATAL_TELEPORT);
				return;
			}
			boolean udara = !player.onGround() && !player.isInWater() && !player.onClimbable() && player.getDeltaMovement().y > -0.3D;
			melayang = udara ? last.melayang() + 1 : 0;
			if (melayang >= 3) {
				batal(player, BATAL_TERBANG);
				return;
			}
		}
		JEJAK.put(player.getUUID(), new Jejak(here, melayang));
		if (player.level() instanceof ServerLevel level) {
			TemaKota.Kota kota = TemaKota.dari(level.getServer(), p.getLongOr("zk", 0L));
			if (kota != null && Math.hypot(here.x - kota.pusat().getX(), here.z - kota.pusat().getZ()) > 320.0D) {
				batal(player, BATAL_PERGI);
			}
		}
	}

	private Ziarah() {
	}
}
