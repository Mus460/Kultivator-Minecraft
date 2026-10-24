package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Night Market of the East Market (DESIGN 38.B.6, every evening 12500-18000): the twelve stalls of the
 * {@code pasar_malam} lot open and their paper lanterns burn on clients (the stall points of the lot's stage anchor),
 * the stall keepers' wares are 38EK's rotating stock, and the townsfolk stroll the stalls (schedule override
 * {@code pasar_malam}). A small event: it never takes a great event's day.
 */
final class PasarMalam implements KotaBesarKalender.Acara {

	static final String ID = LembarKunci.Acara.ACARA_PASAR_MALAM;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int prioritas() {
		return 0;
	}

	@Override
	public boolean hariAsli(MinecraftServer server, long town, long day) {
		return !GelombangBinatang.tutup(town);
	}

	@Override
	public long mulaiJam() {
		return 12500L;
	}

	@Override
	public long akhirJam() {
		return 18000L;
	}

	@Override
	public void mulai(ServerLevel level, Kandidat k, long town, boolean hidup) {
		if (hidup) {
			KotaBesarPayload.sekitar(level, new Vec3(k.pusatX(), 64.0D, k.pusatZ()), "pasar_malam", data(level, k, town, true));
		}
	}

	@Override
	public void akhir(ServerLevel level, Kandidat k, long town, boolean hidup) {
		KotaBesarPayload.sekitar(level, new Vec3(k.pusatX(), 64.0D, k.pusatZ()), "pasar_malam", data(level, k, town, false));
	}

	/** The market picture: the stall points to light ({@code lapak} IA). */
	static CompoundTag data(ServerLevel level, Kandidat k, long town, boolean aktif) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("aktif", aktif);
		tag.putLong("town", town);
		if (aktif) {
			List<Integer> pts = new ArrayList<>();
			KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
			DuniaAnchorEntity stage = kota == null ? null : kota.panggung(ID);
			if (stage != null) {
				for (BlockPos p : KotaBesarAnchors.titikPanggung(level, stage)) {
					pts.add(p.getX());
					pts.add(p.getY());
					pts.add(p.getZ());
				}
			}
			tag.putIntArray("lapak", pts.stream().mapToInt(Integer::intValue).toArray());
		}
		return tag;
	}

	@Override
	public void lembar(ServerPlayer player, long town, @Nullable KultivatorNpc npc, CompoundTag tag) {
		LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.acara.pasar_malam.catatan"));
	}
}
