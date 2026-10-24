package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.dimensi.DimensiEntities;
import dev.kultivator.dunia.dimensi.DimensiLevels;
import dev.kultivator.dunia.dimensi.laut.LautNpcEntity;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.cerita.AdeganPayload;
import dev.kultivator.dunia.tempat.cerita.Akibat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.cerita.Syarat;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.SenjataKota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The story hooks of the Kota Pelabuhan (DESIGN 38.T.3.5, 38.S.1 data engine): the conditions, effects and quest goals
 * that {@code data/kultivator/tempat/{dialog,quest}/tema_pelabuhan*.json} use. Ids are prefixed {@code pelabuhan_} so
 * they never clash with 38ST's or another theme's.
 * <pre>
 * syarat  pelabuhan_pelita N | pelabuhan_di_kota | pelabuhan_malam | pelabuhan_festival | pelabuhan_damai
 *         | pelabuhan_bajak_berhenti | pelabuhan_lunas_pesan | pelabuhan_lunas_siap | pelabuhan_heisha_menyerah
 *         | pelabuhan_bintang_malam N
 * akibat  pelabuhan_pelita N | pelabuhan_serbuan | pelabuhan_heisha | pelabuhan_heisha_musuh | pelabuhan_heisha_ramah
 *         | pelabuhan_nasib serah|damai|tumpas | pelabuhan_upacara | pelabuhan_bintang | pelabuhan_namai NAMA...
 *         | pelabuhan_hadiah gigi_hiu|astrolab|jimat | pelabuhan_kaca_ibrahim | pelabuhan_lomba_latih
 * tujuan  pelabuhan_suar lihat | pelabuhan_dokumen benar|salah | pelabuhan_racik lensa | pelabuhan_bajak menang|kalah
 *         | pelabuhan_heisha kalah | pelabuhan_bintang malam | pelabuhan_lomba juara|ikut|kalahkan_alan
 *         | pesan_lunas perahu|sekte | lunas perahu|sekte       (all accept "*" for any)
 * </pre>
 * Goal {@code pelabuhan_heisha kalah} is also met by a won friendly Duel Pedang against him (event {@code duel
 * tokoh:hei_sha}, the "talk first" route of bab 6 opened by {@code pelabuhan_heisha_ramah}).
 * The chain {@code tema_pelabuhan_pelita} ("Pelita yang Padam", 7 chapters in 9 stages): the dim lamp (Kakek Deng, the
 * cracked lens, Syahbandar Lin Haichao), the forged manifest (the {@code tema_dokumen} puzzle; Zhao Wanli's bribe sets
 * {@code pelabuhan_suap}), the sea-salt glass (the {@code tema_racik} puzzle at the shipyard, or Ibrahim's glass after a
 * night under the stars), the raid of Kapten Hiu Hitam ({@link SerbuanBajakLaut}), the Mutiara Fajar of the Boundless Sea
 * (free crossing; bought from an island merchant or found in a sea serpent), the duel on the breakwater (Hei Sha yields at
 * a fifth of his strength) and his fate (handed to Lin, reconciled with his old master, or cut down), the relit lamp.
 */
public final class PelabuhanCerita {

	public static final String QUEST = "tema_pelabuhan_pelita";
	/** NPC memory: cannot be hurt. */
	public static final String KEBAL = "pelabuhan_kebal";
	public static final long HARGA_MUTIARA_FAJAR = 200L;
	public static final long HARGA_KACA_IBRAHIM = 300L;
	/** A star night: this many ticks at the top of the Pagoda Penunjuk after dark. */
	public static final int BINTANG_TICK = 600;

