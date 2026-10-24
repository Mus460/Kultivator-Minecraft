package dev.kultivator.sekte.mandiri.cerita;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.mandiri.CitaCita;
import dev.kultivator.sekte.mandiri.Dorongan;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * The light {@code sekte_mandiri} part of the disciple details ({@code DiscipleHooks.addInfo}, DESIGN 39.7.3, at most about
 * 600 bytes): path, temptation, the reason codes of the path, goals (kind, progress, priority), the seven drives, the
 * running activity and how long it still runs, the effective mode/caution/pocket money, the purse, the order pause, the
 * wound and whether the disciple has its own policy overlay. The diary, the hour strips and the bag come separately on
 * request ({@code mandiri_harian}).
 */
final class InfoMandiri {

	static void info(DiscipleEntity entity, ServerPlayer master, CompoundTag tag) {
		if (entity.role() != DiscipleEntity.Role.DISCIPLE) {
			return;
		}
		CompoundTag out = new CompoundTag();
		out.putBoolean("on", MandiriApi.aktif());
		if (!MandiriApi.aktif()) {
			tag.put(MandiriIds.INFO, out);
			return;
		}
		DiscipleData data = entity.data();
		CompoundTag m = MandiriApi.bagian(data);
		long now = entity.level().getGameTime();
		out.putString("soul", entity.soulId().toString());
		out.putBoolean("own", master != null && master.getUUID().equals(entity.ownerId()));
		out.putString("j", m.getStringOr(MandiriIds.K_JALAN, ""));
		out.putString("ja", m.getStringOr(MandiriIds.K_JALAN_ASAL, ""));
		out.putInt("i", MandiriApi.iblis(data));
		out.put("al", m.getListOrEmpty(MandiriIds.K_JALAN_ALASAN).copy());
		ListTag cita = new ListTag();
		for (CitaCita.Cita c : CitaCita.aktif(data)) {
			CompoundTag t = new CompoundTag();
			t.putString("j", c.jenis().id());
			t.putByte("p", (byte) Math.max(0, Math.min(100, c.prog())));
			t.putByte("r", (byte) c.prio());
			cita.add(t);
		}
		out.put("c", cita);
		out.put("d", new IntArrayTag(Dorongan.baca(data)));
		Kegiatan k = MandiriApi.kegiatan(entity);
		out.putString("k", k == null ? "" : k.id());
		CompoundTag rec = m.getCompoundOrEmpty(MandiriIds.K_KEGIATAN);
		long sampai = rec.getLongOr(MandiriIds.KG_SAMPAI, 0L);
		out.putInt("ks", k == null || sampai <= now ? 0 : (int) Math.min(Integer.MAX_VALUE, sampai - now));
		Kebijakan kb = MandiriApi.kebijakan(entity);
		out.putString("m", kb.mode().id());
		out.putString("r", kb.risiko().id());
		out.putInt("us", kb.uangSakuPersen());
		out.putBoolean("tp", m.contains(MandiriIds.K_KEBIJAKAN));
		out.putLong("dp", MandiriApi.dompet(data));
		long bebas = MandiriApi.bebasSampai(data);
		out.putInt("bb", bebas == Long.MAX_VALUE ? -1 : bebas > now ? (int) Math.min(Integer.MAX_VALUE, bebas - now) : 0);
		out.putString("lk", MandiriApi.luka(data, now).id());
		out.putBoolean("tt", MandiriApi.tutupDiri(data, now));
		tag.put(MandiriIds.INFO, out);
	}

	private InfoMandiri() {
	}
}
