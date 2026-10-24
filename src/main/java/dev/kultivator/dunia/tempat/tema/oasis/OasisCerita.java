package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.reruntuhan.RRewards;
import dev.kultivator.dunia.tempat.cerita.Akibat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.cerita.Syarat;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.ekonomi.SenjataKota;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.bersama.DagangItems;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The story hooks of the Kota Oasis (DESIGN 38.T.4.7, 38.S.1 data engine): the conditions, effects and quest goals
 * that {@code data/kultivator/tempat/{dialog,quest}/tema_oasis*.json} use, and the service buttons ("Jasa" tab) of the
 * oasis NPCs. Ids are prefixed {@code oasis_} so they never clash with 38ST's or another theme's.
 * <pre>
 * syarat   oasis_air_pulih | oasis_restu | oasis_festival | oasis_mural N | oasis_mural_dipesan | oasis_surat | oasis_di_kota
 *          | oasis_penumpang | oasis_kafilah_tiba | oasis_xie_pertapa
 * akibat   oasis_restu | oasis_air_suci N | oasis_badai | oasis_kafilah_quest | oasis_tidur_pelana | oasis_tari | oasis_paham N | oasis_unta_kabur
 *          | oasis_surat [selamanya] | oasis_panggil_xie | oasis_xie_pertapa | oasis_air_pulih | oasis_hadiah ekor_angin|giok_xie
 *          | oasis_panen | oasis_ajun_lari | oasis_ajun_pulang | oasis_perintah | oasis_mural_buka
 * tujuan   oasis_mural N|* | oasis_kafilah selamat|hilang|akhir|tiba|penumpang|* | oasis_formasi segel|pecah|* | oasis_bos kalajengking
 *          | oasis_tari lulus|* | oasis_tawar murah|* | oasis_temuan 0|1|2|* | oasis_kurma N | oasis_badai reda
 *          | oasis_restu dapat | oasis_unta sewa|pulang | oasis_mural_pesan * | oasis_bea bayar
 * </pre>
 */
public final class OasisCerita {

	/** The weekly orders of the Tuan Benteng (repeatable quests; one a week, picked by the week number). */
	/** Side quest of Si Belang, the camel that bolted in the storm ({@link UntaLari}). */
	public static final String QUEST_UNTA = "tema_oasis_unta_hilang";
	public static final List<String> PERINTAH = List.of("tema_oasis_perintah_reruntuhan", "tema_oasis_perintah_kafilah", "tema_oasis_perintah_buru");
	/** Days in a row the date harvest must go on for the golden dates. */
	public static final int KURMA_HARI = 5;

	static void init() {
		syarat();
		akibat();
		tujuan();
		CeritaApi.daftarJasa(OasisCerita::jasa);
	}

	// ================================================================== conditions

	private static void syarat() {
		Syarat.daftar("oasis_air_pulih", (p, a) -> {
			OasisKota.Hidup kota = OasisKota.di(p);
			return kota != null && KuilAir.pulih(p.level().getServer(), kota.situs);
		});
		Syarat.daftar("oasis_restu", (p, a) -> KuilAir.restuAktif(p));
		Syarat.daftar("oasis_festival", (p, a) -> {
			OasisKota.Hidup kota = OasisKota.di(p);
			return kota != null && TariPutar.festival(kota.situs);
		});
		Syarat.daftar("oasis_mural", (p, a) -> Mural.dibaca(p) >= angka(a, 0, 1));
		Syarat.daftar("oasis_surat", (p, a) -> Bea.suratSah(p));
		Syarat.daftar("oasis_di_kota", (p, a) -> OasisKota.di(p) != null);
		Syarat.daftar("oasis_mural_dipesan", (p, a) -> Mural.sudahPesan(p));
		Syarat.daftar("oasis_penumpang", (p, a) -> p.getVehicle() instanceof UntaKafilahEntity u && u.rombongan());
		Syarat.daftar("oasis_kafilah_tiba", (p, a) -> {
			OasisKota.Hidup kota = OasisKota.di(p);
			return kota != null && p.level() instanceof ServerLevel level
					&& KafilahUnta.diskonKafilah(level.getServer(), kota.situs, OasisKota.hari(level));
		});
		Syarat.daftar("oasis_xie_pertapa", (p, a) -> {
			OasisKota.Hidup kota = OasisKota.di(p);
			return kota != null && OasisKota.kota(p.level().getServer(), kota.situs).getBooleanOr("xie_pertapa", false);
		});
	}

	// ================================================================== effects

