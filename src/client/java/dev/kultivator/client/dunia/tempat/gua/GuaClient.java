package dev.kultivator.client.dunia.tempat.gua;

import dev.kultivator.client.dunia.DuniaScenes;
import dev.kultivator.client.dunia.tempat.model.HiasanDunia;
import dev.kultivator.client.dunia.tempat.model.KaitModel;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.dunia.tempat.gua.GuaPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Client side of 38GU caves and natural sites (DESIGN 38.C.4, 38.C.5): sets the 38MD model hooks of the cave
 * mechanisms ({@code KaitModel}: cage door, glyph plates, cliff door stones, tomb puppets) to the synced state of the
 * entities, and draws {@link GuaPayload}: the entry banner of a cave (red below its suggested realm), stamp cards of
 * passed trials, and the client decorations of {@code HiasanDunia} (the star stage's armillary sphere, incense smoke).
 * Every picture is a title card or a decoration under {@code EffectQuality}, no post chain; the one camera move is the
 * wide pull-back over Panggung Bintang after a passed star calamity ({@link GuaPayload#KAMERA}), a {@link DuniaScenes}
 * world moment (ShotGuard refuses it near danger, Kamera sinematik Penuh / Singkat / Mati, at most 4 s, Backspace
 * skips; with no shot only the stamp card shows). Called once from {@code TempatClient.init()}.
 */
public final class GuaClient {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		KaitModel.sangkarBuka = (cage, pt) -> cage.buka(pt);
		KaitModel.glifPutar = (plate, pt) -> plate.putaran(pt);
		KaitModel.glifNyala = (plate, pt) -> plate.cahaya(pt);
		KaitModel.glifJenis = plate -> plate.jenis();
		KaitModel.pintuGeser = (door, pt) -> door.geser(pt);
		KaitModel.bonekaBangkit = (puppet, pt) -> puppet.bangkitan(pt);
		ClientPlayNetworking.registerGlobalReceiver(GuaPayload.TYPE, (payload, context) -> context.client().execute(() -> terima(payload)));
	}

	private static void terima(GuaPayload payload) {
		CompoundTag data = payload.data();
		switch (payload.jenis()) {
			case GuaPayload.KARTU -> kartu(data);
			case GuaPayload.STEMPEL -> TitleCard.show(TitleCard.stamp(Component.translatable(data.getStringOr("judul", "")),
					data.getStringOr("segel", "洞"), data.getIntOr("warna", 0xFFD54A)));
			case GuaPayload.HIAS -> hias(data);
			case GuaPayload.HIAS_HAPUS -> HiasanDunia.hapus(data.getStringOr("grup", ""));
			case GuaPayload.KAMERA -> kamera(data);
			default -> {
			}
		}
	}

	private static void kartu(CompoundTag data) {
		Component nama = Component.translatable("kultivator.gua.tempat." + data.getStringOr("nama", "gua_pertapa"));
		String ranah = data.getStringOr("ranah", "");
		if (ranah.isEmpty()) {
			DuniaScenes.banner(nama);
			return;
		}
		boolean bawah = data.getBooleanOr("bawah", false);
		Component realm = Component.translatable("kultivator.realm." + ranah);
		DuniaScenes.banner(Component.translatable(bawah ? "kultivator.gua.kartu.bahaya" : "kultivator.gua.kartu.ranah", nama, realm)
				.withStyle(bawah ? ChatFormatting.RED : ChatFormatting.WHITE));
	}

	/** A wide pull-back over the player's own moment, then its card; without a scene only the stamp card. */
	private static void kamera(CompoundTag data) {
		Component judul = Component.translatable(data.getStringOr("judul", ""));
		String segel = data.getStringOr("segel", "洞");
		int warna = data.getIntOr("warna", 0xFFD54A);
		Vec3 at = new Vec3(data.getDoubleOr("x", 0.0D), data.getDoubleOr("y", 0.0D), data.getDoubleOr("z", 0.0D));
		int seed = (int) (Double.doubleToLongBits(at.x) ^ Double.doubleToLongBits(at.z) >>> 7);
		Scene scene = DuniaScenes.world(data.getStringOr("kunci", "gua"), at, seed, 100, true);
		if (scene == null) {
			TitleCard.show(TitleCard.stamp(judul, segel, warna));
			return;
		}
		DuniaScenes.pullback(scene, 6, 5.0F, 17.0F, 2.5F, 9.0F, 3.5F);
		String sub = data.getStringOr("sub", "");
		DuniaScenes.card(scene, 24, judul, sub.isEmpty() ? null : Component.translatable(sub), segel, warna);
	}

	private static void hias(CompoundTag data) {
		String grup = data.getStringOr("grup", "");
		if (grup.isEmpty()) {
			return;
		}
		List<HiasanDunia.Hias> isi = new ArrayList<>();
		for (Tag t : data.getListOrEmpty("isi")) {
			if (!(t instanceof CompoundTag h)) {
				continue;
			}
			HiasanDunia.Jenis jenis;
			try {
				jenis = HiasanDunia.Jenis.valueOf(h.getStringOr("j", ""));
			} catch (IllegalArgumentException e) {
				continue;
			}
			isi.add(new HiasanDunia.Hias(jenis, h.getDoubleOr("x", 0.0D), h.getDoubleOr("y", 0.0D), h.getDoubleOr("z", 0.0D),
					h.getFloatOr("yaw", 0.0F), 0xFFFFFF, h.getFloatOr("ukuran", 0.0F)));
		}
		HiasanDunia.pasang(grup, isi);
	}

	private GuaClient() {
	}
}
