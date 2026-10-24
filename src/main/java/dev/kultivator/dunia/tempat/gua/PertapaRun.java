package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gua Pertapa (DESIGN 38.C.4, anchor {@code gua/pertapa} in the meditation chamber): the hermit sits by the spring with
 * his beads, his realm fixed by the site (Golden Core to Dao Union), his Dao too. His three trials are 38ST's quest
 * {@code tiga_ujian_pertapa} (herb and koan by dialog); the second one is watched here: meditating without a break in
 * his chamber for as long as the incense burns ({@link #DUPA_TICKS}, five minutes) fires {@code ujian pertapa:meditasi}.
 * When a visitor has passed all three, the hermit "goes wandering" the next morning (anchor state {@code pergi}); the
 * cave is then a lawful cave abode (its Qi spring and chamber count for {@code CaveAbode} as any cave does; 38 does not
 * change {@code CaveAbode}) and stays a meditation site ×1.3 ({@link GuaTempat}). Advancement {@code tempat/murid_pertapa}.
 */
public final class PertapaRun extends GuaRun {

	public static final String QUEST = "tiga_ujian_pertapa";
	/** The incense: five minutes of unbroken meditation. */
	public static final int DUPA_TICKS = 6000;

	private @Nullable KultivatorNpc hermit;
	private final Map<UUID, Integer> meditasi = new HashMap<>();
	private final Set<UUID> sapa = new HashSet<>();
	private final int ruang;

	public PertapaRun(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
		this.ruang = Math.max(4, Math.min(10, anchor.data().getIntOr("ruang", 6)));
	}

	/** The hermit's Dao, fixed by the site. */
	public Dao dao() {
		long s = situs();
		return Dao.values()[Math.floorMod((int) (s ^ (s >>> 29)), Dao.values().length)];
	}

	/** The hermit's realm, fixed by the site: Golden Core, Nascent Soul, Deity Transformation or Dao Union. */
	public Realm ranah() {
		long s = situs();
		return Realm.byOrdinal(Realm.GOLDEN_CORE.ordinal() + Math.floorMod((int) (s >>> 7), 4));
	}

	/** True once the hermit has left (the morning after a visitor passed his trials). */
	public boolean pergi() {
		long day = state().getLongOr("pergi", -1L);
		return day >= 0L && today() >= day;
	}

	@Override
	public double radius() {
		return ruang + 4.0D;
	}

	@Override
	protected void spawn() {
		if (!pergi()) {
			hermit = GuaTubuh.buat(this, NpcRole.PERTAPA, anchor.blockPosition(), frame.yaw(0.0F), 4, ranah(), dao(), GerakId.MEDITASI_NAPAS,
					dev.kultivator.sosok.PropId.TASBIH_TANGAN);
		}
		for (ServerPlayer player : playersWithin(48.0D)) {
			dupa(player);
		}
	}

	/** The incense smoke over the burner before the hermit (a client decoration). */
	private void dupa(ServerPlayer player) {
		ListTag isi = new ListTag();
		isi.add(GuaPayload.hiasan("ASAP_DUPA", spot(0, 0, -1), 0.0F, 0.0F));
		GuaPayload.hias(player, "gua/pertapa/" + situs(), isi);
	}

	@Override
	protected void nearby(List<ServerPlayer> within) {
		for (ServerPlayer player : within) {
			if (sapa.add(player.getUUID())) {
				dupa(player);
			}
			watch(player);
		}
		meditasi.keySet().removeIf(id -> within.stream().noneMatch(p -> p.getUUID().equals(id)));
	}

	/** Every 20 ticks per nearby player: the unbroken meditation of the second trial, and the hermit's farewell. */
	private void watch(ServerPlayer player) {
		UUID id = player.getUUID();
		boolean inChamber = player.position().distanceToSqr(centre()) <= (ruang + 1.0D) * (ruang + 1.0D);
		boolean due = QuestTracker.aktif(player, QUEST) && "meditasi".equals(QuestTracker.simpul(player, QUEST));
		if (due && inChamber && Cultivators.get(player).meditating()) {
			int t = meditasi.getOrDefault(id, 0) + 20;
			meditasi.put(id, t);
			if (t % 1200 == 0 && t < DUPA_TICKS) {
				Msg.actionBar(player, Component.translatable("kultivator.gua.pertapa.dupa", t / 1200, DUPA_TICKS / 1200).withStyle(ChatFormatting.GOLD));
			}
			if (t >= DUPA_TICKS) {
				meditasi.remove(id);
				CeritaApi.peristiwa(player, "ujian", "pertapa:meditasi");
				level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 0.8F, 1.2F);
				if (hermit != null) {
					GuaTubuh.ucap(player, hermit, Component.translatable("kultivator.gua.pertapa.dupa_habis"), ChatFormatting.GOLD);
				}
			}
		} else if (meditasi.containsKey(id)) {
			int t = meditasi.remove(id);
			if (due && t >= 200) {
				Msg.actionBar(player, Component.translatable("kultivator.gua.pertapa.terputus").withStyle(ChatFormatting.RED));
			}
		}
		if (CeritaApi.tahap(player, QUEST) == dev.kultivator.dunia.tempat.TempatEvents.QUEST_SELESAI) {
			Advancements.grant(player, "tempat/murid_pertapa");
			if (state().getLongOr("pergi", -1L) < 0L) {
				state().putLong("pergi", today() + 1L);
				if (hermit != null) {
					GuaTubuh.ucap(player, hermit, Component.translatable("kultivator.gua.pertapa.pamit"), ChatFormatting.GOLD);
				}
			}
		}
	}

	/** True while {@code player} is in the middle of the incense trial. */
	public boolean bermeditasi(ServerPlayer player) {
		return meditasi.getOrDefault(player.getUUID(), 0) > 0;
	}

	@Override
	protected void tick(long now) {
		KultivatorNpc h = hermit;
		if (h == null) {
			return;
		}
		if (h.isRemoved()) {
			hermit = null;
			return;
		}
		if (now % 200L == 0L) {
			if (pergi() && playersWithin(10.0D).isEmpty()) {
				// he has gone wandering: only a mat and the quiet spring remain
				Vec3 at = h.position();
				level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1.0D, at.z, 12, 0.3D, 0.6D, 0.3D, 0.02D);
				h.discard();
				hermit = null;
				return;
			}
			// keep him seated in meditation (a dialog may have played another clip)
			HidupApi.klip(h, GerakId.MEDITASI_NAPAS, dev.kultivator.sosok.PropId.TASBIH_TANGAN, 0);
		}
	}

	@Override
	protected void despawn() {
		hermit = null;
		meditasi.clear();
		sapa.clear();
		GuaPayload.hapusHias(level, centre(), "gua/pertapa/" + situs());
		super.despawn();
	}
}
