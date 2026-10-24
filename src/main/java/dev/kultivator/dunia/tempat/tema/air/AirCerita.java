package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.Akibat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.cerita.Syarat;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The story hooks of the Kota Air (DESIGN 38.T.5.5) for 38ST's engine: quest goal kinds, conditions and effects of the
 * data files {@code tempat/quest/tema_air_*.json} and {@code tempat/dialog/tema_air_*.json}, the "Jasa" entries of the
 * named characters (their screens), the clue hunts of the chain and the side quest of the child in the canal.
 * <p>
 * Goal kinds (event {@code <kind> <id>}; a goal {@code <kind>} matches any id, {@code <kind> <id>} only that one):
 * {@link #TUJUAN}. Effects: {@code air_sewa}, {@code air_tur} (Bibi Chuan's boat hire / tour), {@code air_lentera_beli},
 * {@code air_jual_daun}, {@code air_ulat <sakit|pulih>}, {@code air_lepas_lao_shu}. Conditions: {@code air_ulat_sakit},
 * {@code air_malam} (time of day 13000-23000), {@code air_misi <jenis>}, {@code air_bingkisan}, {@code air_tanpa_tikus},
 * {@code air_tanpa_arwah}. Effects also {@code air_misi_selesai <jenis>}, {@code air_bingkisan [ambil]},
 * {@code air_jubah_merah}, {@code air_anugerah <advancement>}.
 * <p>
 * Chain {@code tema_air_benang} stage ids (data {@code tempat/quest/tema_air_benang.json}; the code reads the marked ones):
 * {@code ulat}* (inspect a mulberry leaf in the orchard and fill a bottle at the canal of Rumah Sutra), {@code lapor_su},
 * {@code noda}* (the night tour: Bibi Chuan shows the black stain near the market), {@code minyak} (Lu Yuqing names the
 * Minyak Bayangan), {@code kejar}* (Lao Shu flees; the raid is called that night), {@code lao_shu}, {@code wen_shi},
 * {@code arwah}* (three Arwah Kanal at the lake at night), {@code temui}* (Xiu Niang at the Paviliun Bulan di Air),
 * {@code kain}* (weave the Kain Xiu Niang with Su Jin'er; Air Suci from the shrine basin), {@code lepas}* (release it on
 * the next lantern night), endings {@code tenang} / {@code sepi}.
 */
public final class AirCerita {

	public static final String QUEST = "tema_air_benang";
	public static final String QUEST_ANAK = "tema_air_anak_jembatan";
	public static final List<String> TUJUAN = List.of("air_tur", "air_perahu", "air_lukisan", "air_tenun", "air_kain", "air_kain_lepas",
			"air_petik", "air_upacara", "air_bait", "air_bait_tua", "air_lentera", "air_arwah", "air_naga_menang", "air_naga_ikut",
			"air_tikus", "air_anak", "air_beli_sutra", "air_periksa", "air_noda");
	/** The child of the side quest waits in the water this long before others pull him out. */
	private static final int ANAK_SABAR = 2400;
	/** Reach of the shrine basin for the blessed water of stage {@code kain}. */
	private static final double JARAK_KUIL = 8.0D;

	private static final Map<Long, Long> ANAK_HARI = new HashMap<>();
	private static final Map<UUID, KultivatorNpc> ANAK = new HashMap<>();

	static void init() {
		for (String kind : TUJUAN) {
			QuestTracker.daftarTujuan(kind, (args, jenis, id) -> jenis.equals(kind) && (args.isEmpty() || "*".equals(args.getFirst())
					|| args.getFirst().equals(id)));
		}
		Akibat.daftar("air_sewa", (p, a) -> {
			TempatDialog.akhiri(p);
			Perahu.sewa(p);
		});
		Akibat.daftar("air_tur", (p, a) -> {
			KultivatorNpc guide = TempatDialog.npc(p);
			TempatDialog.akhiri(p);
			Perahu.mulaiTur(p, AirTokoh.adalah(guide, AirTokoh.BIBI_CHUAN) ? guide : null);
		});
		Akibat.daftar("air_lentera_beli", (p, a) -> LenteraAir.beli(p));
		Akibat.daftar("air_jual_daun", (p, a) -> PaviliunTeh.jualDaun(p));
		Akibat.daftar("air_ulat", (p, a) -> {
			AirKota.Hidup kota = AirKota.di(p);
			if (kota != null) {
				AirKota.kota(p.level().getServer(), kota.situs).putBoolean("ulat_pulih", !a.isEmpty() && "pulih".equals(a.getFirst()));
				AirKota.ubah(p.level().getServer());
			}
		});
		Akibat.daftar("air_lepas_lao_shu", (p, a) -> {
			KultivatorNpc npc = TempatDialog.npc(p);
			if (AirTokoh.adalah(npc, AirTokoh.LAO_SHU)) {
				Air.nanti(p.level().getServer(), 40, npc::vanish);
			}
		});
		Akibat.daftar("air_jubah_merah", (p, a) -> dev.kultivator.dunia.tempat.ekonomi.Barang.beri(p,
				RumahSutra.jubah(dev.kultivator.dunia.tempat.tema.TemaItems.JUBAH_BENANG_MERAH, net.minecraft.world.item.DyeColor.RED, "benang_merah")));
		Akibat.daftar("air_anugerah", (p, a) -> {
			if (!a.isEmpty() && a.getFirst().startsWith("tema_air_")) {
				Advancements.grant(p, "tempat/" + a.getFirst());
			}
		});
		// guild missions of inti's TemaMisi run here: antar_sutra (House Su's parcel to the tea house)
		Syarat.daftar("air_misi", (p, a) -> !a.isEmpty() && EkonomiApi.misiAktif(p, a.getFirst()));
		Akibat.daftar("air_misi_selesai", (p, a) -> {
			if (!a.isEmpty() && EkonomiApi.misiAktif(p, a.getFirst())) {
				EkonomiApi.misiSelesai(p, a.getFirst());
			}
		});
		// House Su's silk parcel of mission antar_sutra: a bundle tagged {kultivator_air: "bingkisan_sutra"}
		Syarat.daftar("air_bingkisan", (p, a) -> hitungBingkisan(p) > 0);
		Akibat.daftar("air_bingkisan", (p, a) -> {
			if (!a.isEmpty() && "ambil".equals(a.getFirst())) {
				ambilBingkisan(p);
			} else if (hitungBingkisan(p) == 0) {
				dev.kultivator.dunia.tempat.ekonomi.Barang.beri(p, bingkisan());
			}
		});
		// the chain never waits on a switched-off danger: stage kejar needs the Tikus Air, stage arwah the Arwah Kanal
		Syarat.daftar("air_tanpa_tikus", (p, a) -> !TemaConfig.inti().bahaya || !AirKota.config().tikusAir);
		Syarat.daftar("air_tanpa_arwah", (p, a) -> !TemaConfig.inti().bahaya);
		Syarat.daftar("air_ulat_sakit", (p, a) -> {
			AirKota.Hidup kota = AirKota.di(p);
			return kota != null && !AirKota.kota(p.level().getServer(), kota.situs).getBooleanOr("ulat_pulih", false);
		});
		Syarat.daftar("air_malam", (p, a) -> {
			long t = AirKota.jam(p.level().getServer().overworld());
			return t >= 13000L && t < 23000L;
		});
		CeritaApi.daftarJasa(AirCerita::jasa);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
				return InteractionResult.PASS;
			}
			if (level.getBlockState(hit.getBlockPos()).is(Blocks.AZALEA_LEAVES) && tahap(sp, "ulat")) {
				AirKota.Hidup kota = AirKota.di(sl, hit.getBlockPos());
				if (kota != null && "rumah_sutra".equals(kota.distrik(hit.getBlockPos()))) {
					Msg.chat(sp, Component.translatable("kultivator.kota_tema.air.benang.daun").withStyle(ChatFormatting.GRAY));
					peristiwa(sp, "air_periksa", "daun");
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl) || !player.getItemInHand(hand).is(Items.GLASS_BOTTLE)
					|| !tahap(sp, "ulat") && !tahap(sp, "kain")) {
				return InteractionResult.PASS;
			}
			if (tahap(sp, "kain") && airSuciKuil(sl, sp, hand)) {
				return InteractionResult.SUCCESS;
			}
			HitResult hit = player.pick(5.0D, 0.0F, true);
			if (tahap(sp, "ulat") && hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK && sl.getFluidState(bh.getBlockPos()).isSource()) {
				AirKota.Hidup kota = AirKota.di(sl, bh.getBlockPos());
				if (kota != null && "rumah_sutra".equals(kota.distrik(bh.getBlockPos()))) {
					Msg.chat(sp, Component.translatable("kultivator.kota_tema.air.benang.air").withStyle(ChatFormatting.GRAY));
					peristiwa(sp, "air_periksa", "air");
				}
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(entity instanceof KultivatorNpc npc)
					|| !"anak".equals(npc.memory().getStringOr("air_anak", ""))) {
				return InteractionResult.PASS;
			}
			npc.setCompanion(sp.getUUID(), sp.level().getGameTime() + 2400L);
			npc.memory().putString("air_anak_ikut", sp.getUUID().toString());
			npc.say(Component.translatable("kultivator.kota_tema.air.anak.pegang"), 60);
			Msg.actionBar(sp, Component.translatable("kultivator.kota_tema.air.anak.bawa").withStyle(ChatFormatting.AQUA));
			return InteractionResult.SUCCESS;
		});
	}

	static void reset() {
		ANAK_HARI.clear();
		ANAK.clear();
		XIU.clear();
	}

	/**
	 * Chain stage {@code kain} without Air Suci from the Kota Oasis: a glass bottle filled at the basin of the little
	 * shrine of the Water God by the lake (within {@value #JARAK_KUIL} blocks of the {@code kuil_air} anchor) is blessed
	 * once per player (flag {@code air_suci_kuil}).
	 */
	private static boolean airSuciKuil(ServerLevel level, ServerPlayer player, InteractionHand hand) {
		AirKota.Hidup kota = AirKota.di(player);
		if (kota == null || CeritaApi.bendera(player, "air_suci_kuil")) {
			return false;
		}
		var shrine = kota.satu(AirAnchors.KUIL_AIR);
		if (shrine == null || shrine.distanceToSqr(player) > JARAK_KUIL * JARAK_KUIL) {
			return false;
		}
		player.getItemInHand(hand).shrink(1);
		dev.kultivator.dunia.tempat.ekonomi.Barang.beri(player, new net.minecraft.world.item.ItemStack(dev.kultivator.dunia.tempat.tema.TemaItems.AIR_SUCI));
		CeritaApi.setBendera(player, "air_suci_kuil", true);
		level.sendParticles(ParticleTypes.END_ROD, shrine.getX(), shrine.getY() + 1.2D, shrine.getZ(), 14, 0.4D, 0.5D, 0.4D, 0.02D);
		level.playSound(null, shrine.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.9F, 1.1F);
		Msg.chat(player, Component.translatable("kultivator.kota_tema.air.benang.air_suci").withStyle(ChatFormatting.AQUA));
		return true;
	}

	private static final String TAG_AIR = "kultivator_air";
	private static final String BINGKISAN = "bingkisan_sutra";

	/** A fresh parcel (made only when given, never in a static initialiser). */
	private static net.minecraft.world.item.ItemStack bingkisan() {
		net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(Items.BUNDLE);
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putString(TAG_AIR, BINGKISAN);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
				Component.translatable("kultivator.kota_tema.air.bingkisan").withStyle(ChatFormatting.LIGHT_PURPLE));
		stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
				List.of(Component.translatable("kultivator.kota_tema.air.bingkisan.lore").withStyle(ChatFormatting.GRAY))));
		return stack;
	}

	private static boolean adalahBingkisan(net.minecraft.world.item.ItemStack stack) {
		if (!stack.is(Items.BUNDLE)) {
			return false;
		}
		net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		return data != null && BINGKISAN.equals(data.copyTag().getStringOr(TAG_AIR, ""));
	}

	private static int hitungBingkisan(ServerPlayer player) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (adalahBingkisan(player.getInventory().getItem(i))) {
				n += player.getInventory().getItem(i).getCount();
			}
		}
		return n;
	}

	private static void ambilBingkisan(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (adalahBingkisan(player.getInventory().getItem(i))) {
				player.getInventory().getItem(i).shrink(1);
				return;
			}
		}
	}

	/** A quest event of this theme (38ST {@code QuestTracker.peristiwa}). */
	static void peristiwa(ServerPlayer player, String jenis, String id) {
		QuestTracker.peristiwa(player, jenis, id);
	}

	/** True while the player stands at stage {@code stage} of the chain. */
	static boolean tahap(ServerPlayer player, String stage) {
		return QuestTracker.aktif(player, QUEST) && stage.equals(QuestTracker.simpul(player, QUEST));
	}

	/** The tour passed a place: at stage {@code noda} at night Bibi Chuan shows the black stain near the market. */
	static void turLewat(ServerPlayer player, String place, ServerLevel level, KultivatorNpc guide) {
		long t = AirKota.jam(level);
		boolean night = t >= 13000L && t < 23000L;
		if ("pasar_air".equals(place) && night && tahap(player, "noda")) {
			Msg.chat(player, Component.translatable("kultivator.kota_tema.air.benang.noda").withStyle(ChatFormatting.DARK_PURPLE));
			if (guide != null && !guide.isRemoved()) {
				guide.say(Component.translatable("kultivator.kota_tema.air.benang.noda.kata"), 120);
				HidupApi.klip(guide, GerakId.id("tunjuk"), 0, 60);
			}
			peristiwa(player, "air_noda", "lihat");
		}
	}

	// ================================================================== Xiu Niang at the Paviliun Bulan di Air (stage temui)

	private static final Map<UUID, KultivatorNpc> XIU = new HashMap<>();

	/** Every 20 ticks: at night, a player at stage {@code temui} near the pavilion on the garden pond meets Xiu Niang. */
	static void xiuNiang(ServerLevel level) {
		long t = AirKota.jam(level);
		boolean night = t >= 13000L && t < 23000L;
		for (var it = XIU.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
			// she stays through the talk that moves the chain on to stage kain (until the player's choice sets air_xiu_putus)
			boolean bicara = p != null && (tahap(p, "temui") || tahap(p, "kain") && !CeritaApi.bendera(p, "air_xiu_putus"));
			if (e.getValue().isRemoved() || p == null || !night || !bicara || p.distanceToSqr(e.getValue()) > 64.0D * 64.0D) {
				if (!e.getValue().isRemoved()) {
					e.getValue().vanish();
				}
				it.remove();
			}
		}
		if (!night) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (XIU.containsKey(p.getUUID()) || !tahap(p, "temui")) {
				continue;
			}
			AirKota.Hidup kota = AirKota.di(p);
			if (kota == null) {
				continue;
			}
			for (Titik t2 : AirKota.titik(level, kota, "pandang")) {
				if (!"bulan_di_air".equals(t2.data().getOrDefault("nama", "")) || t2.pos().distSqr(p.blockPosition()) > 24.0D * 24.0D) {
					continue;
				}
				KultivatorNpc xiu = AirTokoh.panggil(level, kota.situs, AirTokoh.XIU_NIANG, t2.pos(), t2.hadap().getOpposite().toYRot());
				if (xiu != null) {
					xiu.memory().putBoolean("hold", true);
					if (kota.town != 0L) {
						xiu.setTown(kota.town);
					}
					level.sendParticles(ParticleTypes.SOUL, xiu.getX(), xiu.getY() + 1.0D, xiu.getZ(), 20, 0.4D, 0.8D, 0.4D, 0.02D);
					level.playSound(null, xiu.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.NEUTRAL, 0.9F, 0.7F);
					xiu.say(Component.translatable("kultivator.kota_tema.air.xiu.muncul"), 100);
					XIU.put(p.getUUID(), xiu);
					Msg.chat(p, Component.translatable("kultivator.kota_tema.air.xiu.lentera").withStyle(ChatFormatting.DARK_AQUA));
				}
				break;
			}
		}
	}

	// ================================================================== services of the named characters

	private static List<TempatDialog.JasaEntri> jasa(ServerPlayer player, KultivatorNpc npc) {
		List<TempatDialog.JasaEntri> out = new ArrayList<>();
		String id = AirTokoh.dari(npc);
		switch (id) {
			case AirTokoh.SU_JIN, AirTokoh.SU_JINER -> out.add(new TempatDialog.JasaEntri(TemaLayar.Sutra.LAYAR,
					Component.translatable("kultivator.kota_tema.air.jasa.sutra"), 0, Component.translatable("kultivator.kota_tema.air.jasa.sutra.catatan"),
					false, null));
			case AirTokoh.LU_YUQING -> out.add(new TempatDialog.JasaEntri(TemaLayar.Teh.LAYAR,
					Component.translatable("kultivator.kota_tema.air.jasa.teh"), 0, Component.translatable("kultivator.kota_tema.air.jasa.teh.catatan"),
					false, null));
			case AirTokoh.WEN_SHI -> out.add(new TempatDialog.JasaEntri(TemaLayar.Bait.LAYAR,
					Component.translatable("kultivator.kota_tema.air.jasa.bait"), 0, Component.translatable("kultivator.kota_tema.air.jasa.bait.catatan"),
					false, null));
			default -> {
			}
		}
		return out;
	}

	// ================================================================== the child in the canal (tema_air_anak_jembatan)

	/** Every 20 ticks: Xiu Niang's meeting; in heavy rain, once a world day per town, a child slips off a bridge. */
	static void tick(ServerLevel level) {
		if (!AirKota.aktif()) {
			return;
		}
		if (TemaConfig.inti().alurCerita) {
			xiuNiang(level);
		}
		if (!TemaConfig.inti().bahaya) {
			return;
		}
		long now = level.getGameTime();
		// children already in the water: saved at the washing steps
		for (var it = ANAK.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			KultivatorNpc child = e.getValue();
			if (child.isRemoved()) {
				it.remove();
				continue;
			}
			String who = child.memory().getStringOr("air_anak_ikut", "");
			ServerPlayer hero = who.isEmpty() ? null : level.getServer().getPlayerList().getPlayer(UUID.fromString(who));
			AirKota.Hidup kota = AirKota.cari(child.memory().getLongOr("air_situs", 0L));
			if (hero != null && kota != null && diTanggaCuci(level, kota, child.blockPosition())) {
				selamat(level, kota, child, hero);
				it.remove();
				continue;
			}
			if (now - child.memory().getLongOr("air_jatuh", now) > ANAK_SABAR) {
				child.vanish();
				it.remove();
			}
		}
		if (now % 200L != 0L || !level.isRaining()) {
			return;
		}
		for (AirKota.Hidup kota : AirKota.semua()) {
			long hari = AirKota.hari(level);
			if (ANAK_HARI.getOrDefault(kota.situs, -1L) == hari || !level.isThundering() && level.getRandom().nextInt(4) != 0) {
				continue;
			}
			for (ServerPlayer p : level.players()) {
				if (p.isSpectator() || kota.jarak(p.position()) > AirKota.JANGKAU_KOTA || Sibuk.why(p) != null || Perahu.diPerahu(p)) {
					continue;
				}
				if (jatuhkan(level, kota, p)) {
					ANAK_HARI.put(kota.situs, hari);
					return;
				}
			}
		}
	}

	/** A child slips into the canal near {@code p}; false when no water is near or the body cap is full. */
	private static boolean jatuhkan(ServerLevel level, AirKota.Hidup kota, ServerPlayer p) {
		Vec3 water = airDekat(level, kota, p);
		if (water == null) {
			return false;
		}
		KultivatorNpc child = HidupApi.tubuh(level, kota.situs, NpcRole.ANAK, BlockPos.containing(water), 0.0F);
		if (child == null) {
			return false;
		}
		child.memory().putString("air_anak", "anak");
		child.memory().putLong("air_situs", kota.situs);
		child.memory().putLong("air_jatuh", level.getGameTime());
		child.say(Component.translatable("kultivator.kota_tema.air.anak.tolong"), 100);
		level.playSound(null, child.blockPosition(), SoundEvents.PLAYER_SPLASH, SoundSource.NEUTRAL, 1.0F, 1.3F);
		ANAK.put(child.getUUID(), child);
		Msg.chat(p, Component.translatable("kultivator.kota_tema.air.anak.jatuh").withStyle(ChatFormatting.RED));
		QuestTracker.mulai(p, QUEST_ANAK, kota.situs);
		return true;
	}

	/** Test command: the child falls in now, rain or not. */
	static void paksaAnak(ServerPlayer p) {
		AirKota.Hidup kota = AirKota.di(p);
		if (kota != null && p.level() instanceof ServerLevel level) {
			jatuhkan(level, kota, p);
		}
	}

	private static Vec3 airDekat(ServerLevel level, AirKota.Hidup kota, ServerPlayer p) {
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0D;
			for (int d = 4; d <= 12; d += 4) {
				Vec3 at = p.position().add(Math.cos(a) * d, 0.0D, Math.sin(a) * d);
				if (Perahu.air(level, kota, at)) {
					return new Vec3(at.x, kota.lantai() - 2, at.z);
				}
			}
		}
		return null;
	}

	private static boolean diTanggaCuci(ServerLevel level, AirKota.Hidup kota, BlockPos pos) {
		for (Titik t : AirKota.titik(level, kota, "kerja:sapu")) {
			if ("cuci".equals(t.data().getOrDefault("nama", "")) && t.pos().distSqr(pos) <= 9.0D) {
				return true;
			}
		}
		return false;
	}

	private static void selamat(ServerLevel level, AirKota.Hidup kota, KultivatorNpc child, ServerPlayer hero) {
		child.setCompanion(null, 0L);
		child.say(Component.translatable("kultivator.kota_tema.air.anak.terima_kasih"), 100);
		HidupApi.klip(child, GerakId.id("sujud_guru"), 0, 60);
		level.sendParticles(ParticleTypes.HEART, child.getX(), child.getY() + 1.4D, child.getZ(), 5, 0.3D, 0.3D, 0.3D, 0.0D);
		AirKota.tambahNamaBaik(hero, kota, 3);
		Msg.chat(hero, Component.translatable("kultivator.kota_tema.air.anak.selamat").withStyle(ChatFormatting.GOLD));
		Advancements.grant(hero, "tempat/tema_air_anak");
		peristiwa(hero, "air_anak", "selamat");
		Air.nanti(level.getServer(), 200, child::vanish);
	}

	private AirCerita() {
	}
}
