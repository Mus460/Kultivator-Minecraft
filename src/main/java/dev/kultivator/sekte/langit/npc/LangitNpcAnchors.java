package dev.kultivator.sekte.langit.npc;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The anchors of the NPC sky palaces (DESIGN 37.7.6), kind {@code sektelangit/<what>} through the Dunia anchor spine:
 * <ul>
 * <li>{@code gerbang}: publishes the palace as a Dunia site ({@code sekte/istana_langit}, a map marker), and greets a
 * visitor within {@link #GREET_RANGE} blocks: first contact through {@code DiplomasiService.meet} (module D grants
 * {@code diplomasi_temu} and reveals its gate), the advancement {@code sekte_langit/temu_langit}, and
 * {@code sekte_langit/sekutu_langit} when the visitor's hall is allied with the sect.</li>
 * <li>{@code murid} (up to 6): a sect body stands there (at most {@code DiplomasiConfig.anggotaMaks} per palace, read
 * through module D's default 6), kept on the island by {@link JaringNpc}.</li>
 * <li>{@code ketua}: the palace elder (a greeter, not the sect master; a war is still won at the mountain gate, module
 * D's rule), only while the visitor's hall is not hostile.</li>
 * <li>{@code dermaga}: the palace dock (Hak Labuh for allies is announced; the ships themselves are module 37M's).</li>
 * </ul>
 * Bodies appear only on the island's floor (the anchors stand there) and vanish when the anchor unloads.
 */
public final class LangitNpcAnchors implements AnchorHandler {

	public static final String MODULE = "sektelangit";
	public static final String SITE_KIND = "sekte/istana_langit";
	public static final double GREET_RANGE = 40.0D;
	public static final long GREET_AGAIN = 20L * 60L * 10L;
	public static final int MAX_BODIES = 6;
	static final LangitNpcAnchors HANDLER = new LangitNpcAnchors();

	private final Map<UUID, DiscipleEntity> byAnchor = new HashMap<>();
	private final Map<String, Long> greeted = new HashMap<>();

	@Override
	public int radius() {
		return 64;
	}

	/** The palace whose column the anchor belongs to (within 96 blocks), or null. */
	private static LangitNpcPlan.@Nullable Istana istanaOf(ServerLevel level, DuniaAnchorEntity anchor) {
		return LangitNpcPlan.near(level.getSeed(), anchor.getX(), anchor.getZ(), 96.0D);
	}

	static Component name(LangitNpcPlan.@Nullable Istana istana) {
		String id = istana == null ? null : LangitNpcPlan.sectId(istana);
		Component name = id == null ? null : SekteApi.diplomasi().name(id);
		return name != null ? name : Component.translatable("kultivator.sekte_langit.npc.tanpa_nama");
	}

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		if (!"gerbang".equals(anchor.what())) {
			return;
		}
		LangitNpcPlan.Istana istana = istanaOf(level, anchor);
		DuniaSites.publish(level, SITE_KIND, anchor.blockPosition(), istana == null ? 5 : Math.max(1, istana.gate().tier()));
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!LangitConfig.on()) {
			return;
		}
		LangitNpcPlan.Istana istana = istanaOf(level, anchor);
		switch (anchor.what()) {
			case "gerbang" -> greet(level, anchor, istana, within);
			case "murid" -> member(level, anchor, istana);
			case "ketua" -> elder(level, anchor, istana, within);
			case "dermaga" -> dock(level, anchor, istana, within);
			default -> {
			}
		}
		JaringNpc.second(level, new ArrayList<>(byAnchor.values()));
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		DiscipleEntity body = byAnchor.remove(anchor.getUUID());
		JaringNpc.vanish(body);
	}

	private void greet(ServerLevel level, DuniaAnchorEntity anchor, LangitNpcPlan.@Nullable Istana istana, List<ServerPlayer> within) {
		long now = level.getGameTime();
		String sectId = istana == null ? null : LangitNpcPlan.sectId(istana);
		DiplomasiService d = SekteApi.diplomasi();
		for (ServerPlayer player : within) {
			if (player.distanceToSqr(anchor) > GREET_RANGE * GREET_RANGE) {
				continue;
			}
			String key = player.getUUID() + "|" + anchor.getUUID();
			Long last = greeted.get(key);
			if (last != null && now - last < GREET_AGAIN) {
				continue;
			}
			greeted.put(key, now);
			Advancements.grant(player, LangitIds.ADV + "temu_langit");
			Sikap sikap = Sikap.NETRAL;
			if (sectId != null) {
				d.meet(player.getUUID(), sectId);
				sikap = d.stance(player.getUUID(), sectId);
			}
			if (sikap == Sikap.SEKUTU) {
				Advancements.grant(player, LangitIds.ADV + "sekutu_langit");
			}
			boolean hostile = sikap == Sikap.PERANG || sikap == Sikap.BERMUSUHAN;
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.npc.sapa." + sikap.id(), name(istana))
					.withStyle(hostile ? ChatFormatting.RED : ChatFormatting.AQUA));
			level.playSound(null, anchor.getX(), anchor.getY(), anchor.getZ(), SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 0.9F, hostile ? 0.6F : 1.1F);
		}
	}

	private int bodiesOf(LangitNpcPlan.@Nullable Istana istana, ServerLevel level) {
		int n = 0;
		for (DiscipleEntity body : byAnchor.values()) {
			if (!body.isRemoved() && body.level() == level && istana != null
					&& LangitNpcPlan.near(level.getSeed(), body.getX(), body.getZ(), 96.0D) == istana) {
				n++;
			}
		}
		return n;
	}

	private void member(ServerLevel level, DuniaAnchorEntity anchor, LangitNpcPlan.@Nullable Istana istana) {
		DiscipleEntity old = byAnchor.get(anchor.getUUID());
		if (old != null && !old.isRemoved()) {
			return;
		}
		byAnchor.remove(anchor.getUUID());
		if (bodiesOf(istana, level) >= MAX_BODIES) {
			return;
		}
		int slot = Math.floorMod(anchor.data().getIntOr("slot", 0), 32);
		long seed = (istana == null ? 0L : istana.gate().lambangSeed()) * 31L + slot * 17L;
		int tier = istana == null ? 3 : istana.gate().tier();
		int step = 8 + tier * 6 + Math.floorMod((int) seed, 6);
		Component name = Component.translatable("kultivator.sekte_langit.npc.murid", name(istana));
		BlockPos at = anchor.blockPosition();
		DiscipleEntity body = JaringNpc.spawn(level, Vec3.atBottomCenterOf(at), seed, step, name, at);
		if (body != null) {
			byAnchor.put(anchor.getUUID(), body);
		}
	}

	private void elder(ServerLevel level, DuniaAnchorEntity anchor, LangitNpcPlan.@Nullable Istana istana, List<ServerPlayer> within) {
		DiscipleEntity old = byAnchor.get(anchor.getUUID());
		if (old != null && !old.isRemoved()) {
			return;
		}
		String sectId = istana == null ? null : LangitNpcPlan.sectId(istana);
		if (sectId != null && !within.isEmpty()) {
			Sikap sikap = SekteApi.diplomasi().stance(within.get(0).getUUID(), sectId);
			if (sikap == Sikap.PERANG || sikap == Sikap.BERMUSUHAN) {
				return;
			}
		}
		long seed = (istana == null ? 7L : istana.gate().lambangSeed()) * 131L + 99L;
		int tier = istana == null ? 3 : istana.gate().tier();
		Component name = Component.translatable("kultivator.sekte_langit.npc.tetua", name(istana));
		BlockPos at = anchor.blockPosition();
		DiscipleEntity body = JaringNpc.spawn(level, Vec3.atBottomCenterOf(at), seed, 20 + tier * 8, name, at);
		if (body != null) {
			byAnchor.put(anchor.getUUID(), body);
		}
	}

	private void dock(ServerLevel level, DuniaAnchorEntity anchor, LangitNpcPlan.@Nullable Istana istana, List<ServerPlayer> within) {
		String sectId = istana == null ? null : LangitNpcPlan.sectId(istana);
		if (sectId == null) {
			return;
		}
		long now = level.getGameTime();
		for (ServerPlayer player : within) {
			if (player.distanceToSqr(anchor) > 12.0D * 12.0D || !player.isPassenger()) {
				continue;
			}
			String key = player.getUUID() + "|dock|" + anchor.getUUID();
			Long last = greeted.get(key);
			if (last != null && now - last < GREET_AGAIN) {
				continue;
			}
			greeted.put(key, now);
			boolean allied = SekteApi.diplomasi().stance(player.getUUID(), sectId) == Sikap.SEKUTU;
			Msg.actionBar(player, Component.translatable(allied ? "kultivator.sekte_langit.npc.labuh" : "kultivator.sekte_langit.npc.labuh_tolak",
					name(istana)).withStyle(allied ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		}
	}

	/** Every live palace body (tests). */
	public static List<DiscipleEntity> alive() {
		return new ArrayList<>(HANDLER.byAnchor.values());
	}

	/** Server stopping: every body goes. */
	public static void clear() {
		for (DiscipleEntity body : HANDLER.byAnchor.values()) {
			JaringNpc.vanish(body);
		}
		HANDLER.byAnchor.clear();
		HANDLER.greeted.clear();
		JaringNpc.clear();
	}
}
