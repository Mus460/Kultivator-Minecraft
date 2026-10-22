package dev.kultivator.sekte.diplomasi;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.sekte.api.Sikap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
 * The people of a mountain gate (DESIGN 36.2.9): the gate structure's anchors (kind {@code sektenpc/gerbang},
 * {@code sektenpc/murid}, {@code sektenpc/ketua}, placed by {@code tools/gen_sekte_gerbang.py}) are turned into bodies only
 * while a player stands within {@link #radius()} blocks: up to {@code anggotaMaks} members (at most 6, defectors of the
 * player's own hall among them) and the sect master at its throne. They leave with the chunk. The gate anchor marks the
 * sect's gate as born (its real height) and greets a visitor by the stance of their hall.
 */
final class AnggotaSekte implements AnchorHandler {

	static final String MODULE = "sektenpc";
	/** The Dunia site kind of an NPC sect's mountain gate (T12: map marker and pointer by module M2). */
	public static final String SITE_KIND = "sekte/gerbang";
	static final AnggotaSekte HANDLER = new AnggotaSekte();
	/** A gate's greeting repeats for the same player after this long (ticks). */
	static final long GREET_AGAIN = 20L * 60L * 10L;
	/** How far from a planned gate an anchor may lie and still belong to that sect (blocks). */
	static final double BELONGS = 256.0D;

	private record Member(UUID anchor, String sectId) {
	}

	private static final Map<UUID, DiscipleEntity> BY_ANCHOR = new HashMap<>();
	private static final Map<UUID, Member> MEMBERS = new HashMap<>();
	private static final Map<String, Long> GREETED = new HashMap<>();

	static void clear() {
		for (DiscipleEntity body : BY_ANCHOR.values()) {
			body.discard();
		}
		BY_ANCHOR.clear();
		MEMBERS.clear();
		GREETED.clear();
	}

	@Override
	public int radius() {
		return 64;
	}

	/** The sect whose planned gate lies nearest the anchor (within {@link #BELONGS}), or null. */
	private static @Nullable SekteNpc sectOf(DuniaAnchorEntity anchor) {
		MinecraftServer server = Diplomasi.server();
		if (server == null) {
			return null;
		}
		SekteNpc best = null;
		double bestD = BELONGS * BELONGS;
		for (SekteNpc sect : DiplomasiData.get(server).sects()) {
			double dx = anchor.getX() - sect.blockX;
			double dz = anchor.getZ() - sect.blockZ;
			double d = dx * dx + dz * dz;
			if (d < bestD) {
				bestD = d;
				best = sect;
			}
		}
		return best;
	}

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		if (!"gerbang".equals(anchor.what())) {
			return;
		}
		SekteNpc sect = sectOf(anchor);
		MinecraftServer server = level.getServer();
		if (sect == null) {
			return;
		}
		// T12: the gate is a Dunia site (map marker, rumours, locate), also for gates born before this was added
		// (publish is idempotent). Its position is the planned column at the anchor's height, the same as sect.gate().
		DuniaSites.publish(level, SITE_KIND, new BlockPos(sect.blockX, anchor.getBlockY(), sect.blockZ), sect.tier);
		if (sect.gateBorn()) {
			return;
		}
		DiplomasiData data = DiplomasiData.get(server);
		sect.gateState = SekteNpc.GERBANG_LAHIR;
		sect.gateY = anchor.getBlockY();
		data.addNews(Diplomasi.today(server), Component.translatable(GeneratorNama.KEY + "berita.gerbang", sect.name()));
		data.setDirty();
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		SekteNpc sect = sectOf(anchor);
		if (sect == null) {
			return;
		}
		switch (anchor.what()) {
			case "gerbang" -> greet(level, anchor, sect, within);
			case "murid" -> member(level, anchor, sect);
			case "ketua" -> master(level, anchor, sect);
			default -> {
			}
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		DiscipleEntity body = BY_ANCHOR.remove(anchor.getUUID());
		if (body != null) {
			MEMBERS.remove(body.getUUID());
			KetuaSekte.forget(body);
			body.discard();
		}
	}

	private static void greet(ServerLevel level, DuniaAnchorEntity anchor, SekteNpc sect, List<ServerPlayer> within) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		for (ServerPlayer player : within) {
			if (player.distanceToSqr(anchor) > 28.0D * 28.0D) {
				continue;
			}
			String key = player.getUUID() + "|" + sect.id;
			Long last = GREETED.get(key);
			if (last != null && now - last < GREET_AGAIN) {
				continue;
			}
			GREETED.put(key, now);
			Diplomasi.meet(server, player.getUUID(), sect);
			Sikap sikap = DiplomasiData.get(server).relasi(player.getUUID(), sect).sikap();
			player.sendSystemMessage(Component.translatable(GeneratorNama.KEY + "gerbang.sapa." + sikap.id(), sect.name(), sect.master())
					.withStyle(sikap == Sikap.PERANG || sikap == Sikap.BERMUSUHAN ? ChatFormatting.RED : ChatFormatting.GOLD));
			level.playSound(null, anchor.getX(), anchor.getY(), anchor.getZ(), SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 1.0F,
					sikap == Sikap.PERANG ? 0.6F : 1.0F);
		}
	}

	private static int membersOf(String sectId) {
		int n = 0;
		for (Member m : MEMBERS.values()) {
			if (m.sectId.equals(sectId)) {
				n++;
			}
		}
		return n;
	}

	private static void member(ServerLevel level, DuniaAnchorEntity anchor, SekteNpc sect) {
		DiscipleEntity old = BY_ANCHOR.get(anchor.getUUID());
		if (old != null && !old.isRemoved()) {
			return;
		}
		if (old != null) {
			BY_ANCHOR.remove(anchor.getUUID());
			MEMBERS.remove(old.getUUID());
		}
		if (membersOf(sect.id) >= Diplomasi.config().anggotaMaks) {
			return;
		}
		int slot = Math.floorMod(anchor.data().getIntOr("slot", membersOf(sect.id)), 32);
		Component name;
		if (slot < sect.pembelot.size()) {
			name = Component.translatable(GeneratorNama.KEY + "anggota.pembelot", sect.pembelot.get(slot).getStringOr("name", "?"), sect.name());
		} else {
			name = Component.translatable(GeneratorNama.KEY + "anggota.nama", GeneratorNama.member(sect.lambang, slot), sect.name());
		}
		BlockPos at = anchor.blockPosition();
		DiscipleEntity body = Tubuh.spawn(level, Vec3.atBottomCenterOf(at), sect, sect.memberRankStep(slot), slot, name, at,
				anchor.data().getBooleanOr("panji", slot % 3 == 0));
		if (body != null) {
			BY_ANCHOR.put(anchor.getUUID(), body);
			MEMBERS.put(body.getUUID(), new Member(anchor.getUUID(), sect.id));
		}
	}

	private static void master(ServerLevel level, DuniaAnchorEntity anchor, SekteNpc sect) {
		DiscipleEntity old = BY_ANCHOR.get(anchor.getUUID());
		if (old != null && !old.isRemoved()) {
			return;
		}
		BlockPos at = anchor.blockPosition();
		DiscipleEntity body = KetuaSekte.spawnAtGate(level, Vec3.atBottomCenterOf(at), sect, at);
		if (body != null) {
			BY_ANCHOR.put(anchor.getUUID(), body);
		}
	}

	/** Talking to a member: a line by the stance of the speaker's hall. */
	static boolean interact(ServerPlayer player, DiscipleEntity body) {
		Member member = MEMBERS.get(body.getUUID());
		if (member == null) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		SekteNpc sect = DiplomasiData.get(server).sect(member.sectId);
		if (sect == null) {
			return true;
		}
		Diplomasi.meet(server, player.getUUID(), sect);
		Sikap sikap = DiplomasiData.get(server).relasi(player.getUUID(), sect).sikap();
		int line = Math.floorMod(body.getUUID().hashCode(), 3);
		body.getLookControl().setLookAt(player);
		player.sendSystemMessage(Component.translatable(GeneratorNama.KEY + "anggota.sapa." + sikap.id() + "." + line, body.getDisplayName(),
				sect.name(), sect.master()).withStyle(ChatFormatting.WHITE));
		Tubuh.say(body, switch (sikap) {
			case PERANG, BERMUSUHAN -> "attack_alt";
			case DINGIN -> "careful";
			case SEKUTU -> "cheer";
			default -> "greet";
		});
		return true;
	}

	/** The members alive now (for {@code uji perguruan diplomasi info}). */
	static List<DiscipleEntity> alive() {
		return new ArrayList<>(BY_ANCHOR.values());
	}

	private AnggotaSekte() {
	}
}
