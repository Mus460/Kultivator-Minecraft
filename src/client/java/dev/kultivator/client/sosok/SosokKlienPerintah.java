package dev.kultivator.client.sosok;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.kultivator.client.sekte.seragam.LambangCache;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.RupaKunci;
import dev.kultivator.sosok.Sosok;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * The client command {@code /sosok} (DESIGN 40.6.4, no op; pattern of {@code EpicClientCommands}):
 * <ul>
 *   <li>{@code info}: detail level, bodies per LOD, full-Gerak and spring counts against their caps, atlases n/max and
 *       MiB, composing average/max over the last 5 s, the queue;</li>
 *   <li>{@code lod}: a small LOD label above every Sosok body for 30 s;</li>
 *   <li>{@code dump}: writes the atlas of the body under the crosshair to {@code <gameDir>/screenshots/sosok/<hex>.png};
 *       {@code dump benih <sosok> <rupa>} composes the given packed Sosok with a look key and writes it (compare it with
 *       {@code tools/gen_sosok_pratinjau.py --benih});</li>
 *   <li>{@code muat_ulang}: releases every atlas (40K reads its recipe and part sheets again on the next request);</li>
 *   <li>{@code bingkai}: outlines of the body and busana cubes for 30 s (UV check);</li>
 *   <li>{@code detail <otomatis|tinggi|sedang|rendah>}, {@code wajah nyala|mati}: the two settings rows, as a fallback.</li>
 * </ul>
 */
final class SosokKlienPerintah {

