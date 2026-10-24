package dev.kultivator.dunia.tempat.kompleks;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.Distrik;
import dev.kultivator.dunia.tempat.Putusan;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.TempatZones;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.duel.ai.DuelStyle;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Commands of the NPC sect compounds (DESIGN 38.D.10). Op tests under {@code /kultivasi uji kompleks}:
 * <ul>
 *   <li>{@code daftar}: every planned compound, its band, alignment, districts and verdict;</li>
 *   <li>{@code cari <sekte>}: teleport to the compound's front court (its planned floor);</li>
 *   <li>{@code rotasi}: the nearest gate's computed rotation and box ({@link RotasiGerbang}) against the gate's real
 *       structure start (the gate chunk is brought to the structure-start step only, no blocks);</li>
 *   <li>{@code sikap <sekte> <sikap>}: moves the caller's standing into that stance's band (a preview of the welcome,
 *       warnings and war) and forgets the current visit so the gate reacts again.</li>
 * </ul>
 * Player commands (the chips of the cards): {@code /kultivasi kompleks tanding} (a friendly bout on the training ground),
 * {@code /kultivasi kompleks pasar} and {@code /kultivasi kompleks beli <n>} (the sect market).
 */
final class KompleksCommands {

	static void init() {
		DuniaCommands.test("kompleks", KompleksCommands::uji);
		DuniaCommands.player("kompleks", KompleksCommands::pemain);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("kompleks")
				.then(Commands.literal("daftar").executes(ctx -> daftar(ctx.getSource())))
				.then(Commands.literal("cari").then(Commands.argument("sekte", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(sekte(c.getSource()), b))
						.executes(ctx -> cari(ctx.getSource(), StringArgumentType.getString(ctx, "sekte")))))
				.then(Commands.literal("rotasi").executes(ctx -> rotasi(ctx.getSource())))
				.then(Commands.literal("sikap").then(Commands.argument("sekte", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(sekte(c.getSource()), b))
						.then(Commands.argument("sikap", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("sekutu", "ramah", "netral", "dingin", "bermusuhan", "perang"), b))
								.executes(ctx -> sikap(ctx.getSource(), StringArgumentType.getString(ctx, "sekte"),
										StringArgumentType.getString(ctx, "sikap"))))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> pemain() {
		return Commands.literal("kompleks")
				.then(Commands.literal("tanding").executes(ctx -> tanding(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("pasar").executes(ctx -> PasarSekte.lihat(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("beli").then(Commands.argument("no", IntegerArgumentType.integer(0, 31))
						.executes(ctx -> PasarSekte.beli(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "no")))));
	}

	private static List<String> sekte(CommandSourceStack source) {
		List<String> out = new ArrayList<>();
		for (RencanaKompleks.Kompleks k : RencanaKompleks.of(source.getServer().overworld().getSeed()).kompleks()) {
			out.add(k.sekte());
		}
		return out;
	}

	// ------------------------------------------------------------------ uji

	private static int daftar(CommandSourceStack source) {
		ServerLevel level = source.getServer().overworld();
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		RencanaKompleks.Plan plan = RencanaKompleks.of(level.getSeed());
		if (plan.kompleks().isEmpty()) {
			source.sendFailure(Component.translatable(GerbangPlan.enabled() ? "kultivator.kompleks.uji.kosong" : "kultivator.kompleks.uji.mati"));
			return 0;
		}
		int born = 0;
		for (RencanaKompleks.Kompleks k : plan.kompleks()) {
			Putusan p = RencanaKompleks.akhir(sumber, k);
			if (p.layak()) {
				born++;
			}
			StringBuilder districts = new StringBuilder();
			for (Distrik d : k.distrik()) {
				if (!districts.isEmpty()) {
					districts.append(", ");
				}
				districts.append(d.id());
			}
			Component name = KompleksSikap.namaSekte(k.sekte());
			String verdict = p.layak() ? "y " + p.dasarY() : p.alasan();
			source.sendSuccess(() -> Component.translatable(p.layak() ? "kultivator.kompleks.uji.baris_lahir" : "kultivator.kompleks.uji.baris_tidak",
					k.sekte(), name, k.entry().band(), k.aliran(), k.kandidat().pusatX(), k.kandidat().pusatZ(), k.putar().name().toLowerCase(Locale.ROOT),
					districts.toString(), verdict), false);
		}
		int n = born;
		source.sendSuccess(() -> Component.translatable("kultivator.kompleks.uji.ringkas", plan.kompleks().size(), n), false);
		return n;
	}

	private static int cari(CommandSourceStack source, String sekte) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		ServerLevel level = source.getServer().overworld();
		RencanaKompleks.Kompleks k = RencanaKompleks.of(level.getSeed()).kompleks(sekte);
		if (k == null) {
			source.sendFailure(Component.translatable("kultivator.kompleks.uji.tidak_ada", sekte));
			return 0;
		}
		Putusan p = RencanaKompleks.akhir(TempatZones.sumber(level), k);
		if (!p.layak()) {
			source.sendFailure(Component.translatable("kultivator.kompleks.uji.tidak_lahir", sekte, p.alasan()));
			return 0;
		}
		// the front court's street, a few blocks inside from the gate side
		int[] at = k.dunia(0, -20);
		level.getChunk(at[0] >> 4, at[1] >> 4);
		player.teleportTo(level, at[0] + 0.5D, p.dasarY() + 1.0D, at[1] + 0.5D, Set.of(), player.getYRot(), player.getXRot(), true);
		source.sendSuccess(() -> Component.translatable("kultivator.kompleks.uji.cari", KompleksSikap.namaSekte(sekte), at[0], p.dasarY(), at[1]), false);
		return 1;
	}

	private static int rotasi(CommandSourceStack source) {
		ServerLevel level = source.getServer().overworld();
		BlockPos at = BlockPos.containing(source.getPosition());
		GerbangPlan.Entry best = null;
		double bestD = Double.MAX_VALUE;
		for (GerbangPlan.Entry e : GerbangPlan.of(level.getSeed()).entries()) {
			double d = Math.hypot(e.blockX() - at.getX(), e.blockZ() - at.getZ());
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		if (best == null) {
			source.sendFailure(Component.translatable("kultivator.kompleks.uji.mati"));
			return 0;
		}
		RotasiGerbang.Gerbang g = RotasiGerbang.gerbang(level.getSeed(), best);
		GerbangPlan.Entry e = best;
		source.sendSuccess(() -> Component.translatable("kultivator.kompleks.uji.rotasi_hitung", e.id(), e.chunkX(), e.chunkZ(),
				g.putar().name().toLowerCase(Locale.ROOT), g.kotak().minX(), g.kotak().minZ(), g.kotak().maxX(), g.kotak().maxZ()), false);
		ChunkAccess chunk = level.getChunk(best.chunkX(), best.chunkZ(), ChunkStatus.STRUCTURE_STARTS, true);
		if (chunk == null) {
			source.sendFailure(Component.translatable("kultivator.kompleks.uji.rotasi_tanpa"));
			return 0;
		}
		for (StructureStart start : chunk.getAllStarts().values()) {
			if (!start.isValid()) {
				continue;
			}
			for (StructurePiece piece : start.getPieces()) {
				if (piece instanceof PoolElementStructurePiece pool) {
					boolean same = pool.getRotation() == g.putar() && pool.getBoundingBox().minX() == g.kotak().minX()
							&& pool.getBoundingBox().minZ() == g.kotak().minZ() && pool.getBoundingBox().maxX() == g.kotak().maxX()
							&& pool.getBoundingBox().maxZ() == g.kotak().maxZ();
					source.sendSuccess(() -> Component.translatable(same ? "kultivator.kompleks.uji.rotasi_sama" : "kultivator.kompleks.uji.rotasi_beda",
							pool.getRotation().name().toLowerCase(Locale.ROOT), pool.getBoundingBox().minX(), pool.getBoundingBox().minZ(),
							pool.getBoundingBox().maxX(), pool.getBoundingBox().maxZ()).withStyle(same ? ChatFormatting.GREEN : ChatFormatting.RED), false);
					return same ? 1 : 0;
				}
			}
		}
		source.sendFailure(Component.translatable("kultivator.kompleks.uji.rotasi_tanpa"));
		return 0;
	}

	private static int sikap(CommandSourceStack source, String sekte, String nama) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		int target = switch (nama) {
			case "sekutu", "ramah" -> 50;
			case "netral" -> 5;
			case "dingin" -> -20;
			case "bermusuhan" -> -50;
			case "perang" -> -85;
			default -> Integer.MIN_VALUE;
		};
		if (target == Integer.MIN_VALUE) {
			source.sendFailure(Component.translatable("kultivator.kompleks.uji.sikap_salah", nama));
			return 0;
		}
		int now = SekteApi.diplomasi().standing(player.getUUID(), sekte);
		SekteApi.diplomasi().adjust(player.getUUID(), sekte, target - now, "uji_kompleks");
		KompleksSikap.reset();
		Sikap s = SekteApi.diplomasi().stance(player.getUUID(), sekte);
		source.sendSuccess(() -> Component.translatable("kultivator.kompleks.uji.sikap", KompleksSikap.namaSekte(sekte),
				SekteApi.diplomasi().standing(player.getUUID(), sekte), s.id()), false);
		return 1;
	}

	// ------------------------------------------------------------------ the friendly bout

	/** {@code /kultivasi kompleks tanding}: a bout with a disciple at the nearest training ground of a friendly sect. */
	static int tanding(ServerPlayer player) {
		ServerLevel level = player.level();
		DuniaAnchorEntity arena = DuniaAnchors.nearest(level, player.position(), "kompleks/arena", 16.0D);
		RencanaKompleks.Kompleks k = arena == null ? null : Kompleks.dari(level, arena);
		if (arena == null || k == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kompleks.tanding.jauh").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (!KompleksSikap.RAMAH.contains(KompleksSikap.sikap(player, k.sekte()))) {
			Msg.chat(player, Component.translatable("kultivator.kompleks.tanding.bukan_sahabat", KompleksSikap.namaSekte(k.sekte()))
					.withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		KultivatorNpc partner = KompleksSikap.terdekat(level, arena, 32.0D);
		if (partner == null) {
			if (KompleksAnchors.tubuhKu(k.sekte()) >= KompleksConfig.get().tubuhMaks) {
				Msg.actionBar(player, Component.translatable("kultivator.kompleks.tanding.kosong").withStyle(ChatFormatting.GRAY));
				return 0;
			}
			partner = HidupApi.tubuh(level, Kompleks.situs(level, k), NpcRole.MURID_SEKTE_DALAM, arena.blockPosition(), arena.getYRot());
			if (partner == null) {
				Msg.actionBar(player, Component.translatable("kultivator.kompleks.tanding.kosong").withStyle(ChatFormatting.GRAY));
				return 0;
			}
			SosokApi.setRupa(partner, KompleksAnchors.rupa(partner, k.sekte()));
			KompleksAnchors.catatTubuh(k.sekte(), partner);
		}
		return mulaiTanding(player, partner, k);
	}

	/** A friendly bout with {@code npc} (a sect body the player talks to, story effect {@code tanding_sekte}). */
	static int tandingDengan(ServerPlayer player, KultivatorNpc npc) {
		ServerLevel level = player.level();
		RencanaKompleks.Kompleks k = KompleksApi.kompleksDi(level, npc.blockPosition());
		if (k == null || !KompleksSikap.anggota(npc)) {
			Msg.actionBar(player, Component.translatable("kultivator.kompleks.tanding.jauh").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (!KompleksSikap.RAMAH.contains(KompleksSikap.sikap(player, k.sekte()))) {
			Msg.chat(player, Component.translatable("kultivator.kompleks.tanding.bukan_sahabat", KompleksSikap.namaSekte(k.sekte()))
					.withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		return mulaiTanding(player, npc, k);
	}

	private static int mulaiTanding(ServerPlayer player, KultivatorNpc partner, RencanaKompleks.Kompleks k) {
		DuelStyle style = switch (k.aliran()) {
			case "lurus" -> partner.role() == NpcRole.TETUA_SEKTE ? DuelStyle.SANTO_PEDANG : DuelStyle.BERTAHAN;
			case "iblis" -> DuelStyle.AGRESIF;
			default -> DuelStyle.PENIPU;
		};
		String sekte = k.sekte();
		Component why = TandingTempat.mulai(player, partner, style, SekteApi.diplomasi().color(sekte), TandingTempat.pedang(), false,
				(p, npc, menang, kalah) -> hasilTanding(p, npc, sekte, menang, kalah));
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
			return 0;
		}
		HidupApi.tatap(partner, player);
		partner.bow(20);
		Msg.chat(player, Component.translatable("kultivator.kompleks.tanding.mulai", partner.getDisplayName()).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	/** A friendly bout ended: standing +{@code kompleksSekte.tandingStanding} once a day per sect, win or lose. */
	private static void hasilTanding(ServerPlayer player, KultivatorNpc npc, String sekte, boolean menang, boolean kalah) {
		Component sect = KompleksSikap.namaSekte(sekte);
		if (!menang && !kalah) {
			Msg.chat(player, Component.translatable("kultivator.kompleks.tanding.seri", npc.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		npc.bow(40);
		long day = Kota.day(player.level().getServer());
		int n = KompleksConfig.get().tandingStanding;
		boolean counts = n > 0 && KompleksData.get(player.level().getServer()).ambilTanding(player.getUUID(), sekte, day);
		if (counts) {
			SekteApi.diplomasi().adjust(player.getUUID(), sekte, n, "tanding_persahabatan");
		}
		Component line = Component.translatable(menang ? "kultivator.kompleks.tanding.menang" : "kultivator.kompleks.tanding.kalah", npc.getDisplayName(), sect);
		Msg.chat(player, line.copy().withStyle(menang ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
		if (counts) {
			Msg.chat(player, Component.translatable("kultivator.kompleks.tanding.standing", sect, n).withStyle(ChatFormatting.GREEN));
		}
	}

	private KompleksCommands() {
	}
}
