package dev.kultivator.sekte.gabung.hidup;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.alchemy.AlchemyRecipes;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sekte.gabung.dunia.TokohAnchors;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.technique.spec.run.Mastery;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Shifu's side of DESIGN 41.5.7 (owner 41C-K): the weekly lesson, the protection and the moment the disciple surpasses
 * the teacher.
 * <ul>
 *   <li><b>Pelajaran mingguan</b>: once per 7 world days (twice with a bond of {@link #IKATAN_DUA_KALI}+), between 06:00
 *   and 18:00, the member finds his Shifu at the elder's titik ({@code kompleks/tetua}; sneak + use on the Shifu's body,
 *   {@code /kultivasi gabung pelajaran}, or the 41U Shifu card) and sits {@value #DETIK} s listening (clip
 *   {@code dengar_ceramah} 305) while the Shifu lectures in six lines on his Dao. Then: Penguasaan Jurus
 *   +{@code 30 x class} on the member's least-mastered technique of the Shifu's Dao, else (an ALKIMIA sect) one pill recipe
 *   explained, else comprehension +2 of that Dao; bond +3 and the Shifu's attitude +2. A disciple who already stands above
 *   his Shifu's realm "exchanges Dao" instead (comprehension +2, attitude +3). Walking away or being hurt breaks the lesson
 *   (it does not count).</li>
 *   <li><b>Shifu melindungi</b>: bond {@link #LINDUNG_IKATAN}+, the member falls below {@link #LINDUNG_HP} of his health
 *   inside his sect's compound: the Shifu appears (his body, or a body called for the moment), steps between and holds one
 *   attacker for 10 s (slowness, weakness, the attacker loses its target), once per world day; line
 *   {@code gabung.shifu_lindungi}.</li>
 *   <li><b>Melampaui guru</b>: the member's realm passes the Shifu's: a short scene (proud laughter or jealousy by the
 *   Shifu's traits, line {@code gabung.shifu_lampaui}), advancement {@code sekte/gabung_melampaui}; the Shifu stays the
 *   Shifu and lessons become an exchange of Dao.</li>
 * </ul>
 * The Shifu is the elder of {@code Keanggotaan.shifu} ({@code TokohSekte}, 41W); while 41W knows no such elder, the
 * sect's style gives the Dao and the class the realm. Data: {@code Keanggotaan.bagian("pelajaran")} = {@code minggu},
 * {@code jumlah} (lessons this week), {@code total}, {@code lindung} (day of the last protection), {@code lampaui}.
 */
public final class Pelajaran {

	/** Lesson length (seconds) and lecture line interval (ticks). */
	public static final int DETIK = 120;
	public static final int DURASI_TICK = DETIK * 20;
	public static final int BARIS_TICK = 20 * 20;
	/** Lesson hours: 06:00-18:00 (time of day 0-12000). */
	public static final int JAM_AKHIR = 12000;
	/** The member must stay this close to the Shifu (blocks); further breaks the lesson. */
	public static final double JARAK = 6.0D;
	public static final double PUTUS = 10.0D;
	/** Lessons per week, twice from this bond on (41.5.7). */
	public static final int IKATAN_DUA_KALI = 60;
	/** Bond gained per lesson, attitude gained, mastery points per class number. */
	public static final int IKATAN_PELAJARAN = 3;
	public static final int SIKAP_PELAJARAN = 2;
	public static final int PENGUASAAN_PER_KELAS = 30;
	public static final double PAHAM = 2.0D;
	/** Protection: bond needed, health share, hold time (ticks). */
	public static final int LINDUNG_IKATAN = 40;
	public static final float LINDUNG_HP = 0.20F;
	public static final int LINDUNG_TICK = 200;
	/** Lecture lines per Dao (lang {@code kultivator.gabung.pelajaran.dao.<dao>.<0..5>}). */
	public static final int BARIS_DAO = 6;

	private static final String BAGIAN = "pelajaran";
	private static final int WARNA = 0xE8D9A8;

	/** The teacher as 41C-K uses him: id, name, Dao, realm, traits; {@code tokoh} false for the stand-in. */
	public record Shifu(String id, Component nama, Dao dao, Realm ranah, List<Sifat> sifat, boolean tokoh) {
		/** The temper the lecture is spoken in: {@code sombong}, {@code keras}, {@code hangat} or {@code tenang}. */
		public String watak() {
			if (sifat.contains(Sifat.SOMBONG) || sifat.contains(Sifat.AMBISIUS)) {
				return "sombong";
			}
			if (sifat.contains(Sifat.PENDENDAM) || sifat.contains(Sifat.BERANI)) {
				return "keras";
			}
			if (sifat.contains(Sifat.PENYAYANG) || sifat.contains(Sifat.SETIA) || sifat.contains(Sifat.RENDAH_HATI)) {
				return "hangat";
			}
			return "tenang";
		}

		/** True when a proud or vengeful teacher feels jealousy rather than pride (41.5.7 "cemburu menurut watak"). */
		public boolean cemburu() {
			return sifat.contains(Sifat.SOMBONG) || sifat.contains(Sifat.PENDENDAM) || sifat.contains(Sifat.AMBISIUS);
		}
	}

	/** A running lesson. */
	private static final class Sesi {
		final String sekte;
		final Shifu shifu;
		final BlockPos titik;
		final long mulai;
		final long akhir;
		final int urutan;
		@Nullable KultivatorNpc tubuh;
		boolean tubuhSementara;
		int baris;
		boolean terluka;

		Sesi(String sekte, Shifu shifu, BlockPos titik, long mulai, int urutan) {
			this.sekte = sekte;
			this.shifu = shifu;
			this.titik = titik;
			this.mulai = mulai;
			this.akhir = mulai + DURASI_TICK;
			this.urutan = urutan;
		}
	}

	private static final Map<UUID, Sesi> SESI = new HashMap<>();
	/** Bodies called only for a moment (protection, a lesson without a scheduled body) and the tick they leave. */
	private static final Map<KultivatorNpc, Long> SEMENTARA = new HashMap<>();
	private static boolean initialised;
	private static long tickCount;

	private Pelajaran() {
	}

	// ================================================================== wiring

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		UseEntityCallback.EVENT.register(Pelajaran::pakai);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Pelajaran::kenaDamage);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			SESI.clear();
			for (KultivatorNpc npc : SEMENTARA.keySet()) {
				npc.discard();
			}
			SEMENTARA.clear();
		});
		Keanggotaan.addPenyentuh((player, k, hari) -> periksaLampaui(player, k));
	}

	/** From {@link SitusGabung}'s tick, every 20 ticks: lessons, temporary bodies, and (every 30 s) the surpass check. */
	static void tick(MinecraftServer server) {
		tickCount++;
		long now = server.overworld().getGameTime();
		for (Map.Entry<UUID, Sesi> e : List.copyOf(SESI.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				akhiri(e.getKey(), e.getValue());
				continue;
			}
			jalan(player, e.getValue(), now);
		}
		SEMENTARA.entrySet().removeIf(e -> {
			if (e.getKey().isRemoved() || now >= e.getValue()) {
				e.getKey().discard();
				return true;
			}
			return false;
		});
		if (tickCount % 30L == 0L) {
			GabungData data = GabungData.get(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Keanggotaan k = data.peek(player.getUUID());
				if (k != null && k.sekteAktif() != null && !k.shifu.isEmpty()) {
					periksaLampaui(player, k);
				}
			}
		}
	}

	// ================================================================== the Shifu

	/** The member's Shifu (41W's elder, or the stand-in from the sect's style and class), or null without one. */
	public static @Nullable Shifu shifu(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || k.shifu.isEmpty()) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		TokohSekte.Tokoh t = TokohSekte.tokoh(server, k.shifu);
		if (t != null) {
			return new Shifu(t.id(), Component.literal(t.nama()), t.dao(), t.ranah(), t.sifat(), true);
		}
		Corak corak = GabungApi.corak(sekte);
		Realm ranah = KelasSekte.ranahTetua(Math.max(1, GabungApi.tier(sekte)));
		return new Shifu(k.shifu, Component.translatable("kultivator.gabung.pelajaran.shifu_umum"), corak.daoUtama(), ranah, List.of(), false);
	}

	/** Lessons the member may still take this week (0, 1 or 2). */
	public static int sisaMinggu(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		CompoundTag t = k.bagian(BAGIAN);
		long minggu = Math.floorDiv(GabungApi.hari(player.level().getServer()), 7L);
		int jatah = k.ikatanShifu >= IKATAN_DUA_KALI ? 2 : 1;
		int dipakai = t.getLongOr("minggu", -1L) == minggu ? t.getIntOr("jumlah", 0) : 0;
		return Math.max(0, jatah - dipakai);
	}

	/** True while the player sits in a lesson. */
	public static boolean berjalan(ServerPlayer player) {
		return SESI.containsKey(player.getUUID());
	}

	/** Why a lesson cannot start now (without the distance check), or null. */
	public static @Nullable Component alasan(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || !GabungApi.aktif()) {
			return Component.translatable("kultivator.gabung.kediaman.bukan_anggota");
		}
		if (k.shifu.isEmpty()) {
			return Component.translatable("kultivator.gabung.pelajaran.tanpa_shifu");
		}
		if (berjalan(player)) {
			return Component.translatable("kultivator.gabung.pelajaran.berjalan");
		}
		MinecraftServer server = player.level().getServer();
		TokohSekte.Tokoh t = TokohSekte.tokoh(server, k.shifu);
		long hari = GabungApi.hari(server);
		if (t != null && t.terluka(hari)) {
			return Component.translatable("kultivator.gabung.pelajaran.shifu_luka", Component.literal(t.nama()), t.lukaSampai());
		}
		long jam = dev.kultivator.dunia.kota.Kota.timeOfDay(server);
		if (jam >= JAM_AKHIR) {
			return Component.translatable("kultivator.gabung.pelajaran.jam");
		}
		if (sisaMinggu(player) <= 0) {
			long next = (Math.floorDiv(hari, 7L) + 1L) * 7L;
			return Component.translatable("kultivator.gabung.pelajaran.habis", next);
		}
		String sibuk = Sibuk.why(player);
		if (sibuk != null) {
			return Sibuk.text(sibuk);
		}
		return null;
	}

	/**
	 * Starts the lesson when the member stands with his Shifu (within {@link #JARAK} blocks of the Shifu's body, or of the
	 * elder's titik while no body stands there). Null when it began, else the reason.
	 */
	public static @Nullable Component mulai(ServerPlayer player) {
		Component why = alasan(player);
		if (why != null) {
			return why;
		}
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		Shifu shifu = shifu(player);
		if (sekte == null || shifu == null) {
			return Component.translatable("kultivator.gabung.pelajaran.tanpa_shifu");
		}
		ServerLevel level = player.level();
		KultivatorNpc body = level.dimension() == Level.OVERWORLD ? TokohAnchors.tubuh(level, sekte, k.shifu) : null;
		BlockPos titik = body != null ? body.blockPosition() : GabungApi.titik(sekte, TitikGabung.TETUA);
		if (titik == null || level.dimension() != Level.OVERWORLD
				|| player.position().distanceToSqr(Vec3.atBottomCenterOf(titik)) > (JARAK + 2.0D) * (JARAK + 2.0D)) {
			return Component.translatable("kultivator.gabung.pelajaran.jauh", shifu.nama());
		}
		CompoundTag t = k.bagian(BAGIAN);
		Sesi sesi = new Sesi(sekte, shifu, titik.immutable(), level.getGameTime(), t.getIntOr("total", 0));
		if (body == null) {
			body = tubuhSementara(level, sekte, shifu, titik, DURASI_TICK + 100);
			sesi.tubuhSementara = body != null;
		}
		sesi.tubuh = body;
		SESI.put(player.getUUID(), sesi);
		GabungPayloads.kirimKlip(player, GabungGerakId.DENGAR_CERAMAH, 0);
		if (body != null) {
			HidupApi.tatap(body, player);
			HidupApi.klip(body, GabungGerakId.TETUA_MENILAI, 0, 60);
			Voice.say(body, "gabung.pelajaran_mulai", null);
		}
		level.playSound(null, titik, SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.6F, 1.2F);
		Msg.actionBar(player, Component.translatable("kultivator.gabung.pelajaran.mulai", shifu.nama(), DETIK).withColor(WARNA));
		ucap(player, sesi, Component.translatable("kultivator.gabung.pelajaran.buka." + shifu.watak(), player.getDisplayName()));
		return null;
	}

	private static void jalan(ServerPlayer player, Sesi sesi, long now) {
		Vec3 centre = sesi.tubuh != null && !sesi.tubuh.isRemoved() ? sesi.tubuh.position() : Vec3.atBottomCenterOf(sesi.titik);
		if (sesi.terluka || player.level().dimension() != Level.OVERWORLD || player.position().distanceToSqr(centre) > PUTUS * PUTUS
				|| !player.isAlive()) {
			Msg.chat(player, Component.translatable(sesi.terluka ? "kultivator.gabung.pelajaran.putus_luka" : "kultivator.gabung.pelajaran.putus",
					sesi.shifu.nama()).withStyle(ChatFormatting.GRAY));
			akhiri(player.getUUID(), sesi);
			GabungPayloads.kirimKlip(player, 0, 0);
			return;
		}
		long gone = now - sesi.mulai;
		int due = (int) Math.min(5L, gone / BARIS_TICK);
		while (sesi.baris < due) {
			sesi.baris++;
			ucap(player, sesi, baris(player, sesi, sesi.baris - 1));
			if (sesi.tubuh != null) {
				HidupApi.tatap(sesi.tubuh, player);
				HidupApi.klip(sesi.tubuh, GabungGerakId.TETUA_MENILAI, 0, 60);
			}
			ServerLevel level = player.level();
			level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.4D, player.getZ(), 10, 0.4D, 0.4D, 0.4D, 0.6D);
		}
		if (now >= sesi.akhir) {
			akhiri(player.getUUID(), sesi);
			GabungPayloads.kirimKlip(player, 0, 0);
			hasil(player, sesi);
		}
	}

	/** Lecture line {@code n} (0-4) of the lesson: five of the Dao's six lines, rotating per lesson; or the Dao exchange. */
	private static Component baris(ServerPlayer player, Sesi sesi, int n) {
		Realm own = Cultivators.get(player).realm();
		if (own.ordinal() > sesi.shifu.ranah().ordinal()) {
			return Component.translatable("kultivator.gabung.pelajaran.tukar." + Math.floorMod(sesi.urutan + n, 3), player.getDisplayName());
		}
		int line = Math.floorMod(sesi.urutan + n, BARIS_DAO);
		return Component.translatable("kultivator.gabung.pelajaran.dao." + sesi.shifu.dao().id() + "." + line);
	}

	private static void ucap(ServerPlayer player, Sesi sesi, Component line) {
		Msg.chat(player, Component.translatable("kultivator.gabung.pelajaran.ucap", sesi.shifu.nama(), line).withColor(WARNA));
	}

	private static void akhiri(UUID uuid, Sesi sesi) {
		SESI.remove(uuid);
		if (sesi.tubuhSementara && sesi.tubuh != null) {
			SEMENTARA.put(sesi.tubuh, 0L);
		}
	}

	/** What the finished lesson gives (41.5.7), then the bond, the attitude and the week's count. */
	private static void hasil(ServerPlayer player, Sesi sesi) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		if (!sesi.sekte.equals(k.sekteAktif())) {
			return;
		}
		CultivationData data = Cultivators.get(player);
		Shifu shifu = sesi.shifu;
		Component gain;
		int sikap = SIKAP_PELAJARAN;
		if (data.realm().ordinal() > shifu.ranah().ordinal()) {
			double got = Comprehension.grant(player, shifu.dao(), PAHAM);
			gain = Component.translatable("kultivator.gabung.pelajaran.hasil.tukar", shifu.dao().displayName(), fmt(got));
			sikap = SIKAP_PELAJARAN + 1;
		} else {
			String teknik = teknikDao(data, shifu.dao());
			if (teknik != null) {
				int pts = PENGUASAAN_PER_KELAS * GabungApi.kelas(sesi.sekte);
				Mastery.add(player, teknik, pts, Mastery.Source.TEACH);
				TechniqueSpec spec = TechniqueRegistry.byId(teknik);
				gain = Component.translatable("kultivator.gabung.pelajaran.hasil.teknik", spec == null ? Component.literal(teknik) : spec.displayName(), pts);
			} else if (GabungApi.corak(sesi.sekte) == Corak.ALKIMIA && Math.floorMod(sesi.urutan, 2) == 0) {
				gain = resep(sesi.urutan + sesi.sekte.hashCode());
			} else {
				double got = Comprehension.grant(player, shifu.dao(), PAHAM);
				gain = Component.translatable("kultivator.gabung.pelajaran.hasil.paham", shifu.dao().displayName(), fmt(got));
			}
		}
		ucap(player, sesi, Component.translatable("kultivator.gabung.pelajaran.tutup." + shifu.watak(), player.getDisplayName()));
		Msg.chat(player, gain.copy().withColor(0xBFE6A8));
		k.ikatanShifu = Math.min(100, k.ikatanShifu + IKATAN_PELAJARAN);
		CompoundTag t = k.bagian(BAGIAN);
		long hari = GabungApi.hari(server);
		long minggu = Math.floorDiv(hari, 7L);
		if (t.getLongOr("minggu", -1L) != minggu) {
			t.putLong("minggu", minggu);
			t.putInt("jumlah", 0);
		}
		t.putInt("jumlah", t.getIntOr("jumlah", 0) + 1);
		t.putInt("total", t.getIntOr("total", 0) + 1);
		k.catat(hari, "kultivator.gabung.riwayat.pelajaran", shifu.dao().id());
		GabungData.get(server).setDirty();
		if (shifu.tokoh()) {
			TokohSekte.ubahSikap(server, shifu.id(), player.getUUID(), sikap);
		}
		Msg.actionBar(player, Component.translatable("kultivator.gabung.pelajaran.ikatan", k.ikatanShifu).withColor(WARNA));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.1F);
		periksaLampaui(player, k);
	}

	/** The member's least-mastered technique of {@code dao} below the top level, or null. */
	private static @Nullable String teknikDao(CultivationData data, Dao dao) {
		String best = null;
		int bestLevel = Integer.MAX_VALUE;
		for (String id : data.learnedIds()) {
			TechniqueSpec spec = TechniqueRegistry.byId(id);
			if (spec == null || spec.dao() != dao) {
				continue;
			}
			int level = Mastery.level(data, id);
			if (level < 4 && (level < bestLevel || level == bestLevel && best != null && id.compareTo(best) < 0)) {
				best = id;
				bestLevel = level;
			}
		}
		return best;
	}

	/** One pill recipe explained by an alchemist teacher (the ALKIMIA lesson). */
	private static Component resep(int pick) {
		List<AlchemyRecipes.Recipe> pills = new ArrayList<>();
		for (AlchemyRecipes.Recipe r : AlchemyRecipes.all()) {
			if (r.isPill()) {
				pills.add(r);
			}
		}
		if (pills.isEmpty()) {
			return Component.translatable("kultivator.gabung.pelajaran.hasil.resep_kosong");
		}
		AlchemyRecipes.Recipe r = pills.get(Math.floorMod(pick, pills.size()));
		return Component.translatable("kultivator.gabung.pelajaran.hasil.resep", new ItemStack(r.outputItem()).getHoverName(),
				AlchemyRecipes.ingredientList(r));
	}

	private static String fmt(double v) {
		return String.format(java.util.Locale.ROOT, "%.1f", v);
	}

	// ================================================================== sneak + use on the Shifu

	private static InteractionResult pakai(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !(entity instanceof KultivatorNpc npc)
				|| level.isClientSide() || !(player instanceof ServerPlayer sp) || !GabungApi.aktif()) {
			return InteractionResult.PASS;
		}
		Keanggotaan k = GabungData.get(sp.level().getServer()).peek(sp.getUUID());
		if (k == null || k.sekteAktif() == null || k.shifu.isEmpty()) {
			return InteractionResult.PASS;
		}
		boolean isShifu = k.shifu.equals(npc.memory().getStringOr("tokoh", ""));
		if (!isShifu) {
			Sesi own = SESI.get(sp.getUUID());
			isShifu = own != null && own.tubuh == npc;
		}
		if (!isShifu) {
			KultivatorNpc anchored = TokohAnchors.tubuh(sp.level(), k.sekteAktif(), k.shifu);
			isShifu = anchored == npc;
		}
		if (!isShifu) {
			return InteractionResult.PASS;
		}
		Component why = mulai(sp);
		if (why != null) {
			Msg.actionBar(sp, why.copy().withStyle(ChatFormatting.GOLD));
		}
		return InteractionResult.SUCCESS;
	}

	// ================================================================== the Shifu protects

	private static void kenaDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
		if (taken > 0.0F) {
			if (entity instanceof ServerPlayer p) {
				Sesi sesi = SESI.get(p.getUUID());
				if (sesi != null) {
					sesi.terluka = true;
				}
			}
		}
		if (!(entity instanceof ServerPlayer player) || !player.isAlive() || taken <= 0.0F || !GabungApi.aktif()) {
			return;
		}
		if (player.getHealth() >= player.getMaxHealth() * LINDUNG_HP) {
			return;
		}
		Entity src = source.getEntity();
		if (!(src instanceof LivingEntity attacker) || attacker == player || !attacker.isAlive()) {
			return;
		}
		Keanggotaan k = GabungData.get(player.level().getServer()).peek(player.getUUID());
		String sekte = k == null ? null : k.sekteAktif();
		if (sekte == null || k.shifu.isEmpty() || k.ikatanShifu < LINDUNG_IKATAN || DuelSessions.byEntity(player) != null) {
			return;
		}
		ServerLevel level = player.level();
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		var kompleks = KompleksApi.kompleksDi(level, player.blockPosition());
		if (kompleks == null || !sekte.equals(kompleks.sekte())) {
			return;
		}
		long hari = GabungApi.hari(level.getServer());
		CompoundTag t = k.bagian(BAGIAN);
		if (t.getLongOr("lindung", -1L) == hari) {
			return;
		}
		lindungi(player, k, attacker, hari);
	}

	/** The Shifu steps between {@code player} and {@code attacker} and holds the attacker for 10 s. True when it happened. */
	static boolean lindungi(ServerPlayer player, Keanggotaan k, LivingEntity attacker, long hari) {
		Shifu shifu = shifu(player);
		String sekte = k.sekteAktif();
		if (shifu == null || sekte == null) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		TokohSekte.Tokoh t = shifu.tokoh() ? TokohSekte.tokoh(server, shifu.id()) : null;
		if (t != null && t.terluka(hari)) {
			return false;
		}
		ServerLevel level = player.level();
		KultivatorNpc body = TokohAnchors.tubuh(level, sekte, k.shifu);
		if (body != null && body.distanceToSqr(player) > 64.0D * 64.0D) {
			body = null;
		}
		Vec3 between = player.position().add(attacker.position()).scale(0.5D);
		if (body == null) {
			body = tubuhSementara(level, sekte, shifu, BlockPos.containing(between), LINDUNG_TICK + 60);
		}
		k.bagian(BAGIAN).putLong("lindung", hari);
		GabungData.get(server).setDirty();
		if (body != null) {
			level.sendParticles(ParticleTypes.CLOUD, body.getX(), body.getY() + 0.8D, body.getZ(), 16, 0.4D, 0.6D, 0.4D, 0.02D);
			body.snapTo(between.x, player.getY(), between.z, body.getYRot(), 0.0F);
			HidupApi.tatap(body, attacker);
			HidupApi.klip(body, GabungGerakId.TETUA_MENUNJUK_ATURAN, 0, 60);
			Voice.say(body, "gabung.shifu_lindungi", null);
		}
		attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, LINDUNG_TICK, 9));
		attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, LINDUNG_TICK, 4));
		attacker.addEffect(new MobEffectInstance(MobEffects.GLOWING, LINDUNG_TICK, 0));
		if (attacker instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI / 12.0D;
			level.sendParticles(ParticleTypes.ENCHANT, attacker.getX() + Math.cos(a) * 1.2D, attacker.getY() + 0.2D + (i % 4) * 0.4D,
					attacker.getZ() + Math.sin(a) * 1.2D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
		level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 1.0F, 1.4F);
		Msg.chat(player, Component.translatable("kultivator.gabung.pelajaran.lindung", shifu.nama(), attacker.getDisplayName()).withColor(WARNA));
		return true;
	}

	// ================================================================== surpassing the teacher

	/** The moment the disciple's realm passes the Shifu's (once). */
	private static void periksaLampaui(ServerPlayer player, Keanggotaan k) {
		if (k.sekteAktif() == null || k.shifu.isEmpty()) {
			return;
		}
		CompoundTag t = k.bagian(BAGIAN);
		if (t.getBooleanOr("lampaui", false)) {
			return;
		}
		Shifu shifu = shifu(player);
		if (shifu == null || Cultivators.get(player).realm().ordinal() <= shifu.ranah().ordinal()) {
			return;
		}
		lampaui(player, k, shifu);
	}

	private static void lampaui(ServerPlayer player, Keanggotaan k, Shifu shifu) {
		k.bagian(BAGIAN).putBoolean("lampaui", true);
		k.catat(GabungApi.hari(player.level().getServer()), "kultivator.gabung.riwayat.lampaui", shifu.id());
		GabungData.get(player.level().getServer()).setDirty();
		ServerLevel level = player.level();
		KultivatorNpc body = level.dimension() == Level.OVERWORLD ? TokohAnchors.tubuh(level, k.sekteAktif(), k.shifu) : null;
		if (body != null && body.distanceToSqr(player) <= 32.0D * 32.0D) {
			HidupApi.tatap(body, player);
			Voice.say(body, "gabung.shifu_lampaui", null);
		}
		String key = shifu.cemburu() ? "kultivator.gabung.pelajaran.lampaui.cemburu" : "kultivator.gabung.pelajaran.lampaui.bangga";
		Msg.chat(player, Component.translatable("kultivator.gabung.pelajaran.ucap", shifu.nama(),
				Component.translatable(key, player.getDisplayName())).withColor(0xFFD24A));
		Msg.title(player, Component.translatable("kultivator.gabung.pelajaran.lampaui.judul"), shifu.nama().copy(), 10, 60, 20);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(), 30, 0.5D, 1.0D, 0.5D, 0.03D);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
		Advancements.grant(player, "sekte/gabung_melampaui");
	}

	// ================================================================== bodies called for a moment

	/** A Shifu body for a moment: elder role, the sect's elder robe, the Shifu's name; it leaves after {@code ticks}. */
	private static @Nullable KultivatorNpc tubuhSementara(ServerLevel level, String sekte, Shifu shifu, BlockPos at, int ticks) {
		TempatInfo info = TempatApi.tempatDi(level, at);
		int look = (int) TurnamenMurid.campur(level.getSeed(), shifu.id().hashCode());
		KultivatorNpc npc = HidupApi.tubuhStaf(level, info == null ? 0L : info.situsId(), NpcRole.TETUA_SEKTE, at, 0.0F, look);
		if (npc == null) {
			return null;
		}
		SosokApi.setRupa(npc, TokohAnchors.RUPA_TETUA + "|sekte=npc:" + sekte);
		npc.setup(NpcRole.TETUA_SEKTE, look, shifu.ranah().ordinal(), 2, shifu.dao(), look >>> 3, 0, shifu.nama());
		npc.setPlate(shifu.nama());
		npc.memory().putString("tokoh", shifu.id());
		npc.memory().putBoolean("hold", true);
		SEMENTARA.put(npc, level.getGameTime() + ticks);
		return npc;
	}

	// ================================================================== commands

	/** Player sub {@code /kultivasi gabung pelajaran}: starts the lesson where the member stands with his Shifu. */
	static LiteralArgumentBuilder<CommandSourceStack> pemainNode() {
		return Commands.literal("pelajaran").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			Component why = mulai(player);
			if (why != null) {
				ctx.getSource().sendFailure(why);
				return 0;
			}
			return 1;
		});
	}

	/** {@code uji gabung pelajaran [mulai|selesai|lindungi|lampaui|reset]}. */
	static LiteralArgumentBuilder<CommandSourceStack> ujiNode() {
		return Commands.literal("pelajaran")
				.executes(Pelajaran::ujiStatus)
				.then(Commands.literal("mulai").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Component why = alasanUji(player);
					if (why != null) {
						ctx.getSource().sendFailure(why);
						return 0;
					}
					return 1;
				}))
				.then(Commands.literal("selesai").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Sesi sesi = SESI.get(player.getUUID());
					if (sesi == null) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.pelajaran.uji.tidak_jalan"));
						return 0;
					}
					akhiri(player.getUUID(), sesi);
					GabungPayloads.kirimKlip(player, 0, 0);
					hasil(player, sesi);
					return 1;
				}))
				.then(Commands.literal("lindungi").executes(Pelajaran::ujiLindungi))
				.then(Commands.literal("lampaui").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Keanggotaan k = GabungApi.anggota(player);
					Shifu shifu = shifu(player);
					if (shifu == null) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.pelajaran.tanpa_shifu"));
						return 0;
					}
					lampaui(player, k, shifu);
					return 1;
				}))
				.then(Commands.literal("reset").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					CompoundTag t = GabungApi.anggota(player).bagian(BAGIAN);
					t.remove("minggu");
					t.remove("jumlah");
					t.remove("lindung");
					t.remove("lampaui");
					GabungData.get(ctx.getSource().getServer()).setDirty();
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.pelajaran.uji.reset"), true);
					return 1;
				}));
	}

	/** The test start: the week's count and the hours are ignored, the Shifu body is called beside the tester. */
	private static @Nullable Component alasanUji(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		Shifu shifu = shifu(player);
		if (sekte == null || shifu == null) {
			return Component.translatable("kultivator.gabung.pelajaran.tanpa_shifu");
		}
		if (berjalan(player)) {
			return Component.translatable("kultivator.gabung.pelajaran.berjalan");
		}
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition().relative(player.getDirection(), 2);
		Sesi sesi = new Sesi(sekte, shifu, at, level.getGameTime(), k.bagian(BAGIAN).getIntOr("total", 0));
		sesi.tubuh = tubuhSementara(level, sekte, shifu, at, DURASI_TICK + 100);
		sesi.tubuhSementara = sesi.tubuh != null;
		SESI.put(player.getUUID(), sesi);
		GabungPayloads.kirimKlip(player, GabungGerakId.DENGAR_CERAMAH, 0);
		ucap(player, sesi, Component.translatable("kultivator.gabung.pelajaran.buka." + shifu.watak(), player.getDisplayName()));
		return null;
	}

	private static int ujiStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Shifu shifu = shifu(player);
		Keanggotaan k = GabungApi.anggota(player);
		if (shifu == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.pelajaran.tanpa_shifu"));
			return 0;
		}
		Component why = alasan(player);
		MutableComponent line = Component.translatable("kultivator.gabung.pelajaran.uji.status", shifu.nama(), shifu.dao().displayName(),
				shifu.ranah().displayName(), k.ikatanShifu, sisaMinggu(player), Component.translatable("kultivator.gabung.pelajaran.watak." + shifu.watak()));
		ctx.getSource().sendSuccess(() -> line, false);
		if (why != null) {
			ctx.getSource().sendSuccess(() -> why, false);
		}
		return 1;
	}

	private static int ujiLindungi(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Keanggotaan k = GabungApi.anggota(player);
		LivingEntity target = null;
		double best = Double.MAX_VALUE;
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16.0D))) {
			double d = mob.distanceToSqr(player);
			if (mob.isAlive() && d < best && !(mob instanceof KultivatorNpc)) {
				best = d;
				target = mob;
			}
		}
		if (target == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.pelajaran.uji.tanpa_musuh"));
			return 0;
		}
		boolean ok = lindungi(player, k, target, GabungApi.hari(ctx.getSource().getServer()));
		return ok ? 1 : 0;
	}
}
