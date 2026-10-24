package dev.kultivator.dunia.tempat.tema.oasis;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.tempat.tema.TemaCommands;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * {@code /kultivasi uji tema unta ...} (DESIGN 38.T.13.2, theme word of the oasis): tests of the Kota Oasis without
 * waiting for the calendar. Operators only (the {@code uji tema} root requires level 2).
 * <pre>
 * unta                 status of the nearest oasis: centre, storm, caravan, festival, lake, stalls
 * unta sewa [hari]     hires a riding camel at the player (paid like the pen)
 * unta kafilah tiba|pergi|quest   starts a caravan arrival, a departure, or the chain's stage-2 caravan
 * unta badai           a sandstorm now (omen first)      unta reda   ends it
 * unta tari            the whirling-dance festival now    unta main  opens the rhythm game
 * unta raja [band]     a Raja Kalajengking at the player (band 1-4)
 * unta mural <1-12>    reads a mural (comprehension once)  unta air   restores the lake (stage 6 rise)
 * </pre>
 */
final class OasisCommands {

	static void init() {
		TemaCommands.daftar(OasisKota.TEMA, OasisCommands::node);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("unta")
				.executes(OasisCommands::info)
				.then(Commands.literal("sewa").executes(ctx -> sewa(ctx, 1))
						.then(Commands.argument("hari", IntegerArgumentType.integer(1, KafilahUnta.SEWA_HARI_MAKS))
								.executes(ctx -> sewa(ctx, IntegerArgumentType.getInteger(ctx, "hari")))))
				.then(Commands.literal("kafilah")
						.then(Commands.literal("tiba").executes(ctx -> kafilah(ctx, false, false)))
						.then(Commands.literal("pergi").executes(ctx -> kafilah(ctx, true, false)))
						.then(Commands.literal("quest").executes(ctx -> kafilah(ctx, true, true))))
				.then(Commands.literal("badai").executes(ctx -> badai(ctx, true)))
				.then(Commands.literal("reda").executes(ctx -> badai(ctx, false)))
				.then(Commands.literal("tari").executes(OasisCommands::tari))
				.then(Commands.literal("main").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					return TariPutar.mainkan(p, null) ? 1 : 0;
				}))
				.then(Commands.literal("raja").executes(ctx -> raja(ctx, 0))
						.then(Commands.argument("band", IntegerArgumentType.integer(1, 4))
								.executes(ctx -> raja(ctx, IntegerArgumentType.getInteger(ctx, "band")))))
				.then(Commands.literal("mural").then(Commands.argument("no", IntegerArgumentType.integer(1, Mural.JUMLAH)).executes(ctx -> {
					Mural.baca(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "no"));
					return 1;
				})))
				.then(Commands.literal("air").executes(OasisCommands::air));
	}

	private static OasisKota.Hidup kota(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		OasisKota.Hidup kota = OasisKota.di(p);
		if (kota == null && p.level() instanceof ServerLevel level) {
			kota = OasisKota.terdekat(level, p.position(), 4000.0D);
		}
		if (kota == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota_tema.oasis.uji.tidak_ada"));
		}
		return kota;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		OasisKota.Hidup kota = kota(ctx);
		if (kota == null) {
			return 0;
		}
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) p.level();
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota_tema.oasis.uji.info", OasisNames.nama(level.getServer(), kota.situs),
				kota.pusat.toShortString(), (int) kota.jarak(p.position()), BadaiPasir.aktif(kota.situs) ? "ya" : "-",
				KafilahUnta.berjalan(kota.situs) ? "ya" : "-", TariPutar.festival(kota.situs) ? "ya" : "-",
				KuilAir.pulih(level.getServer(), kota.situs) ? "pulih" : "turun", kota.jangkar.size()), false);
		return 1;
	}

	private static int sewa(CommandContext<CommandSourceStack> ctx, int hari) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		return KafilahUnta.sewa(p, hari, p.position().add(p.getLookAngle().scale(2.0D))) ? 1 : 0;
	}

	private static int kafilah(CommandContext<CommandSourceStack> ctx, boolean pergi, boolean quest) throws CommandSyntaxException {
		OasisKota.Hidup kota = kota(ctx);
		if (kota == null) {
			return 0;
		}
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		boolean ok = quest ? KafilahUnta.mulaiQuest(p) : KafilahUnta.mulai((ServerLevel) p.level(), kota.situs, pergi, false, null);
		ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "kultivator.kota_tema.oasis.uji.ok" : "kultivator.kota_tema.oasis.uji.gagal"), false);
		return ok ? 1 : 0;
	}

	private static int badai(CommandContext<CommandSourceStack> ctx, boolean mulai) throws CommandSyntaxException {
		OasisKota.Hidup kota = kota(ctx);
		if (kota == null) {
			return 0;
		}
		ServerLevel level = (ServerLevel) ctx.getSource().getPlayerOrException().level();
		if (mulai) {
			boolean ok = BadaiPasir.mulai(level, kota.situs, true);
			ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "kultivator.kota_tema.oasis.uji.ok" : "kultivator.kota_tema.oasis.uji.gagal"), false);
			return ok ? 1 : 0;
		}
		BadaiPasir.akhir(level, kota.situs);
		return 1;
	}

	private static int tari(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		OasisKota.Hidup kota = kota(ctx);
		if (kota == null) {
			return 0;
		}
		ServerLevel level = (ServerLevel) ctx.getSource().getPlayerOrException().level();
		boolean ok = TariPutar.festival(kota.situs) ? endTari(level, kota) : TariPutar.mulai(level, kota.situs, true);
		ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "kultivator.kota_tema.oasis.uji.ok" : "kultivator.kota_tema.oasis.uji.gagal"), false);
		return ok ? 1 : 0;
	}

	private static boolean endTari(ServerLevel level, OasisKota.Hidup kota) {
		TariPutar.batal(level, kota.situs);
		return true;
	}

	private static int raja(CommandContext<CommandSourceStack> ctx, int band) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) p.level();
		KalajengkingRajaEntity raja = TemaEntities.KALAJENGKING_RAJA.create(level, EntitySpawnReason.COMMAND);
		if (raja == null) {
			return 0;
		}
		raja.snapTo(p.getX() + p.getLookAngle().x * 6.0D, p.getY(), p.getZ() + p.getLookAngle().z * 6.0D, p.getYRot() + 180.0F, 0.0F);
		raja.setup(band > 0 ? band : Reruntuhan.tingkatBos(p.blockPosition()), p.blockPosition().asLong(), false);
		level.addFreshEntity(raja);
		return 1;
	}

	private static int air(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		OasisKota.Hidup kota = kota(ctx);
		if (kota == null) {
			return 0;
		}
		KuilAir.naik((ServerLevel) ctx.getSource().getPlayerOrException().level(), kota.situs);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota_tema.oasis.uji.ok"), false);
		return 1;
	}

	private OasisCommands() {
	}
}
