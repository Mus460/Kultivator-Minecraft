package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.data.Rank;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.api.HatiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.Kehatian;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.jetbrains.annotations.Nullable;

/**
 * What a journey's plan knows of one traveller at the gate (DESIGN 39.6.2): its traits, path, strength, purse, free bag
 * slots, caution and shopping list. Stages are resolved while the body is in {@code AwayStore}, so they read this
 * snapshot instead of the tablet data; results go back through the return handler.
 * <p>
 * Strength is the expedition rule ({@code Expeditions.memberPower}, package-private there, copied as a pure function):
 * realm + stage/4 + layer/9 + stars/9. Caution is the policy's, one step bolder for the brave and one more careful for
 * the timid, never bolder than the policy (39.5.9).
 */
public final class Watak {

	/** Bitmask of the disciple's traits ({@code 1 << Sifat.ordinal()}). */
	public static int sifat(DiscipleData data) {
		HatiService hati = SekteApi.hati();
		int mask = 0;
		for (Sifat s : Sifat.values()) {
			if (hati.has(data, s)) {
				mask |= 1 << s.ordinal();
			}
		}
		return mask;
	}

	public static boolean punya(int mask, Sifat s) {
		return s != null && (mask & (1 << s.ordinal())) != 0;
	}

	/** {@code Expeditions.memberPower}: realm + stage/4 + layer/9 + stars/9. */
	public static double kekuatan(DiscipleData data) {
		Rank rank = data.rank();
		return rank.realm().ordinal() + rank.stage() / 4.0D + rank.layer() / 9.0D + DisciplePaths.stars(data) / 9.0D;
	}

	/** The caution of a disciple: the policy's, shifted by BERANI/PENAKUT but never bolder than the policy. */
	public static Kehatian kehatian(Kehatian policy, int sifat) {
		Kehatian k = policy;
		if (punya(sifat, Sifat.PENAKUT)) {
			k = k.geser(-1);
		}
		if (punya(sifat, Sifat.BERANI)) {
			Kehatian bolder = k.geser(1);
			if (bolder.ordinal() <= policy.ordinal()) {
				k = bolder;
			}
		}
		return k;
	}

	/** The gate snapshot of {@code murid} for the plan. */
	public static CompoundTag anggota(DiscipleEntity murid) {
		DiscipleData data = murid.data();
		int sifat = sifat(data);
		CompoundTag tag = new CompoundTag();
		tag.putString("soul", murid.soulId().toString());
		tag.putString("nama", murid.getName().getString());
		tag.putInt("band", Tas.band(data));
		tag.putDouble("power", kekuatan(data));
		tag.putLong("uang", MandiriApi.dompet(data));
		tag.putInt("ruang", Tas.ruang(data, murid.level().registryAccess()));
		tag.putInt("sifat", sifat);
		JalanHidup jalan = MandiriApi.jalan(data);
		tag.putString("jalan", jalan == null ? "" : jalan.id());
		tag.putInt("iblis", MandiriApi.iblis(data));
		tag.putBoolean("perempuan", SosokApi.perempuan(murid));
		tag.putString("kehatian", kehatian(MandiriApi.kebijakan(murid).risiko(), sifat).id());
		tag.putInt("jenjang", SectRanks.rank(data));
		tag.putInt("lencana", MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_LENCANA).getIntOr("pangkat", 0));
		tag.putInt("pedang", Perlengkapan.pedang(data));
		tag.putInt("dao", data.dao() == null ? -1 : data.dao().ordinal());
		ListTag cita = new ListTag();
		ListTag active = MandiriApi.bagian(data).getListOrEmpty(MandiriIds.K_CITA);
		for (int i = 0; i < active.size(); i++) {
			cita.add(StringTag.valueOf(active.getCompoundOrEmpty(i).getStringOr("jenis", "")));
		}
		tag.put("cita", cita);
		ListTag daftar = new ListTag();
		for (DaftarBelanja.Baris b : DaftarBelanja.baca(data)) {
			daftar.add(b.toTag());
		}
		tag.put("daftar", daftar);
		ListTag dagangan = new ListTag();
		if (murid.ownerId() != null) {
			for (net.minecraft.world.item.ItemStack stack : MandiriApi.dagangan(murid.ownerId(), murid.soulId())) {
				if (stack.isEmpty() || Tas.dibeliMurid(stack) || dagangan.size() >= 4) {
					continue;
				}
				CompoundTag one = new CompoundTag();
				one.putString("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
				one.putInt("n", stack.getCount());
				one.putLong("nilai", dev.kultivator.dunia.kota.Harga.value(stack));
				dagangan.add(one);
			}
		}
		tag.put("dagangan", dagangan);
		tag.putBoolean("jimatPerisai", Tas.adaJimat(data, murid.level().registryAccess(), dev.kultivator.talisman.TalismanKind.SHIELD));
		tag.putBoolean("jimatKembali", Tas.adaJimat(data, murid.level().registryAccess(), dev.kultivator.talisman.TalismanKind.RETURN));
		tag.putBoolean("jimatLi", Tas.adaJimat(data, murid.level().registryAccess(), dev.kultivator.talisman.TalismanKind.THOUSAND_MILE));
		return tag;
	}

	public static @Nullable JalanHidup jalan(CompoundTag anggota) {
		return JalanHidup.byId(anggota.getStringOr("jalan", ""));
	}

	public static Kehatian kehatian(CompoundTag anggota) {
		return Kehatian.byId(anggota.getStringOr("kehatian", ""), Kehatian.WAJAR);
	}

	public static boolean punyaCita(CompoundTag anggota, String jenis) {
		ListTag cita = anggota.getListOrEmpty("cita");
		for (int i = 0; i < cita.size(); i++) {
			if (jenis.equals(cita.getStringOr(i, ""))) {
				return true;
			}
		}
		return false;
	}

	private Watak() {
	}
}