	private static final String K = "kultivator.sosok.klien.";
	/** One atlas: 128 × 128 RGBA on the GPU plus its NativeImage copy in RAM. */
	private static final float MIB_PER_ATLAS = 128.0F * 128.0F * 4.0F * 2.0F / (1024.0F * 1024.0F);

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("sosok");
		root.then(ClientCommands.literal("info").executes(SosokKlienPerintah::info));
		root.then(ClientCommands.literal("lod").executes(context -> {
			SosokDetail.nyalakanLabelLod();
			return ok(context, Component.translatable(K + "lod"));
		}));
		root.then(ClientCommands.literal("dump")
				.executes(SosokKlienPerintah::dumpTatap)
				.then(ClientCommands.literal("benih")
						.then(ClientCommands.argument("sosok", IntegerArgumentType.integer())
								.then(ClientCommands.argument("rupa", StringArgumentType.greedyString())
										.executes(context -> dumpBenih(context, IntegerArgumentType.getInteger(context, "sosok"),
												StringArgumentType.getString(context, "rupa")))))));
		root.then(ClientCommands.literal("muat_ulang").executes(context -> {
			SosokClient.lepas();
			SosokJembatan.muatUlangGerak();
			return ok(context, Component.translatable(K + "muat_ulang"));
		}));
		root.then(ClientCommands.literal("bingkai").executes(context -> {
			SosokDetail.nyalakanBingkai();
			return ok(context, Component.translatable(K + "bingkai"));
		}));
		LiteralArgumentBuilder<FabricClientCommandSource> detail = ClientCommands.literal("detail");
		for (SosokDetail.Setelan setelan : SosokDetail.Setelan.values()) {
			detail.then(ClientCommands.literal(setelan.id()).executes(context -> {
				SosokDetail.setSetelan(setelan);
				return ok(context, Component.translatable(K + "detail", Component.translatable("kultivator.sosok.setelan.detail." + setelan.id())));
			}));
		}
		root.then(detail);
		root.then(ClientCommands.literal("wajah")
				.then(ClientCommands.literal("nyala").executes(context -> wajah(context, true)))
				.then(ClientCommands.literal("mati").executes(context -> wajah(context, false))));
		dispatcher.register(root);
	}

	private static int wajah(CommandContext<FabricClientCommandSource> context, boolean on) {
		SosokDetail.setWajah(on);
		return ok(context, Component.translatable(K + (on ? "wajah.nyala" : "wajah.mati")));
	}

	// ================================================================ info

	private static int info(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		SosokDetail.Tingkat t = SosokDetail.tingkat();
		source.sendFeedback(Component.translatable(K + "info.judul").withStyle(ChatFormatting.GOLD));
		source.sendFeedback(Component.translatable(K + "info.tingkat", Component.translatable("kultivator.sosok.setelan.detail." + t.id()),
				Component.translatable("kultivator.sosok.setelan.detail." + SosokDetail.setelan().id())).withStyle(ChatFormatting.GRAY));
		source.sendFeedback(Component.translatable(K + "info.lod", SosokDetail.jumlahLod(SosokDetail.Lod.PENUH),
				SosokDetail.jumlahLod(SosokDetail.Lod.KURANG), SosokDetail.jumlahLod(SosokDetail.Lod.SILUET),
				SosokDetail.jumlahLod(SosokDetail.Lod.JAUH), t.penuh, t.kurang, t.siluet).withStyle(ChatFormatting.GRAY));
		source.sendFeedback(Component.translatable(K + "info.gerak", SosokDetail.jumlahGerakPenuh(), t.gerakPenuhMaks,
				SosokDetail.jumlahPegas(), t.pegasMaks).withStyle(ChatFormatting.GRAY));
		source.sendFeedback(Component.translatable(K + "info.katalog", SosokJembatan.jumlahKlip(),
				SosokJembatan.jumlahKeadaanGerak()).withStyle(ChatFormatting.GRAY));
		int atlas = SosokJembatan.jumlahAtlas();
		source.sendFeedback(Component.translatable(K + "info.atlas", atlas, t.atlasMaks,
				String.format(Locale.ROOT, "%.1f", atlas * MIB_PER_ATLAS)).withStyle(ChatFormatting.GRAY));
		float[] susun = SosokDetail.statistikSusun();
		source.sendFeedback(Component.translatable(K + "info.susun", String.format(Locale.ROOT, "%.2f", susun[0]),
				String.format(Locale.ROOT, "%.2f", susun[1]), (int) susun[2], t.susunPerFrame,
				String.format(Locale.ROOT, "%.1f", t.susunMs)).withStyle(ChatFormatting.GRAY));
		source.sendFeedback(Component.translatable(K + "info.antrean", SosokDetail.antrean(), SosokDetail.ANTREAN_MAKS,
				SosokRender.jumlahDilacak()).withStyle(ChatFormatting.GRAY));
		if (SosokJembatan.kulitRusak()) {
			source.sendFeedback(Component.translatable(K + "info.rusak").withStyle(ChatFormatting.RED));
		}
		if (SosokJembatan.gerakRusak()) {
			source.sendFeedback(Component.translatable(K + "info.gerak_rusak").withStyle(ChatFormatting.RED));
		}
		return 1;
	}

	// ================================================================ dump

	private static int dumpTatap(CommandContext<FabricClientCommandSource> context) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity target = minecraft.crosshairPickEntity;
		SosokRender.Keadaan k = target == null ? null : SosokRender.dariId(target.getId());
		if (k == null || !k.kulit || k.atlas == null) {
			return gagal(context, Component.translatable(K + "dump.tidak_ada"));
		}
		Identifier id = k.atlas;
		String nama = id.getPath().startsWith("sosok/") ? id.getPath().substring("sosok/".length()) : namaBerkas(id.getPath());
		AbstractTexture texture = minecraft.getTextureManager().getTexture(id);
		if (texture instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
			return tulis(context, dynamic.getPixels(), nama, false);
		}
		// a fallback atlas is a resource texture: read its PNG
		Optional<Resource> resource = minecraft.getResourceManager().getResource(id);
		if (resource.isEmpty()) {
			return gagal(context, Component.translatable(K + "dump.tidak_ada"));
		}
		try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
			return tulis(context, image, "cadangan_" + nama, false);
		} catch (IOException e) {
			return gagal(context, Component.translatable(K + "dump.gagal", e.getMessage()));
		}
	}

	private static int dumpBenih(CommandContext<FabricClientCommandSource> context, int packed, String rupa) {
		Sosok sosok = Sosok.unpack(packed);
		if (sosok == null) {
			return gagal(context, Component.translatable(K + "dump.benih_salah"));
		}
		RupaKunci kunci = RupaKunci.parse(rupa);
		if (kunci.kosong()) {
			return gagal(context, Component.translatable(K + "dump.benih_salah"));
		}
		LambangCache.Entry seragam = kunci.sekte() == null ? null : LambangCache.get(kunci.sekte());
		int pangkat = kunci.pangkat() < 0 ? 0 : kunci.pangkat();
		SosokRender.RupaMasukan masukan = new SosokRender.RupaMasukan(kunci.format(), 1, kunci.dao(), pangkat, 0, seragam, 0, 0, false);
		Object look = SosokJembatan.rupa(sosok, Gaya.NETRAL, masukan);
		NativeImage image = SosokJembatan.susun(look);
		if (image == null) {
			return gagal(context, Component.translatable(K + "dump.gagal", "40K"));
		}
		try (image) {
			String nama = String.format(Locale.ROOT, "benih_%08x_%s", packed, namaBerkas(kunci.format()));
			return tulis(context, image, nama, true);
		}
	}

	private static int tulis(CommandContext<FabricClientCommandSource> context, NativeImage image, String nama, boolean benih) {
		Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("sosok");
		Path file = dir.resolve(nama + ".png");
		try {
			Files.createDirectories(dir);
			image.writeToFile(file);
		} catch (IOException e) {
			return gagal(context, Component.translatable(K + "dump.gagal", e.getMessage()));
		}
		return ok(context, Component.translatable(K + (benih ? "dump.benih" : "dump.ok"), file.toString()));
	}

	/** A file-name-safe form of a key or path ({@code murid.2|sekte=u:..} -> {@code murid.2_sekte_u_..}). */
	private static String namaBerkas(String text) {
		String s = text;
		int slash = s.lastIndexOf('/');
		if (slash >= 0) {
			s = s.substring(slash + 1);
		}
		if (s.endsWith(".png")) {
			s = s.substring(0, s.length() - 4);
		}
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length() && out.length() < 64; i++) {
			char c = s.charAt(i);
			out.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' ? c : '_');
		}
		return out.toString();
	}

	private static int ok(CommandContext<FabricClientCommandSource> context, Component message) {
		context.getSource().sendFeedback(message.copy().withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int gagal(CommandContext<FabricClientCommandSource> context, @Nullable Component message) {
		if (message != null) {
			context.getSource().sendError(message);
		}
		return 0;
	}

	private SosokKlienPerintah() {
	}
}
