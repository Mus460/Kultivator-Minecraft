package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sosok.Sosok;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * What 39F knows about one of our disciples (DESIGN 39.F): its name, traits, realm and sex, read from the loaded body when
 * there is one, else from its away record (a journey keeps the tablet in {@code core.away[].stored}, read only). Writes
 * never go through here: memories and activities of a body that is not loaded wait in {@link Antrean}.
 *
 * @param soul   the disciple's soul
 * @param nama   its name
 * @param data   its data (a detached copy for an away disciple; null when nothing is known)
 * @param tubuh  the loaded body at the hall, or null
 * @param stored the away record's stored tablet (skin, slim, sosok, gaya), or null
 */
record MuridInfo(UUID soul, Component nama, @Nullable DiscipleData data, @Nullable DiscipleEntity tubuh, @Nullable CompoundTag stored) {

	/** Looks up {@code soul} of {@code owner}'s hall. */
	static MuridInfo dari(UUID owner, UUID soul) {
		for (DiscipleEntity body : Disciples.loaded(owner)) {
			if (body.soulId().equals(soul)) {
				return new MuridInfo(soul, body.getName().copy(), body.data(), body, null);
			}
		}
		ListTag away = Halls.section(owner, "core").getListOrEmpty("away");
		for (Tag tag : away) {
			if (tag instanceof CompoundTag rec && soul.equals(rec.read("soul", UUIDUtil.CODEC).orElse(null))) {
				CompoundTag stored = rec.getCompoundOrEmpty("stored");
				CompoundTag disciple = stored.getCompoundOrEmpty("disciple");
				DiscipleData data = disciple.isEmpty() ? null : DiscipleData.load(disciple);
				Component name = rec.read("name", ComponentSerialization.CODEC)
						.orElse(stored.read("name", ComponentSerialization.CODEC).orElse(Component.translatable("entity.kultivator.disciple")));
				return new MuridInfo(soul, name, data, null, stored);
			}
		}
		return new MuridInfo(soul, Component.translatable("entity.kultivator.disciple"), null, null, null);
	}

	boolean punya(Sifat sifat) {
		return data != null && SekteApi.hati().has(data, sifat);
	}

	/** Strength in realm units (rank step / 4); 1.5 when unknown. */
	double kekuatan() {
		return data == null ? 1.5D : data.rank().step() / 4.0D;
	}

	int ranah() {
		return data == null ? 1 : data.realm().ordinal();
	}

	/** Temptation 0-100 (39A). */
	int iblis() {
		return data == null ? 0 : MandiriApi.iblis(data);
	}

	boolean perempuan() {
		if (tubuh != null) {
			return dev.kultivator.sosok.SosokApi.perempuan(tubuh);
		}
		if (stored != null) {
			Sosok sosok = Sosok.unpack(stored.getIntOr("sosok", Sosok.KOSONG));
			if (sosok != null) {
				return sosok.perempuan();
			}
		}
		return false;
	}

	/** True when an active goal of kind {@code jenis} is in its list (39A {@code extra.mandiri.cita}). */
	boolean cita(JenisCita jenis) {
		if (data == null || jenis == null) {
			return false;
		}
		ListTag list = MandiriApi.bagian(data).getListOrEmpty(MandiriIds.K_CITA);
		for (int i = 0; i < list.size(); i++) {
			if (jenis.id().equals(list.getCompoundOrEmpty(i).getStringOr("jenis", ""))) {
				return true;
			}
		}
		return false;
	}

	/** The town body (role JELMAAN, 39C) that shows this disciple near {@code pos}, or null. */
	@Nullable DiscipleEntity jelmaan(ServerLevel level, Vec3 pos) {
		List<DiscipleEntity> found = level.getEntitiesOfClass(DiscipleEntity.class, new AABB(pos, pos).inflate(48.0D),
				e -> e.isAlive() && e.role() == DiscipleEntity.Role.JELMAAN && e.getCustomName() != null
						&& e.getCustomName().getString().equals(nama.getString()));
		return found.isEmpty() ? null : found.get(0);
	}
}