	private static void akibat() {
		Akibat.daftar("oasis_restu", (p, a) -> KuilAir.restu(p));
		Akibat.daftar("oasis_air_suci", (p, a) -> KuilAir.beliAirSuci(p, angka(a, 0, 1)));
		Akibat.daftar("oasis_badai", (p, a) -> {
			OasisKota.Hidup kota = OasisKota.di(p);
			if (kota != null && p.level() instanceof ServerLevel level) {
				BadaiPasir.mulai(level, kota.situs, true);
			}
		});
		Akibat.daftar("oasis_kafilah_quest", (p, a) -> {
			TempatDialog.akhiri(p);
			KafilahUnta.mulaiQuest(p);
		});
		Akibat.daftar("oasis_tidur_pelana", (p, a) -> {
			TempatDialog.akhiri(p);
			KafilahUnta.tidurPelana(p);
		});
		Akibat.daftar("oasis_tari", (p, a) -> {
			KultivatorNpc npc = TempatDialog.npc(p);
			TempatDialog.akhiri(p);
			TariPutar.mainkan(p, npc);
		});
		Akibat.daftar("oasis_surat", (p, a) -> Bea.beriSurat(p, !a.isEmpty() && "selamanya".equals(a.get(0))));
		Akibat.daftar("oasis_panggil_xie", (p, a) -> panggilXie(p));
		Akibat.daftar("oasis_xie_pertapa", (p, a) -> xiePertapa(p));
		Akibat.daftar("oasis_air_pulih", (p, a) -> airPulih(p));
		Akibat.daftar("oasis_hadiah", (p, a) -> hadiah(p, a.isEmpty() ? "" : a.get(0)));
		Akibat.daftar("oasis_panen", (p, a) -> panen(p));
		Akibat.daftar("oasis_ajun_lari", (p, a) -> ajun(p, true));
		Akibat.daftar("oasis_ajun_pulang", (p, a) -> ajun(p, false));
		Akibat.daftar("oasis_perintah", (p, a) -> perintah(p));
		Akibat.daftar("oasis_unta_kabur", (p, a) -> UntaLari.kabur(p));
		Akibat.daftar("oasis_paham", (p, a) -> {
			// chain stage 3 reward: comprehension of the player's own Dao (else the Dao of mural 7, Ice)
			dev.kultivator.data.CultivationData data = dev.kultivator.data.Cultivators.get(p);
			double got = dev.kultivator.dao.Comprehension.grant(p, data.dao() != null ? data.dao() : Dao.ICE, Math.max(1, angka(a, 0, 1)));
			if (got > 0.0D) {
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.oasis.paham", Msg.number(got)).withStyle(ChatFormatting.AQUA));
			}
		});
		Akibat.daftar("oasis_mural_buka", (p, a) -> {
			KultivatorNpc npc = TempatDialog.npc(p);
			if (npc != null) {
				TemaLayar.buka(p, TemaLayar.Mural.LAYAR, npc.getId());
			}
		});
	}