	/** Hei Sha bodies per player (summoned by the chain only). */
	private static final Map<UUID, KultivatorNpc> HEI_SHA = new HashMap<>();
	/** Star-watch progress per player in ticks this night. */
	private static final Map<UUID, Integer> BINTANG = new HashMap<>();
	/** Ceremonies running (town id -> game time they end). */
	private static final Map<Long, Long> UPACARA = new HashMap<>();
	/** Pagoda tops per town (cached from the template points). */
	private static final Map<Long, BlockPos> PAGODA = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		syarat();
		akibat();
		tujuan();
		ServerTickEvents.END_SERVER_TICK.register(PelabuhanCerita::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HEI_SHA.clear();
			BINTANG.clear();
			UPACARA.clear();
			PAGODA.clear();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(PelabuhanCerita::mati);
		// Hei Sha cannot be hurt while he waits, watches from his deck or has yielded (memory "kebal")
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof KultivatorNpc npc) || !PelabuhanTokoh.adalah(npc, PelabuhanTokoh.HEI_SHA)) {
				return true;
			}
			String alur = npc.memory().getStringOr("alur", "");
			if (npc.memory().getBooleanOr(KEBAL, false) || "tunggu".equals(alur) || "menyerah".equals(alur)) {
				return false;
			}
			if ("musuh".equals(alur) && npc.getHealth() - amount <= npc.getMaxHealth() * 0.2F) {
				// the blow that would fell him leaves him on one knee instead (he yields on the next check)
				npc.setHealth(npc.getMaxHealth() * 0.2F);
				return false;
			}
			return true;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && entity instanceof LautNpcEntity npc && npc.role() == LautNpcEntity.PEDAGANG
					&& sp.isShiftKeyDown() && QuestTracker.aktif(sp, QUEST) && "mutiara".equals(QuestTracker.simpul(sp, QUEST))) {
				beliMutiaraFajar(sp);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	// ================================================================== conditions

	private static void syarat() {
		Syarat.daftar("pelabuhan_pelita", (p, a) -> {
			PelabuhanKota.Hidup kota = PelabuhanKota.di(p);
			return kota != null && Suar.pelita(p.level().getServer(), kota.situs) >= angka(a, 0, 1);
		});
		Syarat.daftar("pelabuhan_di_kota", (p, a) -> PelabuhanKota.di(p) != null);
		Syarat.daftar("pelabuhan_malam", (p, a) -> {
			long t = PelabuhanKota.jam(p.level());
			return t >= 13000L && t < 23000L;
		});
		Syarat.daftar("pelabuhan_festival", (p, a) -> {
			PelabuhanKota.Hidup kota = PelabuhanKota.di(p);
			return kota != null && HariDewiLaut.berjalan(kota.situs);
		});
		Syarat.daftar("pelabuhan_damai", (p, a) -> {
			PelabuhanKota.Hidup kota = PelabuhanKota.di(p);
			return kota != null && PelabuhanKota.kota(p.level().getServer(), kota.situs).getBooleanOr("hei_sha_damai", false);
		});
		Syarat.daftar("pelabuhan_bajak_berhenti", (p, a) -> {
			PelabuhanKota.Hidup kota = PelabuhanKota.di(p);
			return kota != null && SerbuanBajakLaut.berhenti(p.level().getServer(), kota.situs);
		});
		Syarat.daftar("pelabuhan_lunas_pesan", (p, a) -> {
			PelabuhanKota.Hidup kota = PelabuhanKota.di(p);
			return kota != null && Galangan.pesanan(p.level().getServer(), kota.situs, p.getUUID()) != null;
		});
		Syarat.daftar("pelabuhan_lunas_siap", (p, a) -> {
			PelabuhanKota.Hidup kota = PelabuhanKota.di(p);
			Galangan.Pesanan order = kota == null ? null : Galangan.pesanan(p.level().getServer(), kota.situs, p.getUUID());
			return order != null && order.siap(PelabuhanKota.hari(p.level()));
		});
		Syarat.daftar("pelabuhan_bintang_malam", (p, a) ->
				PelabuhanKota.pemain(p.level().getServer(), p.getUUID()).getIntOr("bintang_malam", 0) >= angka(a, 0, 1));
		Syarat.daftar("pelabuhan_heisha_menyerah", (p, a) -> {
			KultivatorNpc hei = HEI_SHA.get(p.getUUID());
			return hei != null && hei.isAlive() && "menyerah".equals(hei.memory().getStringOr("alur", ""));
		});
	}

	// ================================================================== effects

	private static void akibat() {
		Akibat.daftar("pelabuhan_pelita", (p, a) -> {
			PelabuhanKota.Hidup kota = PelabuhanKota.di(p);
			if (kota != null) {
				Suar.setPelita(p.level().getServer(), kota.situs, angka(a, 0, Suar.UTUH));
			}
		});
		Akibat.daftar("pelabuhan_serbuan", (p, a) -> {
			PelabuhanKota.Hidup kota = PelabuhanKota.di(p);
			if (kota != null) {
				int waves = 3 + (CeritaApi.bendera(p, "pelabuhan_suap") ? 1 : 0);
				SerbuanBajakLaut.siapkanQuest(p.level().getServer(), kota.situs, p, waves);
			}
		});
		Akibat.daftar("pelabuhan_heisha", (p, a) -> panggilHeiSha(p));
		Akibat.daftar("pelabuhan_heisha_musuh", (p, a) -> {
			KultivatorNpc hei = HEI_SHA.get(p.getUUID());
			if (hei != null && hei.isAlive()) {
				hei.memory().putString("alur", "musuh");
				hei.memory().putBoolean("hold", false);
				hei.markHostile(p);
				hei.setTarget(p);
				if (VoiceEvents.exists("tema.pelabuhan.black_shark")) {
					Voice.say(hei, "tema.pelabuhan.black_shark", null);
				}
			}
		});
		// "talk first" (bab 6): he hears his master's tale and accepts a friendly Duel Pedang (no lethal fight)
		Akibat.daftar("pelabuhan_heisha_ramah", (p, a) -> {
			KultivatorNpc hei = HEI_SHA.get(p.getUUID());
			if (hei != null && hei.isAlive()) {
				hei.memory().putString("alur", "ramah");
				hei.memory().putBoolean(KEBAL, false);
				hei.memory().putBoolean("hold", true);
				HidupApi.klip(hei, GerakId.HORMAT, 0, 40);
			}
		});
		Akibat.daftar("pelabuhan_nasib", (p, a) -> nasib(p, a.isEmpty() ? "serah" : a.get(0)));
		Akibat.daftar("pelabuhan_upacara", PelabuhanCerita::upacara);
		Akibat.daftar("pelabuhan_bintang", (p, a) -> {
			PelabuhanKota.pemain(p.level().getServer(), p.getUUID()).putBoolean("bintang_aktif", true);
			PelabuhanKota.ubah(p.level().getServer());
		});
		Akibat.daftar("pelabuhan_namai", (p, a) -> {
			PelabuhanKota.Hidup kota = PelabuhanKota.di(p);
			if (kota != null && !a.isEmpty()) {
				String name = Component.translatable("kultivator.kota_tema.pelabuhan.nama_perahu." + a.get(0)).getString();
				Galangan.namai(p, kota.situs, name);
			}
		});
		Akibat.daftar("pelabuhan_hadiah", (p, a) -> hadiah(p, a.isEmpty() ? "" : a.get(0)));
		Akibat.daftar("pelabuhan_kaca_ibrahim", (p, a) -> {
			if (EkonomiApi.bayar(p, HARGA_KACA_IBRAHIM, "pelabuhan.kaca")) {
				PelabuhanKota.beri(p, new ItemStack(TemaItems.KACA_GARAM_LAUT));
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.pelabuhan.kaca.dibeli").withStyle(ChatFormatting.GOLD));
			} else {
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.pelabuhan.kurang_uang").withStyle(ChatFormatting.RED));
			}
		});
		Akibat.daftar("pelabuhan_lomba_latih", (p, a) -> {
			PelabuhanKota.Hidup kota = PelabuhanKota.di(p);
			if (kota != null && p.level() instanceof ServerLevel level) {
				HariDewiLaut.latihan(level, kota.situs, p);
			}
		});
	}

	// ================================================================== goals

	private static void tujuan() {
		for (String kind : List.of("pelabuhan_suar", "pelabuhan_dokumen", "pelabuhan_racik", "pelabuhan_bajak", "pelabuhan_heisha",
				"pelabuhan_bintang", "pelabuhan_lomba", "pesan_lunas", "lunas")) {
			QuestTracker.daftarTujuan(kind, (args, jenis, id) -> jenis.equals(kind) && (args.isEmpty() || "*".equals(args.get(0)) || args.get(0).equals(id))
					|| "pelabuhan_heisha".equals(kind) && "duel".equals(jenis) && ("tokoh:" + PelabuhanTokoh.HEI_SHA).equals(id)
					&& (args.isEmpty() || "*".equals(args.get(0)) || "kalah".equals(args.get(0))));
		}
	}

	// ================================================================== Hei Sha

	/** Summons Kapten Hiu Hitam on the breakwater (by the lighthouse; else near the player), tier 4, one realm above. */
	static void panggilHeiSha(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		KultivatorNpc old = HEI_SHA.remove(player.getUUID());
		if (old != null && old.isAlive()) {
			old.vanish();
		}
		PelabuhanKota.Hidup kota = PelabuhanKota.di(player);
		BlockPos at = null;
		if (kota != null) {
			for (DuniaAnchorEntity a : kota.jangkar(PelabuhanAnchors.TOKOH)) {
				if (PelabuhanTokoh.DENG_MING.equals(PelabuhanAnchors.teks(a, "tokoh"))) {
					at = a.blockPosition().offset(2, 0, 2);
				}
			}
		}
		if (at == null) {
			double angle = Math.toRadians(player.getYRot());
			int x = (int) Math.floor(player.getX() - Math.sin(angle) * 6.0D);
			int z = (int) Math.floor(player.getZ() + Math.cos(angle) * 6.0D);
			BlockPos ground = TempatApi.groundSpot(level, x, z, player.blockPosition().getY());
			at = ground == null ? player.blockPosition() : ground;
		}
		long situs = kota != null ? kota.situs : 0L;
		KultivatorNpc hei = PelabuhanTokoh.panggil(level, situs, PelabuhanTokoh.HEI_SHA, at, player.getYRot() + 180.0F);
		if (hei == null) {
			return;
		}
		int realm = Math.min(dev.kultivator.data.Realm.values().length - 1, Cultivators.get(player).realm().ordinal() + 1);
		PelabuhanTokoh.Data data = PelabuhanTokoh.byId(PelabuhanTokoh.HEI_SHA);
		Component plate = Component.translatable("kultivator.kota.papan_nama", data.nama(), Component.translatable(data.kunciGelar()));
		hei.setup(data.peran(), 4, hei.look(), realm, 2, null, data.suara(), hei.robe(), plate);
		hei.memory().putString("alur", "tunggu");
		hei.memory().putBoolean("hold", true);
		hei.memory().putString("pelabuhan_untuk", player.getUUID().toString());
		HEI_SHA.put(player.getUUID(), hei);
		AdeganPayload.kirim(player, "tema_pelabuhan_heisha", hei, hei.position(), 0);
		if (VoiceEvents.exists("tema.pelabuhan.black_shark")) {
			Voice.say(hei, "tema.pelabuhan.black_shark", null);
		}
	}

	/** Hei Sha's fate after he yielded: {@code serah} to Lin, {@code damai} with Kakek Deng, {@code tumpas} cut down. */
	private static void nasib(ServerPlayer player, String how) {
		MinecraftServer server = player.level().getServer();
		KultivatorNpc hei = HEI_SHA.remove(player.getUUID());
		PelabuhanKota.Hidup kota = PelabuhanKota.di(player);
		long situs = kota != null ? kota.situs : 0L;
		switch (how) {
			case "damai" -> {
				if (situs != 0L) {
					PelabuhanKota.kota(server, situs).putBoolean("hei_sha_damai", true);
				}
				CeritaApi.setBendera(player, "pelabuhan_hasil_damai", true);
				CeritaApi.kabar(player.level(), situs, "tema_pelabuhan_heisha_damai", player.getName().getString());
			}
			case "tumpas" -> {
				CeritaApi.setBendera(player, "pelabuhan_hasil_tumpas", true);
				CeritaApi.kabar(player.level(), situs, "tema_pelabuhan_heisha_tumpas", player.getName().getString());
				if (hei != null) {
					player.level().playSound(null, hei.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 0.6F);
				}
			}
			default -> {
				CeritaApi.setBendera(player, "pelabuhan_hasil_serah", true);
				CeritaApi.kabar(player.level(), situs, "tema_pelabuhan_heisha_serah", player.getName().getString());
			}
		}
		if (situs != 0L) {
			PelabuhanKota.kota(server, situs).putBoolean("bajak_berhenti", true);
			PelabuhanKota.ubah(server);
		}
		if (hei != null && hei.isAlive()) {
			hei.forgive(player.getUUID());
			hei.vanish();
		}
		hadiah(player, "gigi_hiu");
	}

	/** Every 10 ticks: Hei Sha yields at a fifth of his health (he never dies in the chain's fight). */
	private static void jagaHeiSha() {
		for (Iterator<Map.Entry<UUID, KultivatorNpc>> it = HEI_SHA.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, KultivatorNpc> e = it.next();
			KultivatorNpc hei = e.getValue();
			if (hei.isRemoved()) {
				it.remove();
				continue;
			}
			if (!"musuh".equals(hei.memory().getStringOr("alur", ""))) {
				continue;
			}
			if (hei.getHealth() <= hei.getMaxHealth() * 0.2F) {
				hei.memory().putString("alur", "menyerah");
				hei.memory().putBoolean("hold", true);
				hei.setTarget(null);
				hei.forgive(e.getKey());
				hei.setHealth(Math.max(hei.getHealth(), hei.getMaxHealth() * 0.2F));
				hei.memory().putBoolean(KEBAL, true);
				HidupApi.klip(hei, GerakId.id("berlutut"), 0, 200);
				MinecraftServer server = hei.level().getServer();
				ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(e.getKey());
				if (player != null) {
					CeritaApi.peristiwa(player, "pelabuhan_heisha", "kalah");
					Msg.chat(player, Component.translatable("kultivator.kota_tema.pelabuhan.heisha.menyerah").withStyle(ChatFormatting.GOLD));
				}
			}
		}
	}

	/** Deaths: a sea serpent in the Boundless Sea holds a Mutiara Fajar for a player on stage {@code mutiara}. */
	private static void mati(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		if (entity.getType() != DimensiEntities.ULAR_LAUT || !DimensiLevels.isLaut(entity.level())) {
			return;
		}
		if (source.getEntity() instanceof ServerPlayer player && QuestTracker.aktif(player, QUEST) && "mutiara".equals(QuestTracker.simpul(player, QUEST))
				&& BarangCeritaPunya.tidak(player)) {
			PelabuhanKota.beri(player, new ItemStack(TemaItems.MUTIARA_FAJAR));
			Msg.chat(player, Component.translatable("kultivator.kota_tema.pelabuhan.mutiara.ular").withStyle(ChatFormatting.AQUA));
		}
	}

	/** Sneak-use on an island merchant on stage {@code mutiara}: the Mutiara Fajar for 200 NB. */
	private static void beliMutiaraFajar(ServerPlayer player) {
		if (!BarangCeritaPunya.tidak(player)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.pelabuhan.mutiara.sudah").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!EkonomiApi.bayar(player, HARGA_MUTIARA_FAJAR, "pelabuhan.mutiara_fajar")) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.pelabuhan.kurang_uang").withStyle(ChatFormatting.RED));
			return;
		}
		PelabuhanKota.beri(player, new ItemStack(TemaItems.MUTIARA_FAJAR));
		Msg.chat(player, Component.translatable("kultivator.kota_tema.pelabuhan.mutiara.dibeli").withStyle(ChatFormatting.AQUA));
	}

	/** Small holder for "the player has no Mutiara Fajar yet". */
	private static final class BarangCeritaPunya {
		static boolean tidak(ServerPlayer player) {
			return dev.kultivator.dunia.tempat.cerita.BarangCerita.hitung(player, "kultivator:tema/mutiara_fajar") <= 0;
		}
	}

	// ================================================================== the ceremony (bab 7)

	/** True while the relighting ceremony runs in town {@code situs} (the lamp burns even at noon). */
	static boolean upacaraBerjalan(long situs, long gameTime) {
		Long until = UPACARA.get(situs);
		return until != null && until > gameTime;
	}

	private static void upacara(ServerPlayer player, List<String> args) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		PelabuhanKota.Hidup kota = PelabuhanKota.di(player);
		if (kota == null) {
			return;
		}
		dev.kultivator.dunia.tempat.cerita.BarangCerita.ambil(player, "kultivator:tema/mutiara_fajar", 1);
		Suar.setPelita(level.getServer(), kota.situs, Suar.FAJAR);
		UPACARA.put(kota.situs, level.getGameTime() + 1200L);
		// everyone on the quays looks up at the beam
		AABB box = new AABB(player.blockPosition()).inflate(64.0D);
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, box, n -> n.isAlive() && !n.isHostileTo(player.getUUID()))) {
			HidupApi.klip(npc, GerakId.TATAP_LANGIT, 0, 100);
		}
		for (ServerPlayer near : level.getPlayers(pp -> pp.distanceToSqr(player) < 96.0D * 96.0D)) {
			Vec3 lamp = lampu(kota);
			AdeganPayload.kirim(near, "tema_pelabuhan_pelita", null, lamp != null ? lamp : player.position(), 0);
		}
		KultivatorNpc deng = PelabuhanAnchors.tokohDekat(player, PelabuhanTokoh.DENG_MING, 32.0D);
		if (deng != null && VoiceEvents.exists("tema.pelabuhan.lamp_lit")) {
			Voice.say(deng, "tema.pelabuhan.lamp_lit", null);
		}
		CeritaApi.setBendera(player, "pelabuhan_dewi_untukmu", true);
		Advancements.grant(player, "tempat/tema_penjaga_pelita");
	}

	private static @Nullable Vec3 lampu(PelabuhanKota.Hidup kota) {
		for (DuniaAnchorEntity a : kota.jangkar(PelabuhanAnchors.SUAR)) {
			return a.position();
		}
		return null;
	}

	// ================================================================== rewards

	/** Unique rewards made from the existing systems (38.T.0: no new items). */
	static void hadiah(ServerPlayer player, String jenis) {
		ItemStack stack;
		switch (jenis) {
			case "gigi_hiu" -> {
				stack = SenjataKota.stack(SenjataKota.Bentuk.DAO, SenjataKota.Mutu.ROH_MENENGAH);
				stack.set(DataComponents.CUSTOM_NAME, Component.translatable("kultivator.kota_tema.pelabuhan.hadiah.gigi_hiu").withStyle(ChatFormatting.AQUA));
				stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("kultivator.kota_tema.pelabuhan.hadiah.gigi_hiu.lore")
						.withStyle(ChatFormatting.GRAY))));
				stack.set(DataComponents.RARITY, Rarity.EPIC);
			}
			case "astrolab" -> stack = new ItemStack(TemaItems.ASTROLAB_KECIL);
			case "jimat" -> stack = new ItemStack(TemaItems.JIMAT_OMBAK);
			default -> {
				return;
			}
		}
		Component name = stack.getHoverName();
		PelabuhanKota.beri(player, stack);
		Msg.chat(player, Component.translatable("kultivator.kota_tema.pelabuhan.hadiah.dapat", name).withStyle(ChatFormatting.GOLD));
	}

	// ================================================================== tick: star nights, the lens, Hei Sha

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % 10 == 4) {
			jagaHeiSha();
		}
		if (t % 20 != 9) {
			return;
		}
		long now = server.overworld().getGameTime();
		UPACARA.values().removeIf(until -> until < now);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PelabuhanKota.Hidup kota = PelabuhanKota.di(player);
			if (kota == null) {
				continue;
			}
			bintang(player, kota);
		}
	}

	/** A star night: after dark, at the top of the Pagoda Penunjuk, for {@value #BINTANG_TICK} ticks, once a night. */
	private static void bintang(ServerPlayer player, PelabuhanKota.Hidup kota) {
		MinecraftServer server = player.level().getServer();
		var p = PelabuhanKota.pemain(server, player.getUUID());
		if (!p.getBooleanOr("bintang_aktif", false)) {
			return;
		}
		long jam = PelabuhanKota.jam(player.level());
		if (jam < 13000L || jam >= 23000L) {
			BINTANG.remove(player.getUUID());
			return;
		}
		long night = PelabuhanKota.hari(player.level());
		if (p.getLongOr("bintang_hari", Long.MIN_VALUE) == night) {
			return;
		}
		BlockPos top = pagoda(player.level(), kota);
		if (top == null || player.blockPosition().distSqr(top) > 7.0D * 7.0D) {
			return;
		}
		int n = BINTANG.merge(player.getUUID(), 20, Integer::sum);
		if (n == 20) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.pelabuhan.bintang.mulai").withStyle(ChatFormatting.AQUA));
		}
		if (n >= BINTANG_TICK) {
			BINTANG.remove(player.getUUID());
			p.putLong("bintang_hari", night);
			p.putInt("bintang_malam", p.getIntOr("bintang_malam", 0) + 1);
			PelabuhanKota.ubah(server);
			CeritaApi.peristiwa(player, "pelabuhan_bintang", "malam");
			Msg.chat(player, Component.translatable("kultivator.kota_tema.pelabuhan.bintang.selesai", p.getIntOr("bintang_malam", 1))
					.withStyle(ChatFormatting.AQUA));
			player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
		}
	}

	/** The top of the Pagoda Penunjuk of a town (its highest {@code pandang} point), cached. */
	private static @Nullable BlockPos pagoda(ServerLevel level, PelabuhanKota.Hidup kota) {
		BlockPos cached = PAGODA.get(kota.situs);
		if (cached != null || kota.kota == null) {
			return cached;
		}
		BlockPos best = null;
		for (dev.kultivator.dunia.tempat.Titik pt : TemaKota.titik(level, kota.kota, "pandang")) {
			String d = TemaKota.distrikDi(kota.kota, pt.pos());
			if ("kuil_laut".equals(d) && (best == null || pt.pos().getY() > best.getY())) {
				best = pt.pos();
			}
		}
		if (best != null) {
			PAGODA.put(kota.situs, best.immutable());
		}
		return best;
	}

	/** True when a named character of the chain is not to be made at its anchor now ({@code hei_sha} only by the chain). */
	static boolean tokohPergi(MinecraftServer server, long situs, String id) {
		return PelabuhanTokoh.HEI_SHA.equals(id);
	}

	/** The lens check of bab 1: players near the lamp with the chain on {@code redup}. */
	static void dekatLampu(ServerLevel level, Entity lamp) {
		for (ServerPlayer player : level.getPlayers(pp -> pp.distanceToSqr(lamp) < 6.0D * 6.0D)) {
			if (QuestTracker.aktif(player, QUEST) && "redup".equals(QuestTracker.simpul(player, QUEST))) {
				CeritaApi.peristiwa(player, "pelabuhan_suar", "lihat");
			}
		}
	}

	static int angka(List<String> a, int i, int fallback) {
		if (i >= a.size()) {
			return fallback;
		}
		try {
			return Integer.parseInt(a.get(i));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Reputation helper for code paths outside the dialog engine. */
	static void namaBaik(ServerPlayer player, long town, int delta) {
		if (town != 0L && delta != 0) {
			KotaData.get(player.level().getServer()).addReputation(town, player.getUUID(), delta);
		}
	}

	static ItemStack ikonPeta() {
		return new ItemStack(Items.MAP);
	}

	private PelabuhanCerita() {
	}
}
