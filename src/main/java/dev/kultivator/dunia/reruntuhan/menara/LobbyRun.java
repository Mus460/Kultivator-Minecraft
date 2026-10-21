package dev.kultivator.dunia.reruntuhan.menara;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.reruntuhan.AnchorRun;
import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.RNet;
import dev.kultivator.dunia.reruntuhan.Reruntuhan;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
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
 * The lobby of the tower (anchor {@code menara/lobi} at the dimension's origin, DESIGN 34.5.7): ten band gates on a
 * ring (a {@link TekaPartEntity#GATE} each; the client draws the gate quads lit up to the player's highest band), the
 * exit gate home and the ranking stele. Standing in a gate for a second, or a click, takes the players standing with you
 * (≤ 4 within 6 blocks) up to the first floor of that band; with {@code startAtBand} the bands already climbed need not
 * be climbed again. The exit returns everyone to where they entered.
 */
public final class LobbyRun extends AnchorRun {

	public static final int STAND_TICKS = 20;
	private final TekaPartEntity[] gates = new TekaPartEntity[10];
	private @Nullable TekaPartEntity exit;
	private final Map<UUID, int[]> standing = new HashMap<>();

	public LobbyRun(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
	}

	@Override
	public double radius() {
		return 32.0D;
	}

	@Override
	protected void spawn() {
		int n = Frame.triples(data(), "gates");
		for (int b = 0; b < Math.min(10, n); b++) {
			int[] off = Frame.triple(data(), "gates", b);
			TekaPartEntity gate = make(REntities.TEKA_PLAT, frame.bottom(off[0], off[1], off[2]), 0.0F);
			if (gate == null) {
				continue;
			}
			gate.setRole(TekaPartEntity.GATE);
			gate.setCenter(centre());
			gate.setArg(b + 1);
			final int band = b + 1;
			gate.setListener((player, part, attack) -> take(player, band));
			if (add(gate)) {
				gates[b] = gate;
			}
		}
		int[] e = Frame.triple(data(), "exit", 0);
		if (e != null) {
			TekaPartEntity made = make(REntities.TEKA_PLAT, frame.bottom(e[0], e[1], e[2]), 0.0F);
			if (made != null) {
				made.setRole(TekaPartEntity.EXIT);
				made.setCenter(centre());
				made.setListener((player, part, attack) -> leave(player));
				if (add(made)) {
					exit = made;
				}
			}
		}
	}

	@Override
	protected void tick(long now) {
		List<ServerPlayer> here = playersWithin(28.0D);
		for (ServerPlayer player : here) {
			if (Menara.inRun(player)) {
				continue;
			}
			int at = -1;
			for (int b = 0; b < 10; b++) {
				TekaPartEntity gate = gates[b];
				if (gate != null && player.position().distanceToSqr(gate.position()) < 1.1D * 1.1D) {
					at = b + 1;
				}
			}
			if (exit != null && player.position().distanceToSqr(exit.position()) < 1.1D * 1.1D) {
				at = 0;
			}
			int[] s = standing.get(player.getUUID());
			if (at < 0) {
				standing.remove(player.getUUID());
				continue;
			}
			if (s == null || s[0] != at) {
				standing.put(player.getUUID(), new int[]{at, 0});
				continue;
			}
			if (++s[1] == STAND_TICKS) {
				if (at == 0) {
					leave(player);
				} else {
					take(player, at);
				}
			}
		}
		if (now % 40L == 0L) {
			sync(here);
		}
		if (now % 100L == 50L) {
			int[] st = Frame.triple(data(), "stele", 0);
			Vec3 stele = st == null ? centre() : frame.bottom(st[0], st[1], st[2]);
			RankStele.send(level, stele.add(0.0D, 3.0D, 0.0D), playersWithin(28.0D), anchor.getId());
		}
	}

	private void sync(List<ServerPlayer> here) {
		int[] pos = new int[30];
		for (int b = 0; b < 10; b++) {
			TekaPartEntity gate = gates[b];
			if (gate != null) {
				pos[b * 3] = gate.getBlockX();
				pos[b * 3 + 1] = gate.getBlockY();
				pos[b * 3 + 2] = gate.getBlockZ();
			}
		}
		for (ServerPlayer player : here) {
			CompoundTag tag = new CompoundTag();
			tag.putString("op", "lobby");
			tag.put("gates", new IntArrayTag(pos));
			tag.putInt("unlocked", Menara.unlockedBand(player));
			tag.putInt("best", Menara.best(player));
			tag.putDouble("cx", centre().x);
			tag.putDouble("cy", centre().y);
			tag.putDouble("cz", centre().z);
			RNet.send(player, tag);
		}
	}

	/** Takes the players at the gate up to the first floor of {@code band}. */
	private void take(ServerPlayer player, int band) {
		if (Menara.inRun(player)) {
			return;
		}
		if (!Reruntuhan.config().tower) {
			Msg.chat(player, Component.translatable("kultivator.reruntuhan.menara.closed").withStyle(ChatFormatting.GRAY));
			return;
		}
		int unlocked = Menara.unlockedBand(player);
		if (band > unlocked) {
			Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.menara.gate.locked", band, unlocked).withStyle(ChatFormatting.RED));
			return;
		}
		if (Menara.lukaLeft(player) > 0L) {
			Msg.chat(player, Component.translatable("kultivator.reruntuhan.menara.why.luka", Msg.number(Math.ceil(Menara.lukaLeft(player) / 1200.0D)))
					.withStyle(ChatFormatting.RED));
			return;
		}
		int floor = Reruntuhan.config().startAtBand ? 10 * (band - 1) + 1 : 1;
		TekaPartEntity gate = gates[band - 1];
		Vec3 at = gate == null ? player.position() : gate.position();
		List<ServerPlayer> party = new ArrayList<>();
		party.add(player);
		for (ServerPlayer other : playersWithin(28.0D)) {
			if (other != player && party.size() < Menara.PARTY_MAX && other.position().distanceToSqr(at) <= Menara.PARTY_RANGE * Menara.PARTY_RANGE
					&& !Menara.inRun(other) && Menara.lukaLeft(other) <= 0L) {
				party.add(other);
			}
		}
		RFx.at(level, at.add(0.0D, 1.5D, 0.0D), RFx.BAND_GATE, band, 1, 2.0F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F + band * 0.08F);
		standing.clear();
		Menara.start(party, floor, false);
	}

	private void leave(ServerPlayer player) {
		standing.remove(player.getUUID());
		Menara.sendHome(player);
	}

	@Override
	protected void despawn() {
		standing.clear();
		exit = null;
		for (int b = 0; b < 10; b++) {
			gates[b] = null;
		}
		super.despawn();
	}
}