	/** Chain stage 5: Xie Duo comes out of the sand in front of the player. */
	private static void panggilXie(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(32.0D))) {
			if (OasisTokoh.adalah(npc, OasisTokoh.XIE_DUO)) {
				return;
			}
		}
		double rad = Math.toRadians(player.getYRot());
		BlockPos at = BlockPos.containing(player.getX() - Math.sin(rad) * 5.0D, player.getY(), player.getZ() + Math.cos(rad) * 5.0D);
		OasisKota.Hidup kota = OasisKota.terdekat(level, player.position(), 2000.0D);
		KultivatorNpc xie = OasisTokoh.panggil(level, kota != null ? kota.situs : 0L, OasisTokoh.XIE_DUO, at, player.getYRot() + 180.0F);
		if (xie != null) {
			xie.memory().putBoolean("hold", true);
			xie.setLonely(64.0D, 2400);
			SosokApi.gerak(xie, GerakId.SIKAP_ANGKUH, 0, 0);
			dev.kultivator.voice.Voice.say(xie, "tema.oasis.scorpion", null);
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
					net.minecraft.world.level.block.Blocks.SAND.defaultBlockState()), xie.getX(), xie.getY() + 0.5D, xie.getZ(), 40, 0.6D, 0.6D, 0.6D, 0.1D);
		}
	}

	/** Branch (b): Xie Duo gives in and becomes the hermit of the twelfth niche of the player's oasis. */
	private static void xiePertapa(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		OasisKota.Hidup kota = OasisKota.terdekat(level, player.position(), 2000.0D);
		if (kota != null) {
			OasisKota.kota(level.getServer(), kota.situs).putBoolean("xie_pertapa", true);
			OasisKota.ubah(level.getServer());
		}
		KultivatorNpc npc = TempatDialog.npc(player);
		if (npc != null && OasisTokoh.adalah(npc, OasisTokoh.XIE_DUO)) {
			SosokApi.gerak(npc, GerakId.HORMAT_DALAM, 0, 40);
			npc.setLonely(8.0D, 80);
		}
	}

	/** Chain stage 6: the lake rises, the dance night is held for the player. */
	private static void airPulih(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		OasisKota.Hidup kota = OasisKota.di(player);
		if (kota == null) {
			return;
		}
		KuilAir.naik(level, kota.situs);
		TariPutar.mulai(level, kota.situs, true);
		CeritaApi.kabar(level, kota.situs, "tema_oasis_air_pulih", player.getName().getString());
		// the 38.T.11 title advancement of inti (impossible trigger, granted by the theme that runs the deed)
		dev.kultivator.util.Advancements.grant(player, "tempat/tema_pembawa_air");
	}

	/** Unique rewards made from the existing systems (38.T.0: no new items): Ekor Angin, the jade of Xie Duo. */
	private static void hadiah(ServerPlayer player, String jenis) {
		ItemStack stack;
		switch (jenis) {
			case "ekor_angin" -> {
				stack = SenjataKota.stack(SenjataKota.Bentuk.KEBUT, SenjataKota.Mutu.ROH_MENENGAH);
				stack.set(DataComponents.CUSTOM_NAME, Component.translatable("kultivator.kota_tema.oasis.hadiah.ekor_angin").withStyle(ChatFormatting.AQUA));
				stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("kultivator.kota_tema.oasis.hadiah.ekor_angin.lore")
						.withStyle(ChatFormatting.GRAY))));
				stack.set(DataComponents.RARITY, Rarity.EPIC);
			}
			case "giok_xie" -> {
				stack = RRewards.slip(player.getRandom(), TechniqueGrade.XUAN, TechniqueGrade.DI, Dao.ICE);
				if (stack.isEmpty()) {
					stack = new ItemStack(DagangItems.GIOK_BARAT, 3);
				} else {
					stack.set(DataComponents.CUSTOM_NAME, Component.translatable("kultivator.kota_tema.oasis.hadiah.giok_xie").withStyle(ChatFormatting.AQUA));
				}
			}
			default -> {
				return;
			}
		}
		Component nama = stack.getHoverName();
		OasisKota.beri(player, stack);
		Msg.chat(player, Component.translatable("kultivator.kota_tema.oasis.hadiah.dapat", nama).withStyle(ChatFormatting.GOLD));
	}

	/** The date harvest (quest tema_oasis_kurma_emas): once a day in the date garden; the streak is the quest count. */
	private static void panen(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		OasisKota.Hidup kota = OasisKota.di(player);
		boolean diKebun = false;
		if (kota != null) {
			for (dev.kultivator.dunia.DuniaAnchorEntity a : kota.jangkar(OasisAnchors.KEBUN)) {
				int r = Math.max(8, OasisAnchors.angka(a, "r", 20));
				if (a.distanceToSqr(player) <= (double) r * r) {
					diKebun = true;
				}
			}
		}
		if (!diKebun) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.panen.jauh").withStyle(ChatFormatting.YELLOW));
			return;
		}
		MinecraftServer server = level.getServer();
		CompoundTag me = OasisKota.pemain(server, player.getUUID());
		long hari = OasisKota.hari(level);
		long last = me.getLongOr("panen_hari", Long.MIN_VALUE);
		if (last == hari) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.panen.sudah").withStyle(ChatFormatting.YELLOW));
			return;
		}
		int streak = last == hari - 1 ? me.getIntOr("panen_berturut", 0) + 1 : 1;
		me.putLong("panen_hari", hari);
		me.putInt("panen_berturut", streak);
		OasisKota.ubah(server);
		SosokApi.gerak(player, GerakId.MEMANEN, 0, 60);
		ItemStack kurma = new ItemStack(DagangItems.KURMA_EMAS, 2 + Math.min(4, streak));
		OasisKota.beri(player, kurma);
		Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.panen.dapat", streak, KURMA_HARI).withStyle(ChatFormatting.GOLD));
		QuestTracker.peristiwa(player, "oasis_kurma", Integer.toString(streak));
	}

	/** Ajun runs away (branch c) or comes home after being found in the storm. */
	private static void ajun(ServerPlayer player, boolean lari) {
		KultivatorNpc npc = TempatDialog.npc(player);
		if (npc == null || !OasisTokoh.adalah(npc, OasisTokoh.AJUN)) {
			if (player.level() instanceof ServerLevel level) {
				for (KultivatorNpc n : level.getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(24.0D))) {
					if (OasisTokoh.adalah(n, OasisTokoh.AJUN)) {
						npc = n;
					}
				}
			}
		}
		if (npc == null) {
			return;
		}
		npc.memory().putBoolean("tersesat", false);
		if (lari) {
			SosokApi.gerak(npc, GerakId.LARI_TAKUT, 0, 60);
			Vec3 away = npc.position().add(npc.position().subtract(player.position()).normalize().scale(16.0D));
			npc.memory().putBoolean("hold", false);
			npc.getNavigation().moveTo(away.x, away.y, away.z, 1.2D);
			npc.setLonely(4.0D, 60);
		} else {
			SosokApi.gerak(npc, GerakId.LAMBAI, 0, 40);
			npc.setLonely(8.0D, 100);
		}
	}

	/** Perintah Benteng: this week's order (one of three repeatable quests). */
	private static void perintah(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		OasisKota.Hidup kota = OasisKota.di(player);
		if (kota == null) {
			return;
		}
		long minggu = Math.floorDiv(OasisKota.hari(level), 7L);
		String id = PERINTAH.get((int) Math.floorMod(minggu + (kota.situs & 0xFF), (long) PERINTAH.size()));
		CompoundTag me = OasisKota.pemain(level.getServer(), player.getUUID());
		if (me.getLongOr("perintah_minggu", Long.MIN_VALUE) == minggu) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.perintah.sudah").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (QuestTracker.mulai(player, id, kota.situs)) {
			me.putLong("perintah_minggu", minggu);
			OasisKota.ubah(level.getServer());
		}
	}

	// ================================================================== quest goals

	private static void tujuan() {
		// "oasis_kafilah akhir": the quest caravan ended either way (selamat or hilang; the flag oasis.kafilah_hilang says which)
		QuestTracker.daftarTujuan("oasis_kafilah", (a, j, id) -> j.equals("oasis_kafilah") && (a.isEmpty() || "*".equals(a.get(0))
				|| a.get(0).equals(id) || "akhir".equals(a.get(0)) && ("selamat".equals(id) || "hilang".equals(id))));
		for (String jenis : List.of("oasis_mural", "oasis_formasi", "oasis_bos", "oasis_tari", "oasis_tawar", "oasis_temuan",
				"oasis_badai", "oasis_restu", "oasis_unta", "oasis_mural_pesan", "oasis_bea")) {
			QuestTracker.daftarTujuan(jenis, (a, j, id) -> j.equals(jenis) && (a.isEmpty() || "*".equals(a.get(0)) || a.get(0).equals(id)));
		}
		QuestTracker.daftarTujuan("oasis_kurma", (a, j, id) -> j.equals("oasis_kurma") && angka(id, 0) >= (a.isEmpty() ? KURMA_HARI : angka(a, 0, KURMA_HARI)));
	}

	// ================================================================== service buttons

	private static List<TempatDialog.JasaEntri> jasa(ServerPlayer player, KultivatorNpc npc) {
		List<TempatDialog.JasaEntri> out = new ArrayList<>();
		if (!(npc.level() instanceof ServerLevel level) || OasisKota.terdekat(level, npc.position(), OasisKota.JANGKAU_KOTA) == null) {
			return out;
		}
		String kios = npc.memory().getStringOr(Tawar.KIOS, "");
		if (Tawar.pedagang(npc) && !"kandang".equals(kios) && !"kurma".equals(kios)) {
			out.add(entri(TemaLayar.Tawar.LAYAR, "tawar", LembarKunci.Ikon.TIMBANGAN));
		}
		if (npc.role() == NpcRole.KAFILAH_BASHI || npc.role() == NpcRole.PENUNTUN_UNTA || OasisTokoh.adalah(npc, OasisTokoh.BULAT_KHAN)) {
			out.add(entri(TemaLayar.Kafilah.LAYAR, "kafilah", LembarKunci.Ikon.KAFILAH));
		}
		if (npc.role() == NpcRole.PELUKIS_GUA || OasisTokoh.adalah(npc, OasisTokoh.MO_HUA)) {
			out.add(entri(TemaLayar.Mural.LAYAR, "mural", LembarKunci.Ikon.PERPUSTAKAAN));
		}
		return out;
	}

	private static TempatDialog.JasaEntri entri(String layar, String nama, int ikon) {
		return new TempatDialog.JasaEntri(layar, Component.translatable("kultivator.kota_tema.oasis.jasa." + nama), ikon,
				Component.translatable("kultivator.kota_tema.oasis.jasa." + nama + ".catatan"), false, null);
	}

	// ================================================================== helpers

	private static int angka(List<String> a, int i, int fallback) {
		return i < a.size() ? angka(a.get(i), fallback) : fallback;
	}

	private static int angka(String s, int fallback) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private OasisCerita() {
	}
}
