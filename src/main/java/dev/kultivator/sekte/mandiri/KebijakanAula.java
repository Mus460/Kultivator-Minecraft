package dev.kultivator.sekte.mandiri;

import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.mandiri.api.IzinKegiatan;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import dev.kultivator.util.Advancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The master's policy (DESIGN 39.7.1) and "the orders win" (39.7.2): the hall policy lives in
 * {@code MandiriData.<owner>.kebijakan} (written the first time it is read, from {@code mandiri.modeBawaan}); a disciple's
 * overlay lives in its own {@code extra.mandiri.kebijakan} with a copy in {@code MandiriData.<owner>.tumpang.<soul>} so the
 * board and the checks of unloaded disciples read the same thing. Installed as {@code MandiriApi.SumberKebijakan}.
 * <p>
 * Secret realms are off in a new hall but switch themselves on for a disciple from Nascent Soul, until the master sets the
 * permission by hand (the hall record keeps {@code rahasiaManual}). Switching the hall to {@link ModeMandiri#PATUH} ends
 * every running planner activity of that hall at once ({@code Perencana.batalSemua}): behaviour exactly as before 39.
 */
public final class KebijakanAula implements MandiriApi.SumberKebijakan {

	static final KebijakanAula INSTANCE = new KebijakanAula();

	private static final String RAHASIA_MANUAL = "rahasiaManual";

	@Override
	public Kebijakan aula(UUID owner) {
		MinecraftServer server = Perencana.server();
		if (server == null || owner == null) {
			return bawaan();
		}
		CompoundTag tag = MandiriData.get(server).bagian(owner, MandiriIds.A_KEBIJAKAN);
		if (tag.isEmpty()) {
			Kebijakan fresh = bawaan();
			tag.merge(fresh.toTag());
			MandiriData.get(server).setDirty();
			return fresh;
		}
		return Kebijakan.fromTag(tag, bawaan());
	}

	@Override
	public @Nullable CompoundTag tumpang(UUID owner, UUID soul) {
		MinecraftServer server = Perencana.server();
		if (server == null || owner == null || soul == null) {
			return null;
		}
		CompoundTag copies = MandiriData.get(server).aula(owner).getCompoundOrEmpty(MandiriIds.A_TUMPANG);
		return copies.getCompound(soul.toString()).orElse(null);
	}

	private static Kebijakan bawaan() {
		MandiriConfig config = MandiriConfig.get();
		return Kebijakan.bawaan(config.modeAwal(), config.tungguGuruHari, config.tungguDaoHari);
	}

	// ================================================================== writers

	/**
	 * Sets the hall policy ({@code actor}, when given, earns {@code mandiri_kebijakan}). Patuh ends every planner activity
	 * of the hall now; a change of the secret-realm bit marks it as set by hand.
	 */
	public static void setAula(MinecraftServer server, UUID owner, Kebijakan baru, @Nullable ServerPlayer actor) {
		if (server == null || owner == null || baru == null) {
			return;
		}
		MandiriData store = MandiriData.get(server);
		Kebijakan lama = INSTANCE.aula(owner);
		CompoundTag tag = store.bagian(owner, MandiriIds.A_KEBIJAKAN);
		boolean manual = tag.getBooleanOr(RAHASIA_MANUAL, false)
				|| IzinKegiatan.ALAM_RAHASIA.boleh(lama.izin()) != IzinKegiatan.ALAM_RAHASIA.boleh(baru.izin());
		for (String key : tag.keySet().toArray(new String[0])) {
			tag.remove(key);
		}
		tag.merge(baru.toTag());
		if (manual) {
			tag.putBoolean(RAHASIA_MANUAL, true);
		}
		store.setDirty();
		if (baru.mode() == ModeMandiri.PATUH && lama.mode() != ModeMandiri.PATUH) {
			Perencana.batalSemua(owner);
		}
		if (lama.mode() != baru.mode()) {
			MandiriApi.kabar(server, owner, null, "kultivator.sekte.mandiri.inti.kabar.mode", false, baru.mode().kunciNama());
			Halls.log(owner, Component.translatable("kultivator.sekte.mandiri.inti.log.mode",
					Component.translatable(baru.mode().kunciNama())));
		}
		ServerPlayer player = actor != null ? actor : server.getPlayerList().getPlayer(owner);
		if (player != null && player.getUUID().equals(owner)) {
			Advancements.grant(player, SekteIds.ADVANCEMENT_FOLDER + MandiriIds.ADV_KEBIJAKAN);
		}
	}

	/** Sets a disciple's overlay (only the keys present override) or removes it ({@code tumpang} null: "Ikut aula"). */
	public static void setTumpang(DiscipleEntity entity, @Nullable CompoundTag tumpang) {
		UUID owner = entity.ownerId();
		MinecraftServer server = Perencana.server();
		CompoundTag section = MandiriApi.bagianTulis(entity.data());
		if (tumpang == null || tumpang.isEmpty()) {
			section.remove(MandiriIds.K_KEBIJAKAN);
		} else {
			section.put(MandiriIds.K_KEBIJAKAN, tumpang.copy());
		}
		if (server != null && owner != null) {
			CompoundTag copies = MandiriData.get(server).bagian(owner, MandiriIds.A_TUMPANG);
			if (tumpang == null || tumpang.isEmpty()) {
				copies.remove(entity.soulId().toString());
			} else {
				copies.put(entity.soulId().toString(), tumpang.copy());
			}
			MandiriData.get(server).setDirty();
		}
		if (MandiriApi.kebijakan(entity).mode() == ModeMandiri.PATUH) {
			Perencana.batal(entity, true);
		}
	}

	/** One text key of a disciple's overlay ({@code mode}, {@code risiko}, {@code jenjang}); null removes it. */
	public static void setTumpangKunci(DiscipleEntity entity, String key, @Nullable String value) {
		CompoundTag now = MandiriApi.bagian(entity.data()).getCompoundOrEmpty(MandiriIds.K_KEBIJAKAN).copy();
		if (value == null) {
			now.remove(key);
		} else {
			now.putString(key, value);
		}
		setTumpang(entity, now);
	}

	/** One number key of a disciple's overlay ({@code uangSaku}, {@code izin}, {@code tungguTerobosan} ...). */
	public static void setTumpangAngka(DiscipleEntity entity, String key, int value) {
		CompoundTag now = MandiriApi.bagian(entity.data()).getCompoundOrEmpty(MandiriIds.K_KEBIJAKAN).copy();
		now.putInt(key, value);
		setTumpang(entity, now);
	}

	/**
	 * "Bebaskan ke Jalan Hidup" (39.7.2): the order pause ends now and the disciple stays where it stands (STAY, the
	 * calmest old behaviour) until the planner gives it something.
	 */
	public static void bebaskan(DiscipleEntity entity) {
		if (entity == null || entity.role() != DiscipleEntity.Role.DISCIPLE) {
			return;
		}
		MandiriApi.setBebas(entity.data(), 0L);
		if (entity.mode() != DiscipleEntity.Mode.STAY) {
			MandiriApi.setModeSendiri(entity, DiscipleEntity.Mode.STAY);
		}
		Perencana.segera(entity);
	}

	/**
	 * The permission mask this disciple really has: the effective policy, plus secret realms from Nascent Soul while the
	 * master never set that bit by hand.
	 */
	public static int izinEfektif(DiscipleEntity entity, Kebijakan kebijakan) {
		int izin = kebijakan.izin();
		UUID owner = entity.ownerId();
		MinecraftServer server = Perencana.server();
		if (server != null && owner != null && entity.data().realm().ordinal() >= Realm.NASCENT_SOUL.ordinal()
				&& !MandiriData.get(server).bagian(owner, MandiriIds.A_KEBIJAKAN).getBooleanOr(RAHASIA_MANUAL, false)
				&& !MandiriApi.bagian(entity.data()).getCompoundOrEmpty(MandiriIds.K_KEBIJAKAN).contains("izin")) {
			izin |= IzinKegiatan.ALAM_RAHASIA.bit();
		}
		return izin;
	}

	private KebijakanAula() {
	}
}
