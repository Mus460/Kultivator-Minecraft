package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaAnchors;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaInti;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Skull Gate and the rules of entry (DESIGN 38.T.7.4): anchor {@code kota_tema/gerbang_iblis} of the gate piece
 * (tools/gen_tempat_tema_iblis.py {@code gerbang_tengkorak}) brings the gate body ({@link GerbangTengkorakEntity}, the
 * iron double door and the lower jaw; solid while shut, no block ever changes) and the two toll enforcers outside it.
 * <ul>
 * <li><b>Upeti Gerbang</b> {@code 10 x (band + 1)} NB per entry per world day, paid by talking to a gate enforcer
 * (dialog {@code tema_iblis_upeti}, effect {@code iblis_bayar_upeti}); free with Gentar &gt;= 60 or the title "Tamu
 * Darah"; x1.5 for 3 days after the city held a siege.</li>
 * <li><b>Realm gate</b>: band &lt; 2 (below Foundation) is turned away (dialog {@code tema_iblis_upeti_lemah}); band 2 pays
 * and gets a red warning title (Golden Core is the advised realm). Capital fugitives are welcome.</li>
 * <li><b>Penyusup</b>: inside the town without today's toll, away from the gate, seen by an enforcer: the enforcers are
 * hostile for one world day in this town (not a capital crime).</li>
 * </ul>
 * The gate opens for a player who may pass (paid, free, or leaving from inside) within 7 blocks and shuts 3 s after
 * nobody needs it; during the righteous siege it stays shut unless the lever of the toll house was pulled.
 */
public final class Gerbang implements AnchorHandler {

	static final Gerbang HANDLER = new Gerbang();

	public static final String DISTRIK = "gerbang_tengkorak";
	/** Canvas position of the gate body (the opening x 45..50, the wall band z 96..103 of the district canvas). */
	static final double GX = 48.0D;
	static final double GZ = 99.5D;
	/** Canvas spots of the two toll enforcers outside the jaws. */
	static final int[][] JAGA = {{43, 106}, {53, 106}};
	public static final int BUKA_JARAK = 7;
	public static final int TUTUP_TUNDA = 60;
	public static final int ANIM = 24;
	/** Ticks of hostility for an intruder (one world day). */
	public static final long PENYUSUP_TICK = 24000L;
	/** Distance from the gate inside which a player without toll is "still at the gate", not an intruder. */
	public static final double DEKAT_GERBANG = 22.0D;

	private record Hidup(GerbangTengkorakEntity gerbang, List<KultivatorNpc> jaga, TemaKota.Kota kota) {
	}

	/** Town id -> its live gate. */
	private static final Map<Long, Hidup> HIDUP = new HashMap<>();
	/** Town id -> game time until which the gate is held open by the lever (quest chapter 5, route Lurus). */
	private static final Map<Long, Long> TUAS = new HashMap<>();
	/** Town id -> last game time somebody needed the gate open. */
	private static final Map<Long, Long> PERLU = new HashMap<>();

	private Gerbang() {
	}

	@Override
	public int radius() {
		return 72;
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!TemaConfig.aktif(Tema.IBLIS)) {
			return;
		}
		TemaKota.Kota kota = TemaAnchors.kota(anchor);
		if (kota == null || kota.tema() != Tema.IBLIS) {
			return;
		}
		Hidup h = HIDUP.get(kota.situsId());
		if (h == null || h.gerbang().isRemoved()) {
			Vec3 at = IblisKota.lokal(kota, DISTRIK, GX, IblisKota.LY + 1, GZ);
			if (at == null) {
				return;
			}
			GerbangTengkorakEntity gate = TemaMekanisme.muncul(level, TemaEntities.GERBANG_TENGKORAK, at, IblisKota.yaw(kota, 0.0F), kota,
					anchor.blockPosition());
			if (gate == null) {
				return;
			}
			gate.setKeadaan(GerbangTengkorakEntity.KEADAAN_TERTUTUP);
			h = new Hidup(gate, new ArrayList<>(), kota);
			HIDUP.put(kota.situsId(), h);
		}
		// the two toll enforcers
		h.jaga().removeIf(npc -> npc.isRemoved() || !npc.isAlive());
		if (h.jaga().size() < JAGA.length && (level.getGameTime() + anchor.getId()) % 40L < 20L) {
			int[] spot = JAGA[h.jaga().size()];
			BlockPos pos = IblisKota.blok(kota, DISTRIK, spot[0], IblisKota.LY + 1, spot[1]);
			if (pos != null) {
				KultivatorNpc npc = HidupApi.tubuh(level, kota.situsId(), NpcRole.PENEGAK_IBLIS, pos, IblisKota.yaw(kota, 0.0F),
						(int) (kota.situsId() * 31 + h.jaga().size() * 977));
				if (npc != null) {
					npc.memory().putBoolean("iblis_gerbang", true);
					npc.memory().putBoolean("hold", true);
					HidupApi.klip(npc, GerakId.BERDIRI_JAGA, 0, 0);
					h.jaga().add(npc);
				}
			}
		}
		for (ServerPlayer p : within) {
			if (p.distanceToSqr(h.gerbang()) < 12.0D * 12.0D && !bolehLewat(p, kota) && boleh(p) == null) {
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.gerbang.petunjuk", NilaiBatu.text(upeti(p, kota)))
						.withStyle(ChatFormatting.DARK_RED));
			}
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		TemaKota.Kota kota = TemaAnchors.kota(anchor);
		if (kota == null) {
			return;
		}
		Hidup h = HIDUP.remove(kota.situsId());
		if (h != null) {
			TemaMekanisme.lepas(h.gerbang());
			for (KultivatorNpc npc : h.jaga()) {
				npc.vanish();
			}
		}
	}

	static void clear() {
		HIDUP.clear();
		TUAS.clear();
		PERLU.clear();
	}

	// ================================================================== the gate body

	/** Every 5 ticks: opens for whoever may pass, shuts after {@value #TUTUP_TUNDA} ticks of nobody, animates. */
	static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		HIDUP.values().removeIf(h -> h.gerbang().isRemoved());
		for (Hidup h : HIDUP.values()) {
			GerbangTengkorakEntity gate = h.gerbang();
			if (!(gate.level() instanceof ServerLevel level)) {
				continue;
			}
			long situs = h.kota().situsId();
			boolean tuas = TUAS.getOrDefault(situs, 0L) > now;
			boolean kepung = PenyerbuanLurus.berlangsung(situs);
			boolean perlu = tuas;
			if (!kepung || tuas) {
				AABB box = gate.getBoundingBox().inflate(BUKA_JARAK, 3.0D, BUKA_JARAK);
				for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
					if (!p.isSpectator() && (bolehLewat(p, h.kota()) || didalam(p, h.kota(), gate))) {
						perlu = true;
						break;
					}
				}
			}
			if (perlu) {
				PERLU.put(situs, now);
			}
			int state = gate.keadaan();
			long since = IblisKota.sejak(gate, now);
			switch (state) {
				case GerbangTengkorakEntity.KEADAAN_TERTUTUP -> {
					if (perlu) {
						gate.setKeadaan(GerbangTengkorakEntity.KEADAAN_MEMBUKA);
						level.playSound(null, gate.getX(), gate.getY() + 2.0D, gate.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.4F, 0.6F);
						level.playSound(null, gate.getX(), gate.getY() + 2.0D, gate.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.5F);
					}
				}
				case GerbangTengkorakEntity.KEADAAN_MEMBUKA -> {
					gate.setNilai(Math.min(1.0F, since / (float) ANIM));
					if (since >= ANIM) {
						gate.setKeadaan(GerbangTengkorakEntity.KEADAAN_TERBUKA);
						gate.setNilai(1.0F);
					}
				}
				case GerbangTengkorakEntity.KEADAAN_TERBUKA -> {
					if (!perlu && now - PERLU.getOrDefault(situs, 0L) > TUTUP_TUNDA) {
						gate.setKeadaan(GerbangTengkorakEntity.KEADAAN_MENUTUP);
						level.playSound(null, gate.getX(), gate.getY() + 2.0D, gate.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.4F, 0.55F);
					}
				}
				default -> {
					gate.setNilai(Math.max(0.0F, 1.0F - since / (float) ANIM));
					if (since >= ANIM) {
						gate.setKeadaan(GerbangTengkorakEntity.KEADAAN_TERTUTUP);
						gate.setNilai(0.0F);
					} else if (perlu) {
						gate.setKeadaan(GerbangTengkorakEntity.KEADAAN_MEMBUKA);
					}
				}
			}
		}
	}

	/** True for a player on the town side of the gate (leaving is always allowed). */
	private static boolean didalam(ServerPlayer p, TemaKota.Kota kota, GerbangTengkorakEntity gate) {
		Vec3 inner = IblisKota.lokal(kota, DISTRIK, GX, IblisKota.LY + 1, GZ - 10.0D);
		return inner != null && p.position().distanceToSqr(inner) < gate.position().distanceToSqr(inner) + 1.0D;
	}

	/** The lever of the toll house holds the gate open for {@code ticks} (quest chapter 5, route Lurus). */
	public static void tahanTerbuka(long situsId, long ticks, long now) {
		TUAS.put(situsId, now + ticks);
	}

	public static @Nullable GerbangTengkorakEntity gerbang(long situsId) {
		Hidup h = HIDUP.get(situsId);
		return h == null || h.gerbang().isRemoved() ? null : h.gerbang();
	}

	// ================================================================== toll and entry rules

	/** The toll for this player today: 0 when free (Gentar &gt;= 60, Tamu Darah), x1.5 after a held siege. */
	public static long upeti(ServerPlayer p, TemaKota.Kota kota) {
		if (gratis(p, kota)) {
			return 0L;
		}
		long base = 10L * (IblisKota.band(p) + 1L);
		long hari = IblisKota.hari(p.level());
		if (IblisData.kota(p.level().getServer(), kota.situsId()).getLongOr("upeti_naik_sampai", -1L) >= hari) {
			base = Math.round(base * 1.5D);
		}
		return base;
	}

	public static boolean gratis(ServerPlayer p, TemaKota.Kota kota) {
		return IblisMeter.gentar(p, kota.situsId()) >= IblisMeter.GENTAR_AUDIENSI || IblisData.gelar(p, IblisData.GELAR_TAMU)
				|| IblisData.PENGUASA_YE.equals(IblisData.penguasa(p.level().getServer(), kota.situsId())) && IblisData.gelar(p, IblisData.GELAR_FAJAR);
	}

	/** True when the player paid today's toll (or passes free). */
	public static boolean lunas(ServerPlayer p, TemaKota.Kota kota) {
		if (gratis(p, kota)) {
			return true;
		}
		long hari = IblisKota.hari(p.level());
		return IblisData.pemainKota(p.level().getServer(), kota.situsId(), p.getUUID()).getLongOr("upeti_hari", -1L) == hari;
	}

	/** Why the realm gate refuses the player, or null (band &lt; 2 is prey too small). */
	public static @Nullable Component boleh(ServerPlayer p) {
		return IblisKota.band(p) < 2 ? Component.translatable("kultivator.kota_tema.iblis.gerbang.lemah") : null;
	}

	/** True when the gate opens for this player now. */
	public static boolean bolehLewat(ServerPlayer p, TemaKota.Kota kota) {
		return boleh(p) == null && lunas(p, kota);
	}

	/** Effect {@code iblis_bayar_upeti}: pays (or passes free), opens the gate, warns band 2, reports the chain event. */
	public static boolean bayar(ServerPlayer p, TemaKota.Kota kota) {
		Component refusal = boleh(p);
		if (refusal != null) {
			Msg.actionBar(p, refusal.copy().withStyle(ChatFormatting.RED));
			return false;
		}
		MinecraftServer server = p.level().getServer();
		long price = upeti(p, kota);
		if (!lunas(p, kota)) {
			if (price > 0L && !NilaiBatu.pay(p, price)) {
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.uang_kurang", NilaiBatu.text(price)).withStyle(ChatFormatting.RED));
				return false;
			}
			IblisData.pemainKota(server, kota.situsId(), p.getUUID()).putLong("upeti_hari", IblisKota.hari(p.level()));
			IblisData.tambahKas(server, kota.situsId(), price);
			Msg.chat(p, Component.translatable(price > 0L ? "kultivator.kota_tema.iblis.gerbang.bayar" : "kultivator.kota_tema.iblis.gerbang.gratis",
					NilaiBatu.text(price)).withStyle(ChatFormatting.GRAY));
		}
		IblisData.hapus(server, kota.situsId(), p.getUUID(), "penyusup_sampai");
		PERLU.put(kota.situsId(), server.overworld().getGameTime());
		if (IblisKota.band(p) == 2) {
			Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.gerbang.awas").withStyle(ChatFormatting.DARK_RED),
					Component.translatable("kultivator.kota_tema.iblis.gerbang.awas.sub").withStyle(ChatFormatting.RED), 10, 70, 20);
		}
		Advancements.grant(p, "tempat/tema_iblis_upeti");
		CeritaApi.peristiwa(p, IblisCerita.UPETI, Long.toString(kota.situsId()));
		IblisCerita.upetiPertama(p, kota);
		return true;
	}

	// ================================================================== intruders and the enforcers' grudges

	/**
	 * Every 40 ticks for every player inside a Kota Iblis: the day's first sight (Gentar decay), intruder detection, and
	 * the enforcers within 32 blocks turn on a player the town hunts (intruder, Pact breaker, refused blood bill).
	 */
	static void pindai(ServerPlayer p, TemaKota.Kota kota) {
		MinecraftServer server = p.level().getServer();
		if (p.isCreative() || p.isSpectator()) {
			return;
		}
		IblisMeter.susut(p, kota.situsId());
		ServerLevel level = p.level();
		List<KultivatorNpc> penegak = level.getEntitiesOfClass(KultivatorNpc.class, p.getBoundingBox().inflate(32.0D),
				n -> n.isAlive() && n.role() == NpcRole.PENEGAK_IBLIS);
		if (!lunas(p, kota) && !IblisData.sampai(server, kota.situsId(), p.getUUID(), "penyusup_sampai")) {
			GerbangTengkorakEntity gate = gerbang(kota.situsId());
			Vec3 gatePos = gate != null ? gate.position() : IblisKota.lokal(kota, DISTRIK, GX, IblisKota.LY + 1, GZ);
			boolean diGerbang = gatePos != null && p.position().distanceToSqr(gatePos) < DEKAT_GERBANG * DEKAT_GERBANG;
			if (!diGerbang) {
				for (KultivatorNpc n : penegak) {
					if (n.distanceToSqr(p) < 24.0D * 24.0D && n.hasLineOfSight(p)) {
						IblisData.setSampai(server, kota.situsId(), p.getUUID(), "penyusup_sampai", PENYUSUP_TICK);
						Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.penyusup").withStyle(ChatFormatting.DARK_RED),
								Component.translatable("kultivator.kota_tema.iblis.penyusup.sub").withStyle(ChatFormatting.RED), 5, 60, 15);
						HidupApi.klip(n, GerakId.MARAH_TUNJUK, 0, 40);
						break;
					}
				}
			}
		}
		boolean diburu = diburu(p, kota.situsId());
		boolean takut = IblisMeter.gentar(p, kota.situsId()) >= IblisMeter.GENTAR_DITAKUTI;
		for (KultivatorNpc n : penegak) {
			if (diburu) {
				if (!n.isHostileTo(p.getUUID())) {
					n.markHostile(p);
				}
			} else if (n.isHostileTo(p.getUUID())) {
				n.forgive(p.getUUID());
			} else if (takut && n.distanceToSqr(p) < 6.0D * 6.0D && n.getTarget() == null && (level.getGameTime() / 40L + n.getId()) % 9L == 0L) {
				HidupApi.klip(n, GerakId.HORMAT, 0, 30);
				HidupApi.tatap(n, p);
			}
		}
	}

	/** True while the enforcers of this town hunt the player (intruder, Pact breaker, refused blood bill, siege side). */
	public static boolean diburu(ServerPlayer p, long situsId) {
		MinecraftServer server = p.level().getServer();
		return IblisData.sampai(server, situsId, p.getUUID(), "penyusup_sampai") || IblisData.sampai(server, situsId, p.getUUID(), "langgar_sampai")
				|| TemaInti.dimusuhi(p, situsId) || PenyerbuanLurus.melawanKota(p, situsId) || ZonaPerjanjian.terlarangDiSini(p, situsId);
	}
}
